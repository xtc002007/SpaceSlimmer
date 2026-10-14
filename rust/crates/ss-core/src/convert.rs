use ss_domain::{
    Compatibility as DCompatibility, CompressRequest as DCompressRequest,
    Decision as DDecision, HwCaps as DHwCaps, MediaFingerprint as DMediaFingerprint,
    MediaKind as DMediaKind, OutputPolicy as DOutputPolicy, QualityPreset as DQualityPreset,
    Recipe as DRecipe, SkipReason as DSkipReason, SkipThresholds as DSkipThresholds,
    TimeClass as DTimeClass, VideoCustomParams as DVideoCustomParams,
};
use ss_jobs::{
    FailReason as JFailReason, GroupView as JGroupView, JobSpec as JJobSpec, JobState as JJobState,
    JobView as JJobView,
};

use crate::types::*;

pub fn media_kind_to_domain(kind: MediaKind) -> DMediaKind {
    match kind {
        MediaKind::Video => DMediaKind::Video,
        MediaKind::Image => DMediaKind::Image,
        MediaKind::Audio => DMediaKind::Audio,
    }
}

pub fn media_kind_from_domain(kind: DMediaKind) -> MediaKind {
    match kind {
        DMediaKind::Video => MediaKind::Video,
        DMediaKind::Image => MediaKind::Image,
        DMediaKind::Audio => MediaKind::Audio,
    }
}

pub fn quality_preset_to_domain(p: QualityPreset) -> DQualityPreset {
    match p {
        QualityPreset::VideoExtreme => DQualityPreset::VideoExtreme,
        QualityPreset::VideoHigh => DQualityPreset::VideoHigh,
        QualityPreset::VideoBalanced => DQualityPreset::VideoBalanced,
        QualityPreset::VideoSmall => DQualityPreset::VideoSmall,
        QualityPreset::VideoTiny => DQualityPreset::VideoTiny,
        QualityPreset::VideoTargetSize => DQualityPreset::VideoTargetSize,
        QualityPreset::VideoCustom => DQualityPreset::VideoCustom,
        QualityPreset::ImageMax => DQualityPreset::ImageMax,
        QualityPreset::ImageHigh => DQualityPreset::ImageHigh,
        QualityPreset::ImageSmall => DQualityPreset::ImageSmall,
        QualityPreset::AudioSpeech => DQualityPreset::AudioSpeech,
        QualityPreset::AudioMusicHigh => DQualityPreset::AudioMusicHigh,
        QualityPreset::AudioMusicSmall => DQualityPreset::AudioMusicSmall,
    }
}

pub fn quality_preset_from_domain(p: DQualityPreset) -> QualityPreset {
    match p {
        DQualityPreset::VideoExtreme => QualityPreset::VideoExtreme,
        DQualityPreset::VideoHigh => QualityPreset::VideoHigh,
        DQualityPreset::VideoBalanced => QualityPreset::VideoBalanced,
        DQualityPreset::VideoSmall => QualityPreset::VideoSmall,
        DQualityPreset::VideoTiny => QualityPreset::VideoTiny,
        DQualityPreset::VideoTargetSize => QualityPreset::VideoTargetSize,
        DQualityPreset::VideoCustom => QualityPreset::VideoCustom,
        DQualityPreset::ImageMax => QualityPreset::ImageMax,
        DQualityPreset::ImageHigh => QualityPreset::ImageHigh,
        DQualityPreset::ImageSmall => QualityPreset::ImageSmall,
        DQualityPreset::AudioSpeech => QualityPreset::AudioSpeech,
        DQualityPreset::AudioMusicHigh => QualityPreset::AudioMusicHigh,
        DQualityPreset::AudioMusicSmall => QualityPreset::AudioMusicSmall,
    }
}

pub fn fingerprint_to_domain(fp: &MediaFingerprint) -> DMediaFingerprint {
    DMediaFingerprint {
        kind: media_kind_to_domain(fp.kind),
        mime: fp.mime.clone(),
        container: fp.container.clone(),
        video_codec: fp.video_codec.clone(),
        audio_codec: fp.audio_codec.clone(),
        width: fp.width,
        height: fp.height,
        duration_ms: fp.duration_ms,
        fps: fp.fps,
        rotation: fp.rotation,
        file_bytes: fp.file_bytes,
        video_bitrate_bps: fp.video_bitrate_bps,
        audio_bitrate_bps: fp.audio_bitrate_bps,
        has_alpha: fp.has_alpha,
        is_hdr: fp.is_hdr,
        is_slowmo: fp.is_slowmo,
        is_live_photo: fp.is_live_photo,
        channels: fp.channels,
        sample_rate: fp.sample_rate,
    }
}

