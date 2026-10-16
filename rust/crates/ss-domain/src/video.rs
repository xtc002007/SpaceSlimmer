//! Video compression decision logic.



use crate::presets::{

    scale_bitrate_bounds_for_resolution, video_bitrate_bounds_1080p, video_max_long_edge,

    video_quality_q,

};

use crate::types::{

    CompressRequest, Decision, HwCaps, MediaFingerprint, QualityPreset, Recipe, SkipReason,

    TimeClass,

};

use crate::util::{

    codec_coeff, estimate_range, long_edge, normalize_codec, pixel_ratio, scale_dimensions,

};



const MUX_OVERHEAD_RATIO: f64 = 0.02;



/// Resolution long-edge ladder for target-size mode: 1080p 鈫?720p 鈫?540p 鈫?480p.

const TARGET_SIZE_LONG_EDGES: [u32; 4] = [1920, 1280, 960, 480];



pub fn decide_video(fp: &MediaFingerprint, req: &CompressRequest, caps: &HwCaps) -> Decision {

    if req.skip_enabled {

        if let Some(reason) = pre_skip_video(fp, req) {

            return skip_decision(fp.file_bytes, reason, TimeClass::Medium);

        }

    }



    if fp.is_hdr && !req.keep_hdr {

        return skip_decision(fp.file_bytes, SkipReason::UnsupportedHdr, TimeClass::PlugIn);

    }



    if fp.is_slowmo || fp.fps.unwrap_or(0.0) >= 120.0 {

        return skip_decision(fp.file_bytes, SkipReason::UnsupportedFps, TimeClass::PlugIn);

    }



    if req.preset == QualityPreset::VideoTargetSize {

        return decide_video_target_size(fp, req, caps);

    }



    let preset = effective_video_preset(req);

    let src_long = long_edge(fp.width, fp.height);

    let max_long = req

        .custom

        .as_ref()

        .and_then(|c| c.max_long_edge)

        .unwrap_or_else(|| video_max_long_edge(preset, src_long));



    decide_video_with_resolution(fp, req, caps, max_long)

}



fn decide_video_target_size(

    fp: &MediaFingerprint,

    req: &CompressRequest,

    caps: &HwCaps,

) -> Decision {

    let target_bytes = req.target_bytes.unwrap_or(0);

    if target_bytes == 0 {

        return skip_decision(

            fp.file_bytes,

            SkipReason::TargetSizeUnreachable,

            TimeClass::Medium,

        );

    }

    if fp.file_bytes <= target_bytes {

        return skip_decision(

            fp.file_bytes,

            SkipReason::TooSmall,

            TimeClass::Fast,

        );

    }



    let preset = effective_video_preset(req);

    let src_long = long_edge(fp.width, fp.height);

    let mut min_achievable_estimate = u64::MAX;



    for ladder_long in TARGET_SIZE_LONG_EDGES {

        let max_long = src_long.min(ladder_long);

        let decision = decide_video_with_resolution(fp, req, caps, max_long);

        if !decision.skip {

            if let Some(recipe) = decision.recipe.as_ref() {

                let out_codec = &recipe.video_codec;

                let video_bps = recipe.video_bitrate_bps;

                let (out_w, out_h) = (recipe.out_w, recipe.out_h);

                if is_target_reachable_at_resolution(

                    fp,

                    preset,

                    out_codec,

                    out_w,

                    out_h,

                    video_bps,

                    req,

                ) {

                    return decision;

                }

                let estimate = (decision.estimate_bytes_low + decision.estimate_bytes_high) / 2;

                min_achievable_estimate = min_achievable_estimate.min(estimate);

            }

        }

    }



    let min_estimate = if min_achievable_estimate == u64::MAX {

        let floor_decision = decide_video_with_resolution(fp, req, caps, src_long.min(480));

        (floor_decision.estimate_bytes_low + floor_decision.estimate_bytes_high) / 2

    } else {

        min_achievable_estimate

    };

    let (low, high) = estimate_range(min_estimate, true);

    Decision {

        skip: true,

        skip_reason: Some(SkipReason::TargetSizeUnreachable),

        recipe: None,

        estimate_bytes_low: low,

        estimate_bytes_high: high,

        time_class: TimeClass::Medium,

    }

}



