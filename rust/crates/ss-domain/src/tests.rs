use super::*;

fn h264_1080p_video(bytes: u64) -> MediaFingerprint {
    MediaFingerprint {
        kind: MediaKind::Video,
        mime: "video/mp4".into(),
        container: "mp4".into(),
        video_codec: Some("h264".into()),
        audio_codec: Some("aac".into()),
        width: 1920,
        height: 1080,
        duration_ms: 60_000,
        fps: Some(30.0),
        rotation: 0,
        file_bytes: bytes,
        video_bitrate_bps: None,
        audio_bitrate_bps: Some(128_000),
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: None,
        sample_rate: None,
    }
}

fn hevc_4k_video(bytes: u64) -> MediaFingerprint {
    MediaFingerprint {
        kind: MediaKind::Video,
        mime: "video/mp4".into(),
        container: "mp4".into(),
        video_codec: Some("hevc".into()),
        audio_codec: Some("aac".into()),
        width: 3840,
        height: 2160,
        duration_ms: 60_000,
        fps: Some(30.0),
        rotation: 0,
        file_bytes: bytes,
        video_bitrate_bps: Some(25_000_000),
        audio_bitrate_bps: Some(128_000),
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: None,
        sample_rate: None,
    }
}

fn balanced_request() -> CompressRequest {
    CompressRequest {
        preset: QualityPreset::VideoBalanced,
        compatibility: Compatibility::Efficient,
        output_policy: OutputPolicy::SaveAsNew,
        target_bytes: None,
        custom: None,
        keep_gps: false,
        keep_hdr: false,
        skip_enabled: true,
        image_format: None,
        audio_format: None,
        skip_thresholds: SkipThresholds::default(),
        estimate_scale: 1.0,
    }
}

fn caps_hevc() -> HwCaps {
    HwCaps {
        hevc_hw_encode: true,
        av1_hw_encode: false,
        hevc_max_w: 3840,
        hevc_max_h: 2160,
        av1_max_w: 0,
        av1_max_h: 0,
        max_image_side: 8192,
    }
}

#[test]
fn preset_table_version_is_locked_for_phase1() {
    assert_eq!(PRESET_TABLE_VERSION, "preset_v1.0");
}

#[test]
fn balanced_h264_1080p_reduces_size() {
    let fp = h264_1080p_video(80 * 1024 * 1024);
    let d = decide(&fp, &balanced_request(), &caps_hevc());
    assert!(!d.skip, "expected compress: {:?}", d.skip_reason);
    let recipe = d.recipe.as_ref().expect("recipe");
    assert_eq!(recipe.video_codec, "hevc");
    assert!(d.estimate_bytes_high < fp.file_bytes);
    assert!(d.estimate_bytes_high <= fp.file_bytes * 55 / 100 + fp.file_bytes / 10);
}

#[test]
fn small_preset_caps_resolution_at_720p() {
    let fp = h264_1080p_video(60 * 1024 * 1024);
    let req = CompressRequest {
        preset: QualityPreset::VideoSmall,
        compatibility: Compatibility::Compatible,
        output_policy: OutputPolicy::SaveAsNew,
        target_bytes: None,
        custom: None,
        keep_gps: false,
        keep_hdr: false,
        skip_enabled: true,
        image_format: None,
        audio_format: None,
        skip_thresholds: SkipThresholds::default(),
        estimate_scale: 1.0,
    };
    let d = decide(&fp, &req, &HwCaps::default());
    let recipe = d.recipe.as_ref().expect("recipe");
    assert!(recipe.out_w <= 1280 || recipe.out_h <= 1280);
    assert_eq!(recipe.video_codec, "h264");
}

#[test]
fn hevc_4k_balanced_downscales_even_when_source_is_hevc() {
    // Large 4K HEVC still benefits from 1080p cap on phone viewing.
    let fp = hevc_4k_video(120 * 1024 * 1024);
    let balanced = decide(&fp, &balanced_request(), &caps_hevc());
    let recipe = balanced.recipe.as_ref().expect("balanced should produce recipe");
    assert!(recipe.out_w <= 1920);
    assert!(recipe.out_h <= 1080);
    assert!(balanced.estimate_bytes_high < fp.file_bytes);
}

