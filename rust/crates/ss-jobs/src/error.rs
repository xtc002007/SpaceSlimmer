use thiserror::Error;

#[derive(Debug, Error)]
pub enum JobError {
    #[error("db: {0}")]
    Db(#[from] ss_sqlite::DbError),
    #[error("not found: {0}")]
    NotFound(String),
    #[error("invalid state: {0}")]
    InvalidState(String),
    #[error("io: {0}")]
    Io(String),
}
