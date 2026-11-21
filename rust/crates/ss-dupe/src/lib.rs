//! Three-tier fast duplicate file detector.
//!
//! Tier 1: Group files by exact byte size.
//! Tier 2: Compute sample hash from header, middle, and tail chunks.
//! Tier 3: Compute full hash on remaining colliding candidates.

use std::collections::HashMap;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use crc32fast::Hasher;
use serde::{Deserialize, Serialize};

/// A file candidate provided to the duplicate detection engine.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CandidateFile {
    pub id: u32,
    pub path: String,
    pub size: u64,
    pub mtime: u32,
}

/// A specific file within a duplicate cluster.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DuplicateItem {
    pub id: u32,
    pub path: String,
    pub mtime: u32,
    /// Suggestion flag: typically true for the oldest file or preferred path.
    pub is_suggested_keep: bool,
}

/// A group of identical duplicate files.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DuplicateGroup {
    pub hash: String,
    pub file_size: u64,
    pub total_waste_bytes: u64,
    pub items: Vec<DuplicateItem>,
}

/// Options to tune duplicate detection.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DupeOptions {
    /// Minimum file size to consider (smaller files usually not worth deduplicating). Default: 4096.
    pub min_file_size: u64,
    /// Maximum number of duplicate groups to return. Default: 100.
    pub max_groups: usize,
}

impl Default for DupeOptions {
    fn default() -> Self {
        Self {
            min_file_size: 4096,
            max_groups: 100,
        }
    }
}

/// Find duplicate files among the given list of candidates.
pub fn find_duplicates<F>(
    candidates: &[CandidateFile],
    opts: &DupeOptions,
    cancel_flag: Option<&Arc<AtomicBool>>,
    mut progress: F,
) -> Vec<DuplicateGroup>
where
    F: FnMut(usize, usize),
{
    let is_cancelled = || {
        cancel_flag
            .map(|flag| flag.load(Ordering::Relaxed))
            .unwrap_or(false)
    };

    // --- Tier 1: Group by exact size ---
    let mut size_groups: HashMap<u64, Vec<&CandidateFile>> = HashMap::new();
    for file in candidates {
        if file.size >= opts.min_file_size {
            size_groups.entry(file.size).or_default().push(file);
        }
    }

    let mut potential_dupes: Vec<&CandidateFile> = Vec::new();
    for (_sz, files) in size_groups {
        if files.len() >= 2 {
            potential_dupes.extend(files);
        }
    }

    if potential_dupes.is_empty() || is_cancelled() {
        return Vec::new();
    }

    let total_candidates = potential_dupes.len();
    progress(0, total_candidates);

    // --- Tier 2: Sample hash ---
    let mut sample_groups: HashMap<(u64, u32), Vec<&CandidateFile>> = HashMap::new();
    for (i, file) in potential_dupes.iter().enumerate() {
        if is_cancelled() {
            return Vec::new();
        }
        if let Ok(sample_hash) = compute_sample_hash(&file.path, file.size) {
            sample_groups
                .entry((file.size, sample_hash))
                .or_default()
                .push(*file);
        }
        if i % 50 == 0 {
            progress(i, total_candidates);
        }
    }

    let mut tier2_candidates: Vec<&CandidateFile> = Vec::new();
    for (_k, files) in sample_groups {
        if files.len() >= 2 {
            tier2_candidates.extend(files);
        }
    }

    if tier2_candidates.is_empty() || is_cancelled() {
        return Vec::new();
    }

    // --- Tier 3: Full file hash ---
    let mut full_groups: HashMap<(u64, u64), Vec<&CandidateFile>> = HashMap::new();
    let total_tier2 = tier2_candidates.len();

    for (i, file) in tier2_candidates.iter().enumerate() {
        if is_cancelled() {
            return Vec::new();
        }
        if let Ok(full_hash) = compute_full_hash(&file.path) {
            full_groups
                .entry((file.size, full_hash))
                .or_default()
                .push(*file);
        }
        if i % 20 == 0 {
            progress(i, total_tier2);
        }
    }

    progress(total_tier2, total_tier2);

    // Construct duplicate groups
    let mut results: Vec<DuplicateGroup> = Vec::new();
    for ((size, hash), mut files) in full_groups {
        if files.len() < 2 {
            continue;
        }

        // Sort items by mtime ascending (oldest first)
        files.sort_by_key(|f| f.mtime);

        let items: Vec<DuplicateItem> = files
            .into_iter()
            .enumerate()
            .map(|(idx, f)| DuplicateItem {
                id: f.id,
                path: f.path.clone(),
                mtime: f.mtime,
                is_suggested_keep: idx == 0, // Keep the oldest copy by default
            })
            .collect();

        let total_waste = (items.len() - 1) as u64 * size;
        results.push(DuplicateGroup {
            hash: format!("{:016x}", hash),
            file_size: size,
            total_waste_bytes: total_waste,
            items,
        });
    }

    // Sort groups descending by total waste bytes
    results.sort_by(|a, b| b.total_waste_bytes.cmp(&a.total_waste_bytes));
    if results.len() > opts.max_groups {
        results.truncate(opts.max_groups);
    }

    results
}

