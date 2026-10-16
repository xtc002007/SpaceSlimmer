use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use ss_domain::{decide, HwCaps, MediaKind};
use ss_sqlite::{GroupRow, JobRow, Store};
use uuid::Uuid;

use crate::error::JobError;
use crate::quotas::{ParallelProfile, quotas_for_profile};
use crate::telemetry;
use crate::types::{
    build_group_view, FailReason, GroupState, GroupView,
    JobRecord, JobSpec, JobState, JobView, VaultRestoreInfo, recipe_for,
    skip_message_for_fingerprint, skip_reason_as_str, skip_reason_message_key, skip_reason_parse,
};

#[derive(Clone)]
pub struct SchedulerConfig {
    pub data_dir: PathBuf,
    pub tmp_dir: PathBuf,
    pub vault_dir: PathBuf,
    pub parallel_profile: ParallelProfile,
}

pub struct JobScheduler {
    store: Store,
    config: SchedulerConfig,
    hw_caps: HwCaps,
    thermal_level: u8,
    disk_full_halt: bool,
    dual_video_hw: bool,
    /// Jobs that should re-queue (not fail) when a pause-cancel platform ack arrives after resume.
    resume_after_cancel_ack: HashSet<String>,
}

impl JobScheduler {
    pub fn open(config: SchedulerConfig) -> Result<Self, JobError> {
        fs::create_dir_all(&config.data_dir)
            .map_err(|e| JobError::Io(format!("create data_dir: {e}")))?;
        fs::create_dir_all(&config.tmp_dir)
            .map_err(|e| JobError::Io(format!("create tmp_dir: {e}")))?;
        fs::create_dir_all(&config.vault_dir)
            .map_err(|e| JobError::Io(format!("create vault_dir: {e}")))?;
        let db_path = config.data_dir.join("spaceslimmer.db");
        let store = Store::open(&db_path).map_err(JobError::from)?;
        let scheduler = Self {
            store,
            config,
            hw_caps: HwCaps::default(),
            thermal_level: 0,
            disk_full_halt: false,
            dual_video_hw: false,
            resume_after_cancel_ack: HashSet::new(),
        };
        scheduler.recover_after_crash()?;
        Ok(scheduler)
    }

    pub fn open_in_memory(config: SchedulerConfig) -> Result<Self, JobError> {
        let store = Store::open_in_memory().map_err(JobError::from)?;
        Ok(Self {
            store,
            config,
            hw_caps: HwCaps::default(),
            thermal_level: 0,
            disk_full_halt: false,
            dual_video_hw: false,
            resume_after_cancel_ack: HashSet::new(),
        })
    }

    pub fn set_hw_caps(&mut self, caps: HwCaps) {
        self.hw_caps = caps;
    }

    pub fn update_thermal(&mut self, level: u8) {
        self.thermal_level = level.min(3);
    }

    pub fn set_dual_video_hw(&mut self, enabled: bool) {
        self.dual_video_hw = enabled;
    }

    pub fn set_parallel_profile(&mut self, profile: ParallelProfile) {
        self.config.parallel_profile = profile;
    }

    pub fn store(&self) -> &Store {
        &self.store
    }

    pub fn tmp_dir(&self) -> &Path {
        &self.config.tmp_dir
    }

    pub fn vault_dir(&self) -> &Path {
        &self.config.vault_dir
    }

    pub fn register_vault_backup(
        &self,
        job_id: &str,
        vault_path: &str,
        original_uri: &str,
        original_bytes: u64,
        expires_at: i64,
    ) -> Result<String, JobError> {
        let id = Uuid::new_v4().to_string();
        self.store
            .insert_vault(&ss_sqlite::VaultRow {
                id: id.clone(),
                job_id: job_id.to_string(),
                vault_path: vault_path.to_string(),
                original_uri: original_uri.to_string(),
                compressed_uri: String::new(),
                original_bytes,
                expires_at,
                restored: false,
            })
            .map_err(JobError::from)?;
        Ok(id)
    }

    pub fn set_vault_expires_at(&self, vault_id: &str, expires_at: i64) -> Result<(), JobError> {
        self.store
            .extend_vault_expiry(vault_id, expires_at)
            .map_err(JobError::from)
    }

    pub fn purge_expired_vault(&self, now: i64) -> Result<Vec<String>, JobError> {
        let deleted = self
            .store
            .delete_expired_vault_items(now)
            .map_err(JobError::from)?;
        Ok(deleted.into_iter().map(|(id, _)| id).collect())
    }

    pub fn record_event(
        &self,
        event_type: &str,
        payload_json: &str,
    ) -> Result<String, JobError> {
        let id = Uuid::new_v4().to_string();
        let ts = now_ms();
        let payload = telemetry::sanitize_event_payload(payload_json);
        self.store
            .insert_event(&id, ts, event_type, &payload)
            .map_err(JobError::from)?;
        Ok(id)
    }

    pub fn list_events(&self, limit: u32) -> Result<Vec<(String, i64, String, String)>, JobError> {
        self.store.list_events(limit).map_err(JobError::from)
    }

    pub fn restore_vault_item(&self, vault_id: &str) -> Result<VaultRestoreInfo, JobError> {
        let row = self.store.get_vault_item(vault_id).map_err(JobError::from)?;
        if row.restored {
            return Err(JobError::InvalidState("vault item already restored".into()));
        }
        Ok(VaultRestoreInfo {
            vault_path: row.vault_path,
            original_uri: row.original_uri,
        })
    }

    pub fn confirm_vault_restored(&self, vault_id: &str) -> Result<(), JobError> {
        let row = self.store.get_vault_item(vault_id).map_err(JobError::from)?;
        if row.restored {
            return Err(JobError::InvalidState("vault item already restored".into()));
        }
        self.store
            .mark_vault_restored(vault_id)
            .map_err(JobError::from)?;
        Ok(())
    }

    pub fn extend_vault_item(&self, vault_id: &str, days: u32) -> Result<ss_sqlite::VaultRow, JobError> {
        if days == 0 {
            return Err(JobError::InvalidState("days must be > 0".into()));
        }
        let row = self.store.get_vault_item(vault_id).map_err(JobError::from)?;
        if row.restored {
            return Err(JobError::InvalidState("vault item already restored".into()));
        }
        self.store
            .extend_vault_expires(vault_id, days)
            .map_err(JobError::from)
    }

    /// Running jobs in a group that should receive cancel before pause/cancel.
    pub fn running_job_ids_in_group(
        &self,
        group_id: &str,
    ) -> Result<Vec<String>, JobError> {
        self.job_ids_in_group_with_states(group_id, &[JobState::Running])
    }

    pub fn cancelling_job_ids_in_group(
        &self,
        group_id: &str,
    ) -> Result<Vec<String>, JobError> {
        self.job_ids_in_group_with_states(group_id, &[JobState::Cancelling])
    }

    pub fn inflight_job_ids_in_group(
        &self,
        group_id: &str,
    ) -> Result<Vec<String>, JobError> {
        self.job_ids_in_group_with_states(group_id, &[JobState::Running, JobState::Cancelling])
    }

