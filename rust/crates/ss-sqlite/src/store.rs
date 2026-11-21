use std::fs;
use std::io;
use std::path::Path;

use rusqlite::{params, Connection};
use serde::{de::DeserializeOwned, Deserialize, Serialize};

use crate::error::DbError;
use crate::schema::migrate;

#[derive(Clone, Debug, PartialEq)]
pub struct GroupRow {
    pub id: String,
    pub created_at: i64,
    pub request_json: String,
    pub state: String,
}

#[derive(Clone, Debug, PartialEq)]
pub struct JobRow {
    pub id: String,
    pub group_id: String,
    pub spec_json: String,
    pub state: String,
    pub progress: f32,
    pub decision_json: Option<String>,
    pub recipe_json: Option<String>,
    pub output_uri: Option<String>,
    pub output_bytes: Option<u64>,
    pub saved_bytes: Option<i64>,
    pub skip_reason: Option<String>,
    pub fail_reason: Option<String>,
    pub message: String,
    pub updated_at: i64,
}

#[derive(Clone, Debug, PartialEq)]
pub struct VaultRow {
    pub id: String,
    pub job_id: String,
    pub vault_path: String,
    pub original_uri: String,
    pub compressed_uri: String,
    pub original_bytes: u64,
    pub expires_at: i64,
    pub restored: bool,
}

pub struct Store {
    conn: Connection,
}

