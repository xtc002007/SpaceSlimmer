//! SQLite persistence for job groups, jobs, vault, and settings.

mod error;
mod schema;
mod store;

pub use error::DbError;
pub use schema::SCHEMA_VERSION;
pub use store::{GroupRow, JobRow, SettingsStore, Store, VaultRow};
