use thiserror::Error;

#[derive(Debug, Error)]
pub enum ImageError {
    #[error("io: {0}")]
    Io(String),
    #[error("decode failed: {0}")]
    Decode(String),
    #[error("encode failed: {0}")]
    Encode(String),
    #[error("unsupported format: {0}")]
    UnsupportedFormat(String),
    #[error("invalid recipe")]
    InvalidRecipe,
    #[error("cancelled")]
    Cancelled,
}