pub fn decide_video_with_resolution(

    fp: &MediaFingerprint,

    req: &CompressRequest,

    caps: &HwCaps,

    max_long: u32,

) -> Decision {

    let preset = effective_video_preset(req);

    let out_codec = pick_video_codec(req, caps);

    let capped_long = cap_max_long_for_codec(&out_codec, caps, max_long);

    let (out_w, out_h) = scale_dimensions(fp.width, fp.height, capped_long);



    let src_fps = fp.fps.unwrap_or(30.0).max(1.0);

    let out_fps = cap_fps(src_fps, preset, req);



    let audio_bps = target_audio_bps(fp, preset, req);

    let video_bps =

        compute_video_bitrate(fp, preset, &out_codec, out_w, out_h, src_fps, out_fps, req);



    let audio_copy = should_copy_audio(fp, req, audio_bps);

    let duration_ms = effective_duration_ms(fp, req);

    let estimate =
        estimate_video_bytes(duration_ms, video_bps, audio_bps, audio_copy, fp.audio_bitrate_bps);

    let estimate_for_skip =
        crate::calibration::scale_estimate_bytes(estimate, req.estimate_scale);

    if req.skip_enabled && req.preset != QualityPreset::VideoTargetSize {

        if let Some(reason) = post_skip_video(
            fp,
            estimate_for_skip,
            &out_codec,
            out_w,
            out_h,
            video_bps,
            &req.skip_thresholds,
        ) {

            return skip_decision(fp.file_bytes, reason, TimeClass::Medium);

        }

    }



    let (low, high) = estimate_range(estimate_for_skip, true);

    let recipe = Recipe {

        kind: crate::types::MediaKind::Video,

        out_w,

        out_h,

        fps: Some(out_fps),

        video_codec: out_codec,

        video_bitrate_bps: video_bps,

        audio_copy,

        audio_codec: "aac".into(),

        audio_bitrate_bps: audio_bps,

        container: "mp4".into(),

        image_out_format: None,

        image_quality: None,

        max_side: None,

        audio_out_format: None,

        audio_channels: None,

        trim_start_ms: req.custom.as_ref().and_then(|c| c.trim_start_ms),

        trim_end_ms: req.custom.as_ref().and_then(|c| c.trim_end_ms),

        target_bytes: recipe_target_bytes(req),

    };



    Decision {

        skip: false,

        skip_reason: None,

        recipe: Some(recipe),

        estimate_bytes_low: low,

        estimate_bytes_high: high,

        time_class: video_time_class(out_w, out_h, fp.duration_ms),

    }

}



fn is_target_reachable_at_resolution(

    fp: &MediaFingerprint,

    preset: QualityPreset,

    out_codec: &str,

    out_w: u32,

    out_h: u32,

    video_bps: u64,

    req: &CompressRequest,

) -> bool {

    let (floor, _) = bitrate_bounds_for_codec(preset, out_codec);

    let out_long = long_edge(out_w, out_h);

    let (floor, _) = scale_bitrate_bounds_for_resolution(floor, floor, out_long);

    if video_bps < floor {

        return false;

    }

    if let Some(target_bytes) = req.target_bytes {

        let audio_bps = target_audio_bps(fp, preset, req);

        let audio_copy = should_copy_audio(fp, req, audio_bps);

        let estimate = estimate_video_bytes(
            effective_duration_ms(fp, req),
            video_bps,
            audio_bps,
            audio_copy,
            fp.audio_bitrate_bps,
        );

        let estimate = crate::calibration::scale_estimate_bytes(estimate, req.estimate_scale);

        if estimate > target_bytes * 12 / 10 {

            return false;

        }

    }

    true

}



fn effective_video_preset(req: &CompressRequest) -> QualityPreset {

    match req.preset {

        QualityPreset::VideoCustom | QualityPreset::VideoTargetSize => QualityPreset::VideoBalanced,

        p => p,

    }

}



fn pick_video_codec(req: &CompressRequest, caps: &HwCaps) -> String {

    if let Some(codec) = req.custom.as_ref().and_then(|c| c.codec.clone()) {

        return codec;

    }

    match req.compatibility {

        crate::types::Compatibility::Compatible | crate::types::Compatibility::KeepContainer => {

            "h264".into()

        }

        crate::types::Compatibility::Efficient => {

            if caps.hevc_hw_encode {

                "hevc".into()

            } else {

                "h264".into()

            }

        }

        crate::types::Compatibility::MoreEfficient => {

            if caps.av1_hw_encode {

                "av1".into()

            } else if caps.hevc_hw_encode {

                "hevc".into()

            } else {

                "h264".into()

            }

        }

    }

}



