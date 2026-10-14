use serde::{Deserialize, Serialize};
use ss_domain::{
    CompressRequest, Decision, MediaFingerprint, MediaKind, Recipe, SkipReason,
};

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct VaultRestoreInfo {
    pub vault_path: String,
    pub original_uri: String,
}

pub fn skip_reason_as_str(reason: SkipReason) -> &'static str {
    match reason {
        SkipReason::AlreadyEfficient => "AlreadyEfficient",
        SkipReason::TooSmall => "TooSmall",
        SkipReason::EstimateNotWorth => "EstimateNotWorth",
        SkipReason::WouldGrow => "WouldGrow",
        SkipReason::UnsupportedHdr => "UnsupportedHdr",
        SkipReason::UnsupportedFps => "UnsupportedFps",
        SkipReason::UnsupportedCodec => "UnsupportedCodec",
        SkipReason::UserDisabled => "UserDisabled",
        SkipReason::TargetSizeUnreachable => "TargetSizeUnreachable",
    }
}

pub fn skip_reason_parse(s: &str) -> Option<SkipReason> {
    match s {
        "AlreadyEfficient" => Some(SkipReason::AlreadyEfficient),
        "TooSmall" => Some(SkipReason::TooSmall),
        "EstimateNotWorth" => Some(SkipReason::EstimateNotWorth),
        "WouldGrow" => Some(SkipReason::WouldGrow),
        "UnsupportedHdr" => Some(SkipReason::UnsupportedHdr),
        "UnsupportedFps" => Some(SkipReason::UnsupportedFps),
        "UnsupportedCodec" => Some(SkipReason::UnsupportedCodec),
        "UserDisabled" => Some(SkipReason::UserDisabled),
        "TargetSizeUnreachable" => Some(SkipReason::TargetSizeUnreachable),
        _ => None,
    }
}

/// Job message key for skipped items; GIF/RAW use dedicated keys instead of `skip.codec`.
pub fn skip_message_for_fingerprint(fp: &MediaFingerprint, reason: Option<SkipReason>) -> String {
    let mime = fp.mime.to_lowercase();
    if mime.contains("gif") {
        return "skip.gif".into();
    }
    if mime.contains("raw")
        || mime.contains("dng")
        || mime.contains("cr2")
        || mime.contains("nef")
        || mime.contains("arw")
    {
        return "skip.raw".into();
    }
    reason
        .map(|r| skip_reason_message_key(r).to_string())
        .unwrap_or_default()
}

pub fn skip_reason_message_key(reason: SkipReason) -> &'static str {
    match reason {
        SkipReason::AlreadyEfficient => "skip.already_efficient",
        SkipReason::TooSmall => "skip.too_small",
        SkipReason::EstimateNotWorth => "skip.not_worth",
        SkipReason::WouldGrow => "skip.would_grow",
        SkipReason::UnsupportedHdr => "skip.hdr",
        SkipReason::UnsupportedFps => "skip.slowmo",
        SkipReason::UnsupportedCodec => "skip.codec",
        SkipReason::UserDisabled => "skip.user_disabled",
        SkipReason::TargetSizeUnreachable => "skip.target_unreachable",
    }
}

pub fn fail_reason_as_str(reason: FailReason) -> &'static str {
    reason.as_str()
}