    fn job_ids_in_group_with_states(
        &self,
        group_id: &str,
        states: &[JobState],
    ) -> Result<Vec<String>, JobError> {
        let jobs = self
            .store
            .list_jobs_for_group(group_id)
            .map_err(JobError::from)?;
        let mut ids = Vec::new();
        for row in jobs {
            let record = self.row_to_record(&row)?;
            if states.contains(&record.state) {
                ids.push(record.spec.id);
            }
        }
        Ok(ids)
    }

    pub fn mark_resume_after_cancel_ack(&mut self, job_id: &str) {
        self.resume_after_cancel_ack.insert(job_id.to_string());
    }

    pub fn clear_resume_after_cancel_ack(&mut self, job_id: &str) {
        self.resume_after_cancel_ack.remove(job_id);
    }

    pub fn disk_full_halt(&self) -> bool {
        self.disk_full_halt
    }

    pub fn clear_disk_full_halt(&mut self) {
        self.disk_full_halt = false;
    }

    fn pause_all_active_groups(&mut self) -> Result<(), JobError> {
        let groups = self.store.list_groups().map_err(JobError::from)?;
        for group in groups {
            if group.state == GroupState::Active.as_str() {
                let _ = self.pause_group(&group.id)?;
            }
        }
        Ok(())
    }

    pub fn mark_job_committed_replace(&mut self, job_id: &str) -> Result<JobView, JobError> {
        let mut record = self.row_to_record(
            &self.store.get_job(job_id).map_err(JobError::from)?,
        )?;
        record.state = JobState::CommittedReplace;
        record.updated_at = now_ms();
        self.update_job_record(&record)?;
        let compressed_uri = record.spec.source_uri.clone();
        let _ = self
            .store
            .set_vault_compressed_uri_for_job(job_id, &compressed_uri);
        Ok(record.to_view())
    }

    pub fn recover_after_crash(&self) -> Result<Vec<String>, JobError> {
        let now = now_ms();
        let mut recovered = Vec::new();
        for state in [JobState::Running, JobState::Cancelling] {
            let rows = self
                .store
                .list_jobs_by_state(state.as_str())
                .map_err(JobError::from)?;
            for row in rows {
                let group = self.store.get_group(&row.group_id).map_err(JobError::from)?;
                let mut record = self.row_to_record(&row)?;
                match GroupState::parse(&group.state) {
                    Some(GroupState::Paused) => {
                        record.state = JobState::Paused;
                        record.fail = None;
                        record.message = String::new();
                    }
                    Some(GroupState::Cancelled) => {
                        record.state = JobState::Failed;
                        record.fail = Some(FailReason::Cancelled);
                        record.message = FailReason::Cancelled.message_key().to_string();
                    }
                    _ => {
                        record.state = JobState::Queued;
                        record.fail = None;
                        record.message = String::new();
                    }
                }
                record.progress = 0.0;
                record.updated_at = now;
                self.update_job_record(&record)?;
                self.cleanup_tmp_for_job(&record.spec.id)?;
                recovered.push(record.spec.id);
            }
        }
        Ok(recovered)
    }

    pub fn submit_group(
        &mut self,
        specs: Vec<JobSpec>,
        request: ss_domain::CompressRequest,
        start_paused: bool,
    ) -> Result<String, JobError> {
        if specs.is_empty() {
            return Err(JobError::InvalidState("submit_group requires jobs".into()));
        }

        let group_id = Uuid::new_v4().to_string();
        let created_at = now_ms();
        let request_json = serde_json::to_string(&request).map_err(|e| JobError::Io(e.to_string()))?;
        let group_state = if start_paused {
            GroupState::Paused
        } else {
            self.disk_full_halt = false;
            GroupState::Active
        };

        self.store
            .insert_group(&GroupRow {
                id: group_id.clone(),
                created_at,
                request_json,
                state: group_state.as_str().to_string(),
            })
            .map_err(JobError::from)?;

        for spec in specs {
            let job_req = spec.request.as_ref().unwrap_or(&request);
            let decision = decide(&spec.fingerprint, job_req, &self.hw_caps);
            let state = if decision.skip {
                JobState::Skipped
            } else {
                JobState::Queued
            };
            let skip = decision.skip_reason;
            let message = if decision.skip {
                skip_message_for_fingerprint(&spec.fingerprint, skip)
            } else {
                String::new()
            };
            let recipe_json = decision
                .recipe
                .as_ref()
                .map(|r| serde_json::to_string(r))
                .transpose()
                .map_err(|e| JobError::Io(e.to_string()))?;
            let decision_json = serde_json::to_string(&decision)
                .map_err(|e| JobError::Io(e.to_string()))?;

            let record = JobRecord {
                spec,
                state,
                progress: if state == JobState::Skipped { 1.0 } else { 0.0 },
                decision: Some(decision),
                output_uri: None,
                output_bytes: None,
                saved_bytes: None,
                fail: None,
                skip,
                message,
                updated_at: created_at,
            };
            self.persist_job(&group_id, &record, &decision_json, recipe_json)?;
            let fp = &record.spec.fingerprint;
            let codec = fp
                .video_codec
                .as_deref()
                .or(fp.audio_codec.as_deref())
                .unwrap_or("");
            let kind = match fp.kind {
                MediaKind::Video => "video",
                MediaKind::Image => "image",
                MediaKind::Audio => "audio",
            };
            let _ = self.record_event(
                "probe_done",
                &telemetry::probe_done_payload(kind, codec, fp.width, fp.height, fp.duration_ms),
            );
            if record.state == JobState::Skipped {
                let _ = self.record_event(
                    "job_finished",
                    &telemetry::job_finished_payload(
                        "skip",
                        skip_reason_as_str(record.skip.unwrap_or(ss_domain::SkipReason::EstimateNotWorth)),
                        fp.file_bytes,
                        fp.file_bytes,
                        false,
                    ),
                );
            }
        }

        Ok(group_id)
    }

    pub fn list_groups(&self) -> Result<Vec<GroupView>, JobError> {
        let groups = self.store.list_groups().map_err(JobError::from)?;
        groups
            .into_iter()
            .map(|g| self.load_group_view(&g.id))
            .collect()
    }

    pub fn group(&self, group_id: &str) -> Result<GroupView, JobError> {
        self.load_group_view(group_id)
    }

    pub fn job(&self, job_id: &str) -> Result<JobView, JobError> {
        let row = self.store.get_job(job_id).map_err(JobError::from)?;
        Ok(self.row_to_record(&row)?.to_view())
    }

    pub fn pause_group(&mut self, group_id: &str) -> Result<GroupView, JobError> {
        let jobs = self.store.list_jobs_for_group(group_id).map_err(JobError::from)?;
        let mut has_inflight = false;
        let mut has_queued = false;
        for row in &jobs {
            let record = self.row_to_record(row)?;
            match record.state {
                JobState::Running | JobState::Cancelling => has_inflight = true,
                JobState::Queued => has_queued = true,
                _ => {}
            }
        }
        if !has_inflight && !has_queued {
            if let Some(row) = jobs.first() {
                let record = self.row_to_record(row)?;
                self.maybe_complete_group(&record)?;
            }
            return self.load_group_view(group_id);
        }
        self.store
            .update_group_state(group_id, GroupState::Paused.as_str())
            .map_err(JobError::from)?;
        for row in jobs {
            let mut record = self.row_to_record(&row)?;
            match record.state {
                JobState::Running => {
                    record.state = JobState::Cancelling;
                    record.updated_at = now_ms();
                    self.update_job_record(&record)?;
                }
                JobState::Queued => {
                    record.state = JobState::Paused;
                    record.updated_at = now_ms();
                    self.update_job_record(&record)?;
                }
                JobState::Cancelling => {
                    self.clear_resume_after_cancel_ack(&record.spec.id);
                }
                _ => {}
            }
        }
        self.load_group_view(group_id)
    }

