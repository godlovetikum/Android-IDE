/// android-ide/modules/editor/src/lib.rs
///
/// Editor module — public API surface.
///
/// Manages Monaco Editor integration via WebView (Android WebView on device,
/// wry on desktop). Handles tab lifecycle, cursor state, content buffering,
/// and bidirectional JS bridge communication.
///
/// Dependencies: filesystem (for read/write), settings (for editor config)

pub mod bridge;
pub mod error;
pub mod manager;
pub mod tab;
pub mod webview;

pub use error::EditorError;
pub use manager::EditorManager;
pub use tab::{EditorTab, TabId};

pub type Result<T> = std::result::Result<T, EditorError>;
