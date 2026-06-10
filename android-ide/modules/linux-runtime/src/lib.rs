/// android-ide/modules/linux-runtime/src/lib.rs
///
/// Linux Runtime module — public API surface.
/// Manages the proot-based embedded Linux environment on Android.
///
/// Phase 2 subsystem — not yet implemented.
/// See STATUS_TRACKER.md task 103–104.

pub mod error;

pub use error::LinuxRuntimeError;

pub type Result<T> = std::result::Result<T, LinuxRuntimeError>;

/// Placeholder LinuxRuntimeManager.
/// TODO(task-103): Implement proot environment bootstrap.
pub struct LinuxRuntimeManager;

impl LinuxRuntimeManager {
    pub fn new() -> Self { Self }
}