    pub fn resume_group(&mut self, group_id: &str) -> Result<GroupView, JobError> {
        self.disk_full_halt = false;
        self.store
            .update_group_state(group_id, GroupState::Active.as_str())
            .map_err(JobError::from)?;
        let jobs = self.store.list_jobs_for_group(group_id).map_err(JobError::from)?;
        for row in jobs {
            let mut record = self.row_to_record(&row)?;
            if record.state == JobState::Paused {
                record.state = JobState::Queued;
                record.fail = None;
                record.message = String::new();
                record.progress = 0.0;
                record.updated_at = now_ms();
                self.update_job_record(&record)?;
            }
        }
        self.load_group_view(group_id)
    }

    pub fn cancel_group(&mut self, group_id: &str) -> Result<GroupView, JobError> {
        self.store
            .update_group_state(group_id, GroupState::Cancelled.as_str())
            .map_err(JobError::from)?;
        let jobs = self.store.list_jobs_for_group(group_id).map_err(JobError::from)?;
        for row in jobs {
            let mut record = self.row_to_record(&row)?;
            match record.state {
                JobState::Running => {
                    record.state = JobState::Cancelling;
                    record.updated_at = now_ms();
                    self.update_job_record(&record)?;
                }
                JobState::Queued | JobState::Paused | JobState::Cancelling => {
                    self.clear_resume_after_cancel_ack(&record.spec.id);
                    self.cleanup_tmp_for_job(&record.spec.id)?;
                    record.state = JobState::Failed;
                    record.fail = Some(FailReason::Cancelled);
                    record.message = FailReason::Cancelled.message_key().to_string();
                    record.progress = 0.0;
                    record.updated_at = now_ms();
                    self.update_job_record(&record)?;
                }
                _ => {}
            }
        }
        self.load_group_view(group_id)
    }

    pub fn cancel_job(&mut self, job_id: &str) -> Result<JobView, JobError> {
        let row = self.store.get_job(job_id).map_err(JobError::from)?;
        let mut record = self.row_to_record(&row)?;
        match record.state {
            JobState::Running => {
                self.clear_resume_after_cancel_ack(job_id);
                record.state = JobState::Cancelling;
                record.updated_at = now_ms();
                self.update_job_record(&record)?;
            }
            JobState::Cancelling => {
                self.clear_resume_after_cancel_ack(job_id);
            }
            JobState::Queued | JobState::Paused => {
                self.clear_resume_after_cancel_ack(job_id);
                self.cleanup_tmp_for_job(job_id)?;
                record.state = JobState::Failed;
                record.fail = Some(FailReason::Cancelled);
                record.message = FailReason::Cancelled.message_key().to_string();
                record.progress = 0.0;
                record.updated_at = now_ms();
                self.update_job_record(&record)?;
            }
            _ => {}
        }
        Ok(record.to_view())
    }

    /// Deletes a finished job. Returns the group id when the group still exists.
    pub fn delete_job(&mut self, job_id: &str) -> Result<Option<String>, JobError> {
        let row = self.store.get_job(job_id).map_err(JobError::from)?;
        let record = self.row_to_record(&row)?;
        match record.state {
            JobState::Running | JobState::Queued | JobState::Paused | JobState::Cancelling => {
                return Err(JobError::InvalidState("cannot delete in-flight job".into()));
            }
            _ => {}
        }
        let group_id = self.store.delete_job(job_id).map_err(JobError::from)?;
        if self.store.count_jobs_in_group(&group_id).map_err(JobError::from)? == 0 {
            self.store.delete_group(&group_id).map_err(JobError::from)?;
            Ok(None)
        } else {
            Ok(Some(group_id))
        }
    }

    pub fn retry_job(&mut self, job_id: &str) -> Result<JobView, JobError> {
        self.disk_full_halt = false;
        let row = self.store.get_job(job_id).map_err(JobError::from)?;
        let group_id = row.group_id.clone();
        let mut record = self.row_to_record(&row)?;
        if !matches!(record.state, JobState::Failed | JobState::Skipped) {
            return Err(JobError::InvalidState(format!(
                "job {} cannot retry from {}",
                job_id,
                record.state.as_str()
            )));
        }
        if record.skip.is_some() && record.decision.as_ref().is_some_and(|d| d.skip) {
            return Err(JobError::InvalidState("skipped jobs are not retriable".into()));
        }
        self.cleanup_tmp_for_job(job_id)?;
        record.state = JobState::Queued;
        record.progress = 0.0;
        record.fail = None;
        record.skip = None;
        record.output_uri = None;
        record.output_bytes = None;
        record.saved_bytes = None;
        record.message = String::new();
        record.updated_at = now_ms();
        self.update_job_record(&record)?;
        self.store
            .update_group_state(&group_id, GroupState::Active.as_str())
            .map_err(JobError::from)?;
        Ok(record.to_view())
    }

    /// Resume dispatch for groups left Active with queued jobs after crash recovery.
    pub fn groups_needing_dispatch(&self) -> Result<Vec<String>, JobError> {
        let groups = self.store.list_groups().map_err(JobError::from)?;
        let mut ids = Vec::new();
        for group in groups {
            if group.state != GroupState::Active.as_str() {
                continue;
            }
            let jobs = self
                .store
                .list_jobs_for_group(&group.id)
                .map_err(JobError::from)?;
            if jobs.iter().any(|j| j.state == JobState::Queued.as_str()) {
                ids.push(group.id);
            }
        }
        Ok(ids)
    }

    /// Returns jobs that should start platform/Rust work, respecting parallelism quotas.
    pub fn dispatch_ready_jobs(&mut self) -> Result<Vec<JobRecord>, JobError> {
        if self.disk_full_halt {
            return Ok(Vec::new());
        }
        let quotas = quotas_for_profile(
            self.config.parallel_profile,
            self.thermal_level,
            self.dual_video_hw,
        );
        let mut running_video = 0u32;
        let mut running_image = 0u32;
        let mut running_audio = 0u32;
        for state in [JobState::Running, JobState::Cancelling] {
            let rows = self
                .store
                .list_jobs_by_state(state.as_str())
                .map_err(JobError::from)?;
            for row in rows {
                let record = self.row_to_record(&row)?;
                match record.spec.fingerprint.kind {
                    MediaKind::Video => running_video += 1,
                    MediaKind::Image => running_image += 1,
                    MediaKind::Audio => running_audio += 1,
                }
            }
        }

        let queued = self
            .store
            .list_jobs_by_state(JobState::Queued.as_str())
            .map_err(JobError::from)?;
        let mut started = Vec::new();
        for row in queued {
            let group_state = self
                .store
                .get_group(&row.group_id)
                .map_err(JobError::from)?;
            if group_state.state == GroupState::Paused.as_str()
                || group_state.state == GroupState::Cancelled.as_str()
            {
                continue;
            }

            let record = self.row_to_record(&row)?;
            let can_start = match record.spec.fingerprint.kind {
                MediaKind::Video => running_video < quotas.video,
                MediaKind::Image => running_image < quotas.image,
                MediaKind::Audio => running_audio < quotas.audio,
            };
            if !can_start {
                continue;
            }

            if let Err(msg) = self.validate_dispatch(&record) {
                let mut failed = record;
                failed.state = JobState::Failed;
                failed.fail = Some(FailReason::EncodeFailed);
                failed.message = msg;
                failed.progress = 0.0;
                failed.updated_at = now_ms();
                self.update_job_record(&failed)?;
                continue;
            }

            let mut running_record = record;
            running_record.state = JobState::Running;
            running_record.updated_at = now_ms();
            self.update_job_record(&running_record)?;

            match running_record.spec.fingerprint.kind {
                MediaKind::Video => running_video += 1,
                MediaKind::Image => running_image += 1,
                MediaKind::Audio => running_audio += 1,
            }
            started.push(running_record);
        }
        Ok(started)
    }

