/// android-ide/modules/editor/src/bridge.rs
///
/// JavaScript bridge for Monaco Editor WebView communication.
///
/// Messages flow in two directions:
/// - Rust → JS: loadFile, setTheme, setFontSize, saveFile, closeTab
/// - JS → Rust: onContentChanged, onCursorMoved, onFileSaved, onReady
///
/// The bridge serializes all messages as JSON.

use serde::{Deserialize, Serialize};

/// Messages Rust sends to the Monaco editor (via WebView.evaluateJavascript)
#[derive(Debug, Serialize)]
#[serde(tag = "type", rename_all = "camelCase")]
pub enum EditorOutbound {
    /// Load a file into the editor
    LoadFile {
        path: String,
        content: String,
        language: String,
    },
    /// Apply a Monaco theme
    SetTheme {
        theme: String,
    },
    /// Update font size
    SetFontSize {
        size: u32,
    },
    /// Trigger explicit save (JS side should notify back via onFileSaved)
    RequestSave {
        path: String,
    },
    /// Close a tab by path
    CloseTab {
        path: String,
    },
}

/// Messages the Monaco editor sends back to Rust (via JavaScript interface / addJavascriptInterface)
#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "camelCase")]
pub enum EditorInbound {
    /// Editor is ready to receive content
    Ready,
    /// File content has changed (triggered on each edit)
    ContentChanged {
        path: String,
        content: String,
    },
    /// Cursor position changed
    CursorMoved {
        line: u32,
        column: u32,
    },
    /// File was saved (either explicitly or via auto-save)
    FileSaved {
        path: String,
    },
}

/// Serialize an outbound message to a JavaScript call string.
/// Usage: webview.evaluate_javascript(&outbound_to_js(&msg))
pub fn outbound_to_js(msg: &EditorOutbound) -> String {
    let json = serde_json::to_string(msg).unwrap_or_default();
    format!("window.androidIDE?.receiveMessage({json});")
}
