//! SpaceSlimmer classification engine: maps file names, extensions, MIME types,
//! and paths to unified categories, colors, and cleanup suggestions.

use serde::{Deserialize, Serialize};

/// Version string for the classification rule set.
pub const CLASSIFY_VERSION: &str = "classify_v1.0";

/// Top-level category for file storage analysis.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[repr(u8)]
pub enum FileCategory {
    Video = 1,
    Image = 2,
    Audio = 3,
    Archive = 4,
    Document = 5,
    Package = 6,
    System = 7,
    Other = 8,
}

impl FileCategory {
    pub const ALL: [FileCategory; 8] = [
        FileCategory::Video,
        FileCategory::Image,
        FileCategory::Audio,
        FileCategory::Archive,
        FileCategory::Document,
        FileCategory::Package,
        FileCategory::System,
        FileCategory::Other,
    ];

    pub fn as_u8(&self) -> u8 {
        *self as u8
    }

    pub fn from_u8(val: u8) -> Self {
        match val {
            1 => FileCategory::Video,
            2 => FileCategory::Image,
            3 => FileCategory::Audio,
            4 => FileCategory::Archive,
            5 => FileCategory::Document,
            6 => FileCategory::Package,
            7 => FileCategory::System,
            _ => FileCategory::Other,
        }
    }

    pub fn key(&self) -> &'static str {
        match self {
            FileCategory::Video => "video",
            FileCategory::Image => "image",
            FileCategory::Audio => "audio",
            FileCategory::Archive => "archive",
            FileCategory::Document => "document",
            FileCategory::Package => "package",
            FileCategory::System => "system",
            FileCategory::Other => "other",
        }
    }

    /// English display name.
    pub fn display_name(&self) -> &'static str {
        match self {
            FileCategory::Video => "Video",
            FileCategory::Image => "Image",
            FileCategory::Audio => "Audio",
            FileCategory::Archive => "Archive",
            FileCategory::Document => "Document",
            FileCategory::Package => "Installation Package",
            FileCategory::System => "System & Cache",
            FileCategory::Other => "Other",
        }
    }

    /// Default hex color code for UI rendering.
    pub fn default_color_hex(&self) -> &'static str {
        match self {
            FileCategory::Video => "#3F51B5",    // Indigo
            FileCategory::Image => "#009688",    // Teal
            FileCategory::Audio => "#FF9800",    // Orange
            FileCategory::Archive => "#795548",  // Brown
            FileCategory::Document => "#2196F3", // Blue
            FileCategory::Package => "#4CAF50",  // Green
            FileCategory::System => "#607D8B",   // Grey-Blue
            FileCategory::Other => "#9E9E9E",    // Slate Grey
        }
    }

    /// Whether files of this category are candidates for media compression.
    pub fn is_compressible(&self) -> bool {
        matches!(self, FileCategory::Video | FileCategory::Image | FileCategory::Audio)
    }

    /// Whether this category contains typically safe-to-clean junk or temporary files.
    pub fn is_junk_candidate(&self) -> bool {
        matches!(self, FileCategory::Package | FileCategory::System)
    }
}

/// Detailed classification result for a scanned file.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ClassifiedFile {
    pub category: FileCategory,
    pub extension: String,
    pub is_system_path: bool,
}

