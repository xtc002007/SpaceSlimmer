use std::fs;
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::SystemTime;

use serde::{Deserialize, Serialize};
use ss_classify::{classify, FileCategory};
use ss_platform::{DeviceStorageMetrics, DirLister};

use crate::error::ScanError;
use crate::tree::{
    NodeKind, ScanTree, FLAG_COLLAPSED, FLAG_NONE, FLAG_PROTECTED, FLAG_SCAN_ERROR, NO_NODE,
};

/// Scan scope definition.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ScanScope {
    FullDisk,
    Categories(Vec<FileCategory>),
    Folders(Vec<String>),
}

/// Configuration options for storage scanning.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScanConfig {
    pub root_path: String,
    pub scope: ScanScope,
    /// Files smaller than this threshold will be folded into a collapsed node.
    /// Default: 262,144 (256 KB). Set to 0 to disable folding.
    pub collapse_threshold_bytes: u64,
    pub include_hidden: bool,
    pub min_file_size: u64,
    pub device_metrics: Option<DeviceStorageMetrics>,
}

impl Default for ScanConfig {
    fn default() -> Self {
        Self {
            root_path: "/storage/emulated/0".into(),
            scope: ScanScope::FullDisk,
            collapse_threshold_bytes: 262_144,
            include_hidden: false,
            min_file_size: 0,
            device_metrics: None,
        }
    }
}

/// Progress callback payload.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScanProgress {
    pub scanned_bytes: u64,
    pub scanned_files: u32,
    pub current_path: String,
}

/// Scan a filesystem directory tree natively.
pub fn scan_native<F>(
    config: &ScanConfig,
    cancel_flag: Option<&Arc<AtomicBool>>,
    pause_flag: Option<&Arc<AtomicBool>>,
    mut progress_cb: F,
) -> Result<ScanTree, ScanError>
where
    F: FnMut(&ScanProgress),
{
    let root_path = Path::new(&config.root_path);
    if !root_path.exists() {
        return Err(ScanError::InvalidRoot(config.root_path.clone()));
    }

    let mut tree = ScanTree::new();
    let root_name = config.root_path.clone();

    let root_id = tree.add_node(
        NO_NODE,
        &root_name,
        NodeKind::Directory,
        FileCategory::Other,
        0,
        0,
        current_time_sec(),
        FLAG_NONE,
    );
    tree.root = root_id;

    let mut progress = ScanProgress {
        scanned_bytes: 0,
        scanned_files: 0,
        current_path: config.root_path.clone(),
    };

    match &config.scope {
        ScanScope::Folders(folders) if !folders.is_empty() => {
            for folder_str in folders {
                let p = Path::new(folder_str);
                if p.exists() {
                    let folder_name = p
                        .file_name()
                        .map(|n| n.to_string_lossy().to_string())
                        .unwrap_or_else(|| folder_str.clone());

                    let sub_id = tree.add_node(
                        root_id,
                        &folder_name,
                        NodeKind::Directory,
                        FileCategory::Other,
                        0,
                        0,
                        current_time_sec(),
                        FLAG_NONE,
                    );
                    tree.nodes[root_id as usize].dir_count += 1;

                    scan_dir_recursive(
                        p,
                        sub_id,
                        &mut tree,
                        config,
                        cancel_flag,
                        pause_flag,
                        &mut progress,
                        &mut progress_cb,
                    )?;
                }
            }
        }
        _ => {
            scan_dir_recursive(
                root_path,
                root_id,
                &mut tree,
                config,
                cancel_flag,
                pause_flag,
                &mut progress,
                &mut progress_cb,
            )?;
        }
    }

    // Add Free Space and Unknown pseudo nodes if metrics provided
    if let Some(metrics) = config.device_metrics {
        add_pseudo_nodes(&mut tree, root_id, metrics);
    }

    // Sort the entire tree by size
    tree.sort_tree_by_size(root_id);

    Ok(tree)
}

