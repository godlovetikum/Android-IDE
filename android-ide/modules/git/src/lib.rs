/// android-ide/modules/git/src/lib.rs
///
/// Git module — public API surface.
/// Full Git workflow via git2-rs: clone, commit, branch, push/pull, status, diff.
///
/// Phase 3 subsystem — skeleton only.
/// See STATUS_TRACKER.md task 201–208.

pub mod error;

pub use error::GitError;

pub type Result<T> = std::result::Result<T, GitError>;

/// Placeholder GitManager.
/// TODO(task-201): Implement Git operations.
pub struct GitManager;

impl GitManager {
    pub fn new() -> Self { Self }
}
