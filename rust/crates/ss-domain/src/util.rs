//! Shared math helpers for decision logic.

pub fn align_down(value: u32, alignment: u32) -> u32 {
    if alignment == 0 {
        return value;
    }
    (value / alignment) * alignment
}

pub fn long_edge(width: u32, height: u32) -> u32 {
    width.max(height)
}

pub fn scale_dimensions(src_w: u32, src_h: u32, max_long: u32) -> (u32, u32) {
    if src_w == 0 || src_h == 0 {
        return (src_w, src_h);
    }
    let src_long = long_edge(src_w, src_h);
    let out_long = src_long.min(max_long);
    if out_long == src_long {
        return (src_w, src_h);
    }
    let ratio = out_long as f64 / src_long as f64;
    let out_w = align_down((src_w as f64 * ratio).round() as u32, 16).max(16);
    let out_h = align_down((src_h as f64 * ratio).round() as u32, 16).max(16);
    (out_w, out_h)
}

pub fn pixel_ratio(out_w: u32, out_h: u32, src_w: u32, src_h: u32) -> f64 {
    if src_w == 0 || src_h == 0 {
        return 1.0;
    }
    let out_px = (out_w as f64) * (out_h as f64);
    let src_px = (src_w as f64) * (src_h as f64);
    (out_px / src_px).min(1.0)
}

pub fn estimate_range(point: u64, wide: bool) -> (u64, u64) {
    if wide {
        let low = ((point as f64) * 0.75).floor() as u64;
        let high = ((point as f64) * 1.25).ceil() as u64;
        (low, high.max(low))
    } else {
        let low = ((point as f64) * 0.80).floor() as u64;
        let high = ((point as f64) * 1.15).ceil() as u64;
        (low, high.max(low))
    }
}

pub fn normalize_codec(codec: &str) -> String {
    codec.trim().to_ascii_lowercase()
}

pub fn codec_coeff(codec: &str) -> f64 {
    match normalize_codec(codec).as_str() {
        "h264" | "avc" | "h.264" => 1.0,
        "hevc" | "h265" | "h.265" => 0.60,
        "av1" | "av01" => 0.45,
        "vp9" => 0.55,
        _ => 1.0,
    }
}

pub fn is_lossless_audio(codec: &str) -> bool {
    matches!(
        normalize_codec(codec).as_str(),
        "wav" | "pcm" | "flac" | "alac" | "pcm_s16le" | "pcm_s24le"
    )
}

pub fn normalize_audio_codec(codec: &str) -> String {
    match normalize_codec(codec).as_str() {
        "mp4a-latm" | "mp4a" | "mpeg4-generic" => "aac".into(),
        "amr-nb" | "3gpp" | "amr-wb" => "amr".into(),
        other => other.to_string(),
    }
}

pub fn is_lossy_audio(codec: &str) -> bool {
    matches!(
        normalize_audio_codec(codec).as_str(),
        "aac" | "mp3" | "opus" | "m4a" | "vorbis" | "amr" | "amr-nb" | "amr-wb"
    )
}
