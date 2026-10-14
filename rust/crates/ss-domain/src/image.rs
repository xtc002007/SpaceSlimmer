//! Image compression decision logic.

use crate::presets::{image_jpeg_quality, image_max_side, image_webp_quality};
use crate::types::{
    CompressRequest, Decision, MediaFingerprint, QualityPreset, Recipe, SkipReason, TimeClass,
};
use crate::util::{estimate_range, long_edge, normalize_codec, scale_dimensions};

pub fn decide_image(fp: &MediaFingerprint, req: &CompressRequest) -> Decision {
    if req.skip_enabled {
        if let Some(reason) = skip_image(fp) {
            return skip_decision(fp.file_bytes, reason);
        }
    }

    let preset = image_preset(req.preset);
    let out_format = pick_image_format(fp, req);
    let quality = if normalize_codec(&out_format) == "webp" {
        image_webp_quality(preset)
    } else {
        image_jpeg_quality(preset)
    };
    let max_side = image_max_side(preset);

    let estimate = estimate_image_bytes(fp, quality, &out_format, max_side);

    let estimate_for_skip =
        crate::calibration::scale_estimate_bytes(estimate, req.estimate_scale);

    if req.skip_enabled {
        let saved = fp.file_bytes.saturating_sub(estimate_for_skip);
        let ratio = if fp.file_bytes > 0 {
            saved as f64 / fp.file_bytes as f64
        } else {
            0.0
        };
        let abs = req.skip_thresholds.image_abs_bytes;
        let min_ratio = req.skip_thresholds.image_ratio as f64;
        if estimate_for_skip >= fp.file_bytes * 9 / 10 && saved < abs {
            return skip_decision(
                fp.file_bytes,
                if estimate_for_skip >= fp.file_bytes {
                    SkipReason::WouldGrow
                } else {
                    SkipReason::EstimateNotWorth
                },
            );
        }
        if ratio < min_ratio && saved < abs {
            return skip_decision(fp.file_bytes, SkipReason::EstimateNotWorth);
        }
    }

    let (low, high) = estimate_range(estimate_for_skip, false);
    let max_long = max_side.unwrap_or(long_edge(fp.width, fp.height));
    let (out_w, out_h) = scale_dimensions(fp.width, fp.height, max_long);
    let recipe = Recipe {
        kind: crate::types::MediaKind::Image,
        out_w,
        out_h,
        fps: None,
        video_codec: String::new(),
        video_bitrate_bps: 0,
        audio_copy: false,
        audio_codec: String::new(),
        audio_bitrate_bps: 0,
        container: String::new(),
        image_out_format: Some(out_format),
        image_quality: Some(quality),
        max_side,
        audio_out_format: None,
        audio_channels: None,
        trim_start_ms: None,
        trim_end_ms: None,
        target_bytes: None,
    };

    Decision {
        skip: false,
        skip_reason: None,
        recipe: Some(recipe),
        estimate_bytes_low: low,
        estimate_bytes_high: high,
        time_class: TimeClass::Fast,
    }
}

fn image_preset(preset: QualityPreset) -> QualityPreset {
    match preset {
        QualityPreset::ImageMax | QualityPreset::ImageHigh | QualityPreset::ImageSmall => preset,
        _ => QualityPreset::ImageHigh,
    }
}

fn pick_image_format(fp: &MediaFingerprint, req: &CompressRequest) -> String {
    if let Some(fmt) = req.image_format.clone() {
        return fmt;
    }
    let mime = normalize_codec(&fp.mime);
    if fp.has_alpha {
        return "webp".into();
    }
    if mime.contains("png") {
        return "jpeg".into();
    }
    if mime.contains("webp") {
        return "webp".into();
    }
    "jpeg".into()
}

fn estimate_image_bytes(
    fp: &MediaFingerprint,
    quality: u8,
    out_format: &str,
    max_side: Option<u32>,
) -> u64 {
    let q = quality as f64 / 100.0;
    let format_factor = match normalize_codec(out_format).as_str() {
        "jpeg" | "jpg" => 0.55 + 0.35 * q,
        "webp" => 0.50 + 0.30 * q,
        "png" => 0.95,
        _ => 0.60,
    };
    let src_side = long_edge(fp.width, fp.height).max(1) as f64;
    let out_side = max_side
        .map(|m| src_side.min(m as f64))
        .unwrap_or(src_side);
    let pixel_factor = (out_side / 3000.0).clamp(0.3, 1.0);
    let base = fp.file_bytes as f64 * format_factor * pixel_factor;
    base.max(1024.0).round() as u64
}

fn skip_image(fp: &MediaFingerprint) -> Option<SkipReason> {
    let mime = normalize_codec(&fp.mime);
    if mime.contains("gif") {
        return Some(SkipReason::UnsupportedCodec);
    }
    if mime.contains("raw") || mime.contains("dng") {
        return Some(SkipReason::UnsupportedCodec);
    }
    // Live/Motion Photo stills are compressed as stills; companion video is optional.
    if fp.file_bytes < 150 * 1024 && mime.contains("jpeg") && !mime.contains("png") {
        return Some(SkipReason::TooSmall);
    }
    None
}

fn skip_decision(source_bytes: u64, reason: SkipReason) -> Decision {
    Decision {
        skip: true,
        skip_reason: Some(reason),
        recipe: None,
        estimate_bytes_low: source_bytes,
        estimate_bytes_high: source_bytes,
        time_class: TimeClass::Fast,
    }
}