impl Store {
    pub fn open(path: &Path) -> Result<Self, DbError> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| DbError::InvalidData(e.to_string()))?;
        }
        let conn = Connection::open(path)?;
        conn.busy_timeout(std::time::Duration::from_secs(5))?;
        conn.execute_batch("PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL;")?;
        migrate(&conn)?;
        Ok(Self { conn })
    }

    pub fn open_in_memory() -> Result<Self, DbError> {
        let conn = Connection::open_in_memory()?;
        conn.execute_batch("PRAGMA foreign_keys = ON;")?;
        migrate(&conn)?;
        Ok(Self { conn })
    }

    pub fn insert_group(&self, row: &GroupRow) -> Result<(), DbError> {
        self.conn.execute(
            "INSERT INTO groups(id, created_at, request_json, state) VALUES (?1, ?2, ?3, ?4)",
            params![row.id, row.created_at, row.request_json, row.state],
        )?;
        Ok(())
    }

    pub fn update_group_state(&self, id: &str, state: &str) -> Result<(), DbError> {
        let n = self
            .conn
            .execute(
                "UPDATE groups SET state = ?1 WHERE id = ?2",
                params![state, id],
            )?;
        if n == 0 {
            return Err(DbError::NotFound(id.to_string()));
        }
        Ok(())
    }

    pub fn list_groups(&self) -> Result<Vec<GroupRow>, DbError> {
        let mut stmt = self.conn.prepare(
            "SELECT id, created_at, request_json, state FROM groups ORDER BY created_at DESC",
        )?;
        let rows = stmt.query_map([], |row| {
            Ok(GroupRow {
                id: row.get(0)?,
                created_at: row.get(1)?,
                request_json: row.get(2)?,
                state: row.get(3)?,
            })
        })?;
        rows.collect::<Result<Vec<_>, _>>().map_err(DbError::from)
    }

    pub fn get_group(&self, id: &str) -> Result<GroupRow, DbError> {
        self.conn
            .query_row(
                "SELECT id, created_at, request_json, state FROM groups WHERE id = ?1",
                [id],
                |row| {
                    Ok(GroupRow {
                        id: row.get(0)?,
                        created_at: row.get(1)?,
                        request_json: row.get(2)?,
                        state: row.get(3)?,
                    })
                },
            )
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => DbError::NotFound(id.to_string()),
                other => DbError::from(other),
            })
    }

    pub fn insert_job(&self, row: &JobRow) -> Result<(), DbError> {
        self.conn.execute(
            "INSERT INTO jobs(
                id, group_id, spec_json, state, progress, decision_json, recipe_json,
                output_uri, output_bytes, saved_bytes, skip_reason, fail_reason, message, updated_at
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
            params![
                row.id,
                row.group_id,
                row.spec_json,
                row.state,
                row.progress,
                row.decision_json,
                row.recipe_json,
                row.output_uri,
                row.output_bytes,
                row.saved_bytes,
                row.skip_reason,
                row.fail_reason,
                row.message,
                row.updated_at,
            ],
        )?;
        Ok(())
    }

    pub fn update_job(&self, row: &JobRow) -> Result<(), DbError> {
        let n = self.conn.execute(
            "UPDATE jobs SET
                state = ?2, progress = ?3, decision_json = ?4, recipe_json = ?5,
                output_uri = ?6, output_bytes = ?7, saved_bytes = ?8,
                skip_reason = ?9, fail_reason = ?10, message = ?11, updated_at = ?12
             WHERE id = ?1",
            params![
                row.id,
                row.state,
                row.progress,
                row.decision_json,
                row.recipe_json,
                row.output_uri,
                row.output_bytes,
                row.saved_bytes,
                row.skip_reason,
                row.fail_reason,
                row.message,
                row.updated_at,
            ],
        )?;
        if n == 0 {
            return Err(DbError::NotFound(row.id.clone()));
        }
        Ok(())
    }

    pub fn get_job(&self, id: &str) -> Result<JobRow, DbError> {
        self.conn
            .query_row(
                "SELECT id, group_id, spec_json, state, progress, decision_json, recipe_json,
                        output_uri, output_bytes, saved_bytes, skip_reason, fail_reason, message, updated_at
                 FROM jobs WHERE id = ?1",
                [id],
                map_job_row,
            )
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => DbError::NotFound(id.to_string()),
                other => DbError::from(other),
            })
    }

    pub fn delete_job(&self, id: &str) -> Result<String, DbError> {
        let group_id: String = self
            .conn
            .query_row(
                "SELECT group_id FROM jobs WHERE id = ?1",
                [id],
                |row| row.get(0),
            )
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => DbError::NotFound(id.to_string()),
                other => DbError::from(other),
            })?;
        let n = self.conn.execute("DELETE FROM jobs WHERE id = ?1", [id])?;
        if n == 0 {
            return Err(DbError::NotFound(id.to_string()));
        }
        Ok(group_id)
    }

    pub fn count_jobs_in_group(&self, group_id: &str) -> Result<u32, DbError> {
        let count: i64 = self
            .conn
            .query_row(
                "SELECT COUNT(*) FROM jobs WHERE group_id = ?1",
                [group_id],
                |row| row.get(0),
            )?;
        Ok(count as u32)
    }

    pub fn delete_group(&self, id: &str) -> Result<(), DbError> {
        let n = self.conn.execute("DELETE FROM groups WHERE id = ?1", [id])?;
        if n == 0 {
            return Err(DbError::NotFound(id.to_string()));
        }
        Ok(())
    }

    pub fn list_jobs_for_group(&self, group_id: &str) -> Result<Vec<JobRow>, DbError> {
        let mut stmt = self.conn.prepare(
            "SELECT id, group_id, spec_json, state, progress, decision_json, recipe_json,
                    output_uri, output_bytes, saved_bytes, skip_reason, fail_reason, message, updated_at
             FROM jobs WHERE group_id = ?1 ORDER BY updated_at ASC",
        )?;
        let rows = stmt.query_map([group_id], map_job_row)?;
        rows.collect::<Result<Vec<_>, _>>().map_err(DbError::from)
    }

    pub fn list_jobs_by_state(&self, state: &str) -> Result<Vec<JobRow>, DbError> {
        let mut stmt = self.conn.prepare(
            "SELECT id, group_id, spec_json, state, progress, decision_json, recipe_json,
                    output_uri, output_bytes, saved_bytes, skip_reason, fail_reason, message, updated_at
             FROM jobs WHERE state = ?1 ORDER BY updated_at ASC",
        )?;
        let rows = stmt.query_map([state], map_job_row)?;
        rows.collect::<Result<Vec<_>, _>>().map_err(DbError::from)
    }

    pub fn reset_running_jobs(&self, now: i64) -> Result<Vec<String>, DbError> {
        let mut ids = Vec::new();
        for state in ["Running", "Cancelling"] {
            let jobs = self.list_jobs_by_state(state)?;
            for job in jobs {
                ids.push(job.id.clone());
                let mut updated = job;
                updated.state = "Queued".to_string();
                updated.progress = 0.0;
                updated.updated_at = now;
                self.update_job(&updated)?;
            }
        }
        Ok(ids)
    }

    pub fn insert_vault(&self, row: &VaultRow) -> Result<(), DbError> {
        self.conn.execute(
            "INSERT INTO vault(id, job_id, original_path, vault_path, original_uri, compressed_uri, original_bytes, expires_at, restored)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
            params![
                row.id,
                row.job_id,
                row.vault_path,
                row.vault_path,
                row.original_uri,
                row.compressed_uri,
                row.original_bytes,
                row.expires_at,
                row.restored as i32,
            ],
        )?;
        Ok(())
    }

    pub fn set_vault_compressed_uri_for_job(
        &self,
        job_id: &str,
        compressed_uri: &str,
    ) -> Result<(), DbError> {
        let n = self.conn.execute(
            "UPDATE vault SET compressed_uri = ?1 WHERE job_id = ?2",
            params![compressed_uri, job_id],
        )?;
        if n == 0 {
            return Err(DbError::NotFound(format!("vault for job {job_id}")));
        }
        Ok(())
    }

    pub fn vault_usage(&self) -> Result<u64, DbError> {
        let bytes: i64 = self
            .conn
            .query_row(
                "SELECT COALESCE(SUM(original_bytes), 0) FROM vault WHERE restored = 0",
                [],
                |row| row.get(0),
            )?;
        Ok(bytes.max(0) as u64)
    }

    pub fn delete_vault_item(&self, id: &str) -> Result<(), DbError> {
        let n = self
            .conn
            .execute("DELETE FROM vault WHERE id = ?1", [id])?;
        if n == 0 {
            return Err(DbError::NotFound(id.to_string()));
        }
        Ok(())
    }

    pub fn get_vault_item(&self, id: &str) -> Result<VaultRow, DbError> {
        self.conn
            .query_row(
                "SELECT id, job_id,
                        COALESCE(vault_path, original_path),
                        COALESCE(original_uri, original_path),
                        COALESCE(compressed_uri, ''),
                        original_bytes, expires_at, restored
                 FROM vault WHERE id = ?1",
                [id],
                map_vault_row,
            )
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => DbError::NotFound(id.to_string()),
                other => DbError::from(other),
            })
    }

    pub fn extend_vault_expires(&self, id: &str, extra_days: u32) -> Result<VaultRow, DbError> {
        let row = self.get_vault_item(id)?;
        let new_expires = row.expires_at + i64::from(extra_days) * 86_400;
        let n = self
            .conn
            .execute("UPDATE vault SET expires_at = ?1 WHERE id = ?2", (new_expires, id))?;
        if n == 0 {
            return Err(DbError::NotFound(id.to_string()));
        }
        self.get_vault_item(id)
    }

    pub fn mark_vault_restored(&self, id: &str) -> Result<(), DbError> {
        let n = self
            .conn
            .execute("UPDATE vault SET restored = 1 WHERE id = ?1", [id])?;
        if n == 0 {
            return Err(DbError::NotFound(id.to_string()));
        }
        Ok(())
    }

    pub fn extend_vault_expiry(&self, id: &str, expires_at: i64) -> Result<(), DbError> {
        let n = self
            .conn
            .execute(
                "UPDATE vault SET expires_at = ?1 WHERE id = ?2",
                params![expires_at, id],
            )?;
        if n == 0 {
            return Err(DbError::NotFound(id.to_string()));
        }
        Ok(())
    }

    pub fn list_expired_vault(&self, now: i64) -> Result<Vec<VaultRow>, DbError> {
        let mut stmt = self.conn.prepare(
            "SELECT id, job_id,
                    COALESCE(vault_path, original_path),
                    COALESCE(original_uri, original_path),
                    COALESCE(compressed_uri, ''),
                    original_bytes, expires_at, restored
             FROM vault WHERE expires_at <= ?1 AND restored = 0
             ORDER BY expires_at ASC",
        )?;
        let rows = stmt.query_map([now], map_vault_row)?;
        rows.collect::<Result<Vec<_>, _>>().map_err(DbError::from)
    }

    /// Returns `(vault_id, vault_path)` for each deleted row. `vault_path` may be empty.
    pub fn delete_expired_vault_items(&self, now: i64) -> Result<Vec<(String, String)>, DbError> {
        let expired = self.list_expired_vault(now)?;
        let mut purged = Vec::new();
        for row in expired {
            if !row.vault_path.is_empty() {
                match fs::remove_file(&row.vault_path) {
                    Ok(()) => {}
                    Err(e) if e.kind() == io::ErrorKind::NotFound => {}
                    Err(_) => continue,
                }
            }
            self.conn
                .execute("DELETE FROM vault WHERE id = ?1", [row.id.as_str()])?;
            purged.push((row.id, row.vault_path));
        }
        Ok(purged)
    }

    pub fn insert_event(
        &self,
        id: &str,
        ts: i64,
        event_type: &str,
        payload_json: &str,
    ) -> Result<(), DbError> {
        self.conn.execute(
            "INSERT INTO events(id, ts, type, payload_json) VALUES (?1, ?2, ?3, ?4)",
            params![id, ts, event_type, payload_json],
        )?;
        Ok(())
    }

    pub fn list_events(&self, limit: u32) -> Result<Vec<(String, i64, String, String)>, DbError> {
        let mut stmt = self.conn.prepare(
            "SELECT id, ts, type, payload_json FROM events ORDER BY ts DESC LIMIT ?1",
        )?;
        let rows = stmt.query_map([limit as i64], |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, i64>(1)?,
                row.get::<_, String>(2)?,
                row.get::<_, String>(3)?,
            ))
        })?;
        rows.collect::<Result<Vec<_>, _>>().map_err(DbError::from)
    }

    pub fn list_vault(&self) -> Result<Vec<VaultRow>, DbError> {
        let mut stmt = self.conn.prepare(
            "SELECT id, job_id,
                    COALESCE(vault_path, original_path),
                    COALESCE(original_uri, original_path),
                    COALESCE(compressed_uri, ''),
                    original_bytes, expires_at, restored
             FROM vault ORDER BY expires_at ASC",
        )?;
        let rows = stmt.query_map([], map_vault_row)?;
        rows.collect::<Result<Vec<_>, _>>().map_err(DbError::from)
    }
}