#[test]
fn efficient_hevc_1080p_skips_as_already_efficient() {
    let fp = MediaFingerprint {
        kind: MediaKind::Video,
        mime: "video/mp4".into(),
        container: "mp4".into(),
        video_codec: Some("hevc".into()),
        audio_codec: Some("aac".into()),
        width: 1920,
        height: 1080,
        duration_ms: 60_000,
        fps: Some(30.0),
        rotation: 0,
        file_bytes: 12 * 1024 * 1024,
        video_bitrate_bps: Some(1_500_000),
        audio_bitrate_bps: Some(128_000),
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: None,
        sample_rate: None,
    };
    let d = decide(&fp, &balanced_request(), &caps_hevc());
    assert!(d.skip, "expected skip: {:?}", d.skip_reason);
    assert_eq!(d.skip_reason, Some(SkipReason::AlreadyEfficient));
}

#[test]
fn audio_copy_estimate_includes_source_audio_bitrate() {
    let fp = MediaFingerprint {
        kind: MediaKind::Video,
        mime: "video/mp4".into(),
        container: "mp4".into(),
        video_codec: Some("h264".into()),
        audio_codec: Some("aac".into()),
        width: 1920,
        height: 1080,
        duration_ms: 60_000,
        fps: Some(30.0),
        rotation: 0,
        file_bytes: 80 * 1024 * 1024,
        video_bitrate_bps: Some(8_000_000),
        audio_bitrate_bps: Some(256_000),
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: None,
        sample_rate: None,
    };
    let req = CompressRequest {
        preset: QualityPreset::VideoBalanced,
        compatibility: Compatibility::Efficient,
        output_policy: OutputPolicy::SaveAsNew,
        target_bytes: None,
        custom: Some(crate::types::VideoCustomParams {
            max_long_edge: None,
            fps_cap: None,
            video_bitrate_bps: None,
            audio_bitrate_bps: None,
            codec: None,
            audio_copy: Some(true),
            trim_start_ms: None,
            trim_end_ms: None,
            strict_target: None,
        }),
        keep_gps: false,
        keep_hdr: false,
        skip_enabled: false,
        image_format: None,
        audio_format: None,
        skip_thresholds: SkipThresholds::default(),
        estimate_scale: 1.0,
    };
    let d = decide(&fp, &req, &caps_hevc());
    let mid = (d.estimate_bytes_low + d.estimate_bytes_high) / 2;
    // 60s @ ~256kbps audio alone is ~1.9MB; copy path must not drop audio from estimate.
    assert!(mid > 1_500_000, "estimate too low without audio: {mid}");
}

#[test]
fn balanced_h264_decision_snapshot_fields() {
    let fp = h264_1080p_video(80 * 1024 * 1024);
    let d = decide(&fp, &balanced_request(), &caps_hevc());
    assert!(!d.skip);
    let recipe = d.recipe.as_ref().expect("recipe");
    assert_eq!(recipe.video_codec, "hevc");
    assert!(recipe.out_w <= 1920);
    assert!(recipe.out_h <= 1080);
    assert!(d.estimate_bytes_low < d.estimate_bytes_high);
    assert!(d.estimate_bytes_high < fp.file_bytes);
}

/// Golden digest for `preset_v1.0` — update only when `03` preset table changes.
#[test]
fn golden_balanced_h264_1080p_decision_digest() {
    let fp = h264_1080p_video(80 * 1024 * 1024);
    let d = decide(&fp, &balanced_request(), &caps_hevc());
    let recipe = d.recipe.as_ref().expect("recipe");
    let digest = format!(
        "skip={};reason={};codec={};{}x{};v_bps={};a_copy={};low={};high={}",
        d.skip,
        d.skip_reason.map(|r| format!("{r:?}")).unwrap_or_else(|| "none".into()),
        recipe.video_codec,
        recipe.out_w,
        recipe.out_h,
        recipe.video_bitrate_bps,
        recipe.audio_copy,
        d.estimate_bytes_low,
        d.estimate_bytes_high,
    );
    assert!(
        digest.starts_with("skip=false;reason=none;codec=hevc;1920x1080;"),
        "unexpected digest: {digest}",
    );
    assert!(recipe.video_bitrate_bps >= 1_500_000);
    assert!(recipe.video_bitrate_bps <= 8_000_000);
    assert!(d.estimate_bytes_high < fp.file_bytes);
}

