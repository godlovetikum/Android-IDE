/// android-ide/modules/editor/src/webview.rs
///
/// WebView manager for the Monaco Editor.
///
/// Two platforms are supported:
///
///   Desktop  — wry (cross-platform webview crate). The webview is embedded
///              into the Slint window's editor panel. IPC via wry's built-in channel.
///
///   Android  — The Activity owns the android.webkit.WebView. Rust sends
///              messages via JNI (evaluateJavascript); Monaco calls back via
///              EditorBridge.nativeOnEditorMessage().
///
/// Bridge protocol: see bridge.rs.
///
/// Save callback (task 019):
///   Callers register a save handler via register_save_handler(). When the
///   editor sends a `fileSaved` message, the handler receives (path, content)
///   and is responsible for writing to disk and confirming completion.
///
/// Dependencies (new for task 017):
///   wry = "0.46"              (desktop only)
///   raw-window-handle = "0.6" (desktop only)
///   jni = "0.21"              (Android only)

use std::sync::{Arc, Mutex, OnceLock};
use tracing::{debug, error, info, warn};

use crate::bridge::{outbound_to_js, EditorInbound, EditorOutbound};
use crate::error::EditorError;
use crate::manager::EditorManager;

// ---------------------------------------------------------------------------
// Save callback registry (task 019)
// ---------------------------------------------------------------------------

type SaveHandler = dyn Fn(String /*path*/, String /*content*/) + Send + Sync;

static SAVE_HANDLER: OnceLock<Arc<SaveHandler>> = OnceLock::new();

// ---------------------------------------------------------------------------
// Cursor callback registry (task 021)
// ---------------------------------------------------------------------------

/// Called whenever the cursor moves in Monaco (line, column — 1-based).
/// Registered by the UI layer to update the status bar properties.
type CursorHandler = dyn Fn(u32 /*line*/, u32 /*column*/) + Send + Sync;

static CURSOR_HANDLER: OnceLock<Arc<CursorHandler>> = OnceLock::new();

/// Register a function to be called on every cursor position change.
///
/// On desktop the handler runs on the main thread (wry IPC callback is
/// main-thread). On Android it runs on the WebView's background thread, so
/// the handler must use `slint::invoke_from_event_loop` to update UI.
///
/// Only the first registration is accepted; subsequent calls are ignored.
pub fn register_cursor_handler<F>(handler: F)
where
    F: Fn(u32, u32) + Send + Sync + 'static,
{
    if CURSOR_HANDLER.set(Arc::new(handler)).is_err() {
        warn!("Cursor handler already registered — duplicate registration ignored");
    }
}

/// Register a function to be called when the editor requests a save.
///
/// The handler receives the file path and the current content. It is
/// responsible for writing to disk. Call this once from the app init path
/// (src/ui.rs) before any editor messages arrive.
///
/// Only the first registration is accepted; subsequent calls are ignored.
pub fn register_save_handler<F>(handler: F)
where
    F: Fn(String, String) + Send + Sync + 'static,
{
    if SAVE_HANDLER.set(Arc::new(handler)).is_err() {
        warn!("Save handler already registered — duplicate registration ignored");
    }
}

fn invoke_save_handler(path: String, content: String) {
    if let Some(handler) = SAVE_HANDLER.get() {
        handler(path, content);
    } else {
        warn!("fileSaved fired but no save handler registered — content discarded");
    }
}

// ---------------------------------------------------------------------------
// Editor-ready flag
// ---------------------------------------------------------------------------

static EDITOR_READY: OnceLock<bool> = OnceLock::new();

/// Returns true once Monaco has sent the `ready` message.
pub fn is_editor_ready() -> bool {
    EDITOR_READY.get().copied().unwrap_or(false)
}

// ---------------------------------------------------------------------------
// Inbound message handler (shared between platforms)
// ---------------------------------------------------------------------------

