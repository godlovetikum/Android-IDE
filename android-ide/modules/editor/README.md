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
- Edit+preview split panel management (Android only)

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

// Edit+preview split panel (Android only)
#[cfg(target_os = "android")]
webview::android::show_preview("file:///data/data/dev.androidide/files/output.html")?;
#[cfg(target_os = "android")]
webview::android::hide_preview()?;

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
| `android/java/dev/androidide/IDEActivity.java` | NativeActivity subclass that creates and owns the WebView overlay |

## Android WebView Architecture

Monaco runs in `android.webkit.WebView`, which is a Java `View`. Java `View`s
are always composited **above** NativeActivity's native Surface (where Slint renders).
`IDEActivity extends NativeActivity` adds a `FrameLayout` overlay via
`getWindow().addContentView()`:

```
┌─────────────────────────────────────────┐
│  Java layer (above native surface)      │
│  ┌─────────────────────────────────┐    │  top margin = 84dp
│  │  mEditorPreviewContainer        │    │  left margin = 0 (portrait)
│  │  ┌──────────────┬─────────────┐ │    │           or 241dp (landscape)
│  │  │ mEditorWV    │ mPreviewWV  │ │    │  bottom margin = 22dp
│  │  │ (Monaco)     │ (preview,   │ │    │
│  │  │              │  GONE by    │ │    │
│  │  │              │  default)   │ │    │
│  │  └──────────────┴─────────────┘ │    │
│  └─────────────────────────────────┘    │
├─────────────────────────────────────────┤
│  Slint native surface (background)      │
│  (app bar, sidebar, tab bar, status bar │
│   visible through WebView gaps/margins) │
└─────────────────────────────────────────┘
```

The preview panel is activated by calling `webview::android::show_preview(url)`
from Rust, which calls `IDEActivity.showPreview(url)` via JNI on the UI thread.
Both WebViews take `weight=1` so they split the available width 50/50.

## WebView Registration Sequence

```
1. JNI_OnLoad fires          → JavaVM stored in saf::JAVA_VM
2. IDEActivity.onCreate()    → super.onCreate() loads .so
                             → setupEditorOverlay() creates mEditorWebView
                             → (no JNI call from Java — PULL design)
3. onStart()                 → NativeActivity starts native thread
4. android_main() begins
   4a. init_safe_bridge()    → SafBridge.init(activity) via JNI
   4b. init_webview_from_activity()
                             → IDEActivity.getInstance().getEditorWebView()
                             → GlobalRef stored in WEBVIEW_SENDER ✓
   4c. slint::android::init()
   4d. run_ui()              → send_to_editor() now works ✓
```

## Dependencies

| Crate | Target | Justification |
|-------|--------|---------------|
| `serde` / `serde_json` | all | JSON bridge message serialization |
| `thiserror` | all | Error type derivation |
| `tracing` | all | Structured debug logging |
| `uuid = "1"` (features: `["v4"]`) | all | Collision-proof tab IDs using OS entropy (`/dev/urandom` on Android). `SystemTime`-based IDs risk collisions when multiple files are opened in rapid succession; UUID v4 is 122 bits of random data and collision-proof in practice. |
| `wry = "0.46"` | desktop only | Native OS webview (WebKit2GTK / WKWebView / WebView2). Only mature cross-platform Rust webview binding. |
| `raw-window-handle = "0.6"` | desktop only | Required by wry for window embedding |
| `jni = "0.21"` | Android only | JNI bridge: `nativeOnEditorMessage` inbound from `EditorBridge.java`; `init_webview_from_activity`, `show_preview`, `hide_preview`, `adjust_editor_bounds` outbound to `IDEActivity.java` |
| `android-ide-filesystem` | all | `saf::get_vm()` — provides the stored `JavaVM` for `init_webview_from_activity()` |

## Known Limitations

- Desktop wry integration requires a native window handle; integration with Slint's winit backend is task 017 follow-up.
- Auto-save is implemented in `src/ui.rs` (app layer) rather than this module to avoid a circular dependency with the filesystem module.
- `adjustEditorBounds()` is designed for dynamic repositioning from Rust once the Slint window performs its first layout pass; it is not yet called automatically. The compile-time dp constants in `IDEActivity` are used until a Rust call overrides them.
- Monaco is bundled in APK assets under `android/assets/editor/vs/`. This directory is gitignored (~20 MB); run `scripts/fetch-monaco.sh` before building locally.
