use thiserror::Error;

#[derive(Debug, Error)]
pub enum AudioError {
    #[error("io: {0}")]
    Io(String),
    #[error("decode: {0}")]
    Decode(String),
    #[error("encode: {0}")]
    Encode(String),
    #[error("unsupported: {0}")]
    Unsupported(String),
    #[error("invalid recipe")]
    InvalidRecipe,
}
