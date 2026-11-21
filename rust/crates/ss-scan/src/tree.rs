use serde::{Deserialize, Serialize};
use ss_classify::FileCategory;

pub const NO_NODE: u32 = u32::MAX;

pub const FLAG_NONE: u16 = 0;
pub const FLAG_SCAN_ERROR: u16 = 1 << 0;
pub const FLAG_SYMLINK: u16 = 1 << 1;
pub const FLAG_PROTECTED: u16 = 1 << 2;
pub const FLAG_DELETED: u16 = 1 << 3;
pub const FLAG_COLLAPSED: u16 = 1 << 4;

/// Kind of an item in the scan tree.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[repr(u8)]
pub enum NodeKind {
    Directory = 1,
    File = 2,
    FreeSpace = 3,
    Unknown = 4,
    CollapsedSmallFiles = 5,
}

impl NodeKind {
    pub fn is_dir(&self) -> bool {
        matches!(self, NodeKind::Directory | NodeKind::CollapsedSmallFiles)
    }
}

/// Compact node record stored in the arena (52 bytes).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Node {
    pub parent: u32,
    pub first_child: u32,
    pub next_sibling: u32,
    pub name_offset: u32,
    pub name_len: u16,
    pub kind: NodeKind,
    pub category: FileCategory,
    pub flags: u16,
    pub size_logical: u64,
    pub size_physical: u64,
    pub file_count: u32,
    pub dir_count: u32,
    pub mtime: u32,
}

/// Compact arena tree representing scanned filesystem hierarchy.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScanTree {
    pub nodes: Vec<Node>,
    pub names: Vec<u8>,
    pub root: u32,
    pub free_node: Option<u32>,
    pub unknown_node: Option<u32>,
}

impl Default for ScanTree {
    fn default() -> Self {
        Self::new()
    }
}

impl ScanTree {
    pub fn new() -> Self {
        Self {
            nodes: Vec::with_capacity(1024),
            names: Vec::with_capacity(32768),
            root: NO_NODE,
            free_node: None,
            unknown_node: None,
        }
    }

    /// Add a new node to the tree.
    pub fn add_node(
        &mut self,
        parent: u32,
        name: &str,
        kind: NodeKind,
        category: FileCategory,
        size_logical: u64,
        size_physical: u64,
        mtime: u32,
        flags: u16,
    ) -> u32 {
        let name_bytes = name.as_bytes();
        let name_offset = self.names.len() as u32;
        let name_len = name_bytes.len().min(u16::MAX as usize) as u16;
        self.names.extend_from_slice(&name_bytes[..name_len as usize]);

        let id = self.nodes.len() as u32;
        let node = Node {
            parent,
            first_child: NO_NODE,
            next_sibling: NO_NODE,
            name_offset,
            name_len,
            kind,
            category,
            flags,
            size_logical,
            size_physical,
            file_count: if kind == NodeKind::File { 1 } else { 0 },
            dir_count: if kind == NodeKind::Directory { 1 } else { 0 },
            mtime,
        };

        self.nodes.push(node);

        if parent != NO_NODE && (parent as usize) < self.nodes.len() {
            // Prepend or append to siblings list
            let prev_first = self.nodes[parent as usize].first_child;
            self.nodes[id as usize].next_sibling = prev_first;
            self.nodes[parent as usize].first_child = id;
        }

        id
    }

    /// Get name of a node.
    pub fn node_name(&self, id: u32) -> &str {
        if let Some(node) = self.nodes.get(id as usize) {
            let start = node.name_offset as usize;
            let end = start + node.name_len as usize;
            if end <= self.names.len() {
                if let Ok(s) = std::str::from_utf8(&self.names[start..end]) {
                    return s;
                }
            }
        }
        ""
    }

    /// Get the full path by climbing parents.
    pub fn node_path(&self, id: u32) -> String {
        let mut parts = Vec::new();
        let mut curr = id;
        while curr != NO_NODE && (curr as usize) < self.nodes.len() {
            let name = self.node_name(curr);
            if !name.is_empty() {
                parts.push(name);
            }
            curr = self.nodes[curr as usize].parent;
        }

        parts.reverse();
        if parts.is_empty() {
            return String::new();
        }
        let mut pb = std::path::PathBuf::from(parts[0]);
        for p in &parts[1..] {
            pb.push(p);
        }
        pb.to_string_lossy().replace('\\', "/")
    }

    /// List immediate active (non-deleted) children of a node.
    pub fn children(&self, id: u32) -> Vec<u32> {
        let mut res = Vec::new();
        if let Some(node) = self.nodes.get(id as usize) {
            let mut curr = node.first_child;
            while curr != NO_NODE && (curr as usize) < self.nodes.len() {
                let child = &self.nodes[curr as usize];
                if (child.flags & FLAG_DELETED) == 0 {
                    res.push(curr);
                }
                curr = child.next_sibling;
            }
        }
        res
    }

