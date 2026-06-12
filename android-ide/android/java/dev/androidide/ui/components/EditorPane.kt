// android-ide/android/java/dev/androidide/ui/components/EditorPane.kt
//
// Monaco editor WebView + optional live-preview WebView side-by-side.
//
// Migration note (2026-06-12):
//   Replaces the WebView placeholder in ui/main.slint and the IDEActivity
//   overlay Java code that positioned the editor/preview WebViews.
//   The Monaco WebView is now a first-class Compose AndroidView.
//
// Architecture decision MD-003:
//   Both WebViews are wrapped in remember {} so they survive recompositions.
//   The AndroidView update lambda is intentionally a no-op for the editor —
//   all editor state changes are driven via evaluateJavascript() by EditorBridge.
//
// Bridge protocol (MD-004):
//   Inbound  from Monaco: window.AndroidBridge.onMessage(json)
//   Outbound to Monaco:   window.androidIDE.receiveMessage(obj)
//   The JS files in android/assets/editor/ are NOT modified.

package dev.androidide.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.androidide.editor.EditorBridge
import dev.androidide.editor.EditorInbound
import dev.androidide.editor.EditorOutbound
import dev.androidide.ui.theme.IdeBackground
import dev.androidide.ui.theme.IdeSeparator
import dev.androidide.viewmodel.model.EditorTab

// SetJavaScriptEnabled: Monaco requires JS — this is intentional for a code editor.
// WebViewClientOnReceivedSslError: default WebViewClient behaviour is appropriate
//   for development; a production build should override onReceivedSslError.
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "WebViewClientOnReceivedSslError")
@Composable
fun EditorPane(
    activeTab: EditorTab?,
    isEditorReady: Boolean,
    isPreviewVisible: Boolean,
    previewUrl: String,
    onEditorReady: () -> Unit,
    onEditorMessage: (EditorInbound) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // ── EditorBridge — survives recompositions ─────────────────────────────
    // Explicit type annotation required: lint resolves addJavascriptInterface(obj, name)
    // against the declared type of obj. Without it, lint sees the generic return type T
    // of remember<T>{} and cannot find @JavascriptInterface on T.
    val editorBridge: EditorBridge = remember { EditorBridge() }

    // Update the message listener when the callback reference changes.
    DisposableEffect(onEditorMessage) {
        editorBridge.messageListener = { msg ->
            if (msg is EditorInbound.Ready) onEditorReady()
            onEditorMessage(msg)
        }
        onDispose { editorBridge.messageListener = null }
    }

    // ── Monaco WebView — created once, never recreated ─────────────────────
    val editorWebView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true
                allowFileAccessFromFileURLs      = true
                allowUniversalAccessFromFileURLs = true
                useWideViewPort                  = true
                loadWithOverviewMode             = true
                setSupportZoom(false)
            }
            isScrollbarFadingEnabled    = false
            isVerticalScrollBarEnabled  = false
            isHorizontalScrollBarEnabled = false
            addJavascriptInterface(editorBridge, "AndroidBridge")
            webChromeClient = WebChromeClient()
            webViewClient   = WebViewClient()
            // Load the Monaco bundle from APK assets.
            loadUrl("file:///android_asset/editor/index.html")
        }
    }

    // ── Load active file into Monaco when tab or readiness changes ──────────
    // Fires when: a new tab becomes active, or the editor signals ready.
    LaunchedEffect(activeTab?.id, activeTab?.content, isEditorReady) {
        if (isEditorReady && activeTab != null && activeTab.content != null) {
            editorBridge.send(
                editorWebView,
                EditorOutbound.LoadFile(
                    path     = activeTab.documentUri,
                    content  = activeTab.content,
                    language = activeTab.language,
                ),
            )
        }
    }

    // ── Layout ──────────────────────────────────────────────────────────────
    Row(modifier = modifier.background(IdeBackground)) {
        // Monaco editor
        AndroidView(
            factory = { editorWebView },
            update  = { /* no-op — all updates via evaluateJavascript */ },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )

        // Live preview panel (conditionally visible)
        if (isPreviewVisible) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(IdeSeparator),
            )

            val previewWebView = remember {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    webViewClient   = WebViewClient()
                }
            }

            LaunchedEffect(previewUrl) {
                previewWebView.post { previewWebView.loadUrl(previewUrl) }
            }

            AndroidView(
                factory  = { previewWebView },
                update   = { /* no-op */ },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}
