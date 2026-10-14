//! Audio pipeline for SpaceSlimmer (Sprint 4).
//!
//! Decode/probe runs in Rust (symphonia). AAC encoding is delegated to the
//! Android `audio-hw` MediaCodec backend.

mod error;
mod transcode;

pub use error::AudioError;
pub use transcode::{
    options_from_recipe, probe_audio, transcode_audio, validate_source, AudioEncodeOptions,
    AudioEncodeResult, AudioProbe,
};