    /// Upward accumulation of sizes and file/dir counts to all ancestor nodes.
    pub fn upward_add(&mut self, start_parent: u32, logical: u64, physical: u64, files: u32, dirs: u32) {
        let mut curr = start_parent;
        while curr != NO_NODE && (curr as usize) < self.nodes.len() {
            let node = &mut self.nodes[curr as usize];
            node.size_logical = node.size_logical.saturating_add(logical);
            node.size_physical = node.size_physical.saturating_add(physical);
            node.file_count = node.file_count.saturating_add(files);
            node.dir_count = node.dir_count.saturating_add(dirs);
            curr = node.parent;
        }
    }

    /// Upward subtraction of sizes when an item is deleted or compressed.
    pub fn upward_subtract(&mut self, start_parent: u32, logical: u64, physical: u64, files: u32, dirs: u32) {
        let mut curr = start_parent;
        while curr != NO_NODE && (curr as usize) < self.nodes.len() {
            let node = &mut self.nodes[curr as usize];
            node.size_logical = node.size_logical.saturating_sub(logical);
            node.size_physical = node.size_physical.saturating_sub(physical);
            node.file_count = node.file_count.saturating_sub(files);
            node.dir_count = node.dir_count.saturating_sub(dirs);
            curr = node.parent;
        }
    }

    /// Sort all child lists descending by physical size (and recursively for subdirectories).
    pub fn sort_tree_by_size(&mut self, node_id: u32) {
        let mut child_ids = self.children(node_id);
        if child_ids.is_empty() {
            return;
        }

        // Sort descending by physical size
        child_ids.sort_by(|&a, &b| {
            let sa = self.nodes[a as usize].size_physical;
            let sb = self.nodes[b as usize].size_physical;
            sb.cmp(&sa)
        });

        // Re-link siblings
        for i in 0..child_ids.len() {
            let curr = child_ids[i];
            let next = if i + 1 < child_ids.len() {
                child_ids[i + 1]
            } else {
                NO_NODE
            };
            self.nodes[curr as usize].next_sibling = next;
        }

        if let Some(first) = child_ids.first() {
            self.nodes[node_id as usize].first_child = *first;
        }

        // Recurse for directories
        for cid in child_ids {
            if self.nodes[cid as usize].kind.is_dir() {
                self.sort_tree_by_size(cid);
            }
        }
    }

    /// Mark removed nodes as deleted and subtract sizes upwards.
    pub fn apply_removed(&mut self, removed_ids: &[u32]) {
        for &id in removed_ids {
            if (id as usize) >= self.nodes.len() {
                continue;
            }
            if (self.nodes[id as usize].flags & FLAG_DELETED) != 0 {
                continue;
            }

            let logical = self.nodes[id as usize].size_logical;
            let physical = self.nodes[id as usize].size_physical;
            let files = self.nodes[id as usize].file_count;
            let dirs = self.nodes[id as usize].dir_count;
            let parent = self.nodes[id as usize].parent;

            self.nodes[id as usize].flags |= FLAG_DELETED;
            self.upward_subtract(parent, logical, physical, files, dirs);
        }
    }

    /// Aggregate category statistics under a given node.
    pub fn category_stats(&self, root_id: u32) -> Vec<(FileCategory, u64, u32)> {
        let mut stats = [ (0u64, 0u32); 9 ]; // index 1..8

        let mut stack = vec![root_id];
        while let Some(curr) = stack.pop() {
            if (curr as usize) >= self.nodes.len() {
                continue;
            }
            let node = &self.nodes[curr as usize];
            if (node.flags & FLAG_DELETED) != 0 {
                continue;
            }

            if node.kind == NodeKind::File {
                let cat_idx = node.category.as_u8() as usize;
                if cat_idx < stats.len() {
                    stats[cat_idx].0 += node.size_physical;
                    stats[cat_idx].1 += 1;
                }
            }

            let mut child = node.first_child;
            while child != NO_NODE && (child as usize) < self.nodes.len() {
                stack.push(child);
                child = self.nodes[child as usize].next_sibling;
            }
        }

        FileCategory::ALL
            .iter()
            .map(|&cat| {
                let idx = cat.as_u8() as usize;
                let (bytes, count) = stats[idx];
                (cat, bytes, count)
            })
            .collect()
    }

