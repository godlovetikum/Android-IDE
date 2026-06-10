/// android-ide/modules/extensions/src/lib.rs
///
/// Extensions module — extension loader, permissions, lifecycle.
///
/// Phase 5 subsystem — skeleton only.
/// See STATUS_TRACKER.md task 401–405.

pub mod error;

pub use error::ExtensionError;

pub type Result<T> = std::result::Result<T, ExtensionError>;

pub struct ExtensionManager;

impl ExtensionManager {
    pub fn new() -> Self { Self }
}
