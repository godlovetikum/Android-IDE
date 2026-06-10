# editor module

## Purpose

Monaco Editor integration via WebView. Manages open file tabs, cursor state, pending content buffering, and bidirectional JS↔Rust communication on both Android and desktop builds.

## Responsibilities

- Tab lifecycle: open, close, switch, dirty tracking
- Language detection from file extension (20+ languages)
- Pending content buffering — stores editor content between edits and save
- Cursor position tracking for the status bar
- JavaScript bridge protocol (EditorOutbound / EditorInbound messages)
- Monaco configuration sync (theme, font size, tab size)

## Public API

```rust
use android_ide_editor::{EditorManager, EditorError};
use android_ide_editor::webview;
use android_ide_editor::bridge::EditorOutbound;

let mut editor = EditorManager::new();

// Tab management
let tab_id = editor.open_file("/path/to/main.rs");
editor.mark_dirty(&tab_id);
editor.mark_clean(&tab_id);
let active = editor.active_tab();
editor.close_tab(&tab_id)?;

// Content buffering (called by webview bridge on contentChanged)
editor.set_pending_content(&tab_id, new_content.to_string());
let to_save = editor.take_pending_content(&tab_id); // consumed at save time

// Cursor position
editor.update_cursor(42, 15);
let (line, col) = editor.cursor_position();

// Send message to Monaco (Android: via JNI; Desktop: via wry handle)
#[cfg(target_os = "android")]
webview::send_to_editor(&EditorOutbound::SetTheme { theme: "vs-dark".into() })?;

// Check readiness
if webview::is_editor_ready() { ... }
```

## JavaScript Bridge

| Direction | Message type | Payload |
|-----------|-------------|---------|
| Rust → JS | `loadFile` | path, content, language |
| Rust → JS | `setTheme` | theme |
| Rust → JS | `setFontSize` | size |
| Rust → JS | `requestSave` | path |
| Rust → JS | `closeTab` | path |
| JS → Rust | `ready` | — |
| JS → Rust | `contentChanged` | path, content |
| JS → Rust | `cursorMoved` | line, column |
| JS → Rust | `fileSaved` | path |

## Module Structure

| File | Purpose |
|------|---------|
| `src/lib.rs` | Public API surface |
| `src/manager.rs` | `EditorManager` — tab lifecycle, cursor state, pending content |
| `src/tab.rs` | `EditorTab`, `TabId`, language detection |
| `src/bridge.rs` | `EditorOutbound` / `EditorInbound` message types + `outbound_to_js()` |
| `src/webview.rs` | WebView lifecycle: wry (desktop) and Android JNI bridge |
| `src/error.rs` | `EditorError` enum |

Android companion files:

| File | Purpose |
|------|---------|
| `android/assets/editor/index.html` | Monaco HTML shell loaded by the WebView |
| `android/assets/editor/monaco-init.js` | Monaco initialisation and bridge protocol implementation |
| `android/java/dev/androidide/EditorBridge.java` | `@JavascriptInterface` receiver + `evaluateScriptAsync` helper |

## Dependencies

| Crate | Target | Justification |
|-------|--------|---------------|
| `serde` / `serde_json` | all | JSON bridge message serialization |
| `thiserror` | all | Error type derivation |
| `tracing` | all | Structured debug logging |
| `wry = "0.46"` | desktop only | Native OS webview (WebKit2GTK / WKWebView / WebView2). Only mature cross-platform Rust webview binding. |
| `raw-window-handle = "0.6"` | desktop only | Required by wry for window embedding |
| `jni = "0.21"` | Android only | JNI bridge for `nativeOnEditorMessage` and `nativeRegisterEditorWebView` |

## Known Limitations

- Desktop wry integration requires a native window handle; integration with Slint's winit backend is task 017 follow-up.
- Auto-save is implemented in `src/ui.rs` (app layer) rather than this module to avoid a circular dependency with the filesystem module.
- The Monaco HTML loads from CDN (`unpkg.com/monaco-editor@0.52.0`); offline bundling is a future enhancement.
