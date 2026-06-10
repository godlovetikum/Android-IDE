/// android-ide/modules/lsp/src/lib.rs
///
/// LSP module — Language Server Protocol client.
/// Manages server lifecycle, JSON-RPC transport, diagnostics, completion.
///
/// Phase 4 subsystem — skeleton only.
/// See STATUS_TRACKER.md task 301–306.

pub mod error;

pub use error::LspError;

pub type Result<T> = std::result::Result<T, LspError>;

/// Placeholder LspManager.
/// TODO(task-301): Implement LSP server lifecycle.
pub struct LspManager;

impl LspManager {
    pub fn new() -> Self { Self }
}