fn scan_dir_recursive<F>(
    dir_path: &Path,
    dir_node_id: u32,
    tree: &mut ScanTree,
    config: &ScanConfig,
    cancel_flag: Option<&Arc<AtomicBool>>,
    pause_flag: Option<&Arc<AtomicBool>>,
    progress: &mut ScanProgress,
    progress_cb: &mut F,
) -> Result<(), ScanError>
where
    F: FnMut(&ScanProgress),
{
    // Check cancellation
    if let Some(flag) = cancel_flag {
        if flag.load(Ordering::Relaxed) {
            return Err(ScanError::Cancelled);
        }
    }

    // Check pause
    if let Some(flag) = pause_flag {
        while flag.load(Ordering::Relaxed) {
            std::thread::sleep(std::time::Duration::from_millis(100));
            if let Some(c_flag) = cancel_flag {
                if c_flag.load(Ordering::Relaxed) {
                    return Err(ScanError::Cancelled);
                }
            }
        }
    }

    let entries = match fs::read_dir(dir_path) {
        Ok(e) => e,
        Err(_) => {
            tree.nodes[dir_node_id as usize].flags |= FLAG_SCAN_ERROR;
            return Ok(());
        }
    };

    let mut dir_logical = 0u64;
    let mut dir_physical = 0u64;
    let mut dir_files = 0u32;
    let mut dir_subdirs = 0u32;

    let mut collapsed_count = 0u32;
    let mut collapsed_logical = 0u64;
    let mut collapsed_physical = 0u64;

    let mut subdirs_to_visit = Vec::new();

    for entry_res in entries {
        if let Some(flag) = cancel_flag {
            if flag.load(Ordering::Relaxed) {
                return Err(ScanError::Cancelled);
            }
        }

        let entry = match entry_res {
            Ok(e) => e,
            Err(_) => continue,
        };

        let file_name = entry.file_name();
        let name_str = file_name.to_string_lossy();

        if !config.include_hidden && name_str.starts_with('.') && name_str != "." && name_str != ".." {
            continue;
        }

        let file_type = match entry.file_type() {
            Ok(ft) => ft,
            Err(_) => continue,
        };

        let full_path = entry.path();
        let full_path_str = full_path.to_string_lossy();

        if file_type.is_dir() {
            // Check for protected directories (e.g. Android/data or Android/obb)
            let is_protected = full_path_str.contains("/Android/data")
                || full_path_str.contains("\\Android\\data")
                || full_path_str.contains("/Android/obb")
                || full_path_str.contains("\\Android\\obb");

            let flags = if is_protected { FLAG_PROTECTED } else { FLAG_NONE };

            let sub_id = tree.add_node(
                dir_node_id,
                &name_str,
                NodeKind::Directory,
                FileCategory::Other,
                0,
                0,
                current_time_sec(),
                flags,
            );

            dir_subdirs += 1;
            subdirs_to_visit.push((full_path, sub_id));
        } else if file_type.is_file() {
            let metadata = match entry.metadata() {
                Ok(m) => m,
                Err(_) => continue,
            };

            let size = metadata.len();
            if size < config.min_file_size {
                continue;
            }

            // Estimate physical size (4KB block alignment on typical mobile filesystems)
            let physical = ((size + 4095) / 4096) * 4096;
            let mtime = metadata
                .modified()
                .ok()
                .and_then(|t| t.duration_since(SystemTime::UNIX_EPOCH).ok())
                .map(|d| d.as_secs() as u32)
                .unwrap_or(0);

            let classified = classify(&name_str, Some(&full_path_str), None);

            // Filter by scope if needed
            if let ScanScope::Categories(ref allowed_cats) = config.scope {
                if !allowed_cats.contains(&classified.category) {
                    continue;
                }
            }

            // Small file folding check
            if config.collapse_threshold_bytes > 0 && size < config.collapse_threshold_bytes {
                collapsed_count += 1;
                collapsed_logical += size;
                collapsed_physical += physical;
            } else {
                let flags = if classified.is_system_path {
                    FLAG_PROTECTED
                } else {
                    FLAG_NONE
                };

                tree.add_node(
                    dir_node_id,
                    &name_str,
                    NodeKind::File,
                    classified.category,
                    size,
                    physical,
                    mtime,
                    flags,
                );
            }

            dir_logical += size;
            dir_physical += physical;
            dir_files += 1;

            progress.scanned_bytes += size;
            progress.scanned_files += 1;

            if progress.scanned_files % 100 == 0 {
                progress.current_path = full_path_str.into_owned();
                progress_cb(progress);
            }
        }
    }

    // Add collapsed node if any small files were grouped
    if collapsed_count > 0 {
        tree.add_node(
            dir_node_id,
            &format!("<{} small files>", collapsed_count),
            NodeKind::CollapsedSmallFiles,
            FileCategory::Other,
            collapsed_logical,
            collapsed_physical,
            current_time_sec(),
            FLAG_COLLAPSED,
        );
    }

    // Accumulate locally
    tree.nodes[dir_node_id as usize].size_logical += dir_logical;
    tree.nodes[dir_node_id as usize].size_physical += dir_physical;
    tree.nodes[dir_node_id as usize].file_count += dir_files;
    tree.nodes[dir_node_id as usize].dir_count += dir_subdirs;

    // Recurse into subdirectories
    for (sub_path, sub_id) in subdirs_to_visit {
        scan_dir_recursive(
            &sub_path,
            sub_id,
            tree,
            config,
            cancel_flag,
            pause_flag,
            progress,
            progress_cb,
        )?;
    }

    // After finishing all children, propagate up to parent
    let node = &tree.nodes[dir_node_id as usize];
    let parent = node.parent;
    if parent != NO_NODE {
        let logical = node.size_logical;
        let physical = node.size_physical;
        let files = node.file_count;
        let dirs = node.dir_count;
        tree.upward_add(parent, logical, physical, files, dirs);
    }

    Ok(())
}