    pub fn on_platform_progress(&mut self, job_id: &str, ratio: f32) -> Result<JobView, JobError> {
        let mut record = self.row_to_record(
            &self.store.get_job(job_id).map_err(JobError::from)?,
        )?;
        if record.state == JobState::Running {
            record.progress = ratio.clamp(0.0, 1.0);
            record.updated_at = now_ms();
            self.update_job_record(&record)?;
        }
        Ok(record.to_view())
    }

    pub fn on_platform_succeeded(
        &mut self,
        job_id: &str,
        output_path: &str,
        output_bytes: u64,
    ) -> Result<JobView, JobError> {
        let mut record = self.row_to_record(
            &self.store.get_job(job_id).map_err(JobError::from)?,
        )?;
        if record.state != JobState::Running {
            if matches!(
                record.state,
                JobState::Succeeded | JobState::CommittedReplace | JobState::Skipped
            ) {
                return Ok(record.to_view());
            }
            if record.state == JobState::Cancelling {
                return self.finalize_cancelling(job_id, record);
            }
            if record.state == JobState::Paused {
                return Ok(record.to_view());
            }
            return Ok(record.to_view());
        }
        let source_bytes = record.spec.fingerprint.file_bytes;
        record.state = JobState::Succeeded;
        record.progress = 1.0;
        record.output_uri = Some(output_path.to_string());
        record.output_bytes = Some(output_bytes);
        record.saved_bytes = Some((source_bytes as i64) - (output_bytes as i64));
        record.message = String::new();
        record.updated_at = now_ms();
        self.update_job_record(&record)?;
        self.maybe_complete_group(&record)?;
        let hw = matches!(record.spec.fingerprint.kind, MediaKind::Video);
        let _ = self.record_event(
            "job_finished",
            &telemetry::job_finished_payload(
                "ok",
                kind_preset_label(&record),
                source_bytes,
                output_bytes,
                hw,
            ),
        );
        Ok(record.to_view())
    }

    pub fn on_platform_failed(
        &mut self,
        job_id: &str,
        reason: FailReason,
        detail: &str,
    ) -> Result<JobView, JobError> {
        let mut record = self.row_to_record(
            &self.store.get_job(job_id).map_err(JobError::from)?,
        )?;
        if record.state == JobState::Paused {
            return Ok(record.to_view());
        }
        if record.state == JobState::Cancelling {
            return self.finalize_cancelling(job_id, record);
        }
        if record.state != JobState::Running {
            if matches!(
                record.state,
                JobState::Succeeded | JobState::CommittedReplace | JobState::Skipped
            ) {
                return Ok(record.to_view());
            }
            if record.state == JobState::Paused {
                return Ok(record.to_view());
            }
            return Ok(record.to_view());
        }
        self.cleanup_tmp_for_job(job_id)?;
        record.state = JobState::Failed;
        record.fail = Some(reason);
        record.message = if detail.is_empty() {
            reason.message_key().to_string()
        } else {
            detail.to_string()
        };
        record.progress = 0.0;
        record.updated_at = now_ms();
        self.update_job_record(&record)?;
        if reason == FailReason::DiskFull {
            self.disk_full_halt = true;
            let _ = self.pause_all_active_groups();
        }
        let _ = self.record_event(
            "job_finished",
            &telemetry::job_finished_payload(
                "fail",
                reason.as_str(),
                record.spec.fingerprint.file_bytes,
                0,
                matches!(record.spec.fingerprint.kind, MediaKind::Video),
            ),
        );
        Ok(record.to_view())
    }

    /// Finalize a produced output file, skipping when the output is not smaller.
    pub fn complete_with_output_check(
        &mut self,
        job_id: &str,
        output_path: &str,
    ) -> Result<JobView, JobError> {
        let output_bytes = match fs::metadata(output_path) {
            Ok(meta) => meta.len(),
            Err(e) => {
                if e.kind() == std::io::ErrorKind::StorageFull {
                    return self.on_platform_failed(job_id, FailReason::DiskFull, "fail.disk_full");
                }
                return Err(JobError::Io(e.to_string()));
            }
        };
        let record = self.row_to_record(
            &self.store.get_job(job_id).map_err(JobError::from)?,
        )?;
        if record.state != JobState::Running {
            if matches!(
                record.state,
                JobState::Succeeded | JobState::CommittedReplace | JobState::Skipped
            ) {
                return Ok(record.to_view());
            }
            if record.state == JobState::Cancelling {
                return self.finalize_cancelling(job_id, record);
            }
            if record.state == JobState::Paused {
                return Ok(record.to_view());
            }
            return Ok(record.to_view());
        }
        if output_bytes >= record.spec.fingerprint.file_bytes {
            self.cleanup_tmp_for_job(job_id)?;
            let mut skipped = record;
            skipped.state = JobState::Skipped;
            skipped.progress = 1.0;
            skipped.skip = Some(ss_domain::SkipReason::WouldGrow);
            skipped.message =
                skip_reason_message_key(ss_domain::SkipReason::WouldGrow).to_string();
            skipped.output_uri = None;
            skipped.output_bytes = None;
            skipped.saved_bytes = None;
            skipped.updated_at = now_ms();
            self.update_job_record(&skipped)?;
            self.maybe_complete_group(&skipped)?;
            let _ = self.record_event(
                "job_finished",
                &telemetry::job_finished_payload(
                    "skip",
                    "WouldGrow",
                    skipped.spec.fingerprint.file_bytes,
                    output_bytes,
                    matches!(skipped.spec.fingerprint.kind, MediaKind::Video),
                ),
            );
            return Ok(skipped.to_view());
        }
        self.on_platform_succeeded(job_id, output_path, output_bytes)
    }

    pub fn tmp_output_path_for(&self, job_id: &str, ext: &str) -> PathBuf {
        self.config.tmp_dir.join(format!("{job_id}.{ext}"))
    }