#[test]
fn golden_efficient_hevc_skip_digest() {
    let fp = MediaFingerprint {
        kind: MediaKind::Video,
        mime: "video/mp4".into(),
        container: "mp4".into(),
        video_codec: Some("hevc".into()),
        audio_codec: Some("aac".into()),
        width: 1920,
        height: 1080,
        duration_ms: 60_000,
        fps: Some(30.0),
        rotation: 0,
        file_bytes: 12 * 1024 * 1024,
        video_bitrate_bps: Some(1_500_000),
        audio_bitrate_bps: Some(128_000),
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: None,
        sample_rate: None,
    };
    let d = decide(&fp, &balanced_request(), &caps_hevc());
    assert_eq!(
        format!("skip={};reason={:?};recipe={}", d.skip, d.skip_reason, d.recipe.is_some()),
        "skip=true;reason=Some(AlreadyEfficient);recipe=false",
    );
}

#[test]
fn tiny_hevc_4k_may_skip_when_not_worth_it() {
    let fp = hevc_4k_video(8 * 1024 * 1024);
    let d = decide(&fp, &balanced_request(), &caps_hevc());
    assert!(d.skip);
    assert!(matches!(
        d.skip_reason,
        Some(SkipReason::EstimateNotWorth) | Some(SkipReason::AlreadyEfficient) | Some(SkipReason::WouldGrow)
    ));
}

#[test]
fn balanced_4k_downscales_to_1080p() {
    let fp = hevc_4k_video(120 * 1024 * 1024);
    let d = decide(&fp, &balanced_request(), &caps_hevc());
    assert!(!d.skip);
    let recipe = d.recipe.as_ref().unwrap();
    assert!(recipe.out_w <= 1920);
    assert!(recipe.out_h <= 1080);
}

#[test]
fn hdr_skipped_when_not_kept() {
    let mut fp = h264_1080p_video(50 * 1024 * 1024);
    fp.is_hdr = true;
    let d = decide(&fp, &balanced_request(), &caps_hevc());
    assert!(d.skip);
    assert_eq!(d.skip_reason, Some(SkipReason::UnsupportedHdr));
}

#[test]
fn slowmo_skipped() {
    let mut fp = h264_1080p_video(50 * 1024 * 1024);
    fp.is_slowmo = true;
    fp.fps = Some(240.0);
    let d = decide(&fp, &balanced_request(), &caps_hevc());
    assert!(d.skip);
    assert_eq!(d.skip_reason, Some(SkipReason::UnsupportedFps));
}

#[test]
fn decide_batch_matches_single() {
    let fp = h264_1080p_video(40 * 1024 * 1024);
    let req = balanced_request();
    let caps = caps_hevc();
    let single = decide(&fp, &req, &caps);
    let batch = decide_batch(&[fp], &req, &caps);
    assert_eq!(batch.len(), 1);
    assert_eq!(batch[0].skip, single.skip);
    assert_eq!(batch[0].estimate_bytes_low, single.estimate_bytes_low);
}

#[test]
fn image_jpeg_high_reduces_estimate() {
    let fp = MediaFingerprint {
        kind: MediaKind::Image,
        mime: "image/jpeg".into(),
        container: "jpeg".into(),
        video_codec: None,
        audio_codec: None,
        width: 4000,
        height: 3000,
        duration_ms: 0,
        fps: None,
        rotation: 0,
        file_bytes: 4 * 1024 * 1024,
        video_bitrate_bps: None,
        audio_bitrate_bps: None,
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: None,
        sample_rate: None,
    };
    let req = CompressRequest {
        preset: QualityPreset::ImageHigh,
        compatibility: Compatibility::Efficient,
        output_policy: OutputPolicy::SaveAsNew,
        target_bytes: None,
        custom: None,
        keep_gps: true,
        keep_hdr: false,
        skip_enabled: true,
        image_format: Some("jpeg".into()),
        audio_format: None,
        skip_thresholds: SkipThresholds::default(),
        estimate_scale: 1.0,
    };
    let d = decide(&fp, &req, &HwCaps::default());
    assert!(!d.skip);
    assert!(d.estimate_bytes_high < fp.file_bytes);
}