fn map_vault_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<VaultRow> {
    Ok(VaultRow {
        id: row.get(0)?,
        job_id: row.get(1)?,
        vault_path: row.get(2)?,
        original_uri: row.get(3)?,
        compressed_uri: row.get(4)?,
        original_bytes: row.get(5)?,
        expires_at: row.get(6)?,
        restored: row.get::<_, i32>(7)? != 0,
    })
}

fn map_job_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<JobRow> {
    Ok(JobRow {
        id: row.get(0)?,
        group_id: row.get(1)?,
        spec_json: row.get(2)?,
        state: row.get(3)?,
        progress: row.get(4)?,
        decision_json: row.get(5)?,
        recipe_json: row.get(6)?,
        output_uri: row.get(7)?,
        output_bytes: row.get(8)?,
        saved_bytes: row.get(9)?,
        skip_reason: row.get(10)?,
        fail_reason: row.get(11)?,
        message: row.get(12)?,
        updated_at: row.get(13)?,
    })
}

pub struct SettingsStore<'a> {
    conn: &'a Connection,
}

impl Store {
    pub fn settings(&self) -> SettingsStore<'_> {
        SettingsStore { conn: &self.conn }
    }
}

impl<'a> SettingsStore<'a> {
    pub fn get<T: DeserializeOwned>(&self, key: &str) -> Result<Option<T>, DbError> {
        let json: Option<String> = self
            .conn
            .query_row(
                "SELECT value_json FROM settings WHERE key = ?1",
                [key],
                |row| row.get(0),
            )
            .optional()
            .map_err(DbError::from)?;
        match json {
            Some(s) => Ok(Some(serde_json::from_str(&s)?)),
            None => Ok(None),
        }
    }

