/// android-ide/modules/editor/src/webview.rs
///
/// WebView manager for the Monaco Editor.
///
/// Two platforms are supported:
///
///   Desktop  — wry (cross-platform webview crate). The webview is embedded
///              into the Slint window's editor panel. IPC via wry's built-in channel.
///
///   Android  — IDEActivity owns the android.webkit.WebView. Rust sends
///              messages via JNI (evaluateJavascript); Monaco calls back via
///              EditorBridge.nativeOnEditorMessage(). A second "preview" WebView
///              can be shown alongside the editor for live output via showPreview().
///
/// Bridge protocol: see bridge.rs.
///
/// WebView registration (Android):
///   Rust pulls the WebView from Java (not the other way around).
///   android::init_webview_from_activity() is called from android_main() AFTER
///   IDEActivity.onCreate() has run, using IDEActivity.getInstance().getEditorWebView()
///   via JNI. This avoids all race conditions between the Java main thread and the
///   native android_main thread.
///
/// Edit+Preview:
///   android::show_preview(url) — calls IDEActivity.showPreview(url) via JNI,
///     making the preview WebView visible alongside Monaco (50/50 split).
///   android::hide_preview()    — calls IDEActivity.hidePreview() via JNI.
///
/// Save callback:
///   Callers register a save handler via register_save_handler(). When the
///   editor sends a `fileSaved` message, the handler receives (path, content)
///   and is responsible for writing to disk.

use std::sync::{Arc, Mutex, OnceLock};
use tracing::{debug, error, info, warn};

use crate::bridge::{outbound_to_js, EditorInbound, EditorOutbound};
use crate::error::EditorError;
use crate::manager::EditorManager;

// ---------------------------------------------------------------------------
// Save callback registry
// ---------------------------------------------------------------------------

type SaveHandler = dyn Fn(String /*path*/, String /*content*/) + Send + Sync;

