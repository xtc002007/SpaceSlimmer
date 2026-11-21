//! UniFFI entry for SpaceSlimmer.

uniffi::setup_scaffolding!("ss_core");

mod convert;
mod engine;
mod observer;
mod probe;
mod types;
mod scan;

pub use scan::*;

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

    #[test]
    fn test_storage_analyzer_end_to_end_flow() {
        let root = env::temp_dir().join(format!("ss-core-scan-{}", std::process::id()));
        let scan_dir = root.join("test_storage");
        let dcim = scan_dir.join("DCIM");
        let dl = scan_dir.join("Downloads");
        let doc = scan_dir.join("Documents");

        std::fs::create_dir_all(&dcim).unwrap();
        std::fs::create_dir_all(&dl).unwrap();
        std::fs::create_dir_all(&doc).unwrap();

        std::fs::write(dcim.join("VID_001.mp4"), vec![0xAA; 500_000]).unwrap();
        std::fs::write(dcim.join("IMG_001.jpg"), vec![0xBB; 200_000]).unwrap();
        std::fs::write(dl.join("archive.zip"), vec![0xCC; 300_000]).unwrap();
        std::fs::write(dl.join("app.apk"), vec![0xDD; 400_000]).unwrap();
        std::fs::write(doc.join("notes.txt"), b"some notes").unwrap();

        let dup_content = vec![0xEE; 50_000];
        std::fs::write(dl.join("dup1.bin"), &dup_content).unwrap();
        std::fs::write(dl.join("dup2.bin"), &dup_content).unwrap();

        let engine = CoreEngine::open(EngineConfig {
            data_dir: root.join("data").to_string_lossy().into_owned(),
            tmp_dir: root.join("tmp").to_string_lossy().into_owned(),
            vault_dir: root.join("vault").to_string_lossy().into_owned(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");

        let req = ScanRequest {
            root_path: scan_dir.to_string_lossy().into_owned(),
            scope_type: "FullDisk".into(),
            selected_categories: vec![],
            selected_folders: vec![],
            collapse_threshold_bytes: 10_000,
            include_hidden: false,
            min_file_size: 0,
            source_level: "L3".into(),
            device_total_bytes: Some(10_000_000),
            device_free_bytes: Some(4_000_000),
            device_apps_bytes: Some(1_000_000),
        };

        let scan_id = engine.start_scan(req, None, None).expect("start_scan");

        // Wait for background scan thread to finish
        std::thread::sleep(std::time::Duration::from_millis(500));

        let summary = engine.get_scan_summary(&scan_id).expect("get_summary");
        assert_eq!(summary.id, scan_id);
        assert!(summary.scanned_bytes >= 1_400_000);
        assert_eq!(summary.file_count, 7);

        // Root node inspection
        let root_node = engine.get_scan_node(&scan_id, 0).expect("root node");
        assert!(root_node.is_dir);

        // Children of root
        let children = engine.get_scan_children(&scan_id, 0, 0, 10).expect("children");
        assert!(!children.is_empty());

        // Treemap layout
        let treemap = engine.get_treemap_layout(&scan_id, 0, 1000.0, 600.0, 5.0, 50).expect("treemap");
        assert!(!treemap.items.is_empty());
        assert_eq!(treemap.bounds_w, 1000.0);

        // Category stats
        let cat_stats = engine.get_category_stats(&scan_id, 0).expect("cat stats");
        let video_stat = cat_stats.iter().find(|c| c.category_key == "video").unwrap();
        assert_eq!(video_stat.file_count, 1);
        assert!(video_stat.bytes >= 500_000);

        // Top files
        let top = engine.get_top_files(&scan_id, 0, 100_000, 5).expect("top files");
        assert!(!top.is_empty());
        assert_eq!(top[0].name, "VID_001.mp4");

        // Duplicate files
        let dupes = engine.find_duplicates(&scan_id, 0, 1000, 10).expect("find_duplicates");
        assert_eq!(dupes.len(), 1);
        assert_eq!(dupes[0].items.len(), 2);
        assert_eq!(dupes[0].file_size, 50_000);

        // CSV Export
        let csv_path = root.join("scan_report.csv");
        engine.export_scan_csv(&scan_id, &csv_path.to_string_lossy()).expect("export_csv");
        assert!(csv_path.exists());
        let csv_content = std::fs::read_to_string(&csv_path).unwrap();
        assert!(csv_content.contains("Size (Physical)"));
        assert!(csv_content.contains("VID_001.mp4"));

        // Incremental deletion
        let initial_scanned = summary.scanned_bytes;
        engine.apply_scan_removed(&scan_id, vec![top[0].id]).expect("apply_removed");
        let updated_root = engine.get_scan_node(&scan_id, 0).expect("updated root");
        assert!(updated_root.size_physical < initial_scanned);

        // History
        let hist = engine.list_historical_scans(10).expect("list_historical");
        assert!(!hist.is_empty());

        let _ = std::fs::remove_dir_all(&root);
    }

}