#[test]
fn wav_audio_never_skips_as_already_efficient() {
    let fp = MediaFingerprint {
        kind: MediaKind::Audio,
        mime: "audio/wav".into(),
        container: "wav".into(),
        video_codec: None,
        audio_codec: Some("pcm".into()),
        width: 0,
        height: 0,
        duration_ms: 600_000,
        fps: None,
        rotation: 0,
        file_bytes: 100 * 1024 * 1024,
        video_bitrate_bps: None,
        audio_bitrate_bps: None,
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: Some(1),
        sample_rate: Some(48_000),
    };
    let req = CompressRequest {
        preset: QualityPreset::AudioSpeech,
        compatibility: Compatibility::Compatible,
        output_policy: OutputPolicy::SaveAsNew,
        target_bytes: None,
        custom: None,
        keep_gps: false,
        keep_hdr: false,
        skip_enabled: true,
        image_format: None,
        audio_format: Some("aac".into()),
        skip_thresholds: SkipThresholds::default(),
        estimate_scale: 1.0,
    };
    let d = decide(&fp, &req, &HwCaps::default());
    assert!(!d.skip);
    assert!(d.estimate_bytes_high < fp.file_bytes / 5);
}

#[test]
fn target_size_downgrades_resolution_when_needed() {
    let fp = h264_1080p_video(80 * 1024 * 1024);
    let req = CompressRequest {
        preset: QualityPreset::VideoTargetSize,
        compatibility: Compatibility::Compatible,
        output_policy: OutputPolicy::SaveAsNew,
        target_bytes: Some(20 * 1024 * 1024),
        custom: None,
        keep_gps: false,
        keep_hdr: false,
        skip_enabled: true,
        image_format: None,
        audio_format: None,
        skip_thresholds: SkipThresholds::default(),
        estimate_scale: 1.0,
    };
    let d = decide(&fp, &req, &HwCaps::default());
    assert!(!d.skip, "expected compress: {:?}", d.skip_reason);
    let recipe = d.recipe.as_ref().expect("recipe");
    assert!(
        recipe.out_w <= 1280 || recipe.out_h <= 1280,
        "expected downgrade from 1080p, got {}x{}",
        recipe.out_w,
        recipe.out_h
    );
    // Video estimate band is 0.75–1.25× mid (docs/03 §5).
    assert!(d.estimate_bytes_high <= req.target_bytes.unwrap() * 125 / 100);
}

#[test]
fn target_size_unreachable_reports_min_achievable_volume() {
    let fp = h264_1080p_video(80 * 1024 * 1024);
    let req = CompressRequest {
        preset: QualityPreset::VideoTargetSize,
        compatibility: Compatibility::Compatible,
        output_policy: OutputPolicy::SaveAsNew,
        target_bytes: Some(50_000),
        custom: None,
        keep_gps: false,
        keep_hdr: false,
        skip_enabled: true,
        image_format: None,
        audio_format: None,
        skip_thresholds: SkipThresholds::default(),
        estimate_scale: 1.0,
    };
    let d = decide(&fp, &req, &HwCaps::default());
    assert!(d.skip);
    assert_eq!(d.skip_reason, Some(SkipReason::TargetSizeUnreachable));
    assert!(d.estimate_bytes_low > req.target_bytes.unwrap());
    assert!(d.estimate_bytes_high >= d.estimate_bytes_low);
}

#[test]
fn aac_128kbps_skips_as_already_efficient() {
    let fp = MediaFingerprint {
        kind: MediaKind::Audio,
        mime: "audio/mp4".into(),
        container: "m4a".into(),
        video_codec: None,
        audio_codec: Some("mp4a-latm".into()),
        width: 0,
        height: 0,
        duration_ms: 600_000,
        fps: None,
        rotation: 0,
        file_bytes: 10 * 1024 * 1024,
        video_bitrate_bps: None,
        audio_bitrate_bps: Some(128_000),
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: Some(2),
        sample_rate: Some(44_100),
    };
    let req = CompressRequest {
        preset: QualityPreset::AudioMusicHigh,
        compatibility: Compatibility::Compatible,
        output_policy: OutputPolicy::SaveAsNew,
        target_bytes: None,
        custom: None,
        keep_gps: false,
        keep_hdr: false,
        skip_enabled: true,
        image_format: None,
        audio_format: Some("aac".into()),
        skip_thresholds: SkipThresholds::default(),
        estimate_scale: 1.0,
    };
    let d = decide(&fp, &req, &HwCaps::default());
    assert!(d.skip);
    assert_eq!(d.skip_reason, Some(SkipReason::AlreadyEfficient));
}