/// Process a JSON message received from the Monaco editor.
///
/// Called from the desktop IPC handler and from the Android JNI bridge.
/// Thread-safe.
pub fn handle_inbound_message(json: &str, manager: &Mutex<EditorManager>) {
    let msg: EditorInbound = match serde_json::from_str(json) {
        Ok(m) => m,
        Err(e) => {
            error!("Failed to parse editor message: {e} — payload: {json}");
            return;
        }
    };

    debug!(?msg, "Editor inbound");

    let mut mgr = match manager.lock() {
        Ok(g) => g,
        Err(e) => { error!("EditorManager poisoned: {e}"); return; }
    };

    match msg {
        EditorInbound::Ready => {
            info!("Monaco editor ready");
            EDITOR_READY.get_or_init(|| true);
        }

        EditorInbound::ContentChanged { path, content } => {
            // Explicit binding: releases the immutable borrow from tab_by_path
            // before the mutable borrows of mark_dirty / set_pending_content.
            let tab_opt = mgr.tab_by_path(&path).cloned();
            if let Some(tab) = tab_opt {
                mgr.mark_dirty(&tab.id);
                mgr.set_pending_content(&tab.id, content);
            }
        }

        EditorInbound::CursorMoved { line, column } => {
            mgr.update_cursor(line, column);
            // Notify the UI layer so the status bar can be updated.
            // The handler uses invoke_from_event_loop on Android (task 021).
            if let Some(handler) = CURSOR_HANDLER.get() {
                handler(line, column);
            }
        }

        EditorInbound::FileSaved { path } => {
            // Explicit binding: releases immutable borrow before mutable take/mark calls.
            let tab_opt = mgr.tab_by_path(&path).cloned();
            if let Some(tab) = tab_opt {
                if let Some(content) = mgr.take_pending_content(&tab.id) {
                    // Optimistically mark clean; save handler re-dirties on failure.
                    mgr.mark_clean(&tab.id);
                    drop(mgr); // release lock before invoking handler
                    invoke_save_handler(path, content);
                }
                // No pending content means the file is already clean — nothing to do.
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Desktop webview (wry)
// ---------------------------------------------------------------------------

#[cfg(not(target_os = "android"))]
pub mod desktop {
    use super::*;
    use wry::{WebView, WebViewBuilder};

    /// Build a wry WebView that loads the Monaco editor HTML.
    ///
    /// `window` must implement `raw_window_handle::HasWindowHandle`.
    /// The IPC handler forwards JS messages to `handle_inbound_message`.
    pub fn build_webview<W>(
        window: &W,
        editor_html_path: &std::path::Path,
        manager: Arc<Mutex<EditorManager>>,
    ) -> Result<WebView, EditorError>
    where
        W: raw_window_handle::HasWindowHandle + raw_window_handle::HasDisplayHandle,
    {
        let html_url = format!("file://{}", editor_html_path.to_string_lossy());
        let mgr = Arc::clone(&manager);

        let webview = WebViewBuilder::new()
            .with_url(&html_url)
            .with_ipc_handler(move |request| {
                handle_inbound_message(request.body(), &mgr);
            })
            .with_devtools(cfg!(debug_assertions))
            .build(window)
            .map_err(|e| EditorError::WebViewInitFailed(e.to_string()))?;

        info!(url = html_url, "Desktop WebView created");
        Ok(webview)
    }

    /// Send an outbound message to Monaco.
    pub fn send_message(webview: &WebView, msg: &EditorOutbound) -> Result<(), EditorError> {
        let js = outbound_to_js(msg);
        webview.evaluate_script(&js)
            .map_err(|e| EditorError::WebViewEvalFailed(e.to_string()))
    }
}

// ---------------------------------------------------------------------------
// Android webview JNI bridge
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
pub mod android {
    use super::*;
    use jni::objects::{JClass, JObject, JString};
    use jni::JNIEnv;

    static EDITOR_MANAGER: OnceLock<Arc<Mutex<EditorManager>>> = OnceLock::new();

    pub fn init_android_bridge(manager: Arc<Mutex<EditorManager>>) {
        if EDITOR_MANAGER.set(manager).is_err() {
            warn!("Android editor bridge already initialised");
        }
    }

    /// Called by EditorBridge.java → nativeOnEditorMessage().
    #[no_mangle]
    pub extern "system" fn Java_dev_androidide_EditorBridge_nativeOnEditorMessage(
        mut env: JNIEnv,
        _class: JClass,
        json: JString,
    ) {
        let json_str: String = match env.get_string(&json) {
            Ok(s) => s.into(),
            Err(e) => { error!("nativeOnEditorMessage: get_string failed: {e}"); return; }
        };

        if let Some(mgr) = EDITOR_MANAGER.get() {
            handle_inbound_message(&json_str, mgr);
        } else {
            error!("nativeOnEditorMessage: EditorManager not yet initialised");
        }
    }

    // -----------------------------------------------------------------------
    // Sending to Monaco on Android
    // -----------------------------------------------------------------------

    static WEBVIEW_SENDER: OnceLock<WebViewSender> = OnceLock::new();

    pub struct WebViewSender {
        vm: jni::JavaVM,
        webview_ref: jni::objects::GlobalRef,
    }

    // SAFETY: GlobalRef is designed to be Send+Sync across threads.
    unsafe impl Send for WebViewSender {}
    unsafe impl Sync for WebViewSender {}

    impl WebViewSender {
        pub fn evaluate(&self, js: &str) {
            let env = match self.vm.attach_current_thread() {
                Ok(e) => e,
                Err(e) => { error!("WebViewSender attach: {e}"); return; }
            };
            let mut env = unsafe { env.unsafe_clone() };

            let j_js = match env.new_string(js) {
                Ok(s) => s,
                Err(e) => { error!("WebViewSender new_string: {e}"); return; }
            };

            if let Err(e) = env.call_static_method(
                "dev/androidide/EditorBridge",
                "evaluateScriptAsync",
                "(Landroid/webkit/WebView;Ljava/lang/String;)V",
                &[
                    jni::objects::JValue::Object(self.webview_ref.as_obj()),
                    jni::objects::JValue::Object(&j_js),
                ],
            ) {
                error!("evaluateScriptAsync JNI: {e}");
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_dev_androidide_MainActivity_nativeRegisterEditorWebView(
        env: JNIEnv,
        _class: JClass,
        webview: JObject,
    ) {
        let vm = match env.get_java_vm() {
            Ok(v) => v,
            Err(e) => { error!("nativeRegisterEditorWebView get_java_vm: {e}"); return; }
        };
        let global_ref = match env.new_global_ref(webview) {
            Ok(r) => r,
            Err(e) => { error!("nativeRegisterEditorWebView new_global_ref: {e}"); return; }
        };

        if WEBVIEW_SENDER.set(WebViewSender { vm, webview_ref: global_ref }).is_err() {
            warn!("WebView sender already registered");
        } else {
            info!("Editor WebView registered");
        }
    }

    pub fn send_message(msg: &EditorOutbound) -> Result<(), EditorError> {
        let sender = WEBVIEW_SENDER.get().ok_or(EditorError::WebViewNotRegistered)?;
        sender.evaluate(&outbound_to_js(msg));
        Ok(())
    }
}

// ---------------------------------------------------------------------------
// Platform-agnostic send helper
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
pub fn send_to_editor(msg: &EditorOutbound) -> Result<(), EditorError> {
    android::send_message(msg)
}

#[cfg(not(target_os = "android"))]
pub fn send_to_editor(_msg: &EditorOutbound) -> Result<(), EditorError> {
    // On desktop the caller holds the wry WebView directly (see src/ui.rs).
    Err(EditorError::WebViewNotRegistered)
}
