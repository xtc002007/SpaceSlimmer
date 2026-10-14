//! Device-local estimate calibration (EMA of actual / predicted).

use crate::types::{Decision, MediaKind};

const EMA_ALPHA: f32 = 0.2;
const SCALE_MIN: f32 = 0.5;
const SCALE_MAX: f32 = 2.0;
const TARGET_OVERSHOOT: f64 = 1.15;
const SECOND_PASS_MARGIN: f64 = 0.92;
const MIN_VIDEO_BITRATE: u64 = 400_000;

pub fn clamp_estimate_scale(scale: f32) -> f32 {
    if !scale.is_finite() {
        return 1.0;
    }
    scale.clamp(SCALE_MIN, SCALE_MAX)
}

/// `new = (1-α)*old + α*(actual/predicted)`.
pub fn update_estimate_scale(old: f32, predicted_bytes: u64, actual_bytes: u64) -> f32 {
    if predicted_bytes == 0 {
        return clamp_estimate_scale(old);
    }
    let old = clamp_estimate_scale(old);
    let uncalibrated = predicted_bytes as f32 / old;
    if uncalibrated <= 0.0 {
        return old;
    }
    let ratio = actual_bytes as f32 / uncalibrated;
    if !ratio.is_finite() || ratio <= 0.0 {
        return old;
    }
    clamp_estimate_scale((1.0 - EMA_ALPHA) * old + EMA_ALPHA * ratio)
}

pub fn scale_estimate_bytes(bytes: u64, scale: f32) -> u64 {
    let scale = clamp_estimate_scale(scale);
    if (scale - 1.0).abs() < 0.001 {
        return bytes;
    }
    ((bytes as f64) * scale as f64).round().max(0.0) as u64
}

pub fn apply_estimate_scale(decision: &mut Decision, scale: f32) {
    if decision.skip {
        return;
    }
    let scale = clamp_estimate_scale(scale);
    if (scale - 1.0).abs() < 0.001 {
        return;
    }
    let s = scale as f64;
    decision.estimate_bytes_low = ((decision.estimate_bytes_low as f64) * s).round() as u64;
    decision.estimate_bytes_high = ((decision.estimate_bytes_high as f64) * s)
        .round()
        .max(decision.estimate_bytes_low as f64) as u64;
}

pub fn scale_for_kind(kind: MediaKind, image: f32, video: f32, audio: f32) -> f32 {
    match kind {
        MediaKind::Image => clamp_estimate_scale(image),
        MediaKind::Video => clamp_estimate_scale(video),
        MediaKind::Audio => clamp_estimate_scale(audio),
    }
}

/// Lower bitrate for a second encode when output exceeds target by 15%.
/// `audio_bytes` is the measured or estimated audio contribution in `output_bytes`.
pub fn second_pass_video_bitrate(
    current_bps: u64,
    output_bytes: u64,
    target_bytes: u64,
    audio_bytes: u64,
    duration_ms: u64,
) -> Option<u64> {
    if target_bytes == 0 || output_bytes == 0 || duration_ms == 0 {
        return None;
    }
    if (output_bytes as f64) <= (target_bytes as f64) * TARGET_OVERSHOOT {
        return None;
    }
    let video_out = output_bytes.saturating_sub(audio_bytes).max(1);
    let video_target = target_bytes.saturating_sub(audio_bytes).max(1);
    let scale = (video_target as f64) / (video_out as f64);
    let next = ((current_bps as f64) * scale * SECOND_PASS_MARGIN).round() as u64;
    if next < MIN_VIDEO_BITRATE {
        let secs = duration_ms as f64 / 1000.0;
        let max_video_bps = ((video_target * 8) as f64 / secs * (1.0 - 0.02)).floor() as u64;
        if max_video_bps < MIN_VIDEO_BITRATE {
            return None;
        }
        return Some(max_video_bps.min(next.max(MIN_VIDEO_BITRATE)));
    }
    let secs = duration_ms as f64 / 1000.0;
    let max_video_bps = ((video_target * 8) as f64 / secs * (1.0 - 0.02)).floor() as u64;
    if next > max_video_bps {
        return None;
    }
    Some(next)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::types::TimeClass;

    #[test]
    fn ema_moves_toward_ratio() {
        let next = update_estimate_scale(1.0, 100, 120);
        assert!((next - 1.04).abs() < 0.001);
    }

    #[test]
    fn second_pass_triggers_over_15_percent() {
        assert!(second_pass_video_bitrate(
            500_000,
            12_000_000,
            10_000_000,
            500_000,
            180_000,
        )
        .is_some());
        assert!(second_pass_video_bitrate(2_000_000, 1_100_000, 1_000_000, 50_000, 60_000).is_none());
    }

    #[test]
    fn skip_decisions_are_not_scaled() {
        let mut d = Decision {
            skip: true,
            skip_reason: None,
            recipe: None,
            estimate_bytes_low: 10,
            estimate_bytes_high: 10,
            time_class: TimeClass::Fast,
        };
        apply_estimate_scale(&mut d, 1.5);
        assert_eq!(d.estimate_bytes_low, 10);
    }
}