    /// Find top N largest files in the tree.
    pub fn top_files(&self, root_id: u32, min_size: u64, limit: usize) -> Vec<u32> {
        let mut files = Vec::new();
        let mut stack = vec![root_id];

        while let Some(curr) = stack.pop() {
            if (curr as usize) >= self.nodes.len() {
                continue;
            }
            let node = &self.nodes[curr as usize];
            if (node.flags & FLAG_DELETED) != 0 {
                continue;
            }

            if node.kind == NodeKind::File && node.size_physical >= min_size {
                files.push(curr);
            }

            let mut child = node.first_child;
            while child != NO_NODE && (child as usize) < self.nodes.len() {
                stack.push(child);
                child = self.nodes[child as usize].next_sibling;
            }
        }

        files.sort_by(|&a, &b| {
            self.nodes[b as usize]
                .size_physical
                .cmp(&self.nodes[a as usize].size_physical)
        });

        if files.len() > limit {
            files.truncate(limit);
        }
        files
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_tree_construction_and_rollup() {
        let mut tree = ScanTree::new();
        let root = tree.add_node(
            NO_NODE,
            "root",
            NodeKind::Directory,
            FileCategory::Other,
            0,
            0,
            100,
            FLAG_NONE,
        );
        tree.root = root;

        let dir1 = tree.add_node(
            root,
            "DCIM",
            NodeKind::Directory,
            FileCategory::Image,
            0,
            0,
            100,
            FLAG_NONE,
        );

        let f1 = tree.add_node(
            dir1,
            "photo.jpg",
            NodeKind::File,
            FileCategory::Image,
            5000,
            8192,
            100,
            FLAG_NONE,
        );

        let f2 = tree.add_node(
            dir1,
            "video.mp4",
            NodeKind::File,
            FileCategory::Video,
            15000,
            16384,
            100,
            FLAG_NONE,
        );

        tree.upward_add(dir1, 5000 + 15000, 8192 + 16384, 2, 0);

        assert_eq!(tree.nodes[dir1 as usize].size_physical, 24576);
        assert_eq!(tree.nodes[root as usize].size_physical, 24576);
        assert_eq!(tree.nodes[root as usize].file_count, 2);

        // Path test
        assert_eq!(tree.node_path(f1), "root/DCIM/photo.jpg");

        // Apply removed test
        tree.apply_removed(&[f1]);
        assert_eq!(tree.nodes[dir1 as usize].size_physical, 16384);
        assert_eq!(tree.nodes[root as usize].size_physical, 16384);
        assert_eq!(tree.nodes[root as usize].file_count, 1);
        let active = tree.children(dir1);
        assert_eq!(active, vec![f2]);
    }

    #[test]
    fn test_tree_sort_by_size() {
        let mut tree = ScanTree::new();
        let root = tree.add_node(NO_NODE, "root", NodeKind::Directory, FileCategory::Other, 0, 0, 100, FLAG_NONE);
        tree.root = root;

        let small = tree.add_node(root, "small.txt", NodeKind::File, FileCategory::Document, 100, 100, 100, FLAG_NONE);
        let big = tree.add_node(root, "big.mp4", NodeKind::File, FileCategory::Video, 5000, 5000, 100, FLAG_NONE);
        let medium = tree.add_node(root, "medium.jpg", NodeKind::File, FileCategory::Image, 1000, 1000, 100, FLAG_NONE);

        tree.sort_tree_by_size(root);
        let ch = tree.children(root);
        assert_eq!(ch, vec![big, medium, small]);
    }

    #[test]
    fn test_tree_top_large_files() {
        let mut tree = ScanTree::new();
        let root = tree.add_node(NO_NODE, "root", NodeKind::Directory, FileCategory::Other, 0, 0, 100, FLAG_NONE);
        tree.root = root;

        for i in 1..=10 {
            tree.add_node(root, &format!("f_{}.bin", i), NodeKind::File, FileCategory::Other, i * 100, i * 100, 100, FLAG_NONE);
        }

        let top3_ids = tree.top_files(root, 0, 3);
        assert_eq!(top3_ids.len(), 3);
        assert_eq!(tree.nodes[top3_ids[0] as usize].size_physical, 1000);
        assert_eq!(tree.nodes[top3_ids[1] as usize].size_physical, 900);
        assert_eq!(tree.nodes[top3_ids[2] as usize].size_physical, 800);
    }

    #[test]
    fn test_tree_category_stats() {
        let mut tree = ScanTree::new();
        let root = tree.add_node(NO_NODE, "root", NodeKind::Directory, FileCategory::Other, 0, 0, 100, FLAG_NONE);
        tree.root = root;

        tree.add_node(root, "pic1.jpg", NodeKind::File, FileCategory::Image, 1000, 1000, 100, FLAG_NONE);
        tree.add_node(root, "pic2.png", NodeKind::File, FileCategory::Image, 2000, 2000, 100, FLAG_NONE);
        tree.add_node(root, "vid.mp4", NodeKind::File, FileCategory::Video, 5000, 5000, 100, FLAG_NONE);

        let stats = tree.category_stats(root);
        let img = stats.iter().find(|s| s.0 == FileCategory::Image).unwrap();
        let vid = stats.iter().find(|s| s.0 == FileCategory::Video).unwrap();
        assert_eq!(img.1, 3000);
        assert_eq!(img.2, 2);
        assert_eq!(vid.1, 5000);
        assert_eq!(vid.2, 1);
    }
}
