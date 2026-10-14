//! Local diagnostic events. Payloads contain no URIs or filenames.

pub fn size_bucket(bytes: u64) -> &'static str {
    match bytes {
        0..=1_048_575 => "lt_1mb",
        1_048_576..=10_485_759 => "1_10mb",
        10_485_760..=52_428_799 => "10_50mb",
        52_428_800..=209_715_199 => "50_200mb",
        _ => "gte_200mb",
    }
}

pub fn duration_bucket(ms: u64) -> &'static str {
    match ms {
        0..=10_000 => "0_10s",
        10_001..=60_000 => "10_60s",
        60_001..=300_000 => "1_5m",
        _ => "5m_plus",
    }
}

pub fn probe_done_payload(kind: &str, codec_family: &str, w: u32, h: u32, duration_ms: u64) -> String {
    format!(
        r#"{{"type":"{kind}","codec_family":"{codec_family}","w":{w},"h":{h},"duration_bucket":"{}"}}"#,
        duration_bucket(duration_ms)
    )
}

pub fn job_finished_payload(
    result: &str,
    preset: &str,
    in_bytes: u64,
    out_bytes: u64,
    hw: bool,
) -> String {
    let ratio = if in_bytes == 0 {
        0.0
    } else {
        out_bytes as f64 / in_bytes as f64
    };
    format!(
        r#"{{"result":"{result}","preset":"{preset}","in_bucket":"{}","out_bucket":"{}","ratio":{ratio:.3},"ms":0,"hw":{hw}}}"#,
        size_bucket(in_bytes),
        size_bucket(out_bytes),
    )
}

/// Drop payloads that look like they contain paths or URIs.
pub fn sanitize_event_payload(payload_json: &str) -> String {
    let trimmed = payload_json.trim();
    if trimmed.is_empty() {
        return "{}".into();
    }
    let lower = trimmed.to_ascii_lowercase();
    if lower.contains("content://")
        || lower.contains("file://")
        || lower.contains("/storage/")
        || lower.contains("\\\\")
    {
        return r#"{"redacted":true}"#.into();
    }
    trimmed.to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn buckets_are_stable() {
        assert_eq!(size_bucket(100), "lt_1mb");
        assert_eq!(duration_bucket(90_000), "1_5m");
    }

    #[test]
    fn sanitize_strips_uri_payloads() {
        assert_eq!(
            sanitize_event_payload(r#"{"uri":"content://media/1"}"#),
            r#"{"redacted":true}"#
        );
        assert_eq!(sanitize_event_payload(""), "{}");
        assert_eq!(
            sanitize_event_payload(r#"{"result":"ok"}"#),
            r#"{"result":"ok"}"#
        );
    }
}
