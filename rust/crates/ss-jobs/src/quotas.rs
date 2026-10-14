//! Parallel worker quotas per profile.

pub const DEFAULT_VIDEO_PARALLELISM: u32 = 1;
pub const DEFAULT_FAST_VIDEO_PARALLELISM: u32 = 2;
pub const DEFAULT_IMAGE_PARALLELISM: u32 = 4;
pub const DEFAULT_AUDIO_PARALLELISM: u32 = 4;

/// Rough per-image decode budget for quota math (~50MB RGBA worst case).
pub const IMAGE_RAM_BUDGET_BYTES: u64 = 400 * 1024 * 1024;
pub const WORST_IMAGE_FRAME_BYTES: u64 = 50 * 1024 * 1024;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ParallelProfile {
    Auto,
    Eco,
    Fast,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ParallelQuotas {
    pub video: u32,
    pub image: u32,
    pub audio: u32,
}

pub fn parallel_profile_from_str(s: &str) -> ParallelProfile {
    match s {
        "Eco" => ParallelProfile::Eco,
        "Fast" => ParallelProfile::Fast,
        _ => ParallelProfile::Auto,
    }
}

pub fn image_parallelism_for_memory_budget() -> u32 {
    let by_ram = (IMAGE_RAM_BUDGET_BYTES / WORST_IMAGE_FRAME_BYTES).max(1) as u32;
    DEFAULT_IMAGE_PARALLELISM.min(by_ram).max(1)
}

pub fn quotas_for_profile(
    profile: ParallelProfile,
    thermal_level: u8,
    dual_video_hw: bool,
) -> ParallelQuotas {
    let image_cap = image_parallelism_for_memory_budget();
    let base = match profile {
        ParallelProfile::Auto => ParallelQuotas {
            video: DEFAULT_VIDEO_PARALLELISM,
            image: image_cap.min(4),
            audio: DEFAULT_AUDIO_PARALLELISM,
        },
        ParallelProfile::Fast => ParallelQuotas {
            video: if dual_video_hw {
                DEFAULT_FAST_VIDEO_PARALLELISM
            } else {
                DEFAULT_VIDEO_PARALLELISM
            },
            image: image_cap.min(8),
            audio: 6,
        },
        ParallelProfile::Eco => ParallelQuotas {
            video: DEFAULT_VIDEO_PARALLELISM,
            image: 2,
            audio: 2,
        },
    };

    if thermal_level >= 2 {
        ParallelQuotas {
            video: 1,
            image: base.image.min(2),
            audio: base.audio.min(2),
        }
    } else {
        base
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fast_profile_uses_dual_video_when_enabled() {
        let single = quotas_for_profile(ParallelProfile::Fast, 0, false);
        let dual = quotas_for_profile(ParallelProfile::Fast, 0, true);
        assert_eq!(single.video, DEFAULT_VIDEO_PARALLELISM);
        assert_eq!(dual.video, DEFAULT_FAST_VIDEO_PARALLELISM);
    }

    #[test]
    fn thermal_level_caps_parallelism() {
        let normal = quotas_for_profile(ParallelProfile::Auto, 0, false);
        let hot = quotas_for_profile(ParallelProfile::Auto, 2, true);
        assert_eq!(hot.video, 1);
        assert!(hot.image <= normal.image);
        assert!(hot.audio <= normal.audio);
    }
}
