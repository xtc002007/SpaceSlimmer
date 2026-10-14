use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::sync::{Arc, Weak};
use std::thread;

use parking_lot::Mutex;
use ss_domain::{decide, MediaKind as DMediaKind};
use ss_audio::{options_from_recipe as audio_options_from_recipe, transcode_audio, validate_source};
use ss_image::{compress_image, options_from_recipe};
use ss_jobs::{JobScheduler, JobView as JJobView, SchedulerConfig, recipe_for};

use crate::convert::{
    decision_from_domain, fail_reason_to_jobs, fingerprint_to_domain, group_view_from_jobs,
    hw_caps_to_domain, job_spec_to_jobs, job_view_from_jobs, parallel_profile_to_jobs,
    request_to_domain_with_settings, request_to_domain_with_settings_for_kind,
};
use crate::observer::{CoreObserver, ObserverBridge};
use crate::types::*;

/// API-level errors. Job skip/fail stay on job views.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error, uniffi::Error)]
pub enum SsError {
    #[error("invalid argument: {0}")]
    InvalidArgument(String),
    #[error("io: {0}")]
    Io(String),
    #[error("db: {0}")]
    Db(String),
    #[error("not found")]
    NotFound,
    #[error("engine busy")]
    EngineBusy,
    #[error("internal: {0}")]
    Internal(String),
}

struct EngineInner {
    config: EngineConfig,
    scheduler: JobScheduler,
    observer: ObserverBridge,
    hw_caps: HwCaps,
    settings: Settings,
    low_battery: bool,
    background_paused_groups: HashSet<String>,
    /// Groups paused on open/dispatch because crash recovery met idle-charge-only.
    idle_charge_recovery_groups: Vec<String>,
    this: Weak<CoreEngine>,
}

struct VideoStart {
    job_id: String,
    recipe: ss_domain::Recipe,
    src: String,
    dst: String,
}

struct AudioStart {
    job_id: String,
    recipe: ss_domain::Recipe,
    src: String,
    dst: String,
}

struct ImageStart {
    job_id: String,
    input: String,
    recipe: ss_domain::Recipe,
    output_path: String,
    keep_gps: bool,
}

struct DispatchWork {
    notify_jobs: Vec<JJobView>,
    videos: Vec<VideoStart>,
    audios: Vec<AudioStart>,
    audios_rust: Vec<AudioStart>,
    images: Vec<ImageStart>,
}

/// Long-lived engine singleton held by the Android/iOS process.
#[derive(uniffi::Object)]
pub struct CoreEngine {
    inner: Mutex<EngineInner>,
    cancelled_jobs: Mutex<HashSet<String>>,
}

