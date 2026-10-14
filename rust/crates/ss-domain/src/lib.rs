//! Pure domain logic for SpaceSlimmer compression decisions.
//!
//! No I/O, no `unsafe`. All preset numbers are locked to `preset_v1.0`.
#![forbid(unsafe_code)]

mod audio;
mod calibration;
mod decide;
mod image;
mod presets;
mod types;
mod util;
mod video;

pub use audio::decide_audio;
pub use calibration::{
    apply_estimate_scale, clamp_estimate_scale, scale_estimate_bytes, scale_for_kind,
    second_pass_video_bitrate,
    update_estimate_scale,
};
pub use decide::{batch_time_class, decide, decide_batch, skip_disabled_decision};
pub use image::decide_image;
pub use presets::{
    audio_mono, audio_target_bitrate_bps, image_jpeg_quality, image_max_side,
    video_max_long_edge, video_quality_q,
};
pub use types::*;
pub use video::decide_video;

/// Locked preset table for Phase 1. Changing numeric tables requires bumping this.
pub const PRESET_TABLE_VERSION: &str = "preset_v1.0";

/// Engine semver surfaced through UniFFI. Keep in sync with workspace package version.
pub const ENGINE_VERSION: &str = "0.1.0";

#[cfg(test)]
mod tests;