/// Scan using a host-provided DirLister (e.g. SAF or MediaStore).
pub fn scan_platform_lister<F>(
    lister: &dyn DirLister,
    root_handle: &str,
    config: &ScanConfig,
    cancel_flag: Option<&Arc<AtomicBool>>,
    pause_flag: Option<&Arc<AtomicBool>>,
    mut progress_cb: F,
) -> Result<ScanTree, ScanError>
where
    F: FnMut(&ScanProgress),
{
    let mut tree = ScanTree::new();
    let root_id = tree.add_node(
        NO_NODE,
        root_handle,
        NodeKind::Directory,
        FileCategory::Other,
        0,
        0,
        current_time_sec(),
        FLAG_NONE,
    );
    tree.root = root_id;

    let mut progress = ScanProgress {
        scanned_bytes: 0,
        scanned_files: 0,
        current_path: root_handle.to_string(),
    };

    let mut queue = std::collections::VecDeque::new();
    match &config.scope {
        ScanScope::Folders(folders) if !folders.is_empty() => {
            for f in folders {
                let name = f
                    .split(['/', '\\'])
                    .filter(|s| !s.is_empty())
                    .last()
                    .unwrap_or(f.as_str());
                let sub_id = tree.add_node(
                    root_id,
                    name,
                    NodeKind::Directory,
                    FileCategory::Other,
                    0,
                    0,
                    current_time_sec(),
                    FLAG_NONE,
                );
                tree.nodes[root_id as usize].dir_count += 1;
                queue.push_back((sub_id, f.clone()));
            }
        }
        _ => {
            queue.push_back((root_id, root_handle.to_string()));
        }
    }
    let mut counter = 0usize;

    while let Some((parent_id, handle)) = queue.pop_front() {
        if let Some(flag) = cancel_flag {
            if flag.load(Ordering::Relaxed) {
                return Err(ScanError::Cancelled);
            }
        }

        if let Some(flag) = pause_flag {
            while flag.load(Ordering::Relaxed) {
                std::thread::sleep(std::time::Duration::from_millis(100));
                if let Some(c_flag) = cancel_flag {
                    if c_flag.load(Ordering::Relaxed) {
                        return Err(ScanError::Cancelled);
                    }
                }
            }
        }

        progress.current_path = handle.clone();

        let entries = match lister.list_dir(&handle) {
            Ok(e) => e,
            Err(_) => continue,
        };

        let mut collapsed_count = 0u32;
        let mut collapsed_logical = 0u64;
        let mut collapsed_physical = 0u64;

        for entry in entries {
            if let Some(flag) = cancel_flag {
                if flag.load(Ordering::Relaxed) {
                    return Err(ScanError::Cancelled);
                }
            }

            if let Some(flag) = pause_flag {
                while flag.load(Ordering::Relaxed) {
                    std::thread::sleep(std::time::Duration::from_millis(100));
                    if let Some(c_flag) = cancel_flag {
                        if c_flag.load(Ordering::Relaxed) {
                            return Err(ScanError::Cancelled);
                        }
                    }
                }
            }

            if entry.is_dir {
                let sub_id = tree.add_node(
                    parent_id,
                    &entry.name,
                    NodeKind::Directory,
                    FileCategory::Other,
                    0,
                    0,
                    entry.mtime_sec,
                    FLAG_NONE,
                );
                tree.nodes[parent_id as usize].dir_count += 1;

                let child_handle = if handle.ends_with('/') {
                    format!("{}{}", handle, entry.name)
                } else {
                    format!("{}/{}", handle, entry.name)
                };
                queue.push_back((sub_id, child_handle));
            } else {
                let classified = classify(&entry.name, None, entry.mime_type.as_deref());

                if let ScanScope::Categories(ref allowed_cats) = config.scope {
                    if !allowed_cats.contains(&classified.category) {
                        continue;
                    }
                }

                let size = entry.size_bytes;
                let physical = ((size + 4095) / 4096) * 4096;

                if config.collapse_threshold_bytes > 0 && size < config.collapse_threshold_bytes {
                    collapsed_count += 1;
                    collapsed_logical += size;
                    collapsed_physical += physical;
                } else {
                    let flags = if classified.is_system_path {
                        FLAG_PROTECTED
                    } else {
                        FLAG_NONE
                    };

                    tree.add_node(
                        parent_id,
                        &entry.name,
                        NodeKind::File,
                        classified.category,
                        size,
                        physical,
                        entry.mtime_sec,
                        flags,
                    );
                    tree.upward_add(parent_id, size, physical, 1, 0);
                    progress.scanned_bytes += size;
                    progress.scanned_files += 1;
                }
            }

            counter += 1;
            if counter % 32 == 0 {
                progress_cb(&progress);
            }
        }

        // Add collapsed group if any
        if collapsed_count > 0 {
            let col_name = format!("<{} small items>", collapsed_count);
            tree.add_node(
                parent_id,
                &col_name,
                NodeKind::File,
                FileCategory::Other,
                collapsed_logical,
                collapsed_physical,
                current_time_sec(),
                FLAG_COLLAPSED,
            );
            tree.upward_add(
                parent_id,
                collapsed_logical,
                collapsed_physical,
                collapsed_count,
                0,
            );
            progress.scanned_bytes += collapsed_logical;
            progress.scanned_files += collapsed_count;
        }

        progress_cb(&progress);
    }

    if let Some(metrics) = config.device_metrics {
        add_pseudo_nodes(&mut tree, root_id, metrics);
    }

    tree.sort_tree_by_size(root_id);
    Ok(tree)
}