    /// Sprint 2 stub: mark non-video queued/running jobs succeeded with estimated output.
    #[cfg(feature = "test-stubs")]
    pub fn stub_complete_job(&mut self, job_id: &str) -> Result<JobView, JobError> {
        let record = self.row_to_record(
            &self.store.get_job(job_id).map_err(JobError::from)?,
        )?;
        if record.spec.fingerprint.kind == MediaKind::Video {
            return Err(JobError::InvalidState(
                "video jobs require platform callback".into(),
            ));
        }
        let estimate = record
            .decision
            .as_ref()
            .map(|d| (d.estimate_bytes_low + d.estimate_bytes_high) / 2)
            .unwrap_or(record.spec.fingerprint.file_bytes);
        let output_path = self.tmp_output_path(job_id);
        if let Some(parent) = output_path.parent() {
            fs::create_dir_all(parent)
                .map_err(|e| JobError::Io(format!("create tmp parent: {e}")))?;
        }
        fs::write(&output_path, &[] as &[u8])
            .map_err(|e| JobError::Io(format!("write stub output: {e}")))?;
        self.on_platform_succeeded(job_id, &output_path.to_string_lossy(), estimate)
    }

    pub fn tmp_output_path(&self, job_id: &str) -> PathBuf {
        self.config.tmp_dir.join(job_id)
    }

    fn group_state_for_job(&self, job_id: &str) -> Result<GroupState, JobError> {
        let row = self.store.get_job(job_id).map_err(JobError::from)?;
        let group = self.store.get_group(&row.group_id).map_err(JobError::from)?;
        GroupState::parse(&group.state).ok_or_else(|| {
            JobError::InvalidState(format!("unknown group state {}", group.state))
        })
    }

    fn finalize_cancelling(
        &mut self,
        job_id: &str,
        mut record: JobRecord,
    ) -> Result<JobView, JobError> {
        self.cleanup_tmp_for_job(job_id)?;
        let group_state = self.group_state_for_job(job_id)?;
        if group_state == GroupState::Paused {
            record.state = JobState::Paused;
            record.fail = None;
            record.message = String::new();
        } else if group_state == GroupState::Active
            && self.resume_after_cancel_ack.remove(job_id)
        {
            record.state = JobState::Queued;
            record.fail = None;
            record.message = String::new();
            record.progress = 0.0;
        } else {
            record.state = JobState::Failed;
            record.fail = Some(FailReason::Cancelled);
            record.message = FailReason::Cancelled.message_key().to_string();
            record.progress = 0.0;
        }
        record.updated_at = now_ms();
        self.update_job_record(&record)?;
        self.maybe_complete_group(&record)?;
        Ok(record.to_view())
    }

    fn maybe_complete_group(&self, job: &JobRecord) -> Result<(), JobError> {
        let row = self.store.get_job(&job.spec.id).map_err(JobError::from)?;
        let group = self.store.get_group(&row.group_id).map_err(JobError::from)?;
        if group.state == GroupState::Cancelled.as_str()
            || group.state == GroupState::Paused.as_str()
        {
            return Ok(());
        }
        let jobs = self
            .store
            .list_jobs_for_group(&row.group_id)
            .map_err(JobError::from)?;
        let all_terminal = jobs.iter().all(|j| {
            JobState::parse(&j.state).is_some_and(|s| {
                matches!(
                    s,
                    JobState::Succeeded
                        | JobState::Skipped
                        | JobState::Failed
                        | JobState::CommittedReplace
                )
            })
        });
        if all_terminal {
            self.store
                .update_group_state(&row.group_id, GroupState::Completed.as_str())
                .map_err(JobError::from)?;
        }
        Ok(())
    }

    fn load_group_view(&self, group_id: &str) -> Result<GroupView, JobError> {
        let group_row = self.store.get_group(group_id).map_err(JobError::from)?;
        let request: ss_domain::CompressRequest =
            serde_json::from_str(&group_row.request_json).map_err(|e| JobError::Io(e.to_string()))?;
        let rows = self
            .store
            .list_jobs_for_group(group_id)
            .map_err(JobError::from)?;
        let jobs = rows
            .iter()
            .map(|row| self.row_to_record(row))
            .collect::<Result<Vec<_>, _>>()?;
        let output_policy = request.output_policy;
        Ok(build_group_view(
            group_id.to_string(),
            group_row.created_at,
            request,
            jobs,
            output_policy,
        ))
    }

    fn persist_job(
        &self,
        group_id: &str,
        record: &JobRecord,
        decision_json: &str,
        recipe_json: Option<String>,
    ) -> Result<(), JobError> {
        let spec_json = serde_json::to_string(&record.spec)
            .map_err(|e| JobError::Io(e.to_string()))?;
        self.store
            .insert_job(&JobRow {
                id: record.spec.id.clone(),
                group_id: group_id.to_string(),
                spec_json,
                state: record.state.as_str().to_string(),
                progress: record.progress,
                decision_json: Some(decision_json.to_string()),
                recipe_json,
                output_uri: record.output_uri.clone(),
                output_bytes: record.output_bytes,
                saved_bytes: record.saved_bytes,
                skip_reason: record.skip.map(skip_reason_as_str).map(str::to_string),
                fail_reason: record.fail.map(|f| f.as_str().to_string()),
                message: record.message.clone(),
                updated_at: record.updated_at,
            })
            .map_err(JobError::from)
    }

    fn update_job_record(&self, record: &JobRecord) -> Result<(), JobError> {
        let existing = self
            .store
            .get_job(&record.spec.id)
            .map_err(JobError::from)?;
        let decision_json = record
            .decision
            .as_ref()
            .map(serde_json::to_string)
            .transpose()
            .map_err(|e| JobError::Io(e.to_string()))?
            .or(existing.decision_json);
        let recipe_json = record
            .decision
            .as_ref()
            .and_then(|d| d.recipe.as_ref())
            .map(serde_json::to_string)
            .transpose()
            .map_err(|e| JobError::Io(e.to_string()))?
            .or(existing.recipe_json);
        let spec_json = serde_json::to_string(&record.spec)
            .map_err(|e| JobError::Io(e.to_string()))?;
        self.store
            .update_job(&JobRow {
                id: record.spec.id.clone(),
                group_id: existing.group_id,
                spec_json,
                state: record.state.as_str().to_string(),
                progress: record.progress,
                decision_json,
                recipe_json,
                output_uri: record.output_uri.clone(),
                output_bytes: record.output_bytes,
                saved_bytes: record.saved_bytes,
                skip_reason: record.skip.map(skip_reason_as_str).map(str::to_string),
                fail_reason: record.fail.map(|f| f.as_str().to_string()),
                message: record.message.clone(),
                updated_at: record.updated_at,
            })
            .map_err(JobError::from)
    }

    fn row_to_record(&self, row: &JobRow) -> Result<JobRecord, JobError> {
        let spec: JobSpec = serde_json::from_str(&row.spec_json)
            .map_err(|e| JobError::Io(e.to_string()))?;
        let decision = row
            .decision_json
            .as_deref()
            .map(|s| serde_json::from_str(s))
            .transpose()
            .map_err(|e| JobError::Io(e.to_string()))?;
        let state = JobState::parse(&row.state).ok_or_else(|| {
            JobError::InvalidState(format!("unknown job state {}", row.state))
        })?;
        let skip = row
            .skip_reason
            .as_deref()
            .and_then(skip_reason_parse);
        let fail = row
            .fail_reason
            .as_deref()
            .and_then(FailReason::parse);
        Ok(JobRecord {
            spec,
            state,
            progress: row.progress,
            decision,
            output_uri: row.output_uri.clone(),
            output_bytes: row.output_bytes,
            saved_bytes: row.saved_bytes,
            fail,
            skip,
            message: row.message.clone(),
            updated_at: row.updated_at,
        })
    }