    pub fn set<T: Serialize>(&self, key: &str, value: &T) -> Result<(), DbError> {
        let json = serde_json::to_string(value)?;
        self.conn.execute(
            "INSERT INTO settings(key, value_json) VALUES (?1, ?2)
             ON CONFLICT(key) DO UPDATE SET value_json = excluded.value_json",
            params![key, json],
        )?;
        Ok(())
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ScanSummaryRow {
    pub id: String,
    pub created_at: i64,
    pub scope_json: String,
    pub source_level: String,
    pub root_path: String,
    pub total_bytes: u64,
    pub used_bytes: u64,
    pub free_bytes: u64,
    pub scanned_bytes: u64,
    pub file_count: u32,
    pub duration_ms: u64,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ScanCategoryStatRow {
    pub scan_id: String,
    pub category: u8,
    pub bytes: u64,
    pub file_count: u32,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ScanDiffRow {
    pub category: u8,
    pub older_bytes: u64,
    pub newer_bytes: u64,
    pub delta_bytes: i64,
    pub older_files: u32,
    pub newer_files: u32,
    pub delta_files: i32,
}

impl Store {
    pub fn insert_scan(
        &self,
        summary: &ScanSummaryRow,
        tree: &ss_scan::ScanTree,
        category_stats: &[(ss_classify::FileCategory, u64, u32)],
    ) -> Result<(), DbError> {
        let tree_blob = serde_json::to_string(tree)?;
        self.conn.execute(
            "INSERT INTO scans(id, created_at, scope_json, source_level, root_path, total_bytes, used_bytes, free_bytes, scanned_bytes, file_count, duration_ms, tree_blob)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)",
            params![
                summary.id,
                summary.created_at,
                summary.scope_json,
                summary.source_level,
                summary.root_path,
                summary.total_bytes,
                summary.used_bytes,
                summary.free_bytes,
                summary.scanned_bytes,
                summary.file_count,
                summary.duration_ms,
                tree_blob,
            ],
        )?;

        let mut stat_stmt = self.conn.prepare(
            "INSERT OR REPLACE INTO scan_category_stats(scan_id, category, bytes, file_count) VALUES (?1, ?2, ?3, ?4)",
        )?;
        for &(cat, bytes, count) in category_stats {
            stat_stmt.execute(params![summary.id, cat.as_u8(), bytes, count])?;
        }

        Ok(())
    }

    pub fn update_scan_tree(
        &self,
        scan_id: &str,
        tree: &ss_scan::ScanTree,
        category_stats: &[(ss_classify::FileCategory, u64, u32)],
    ) -> Result<(), DbError> {
        let tree_blob = serde_json::to_string(tree)?;
        let root_node = tree.nodes.get(tree.root as usize);
        let (scanned_bytes, file_count) = root_node
            .map(|n| (n.size_physical, n.file_count))
            .unwrap_or((0, 0));

        self.conn.execute(
            "UPDATE scans SET scanned_bytes = ?1, file_count = ?2, tree_blob = ?3 WHERE id = ?4",
            params![scanned_bytes, file_count, tree_blob, scan_id],
        )?;

        self.conn.execute("DELETE FROM scan_category_stats WHERE scan_id = ?1", params![scan_id])?;

        let mut stat_stmt = self.conn.prepare(
            "INSERT INTO scan_category_stats(scan_id, category, bytes, file_count) VALUES (?1, ?2, ?3, ?4)",
        )?;
        for &(cat, bytes, count) in category_stats {
            stat_stmt.execute(params![scan_id, cat.as_u8(), bytes, count])?;
        }

        Ok(())
    }

    pub fn list_scans(&self, limit: u32) -> Result<Vec<ScanSummaryRow>, DbError> {
        let mut stmt = self.conn.prepare(
            "SELECT id, created_at, scope_json, source_level, root_path, total_bytes, used_bytes, free_bytes, scanned_bytes, file_count, duration_ms
             FROM scans ORDER BY created_at DESC LIMIT ?1",
        )?;
        let rows = stmt.query_map([limit], |row| {
            Ok(ScanSummaryRow {
                id: row.get(0)?,
                created_at: row.get(1)?,
                scope_json: row.get(2)?,
                source_level: row.get(3)?,
                root_path: row.get(4)?,
                total_bytes: row.get(5)?,
                used_bytes: row.get(6)?,
                free_bytes: row.get(7)?,
                scanned_bytes: row.get(8)?,
                file_count: row.get(9)?,
                duration_ms: row.get(10)?,
            })
        })?;
        let mut res = Vec::new();
        for r in rows {
            res.push(r?);
        }
        Ok(res)
    }

    pub fn get_scan_summary(&self, id: &str) -> Result<ScanSummaryRow, DbError> {
        self.conn
            .query_row(
                "SELECT id, created_at, scope_json, source_level, root_path, total_bytes, used_bytes, free_bytes, scanned_bytes, file_count, duration_ms
                 FROM scans WHERE id = ?1",
                [id],
                |row| {
                    Ok(ScanSummaryRow {
                        id: row.get(0)?,
                        created_at: row.get(1)?,
                        scope_json: row.get(2)?,
                        source_level: row.get(3)?,
                        root_path: row.get(4)?,
                        total_bytes: row.get(5)?,
                        used_bytes: row.get(6)?,
                        free_bytes: row.get(7)?,
                        scanned_bytes: row.get(8)?,
                        file_count: row.get(9)?,
                        duration_ms: row.get(10)?,
                    })
                },
            )
            .optional()
            .map_err(DbError::from)?
            .ok_or_else(|| DbError::NotFound(id.to_string()))
    }

    pub fn load_scan_tree(&self, id: &str) -> Result<ss_scan::ScanTree, DbError> {
        let json: String = self
            .conn
            .query_row("SELECT tree_blob FROM scans WHERE id = ?1", [id], |row| {
                row.get(0)
            })
            .optional()
            .map_err(DbError::from)?
            .ok_or_else(|| DbError::NotFound(id.to_string()))?;
        Ok(serde_json::from_str(&json)?)
    }

    pub fn get_scan_category_stats(&self, id: &str) -> Result<Vec<ScanCategoryStatRow>, DbError> {
        let mut stmt = self.conn.prepare(
            "SELECT scan_id, category, bytes, file_count FROM scan_category_stats WHERE scan_id = ?1 ORDER BY bytes DESC",
        )?;
        let rows = stmt.query_map([id], |row| {
            Ok(ScanCategoryStatRow {
                scan_id: row.get(0)?,
                category: row.get(1)?,
                bytes: row.get(2)?,
                file_count: row.get(3)?,
            })
        })?;
        let mut res = Vec::new();
        for r in rows {
            res.push(r?);
        }
        Ok(res)
    }

    pub fn delete_scan(&self, id: &str) -> Result<(), DbError> {
        self.conn.execute("DELETE FROM scans WHERE id = ?1", [id])?;
        Ok(())
    }

    pub fn compare_scans(&self, id_older: &str, id_newer: &str) -> Result<Vec<ScanDiffRow>, DbError> {
        let older_stats = self.get_scan_category_stats(id_older)?;
        let newer_stats = self.get_scan_category_stats(id_newer)?;

        let mut diffs = Vec::new();
        for cat in ss_classify::FileCategory::ALL {
            let cat_u8 = cat.as_u8();
            let old = older_stats.iter().find(|s| s.category == cat_u8);
            let new = newer_stats.iter().find(|s| s.category == cat_u8);

            let old_bytes = old.map(|s| s.bytes).unwrap_or(0);
            let new_bytes = new.map(|s| s.bytes).unwrap_or(0);
            let old_files = old.map(|s| s.file_count).unwrap_or(0);
            let new_files = new.map(|s| s.file_count).unwrap_or(0);

            diffs.push(ScanDiffRow {
                category: cat_u8,
                older_bytes: old_bytes,
                newer_bytes: new_bytes,
                delta_bytes: (new_bytes as i64) - (old_bytes as i64),
                older_files: old_files,
                newer_files: new_files,
                delta_files: (new_files as i32) - (old_files as i32),
            });
        }
        Ok(diffs)
    }
}

trait OptionalRow {
    fn optional(self) -> Result<Option<Self::Item>, rusqlite::Error>;
    type Item;
}

impl<T> OptionalRow for rusqlite::Result<T> {
    type Item = T;

    fn optional(self) -> Result<Option<T>, rusqlite::Error> {
        match self {
            Ok(v) => Ok(Some(v)),
            Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
            Err(e) => Err(e),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn schema_migrates_and_round_trips_group_job() {
        let store = Store::open_in_memory().expect("open");
        let group = GroupRow {
            id: "g1".into(),
            created_at: 1,
            request_json: "{}".into(),
            state: "Active".into(),
        };
        store.insert_group(&group).expect("group");
        let job = JobRow {
            id: "j1".into(),
            group_id: "g1".into(),
            spec_json: "{}".into(),
            state: "Queued".into(),
            progress: 0.0,
            decision_json: None,
            recipe_json: None,
            output_uri: None,
            output_bytes: None,
            saved_bytes: None,
            skip_reason: None,
            fail_reason: None,
            message: String::new(),
            updated_at: 1,
        };
        store.insert_job(&job).expect("job");
        let loaded = store.get_job("j1").expect("get");
        assert_eq!(loaded.state, "Queued");
        assert_eq!(store.list_groups().expect("list").len(), 1);
    }

    #[test]
    fn running_jobs_reset_to_queued() {
        let store = Store::open_in_memory().expect("open");
        store
            .insert_group(&GroupRow {
                id: "g1".into(),
                created_at: 1,
                request_json: "{}".into(),
                state: "Active".into(),
            })
            .unwrap();
        store
            .insert_job(&JobRow {
                id: "j1".into(),
                group_id: "g1".into(),
                spec_json: "{}".into(),
                state: "Running".into(),
                progress: 0.5,
                decision_json: None,
                recipe_json: None,
                output_uri: None,
                output_bytes: None,
                saved_bytes: None,
                skip_reason: None,
                fail_reason: None,
                message: String::new(),
                updated_at: 1,
            })
            .unwrap();
        store
            .insert_job(&JobRow {
                id: "j2".into(),
                group_id: "g1".into(),
                spec_json: "{}".into(),
                state: "Cancelling".into(),
                progress: 0.2,
                decision_json: None,
                recipe_json: None,
                output_uri: None,
                output_bytes: None,
                saved_bytes: None,
                skip_reason: None,
                fail_reason: None,
                message: String::new(),
                updated_at: 1,
            })
            .unwrap();
        let ids = store.reset_running_jobs(2).expect("reset");
        assert_eq!(ids, vec!["j1".to_string(), "j2".to_string()]);
        assert_eq!(store.get_job("j1").unwrap().state, "Queued");
        assert_eq!(store.get_job("j2").unwrap().state, "Queued");
    }

    #[test]
    fn delete_job_removes_row_and_empty_group() {
        let store = Store::open_in_memory().expect("open");
        store
            .insert_group(&GroupRow {
                id: "g1".into(),
                created_at: 1,
                request_json: "{}".into(),
                state: "Active".into(),
            })
            .unwrap();
        store
            .insert_job(&JobRow {
                id: "j1".into(),
                group_id: "g1".into(),
                spec_json: "{}".into(),
                state: "Succeeded".into(),
                progress: 1.0,
                decision_json: None,
                recipe_json: None,
                output_uri: None,
                output_bytes: None,
                saved_bytes: None,
                skip_reason: None,
                fail_reason: None,
                message: String::new(),
                updated_at: 1,
            })
            .unwrap();
        let group_id = store.delete_job("j1").expect("delete");
        assert_eq!(group_id, "g1");
        assert!(store.get_job("j1").is_err());
        assert_eq!(store.count_jobs_in_group("g1").unwrap(), 0);
        store.delete_group("g1").expect("delete group");
        assert!(store.get_group("g1").is_err());
    }

    #[test]
    fn delete_job_keeps_group_with_remaining_jobs() {
        let store = Store::open_in_memory().expect("open");
        store
            .insert_group(&GroupRow {
                id: "g1".into(),
                created_at: 1,
                request_json: "{}".into(),
                state: "Active".into(),
            })
            .unwrap();
        for id in ["j1", "j2"] {
            store
                .insert_job(&JobRow {
                    id: id.into(),
                    group_id: "g1".into(),
                    spec_json: "{}".into(),
                    state: "Succeeded".into(),
                    progress: 1.0,
                    decision_json: None,
                    recipe_json: None,
                    output_uri: None,
                    output_bytes: None,
                    saved_bytes: None,
                    skip_reason: None,
                    fail_reason: None,
                    message: String::new(),
                    updated_at: 1,
                })
                .unwrap();
        }
        let group_id = store.delete_job("j1").expect("delete");
        assert_eq!(group_id, "g1");
        assert!(store.get_job("j1").is_err());
        assert_eq!(store.count_jobs_in_group("g1").unwrap(), 1);
        assert!(store.get_group("g1").is_ok());
    }

    #[test]
    fn scan_storage_round_trip_and_diff() {
        let store = Store::open_in_memory().expect("open");
        let mut tree = ss_scan::ScanTree::new();
        let root = tree.add_node(
            ss_scan::NO_NODE,
            "root",
            ss_scan::NodeKind::Directory,
            ss_classify::FileCategory::Other,
            1000,
            1000,
            10,
            0,
        );
        tree.root = root;

        let summary = ScanSummaryRow {
            id: "scan-1".into(),
            created_at: 100,
            scope_json: "{}".into(),
            source_level: "L3".into(),
            root_path: "/storage/emulated/0".into(),
            total_bytes: 100_000,
            used_bytes: 60_000,
            free_bytes: 40_000,
            scanned_bytes: 55_000,
            file_count: 50,
            duration_ms: 1200,
        };

        let stats = vec![
            (ss_classify::FileCategory::Video, 30_000, 10),
            (ss_classify::FileCategory::Image, 25_000, 40),
        ];

        store.insert_scan(&summary, &tree, &stats).expect("insert");

        let list = store.list_scans(10).expect("list");
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].id, "scan-1");

        let loaded_tree = store.load_scan_tree("scan-1").expect("load tree");
        assert_eq!(loaded_tree.root, root);

        let cat_stats = store.get_scan_category_stats("scan-1").expect("cat stats");
        assert_eq!(cat_stats.len(), 2);
        // Test update_scan_tree
        let mut updated_tree = loaded_tree;
        let _sub = updated_tree.add_node(root, "sub.mp4", ss_scan::NodeKind::File, ss_classify::FileCategory::Video, 500, 500, 20, 0);
        let updated_stats = vec![(ss_classify::FileCategory::Video, 500, 1)];
        store.update_scan_tree("scan-1", &updated_tree, &updated_stats).expect("update tree");

        let reloaded = store.load_scan_tree("scan-1").expect("reload tree");
        assert_eq!(reloaded.nodes.len(), 2);
        let updated_cat = store.get_scan_category_stats("scan-1").expect("cat stats after update");
        assert_eq!(updated_cat.len(), 1);
        assert_eq!(updated_cat[0].bytes, 500);
    }
}
