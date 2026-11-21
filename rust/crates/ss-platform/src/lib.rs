//! Platform capability traits. Concrete implementations live in Kotlin/Swift.
//!
//! Rust core calls into these traits for hardware encode, system state,
//! storage analytics, and notifications. Implementations must not synchronously
//! re-enter `CoreEngine`.

use serde::{Deserialize, Serialize};

/// Marker that a video recipe should be executed by the host encoder.
pub const VIDEO_BACKEND_HW: &str = "hw_video";

/// Marker that image work stays in Rust.
pub const IMAGE_BACKEND_RUST: &str = "rust_image";

/// Marker that audio work is mixed Rust + platform AAC.
pub const AUDIO_BACKEND_MIXED: &str = "rust_audio";

/// Point-in-time system snapshot used for thermal/power-aware scheduling.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct SystemInfoSnapshot {
    pub thermal_level: u8,
    pub battery_pct: u8,
    pub charging: bool,
    pub power_save: bool,
}

/// Host-provided system state. Android/iOS gateways implement this trait.
pub trait SystemInfo: Send + Sync {
    /// Returns the latest thermal, battery, and power-save hints.
    fn snapshot(&self) -> SystemInfoSnapshot;
}

/// Host-provided video transcoder. Media3 / AVFoundation implement this trait.
pub trait VideoTranscoder: Send + Sync {
    /// Starts a hardware transcode job. Progress is reported via `CoreObserver`.
    fn start(&self, job_id: &str, src: &str, dst: &str) -> Result<(), String>;
    /// Requests cancellation of an in-flight transcode.
    fn cancel(&self, job_id: &str);
}

/// Host-provided AAC encoder for formats Rust does not encode directly.
pub trait AacEncoder: Send + Sync {
    /// Encodes PCM or container input to AAC-in-M4A at the given bitrate.
    fn encode_to_m4a(&self, src: &str, dst: &str, bitrate_bps: u64) -> Result<(), String>;
}

/// Raw directory entry passed across platform boundary (e.g. from SAF or MediaStore).
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct RawDirEntry {
    pub name: String,
    pub is_dir: bool,
    pub size_bytes: u64,
    pub mtime_sec: u32,
    pub mime_type: Option<String>,
}

/// Device storage metrics returned by host.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct DeviceStorageMetrics {
    pub total_bytes: u64,
    pub free_bytes: u64,
    pub apps_bytes: u64,
}

/// Host-provided directory enumerator for non-standard / sandboxed paths.
pub trait DirLister: Send + Sync {
    /// List entries under a folder handle (URI or path).
    fn list_dir(&self, handle: &str) -> Result<Vec<RawDirEntry>, String>;
}

/// Host-provided storage info provider.
pub trait StorageInfoProvider: Send + Sync {
    fn query_storage(&self) -> Result<DeviceStorageMetrics, String>;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn backend_markers_are_stable() {
        assert_eq!(super::VIDEO_BACKEND_HW, "hw_video");
    }
}
