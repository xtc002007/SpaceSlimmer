use std::fs::File;
use std::io::Write;
use std::sync::atomic::AtomicBool;
use std::sync::Arc;
use std::time::SystemTime;

use parking_lot::RwLock;
use ss_classify::FileCategory;
use ss_dupe::{CandidateFile, DupeOptions};
use ss_platform::DeviceStorageMetrics;
use ss_scan::{
    scan_native, scan_platform_lister, NodeKind, ScanConfig, ScanScope, ScanTree,
    FLAG_COLLAPSED, FLAG_DELETED, FLAG_PROTECTED, NO_NODE,
};
use ss_treemap::{compute_treemap, Rect, TreemapInput, TreemapOptions};

use crate::engine::SsError;

/// Input request to trigger a storage analysis scan.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ScanRequest {
    pub root_path: String,
    pub scope_type: String, // "FullDisk", "Categories", "Folders"
    pub selected_categories: Vec<String>,
    pub selected_folders: Vec<String>,
    pub collapse_threshold_bytes: u64,
    pub include_hidden: bool,
    pub min_file_size: u64,
    pub source_level: String, // "L1", "L2", "L3"
    pub device_total_bytes: Option<u64>,
    pub device_free_bytes: Option<u64>,
    pub device_apps_bytes: Option<u64>,
}

/// High-level summary of a scan session.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ScanSummary {
    pub id: String,
    pub created_at: i64,
    pub scope_json: String,
    pub source_level: String,
    pub root_path: String,
    pub total_bytes: u64,
    pub used_bytes: u64,
    pub free_bytes: u64,
    pub scanned_bytes: u64,
    pub file_count: u32,
    pub duration_ms: u64,
}

/// Category distribution statistic for UI donut/stacked bars.
#[derive(Debug, Clone, uniffi::Record)]
pub struct CategoryStatView {
    pub category_key: String,
    pub display_name: String,
    pub color_hex: String,
    pub bytes: u64,
    pub file_count: u32,
    pub is_compressible: bool,
    pub is_junk_candidate: bool,
}

/// A node view for directory trees and list items.
#[derive(Debug, Clone, uniffi::Record)]
pub struct NodeView {
    pub id: u32,
    pub parent: u32,
    pub name: String,
    pub path: String,
    pub is_dir: bool,
    pub is_collapsed: bool,
    pub category_key: String,
    pub color_hex: String,
    pub size_logical: u64,
    pub size_physical: u64,
    pub file_count: u32,
    pub dir_count: u32,
    pub mtime: u32,
    pub is_protected: bool,
    pub is_compressible: bool,
}

/// Treemap item geometry and meta for Canvas rendering.
#[derive(Debug, Clone, uniffi::Record)]
pub struct TreemapItemView {
    pub id: u32,
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
    pub weight: u64,
    pub name: String,
    pub category_key: String,
    pub color_hex: String,
    pub is_dir: bool,
    pub percentage: f32,
    pub is_collapsed: bool,
    pub collapsed_count: u32,
}

/// Full Treemap layout result for a view bounds.
#[derive(Debug, Clone, uniffi::Record)]
pub struct TreemapViewResult {
    pub bounds_w: f32,
    pub bounds_h: f32,
    pub total_weight: u64,
    pub items: Vec<TreemapItemView>,
}

/// Item within a duplicate group.
#[derive(Debug, Clone, uniffi::Record)]
pub struct DuplicateItemView {
    pub id: u32,
    pub path: String,
    pub mtime: u32,
    pub is_suggested_keep: bool,
}

/// Group of duplicate files.
#[derive(Debug, Clone, uniffi::Record)]
pub struct DuplicateGroupView {
    pub hash: String,
    pub file_size: u64,
    pub waste_bytes: u64,
    pub items: Vec<DuplicateItemView>,
}

/// Difference in storage between two scan snapshots.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ScanDiffView {
    pub category_key: String,
    pub display_name: String,
    pub older_bytes: u64,
    pub newer_bytes: u64,
    pub delta_bytes: i64,
    pub older_files: u32,
    pub newer_files: u32,
    pub delta_files: i32,
}

/// Real-time progress payload reported during scanning.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ScanProgressView {
    pub scanned_bytes: u64,
    pub scanned_files: u32,
    pub current_path: String,
}