pub fn fail_reason_parse(s: &str) -> Option<FailReason> {
    FailReason::parse(s)
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
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

impl JobState {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Queued => "Queued",
            Self::Running => "Running",
            Self::Paused => "Paused",
            Self::Cancelling => "Cancelling",
            Self::Succeeded => "Succeeded",
            Self::Skipped => "Skipped",
            Self::Failed => "Failed",
            Self::CommittedReplace => "CommittedReplace",
        }
    }

    pub fn parse(s: &str) -> Option<Self> {
        match s {
            "Queued" => Some(Self::Queued),
            "Running" => Some(Self::Running),
            "Paused" => Some(Self::Paused),
            "Cancelling" => Some(Self::Cancelling),
            "Succeeded" => Some(Self::Succeeded),
            "Skipped" => Some(Self::Skipped),
            "Failed" => Some(Self::Failed),
            "CommittedReplace" => Some(Self::CommittedReplace),
            _ => None,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
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

impl FailReason {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::ProbeFailed => "ProbeFailed",
            Self::DecodeFailed => "DecodeFailed",
            Self::EncodeFailed => "EncodeFailed",
            Self::MuxFailed => "MuxFailed",
            Self::DiskFull => "DiskFull",
            Self::Permission => "Permission",
            Self::Cancelled => "Cancelled",
            Self::InvalidOutput => "InvalidOutput",
            Self::PlatformCrash => "PlatformCrash",
            Self::Unknown => "Unknown",
        }
    }

    pub fn parse(s: &str) -> Option<Self> {
        match s {
            "ProbeFailed" => Some(Self::ProbeFailed),
            "DecodeFailed" => Some(Self::DecodeFailed),
            "EncodeFailed" => Some(Self::EncodeFailed),
            "MuxFailed" => Some(Self::MuxFailed),
            "DiskFull" => Some(Self::DiskFull),
            "Permission" => Some(Self::Permission),
            "Cancelled" => Some(Self::Cancelled),
            "InvalidOutput" => Some(Self::InvalidOutput),
            "PlatformCrash" => Some(Self::PlatformCrash),
            "Unknown" => Some(Self::Unknown),
            _ => None,
        }
    }

    pub fn message_key(self) -> &'static str {
        match self {
            Self::EncodeFailed => "fail.encode",
            Self::DiskFull => "fail.disk_full",
            Self::Permission => "fail.permission",
            Self::InvalidOutput => "fail.invalid_output",
            _ => "fail.unknown",
        }
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct JobSpec {
    pub id: String,
    pub display_name: String,
    pub source_uri: String,
    pub local_path: Option<String>,
    pub fingerprint: MediaFingerprint,
    /// Per-job request for mixed groups. Falls back to the group request.
    #[serde(default)]
    pub request: Option<CompressRequest>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct JobRecord {
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

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct GroupRecord {
    pub id: String,
    pub created_at: i64,
    pub request: CompressRequest,
    pub state: GroupState,
    pub jobs: Vec<JobRecord>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum GroupState {
    Active,
    Paused,
    Completed,
    Cancelled,
}

impl GroupState {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Active => "Active",
            Self::Paused => "Paused",
            Self::Completed => "Completed",
            Self::Cancelled => "Cancelled",
        }
    }

    pub fn parse(s: &str) -> Option<Self> {
        match s {
            "Active" => Some(Self::Active),
            "Paused" => Some(Self::Paused),
            "Completed" => Some(Self::Completed),
            "Cancelled" => Some(Self::Cancelled),
            _ => None,
        }
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
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
    pub output_policy: ss_domain::OutputPolicy,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
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

impl JobRecord {
    pub fn to_view(&self) -> JobView {
        JobView {
            spec: self.spec.clone(),
            state: self.state,
            progress: self.progress,
            decision: self.decision.clone(),
            output_uri: self.output_uri.clone(),
            output_bytes: self.output_bytes,
            saved_bytes: self.saved_bytes,
            fail: self.fail,
            skip: self.skip,
            message: self.message.clone(),
            updated_at: self.updated_at,
        }
    }
}

pub fn build_group_view(
    id: String,
    created_at: i64,
    request: CompressRequest,
    jobs: Vec<JobRecord>,
    output_policy: ss_domain::OutputPolicy,
) -> GroupView {
    let mut running = 0u32;
    let mut done = 0u32;
    let mut failed = 0u32;
    let mut skipped = 0u32;
    let mut saved_bytes = 0i64;

    let views = jobs
        .iter()
        .map(|j| {
            match j.state {
                JobState::Running | JobState::Cancelling => running += 1,
                JobState::Succeeded | JobState::CommittedReplace => {
                    done += 1;
                    if let Some(saved) = j.saved_bytes {
                        saved_bytes += saved;
                    }
                }
                JobState::Skipped => skipped += 1,
                JobState::Failed => failed += 1,
                JobState::Queued | JobState::Paused => {}
            }
            j.to_view()
        })
        .collect();

    GroupView {
        id,
        created_at,
        request,
        jobs: views,
        running,
        done,
        failed,
        skipped,
        saved_bytes,
        output_policy,
    }
}

pub fn media_kind(job: &JobRecord) -> MediaKind {
    job.spec.fingerprint.kind
}

#[cfg(test)]
mod skip_message_tests {
    use super::*;
    use ss_domain::{MediaFingerprint, MediaKind, SkipReason};

    #[test]
    fn gif_uses_skip_gif_key() {
        let fp = MediaFingerprint {
            kind: MediaKind::Image,
            mime: "image/gif".into(),
            container: String::new(),
            video_codec: None,
            audio_codec: None,
            width: 100,
            height: 100,
            duration_ms: 0,
            fps: None,
            rotation: 0,
            file_bytes: 1024,
            video_bitrate_bps: None,
            audio_bitrate_bps: None,
            has_alpha: true,
            is_hdr: false,
            is_slowmo: false,
            is_live_photo: false,
            channels: None,
            sample_rate: None,
        };
        assert_eq!(
            skip_message_for_fingerprint(&fp, Some(SkipReason::UnsupportedCodec)),
            "skip.gif"
        );
    }

    #[test]
    fn raw_uses_skip_raw_key() {
        let fp = MediaFingerprint {
            kind: MediaKind::Image,
            mime: "image/x-adobe-dng".into(),
            container: String::new(),
            video_codec: None,
            audio_codec: None,
            width: 100,
            height: 100,
            duration_ms: 0,
            fps: None,
            rotation: 0,
            file_bytes: 1024,
            video_bitrate_bps: None,
            audio_bitrate_bps: None,
            has_alpha: false,
            is_hdr: false,
            is_slowmo: false,
            is_live_photo: false,
            channels: None,
            sample_rate: None,
        };
        assert_eq!(
            skip_message_for_fingerprint(&fp, Some(SkipReason::UnsupportedCodec)),
            "skip.raw"
        );
    }
}

pub fn recipe_for(job: &JobRecord) -> Option<Recipe> {
    job.decision
        .as_ref()
        .and_then(|d| d.recipe.clone())
}