    fn validate_dispatch(&self, record: &JobRecord) -> Result<(), String> {
        if recipe_for(record).is_none() {
            return Err("job missing recipe".into());
        }
        match record.spec.fingerprint.kind {
            MediaKind::Image => {
                let path = record.spec.local_path.as_deref().unwrap_or("");
                if path.is_empty() {
                    return Err("image jobs require local_path".into());
                }
            }
            MediaKind::Audio => {
                let path = record
                    .spec
                    .local_path
                    .clone()
                    .filter(|p| !p.is_empty())
                    .or_else(|| {
                        let uri = record.spec.source_uri.clone();
                        if uri.is_empty() {
                            None
                        } else {
                            Some(uri)
                        }
                    });
                if path.is_none() {
                    return Err("audio jobs require readable path".into());
                }
            }
            MediaKind::Video => {}
        }
        Ok(())
    }

    fn cleanup_tmp_for_job(&self, job_id: &str) -> Result<(), JobError> {
        let candidates = [
            self.tmp_output_path(job_id),
            self.tmp_output_path_for(job_id, "jpg"),
            self.tmp_output_path_for(job_id, "jpeg"),
            self.tmp_output_path_for(job_id, "webp"),
            self.tmp_output_path_for(job_id, "png"),
            self.tmp_output_path_for(job_id, "m4a"),
            self.tmp_output_path_for(job_id, "mp4"),
            self.tmp_output_path_for(job_id, "ogg"),
        ];
        for path in candidates {
            if path.exists() {
                fs::remove_file(&path)
                    .map_err(|e| JobError::Io(format!("remove tmp {}: {e}", path.display())))?;
            }
        }
        if let Ok(entries) = fs::read_dir(&self.config.tmp_dir) {
            for entry in entries.flatten() {
                let file_name = entry.file_name();
                let name = file_name.to_string_lossy();
                if name == job_id || name.starts_with(&format!("{job_id}.")) {
                    if entry.path().is_file() {
                        let _ = fs::remove_file(entry.path());
                    }
                }
            }
        }
        Ok(())
    }
}

fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn kind_preset_label(record: &JobRecord) -> &'static str {
    match record.spec.fingerprint.kind {
        MediaKind::Video => "video",
        MediaKind::Image => "image",
        MediaKind::Audio => "audio",
    }
}

#[cfg(test)]
mod tests {
    use std::env;

    use ss_domain::{
        Compatibility, CompressRequest, MediaFingerprint, MediaKind, OutputPolicy, QualityPreset,
    };

    use super::*;
    use crate::types::JobSpec;

    fn temp_dirs(name: &str) -> (PathBuf, PathBuf, PathBuf, PathBuf) {
        let root = env::temp_dir().join(format!("ss-jobs-{}-{}", std::process::id(), name));
        let data = root.join("data");
        let tmp = root.join("tmp");
        let vault = root.join("vault");
        (root, data, tmp, vault)
    }

    fn sample_spec(id: &str, kind: MediaKind, bytes: u64) -> JobSpec {
        JobSpec {
            id: id.to_string(),
            display_name: format!("file-{id}"),
            source_uri: format!("content://{id}"),
            local_path: None,
            fingerprint: MediaFingerprint {
                kind,
                mime: "video/mp4".into(),
                container: "mp4".into(),
                video_codec: Some("h264".into()),
                audio_codec: Some("aac".into()),
                width: 1920,
                height: 1080,
                duration_ms: 60_000,
                fps: Some(30.0),
                rotation: 0,
                file_bytes: bytes,
                video_bitrate_bps: Some(8_000_000),
                audio_bitrate_bps: Some(128_000),
                has_alpha: false,
                is_hdr: false,
                is_slowmo: false,
                is_live_photo: false,
                channels: None,
                sample_rate: None,
            },
            request: None,
        }
    }

    fn default_request() -> CompressRequest {
        CompressRequest {
            preset: QualityPreset::VideoBalanced,
            compatibility: Compatibility::Compatible,
            output_policy: OutputPolicy::SaveAsNew,
            target_bytes: None,
            custom: None,
            keep_gps: true,
            keep_hdr: false,
            skip_enabled: true,
            image_format: None,
            audio_format: None,
            skip_thresholds: ss_domain::SkipThresholds::default(),
            estimate_scale: 1.0,
        }
    }