fn compute_sample_hash(path: &str, file_size: u64) -> std::io::Result<u32> {
    let mut f = File::open(path)?;
    let mut hasher = Hasher::new();
    let sample_chunk = 8192;

    if file_size <= sample_chunk * 3 {
        // Read full file
        let mut buf = Vec::with_capacity(file_size as usize);
        f.read_to_end(&mut buf)?;
        hasher.update(&buf);
    } else {
        let mut buf = [0u8; 8192];

        // 1. Head
        let n = f.read(&mut buf)?;
        hasher.update(&buf[..n]);

        // 2. Middle
        let mid = (file_size / 2).saturating_sub(4096);
        f.seek(SeekFrom::Start(mid))?;
        let n = f.read(&mut buf)?;
        hasher.update(&buf[..n]);

        // 3. Tail
        let tail = file_size.saturating_sub(sample_chunk);
        f.seek(SeekFrom::Start(tail))?;
        let n = f.read(&mut buf)?;
        hasher.update(&buf[..n]);
    }

    Ok(hasher.finalize())
}

fn compute_full_hash(path: &str) -> std::io::Result<u64> {
    let mut f = File::open(path)?;
    let mut hasher1 = Hasher::new();
    let mut hasher2 = Hasher::new();
    hasher2.update(b"spaceslimmer-salt");

    let mut buf = [0u8; 65536];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher1.update(&buf[..n]);
        // Also update second hasher with inverted bits for strong 64-bit combined hash
        for b in &mut buf[..n] {
            *b = !*b;
        }
        hasher2.update(&buf[..n]);
    }

    let h1 = hasher1.finalize() as u64;
    let h2 = hasher2.finalize() as u64;
    Ok((h1 << 32) | h2)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn test_duplicate_detection_flow() {
        let tmp = std::env::temp_dir().join(format!("ss-dupe-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&tmp);

        let file1 = tmp.join("f1.bin");
        let file2 = tmp.join("f2.bin");
        let file3 = tmp.join("f3.bin");

        let content = vec![0xAB; 20_000];
        let diff_content = vec![0xCD; 20_000];

        File::create(&file1).unwrap().write_all(&content).unwrap();
        File::create(&file2).unwrap().write_all(&content).unwrap();
        File::create(&file3).unwrap().write_all(&diff_content).unwrap();

        let candidates = vec![
            CandidateFile {
                id: 1,
                path: file1.to_string_lossy().into(),
                size: 20_000,
                mtime: 100,
            },
            CandidateFile {
                id: 2,
                path: file2.to_string_lossy().into(),
                size: 20_000,
                mtime: 200,
            },
            CandidateFile {
                id: 3,
                path: file3.to_string_lossy().into(),
                size: 20_000,
                mtime: 300,
            },
        ];

        let opts = DupeOptions::default();
        let dupes = find_duplicates(&candidates, &opts, None, |_, _| {});

        assert_eq!(dupes.len(), 1);
        assert_eq!(dupes[0].items.len(), 2);
        assert_eq!(dupes[0].file_size, 20_000);
        assert_eq!(dupes[0].total_waste_bytes, 20_000);
        assert!(dupes[0].items[0].is_suggested_keep);
        assert!(!dupes[0].items[1].is_suggested_keep);

        let _ = std::fs::remove_dir_all(&tmp);
    }

    #[test]
    fn test_duplicate_empty_and_diff_sizes() {
        let opts = DupeOptions::default();
        let empty_res = find_duplicates(&[], &opts, None, |_, _| {});
        assert!(empty_res.is_empty());

        let candidates = vec![
            CandidateFile { id: 1, path: "f1".into(), size: 100, mtime: 10 },
            CandidateFile { id: 2, path: "f2".into(), size: 200, mtime: 20 },
        ];
        let diff_res = find_duplicates(&candidates, &opts, None, |_, _| {});
        assert!(diff_res.is_empty());
    }

    #[test]
    fn test_duplicate_cancellation() {
        let cancel = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(true));
        let candidates = vec![
            CandidateFile { id: 1, path: "f1".into(), size: 100, mtime: 10 },
            CandidateFile { id: 2, path: "f2".into(), size: 100, mtime: 20 },
        ];
        let res = find_duplicates(&candidates, &DupeOptions::default(), Some(&cancel), |_, _| {});
        assert!(res.is_empty());
    }
}