#[uniffi::export]
impl CoreEngine {
    /// Opens the engine and ensures `data_dir` / `tmp_dir` / `vault_dir` exist.
    #[uniffi::constructor]
    pub fn open(config: EngineConfig) -> Result<Arc<Self>, SsError> {
        ensure_dir(&config.data_dir)?;
        ensure_dir(&config.tmp_dir)?;
        ensure_dir(&config.vault_dir)?;

        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: Path::new(&config.data_dir).to_path_buf(),
            tmp_dir: Path::new(&config.tmp_dir).to_path_buf(),
            vault_dir: Path::new(&config.vault_dir).to_path_buf(),
            parallel_profile: parallel_profile_to_jobs(config.parallel_profile),
        })
        .map_err(map_job_err)?;

        let settings = scheduler
            .store()
            .settings()
            .get::<Settings>("app")
            .map_err(|e| SsError::Db(e.to_string()))?
            .unwrap_or_default();

        scheduler.set_parallel_profile(parallel_profile_to_jobs(settings.parallel_profile));

        let recovered_dispatch = scheduler.groups_needing_dispatch().map_err(map_job_err)?;

        let default_caps = HwCaps {
            hevc_hw_encode: false,
            av1_hw_encode: false,
            hevc_max_w: 1920,
            hevc_max_h: 1080,
            av1_max_w: 0,
            av1_max_h: 0,
            max_image_side: 8192,
        };

        let idle_charge_only = settings.idle_charge_only;
        let engine = Arc::new(Self {
            inner: Mutex::new(EngineInner {
                config: EngineConfig {
                    data_dir: config.data_dir.clone(),
                    tmp_dir: config.tmp_dir.clone(),
                    vault_dir: config.vault_dir.clone(),
                    parallel_profile: settings.parallel_profile,
                },
                scheduler,
                observer: ObserverBridge::new(),
                hw_caps: default_caps,
                settings,
                low_battery: false,
                background_paused_groups: HashSet::new(),
                idle_charge_recovery_groups: recovered_dispatch.clone(),
                this: Weak::new(),
            }),
            cancelled_jobs: Mutex::new(HashSet::new()),
        });
        engine.inner.lock().this = Arc::downgrade(&engine);
        if !recovered_dispatch.is_empty() {
            if idle_charge_only {
                let mut inner = engine.inner.lock();
                for group_id in recovered_dispatch {
                    inner
                        .scheduler
                        .pause_group(&group_id)
                        .map_err(map_job_err)?;
                }
            } else {
                let work = {
                    let mut inner = engine.inner.lock();
                    inner.collect_dispatch_work(true).map_err(map_job_err)?
                };
                let observer = engine.inner.lock().observer.clone_handle();
                CoreEngine::execute_dispatch_work(&engine, work, &observer, true);
            }
        }
        Ok(engine)
    }

    /// Dispatches queued work for active groups (e.g. after crash recovery notification).
    pub fn dispatch_pending_work(&self) -> Result<(), SsError> {
        let idle_only = self.inner.lock().settings.idle_charge_only;
        if idle_only {
            let groups = {
                let inner = self.inner.lock();
                inner
                    .scheduler
                    .groups_needing_dispatch()
                    .map_err(map_job_err)?
            };
            let mut inner = self.inner.lock();
            inner.idle_charge_recovery_groups = groups.clone();
            for group_id in groups {
                inner
                    .scheduler
                    .pause_group(&group_id)
                    .map_err(map_job_err)?;
            }
            return Ok(());
        }
        let (work, observer) = {
            let mut inner = self.inner.lock();
            let work = inner.collect_dispatch_work(true).map_err(map_job_err)?;
            (work, inner.observer.clone_handle())
        };
        Self::execute_dispatch_work(self, work, &observer, true);
        Ok(())
    }

    pub fn version(&self) -> String {
        ss_domain::ENGINE_VERSION.to_string()
    }

    pub fn preset_table_version(&self) -> String {
        ss_domain::PRESET_TABLE_VERSION.to_string()
    }

    /// Whether this build includes Rust Opus encoding (`opus-encode` feature).
    pub fn opus_encode_available(&self) -> bool {
        cfg!(feature = "opus-encode")
    }

    pub fn data_dir(&self) -> String {
        self.inner.lock().config.data_dir.clone()
    }

    pub fn tmp_dir(&self) -> String {
        self.inner.lock().config.tmp_dir.clone()
    }

    pub fn vault_dir(&self) -> String {
        self.inner.lock().config.vault_dir.clone()
    }

    pub fn parallel_profile(&self) -> ParallelProfile {
        self.inner.lock().config.parallel_profile
    }

    pub fn set_observer(&self, observer: Box<dyn CoreObserver>) {
        let mut inner = self.inner.lock();
        inner.observer.set(Some(Arc::from(observer)));
    }

    pub fn clear_observer(&self) {
        let mut inner = self.inner.lock();
        inner.observer.set(None);
    }

    pub fn set_hw_caps(&self, caps: HwCaps) {
        let mut inner = self.inner.lock();
        inner
            .scheduler
            .set_hw_caps(hw_caps_to_domain(&caps));
        inner.hw_caps = caps;
    }

    pub fn update_thermal(&self, level: u8) {
        let mut inner = self.inner.lock();
        inner.scheduler.update_thermal(level);
    }

    pub fn update_power(&self, battery_pct: u8, charging: bool) {
        let (notify_key, observer) = {
            let mut inner = self.inner.lock();
            let was_low = inner.low_battery;
            let is_low = !charging && battery_pct <= 15;
            inner.low_battery = is_low;
            let key = if is_low && !was_low {
                Some("power.low_battery")
            } else if was_low && !is_low && charging {
                Some("power.charging_resume")
            } else {
                None
            };
            (key, inner.observer.clone_handle())
        };
        if let Some(key) = notify_key {
            let ratio = if key == "power.low_battery" {
                battery_pct as f32 / 100.0
            } else {
                1.0
            };
            observer.request_notification(key.into(), ratio);
        }
    }

    pub fn set_dual_video_hw(&self, enabled: bool) {
        let mut inner = self.inner.lock();
        inner.scheduler.set_dual_video_hw(enabled);
    }

    /// Deletes expired vault rows regardless of `auto_delete_vault` setting.
    pub fn purge_expired_vault(&self) -> u32 {
        let now = now_secs();
        let (deleted, observer) = {
            let inner = self.inner.lock();
            let deleted = inner
                .scheduler
                .purge_expired_vault(now)
                .map_err(map_job_err)
                .unwrap_or_default();
            let _ = inner
                .scheduler
                .record_event("purge_expired_vault", &format!(r#"{{"deleted":{}}}"#, deleted.len()));
            (deleted, inner.observer.clone_handle())
        };
        if !deleted.is_empty() {
            observer.request_notification(
                &format!("vault.purged.{}", deleted.len()),
                1.0,
            );
        }
        deleted.len() as u32
    }

    /// Pauses every group that currently has running jobs (app backgrounded).
    pub fn pause_on_background(&self) -> Result<(), SsError> {
        let (running_jobs, paused_views, observer) = {
            let mut inner = self.inner.lock();
            inner.background_paused_groups.clear();
            let groups = inner.scheduler.list_groups().map_err(map_job_err)?;
            let mut running_jobs = Vec::new();
            let mut paused_views = Vec::new();
            for group in groups {
                if group.running > 0 {
                    inner.background_paused_groups.insert(group.id.clone());
                    running_jobs.extend(
                        inner
                            .scheduler
                            .inflight_job_ids_in_group(&group.id)
                            .map_err(map_job_err)?,
                    );
                    let view = inner
                        .scheduler
                        .pause_group(&group.id)
                        .map_err(map_job_err)?;
                    paused_views.push(view);
                }
            }
            (running_jobs, paused_views, inner.observer.clone_handle())
        };
        self.mark_jobs_cancelled(&running_jobs);
        for job_id in running_jobs {
            observer.request_cancel_video(&job_id);
        }
        for view in paused_views {
            observer.notify_group(&view, true);
        }
        Ok(())
    }

    /// Resumes groups paused by [`pause_on_background`] when not in low-battery state.
    pub fn resume_on_foreground(&self) -> Result<(), SsError> {
        let (work, views, observer) = {
            let mut inner = self.inner.lock();
            if inner.low_battery {
                return Ok(());
            }
            let to_resume = inner.background_paused_groups.clone();
            inner.background_paused_groups.clear();
            let mut work = DispatchWork {
                notify_jobs: Vec::new(),
                videos: Vec::new(),
                audios: Vec::new(),
                audios_rust: Vec::new(),
                images: Vec::new(),
            };
            let mut views = Vec::new();
            for group_id in to_resume {
                let cancelling = inner
                    .scheduler
                    .cancelling_job_ids_in_group(&group_id)
                    .map_err(map_job_err)?;
                for job_id in cancelling {
                    inner.scheduler.mark_resume_after_cancel_ack(&job_id);
                }
                let view = inner
                    .scheduler
                    .resume_group(&group_id)
                    .map_err(map_job_err)?;
                views.push(view);
                let batch = inner.collect_dispatch_work(true).map_err(map_job_err)?;
                work.notify_jobs.extend(batch.notify_jobs);
                work.videos.extend(batch.videos);
                work.audios.extend(batch.audios);
                work.audios_rust.extend(batch.audios_rust);
                work.images.extend(batch.images);
            }
            (work, views, inner.observer.clone_handle())
        };
        Self::execute_dispatch_work(self, work, &observer, true);
        for view in views {
            observer.notify_group(&view, true);
        }
        Ok(())
    }

    pub fn flush(&self) -> Result<(), SsError> {
        let (deleted, observer) = {
            let inner = self.inner.lock();
            let now = now_secs();
            let deleted = if inner.settings.auto_delete_vault {
                inner
                    .scheduler
                    .purge_expired_vault(now)
                    .map_err(map_job_err)?
            } else {
                Vec::new()
            };
            let _ = inner
                .scheduler
                .record_event("flush", &format!(r#"{{"deleted":{}}}"#, deleted.len()));
            (deleted, inner.observer.clone_handle())
        };
        if !deleted.is_empty() {
            observer.request_notification(
                &format!("vault.purged.{}", deleted.len()),
                1.0,
            );
        }
        Ok(())
    }

    /// Local diagnostic event. `payload_json` must not contain media URIs or filenames.
    pub fn record_telemetry(&self, event_type: String, payload_json: String) -> Result<(), SsError> {
        if event_type.trim().is_empty() {
            return Err(SsError::InvalidArgument("event_type required".into()));
        }
        self.inner
            .lock()
            .scheduler
            .record_event(&event_type, &payload_json)
            .map_err(map_job_err)?;
        Ok(())
    }

    /// JSON array of recent events. Payloads are size/codec buckets only.
    pub fn telemetry_json(&self, limit: u32) -> Result<String, SsError> {
        let events = self
            .inner
            .lock()
            .scheduler
            .list_events(limit.max(1).min(500))
            .map_err(map_job_err)?;
        let mut items = Vec::new();
        for (id, ts, kind, payload) in events {
            items.push(format!(
                r#"{{"id":"{id}","ts":{ts},"type":"{kind}","payload":{payload}}}"#
            ));
        }
        Ok(format!("[{}]", items.join(",")))
    }

    pub fn decide(
        &self,
        fp: MediaFingerprint,
        req: CompressRequest,
        caps: HwCaps,
    ) -> Decision {
        let inner = self.inner.lock();
        let domain_fp = fingerprint_to_domain(&fp);
        let domain_req = request_to_domain_with_settings_for_kind(&req, &inner.settings, fp.kind);
        let domain_caps = hw_caps_to_domain(&caps);
        decision_from_domain(&decide(&domain_fp, &domain_req, &domain_caps))
    }

    pub fn decide_batch(
        &self,
        items: Vec<MediaFingerprint>,
        req: CompressRequest,
        caps: HwCaps,
    ) -> Vec<Decision> {
        let inner = self.inner.lock();
        let domain_caps = hw_caps_to_domain(&caps);
        items
            .iter()
            .map(|fp| {
                let domain_fp = fingerprint_to_domain(fp);
                let domain_req =
                    request_to_domain_with_settings_for_kind(&req, &inner.settings, fp.kind);
                decision_from_domain(&decide(&domain_fp, &domain_req, &domain_caps))
            })
            .collect()
    }

    pub fn submit_group(
        &self,
        jobs: Vec<JobSpec>,
        req: CompressRequest,
        start_paused: bool,
    ) -> Result<String, SsError> {
        let work = {
            let mut inner = self.inner.lock();
            let domain_req = request_to_domain_with_settings(&req, &inner.settings);
            let job_specs = jobs
                .iter()
                .map(|spec| {
                    let src_req = spec.request_override.as_ref().unwrap_or(&req);
                    let mut mapped = job_spec_to_jobs(spec);
                    mapped.request = Some(request_to_domain_with_settings_for_kind(
                        src_req,
                        &inner.settings,
                        spec.fingerprint.kind,
                    ));
                    mapped
                })
                .collect();
            let group_id = inner
                .scheduler
                .submit_group(job_specs, domain_req, start_paused)
                .map_err(map_job_err)?;
            let work = if start_paused {
                DispatchWork {
                    notify_jobs: Vec::new(),
                    videos: Vec::new(),
                    audios: Vec::new(),
                    audios_rust: Vec::new(),
                    images: Vec::new(),
                }
            } else {
                inner.collect_dispatch_work(true).map_err(map_job_err)?
            };
            (group_id, work, inner.observer.clone_handle())
        };
        Self::execute_dispatch_work(self, work.1, &work.2, true);
        Ok(work.0)
    }

    pub fn pause_group(&self, group_id: String) -> Result<GroupView, SsError> {
        let (view, cancel_jobs, observer) = {
            let mut inner = self.inner.lock();
            let cancel_jobs = inner
                .scheduler
                .inflight_job_ids_in_group(&group_id)
                .map_err(map_job_err)?;
            let view = inner
                .scheduler
                .pause_group(&group_id)
                .map_err(map_job_err)?;
            (view, cancel_jobs, inner.observer.clone_handle())
        };
        self.mark_jobs_cancelled(&cancel_jobs);
        for job_id in cancel_jobs {
            observer.request_cancel_video(&job_id);
        }
        observer.notify_group(&view, true);
        Ok(group_view_from_jobs(&view))
    }

    pub fn resume_group(&self, group_id: String) -> Result<GroupView, SsError> {
        let (view, work, observer) = {
            let mut inner = self.inner.lock();
            let cancelling = inner
                .scheduler
                .cancelling_job_ids_in_group(&group_id)
                .map_err(map_job_err)?;
            for job_id in cancelling {
                inner.scheduler.mark_resume_after_cancel_ack(&job_id);
            }
            let view = inner
                .scheduler
                .resume_group(&group_id)
                .map_err(map_job_err)?;
            let work = inner.collect_dispatch_work(true).map_err(map_job_err)?;
            (view, work, inner.observer.clone_handle())
        };
        Self::execute_dispatch_work(self, work, &observer, true);
        observer.notify_group(&view, true);
        Ok(group_view_from_jobs(&view))
    }

    /// Group ids paused during crash recovery under idle-charge-only (not user-paused groups).
    pub fn idle_charge_recovery_group_ids(&self) -> Vec<String> {
        self.inner.lock().idle_charge_recovery_groups.clone()
    }

    pub fn cancel_group(&self, group_id: String) -> Result<GroupView, SsError> {
        let (view, running_jobs, observer) = {
            let mut inner = self.inner.lock();
            let running_jobs = inner
                .scheduler
                .inflight_job_ids_in_group(&group_id)
                .map_err(map_job_err)?;
            let view = inner
                .scheduler
                .cancel_group(&group_id)
                .map_err(map_job_err)?;
            (view, running_jobs, inner.observer.clone_handle())
        };
        self.mark_jobs_cancelled(&running_jobs);
        for job_id in running_jobs {
            observer.request_cancel_video(&job_id);
        }
        observer.notify_group(&view, true);
        Ok(group_view_from_jobs(&view))
    }

    pub fn cancel_job(&self, job_id: String) -> Result<JobView, SsError> {
        let (view, should_cancel, observer) = {
            let mut inner = self.inner.lock();
            let record = inner
                .scheduler
                .job(&job_id)
                .map_err(map_job_err)?;
            let should_cancel = matches!(
                record.state,
                ss_jobs::JobState::Running | ss_jobs::JobState::Cancelling
            );
            let view = inner
                .scheduler
                .cancel_job(&job_id)
                .map_err(map_job_err)?;
            (view, should_cancel, inner.observer.clone_handle())
        };
        if should_cancel {
            self.mark_jobs_cancelled(&[job_id.clone()]);
            observer.request_cancel_video(&job_id);
        }
        observer.notify_job(&view, true);
        Ok(job_view_from_jobs(&view))
    }

    pub fn retry_job(&self, job_id: String) -> Result<JobView, SsError> {
        let (view, work, observer) = {
            let mut inner = self.inner.lock();
            let view = inner
                .scheduler
                .retry_job(&job_id)
                .map_err(map_job_err)?;
            let work = inner.collect_dispatch_work(true).map_err(map_job_err)?;
            (view, work, inner.observer.clone_handle())
        };
        Self::execute_dispatch_work(self, work, &observer, true);
        observer.notify_job(&view, true);
        Ok(job_view_from_jobs(&view))
    }

    pub fn list_groups(&self) -> Result<Vec<GroupView>, SsError> {
        let inner = self.inner.lock();
        inner
            .scheduler
            .list_groups()
            .map(|groups| groups.iter().map(group_view_from_jobs).collect())
            .map_err(map_job_err)
    }

    pub fn group(&self, group_id: String) -> Result<GroupView, SsError> {
        let inner = self.inner.lock();
        inner
            .scheduler
            .group(&group_id)
            .map(|g| group_view_from_jobs(&g))
            .map_err(map_job_err)
    }

    /// Removes a finished job record from history. Does not delete media files or vault backups.
    pub fn delete_job(&self, job_id: String) -> Result<(), SsError> {
        let (maybe_view, observer) = {
            let mut inner = self.inner.lock();
            let maybe_group_id = inner
                .scheduler
                .delete_job(&job_id)
                .map_err(map_job_err)?;
            let view = maybe_group_id
                .as_deref()
                .and_then(|group_id| inner.scheduler.group(group_id).ok());
            (view, inner.observer.clone_handle())
        };
        if let Some(view) = maybe_view {
            observer.notify_group(&view, true);
        }
        Ok(())
    }

    pub fn vault_usage(&self) -> Result<u64, SsError> {
        let inner = self.inner.lock();
        inner
            .scheduler
            .store()
            .vault_usage()
            .map_err(|e| SsError::Db(e.to_string()))
    }

    pub fn delete_vault_item(&self, id: String) -> Result<(), SsError> {
        let inner = self.inner.lock();
        inner
            .scheduler
            .store()
            .delete_vault_item(&id)
            .map_err(|e| SsError::Db(e.to_string()))
    }

    pub fn extend_vault_item(&self, vault_id: String, days: u32) -> Result<VaultItemView, SsError> {
        let inner = self.inner.lock();
        let row = inner
            .scheduler
            .extend_vault_item(&vault_id, days)
            .map_err(map_job_err)?;
        Ok(VaultItemView {
            id: row.id,
            job_id: row.job_id,
            vault_path: row.vault_path,
            original_uri: row.original_uri,
            compressed_uri: row.compressed_uri,
            original_bytes: row.original_bytes,
            expires_at: row.expires_at,
            restored: row.restored,
        })
    }

    pub fn register_vault_backup(
        &self,
        job_id: String,
        vault_path: String,
        original_uri: String,
        original_bytes: u64,
        expires_at: i64,
    ) -> Result<String, SsError> {
        let inner = self.inner.lock();
        inner
            .scheduler
            .register_vault_backup(
                &job_id,
                &vault_path,
                &original_uri,
                original_bytes,
                expires_at,
            )
            .map_err(map_job_err)
    }

    pub fn restore_vault_item(&self, vault_id: String) -> Result<VaultRestoreView, SsError> {
        let inner = self.inner.lock();
        let info = inner
            .scheduler
            .restore_vault_item(&vault_id)
            .map_err(map_job_err)?;
        Ok(VaultRestoreView {
            vault_path: info.vault_path,
            original_uri: info.original_uri,
        })
    }

    pub fn confirm_vault_restored(&self, vault_id: String) -> Result<(), SsError> {
        let inner = self.inner.lock();
        inner
            .scheduler
            .confirm_vault_restored(&vault_id)
            .map_err(map_job_err)?;
        Ok(())
    }

    pub fn mark_job_committed_replace(&self, job_id: String) -> Result<JobView, SsError> {
        let (view, observer) = {
            let mut inner = self.inner.lock();
            let view = inner
                .scheduler
                .mark_job_committed_replace(&job_id)
                .map_err(map_job_err)?;
            (view, inner.observer.clone_handle())
        };
        observer.notify_job(&view, true);
        Ok(job_view_from_jobs(&view))
    }

    pub fn list_vault_items(&self) -> Result<Vec<VaultItemView>, SsError> {
        let inner = self.inner.lock();
        inner
            .scheduler
            .store()
            .list_vault()
            .map(|rows| {
                rows.into_iter()
                    .map(|row| VaultItemView {
                        id: row.id,
                        job_id: row.job_id,
                        vault_path: row.vault_path,
                        original_uri: row.original_uri,
                        compressed_uri: row.compressed_uri,
                        original_bytes: row.original_bytes,
                        expires_at: row.expires_at,
                        restored: row.restored,
                    })
                    .collect()
            })
            .map_err(|e| SsError::Db(e.to_string()))
    }

    pub fn set_settings(&self, settings: Settings) -> Result<(), SsError> {
        let mut inner = self.inner.lock();
        inner
            .scheduler
            .store()
            .settings()
            .set("app", &settings)
            .map_err(|e| SsError::Db(e.to_string()))?;
        inner.config.parallel_profile = settings.parallel_profile;
        inner
            .scheduler
            .set_parallel_profile(parallel_profile_to_jobs(settings.parallel_profile));
        inner.settings = settings;
        Ok(())
    }

    pub fn settings(&self) -> Settings {
        self.inner.lock().settings.clone()
    }

    pub fn on_platform_progress(&self, job_id: String, ratio: f32) -> Result<JobView, SsError> {
        let (view, observer) = {
            let mut inner = self.inner.lock();
            let view = inner
                .scheduler
                .on_platform_progress(&job_id, ratio)
                .map_err(map_job_err)?;
            (view, inner.observer.clone_handle())
        };
        observer.notify_job(&view, false);
        Ok(job_view_from_jobs(&view))
    }

    pub fn on_platform_succeeded(
        &self,
        job_id: String,
        output_path: String,
        _output_bytes: u64,
    ) -> Result<JobView, SsError> {
        let (view, work, observer) = {
            let mut inner = self.inner.lock();
            let view = inner
                .scheduler
                .complete_with_output_check(&job_id, &output_path)
                .map_err(map_job_err)?;
            if matches!(
                view.state,
                ss_jobs::JobState::Succeeded | ss_jobs::JobState::CommittedReplace
            ) {
                if let (Some(decision), Some(actual)) = (view.decision.as_ref(), view.output_bytes) {
                    let predicted = (decision.estimate_bytes_low + decision.estimate_bytes_high) / 2;
                    let kind = view.spec.fingerprint.kind;
                    match kind {
                        ss_domain::MediaKind::Image => {
                            inner.settings.estimate_scale_image = ss_domain::update_estimate_scale(
                                inner.settings.estimate_scale_image,
                                predicted,
                                actual,
                            );
                        }
                        ss_domain::MediaKind::Video => {
                            inner.settings.estimate_scale_video = ss_domain::update_estimate_scale(
                                inner.settings.estimate_scale_video,
                                predicted,
                                actual,
                            );
                        }
                        ss_domain::MediaKind::Audio => {
                            inner.settings.estimate_scale_audio = ss_domain::update_estimate_scale(
                                inner.settings.estimate_scale_audio,
                                predicted,
                                actual,
                            );
                        }
                    }
                    let _ = inner
                        .scheduler
                        .store()
                        .settings()
                        .set("app", &inner.settings);
                }
            }
            let work = inner.collect_dispatch_work(false).map_err(map_job_err)?;
            (view, work, inner.observer.clone_handle())
        };
        Self::execute_dispatch_work(self, work, &observer, false);
        observer.notify_job(&view, true);
        if let Ok(group) = self.group_for_job(&job_id) {
            observer.notify_group(&group, true);
        }
        Ok(job_view_from_jobs(&view))
    }

    pub fn on_platform_failed(
        &self,
        job_id: String,
        reason: FailReason,
        detail: String,
    ) -> Result<JobView, SsError> {
        let (view, work, observer) = {
            let mut inner = self.inner.lock();
            let view = inner
                .scheduler
                .on_platform_failed(&job_id, fail_reason_to_jobs(reason), &detail)
                .map_err(map_job_err)?;
            let work = inner.collect_dispatch_work(false).map_err(map_job_err)?;
            (view, work, inner.observer.clone_handle())
        };
        Self::execute_dispatch_work(self, work, &observer, false);
        observer.notify_job(&view, true);
        if reason == FailReason::DiskFull {
            if let Ok(group) = self.group_for_job(&job_id) {
                observer.notify_group(&group, true);
            }
        }
        Ok(job_view_from_jobs(&view))
    }
}

impl CoreEngine {
    fn mark_jobs_cancelled(&self, job_ids: &[String]) {
        let mut cancelled = self.cancelled_jobs.lock();
        for job_id in job_ids {
            cancelled.insert(job_id.clone());
        }
    }

    fn clear_cancelled_job(&self, job_id: &str) {
        self.cancelled_jobs.lock().remove(job_id);
    }

    fn is_job_cancelled(&self, job_id: &str) -> bool {
        self.cancelled_jobs.lock().contains(job_id)
    }

    fn job_is_running(&self, job_id: &str) -> bool {
        self.inner
            .lock()
            .scheduler
            .job(job_id)
            .ok()
            .is_some_and(|job| job.state == ss_jobs::JobState::Running)
    }

    fn begin_dispatched_job(&self, job_id: &str) -> bool {
        if !self.job_is_running(job_id) {
            return false;
        }
        self.clear_cancelled_job(job_id);
        if self.is_job_cancelled(job_id) || !self.job_is_running(job_id) {
            return false;
        }
        true
    }

    fn group_for_job(&self, job_id: &str) -> Result<ss_jobs::GroupView, SsError> {
        let inner = self.inner.lock();
        let group_id = inner
            .scheduler
            .store()
            .get_job(job_id)
            .map_err(|e| SsError::Db(e.to_string()))?
            .group_id;
        inner.scheduler.group(&group_id).map_err(map_job_err)
    }

    fn execute_dispatch_work(
        engine: &CoreEngine,
        work: DispatchWork,
        observer: &ObserverBridge,
        force: bool,
    ) {
        for job in work.notify_jobs {
            observer.notify_job(&job, force);
        }
        for video in work.videos {
            if engine.begin_dispatched_job(&video.job_id) {
                observer.request_start_video(&video.job_id, &video.recipe, &video.src, &video.dst);
            }
        }
        for audio in work.audios {
            if engine.begin_dispatched_job(&audio.job_id) {
                observer.request_start_audio(&audio.job_id, &audio.recipe, &audio.src, &audio.dst);
            }
        }
        for audio in work.audios_rust {
            let weak = engine.inner.lock().this.clone();
            thread::spawn(move || {
                if let Some(engine) = weak.upgrade() {
                    engine.run_audio_job_background(audio);
                }
            });
        }
        for image in work.images {
            let weak = engine.inner.lock().this.clone();
            thread::spawn(move || {
                if let Some(engine) = weak.upgrade() {
                    engine.run_image_job_background(image);
                }
            });
        }
    }

    fn run_audio_job_background(&self, audio: AudioStart) {
        if !self.begin_dispatched_job(&audio.job_id) {
            let _ = self.on_platform_failed(
                audio.job_id.clone(),
                FailReason::Cancelled,
                "fail.cancelled".into(),
            );
            return;
        }
        let result: Result<(), SsError> = (|| {
            let options = audio_options_from_recipe(&audio.recipe)
                .map_err(|e| SsError::Internal(e.to_string()))?;
            transcode_audio(
                Path::new(&audio.src),
                Path::new(&audio.dst),
                &options,
            )
            .map_err(|e| SsError::Internal(e.to_string()))?;
            Ok(())
        })();
        match result {
            Ok(()) => {
                let _ = self.on_platform_succeeded(audio.job_id, audio.dst, 0);
            }
            Err(err) => {
                let _ = self.on_platform_failed(
                    audio.job_id,
                    FailReason::EncodeFailed,
                    err.to_string(),
                );
            }
        }
    }

    fn run_image_job_background(&self, image: ImageStart) {
        if !self.begin_dispatched_job(&image.job_id) {
            let _ = self.on_platform_failed(
                image.job_id.clone(),
                FailReason::Cancelled,
                "fail.cancelled".into(),
            );
            return;
        }
        let job_id = image.job_id.clone();
        let result: Result<(), SsError> = (|| {
            let options =
                options_from_recipe(&image.recipe).map_err(|e| SsError::Internal(e.to_string()))?;
            let mut opts = options;
            opts.keep_gps = image.keep_gps;
            compress_image(
                Path::new(&image.input),
                Path::new(&image.output_path),
                &opts,
                Some(&|| self.is_job_cancelled(&job_id)),
            )
            .map_err(|e| match e {
                ss_image::ImageError::Cancelled => SsError::Internal("fail.cancelled".into()),
                other => SsError::Internal(other.to_string()),
            })?;
            Ok(())
        })();
        match result {
            Ok(()) => {
                let _ = self.on_platform_succeeded(
                    image.job_id,
                    image.output_path,
                    0,
                );
            }
            Err(err) => {
                let reason = if err.to_string().contains("fail.cancelled") {
                    FailReason::Cancelled
                } else {
                    FailReason::EncodeFailed
                };
                let _ = self.on_platform_failed(image.job_id, reason, err.to_string());
            }
        }
    }
}

impl EngineInner {
    fn collect_dispatch_work(&mut self, force: bool) -> Result<DispatchWork, ss_jobs::JobError> {
        let started = self.scheduler.dispatch_ready_jobs()?;
        let mut work = DispatchWork {
            notify_jobs: Vec::new(),
            videos: Vec::new(),
            audios: Vec::new(),
            audios_rust: Vec::new(),
            images: Vec::new(),
        };
        for job in started {
            work.notify_jobs.push(job.to_view());
            match job.spec.fingerprint.kind {
                DMediaKind::Video => {
                    if let Some(recipe) = recipe_for(&job) {
                        let dst = self
                            .scheduler
                            .tmp_output_path(&job.spec.id)
                            .to_string_lossy()
                            .into_owned();
                        work.videos.push(VideoStart {
                            job_id: job.spec.id.clone(),
                            recipe,
                            src: job.spec.source_uri.clone(),
                            dst,
                        });
                    }
                }
                DMediaKind::Image => {
                    if let Some(input) = job.spec.local_path.clone().filter(|p| !p.is_empty()) {
                        if let Some(recipe) = recipe_for(&job) {
                            let keep_gps = job
                                .spec
                                .request
                                .as_ref()
                                .map(|r| r.keep_gps)
                                .unwrap_or(self.settings.keep_gps);
                            let ext = match recipe
                                .image_out_format
                                .as_deref()
                                .unwrap_or("jpeg")
                                .to_ascii_lowercase()
                                .as_str()
                            {
                                "webp" => "webp",
                                "png" => "png",
                                _ => "jpg",
                            };
                            let output_path = self
                                .scheduler
                                .tmp_output_path_for(&job.spec.id, ext)
                                .to_string_lossy()
                                .into_owned();
                            work.images.push(ImageStart {
                                job_id: job.spec.id.clone(),
                                input,
                                recipe,
                                output_path,
                                keep_gps,
                            });
                        }
                    }
                }
                DMediaKind::Audio => {
                    let job_id = job.spec.id.clone();
                    let input = job
                        .spec
                        .local_path
                        .clone()
                        .or(Some(job.spec.source_uri.clone()));
                    if let Some(recipe) = recipe_for(&job) {
                        if let Some(path) = input {
                            if path.starts_with('/') || path.contains(":\\") {
                                let _ = validate_source(Path::new(&path));
                            }
                            let ext = if recipe.audio_out_format.as_deref() == Some("opus") {
                                "ogg"
                            } else {
                                "m4a"
                            };
                            let dst = self
                                .scheduler
                                .tmp_output_path_for(&job_id, ext)
                                .to_string_lossy()
                                .into_owned();
                            let is_opus = recipe.audio_out_format.as_deref() == Some("opus");
                            let start = AudioStart {
                                job_id,
                                recipe,
                                src: path,
                                dst,
                            };
                            if is_opus {
                                work.audios_rust.push(start);
                            } else {
                                work.audios.push(start);
                            }
                        }
                    }
                }
            }
        }
        if !force {
            // notify_jobs already included in started; force flag only affects throttle in observer
        }
        Ok(work)
    }
}

fn ensure_dir(path: &str) -> Result<(), SsError> {
    if path.trim().is_empty() {
        return Err(SsError::InvalidArgument("directory path is empty".into()));
    }
    let p = Path::new(path);
    fs::create_dir_all(p).map_err(|e| SsError::Io(format!("{}: {e}", p.display())))?;
    Ok(())
}

fn now_ms() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn now_secs() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

fn map_job_err(err: ss_jobs::JobError) -> SsError {
    match err {
        ss_jobs::JobError::NotFound(_) => SsError::NotFound,
        ss_jobs::JobError::Db(e) => SsError::Db(e.to_string()),
        ss_jobs::JobError::Io(msg) => SsError::Io(msg),
        ss_jobs::JobError::InvalidState(msg) => SsError::InvalidArgument(msg),
    }
}
