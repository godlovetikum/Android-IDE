// android-ide/android/java/dev/androidide/EditorBridge.java
//
// JavaScript interface for bidirectional communication between the Monaco
// editor WebView and the Rust layer.
//
// Setup (in Activity or Fragment):
//   WebView webView = findViewById(R.id.editor_webview);
//   webView.getSettings().setJavaScriptEnabled(true);
//   webView.addJavascriptInterface(new EditorBridge(), "AndroidBridge");
//   webView.loadUrl("file:///android_asset/editor/index.html");
//
// The "AndroidBridge" name matches what monaco-init.js checks:
//   window.AndroidBridge.onMessage(jsonString)
//
// To send a message FROM Rust TO Monaco, call:
//   EditorBridge.evaluateScript(webView, jsString);
// which executes on the UI thread via webView.post().

package dev.androidide;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.util.Log;

public final class EditorBridge {

    private static final String TAG = "EditorBridge";

    /**
     * Called by Monaco when it sends a message to the native layer.
     *
     * IMPORTANT: This method is called on a BACKGROUND thread by the WebView.
     * Do NOT touch UI elements here. Forward the message to Rust via JNI,
     * which will post back to the UI thread as needed.
     *
     * @param json  JSON string matching one of the EditorInbound variants
     *              defined in modules/editor/src/bridge.rs
     */
    @JavascriptInterface
    public void onMessage(String json) {
        Log.d(TAG, "onMessage: " + json);
        nativeOnEditorMessage(json);
    }

    // -----------------------------------------------------------------------
    // Sending messages to Monaco
    // -----------------------------------------------------------------------

    /**
     * Execute a JavaScript string in the given WebView.
     *
     * Must be called from the main (UI) thread; if you are on a background
     * thread, use webView.post(() -> evaluateScript(webView, js)) instead.
     *
     * @param webView  The editor WebView instance
     * @param js       JavaScript to evaluate — typically from bridge::outbound_to_js()
     */
    public static void evaluateScript(WebView webView, String js) {
        webView.evaluateJavascript(js, null);
    }

    /**
     * Post a JavaScript evaluation to the UI thread safely from any thread.
     *
     * @param webView  The editor WebView instance
     * @param js       JavaScript string to evaluate
     */
    public static void evaluateScriptAsync(WebView webView, String js) {
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    // -----------------------------------------------------------------------
    // JNI
    // -----------------------------------------------------------------------

    /**
     * Passes a JSON message from Monaco to the Rust layer.
     * Implemented in modules/editor/src/webview.rs via JNI.
     */
    private static native void nativeOnEditorMessage(String json);
}
