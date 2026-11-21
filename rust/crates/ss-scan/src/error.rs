use thiserror::Error;

#[derive(Debug, Error)]
pub enum ScanError {
    #[error("I/O error during scan: {0}")]
    Io(#[from] std::io::Error),

    #[error("Platform lister error: {0}")]
    Platform(String),

    #[error("Scan was cancelled by user")]
    Cancelled,

    #[error("Invalid root path: {0}")]
    InvalidRoot(String),

    #[error("Node not found: {0}")]
    NodeNotFound(u32),
}
