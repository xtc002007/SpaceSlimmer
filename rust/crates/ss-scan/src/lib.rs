//! SpaceSlimmer storage scanner crate.
//!
//! Compact Arena tree, directory walking, small file folding, and aggregation engine.

pub mod error;
pub mod scanner;
pub mod tree;

pub use error::ScanError;
pub use scanner::{scan_native, scan_platform_lister, ScanConfig, ScanProgress, ScanScope};
pub use tree::{
    Node, NodeKind, ScanTree, FLAG_COLLAPSED, FLAG_DELETED, FLAG_NONE, FLAG_PROTECTED,
    FLAG_SCAN_ERROR, FLAG_SYMLINK, NO_NODE,
};