    #[test]
    fn submit_group_persists_queued_and_skipped() {
        let (root, data, tmp, vault) = temp_dirs("submit");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");

        let group_id = scheduler
            .submit_group(
                vec![
                    sample_spec("big", MediaKind::Video, 120 * 1024 * 1024),
                    sample_spec("tiny", MediaKind::Video, 2 * 1024 * 1024),
                ],
                default_request(),
                false,
            )
            .expect("submit");

        let view = scheduler.group(&group_id).expect("group");
        assert_eq!(view.jobs.len(), 2);
        assert!(view.jobs.iter().any(|j| j.state == JobState::Queued));
        assert!(view.jobs.iter().any(|j| j.state == JobState::Skipped));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn mixed_group_uses_per_job_request() {
        let (root, data, tmp, vault) = temp_dirs("mixed");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");

        let mut image = sample_spec("img", MediaKind::Image, 4 * 1024 * 1024);
        image.fingerprint.mime = "image/jpeg".into();
        image.fingerprint.container = "jpeg".into();
        image.fingerprint.width = 4000;
        image.fingerprint.height = 3000;
        image.fingerprint.duration_ms = 0;
        image.fingerprint.video_codec = None;
        image.request = Some(CompressRequest {
            preset: QualityPreset::ImageHigh,
            image_format: Some("jpeg".into()),
            skip_enabled: false,
            ..Default::default()
        });
        let video = sample_spec("vid", MediaKind::Video, 80 * 1024 * 1024);
        let group_id = scheduler
            .submit_group(vec![image, video], default_request(), false)
            .expect("submit");
        let view = scheduler.group(&group_id).expect("group");
        assert_eq!(view.jobs.len(), 2);
        assert!(view
            .jobs
            .iter()
            .any(|j| j.spec.fingerprint.kind == MediaKind::Image && !j.decision.as_ref().map(|d| d.skip).unwrap_or(true)));
        assert!(view
            .jobs
            .iter()
            .any(|j| j.spec.fingerprint.kind == MediaKind::Video));
        let events = scheduler.list_events(20).expect("events");
        assert!(events.iter().any(|e| e.2 == "probe_done"));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn running_jobs_recover_to_queued_after_reopen() {
        let (root, data, tmp, vault) = temp_dirs("recover");
        let _ = fs::create_dir_all(&data);
        let config = SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        };
        let mut scheduler = JobScheduler::open(config.clone()).expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 50 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        let tmp_file = scheduler.tmp_output_path(&job_id);
        fs::write(&tmp_file, b"partial").unwrap();

        drop(scheduler);
        let scheduler2 = JobScheduler::open(config).expect("reopen");
        let job = scheduler2.job(&job_id).expect("job");
        assert_eq!(job.state, JobState::Queued);
        assert!(!tmp_file.exists());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn crash_recovery_cleans_audio_tmp_suffix() {
        let (root, data, tmp, vault) = temp_dirs("audio-tmp");
        let _ = fs::create_dir_all(&data);
        let config = SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        };
        let mut scheduler = JobScheduler::open(config.clone()).expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("a1", MediaKind::Audio, 20 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        let tmp_file = scheduler.tmp_output_path_for(&job_id, "m4a");
        fs::write(&tmp_file, b"partial").unwrap();

        drop(scheduler);
        let scheduler2 = JobScheduler::open(config).expect("reopen");
        let job = scheduler2.job(&job_id).expect("job");
        assert_eq!(job.state, JobState::Queued);
        assert!(!tmp_file.exists());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn disk_full_halt_stops_dispatch_until_resume() {
        let (root, data, tmp, vault) = temp_dirs("disk-full");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data,
            tmp_dir: tmp,
            vault_dir: vault,
            parallel_profile: ParallelProfile::Auto,
        })
        .unwrap();
        let group_id = scheduler
            .submit_group(
                vec![
                    sample_spec("v1", MediaKind::Video, 120 * 1024 * 1024),
                    sample_spec("v2", MediaKind::Video, 120 * 1024 * 1024),
                ],
                default_request(),
                false,
            )
            .unwrap();
        let jobs = scheduler.group(&group_id).unwrap().jobs;
        let first = jobs[0].spec.id.clone();
        let mut running = scheduler
            .row_to_record(&scheduler.store().get_job(&first).unwrap())
            .unwrap();
        running.state = JobState::Running;
        running.updated_at = now_ms();
        scheduler.update_job_record(&running).unwrap();
        scheduler
            .on_platform_failed(&first, FailReason::DiskFull, "fail.disk_full")
            .unwrap();
        assert!(scheduler.disk_full_halt());
        assert!(scheduler.dispatch_ready_jobs().unwrap().is_empty());
        scheduler.resume_group(&group_id).unwrap();
        assert!(!scheduler.disk_full_halt());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn purge_expired_vault_keeps_unexpired_rows() {
        let (root, data, tmp, vault) = temp_dirs("vault-purge");
        let _ = fs::create_dir_all(&data);
        let scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_secs() as i64;
        let vault_path = vault.join("keep.bak");
        fs::write(&vault_path, b"backup").unwrap();
        let keep_id = scheduler
            .register_vault_backup(
                "job-keep",
                vault_path.to_string_lossy().as_ref(),
                "content://keep",
                1024,
                now + 7 * 86_400,
            )
            .expect("register");
        let expired_path = vault.join("gone.bak");
        fs::write(&expired_path, b"old").unwrap();
        let _ = scheduler
            .register_vault_backup(
                "job-gone",
                expired_path.to_string_lossy().as_ref(),
                "content://gone",
                512,
                now - 60,
            )
            .expect("register expired");
        let deleted = scheduler.purge_expired_vault(now).expect("purge");
        assert_eq!(deleted.len(), 1);
        assert!(!expired_path.exists());
        assert!(vault_path.exists());
        assert!(scheduler.store().get_vault_item(&keep_id).is_ok());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn cancelling_late_success_finalizes_to_paused() {
        let (root, data, tmp, vault) = temp_dirs("cancel-success");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 50 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        scheduler.pause_group(&group_id).expect("pause");
        scheduler
            .on_platform_succeeded(&job_id, "/tmp/out.mp4", 1024)
            .expect("late success");
        let paused = scheduler.job(&job_id).expect("job");
        assert_eq!(paused.state, JobState::Paused);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn pause_running_job_waits_for_platform_then_paused() {
        let (root, data, tmp, vault) = temp_dirs("pause-ack");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 50 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        scheduler.pause_group(&group_id).expect("pause");
        let mid = scheduler.job(&job_id).expect("job");
        assert_eq!(mid.state, JobState::Cancelling);
        scheduler
            .on_platform_failed(&job_id, FailReason::Cancelled, "cancelled")
            .expect("ack");
        let paused = scheduler.job(&job_id).expect("job");
        assert_eq!(paused.state, JobState::Paused);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn cancel_group_does_not_mark_group_completed() {
        let (root, data, tmp, vault) = temp_dirs("cancel-not-complete");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data,
            tmp_dir: tmp,
            vault_dir: vault,
            parallel_profile: ParallelProfile::Auto,
        })
        .unwrap();
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 120 * 1024 * 1024)], default_request(), false)
            .unwrap();
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        scheduler.cancel_group(&group_id).unwrap();
        scheduler
            .on_platform_failed(&job_id, FailReason::Cancelled, "cancelled")
            .expect("ack");
        let group = scheduler.store().get_group(&group_id).unwrap();
        assert_eq!(group.state, GroupState::Cancelled.as_str());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn resume_during_cancelling_requeues_job() {
        let (root, data, tmp, vault) = temp_dirs("resume-cancel");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 50 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        scheduler.pause_group(&group_id).expect("pause");
        scheduler.mark_resume_after_cancel_ack(&job_id);
        scheduler.resume_group(&group_id).expect("resume");
        let mid = scheduler.job(&job_id).expect("job");
        assert_eq!(mid.state, JobState::Cancelling);
        scheduler
            .on_platform_failed(&job_id, FailReason::Cancelled, "cancelled")
            .expect("ack");
        let resumed = scheduler.job(&job_id).expect("job");
        assert_eq!(resumed.state, JobState::Queued);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn cancel_job_ack_marks_failed_not_queued() {
        let (root, data, tmp, vault) = temp_dirs("cancel-job");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 50 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        scheduler.cancel_job(&job_id).expect("cancel");
        scheduler
            .on_platform_failed(&job_id, FailReason::Cancelled, "cancelled")
            .expect("ack");
        let failed = scheduler.job(&job_id).expect("job");
        assert_eq!(failed.state, JobState::Failed);
        assert_eq!(failed.fail, Some(FailReason::Cancelled));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn pause_during_resume_ack_keeps_job_paused() {
        let (root, data, tmp, vault) = temp_dirs("pause-during-resume");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 50 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        scheduler.pause_group(&group_id).expect("pause");
        scheduler.mark_resume_after_cancel_ack(&job_id);
        scheduler.resume_group(&group_id).expect("resume");
        assert_eq!(scheduler.job(&job_id).unwrap().state, JobState::Cancelling);
        scheduler.pause_group(&group_id).expect("pause-again");
        let group = scheduler.store().get_group(&group_id).unwrap();
        assert_eq!(group.state, GroupState::Paused.as_str());
        scheduler
            .on_platform_failed(&job_id, FailReason::Cancelled, "cancelled")
            .expect("ack");
        let paused = scheduler.job(&job_id).expect("job");
        assert_eq!(paused.state, JobState::Paused);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn cancel_job_during_resume_ack_marks_failed() {
        let (root, data, tmp, vault) = temp_dirs("cancel-during-resume");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 50 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        scheduler.pause_group(&group_id).expect("pause");
        scheduler.mark_resume_after_cancel_ack(&job_id);
        scheduler.resume_group(&group_id).expect("resume");
        scheduler.cancel_job(&job_id).expect("cancel");
        scheduler
            .on_platform_failed(&job_id, FailReason::Cancelled, "cancelled")
            .expect("ack");
        let failed = scheduler.job(&job_id).expect("job");
        assert_eq!(failed.state, JobState::Failed);
        assert_eq!(failed.fail, Some(FailReason::Cancelled));
        scheduler
            .on_platform_succeeded(&job_id, "/tmp/out.mp4", 1024)
            .expect("late success");
        let still = scheduler.job(&job_id).expect("job");
        assert_eq!(still.state, JobState::Failed);
        let dispatched = scheduler.dispatch_ready_jobs().expect("dispatch");
        assert!(dispatched.iter().all(|job| job.spec.id != job_id));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn cancelling_jobs_recover_to_queued_when_group_active() {
        let (root, data, tmp, vault) = temp_dirs("recover-cancelling-active");
        let _ = fs::create_dir_all(&data);
        let config = SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        };
        let mut scheduler = JobScheduler::open(config.clone()).expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 120 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        assert_eq!(scheduler.job(&job_id).unwrap().state, JobState::Queued);
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Cancelling;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        let tmp_file = scheduler.tmp_output_path(&job_id);
        fs::write(&tmp_file, b"partial").unwrap();

        drop(scheduler);
        let mut scheduler2 = JobScheduler::open(config).expect("reopen");
        let job = scheduler2.job(&job_id).expect("job");
        assert_eq!(job.state, JobState::Queued);
        assert!(!tmp_file.exists());
        let dispatched = scheduler2.dispatch_ready_jobs().expect("dispatch");
        assert!(dispatched.iter().any(|job| job.spec.id == job_id));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn cancelling_jobs_recover_to_paused_when_group_paused() {
        let (root, data, tmp, vault) = temp_dirs("recover-cancelling-paused");
        let _ = fs::create_dir_all(&data);
        let config = SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        };
        let mut scheduler = JobScheduler::open(config.clone()).expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 50 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        scheduler.pause_group(&group_id).expect("pause");
        assert_eq!(scheduler.job(&job_id).unwrap().state, JobState::Cancelling);

        drop(scheduler);
        let scheduler2 = JobScheduler::open(config).expect("reopen");
        let job = scheduler2.job(&job_id).expect("job");
        assert_eq!(job.state, JobState::Paused);
        let group = scheduler2.store().get_group(&group_id).unwrap();
        assert_eq!(group.state, GroupState::Paused.as_str());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn cancelling_jobs_recover_to_failed_when_group_cancelled() {
        let (root, data, tmp, vault) = temp_dirs("recover-cancelling-cancelled");
        let _ = fs::create_dir_all(&data);
        let config = SchedulerConfig {
            data_dir: data.clone(),
            tmp_dir: tmp.clone(),
            vault_dir: vault.clone(),
            parallel_profile: ParallelProfile::Auto,
        };
        let mut scheduler = JobScheduler::open(config.clone()).expect("open");
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 50 * 1024 * 1024)], default_request(), false)
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        scheduler.cancel_group(&group_id).expect("cancel");
        assert_eq!(scheduler.job(&job_id).unwrap().state, JobState::Cancelling);

        drop(scheduler);
        let mut scheduler2 = JobScheduler::open(config).expect("reopen");
        let job = scheduler2.job(&job_id).expect("job");
        assert_eq!(job.state, JobState::Failed);
        assert_eq!(job.fail, Some(FailReason::Cancelled));
        let group = scheduler2.store().get_group(&group_id).unwrap();
        assert_eq!(group.state, GroupState::Cancelled.as_str());
        let dispatched = scheduler2.dispatch_ready_jobs().expect("dispatch");
        assert!(dispatched.iter().all(|job| job.spec.id != job_id));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn cancel_group_marks_active_jobs_cancelled() {
        let (root, data, tmp, vault) = temp_dirs("cancel");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data,
            tmp_dir: tmp,
            vault_dir: vault,
            parallel_profile: ParallelProfile::Auto,
        })
        .unwrap();
        let group_id = scheduler
            .submit_group(vec![sample_spec("v1", MediaKind::Video, 120 * 1024 * 1024)], default_request(), false)
            .unwrap();
        let view = scheduler.cancel_group(&group_id).unwrap();
        assert!(view.jobs.iter().all(|j| j.state == JobState::Failed));
        assert!(view.jobs.iter().all(|j| j.fail == Some(FailReason::Cancelled)));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn delete_job_rejects_in_flight() {
        let (root, data, tmp, vault) = temp_dirs("delete_reject");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data,
            tmp_dir: tmp,
            vault_dir: vault,
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let group_id = scheduler
            .submit_group(
                vec![sample_spec("big", MediaKind::Video, 120 * 1024 * 1024)],
                default_request(),
                false,
            )
            .expect("submit");
        let job_id = scheduler.group(&group_id).unwrap().jobs[0].spec.id.clone();
        let mut record = scheduler
            .row_to_record(&scheduler.store().get_job(&job_id).unwrap())
            .unwrap();
        record.state = JobState::Running;
        record.updated_at = now_ms();
        scheduler.update_job_record(&record).unwrap();
        let err = scheduler.delete_job(&job_id).unwrap_err();
        assert!(matches!(err, JobError::InvalidState(_)));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn delete_job_removes_terminal_job_and_prunes_empty_group() {
        let (root, data, tmp, vault) = temp_dirs("delete_terminal");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data,
            tmp_dir: tmp,
            vault_dir: vault,
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let group_id = scheduler
            .submit_group(
                vec![sample_spec("tiny", MediaKind::Video, 1024)],
                default_request(),
                false,
            )
            .expect("submit");
        let view = scheduler.group(&group_id).expect("group");
        assert_eq!(view.jobs.len(), 1);
        assert_eq!(view.jobs[0].state, JobState::Skipped);
        let job_id = view.jobs[0].spec.id.clone();
        let remaining = scheduler.delete_job(&job_id).expect("delete");
        assert!(remaining.is_none());
        assert!(scheduler.group(&group_id).is_err());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn delete_job_keeps_group_with_remaining_jobs() {
        let (root, data, tmp, vault) = temp_dirs("delete_keep_group");
        let _ = fs::create_dir_all(&data);
        let mut scheduler = JobScheduler::open(SchedulerConfig {
            data_dir: data,
            tmp_dir: tmp,
            vault_dir: vault,
            parallel_profile: ParallelProfile::Auto,
        })
        .expect("open");
        let group_id = scheduler
            .submit_group(
                vec![
                    sample_spec("big", MediaKind::Video, 120 * 1024 * 1024),
                    sample_spec("tiny", MediaKind::Video, 2 * 1024 * 1024),
                ],
                default_request(),
                false,
            )
            .expect("submit");
        let view = scheduler.group(&group_id).expect("group");
        let skipped = view
            .jobs
            .iter()
            .find(|job| job.state == JobState::Skipped)
            .expect("skipped");
        let remaining = scheduler
            .delete_job(&skipped.spec.id)
            .expect("delete");
        assert_eq!(remaining.as_deref(), Some(group_id.as_str()));
        let updated = scheduler.group(&group_id).expect("group");
        assert_eq!(updated.jobs.len(), 1);
        assert_eq!(updated.jobs[0].state, JobState::Queued);
        let _ = fs::remove_dir_all(root);
    }
}