/// Classify a file given its name, optional full path, and optional MIME type.
pub fn classify(name: &str, path: Option<&str>, mime_type: Option<&str>) -> ClassifiedFile {
    let lower_name = name.to_ascii_lowercase();

    // 1. Path-based system/cache check
    let is_system_path = if let Some(p) = path {
        let p_lower = p.to_ascii_lowercase();
        p_lower.contains("/.thumbnails")
            || p_lower.contains("\\.thumbnails")
            || p_lower.contains("/cache/")
            || p_lower.contains("\\cache\\")
            || p_lower.contains("/tmp/")
            || p_lower.contains("\\tmp\\")
            || p_lower.ends_with(".log")
            || p_lower.ends_with(".tmp")
            || p_lower.ends_with(".cache")
    } else {
        false
    };

    if is_system_path {
        let ext = extract_extension(&lower_name);
        return ClassifiedFile {
            category: FileCategory::System,
            extension: ext,
            is_system_path: true,
        };
    }

    // 2. MIME type check if supplied
    if let Some(mime) = mime_type {
        let m = mime.trim().to_ascii_lowercase();
        if m.starts_with("video/") {
            return ClassifiedFile {
                category: FileCategory::Video,
                extension: extract_extension(&lower_name),
                is_system_path: false,
            };
        } else if m.starts_with("image/") {
            return ClassifiedFile {
                category: FileCategory::Image,
                extension: extract_extension(&lower_name),
                is_system_path: false,
            };
        } else if m.starts_with("audio/") {
            return ClassifiedFile {
                category: FileCategory::Audio,
                extension: extract_extension(&lower_name),
                is_system_path: false,
            };
        } else if m == "application/vnd.android.package-archive" {
            return ClassifiedFile {
                category: FileCategory::Package,
                extension: extract_extension(&lower_name),
                is_system_path: false,
            };
        } else if m.contains("zip") || m.contains("tar") || m.contains("compressed") || m.contains("7z") {
            return ClassifiedFile {
                category: FileCategory::Archive,
                extension: extract_extension(&lower_name),
                is_system_path: false,
            };
        } else if m.contains("pdf") || m.contains("msword") || m.contains("officedocument") || m.starts_with("text/") {
            return ClassifiedFile {
                category: FileCategory::Document,
                extension: extract_extension(&lower_name),
                is_system_path: false,
            };
        }
    }

    // 3. Extension lookup
    let ext = extract_extension(&lower_name);
    let category = match ext.as_str() {
        // Video
        "mp4" | "m4v" | "mov" | "mkv" | "avi" | "3gp" | "webm" | "flv" | "wmv" | "ts" | "mts"
        | "m2ts" | "vob" | "rmvb" | "rm" | "asf" | "mpg" | "mpeg" | "divx" | "ogv" => {
            FileCategory::Video
        }

        // Image
        "jpg" | "jpeg" | "png" | "webp" | "heic" | "heif" | "gif" | "bmp" | "tiff" | "tif"
        | "svg" | "ico" | "raw" | "dng" | "cr2" | "nef" | "arw" | "psd" | "avif" => {
            FileCategory::Image
        }

        // Audio
        "mp3" | "m4a" | "aac" | "wav" | "flac" | "ogg" | "opus" | "amr" | "wma" | "ape"
        | "alac" | "mid" | "midi" | "ac3" | "dts" | "mka" => FileCategory::Audio,

        // Packages (Android / apps)
        "apk" | "xapk" | "apks" | "aab" => FileCategory::Package,

        // Archives
        "zip" | "rar" | "7z" | "tar" | "gz" | "bz2" | "xz" | "tgz" | "iso" | "dmg" | "z" => {
            FileCategory::Archive
        }

        // Documents
        "pdf" | "doc" | "docx" | "xls" | "xlsx" | "ppt" | "pptx" | "txt" | "md" | "rtf"
        | "csv" | "epub" | "mobi" | "html" | "htm" | "xml" | "json" => FileCategory::Document,

        // System / Temp
        "tmp" | "temp" | "log" | "bak" | "swp" | "cache" | "thumb" => FileCategory::System,

        _ => FileCategory::Other,
    };

    ClassifiedFile {
        category,
        extension: ext,
        is_system_path: false,
    }
}

fn extract_extension(filename: &str) -> String {
    if let Some(idx) = filename.rfind('.') {
        if idx > 0 && idx + 1 < filename.len() {
            return filename[idx + 1..].to_string();
        }
    }
    String::new()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_video_classification() {
        let res = classify("VID_20261029_120000.MP4", None, None);
        assert_eq!(res.category, FileCategory::Video);
        assert_eq!(res.extension, "mp4");
        assert!(res.category.is_compressible());
    }

    #[test]
    fn test_image_classification() {
        let res = classify("IMG_001.JPG", None, None);
        assert_eq!(res.category, FileCategory::Image);
        assert_eq!(res.extension, "jpg");

        let heic = classify("photo.heic", None, None);
        assert_eq!(heic.category, FileCategory::Image);
    }

    #[test]
    fn test_system_path_override() {
        let res = classify(
            "thumb_123.jpg",
            Some("/storage/emulated/0/DCIM/.thumbnails/thumb_123.jpg"),
            None,
        );
        assert_eq!(res.category, FileCategory::System);
        assert!(res.is_system_path);
    }

    #[test]
    fn test_mime_fallback() {
        let res = classify("no_ext_video", None, Some("video/mp4"));
        assert_eq!(res.category, FileCategory::Video);
    }

    #[test]
    fn test_apk_and_archive() {
        let apk = classify("app-release.apk", None, None);
        assert_eq!(apk.category, FileCategory::Package);
        assert!(apk.category.is_junk_candidate());

        let zip = classify("backup.tar.gz", None, None);
        assert_eq!(zip.category, FileCategory::Archive);
    }
}
