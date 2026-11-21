use rusqlite::Connection;

use crate::error::DbError;

/// Current schema version written to `schema_migrations`.
pub const SCHEMA_VERSION: u32 = 5;

const MIGRATION_V1: &str = r"
CREATE TABLE IF NOT EXISTS groups (
    id TEXT PRIMARY KEY NOT NULL,
    created_at INTEGER NOT NULL,
    request_json TEXT NOT NULL,
    state TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS jobs (
    id TEXT PRIMARY KEY NOT NULL,
    group_id TEXT NOT NULL,
    spec_json TEXT NOT NULL,
    state TEXT NOT NULL,
    progress REAL NOT NULL DEFAULT 0,
    decision_json TEXT,
    recipe_json TEXT,
    output_uri TEXT,
    output_bytes INTEGER,
    saved_bytes INTEGER,
    skip_reason TEXT,
    fail_reason TEXT,
    message TEXT NOT NULL DEFAULT '',
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (group_id) REFERENCES groups(id)
);

CREATE INDEX IF NOT EXISTS idx_jobs_group_id ON jobs(group_id);
CREATE INDEX IF NOT EXISTS idx_jobs_state ON jobs(state);

CREATE TABLE IF NOT EXISTS vault (
    id TEXT PRIMARY KEY NOT NULL,
    job_id TEXT NOT NULL,
    original_path TEXT NOT NULL,
    original_bytes INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    restored INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY NOT NULL,
    value_json TEXT NOT NULL
);
";

pub fn migrate(conn: &Connection) -> Result<(), DbError> {
    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY NOT NULL);",
    )?;

    let version: Option<u32> = conn
        .query_row(
            "SELECT version FROM schema_migrations ORDER BY version DESC LIMIT 1",
            [],
            |row| row.get(0),
        )
        .optional()
        .map_err(DbError::from)?;

    let current = version.unwrap_or(0);
    if current >= SCHEMA_VERSION {
        return Ok(());
    }

    if current < 1 {
        conn.execute_batch(MIGRATION_V1)?;
        conn.execute(
            "INSERT OR REPLACE INTO schema_migrations(version) VALUES (1)",
            [],
        )?;
    }

    if current < 2 {
        conn.execute_batch(MIGRATION_V2)?;
        conn.execute(
            "INSERT OR REPLACE INTO schema_migrations(version) VALUES (2)",
            [],
        )?;
    }

    if current < 3 {
        conn.execute_batch(MIGRATION_V3)?;
        conn.execute(
            "INSERT OR REPLACE INTO schema_migrations(version) VALUES (3)",
            [],
        )?;
    }

    if current < 4 {
        conn.execute_batch(MIGRATION_V4)?;
        conn.execute(
            "INSERT OR REPLACE INTO schema_migrations(version) VALUES (4)",
            [],
        )?;
    }

    if current < 5 {
        conn.execute_batch(MIGRATION_V5)?;
        conn.execute(
            "INSERT OR REPLACE INTO schema_migrations(version) VALUES (5)",
            [],
        )?;
    }

    Ok(())
}

const MIGRATION_V2: &str = r"
ALTER TABLE vault ADD COLUMN vault_path TEXT;
ALTER TABLE vault ADD COLUMN original_uri TEXT;
UPDATE vault SET vault_path = original_path WHERE vault_path IS NULL;
UPDATE vault SET original_uri = original_path WHERE original_uri IS NULL;
";

const MIGRATION_V3: &str = r"
CREATE TABLE IF NOT EXISTS events (
    id TEXT PRIMARY KEY NOT NULL,
    ts INTEGER NOT NULL,
    type TEXT NOT NULL,
    payload_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_events_ts ON events(ts);
";

const MIGRATION_V4: &str = r"
ALTER TABLE vault ADD COLUMN compressed_uri TEXT;
";

const MIGRATION_V5: &str = r"
CREATE TABLE IF NOT EXISTS scans (
    id TEXT PRIMARY KEY NOT NULL,
    created_at INTEGER NOT NULL,
    scope_json TEXT NOT NULL,
    source_level TEXT NOT NULL,
    root_path TEXT NOT NULL,
    total_bytes INTEGER NOT NULL,
    used_bytes INTEGER NOT NULL,
    free_bytes INTEGER NOT NULL,
    scanned_bytes INTEGER NOT NULL,
    file_count INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL,
    tree_blob TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_scans_created_at ON scans(created_at);

CREATE TABLE IF NOT EXISTS scan_category_stats (
    scan_id TEXT NOT NULL,
    category INTEGER NOT NULL,
    bytes INTEGER NOT NULL,
    file_count INTEGER NOT NULL,
    PRIMARY KEY (scan_id, category),
    FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
);
";

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