fn cap_fps(src_fps: f32, preset: QualityPreset, req: &CompressRequest) -> f32 {

    if let Some(cap) = req.custom.as_ref().and_then(|c| c.fps_cap) {

        return src_fps.min(cap);

    }

    if src_fps > 30.0

        && matches!(preset, QualityPreset::VideoSmall | QualityPreset::VideoTiny)

    {

        return 30.0;

    }

    src_fps

}



fn compute_video_bitrate(

    fp: &MediaFingerprint,

    preset: QualityPreset,

    out_codec: &str,

    out_w: u32,

    out_h: u32,

    src_fps: f32,

    out_fps: f32,

    req: &CompressRequest,

) -> u64 {

    if let Some(custom) = req.custom.as_ref().and_then(|c| c.video_bitrate_bps) {

        return custom;

    }



    if let Some(target_bytes) = req.target_bytes {

        let duration_ms = effective_duration_ms(fp, req);

        if duration_ms > 0 {

            let audio_bps = target_audio_bps(fp, preset, req);

            let total_bps = (target_bytes * 8) as f64 / (duration_ms as f64 / 1000.0);

            let video_bps = total_bps * (1.0 - MUX_OVERHEAD_RATIO) - audio_bps as f64;

            if video_bps > 0.0 {

                return video_bps.round() as u64;

            }

        }

    }



    let q = video_quality_q(preset);

    let coeff_out = codec_coeff(out_codec);

    let coeff_in = fp

        .video_codec

        .as_deref()

        .map(codec_coeff)

        .unwrap_or(1.0);



    let src_v_bps = source_video_bitrate(fp);

    let px_factor = pixel_ratio(out_w, out_h, fp.width, fp.height).sqrt();

    let fps_ratio = (out_fps / src_fps).min(1.0) as f64;



    let mut target = if src_v_bps > 0 {

        src_v_bps as f64 * q * coeff_out / coeff_in * px_factor * fps_ratio

    } else {

        let (floor, _) = bitrate_bounds_for_codec(preset, out_codec);

        floor as f64

    };



    let (floor, ceil) = bitrate_bounds_for_codec(preset, out_codec);

    let out_long = long_edge(out_w, out_h);

    let (floor, ceil) = scale_bitrate_bounds_for_resolution(floor, ceil, out_long);



    target = target.clamp(floor as f64, ceil as f64);



    if src_v_bps > 0 {

        let cap = (src_v_bps as f64) * 0.95;

        target = target.min(cap);

    }



    target.round() as u64

}



fn source_video_bitrate(fp: &MediaFingerprint) -> u64 {

    if let Some(v) = fp.video_bitrate_bps {

        return v;

    }

    if fp.duration_ms == 0 {

        return 0;

    }

    let total_bps = (fp.file_bytes * 8) as f64 / (fp.duration_ms as f64 / 1000.0);

    let audio = fp.audio_bitrate_bps.unwrap_or(0) as f64;

    (total_bps - audio).max(0.0).round() as u64

}



fn target_audio_bps(_fp: &MediaFingerprint, preset: QualityPreset, req: &CompressRequest) -> u64 {

    if let Some(bps) = req.custom.as_ref().and_then(|c| c.audio_bitrate_bps) {

        return bps;

    }

    match preset {

        QualityPreset::VideoExtreme | QualityPreset::VideoHigh => 160_000,

        QualityPreset::VideoSmall | QualityPreset::VideoTiny => 96_000,

        _ => 128_000,

    }

}



fn should_copy_audio(fp: &MediaFingerprint, req: &CompressRequest, target_bps: u64) -> bool {

    if let Some(force) = req.custom.as_ref().and_then(|c| c.audio_copy) {

        return force;

    }

    let codec = fp.audio_codec.as_deref().map(normalize_codec);

    codec == Some("aac".to_string())

        && fp.audio_bitrate_bps.unwrap_or(target_bps + 1) <= target_bps

}



fn effective_duration_ms(fp: &MediaFingerprint, req: &CompressRequest) -> u64 {

    let full = fp.duration_ms;

    let start = req

        .custom

        .as_ref()

        .and_then(|c| c.trim_start_ms)

        .unwrap_or(0)

        .min(full);

    let end = req

        .custom

        .as_ref()

        .and_then(|c| c.trim_end_ms)

        .unwrap_or(full)

        .min(full)

        .max(start);

    end.saturating_sub(start).max(1)

}



fn recipe_target_bytes(req: &CompressRequest) -> Option<u64> {

    let strict = req.custom.as_ref().and_then(|c| c.strict_target).unwrap_or(false);

    if req.preset == QualityPreset::VideoTargetSize || strict {

        req.target_bytes

    } else {

        None

    }

}