pub fn fingerprint_from_domain(fp: &DMediaFingerprint) -> MediaFingerprint {
    MediaFingerprint {
        kind: media_kind_from_domain(fp.kind),
        mime: fp.mime.clone(),
        container: fp.container.clone(),
        video_codec: fp.video_codec.clone(),
        audio_codec: fp.audio_codec.clone(),
        width: fp.width,
        height: fp.height,
        duration_ms: fp.duration_ms,
        fps: fp.fps,
        rotation: fp.rotation,
        file_bytes: fp.file_bytes,
        video_bitrate_bps: fp.video_bitrate_bps,
        audio_bitrate_bps: fp.audio_bitrate_bps,
        has_alpha: fp.has_alpha,
        is_hdr: fp.is_hdr,
        is_slowmo: fp.is_slowmo,
        is_live_photo: fp.is_live_photo,
        channels: fp.channels,
        sample_rate: fp.sample_rate,
    }
}

pub fn hw_caps_to_domain(caps: &HwCaps) -> DHwCaps {
    DHwCaps {
        hevc_hw_encode: caps.hevc_hw_encode,
        av1_hw_encode: caps.av1_hw_encode,
        hevc_max_w: caps.hevc_max_w,
        hevc_max_h: caps.hevc_max_h,
        av1_max_w: caps.av1_max_w,
        av1_max_h: caps.av1_max_h,
        max_image_side: caps.max_image_side,
    }
}

pub fn hw_caps_from_domain(caps: &DHwCaps) -> HwCaps {
    HwCaps {
        hevc_hw_encode: caps.hevc_hw_encode,
        av1_hw_encode: caps.av1_hw_encode,
        hevc_max_w: caps.hevc_max_w,
        hevc_max_h: caps.hevc_max_h,
        av1_max_w: caps.av1_max_w,
        av1_max_h: caps.av1_max_h,
        max_image_side: caps.max_image_side,
    }
}

pub fn request_to_domain(req: &CompressRequest) -> DCompressRequest {
    DCompressRequest {
        preset: quality_preset_to_domain(req.preset),
        compatibility: match req.compatibility {
            Compatibility::Compatible => DCompatibility::Compatible,
            Compatibility::Efficient => DCompatibility::Efficient,
            Compatibility::MoreEfficient => DCompatibility::MoreEfficient,
            Compatibility::KeepContainer => DCompatibility::KeepContainer,
        },
        output_policy: match req.output_policy {
            OutputPolicy::SaveAsNew => DOutputPolicy::SaveAsNew,
            OutputPolicy::ReplaceAfterConfirm => DOutputPolicy::ReplaceAfterConfirm,
            OutputPolicy::ReplaceToVault => DOutputPolicy::ReplaceToVault,
        },
        target_bytes: req.target_bytes,
        custom: req.custom.as_ref().map(|c| DVideoCustomParams {
            max_long_edge: c.max_long_edge,
            fps_cap: c.fps_cap,
            video_bitrate_bps: c.video_bitrate_bps,
            audio_bitrate_bps: c.audio_bitrate_bps,
            codec: c.codec.clone(),
            audio_copy: c.audio_copy,
            trim_start_ms: c.trim_start_ms,
            trim_end_ms: c.trim_end_ms,
            strict_target: c.strict_target,
        }),
        keep_gps: req.keep_gps,
        keep_hdr: req.keep_hdr,
        skip_enabled: req.skip_enabled,
        image_format: req.image_format.clone(),
        audio_format: req.audio_format.clone(),
        skip_thresholds: DSkipThresholds::default(),
        estimate_scale: 1.0,
    }
}

pub fn request_to_domain_with_settings(req: &CompressRequest, settings: &Settings) -> DCompressRequest {
    let mut domain = request_to_domain(req);
    domain.skip_enabled = req.skip_enabled && settings.skip_enabled;
    domain.skip_thresholds = DSkipThresholds {
        video_ratio: settings.video_skip_ratio,
        video_abs_bytes: settings.video_skip_abs,
        image_ratio: settings.image_skip_ratio,
        image_abs_bytes: settings.image_skip_abs,
    };
    domain
}

