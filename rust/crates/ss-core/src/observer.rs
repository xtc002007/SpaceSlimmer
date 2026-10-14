use std::sync::Arc;

use crate::convert::{group_view_from_jobs, job_view_from_jobs, recipe_from_domain};
use crate::types::{GroupView, JobView, Recipe};

/// UI and platform callbacks. Implementations must not synchronously re-enter `CoreEngine`.
#[uniffi::export(callback_interface)]
pub trait CoreObserver: Send + Sync {
    fn on_group_changed(&self, group: GroupView);
    fn on_job_changed(&self, job: JobView);
    fn request_start_video(&self, job_id: String, recipe: Recipe, src: String, dst: String);
    fn request_start_audio(&self, job_id: String, recipe: Recipe, src: String, dst: String);
    fn request_cancel_video(&self, job_id: String);
    fn request_notification(&self, progress_text: String, ratio: f32);
}

#[derive(Clone)]
pub(crate) struct ObserverBridge {
    observer: Option<Arc<dyn CoreObserver>>,
    last_emit_ms: Arc<parking_lot::Mutex<i64>>,
}

impl ObserverBridge {
    pub fn new() -> Self {
        Self {
            observer: None,
            last_emit_ms: Arc::new(parking_lot::Mutex::new(0)),
        }
    }

    pub fn clone_handle(&self) -> Self {
        self.clone()
    }

    pub fn set(&mut self, observer: Option<Arc<dyn CoreObserver>>) {
        self.observer = observer;
    }

    pub fn notify_group(&self, group: &ss_jobs::GroupView, force: bool) {
        if let Some(obs) = &self.observer {
            if force || self.should_emit() {
                obs.on_group_changed(group_view_from_jobs(group));
            }
        }
    }

    pub fn notify_job(&self, job: &ss_jobs::JobView, force: bool) {
        if let Some(obs) = &self.observer {
            if force || self.should_emit() {
                obs.on_job_changed(job_view_from_jobs(job));
            }
        }
    }

    pub fn request_start_video(
        &self,
        job_id: &str,
        recipe: &ss_domain::Recipe,
        src: &str,
        dst: &str,
    ) {
        if let Some(obs) = &self.observer {
            obs.request_start_video(
                job_id.to_string(),
                recipe_from_domain(recipe),
                src.to_string(),
                dst.to_string(),
            );
        }
    }

    pub fn request_cancel_video(&self, job_id: &str) {
        if let Some(obs) = &self.observer {
            obs.request_cancel_video(job_id.to_string());
        }
    }

    pub fn request_start_audio(
        &self,
        job_id: &str,
        recipe: &ss_domain::Recipe,
        src: &str,
        dst: &str,
    ) {
        if let Some(obs) = &self.observer {
            obs.request_start_audio(
                job_id.to_string(),
                recipe_from_domain(recipe),
                src.to_string(),
                dst.to_string(),
            );
        }
    }

    pub fn request_notification(&self, text: &str, ratio: f32) {
        if let Some(obs) = &self.observer {
            obs.request_notification(text.to_string(), ratio);
        }
    }

    fn should_emit(&self) -> bool {
        const THROTTLE_MS: i64 = 150;
        let now = now_ms();
        let mut last = self.last_emit_ms.lock();
        if now - *last >= THROTTLE_MS {
            *last = now;
            true
        } else {
            false
        }
    }
}

fn now_ms() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}
