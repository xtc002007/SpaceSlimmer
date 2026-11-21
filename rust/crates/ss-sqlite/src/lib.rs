//! SQLite persistence for job groups, jobs, vault, settings, and storage scans.

mod error;
mod schema;
mod store;

pub use error::DbError;
pub use schema::SCHEMA_VERSION;
pub use store::{
    GroupRow, JobRow, ScanCategoryStatRow, ScanDiffRow, ScanSummaryRow, SettingsStore, Store,
    VaultRow,
};