pub fn request_to_domain_with_settings_for_kind(
    req: &CompressRequest,
    settings: &Settings,
    kind: MediaKind,
) -> DCompressRequest {
    let mut domain = request_to_domain_with_settings(req, settings);
    domain.estimate_scale = ss_domain::scale_for_kind(
        media_kind_to_domain(kind),
        settings.estimate_scale_image,
        settings.estimate_scale_video,
        settings.estimate_scale_audio,
    );
    domain
}

pub fn decision_from_domain(d: &DDecision) -> Decision {
    Decision {
        skip: d.skip,
        skip_reason: d.skip_reason.map(skip_reason_from_domain),
        recipe: d.recipe.as_ref().map(recipe_from_domain),
        estimate_bytes_low: d.estimate_bytes_low,
        estimate_bytes_high: d.estimate_bytes_high,
        time_class: match d.time_class {
            DTimeClass::Fast => TimeClass::Fast,
            DTimeClass::Medium => TimeClass::Medium,
            DTimeClass::PlugIn => TimeClass::PlugIn,
        },
    }
}

pub fn recipe_from_domain(r: &DRecipe) -> Recipe {
    Recipe {
        kind: media_kind_from_domain(r.kind),
        out_w: r.out_w,
        out_h: r.out_h,
        fps: r.fps,
        video_codec: r.video_codec.clone(),
        video_bitrate_bps: r.video_bitrate_bps,
        audio_copy: r.audio_copy,
        audio_codec: r.audio_codec.clone(),
        audio_bitrate_bps: r.audio_bitrate_bps,
        container: r.container.clone(),
        image_out_format: r.image_out_format.clone(),
        image_quality: r.image_quality,
        max_side: r.max_side,
        audio_out_format: r.audio_out_format.clone(),
        audio_channels: r.audio_channels,
        trim_start_ms: r.trim_start_ms,
        trim_end_ms: r.trim_end_ms,
        target_bytes: r.target_bytes,
    }
}

fn skip_reason_from_domain(r: DSkipReason) -> SkipReason {
    match r {
        DSkipReason::AlreadyEfficient => SkipReason::AlreadyEfficient,
        DSkipReason::TooSmall => SkipReason::TooSmall,
        DSkipReason::EstimateNotWorth => SkipReason::EstimateNotWorth,
        DSkipReason::WouldGrow => SkipReason::WouldGrow,
        DSkipReason::UnsupportedHdr => SkipReason::UnsupportedHdr,
        DSkipReason::UnsupportedFps => SkipReason::UnsupportedFps,
        DSkipReason::UnsupportedCodec => SkipReason::UnsupportedCodec,
        DSkipReason::UserDisabled => SkipReason::UserDisabled,
        DSkipReason::TargetSizeUnreachable => SkipReason::TargetSizeUnreachable,
    }
}

pub fn job_spec_to_jobs(spec: &JobSpec) -> JJobSpec {
    JJobSpec {
        id: spec.id.clone(),
        display_name: spec.display_name.clone(),
        source_uri: spec.source_uri.clone(),
        local_path: spec.local_path.clone(),
        fingerprint: fingerprint_to_domain(&spec.fingerprint),
        request: spec.request_override.as_ref().map(request_to_domain),
    }
}

pub fn request_from_domain(req: &DCompressRequest) -> CompressRequest {
    CompressRequest {
        preset: quality_preset_from_domain(req.preset),
        compatibility: match req.compatibility {
            DCompatibility::Compatible => Compatibility::Compatible,
            DCompatibility::Efficient => Compatibility::Efficient,
            DCompatibility::MoreEfficient => Compatibility::MoreEfficient,
            DCompatibility::KeepContainer => Compatibility::KeepContainer,
        },
        output_policy: match req.output_policy {
            DOutputPolicy::SaveAsNew => OutputPolicy::SaveAsNew,
            DOutputPolicy::ReplaceAfterConfirm => OutputPolicy::ReplaceAfterConfirm,
            DOutputPolicy::ReplaceToVault => OutputPolicy::ReplaceToVault,
        },
        target_bytes: req.target_bytes,
        custom: req.custom.as_ref().map(|c| VideoCustomParams {
            max_long_edge: c.max_long_edge,
            fps_cap: c.fps_cap,
            video_bitrate_bps: c.video_bitrate_bps,
            audio_bitrate_bps: c.audio_bitrate_bps,
            codec: c.codec.clone(),
            audio_copy: c.audio_copy,
            trim_start_ms: c.trim_start_ms,
            trim_end_ms: c.trim_end_ms,
            strict_target: c.strict_target,
        }),
        keep_gps: req.keep_gps,
        keep_hdr: req.keep_hdr,
        skip_enabled: req.skip_enabled,
        image_format: req.image_format.clone(),
        audio_format: req.audio_format.clone(),
    }
}