/// Raw directory entry passed across platform boundary (e.g. from SAF or MediaStore).
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct PlatformDirEntry {
    pub name: String,
    pub is_dir: bool,
    pub size_bytes: u64,
    pub mtime_sec: u32,
    pub mime_type: Option<String>,
}

/// Host-provided directory enumerator for non-standard / sandboxed paths.
#[derive(Debug, Clone, thiserror::Error, uniffi::Error)]
pub enum PlatformListerError {
    #[error("Platform lister error: {detail}")]
    Failed { detail: String },
}

/// Host-provided directory enumerator for non-standard / sandboxed paths.
#[uniffi::export(callback_interface)]
pub trait PlatformDirLister: Send + Sync {
    /// List entries under a folder handle (URI or path).
    fn list_dir(&self, handle: String) -> Result<Vec<PlatformDirEntry>, PlatformListerError>;
}

pub struct DirListerAdapter(pub Box<dyn PlatformDirLister>);

impl ss_platform::DirLister for DirListerAdapter {
    fn list_dir(&self, handle: &str) -> Result<Vec<ss_platform::RawDirEntry>, String> {
        let entries = self.0.list_dir(handle.to_string()).map_err(|e| e.to_string())?;
        Ok(entries
            .into_iter()
            .map(|e| ss_platform::RawDirEntry {
                name: e.name,
                is_dir: e.is_dir,
                size_bytes: e.size_bytes,
                mtime_sec: e.mtime_sec,
                mime_type: e.mime_type,
            })
            .collect())
    }
}

/// Callback observer for real-time progress updates.
#[uniffi::export(callback_interface)]
pub trait ScanObserver: Send + Sync {
    fn on_progress(&self, progress: ScanProgressView);
    fn on_done(&self, summary: ScanSummary);
    fn on_error(&self, message: String);
}

/// Active in-memory scan session state.
pub struct ActiveScanSession {
    pub scan_id: String,
    pub tree: RwLock<Option<ScanTree>>,
    pub summary: RwLock<Option<ScanSummary>>,
    pub cancel_flag: Arc<AtomicBool>,
    pub pause_flag: Arc<AtomicBool>,
}

impl ActiveScanSession {
    pub fn new(scan_id: String) -> Self {
        Self {
            scan_id,
            tree: RwLock::new(None),
            summary: RwLock::new(None),
            cancel_flag: Arc::new(AtomicBool::new(false)),
            pause_flag: Arc::new(AtomicBool::new(false)),
        }
    }
}

