/// android-ide/modules/editor/src/error.rs

use thiserror::Error;

#[derive(Debug, Error)]
pub enum EditorError {
    #[error("File not found: {path}")]
    FileNotFound { path: String },

    #[error("Tab not found: {id}")]
    TabNotFound { id: String },

    #[error("Save failed: {reason}")]
    SaveFailed { reason: String },

    #[error("WebView initialisation failed: {0}")]
    WebViewInitFailed(String),

    #[error("WebView script evaluation failed: {0}")]
    WebViewEvalFailed(String),

    /// Returned when send_to_editor() is called before the WebView is registered.
    #[error("Editor WebView not yet registered — call nativeRegisterEditorWebView() from the Activity")]
    WebViewNotRegistered,

    #[error("Filesystem error: {0}")]
    Filesystem(#[from] android_ide_filesystem::FilesystemError),

    #[error("WebView bridge error: {0}")]
    Bridge(String),
}