pub fn job_spec_from_jobs(spec: &JJobSpec) -> JobSpec {
    JobSpec {
        id: spec.id.clone(),
        display_name: spec.display_name.clone(),
        source_uri: spec.source_uri.clone(),
        local_path: spec.local_path.clone(),
        fingerprint: fingerprint_from_domain(&spec.fingerprint),
        request_override: spec.request.as_ref().map(request_from_domain),
    }
}

pub fn job_view_from_jobs(view: &JJobView) -> JobView {
    JobView {
        spec: job_spec_from_jobs(&view.spec),
        state: job_state_from_jobs(view.state),
        progress: view.progress,
        decision: view.decision.as_ref().map(decision_from_domain),
        output_uri: view.output_uri.clone(),
        output_bytes: view.output_bytes,
        saved_bytes: view.saved_bytes,
        fail: view.fail.map(fail_reason_from_jobs),
        skip: view.skip.map(skip_reason_from_domain),
        message: view.message.clone(),
        updated_at: view.updated_at,
    }
}

pub fn output_policy_from_domain(policy: DOutputPolicy) -> OutputPolicy {
    match policy {
        DOutputPolicy::SaveAsNew => OutputPolicy::SaveAsNew,
        DOutputPolicy::ReplaceAfterConfirm => OutputPolicy::ReplaceAfterConfirm,
        DOutputPolicy::ReplaceToVault => OutputPolicy::ReplaceToVault,
    }
}

pub fn group_view_from_jobs(view: &JGroupView) -> GroupView {
    GroupView {
        id: view.id.clone(),
        created_at: view.created_at,
        request: request_from_domain(&view.request),
        jobs: view.jobs.iter().map(job_view_from_jobs).collect(),
        running: view.running,
        done: view.done,
        failed: view.failed,
        skipped: view.skipped,
        saved_bytes: view.saved_bytes,
        output_policy: output_policy_from_domain(view.output_policy),
    }
}

pub fn job_state_from_jobs(state: JJobState) -> JobState {
    match state {
        JJobState::Queued => JobState::Queued,
        JJobState::Running => JobState::Running,
        JJobState::Paused => JobState::Paused,
        JJobState::Cancelling => JobState::Cancelling,
        JJobState::Succeeded => JobState::Succeeded,
        JJobState::Skipped => JobState::Skipped,
        JJobState::Failed => JobState::Failed,
        JJobState::CommittedReplace => JobState::CommittedReplace,
    }
}

pub fn fail_reason_to_jobs(reason: FailReason) -> JFailReason {
    match reason {
        FailReason::ProbeFailed => JFailReason::ProbeFailed,
        FailReason::DecodeFailed => JFailReason::DecodeFailed,
        FailReason::EncodeFailed => JFailReason::EncodeFailed,
        FailReason::MuxFailed => JFailReason::MuxFailed,
        FailReason::DiskFull => JFailReason::DiskFull,
        FailReason::Permission => JFailReason::Permission,
        FailReason::Cancelled => JFailReason::Cancelled,
        FailReason::InvalidOutput => JFailReason::InvalidOutput,
        FailReason::PlatformCrash => JFailReason::PlatformCrash,
        FailReason::Unknown => JFailReason::Unknown,
    }
}

fn fail_reason_from_jobs(reason: JFailReason) -> FailReason {
    match reason {
        JFailReason::ProbeFailed => FailReason::ProbeFailed,
        JFailReason::DecodeFailed => FailReason::DecodeFailed,
        JFailReason::EncodeFailed => FailReason::EncodeFailed,
        JFailReason::MuxFailed => FailReason::MuxFailed,
        JFailReason::DiskFull => FailReason::DiskFull,
        JFailReason::Permission => FailReason::Permission,
        JFailReason::Cancelled => FailReason::Cancelled,
        JFailReason::InvalidOutput => FailReason::InvalidOutput,
        JFailReason::PlatformCrash => FailReason::PlatformCrash,
        JFailReason::Unknown => FailReason::Unknown,
    }
}

pub fn parallel_profile_to_jobs(profile: ParallelProfile) -> ss_jobs::ParallelProfile {
    match profile {
        ParallelProfile::Auto => ss_jobs::ParallelProfile::Auto,
        ParallelProfile::Eco => ss_jobs::ParallelProfile::Eco,
        ParallelProfile::Fast => ss_jobs::ParallelProfile::Fast,
    }
}
