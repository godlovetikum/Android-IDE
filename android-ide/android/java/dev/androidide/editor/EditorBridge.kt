// android-ide/android/java/dev/androidide/editor/EditorBridge.kt
//
// JavaScript ↔ Kotlin bridge for the Monaco editor WebView.
//
// Migration note (2026-06-12):
//   Replaces EditorBridge.java.
//   Key difference: removed "private static native void nativeOnEditorMessage(String)"
//   JNI call to Rust. Inbound messages now dispatch to a Kotlin callback directly.
//   Outbound JS format is identical — monaco-init.js is unchanged.
//
// Setup in EditorPane.kt:
//   val bridge = remember { EditorBridge() }
//   bridge.messageListener = { msg -> viewModel.onEditorMessage(msg) }
//   webView.addJavascriptInterface(bridge, "AndroidBridge")
//   webView.loadUrl("file:///android_asset/editor/index.html")
//
// Thread safety:
//   onMessage() is called on a background thread by the WebView JS engine.
//   The messageListener callback must not touch UI directly — post to the main
//   thread via Compose's side-effect APIs or Handler.main if needed.
//   send() always posts to the WebView's UI thread via webView.post().

package dev.androidide.editor

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.util.Log

class EditorBridge {

    companion object {
        private const val TAG = "EditorBridge"
    }

    /**
     * Callback invoked when Monaco sends a message to the native layer.
     * Called on a BACKGROUND thread — do NOT access UI here.
     *
     * Register this in the composable that owns the WebView:
     *   bridge.messageListener = viewModel::onEditorMessage
     */
    var messageListener: ((EditorInbound) -> Unit)? = null

    // ── Inbound (Monaco → Kotlin) ──────────────────────────────────────────

    /**
     * Called by Monaco via window.AndroidBridge.onMessage(jsonString).
     *
     * This method runs on a background thread managed by the WebView.
     * Do NOT touch UI elements here.
     */
    @JavascriptInterface
    fun onMessage(json: String) {
        Log.d(TAG, "onMessage: $json")
        val message = EditorInbound.fromJson(json) ?: run {
            Log.w(TAG, "Unknown message type in: $json")
            return
        }
        messageListener?.invoke(message)
    }

    // ── Outbound (Kotlin → Monaco) ─────────────────────────────────────────

    /**
     * Send a message to Monaco by evaluating a JS expression in [webView].
     *
     * This method is safe to call from any thread — it posts the evaluation
     * to the WebView's UI thread internally.
     */
    fun send(webView: WebView, message: EditorOutbound) {
        val js = message.toJs()
        Log.d(TAG, "send: $js")
        webView.post { webView.evaluateJavascript(js, null) }
    }
}
