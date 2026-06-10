/// android-ide/modules/terminal/src/lib.rs
///
/// Terminal module — public API surface.
/// Manages PTY sessions connected to the embedded Linux runtime.
///
/// Phase 2 subsystem — not yet implemented.
/// See STATUS_TRACKER.md task 101–105.

pub mod error;

pub use error::TerminalError;

pub type Result<T> = std::result::Result<T, TerminalError>;

/// Placeholder TerminalManager.
/// TODO(task-101): Implement PTY management and terminal UI.
pub struct TerminalManager;

impl TerminalManager {
    pub fn new() -> Self { Self }
}
