//! Preset tables locked to `preset_v1.0`.

use crate::types::QualityPreset;

pub fn video_quality_q(preset: QualityPreset) -> f64 {
    match preset {
        QualityPreset::VideoExtreme => 0.85,
        QualityPreset::VideoHigh => 0.70,
        QualityPreset::VideoBalanced => 0.55,
        QualityPreset::VideoSmall => 0.40,
        QualityPreset::VideoTiny => 0.28,
        QualityPreset::VideoTargetSize | QualityPreset::VideoCustom => 0.55,
        _ => 0.55,
    }
}

pub fn video_max_long_edge(preset: QualityPreset, src_long: u32) -> u32 {
    match preset {
        QualityPreset::VideoExtreme => src_long,
        QualityPreset::VideoHigh => {
            if src_long >= 2160 {
                1920
            } else {
                src_long
            }
        }
        QualityPreset::VideoBalanced | QualityPreset::VideoTargetSize | QualityPreset::VideoCustom => {
            src_long.min(1920)
        }
        QualityPreset::VideoSmall => src_long.min(1280),
        QualityPreset::VideoTiny => src_long.min(960),
        _ => src_long.min(1920),
    }
}

/// Bitrate floor/ceiling for 1080p30 SDR (bps).
pub fn video_bitrate_bounds_1080p(preset: QualityPreset, hevc: bool) -> (u64, u64) {
    let (h264_lo, h264_hi) = match preset {
        QualityPreset::VideoExtreme => (10_000_000, 16_000_000),
        QualityPreset::VideoHigh => (6_000_000, 10_000_000),
        QualityPreset::VideoBalanced | QualityPreset::VideoTargetSize | QualityPreset::VideoCustom => {
            (4_000_000, 7_000_000)
        }
        QualityPreset::VideoSmall => (2_500_000, 4_000_000),
        QualityPreset::VideoTiny => (1_200_000, 2_000_000),
        _ => (4_000_000, 7_000_000),
    };
    if hevc {
        ((h264_lo as f64 * 0.6) as u64, (h264_hi as f64 * 0.6) as u64)
    } else {
        (h264_lo, h264_hi)
    }
}

pub fn scale_bitrate_bounds_for_resolution(
    floor: u64,
    ceil: u64,
    out_long: u32,
) -> (u64, u64) {
    let base = 1080.0;
    let scale = (out_long as f64 / base).powf(0.85).max(0.5);
    (
        (floor as f64 * scale).round() as u64,
        (ceil as f64 * scale).round() as u64,
    )
}

pub fn image_jpeg_quality(preset: QualityPreset) -> u8 {
    match preset {
        QualityPreset::ImageMax => 90,
        QualityPreset::ImageHigh => 82,
        QualityPreset::ImageSmall => 70,
        _ => 82,
    }
}

pub fn image_webp_quality(preset: QualityPreset) -> u8 {
    match preset {
        QualityPreset::ImageMax => 90,
        QualityPreset::ImageHigh => 80,
        QualityPreset::ImageSmall => 70,
        _ => 80,
    }
}

pub fn image_max_side(preset: QualityPreset) -> Option<u32> {
    match preset {
        QualityPreset::ImageSmall => Some(1920),
        _ => None,
    }
}

pub fn audio_target_bitrate_bps(preset: QualityPreset) -> u64 {
    match preset {
        QualityPreset::AudioSpeech => 64_000,
        QualityPreset::AudioMusicHigh => 192_000,
        QualityPreset::AudioMusicSmall => 96_000,
        _ => 128_000,
    }
}

pub fn audio_mono(preset: QualityPreset) -> bool {
    matches!(preset, QualityPreset::AudioSpeech)
}