static SAVE_HANDLER: OnceLock<Arc<SaveHandler>> = OnceLock::new();

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
// Cursor callback registry
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
            let tab_opt = mgr.tab_by_path(&path).cloned();
            if let Some(tab) = tab_opt {
                mgr.mark_dirty(&tab.id);
                mgr.set_pending_content(&tab.id, content);
            }
        }

        EditorInbound::CursorMoved { line, column } => {
            mgr.update_cursor(line, column);
            if let Some(handler) = CURSOR_HANDLER.get() {
                handler(line, column);
            }
        }

        EditorInbound::FileSaved { path } => {
            let tab_opt = mgr.tab_by_path(&path).cloned();
            if let Some(tab) = tab_opt {
                if let Some(content) = mgr.take_pending_content(&tab.id) {
                    mgr.mark_clean(&tab.id);
                    drop(mgr);
                    invoke_save_handler(path, content);
                }
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
// Android WebView JNI bridge
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
pub mod android {
    use super::*;
    use jni::objects::{JClass, JString, JObject, JValue};
    use jni::JNIEnv;

    // ── Editor manager reference ─────────────────────────────────────────────

    static EDITOR_MANAGER: OnceLock<Arc<Mutex<EditorManager>>> = OnceLock::new();

    pub fn init_android_bridge(manager: Arc<Mutex<EditorManager>>) {
        if EDITOR_MANAGER.set(manager).is_err() {
            warn!("Android editor bridge already initialised");
        }
    }

    // ── Inbound: EditorBridge.java → Rust ───────────────────────────────────

    /// Called by EditorBridge.java → nativeOnEditorMessage() when Monaco sends a message.
    /// Runs on the WebView's background JavaScript thread.
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

    // ── WEBVIEW_SENDER — outbound Rust → Monaco ──────────────────────────────

    static WEBVIEW_SENDER: OnceLock<WebViewSender> = OnceLock::new();

    pub struct WebViewSender {
        vm: jni::JavaVM,
        webview_ref: jni::objects::GlobalRef,
    }

    // SAFETY: GlobalRef is designed to be Send+Sync across threads.
    unsafe impl Send for WebViewSender {}
    unsafe impl Sync for WebViewSender {}

    impl WebViewSender {
        /// Evaluate a JavaScript string in the Monaco WebView.
        /// Calls EditorBridge.evaluateScriptAsync via JNI.
        pub fn evaluate(&self, js: &str) {
            let guard = match self.vm.attach_current_thread() {
                Ok(e) => e,
                Err(e) => { error!("WebViewSender attach: {e}"); return; }
            };
            let mut env = unsafe { guard.unsafe_clone() };

            let j_js = match env.new_string(js) {
                Ok(s) => s,
                Err(e) => { error!("WebViewSender new_string: {e}"); return; }
            };

            if let Err(e) = env.call_static_method(
                "dev/androidide/EditorBridge",
                "evaluateScriptAsync",
                "(Landroid/webkit/WebView;Ljava/lang/String;)V",
                &[
                    JValue::Object(self.webview_ref.as_obj()),
                    JValue::Object(&j_js),
                ],
            ) {
                error!("evaluateScriptAsync JNI: {e}");
            }
        }
    }

    // ── WebView registration (called from android_main) ──────────────────────

    /// Register the Monaco WebView by pulling it from IDEActivity via JNI.
    ///
    /// Called once from `android_main()` in `src/lib.rs`, after
    /// `IDEActivity.onCreate()` has run (guaranteed by Android lifecycle:
    /// the native thread starts in `onStart()`, which always follows `onCreate()`).
    ///
    /// Uses the JavaVM stored by `saf::init_vm()` (called during `JNI_OnLoad`).
    ///
    /// Pull design rationale:
    ///   Java pushing (IDEActivity calling a JNI export) could race with the
    ///   native thread. Rust pulling in android_main — after all init steps —
    ///   is guaranteed to run after onCreate() and before run_ui().
    pub fn init_webview_from_activity() -> Result<(), EditorError> {
        let stored_vm = android_ide_filesystem::saf::get_vm()
            .ok_or_else(|| EditorError::WebViewInitFailed(
                "JavaVM not available — call after JNI_OnLoad".into()))?;

        let guard = stored_vm
            .attach_current_thread()
            .map_err(|e| EditorError::WebViewInitFailed(format!("attach thread: {e}")))?;
        let mut env = unsafe { guard.unsafe_clone() };

        // Obtain an owned JavaVM for storage in WebViewSender.
        let vm = env.get_java_vm()
            .map_err(|e| EditorError::WebViewInitFailed(format!("get_java_vm: {e}")))?;

        // ── IDEActivity.getInstance() ─────────────────────────────────────────
        let activity_class = env
            .find_class("dev/androidide/IDEActivity")
            .map_err(|e| EditorError::WebViewInitFailed(format!("find IDEActivity: {e}")))?;

        let instance_val = env
            .call_static_method(
                &activity_class,
                "getInstance",
                "()Ldev/androidide/IDEActivity;",
                &[],
            )
            .map_err(|e| EditorError::WebViewInitFailed(format!("getInstance: {e}")))?;

        let instance = instance_val
            .l()
            .map_err(|e| EditorError::WebViewInitFailed(format!("getInstance l(): {e}")))?;

        if instance.is_null() {
            return Err(EditorError::WebViewInitFailed(
                "IDEActivity.getInstance() returned null — Activity not yet created".into(),
            ));
        }

        // ── instance.getEditorWebView() ───────────────────────────────────────
        let webview_val = env
            .call_method(&instance, "getEditorWebView", "()Landroid/webkit/WebView;", &[])
            .map_err(|e| EditorError::WebViewInitFailed(format!("getEditorWebView: {e}")))?;

        let webview_obj = webview_val
            .l()
            .map_err(|e| EditorError::WebViewInitFailed(format!("getEditorWebView l(): {e}")))?;

        if webview_obj.is_null() {
            return Err(EditorError::WebViewInitFailed(
                "getEditorWebView() returned null — WebView not yet created".into(),
            ));
        }

        // ── Create GlobalRef so the WebView survives GC ───────────────────────
        let global_ref = env
            .new_global_ref(&webview_obj)
            .map_err(|e| EditorError::WebViewInitFailed(format!("new_global_ref: {e}")))?;

        if WEBVIEW_SENDER
            .set(WebViewSender { vm, webview_ref: global_ref })
            .is_err()
        {
            warn!("WebView sender already registered — duplicate init_webview_from_activity call");
        } else {
            info!("Monaco WebView registered via IDEActivity.getEditorWebView()");
        }

        Ok(())
    }

    // ── Outbound: Rust → Monaco ───────────────────────────────────────────────

    /// Send an outbound message to Monaco via evaluateJavascript.
    pub fn send_message(msg: &EditorOutbound) -> Result<(), EditorError> {
        let sender = WEBVIEW_SENDER.get().ok_or(EditorError::WebViewNotRegistered)?;
        sender.evaluate(&outbound_to_js(msg));
        Ok(())
    }

    // ── Preview panel (edit+preview split) ───────────────────────────────────

    /// Show the preview panel alongside Monaco (50/50 horizontal split).
    ///
    /// Calls `IDEActivity.showPreview(url)` on the Android UI thread via JNI.
    ///
    /// `url` may be any URL or `file://` URI. Use `"about:blank"` for an empty
    /// preview panel that the caller will populate via `evaluateJavascript`.
    pub fn show_preview(url: &str) -> Result<(), EditorError> {
        let sender = WEBVIEW_SENDER.get().ok_or(EditorError::WebViewNotRegistered)?;

        let guard = sender.vm
            .attach_current_thread()
            .map_err(|e| EditorError::WebViewEvalFailed(e.to_string()))?;
        let mut env = unsafe { guard.unsafe_clone() };

        let j_url = env
            .new_string(url)
            .map_err(|e| EditorError::WebViewEvalFailed(e.to_string()))?;

        env.call_static_method(
            "dev/androidide/IDEActivity",
            "showPreview",
            "(Ljava/lang/String;)V",
            &[JValue::Object(&j_url)],
        )
        .map_err(|e| EditorError::WebViewEvalFailed(format!("showPreview: {e}")))?;

        Ok(())
    }

    /// Hide the preview panel and restore Monaco to full editor width.
    ///
    /// Calls `IDEActivity.hidePreview()` on the Android UI thread via JNI.
    pub fn hide_preview() -> Result<(), EditorError> {
        let sender = WEBVIEW_SENDER.get().ok_or(EditorError::WebViewNotRegistered)?;

        let guard = sender.vm
            .attach_current_thread()
            .map_err(|e| EditorError::WebViewEvalFailed(e.to_string()))?;
        let mut env = unsafe { guard.unsafe_clone() };

        env.call_static_method(
            "dev/androidide/IDEActivity",
            "hidePreview",
            "()V",
            &[],
        )
        .map_err(|e| EditorError::WebViewEvalFailed(format!("hidePreview: {e}")))?;

        Ok(())
    }

    /// Dynamically adjust the editor overlay bounds.
    ///
    /// Calls `IDEActivity.adjustEditorBounds(left, top, right, bottom)` via JNI.
    /// All values are in density-independent pixels (dp).
    ///
    /// Call this from `run_ui()` after the Slint window's first layout so the
    /// exact chrome dimensions are used instead of the compile-time constants.
    pub fn adjust_editor_bounds(
        left_dp: i32,
        top_dp: i32,
        right_dp: i32,
        bottom_dp: i32,
    ) -> Result<(), EditorError> {
        let sender = WEBVIEW_SENDER.get().ok_or(EditorError::WebViewNotRegistered)?;

        let guard = sender.vm
            .attach_current_thread()
            .map_err(|e| EditorError::WebViewEvalFailed(e.to_string()))?;
        let mut env = unsafe { guard.unsafe_clone() };

        env.call_static_method(
            "dev/androidide/IDEActivity",
            "adjustEditorBounds",
            "(IIII)V",
            &[
                JValue::Int(left_dp),
                JValue::Int(top_dp),
                JValue::Int(right_dp),
                JValue::Int(bottom_dp),
            ],
        )
        .map_err(|e| EditorError::WebViewEvalFailed(format!("adjustEditorBounds: {e}")))?;

        Ok(())
    }
}

// ---------------------------------------------------------------------------
// Platform-agnostic send helpers
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
