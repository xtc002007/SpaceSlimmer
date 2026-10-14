//! Audio compression decision logic.

use crate::presets::{audio_mono, audio_target_bitrate_bps};
use crate::types::{
    CompressRequest, Decision, MediaFingerprint, QualityPreset, Recipe, SkipReason, TimeClass,
};
use crate::util::{estimate_range, is_lossless_audio, is_lossy_audio};

pub fn decide_audio(fp: &MediaFingerprint, req: &CompressRequest) -> Decision {
    let preset = audio_preset(req.preset);
    let target_bps = audio_target_bitrate_bps(preset);
    let out_format = pick_audio_format(req);
    let mono = audio_mono(preset);

    if req.skip_enabled {
        if let Some(reason) = skip_audio(fp, target_bps) {
            return skip_decision(fp.file_bytes, reason);
        }
    }

    let estimate = estimate_audio_bytes(fp.duration_ms, target_bps, mono);
    let estimate_for_skip =
        crate::calibration::scale_estimate_bytes(estimate, req.estimate_scale);

    if req.skip_enabled && !is_lossless_audio(fp.audio_codec.as_deref().unwrap_or("")) {
        let saved = fp.file_bytes.saturating_sub(estimate_for_skip);
        if estimate_for_skip >= fp.file_bytes {
            return skip_decision(fp.file_bytes, SkipReason::WouldGrow);
        }
        if saved < 200 * 1024 && fp.file_bytes < 2 * 1024 * 1024 {
            return skip_decision(fp.file_bytes, SkipReason::EstimateNotWorth);
        }
    }

    let (low, high) = estimate_range(estimate_for_skip, false);
    let recipe = Recipe {
        kind: crate::types::MediaKind::Audio,
        out_w: 0,
        out_h: 0,
        fps: None,
        video_codec: String::new(),
        video_bitrate_bps: 0,
        audio_copy: false,
        audio_codec: if out_format == "opus" {
            "opus".into()
        } else {
            "aac".into()
        },
        audio_bitrate_bps: target_bps,
        container: if out_format == "opus" {
            "ogg".into()
        } else {
            "m4a".into()
        },
        image_out_format: None,
        image_quality: None,
        max_side: None,
        audio_out_format: Some(out_format),
        audio_channels: Some(if mono { 1 } else { fp.channels.unwrap_or(2) }),
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

fn audio_preset(preset: QualityPreset) -> QualityPreset {
    match preset {
        QualityPreset::AudioSpeech
        | QualityPreset::AudioMusicHigh
        | QualityPreset::AudioMusicSmall => preset,
        _ => QualityPreset::AudioSpeech,
    }
}

fn pick_audio_format(req: &CompressRequest) -> String {
    req.audio_format
        .clone()
        .filter(|f| !f.is_empty())
        .unwrap_or_else(|| "aac".into())
}

fn estimate_audio_bytes(duration_ms: u64, bitrate_bps: u64, mono: bool) -> u64 {
    if duration_ms == 0 {
        return 0;
    }
    let secs = duration_ms as f64 / 1000.0;
    let effective_bps = if mono {
        bitrate_bps
    } else {
        bitrate_bps
    };
    let bits = effective_bps as f64 * secs;
    (bits / 8.0).round() as u64
}

fn skip_audio(fp: &MediaFingerprint, target_bps: u64) -> Option<SkipReason> {
    let codec = fp.audio_codec.as_deref().unwrap_or("");
    if is_lossless_audio(codec) {
        return None;
    }
    if is_lossy_audio(codec) {
        let src = fp.audio_bitrate_bps.unwrap_or_else(|| {
            if fp.duration_ms > 0 {
                ((fp.file_bytes * 8 * 1000) / fp.duration_ms).max(1)
            } else {
                target_bps
            }
        });
        if src <= (target_bps as f64 * 1.1).round() as u64 {
            return Some(SkipReason::AlreadyEfficient);
        }
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