fn estimate_video_bytes(
    duration_ms: u64,
    video_bps: u64,
    target_audio_bps: u64,
    audio_copy: bool,
    source_audio_bps: Option<u64>,
) -> u64 {

    if duration_ms == 0 {

        return 0;

    }

    let secs = duration_ms as f64 / 1000.0;

    let audio = if audio_copy {
        source_audio_bps.unwrap_or(target_audio_bps)
    } else {
        target_audio_bps
    };

    let bits = (video_bps + audio) as f64 * secs;

    let bytes = bits / 8.0;

    (bytes * (1.0 + MUX_OVERHEAD_RATIO)).round() as u64

}



fn pre_skip_video(fp: &MediaFingerprint, req: &CompressRequest) -> Option<SkipReason> {

    if fp.duration_ms < 1000 && fp.file_bytes < 2 * 1024 * 1024 {

        return Some(SkipReason::TooSmall);

    }

    if req.preset == QualityPreset::VideoTargetSize && req.target_bytes.is_none() {

        return Some(SkipReason::TargetSizeUnreachable);

    }

    None

}



fn post_skip_video(

    fp: &MediaFingerprint,

    estimate: u64,

    out_codec: &str,

    out_w: u32,

    out_h: u32,

    video_bps: u64,

    thresholds: &crate::types::SkipThresholds,

) -> Option<SkipReason> {

    let src_codec = fp.video_codec.as_deref().map(normalize_codec);

    let out = normalize_codec(out_codec);

    if matches!(src_codec.as_deref(), Some("hevc" | "h265" | "h.265" | "av1" | "av01")) {

        let src_long = long_edge(fp.width, fp.height);

        let out_long = long_edge(out_w, out_h);

        if out_long <= src_long {

            let src_bps = source_video_bitrate(fp);

            if src_bps > 0 && src_bps <= (video_bps as f64 * 1.1).round() as u64 {

                return Some(SkipReason::AlreadyEfficient);

            }

        }

    }



    if src_codec.as_deref() == Some(out.as_str())

        && out_w == fp.width

        && out_h == fp.height

    {

        let src_bps = source_video_bitrate(fp);

        if src_bps > 0 && video_bps >= src_bps {

            return Some(SkipReason::AlreadyEfficient);

        }

    }



    let saved = fp.file_bytes.saturating_sub(estimate);

    let ratio = if fp.file_bytes > 0 {

        saved as f64 / fp.file_bytes as f64

    } else {

        0.0

    };



    if estimate >= fp.file_bytes

        || (ratio < thresholds.video_ratio as f64 && saved < thresholds.video_abs_bytes)

    {

        return Some(if estimate >= fp.file_bytes {

            SkipReason::WouldGrow

        } else {

            SkipReason::EstimateNotWorth

        });

    }



    None

}



fn skip_decision(source_bytes: u64, reason: SkipReason, time_class: TimeClass) -> Decision {

    Decision {

        skip: true,

        skip_reason: Some(reason),

        recipe: None,

        estimate_bytes_low: source_bytes,

        estimate_bytes_high: source_bytes,

        time_class,

    }

}



fn video_time_class(out_w: u32, out_h: u32, duration_ms: u64) -> TimeClass {

    let long = long_edge(out_w, out_h);

    if long > 1920 || duration_ms > 10 * 60 * 1000 {

        TimeClass::PlugIn

    } else if long <= 1920 {

        TimeClass::Medium

    } else {

        TimeClass::PlugIn

    }

}



fn cap_max_long_for_codec(codec: &str, caps: &HwCaps, desired: u32) -> u32 {

    let norm = normalize_codec(codec);

    let (max_w, max_h) = match norm.as_str() {

        "hevc" | "h265" | "h.265" => (caps.hevc_max_w, caps.hevc_max_h),

        "av1" | "av01" => (caps.av1_max_w, caps.av1_max_h),

        _ => return desired,

    };

    if max_w == 0 || max_h == 0 {

        return desired;

    }

    desired.min(max_w.max(max_h))

}



fn bitrate_bounds_for_codec(preset: QualityPreset, codec: &str) -> (u64, u64) {

    let norm = normalize_codec(codec);

    match norm.as_str() {

        "hevc" | "h265" | "h.265" => video_bitrate_bounds_1080p(preset, true),

        "av1" | "av01" => {

            let (floor, ceil) = video_bitrate_bounds_1080p(preset, true);

            ((floor as f64 * 0.75).round() as u64, (ceil as f64 * 0.75).round() as u64)

        }

        _ => video_bitrate_bounds_1080p(preset, false),

    }

}



