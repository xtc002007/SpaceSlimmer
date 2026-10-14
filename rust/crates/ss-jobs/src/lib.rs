//! Job queue, state machine, and parallelism quotas.

mod error;
mod quotas;
mod scheduler;
mod telemetry;
mod types;

pub use error::JobError;
pub use quotas::{ParallelProfile, ParallelQuotas, parallel_profile_from_str, quotas_for_profile,
    DEFAULT_AUDIO_PARALLELISM, DEFAULT_IMAGE_PARALLELISM, DEFAULT_VIDEO_PARALLELISM,
};
pub use scheduler::{JobScheduler, SchedulerConfig};
pub use types::{
    build_group_view, fail_reason_as_str, fail_reason_parse, skip_reason_as_str,
    skip_reason_message_key, skip_reason_parse, FailReason, GroupRecord, GroupState, GroupView,
    JobRecord, JobSpec, JobState, JobView, VaultRestoreInfo, media_kind, recipe_for,
};
