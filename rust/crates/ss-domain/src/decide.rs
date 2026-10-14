//! Main decision entry points.

use crate::audio::decide_audio;
use crate::image::decide_image;
use crate::types::{
    CompressRequest, Decision, HwCaps, MediaFingerprint, MediaKind, SkipReason, TimeClass,
};
use crate::video::decide_video;

pub fn decide(fp: &MediaFingerprint, req: &CompressRequest, caps: &HwCaps) -> Decision {
    if !req.skip_enabled {
        // still run HDR/slowmo guards for video
    }

    match fp.kind {
        MediaKind::Video => decide_video(fp, req, caps),
        MediaKind::Image => decide_image(fp, req),
        MediaKind::Audio => decide_audio(fp, req),
    }
}

pub fn decide_batch(
    items: &[MediaFingerprint],
    req: &CompressRequest,
    caps: &HwCaps,
) -> Vec<Decision> {
    items.iter().map(|fp| decide(fp, req, caps)).collect()
}

pub fn batch_time_class(decisions: &[Decision]) -> TimeClass {
    if decisions.is_empty() {
        return TimeClass::Fast;
    }
    let mut has_plugin = false;
    let mut has_medium = false;
    for d in decisions {
        match d.time_class {
            TimeClass::PlugIn => has_plugin = true,
            TimeClass::Medium => has_medium = true,
            TimeClass::Fast => {}
        }
    }
    if has_plugin {
        TimeClass::PlugIn
    } else if has_medium {
        TimeClass::Medium
    } else {
        TimeClass::Fast
    }
}

pub fn skip_disabled_decision(fp: &MediaFingerprint) -> Decision {
    Decision {
        skip: true,
        skip_reason: Some(SkipReason::UserDisabled),
        recipe: None,
        estimate_bytes_low: fp.file_bytes,
        estimate_bytes_high: fp.file_bytes,
        time_class: TimeClass::Fast,
    }
}
