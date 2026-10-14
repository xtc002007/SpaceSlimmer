//! UniFFI entry for SpaceSlimmer.

uniffi::setup_scaffolding!("ss_core");

mod convert;
mod engine;
mod observer;
mod probe;
mod types;

pub use engine::{CoreEngine, SsError};
pub use observer::CoreObserver;
pub use probe::{
    probe_image_dimensions_l2, probe_mp4_ftyp_l2, probe_mp4_video_dimensions_l2,
    second_pass_video_bitrate, ImageDimensions, Mp4FtypView,
};
pub use types::*;

#[cfg(test)]
mod tests {
    use std::env;

    use super::*;

    #[test]
    fn open_creates_directories_and_reports_versions() {
        let root = env::temp_dir().join(format!("ss-core-{}", std::process::id()));
        let data = root.join("data");
        let tmp = root.join("tmp");
        let vault = root.join("vault");
        let engine = CoreEngine::open(EngineConfig {
            data_dir: data.to_string_lossy().into_owned(),
            tmp_dir: tmp.to_string_lossy().into_owned(),
            vault_dir: vault.to_string_lossy().into_owned(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");

        assert_eq!(engine.version(), "0.1.0");
        assert_eq!(engine.preset_table_version(), "preset_v1.0");
        #[cfg(feature = "opus-encode")]
        assert!(engine.opus_encode_available());
        #[cfg(not(feature = "opus-encode"))]
        assert!(!engine.opus_encode_available());
        assert!(data.is_dir());
        assert!(tmp.is_dir());
        assert!(vault.is_dir());
        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn decide_balanced_1080p_video() {
        let root = env::temp_dir().join(format!("ss-core-decide-{}", std::process::id()));
        let engine = CoreEngine::open(EngineConfig {
            data_dir: root.join("data").to_string_lossy().into_owned(),
            tmp_dir: root.join("tmp").to_string_lossy().into_owned(),
            vault_dir: root.join("vault").to_string_lossy().into_owned(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");

        let decision = engine.decide(
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
                file_bytes: 80 * 1024 * 1024,
                video_bitrate_bps: Some(8_000_000),
                audio_bitrate_bps: Some(128_000),
                has_alpha: false,
                is_hdr: false,
                is_slowmo: false,
                is_live_photo: false,
                channels: None,
                sample_rate: None,
            },
            CompressRequest {
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
            },
            HwCaps {
                hevc_hw_encode: false,
                av1_hw_encode: false,
                hevc_max_w: 1920,
                hevc_max_h: 1080,
                av1_max_w: 0,
                av1_max_h: 0,
                max_image_side: 8192,
            },
        );
        assert!(!decision.skip);
        assert!(decision.recipe.is_some());
        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn decide_respects_settings_skip_disabled() {
        let root = env::temp_dir().join(format!("ss-core-skip-{}", std::process::id()));
        let engine = CoreEngine::open(EngineConfig {
            data_dir: root.join("data").to_string_lossy().into_owned(),
            tmp_dir: root.join("tmp").to_string_lossy().into_owned(),
            vault_dir: root.join("vault").to_string_lossy().into_owned(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");

        let mut settings = engine.settings();
        settings.skip_enabled = false;
        engine.set_settings(settings).expect("settings");

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
            file_bytes: 2 * 1024 * 1024,
            video_bitrate_bps: Some(500_000),
            audio_bitrate_bps: Some(128_000),
            has_alpha: false,
            is_hdr: false,
            is_slowmo: false,
            is_live_photo: false,
            channels: None,
            sample_rate: None,
        };
        let decision = engine.decide(
            fp,
            CompressRequest {
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
            },
            HwCaps {
                hevc_hw_encode: false,
                av1_hw_encode: false,
                hevc_max_w: 1920,
                hevc_max_h: 1080,
                av1_max_w: 0,
                av1_max_h: 0,
                max_image_side: 8192,
            },
        );
        assert!(!decision.skip);
        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn record_telemetry_roundtrip_without_uris() {
        let root = env::temp_dir().join(format!("ss-core-tel-{}", std::process::id()));
        let engine = CoreEngine::open(EngineConfig {
            data_dir: root.join("data").to_string_lossy().into_owned(),
            tmp_dir: root.join("tmp").to_string_lossy().into_owned(),
            vault_dir: root.join("vault").to_string_lossy().into_owned(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        engine
            .record_telemetry(
                "probe_done".into(),
                r#"{"type":"video","codec_family":"h264","w":1920,"h":1080,"duration_bucket":"10_60s"}"#
                    .into(),
            )
            .expect("record");
        engine
            .record_telemetry(
                "fallback".into(),
                r#"{"uri":"content://media/external/1"}"#.into(),
            )
            .expect("record redacted");
        let json = engine.telemetry_json(20).expect("list");
        assert!(json.contains("probe_done"));
        assert!(json.contains("redacted"));
        assert!(!json.contains("content://"));
        let _ = std::fs::remove_dir_all(&root);
    }
}
