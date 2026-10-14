//! UniFFI-facing value types mirrored from `ss-domain` and `ss-jobs`.

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum MediaKind {
    Video,
    Image,
    Audio,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum)]
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

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum)]
pub enum Compatibility {
    Compatible,
    Efficient,
    MoreEfficient,
    KeepContainer,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum OutputPolicy {
    SaveAsNew,
    ReplaceAfterConfirm,
    ReplaceToVault,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum JobState {
    Queued,
    Running,
    Paused,
    Cancelling,
    Succeeded,
    Skipped,
    Failed,
    CommittedReplace,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
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

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum FailReason {
    ProbeFailed,
    DecodeFailed,
    EncodeFailed,
    MuxFailed,
    DiskFull,
    Permission,
    Cancelled,
    InvalidOutput,
    PlatformCrash,
    Unknown,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum TimeClass {
    Fast,
    Medium,
    PlugIn,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
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

#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct HwCaps {
    pub hevc_hw_encode: bool,
    pub av1_hw_encode: bool,
    pub hevc_max_w: u32,
    pub hevc_max_h: u32,
    pub av1_max_w: u32,
    pub av1_max_h: u32,
    pub max_image_side: u32,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct VideoCustomParams {
    pub max_long_edge: Option<u32>,
    pub fps_cap: Option<f32>,
    pub video_bitrate_bps: Option<u64>,
    pub audio_bitrate_bps: Option<u64>,
    pub codec: Option<String>,
    pub audio_copy: Option<bool>,
    pub trim_start_ms: Option<u64>,
    pub trim_end_ms: Option<u64>,
    pub strict_target: Option<bool>,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
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
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
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
    pub trim_start_ms: Option<u64>,
    pub trim_end_ms: Option<u64>,
    pub target_bytes: Option<u64>,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct Decision {
    pub skip: bool,
    pub skip_reason: Option<SkipReason>,
    pub recipe: Option<Recipe>,
    pub estimate_bytes_low: u64,
    pub estimate_bytes_high: u64,
    pub time_class: TimeClass,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct JobSpec {
    pub id: String,
    pub display_name: String,
    pub source_uri: String,
    pub local_path: Option<String>,
    pub fingerprint: MediaFingerprint,
    pub request_override: Option<CompressRequest>,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct JobView {
    pub spec: JobSpec,
    pub state: JobState,
    pub progress: f32,
    pub decision: Option<Decision>,
    pub output_uri: Option<String>,
    pub output_bytes: Option<u64>,
    pub saved_bytes: Option<i64>,
    pub fail: Option<FailReason>,
    pub skip: Option<SkipReason>,
    pub message: String,
    pub updated_at: i64,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct GroupView {
    pub id: String,
    pub created_at: i64,
    pub request: CompressRequest,
    pub jobs: Vec<JobView>,
    pub running: u32,
    pub done: u32,
    pub failed: u32,
    pub skipped: u32,
    pub saved_bytes: i64,
    pub output_policy: OutputPolicy,
}

#[derive(Clone, Debug, PartialEq, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct Settings {
    pub default_preset_video: QualityPreset,
    pub default_preset_image: QualityPreset,
    pub default_preset_audio: QualityPreset,
    pub default_image_format: String,
    pub default_compat: Compatibility,
    pub skip_enabled: bool,
    pub video_skip_ratio: f32,
    pub video_skip_abs: u64,
    pub image_skip_ratio: f32,
    pub image_skip_abs: u64,
    pub keep_gps: bool,
    pub vault_days: u32,
    pub auto_delete_vault: bool,
    pub parallel_profile: ParallelProfile,
    pub language: String,
    #[serde(default = "default_estimate_scale")]
    pub estimate_scale_image: f32,
    #[serde(default = "default_estimate_scale")]
    pub estimate_scale_video: f32,
    #[serde(default = "default_estimate_scale")]
    pub estimate_scale_audio: f32,
    #[serde(default)]
    pub idle_charge_only: bool,
}

fn default_estimate_scale() -> f32 {
    1.0
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            default_preset_video: QualityPreset::VideoBalanced,
            default_preset_image: QualityPreset::ImageHigh,
            default_preset_audio: QualityPreset::AudioSpeech,
            default_image_format: "jpeg".into(),
            default_compat: Compatibility::Compatible,
            skip_enabled: true,
            video_skip_ratio: 0.08,
            video_skip_abs: 5 * 1024 * 1024,
            image_skip_ratio: 0.10,
            image_skip_abs: 200 * 1024,
            keep_gps: true,
            vault_days: 7,
            auto_delete_vault: false,
            parallel_profile: ParallelProfile::Auto,
            language: "en".into(),
            estimate_scale_image: 1.0,
            estimate_scale_video: 1.0,
            estimate_scale_audio: 1.0,
            idle_charge_only: false,
        }
    }
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct VaultItemView {
    pub id: String,
    pub job_id: String,
    pub vault_path: String,
    pub original_uri: String,
    pub compressed_uri: String,
    pub original_bytes: u64,
    pub expires_at: i64,
    pub restored: bool,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct VaultRestoreView {
    pub vault_path: String,
    pub original_uri: String,
}

/// Parallelism profile. Mapped to worker quotas in Sprint 2.
#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum)]
pub enum ParallelProfile {
    Auto,
    Eco,
    Fast,
}

/// Directories owned by the engine. Created on `open`.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct EngineConfig {
    pub data_dir: String,
    pub tmp_dir: String,
    pub vault_dir: String,
    pub parallel_profile: ParallelProfile,
}