/// Run a scan and populate the session.
pub fn execute_scan(
    session: Arc<ActiveScanSession>,
    req: ScanRequest,
    observer: Option<Box<dyn ScanObserver>>,
    lister: Option<Box<dyn PlatformDirLister>>,
    on_persist: Option<Box<dyn FnOnce(&ScanSummary, &ScanTree) + Send>>,
) -> Result<ScanSummary, SsError> {
    let start_time = SystemTime::now();

    // Map scope
    let scope = match req.scope_type.as_str() {
        "Categories" => {
            let cats: Vec<FileCategory> = req
                .selected_categories
                .iter()
                .filter_map(|k| match k.as_str() {
                    "video" => Some(FileCategory::Video),
                    "image" => Some(FileCategory::Image),
                    "audio" => Some(FileCategory::Audio),
                    "archive" => Some(FileCategory::Archive),
                    "document" => Some(FileCategory::Document),
                    "package" => Some(FileCategory::Package),
                    "system" => Some(FileCategory::System),
                    _ => None,
                })
                .collect();
            ScanScope::Categories(cats)
        }
        "Folders" => ScanScope::Folders(req.selected_folders.clone()),
        _ => ScanScope::FullDisk,
    };

    let device_metrics = if let (Some(total), Some(free)) =
        (req.device_total_bytes, req.device_free_bytes)
    {
        Some(DeviceStorageMetrics {
            total_bytes: total,
            free_bytes: free,
            apps_bytes: req.device_apps_bytes.unwrap_or(0),
        })
    } else {
        None
    };

    let config = ScanConfig {
        root_path: req.root_path.clone(),
        scope,
        collapse_threshold_bytes: req.collapse_threshold_bytes,
        include_hidden: req.include_hidden,
        min_file_size: req.min_file_size,
        device_metrics,
    };

    let observer_ref = observer.as_deref();
    let cancel = session.cancel_flag.clone();
    let pause = session.pause_flag.clone();

    let adapter = lister.map(DirListerAdapter);
    let tree_res = if let Some(ref platform_lister) = adapter {
        scan_platform_lister(
            platform_lister,
            &req.root_path,
            &config,
            Some(&cancel),
            Some(&pause),
            |p| {
                if let Some(obs) = observer_ref {
                    obs.on_progress(ScanProgressView {
                        scanned_bytes: p.scanned_bytes,
                        scanned_files: p.scanned_files,
                        current_path: p.current_path.clone(),
                    });
                }
            },
        )
    } else {
        scan_native(&config, Some(&cancel), Some(&pause), |p| {
            if let Some(obs) = observer_ref {
                obs.on_progress(ScanProgressView {
                    scanned_bytes: p.scanned_bytes,
                    scanned_files: p.scanned_files,
                    current_path: p.current_path.clone(),
                });
            }
        })
    };

    let tree = match tree_res {
        Ok(t) => t,
        Err(e) => {
            let msg = format!("Scan failed: {}", e);
            if let Some(obs) = observer_ref {
                obs.on_error(msg.clone());
            }
            return Err(SsError::Internal(msg));
        }
    };

    let duration_ms = start_time
        .elapsed()
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0);

    let root_node = tree.nodes.get(tree.root as usize);
    let (scanned_bytes, file_count) = root_node
        .map(|n| (n.size_physical, n.file_count))
        .unwrap_or((0, 0));

    let total = req.device_total_bytes.unwrap_or(scanned_bytes);
    let free = req.device_free_bytes.unwrap_or(0);
    let used = total.saturating_sub(free);

    let summary = ScanSummary {
        id: session.scan_id.clone(),
        created_at: SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .map(|d| d.as_secs() as i64)
            .unwrap_or(0),
        scope_json: serde_json::to_string(&req.selected_categories).unwrap_or_else(|_| "[]".into()),
        source_level: req.source_level,
        root_path: req.root_path,
        total_bytes: total,
        used_bytes: used,
        free_bytes: free,
        scanned_bytes,
        file_count,
        duration_ms,
    };

    *session.tree.write() = Some(tree);
    *session.summary.write() = Some(summary.clone());

    if let Some(persist) = on_persist {
        if let Some(tree_ref) = session.tree.read().as_ref() {
            persist(&summary, tree_ref);
        }
    }

    if let Some(obs) = observer_ref {
        obs.on_done(summary.clone());
    }

    Ok(summary)
}

/// Convert a ScanTree node into a NodeView.
pub fn make_node_view(tree: &ScanTree, id: u32) -> Option<NodeView> {
    let node = tree.nodes.get(id as usize)?;
    let name = tree.node_name(id);
    let path = tree.node_path(id);
    let cat = node.category;

    Some(NodeView {
        id,
        parent: node.parent,
        name: name.to_string(),
        path,
        is_dir: node.kind.is_dir(),
        is_collapsed: (node.flags & FLAG_COLLAPSED) != 0,
        category_key: cat.key().to_string(),
        color_hex: cat.default_color_hex().to_string(),
        size_logical: node.size_logical,
        size_physical: node.size_physical,
        file_count: node.file_count,
        dir_count: node.dir_count,
        mtime: node.mtime,
        is_protected: (node.flags & FLAG_PROTECTED) != 0,
        is_compressible: cat.is_compressible(),
    })
}