#[test]
fn gif_is_skipped_as_unsupported_codec() {
    let fp = MediaFingerprint {
        kind: MediaKind::Image,
        mime: "image/gif".into(),
        container: "gif".into(),
        video_codec: None,
        audio_codec: None,
        width: 320,
        height: 240,
        duration_ms: 0,
        fps: None,
        rotation: 0,
        file_bytes: 800 * 1024,
        video_bitrate_bps: None,
        audio_bitrate_bps: None,
        has_alpha: true,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: None,
        sample_rate: None,
    };
    let req = CompressRequest {
        preset: QualityPreset::ImageHigh,
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
    };
    let d = decide(&fp, &req, &HwCaps::default());
    assert!(d.skip);
    assert_eq!(d.skip_reason, Some(SkipReason::UnsupportedCodec));
}

#[test]
fn raw_dng_is_skipped_as_unsupported_codec() {
    let fp = MediaFingerprint {
        kind: MediaKind::Image,
        mime: "image/x-adobe-dng".into(),
        container: "dng".into(),
        video_codec: None,
        audio_codec: None,
        width: 4000,
        height: 3000,
        duration_ms: 0,
        fps: None,
        rotation: 0,
        file_bytes: 20 * 1024 * 1024,
        video_bitrate_bps: None,
        audio_bitrate_bps: None,
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: false,
        channels: None,
        sample_rate: None,
    };
    let req = CompressRequest {
        preset: QualityPreset::ImageHigh,
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
    };
    let d = decide(&fp, &req, &HwCaps::default());
    assert!(d.skip);
    assert_eq!(d.skip_reason, Some(SkipReason::UnsupportedCodec));
}

fn long_edge(w: u32, h: u32) -> u32 {
    w.max(h)
}

#[test]
fn custom_video_locks_fps_bitrate_and_audio_copy() {
    let fp = h264_1080p_video(80 * 1024 * 1024);
    let mut req = balanced_request();
    req.preset = QualityPreset::VideoCustom;
    req.custom = Some(VideoCustomParams {
        max_long_edge: Some(1280),
        fps_cap: Some(24.0),
        video_bitrate_bps: Some(1_500_000),
        audio_bitrate_bps: Some(96_000),
        codec: Some("h264".into()),
        audio_copy: Some(false),
        trim_start_ms: Some(1_000),
        trim_end_ms: Some(11_000),
        strict_target: None,
    });
    let d = decide(&fp, &req, &HwCaps::default());
    assert!(!d.skip);
    let recipe = d.recipe.expect("recipe");
    assert_eq!(recipe.fps, Some(24.0));
    assert_eq!(recipe.video_bitrate_bps, 1_500_000);
    assert!(!recipe.audio_copy);
    assert_eq!(recipe.trim_start_ms, Some(1_000));
    assert_eq!(recipe.trim_end_ms, Some(11_000));
}

#[test]
fn live_photo_still_is_compressed_not_skipped() {
    let fp = MediaFingerprint {
        kind: MediaKind::Image,
        mime: "image/jpeg".into(),
        container: "jpeg".into(),
        video_codec: None,
        audio_codec: None,
        width: 4000,
        height: 3000,
        duration_ms: 0,
        fps: None,
        rotation: 0,
        file_bytes: 4 * 1024 * 1024,
        video_bitrate_bps: None,
        audio_bitrate_bps: None,
        has_alpha: false,
        is_hdr: false,
        is_slowmo: false,
        is_live_photo: true,
        channels: None,
        sample_rate: None,
    };
    let req = CompressRequest {
        preset: QualityPreset::ImageHigh,
        image_format: Some("jpeg".into()),
        ..Default::default()
    };
    let d = decide(&fp, &req, &HwCaps::default());
    assert!(!d.skip);
    assert!(d.recipe.is_some());
}

#[test]
fn bmp_and_tiff_are_not_skipped_as_unsupported() {
    for mime in ["image/bmp", "image/tiff"] {
        let fp = MediaFingerprint {
            kind: MediaKind::Image,
            mime: mime.into(),
            container: "bmp".into(),
            video_codec: None,
            audio_codec: None,
            width: 2000,
            height: 1500,
            duration_ms: 0,
            fps: None,
            rotation: 0,
            file_bytes: 3 * 1024 * 1024,
            video_bitrate_bps: None,
            audio_bitrate_bps: None,
            has_alpha: false,
            is_hdr: false,
            is_slowmo: false,
            is_live_photo: false,
            channels: None,
            sample_rate: None,
        };
        let req = CompressRequest {
            preset: QualityPreset::ImageHigh,
            image_format: Some("jpeg".into()),
            ..Default::default()
        };
        let d = decide(&fp, &req, &HwCaps::default());
        assert!(!d.skip, "{mime}");
    }
}
