/// android-ide/modules/filesystem/src/lib.rs
///
/// Filesystem module — public API surface.
///
/// Provides file system access via Android Storage Access Framework (SAF) on Android,
/// and direct std::fs access on desktop for development builds.
///
/// Dependencies: None (foundation layer — no other IDE modules imported here)

pub mod error;
pub mod manager;
pub mod path;
pub mod tree;

#[cfg(target_os = "android")]
pub mod saf;

pub use error::FilesystemError;
pub use manager::FilesystemManager;
pub use tree::{FileKind, FileNode, FileTree};

pub type Result<T> = std::result::Result<T, FilesystemError>;