/// Compute squarified treemap for children of a given node in the active tree.
pub fn generate_treemap_for_node(
    tree: &ScanTree,
    node_id: u32,
    width: f32,
    height: f32,
    min_rect_px: f32,
    max_items: u32,
) -> TreemapViewResult {
    let children = tree.children(node_id);
    let mut inputs = Vec::with_capacity(children.len());

    for cid in children {
        if let Some(child) = tree.nodes.get(cid as usize) {
            if (child.flags & FLAG_DELETED) != 0 {
                continue;
            }
            let name = tree.node_name(cid);
            inputs.push(TreemapInput {
                id: cid,
                weight: child.size_physical,
                name: name.to_string(),
                category: child.category.as_u8(),
                is_dir: child.kind.is_dir(),
            });
        }
    }

    let bounds = Rect::new(0.0, 0.0, width, height);
    let opts = TreemapOptions {
        min_side_px: min_rect_px.max(2.0),
        max_visible_items: max_items as usize,
        item_spacing_px: 1.5,
    };

    let result = compute_treemap(bounds, inputs, &opts);

    let items_view = result
        .items
        .into_iter()
        .map(|it| {
            let cat = FileCategory::from_u8(it.category);
            TreemapItemView {
                id: it.id,
                x: it.rect.x,
                y: it.rect.y,
                width: it.rect.width,
                height: it.rect.height,
                weight: it.weight,
                name: it.name,
                category_key: cat.key().to_string(),
                color_hex: cat.default_color_hex().to_string(),
                is_dir: it.is_dir,
                percentage: it.percentage,
                is_collapsed: it.is_collapsed_group,
                collapsed_count: it.collapsed_count,
            }
        })
        .collect();

    TreemapViewResult {
        bounds_w: width,
        bounds_h: height,
        total_weight: result.total_weight,
        items: items_view,
    }
}

/// Find duplicate candidate files in a tree and run duplicate detection.
pub fn find_tree_duplicates(
    tree: &ScanTree,
    node_id: u32,
    min_size: u64,
    max_groups: u32,
) -> Vec<DuplicateGroupView> {
    let mut candidates = Vec::new();
    let mut stack = vec![node_id];

    while let Some(curr) = stack.pop() {
        if let Some(node) = tree.nodes.get(curr as usize) {
            if (node.flags & FLAG_DELETED) != 0 {
                continue;
            }
            if node.kind == NodeKind::File && node.size_physical >= min_size {
                let path = tree.node_path(curr);
                candidates.push(CandidateFile {
                    id: curr,
                    path,
                    size: node.size_logical,
                    mtime: node.mtime,
                });
            }
            let mut c = node.first_child;
            while c != NO_NODE && (c as usize) < tree.nodes.len() {
                stack.push(c);
                c = tree.nodes[c as usize].next_sibling;
            }
        }
    }

    let opts = DupeOptions {
        min_file_size: min_size.max(1024),
        max_groups: max_groups as usize,
    };

    let dupes = ss_dupe::find_duplicates(&candidates, &opts, None, |_, _| {});

    dupes
        .into_iter()
        .map(|grp| DuplicateGroupView {
            hash: grp.hash,
            file_size: grp.file_size,
            waste_bytes: grp.total_waste_bytes,
            items: grp
                .items
                .into_iter()
                .map(|it| DuplicateItemView {
                    id: it.id,
                    path: it.path,
                    mtime: it.mtime,
                    is_suggested_keep: it.is_suggested_keep,
                })
                .collect(),
        })
        .collect()
}

/// Export scan tree as a WinDirStat-compatible CSV document.
pub fn export_tree_to_csv(tree: &ScanTree, dst_path: &str) -> std::io::Result<()> {
    let mut file = File::create(dst_path)?;
    // Header compatible with WinDirStat CSV columns
    writeln!(
        file,
        "\"Path\",\"Size (Physical)\",\"Size (Logical)\",\"Files\",\"Folders\",\"Last Change\",\"Attributes\""
    )?;

    if tree.root == NO_NODE {
        return Ok(());
    }

    let mut stack = vec![tree.root];
    while let Some(curr) = stack.pop() {
        if let Some(node) = tree.nodes.get(curr as usize) {
            if (node.flags & FLAG_DELETED) != 0 {
                continue;
            }

            let path = tree.node_path(curr);
            let attr = if node.kind.is_dir() { "Directory" } else { "File" };

            writeln!(
                file,
                "\"{}\",{},{},{},{},{},\"{}\"",
                path.replace('"', "\"\""),
                node.size_physical,
                node.size_logical,
                node.file_count,
                node.dir_count,
                node.mtime,
                attr
            )?;

            let mut c = node.first_child;
            while c != NO_NODE && (c as usize) < tree.nodes.len() {
                stack.push(c);
                c = tree.nodes[c as usize].next_sibling;
            }
        }
    }

    Ok(())
}