fn add_pseudo_nodes(tree: &mut ScanTree, root_id: u32, metrics: DeviceStorageMetrics) {
    let scanned_physical = tree.nodes[root_id as usize].size_physical;
    let total = metrics.total_bytes;
    let free = metrics.free_bytes;
    let used = total.saturating_sub(free);

    // Free space
    if free > 0 {
        let free_id = tree.add_node(
            root_id,
            "<Free Space>",
            NodeKind::FreeSpace,
            FileCategory::System,
            free,
            free,
            current_time_sec(),
            FLAG_PROTECTED,
        );
        tree.free_node = Some(free_id);
    }

    // System & Unknown differential
    if used > scanned_physical {
        let unknown_bytes = used - scanned_physical;
        let unknown_id = tree.add_node(
            root_id,
            "<System / Private App Data>",
            NodeKind::Unknown,
            FileCategory::System,
            unknown_bytes,
            unknown_bytes,
            current_time_sec(),
            FLAG_PROTECTED,
        );
        tree.unknown_node = Some(unknown_id);
    }
}

fn current_time_sec() -> u32 {
    SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .map(|d| d.as_secs() as u32)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::File;
    use std::io::Write;

    #[test]
    fn test_scan_native_directory() {
        let tmp = std::env::temp_dir().join(format!("ss-scan-{}", std::process::id()));
        let sub = tmp.join("subfolder");
        let _ = std::fs::create_dir_all(&sub);

        let f1 = tmp.join("video.mp4");
        let f2 = sub.join("doc.pdf");
        let f3 = sub.join("tiny.txt");

        File::create(&f1).unwrap().write_all(&vec![0xAA; 500_000]).unwrap();
        File::create(&f2).unwrap().write_all(&vec![0xBB; 300_000]).unwrap();
        File::create(&f3).unwrap().write_all(b"hello").unwrap(); // < 256KB, will be folded

        let mut config = ScanConfig::default();
        config.root_path = tmp.to_string_lossy().into_owned();
        config.collapse_threshold_bytes = 262_144;

        let tree = scan_native(&config, None, None, |_| {}).expect("scan");

        let root = tree.root;
        assert_ne!(root, NO_NODE);
        assert!(tree.nodes[root as usize].size_physical >= 800_000);
        assert_eq!(tree.nodes[root as usize].file_count, 3);

        // Check category stats
        let stats = tree.category_stats(root);
        let video_stat = stats.iter().find(|s| s.0 == FileCategory::Video).unwrap();
        assert_eq!(video_stat.2, 1);

        let _ = std::fs::remove_dir_all(&tmp);
    }

    #[test]
    fn test_scan_native_cancellation() {
        let tmp = std::env::temp_dir().join(format!("ss-scan-cancel-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&tmp);
        let f1 = tmp.join("test.bin");
        File::create(&f1).unwrap().write_all(&vec![1; 1000]).unwrap();

        let mut config = ScanConfig::default();
        config.root_path = tmp.to_string_lossy().into_owned();

        let cancel = Arc::new(AtomicBool::new(true));
        let res = scan_native(&config, Some(&cancel), None, |_| {});
        assert!(matches!(res, Err(ScanError::Cancelled)));

        let _ = std::fs::remove_dir_all(&tmp);
    }

    struct MockLister {
        entries: Vec<ss_platform::RawDirEntry>,
    }

    impl DirLister for MockLister {
        fn list_dir(&self, _handle: &str) -> Result<Vec<ss_platform::RawDirEntry>, String> {
            Ok(self.entries.clone())
        }
    }

    #[test]
    fn test_scan_platform_lister_basic_and_cancellation() {
        let lister = MockLister {
            entries: vec![
                ss_platform::RawDirEntry {
                    name: "photo.jpg".into(),
                    is_dir: false,
                    size_bytes: 5000,
                    mtime_sec: 100,
                    mime_type: Some("image/jpeg".into()),
                },
                ss_platform::RawDirEntry {
                    name: "video.mp4".into(),
                    is_dir: false,
                    size_bytes: 50000,
                    mtime_sec: 200,
                    mime_type: Some("video/mp4".into()),
                },
            ],
        };

        let mut config = ScanConfig::default();
        config.collapse_threshold_bytes = 0;

        let tree = scan_platform_lister(&lister, "content://test", &config, None, None, |_| {}).expect("scan lister");
        assert_eq!(tree.nodes[tree.root as usize].file_count, 2);
        assert_eq!(tree.nodes[tree.root as usize].size_physical, 61440);

        // Cancel test
        let cancel = Arc::new(AtomicBool::new(true));
        let res = scan_platform_lister(&lister, "content://test", &config, Some(&cancel), None, |_| {});
        assert!(matches!(res, Err(ScanError::Cancelled)));
    }

    #[test]
    fn test_scan_pseudo_nodes() {
        let lister = MockLister {
            entries: vec![
                ss_platform::RawDirEntry {
                    name: "file.bin".into(),
                    is_dir: false,
                    size_bytes: 1000,
                    mtime_sec: 100,
                    mime_type: None,
                },
            ],
        };

        let mut config = ScanConfig::default();
        config.collapse_threshold_bytes = 0;
        config.device_metrics = Some(DeviceStorageMetrics {
            total_bytes: 10_000_000,
            free_bytes: 2_000_000,
            apps_bytes: 0,
        });

        let tree = scan_platform_lister(&lister, "root", &config, None, None, |_| {}).expect("scan pseudo");
        assert!(tree.free_node.is_some());
        assert!(tree.unknown_node.is_some());

        let free_node = tree.free_node.unwrap();
        assert_eq!(tree.node_name(free_node), "<Free Space>");
        assert_eq!(tree.nodes[free_node as usize].flags & FLAG_PROTECTED, FLAG_PROTECTED);

        let unk_node = tree.unknown_node.unwrap();
        assert_eq!(tree.node_name(unk_node), "<System / Private App Data>");
        assert_eq!(tree.nodes[unk_node as usize].flags & FLAG_PROTECTED, FLAG_PROTECTED);
    }
}
