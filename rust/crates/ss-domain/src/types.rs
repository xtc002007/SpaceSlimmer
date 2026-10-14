//! Domain types for compression decisions. Pure data, no I/O.

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum MediaKind {
    Video,
    Image,
    Audio,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum QualityPreset {
    VideoExtreme,
    VideoHigh,
    VideoBalanced,
    VideoSmall,
    VideoTiny,
    VideoTargetSize,
    VideoCustom,
    ImageMax,
    ImageHigh,
    ImageSmall,
    AudioSpeech,
    AudioMusicHigh,
    AudioMusicSmall,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum Compatibility {
    Compatible,
    Efficient,
    MoreEfficient,
    KeepContainer,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum OutputPolicy {
    SaveAsNew,
    ReplaceAfterConfirm,
    ReplaceToVault,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum SkipReason {
    AlreadyEfficient,
    TooSmall,
    EstimateNotWorth,
    WouldGrow,
    UnsupportedHdr,
    UnsupportedFps,
    UnsupportedCodec,
    UserDisabled,
    TargetSizeUnreachable,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum TimeClass {
    Fast,
    Medium,
    PlugIn,
}

#[derive(Clone, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct MediaFingerprint {
    pub kind: MediaKind,
    pub mime: String,
    pub container: String,
    pub video_codec: Option<String>,
    pub audio_codec: Option<String>,
    pub width: u32,
    pub height: u32,
    pub duration_ms: u64,
    pub fps: Option<f32>,
    pub rotation: u16,
    pub file_bytes: u64,
    pub video_bitrate_bps: Option<u64>,
    pub audio_bitrate_bps: Option<u64>,
    pub has_alpha: bool,
    pub is_hdr: bool,
    pub is_slowmo: bool,
    pub is_live_photo: bool,
    pub channels: Option<u8>,
    pub sample_rate: Option<u32>,
}

#[derive(Clone, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct HwCaps {
    pub hevc_hw_encode: bool,
    pub av1_hw_encode: bool,
    pub hevc_max_w: u32,
    pub hevc_max_h: u32,
    pub av1_max_w: u32,
    pub av1_max_h: u32,
    pub max_image_side: u32,
}

impl Default for HwCaps {
    fn default() -> Self {
        Self {
            hevc_hw_encode: false,
            av1_hw_encode: false,
            hevc_max_w: 1920,
            hevc_max_h: 1080,
            av1_max_w: 0,
            av1_max_h: 0,
            max_image_side: 8192,
        }
    }
}

fn default_estimate_scale() -> f32 {
    1.0
}

#[derive(Clone, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct VideoCustomParams {
    pub max_long_edge: Option<u32>,
    pub fps_cap: Option<f32>,
    pub video_bitrate_bps: Option<u64>,
    pub audio_bitrate_bps: Option<u64>,
    pub codec: Option<String>,
    /// `Some(true)` copy audio; `Some(false)` re-encode; `None` auto.
    #[serde(default)]
    pub audio_copy: Option<bool>,
    #[serde(default)]
    pub trim_start_ms: Option<u64>,
    #[serde(default)]
    pub trim_end_ms: Option<u64>,
    /// When set, encoder may run a second pass if output exceeds target by 15%.
    #[serde(default)]
    pub strict_target: Option<bool>,
}

#[derive(Clone, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct CompressRequest {
    pub preset: QualityPreset,
    pub compatibility: Compatibility,
    pub output_policy: OutputPolicy,
    pub target_bytes: Option<u64>,
    pub custom: Option<VideoCustomParams>,
    pub keep_gps: bool,
    pub keep_hdr: bool,
    pub skip_enabled: bool,
    pub image_format: Option<String>,
    pub audio_format: Option<String>,
    pub skip_thresholds: SkipThresholds,
    /// Device EMA scale applied to estimates (`actual/predicted`). Default 1.0.
    #[serde(default = "default_estimate_scale")]
    pub estimate_scale: f32,
}

impl Default for CompressRequest {
    fn default() -> Self {
        Self {
            preset: QualityPreset::VideoBalanced,
            compatibility: Compatibility::Compatible,
            output_policy: OutputPolicy::SaveAsNew,
            target_bytes: None,
            custom: None,
            keep_gps: true,
            keep_hdr: false,
            skip_enabled: true,
            image_format: None,
            audio_format: None,
            skip_thresholds: SkipThresholds::default(),
            estimate_scale: 1.0,
        }
    }
}

#[derive(Clone, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct Recipe {
    pub kind: MediaKind,
    pub out_w: u32,
    pub out_h: u32,
    pub fps: Option<f32>,
    pub video_codec: String,
    pub video_bitrate_bps: u64,
    pub audio_copy: bool,
    pub audio_codec: String,
    pub audio_bitrate_bps: u64,
    pub container: String,
    pub image_out_format: Option<String>,
    pub image_quality: Option<u8>,
    pub max_side: Option<u32>,
    pub audio_out_format: Option<String>,
    pub audio_channels: Option<u8>,
    #[serde(default)]
    pub trim_start_ms: Option<u64>,
    #[serde(default)]
    pub trim_end_ms: Option<u64>,
    #[serde(default)]
    pub target_bytes: Option<u64>,
}

#[derive(Clone, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct Decision {
    pub skip: bool,
    pub skip_reason: Option<SkipReason>,
    pub recipe: Option<Recipe>,
    pub estimate_bytes_low: u64,
    pub estimate_bytes_high: u64,
    pub time_class: TimeClass,
}

#[derive(Clone, Copy, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct SkipThresholds {
    pub video_ratio: f32,
    pub video_abs_bytes: u64,
    pub image_ratio: f32,
    pub image_abs_bytes: u64,
}

impl Default for SkipThresholds {
    fn default() -> Self {
        Self {
            video_ratio: 0.08,
            video_abs_bytes: 5 * 1024 * 1024,
            image_ratio: 0.10,
            image_abs_bytes: 200 * 1024,
        }
    }
}
