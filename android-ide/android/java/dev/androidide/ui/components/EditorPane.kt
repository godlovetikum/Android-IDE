// android-ide/android/java/dev/androidide/ui/components/EditorPane.kt
//
// Monaco editor WebView + optional live-preview WebView side-by-side.
//
// Architecture decision MD-003:
//   Both WebViews are wrapped in remember {} so they survive recompositions.
//   The AndroidView update lambda is intentionally a no-op for the editor —
//   all editor state changes are driven via evaluateJavascript() by EditorBridge.
//
// Bridge protocol (MD-004):
//   Inbound  from Monaco: window.AndroidBridge.onMessage(json)
//   Outbound to Monaco:   window.androidIDE.receiveMessage(obj)
//
// URI encoding fix:
//   monaco-init.js encodes the SAF content:// URI as encodeURIComponent(path)
//   inside the Monaco model URI (androidide:///files/<encoded>) so that
//   monaco.Uri.parse never receives a raw content:// string, which would
//   produce a malformed URI and abort the loadFile call silently.

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
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.model.EditorTab

// SetJavaScriptEnabled: Monaco requires JS — intentional for a code editor.
// JavascriptInterface: EditorBridge.onMessage IS annotated with @JavascriptInterface.
//   Lint reports this as an error because it cannot resolve the concrete type of
//   `editorBridge` through the generic remember<T>{} return — it sees T, not EditorBridge.
//   The annotation IS present; this suppression silences the false-positive.
// WebViewClientOnReceivedSslError: default behaviour is acceptable for a dev tool.
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
    val colors  = LocalIdeColors.current

    // ── EditorBridge — survives recompositions ─────────────────────────────
    val editorBridge: EditorBridge = remember { EditorBridge() }

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
            isScrollbarFadingEnabled     = false
            isVerticalScrollBarEnabled   = false
            isHorizontalScrollBarEnabled = false
            addJavascriptInterface(editorBridge, "AndroidBridge")
            webChromeClient = WebChromeClient()
            webViewClient   = WebViewClient()
            loadUrl("file:///android_asset/editor/index.html")
        }
    }

    // ── Load active file into Monaco when tab or readiness changes ──────────
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
    Row(modifier = modifier.background(colors.background)) {
        AndroidView(
            factory = { editorWebView },
            update  = { /* no-op — all updates via evaluateJavascript */ },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )

        if (isPreviewVisible) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.separator),
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
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}
