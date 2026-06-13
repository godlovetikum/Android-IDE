// android-ide/android/java/dev/androidide/ui/components/EditorPane.kt
//
// Monaco editor WebView + keyboard toolbar + optional live-preview WebView.
//
// Layout: Column
//   Content area (editor ± preview) — weight(1f)
//     • No preview:   AndroidView(editor) fills the area.
//     • Landscape:    Row — editor | separator | preview (side by side).
//     • Portrait:     Column — preview / editor or editor / preview depending
//                     on editorSettings.previewLayout.
//   KeyboardToolbar (fixed 40dp, only shown when a tab is active)
//
// Architecture decisions:
//   Both WebViews are wrapped in remember{} so they survive recompositions.
//   The preview WebView is always remembered (not conditional) so it
//   persists correctly through orientation changes.
//   The AndroidView update lambda is a no-op for the editor — all state
//   changes are driven via evaluateJavascript() by EditorBridge.
//
//   editorCommands: SharedFlow emitted by IdeViewModel and collected here.
//   Each command is forwarded to Monaco so the ViewModel never holds a
//   WebView reference.
//
// WebView settings:
//   useWideViewPort / loadWithOverviewMode are intentionally false — setting
//   them true causes Monaco to render at "viewport" width (4000 px on some
//   devices) instead of the container width, making all text tiny.

package dev.androidide.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.androidide.data.model.PreviewLayout
import dev.androidide.editor.EditorBridge
import dev.androidide.editor.EditorInbound
import dev.androidide.editor.EditorOutbound
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.model.EditorTab
import kotlinx.coroutines.flow.SharedFlow

// SetJavaScriptEnabled: Monaco requires JS.
// JavascriptInterface: EditorBridge.onMessage IS annotated @JavascriptInterface; lint sees T not
//   EditorBridge through the generic remember<T>{}, producing a false-positive.
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "WebViewClientOnReceivedSslError")
@Composable
fun EditorPane(
    activeTab: EditorTab?,
    isEditorReady: Boolean,
    isPreviewVisible: Boolean,
    previewUrl: String,
    previewLayout: PreviewLayout,
    editorCommands: SharedFlow<EditorOutbound>,
    onEditorReady: () -> Unit,
    onEditorMessage: (EditorInbound) -> Unit,
    onInsertText: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors  = LocalIdeColors.current

    // ── EditorBridge — survives recompositions ─────────────────────────────
    val editorBridge: EditorBridge = remember { EditorBridge() }

    // onEditorMessage already dispatches Ready → onEditorReady() in IdeViewModel.
    // Assigning it directly avoids a redundant second call.
    DisposableEffect(onEditorMessage) {
        editorBridge.messageListener = onEditorMessage
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
                // Do NOT set useWideViewPort/loadWithOverviewMode to true: the WebView
                // would treat the layout width as "desktop wide" (~4000 px) and Monaco
                // would render at tiny scale to fit it.
                useWideViewPort                  = false
                loadWithOverviewMode             = false
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

    // ── Preview WebView — always remembered for orientation stability ───────
    val previewWebView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient   = WebViewClient()
        }
    }

    // ── Forward ViewModel editor commands to Monaco ─────────────────────────
    LaunchedEffect(Unit) {
        editorCommands.collect { command ->
            editorBridge.send(editorWebView, command)
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

    // ── Reload preview when previewUrl changes ──────────────────────────────
    LaunchedEffect(previewUrl, isPreviewVisible) {
        if (isPreviewVisible && previewUrl.isNotEmpty()) {
            previewWebView.post { previewWebView.loadUrl(previewUrl) }
        }
    }

    // ── Orientation detection for responsive preview layout ─────────────────
    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.screenWidthDp > configuration.screenHeightDp

    // ── Layout ──────────────────────────────────────────────────────────────
    Column(modifier = modifier.background(colors.background)) {

        when {
            !isPreviewVisible -> {
                // No preview — editor fills entire content area
                AndroidView(
                    factory  = { editorWebView },
                    update   = { /* no-op — all updates via evaluateJavascript */ },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            isLandscape -> {
                // Landscape: editor left, preview right — side by side
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory  = { editorWebView },
                        update   = { },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(colors.separator),
                    )
                    AndroidView(
                        factory  = { previewWebView },
                        update   = { },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }

            else -> {
                // Portrait: stacked order controlled by previewLayout setting
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (previewLayout == PreviewLayout.EDITOR_ABOVE) {
                        AndroidView(
                            factory  = { editorWebView },
                            update   = { },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier
                                .height(1.dp)
                                .fillMaxWidth()
                                .background(colors.separator),
                        )
                        AndroidView(
                            factory  = { previewWebView },
                            update   = { },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    } else {
                        // PREVIEW_ABOVE (default)
                        AndroidView(
                            factory  = { previewWebView },
                            update   = { },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier
                                .height(1.dp)
                                .fillMaxWidth()
                                .background(colors.separator),
                        )
                        AndroidView(
                            factory  = { editorWebView },
                            update   = { },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // Keyboard toolbar — only shown when a tab is active
        if (activeTab != null) {
            HorizontalDivider(thickness = 1.dp, color = colors.separator)
            KeyboardToolbar(
                onInsertText     = onInsertText,
                onExecuteCommand = onExecuteCommand,
            )
        }
    }
}

// ── Keyboard toolbar ──────────────────────────────────────────────────────────

private sealed class ToolbarAction {
    data class Insert(val label: String, val text: String) : ToolbarAction()
    data class Command(val label: String, val commandId: String) : ToolbarAction()
    object Separator : ToolbarAction()
}

private val TOOLBAR_ACTIONS = listOf(
    // Cursor navigation
    ToolbarAction.Command("\u2190",  "cursorLeft"),                  // ←
    ToolbarAction.Command("\u2192",  "cursorRight"),                 // →
    ToolbarAction.Command("\u2191",  "cursorUp"),                    // ↑
    ToolbarAction.Command("\u2193",  "cursorDown"),                  // ↓
    ToolbarAction.Command("Home",    "cursorHome"),
    ToolbarAction.Command("End",     "cursorEnd"),
    ToolbarAction.Separator,
    // Indentation
    ToolbarAction.Command("\u21e5",  "editor.action.indentLines"),   // ⇥ indent
    ToolbarAction.Command("\u21e4",  "editor.action.outdentLines"),  // ⇤ outdent
    ToolbarAction.Separator,
    // Delete
    ToolbarAction.Command("\u232b",  "deleteLeft"),                  // ⌫
    ToolbarAction.Command("\u2326",  "deleteRight"),                 // ⌦
    ToolbarAction.Separator,
    // Undo / Redo
    ToolbarAction.Command("\u21a9",  "undo"),                        // ↩
    ToolbarAction.Command("\u21aa",  "redo"),                        // ↪
    ToolbarAction.Separator,
    // Clipboard
    ToolbarAction.Command("Cut",     "editor.action.clipboardCutAction"),
    ToolbarAction.Command("Copy",    "editor.action.clipboardCopyAction"),
    ToolbarAction.Command("Paste",   "editor.action.clipboardPasteAction"),
    ToolbarAction.Command("All",     "editor.action.selectAll"),
    ToolbarAction.Separator,
    // Keyboard control
    ToolbarAction.Command("\u2328",  "focusEditor"),                 // ⌨ open keyboard
    ToolbarAction.Command("\u2328\u2715", "blurEditor"),             // ⌨✕ close keyboard
    ToolbarAction.Separator,
    // Common symbols
    ToolbarAction.Insert("(",  "("),
    ToolbarAction.Insert(")",  ")"),
    ToolbarAction.Insert("{",  "{"),
    ToolbarAction.Insert("}",  "}"),
    ToolbarAction.Insert("[",  "["),
    ToolbarAction.Insert("]",  "]"),
    ToolbarAction.Insert(";",  ";"),
    ToolbarAction.Insert(":",  ":"),
    ToolbarAction.Insert(",",  ","),
    ToolbarAction.Insert(".",  "."),
    ToolbarAction.Insert("\"", "\""),
    ToolbarAction.Insert("'",  "'"),
    ToolbarAction.Insert("=",  "="),
    ToolbarAction.Insert("+",  "+"),
    ToolbarAction.Insert("-",  "-"),
    ToolbarAction.Insert("_",  "_"),
    ToolbarAction.Insert("@",  "@"),
    ToolbarAction.Insert("!",  "!"),
    ToolbarAction.Insert("/",  "/"),
    ToolbarAction.Insert("\\", "\\"),
    ToolbarAction.Insert("?",  "?"),
    ToolbarAction.Insert("|",  "|"),
    ToolbarAction.Insert("&",  "&"),
    ToolbarAction.Insert("<",  "<"),
    ToolbarAction.Insert(">",  ">"),
)

@Composable
private fun KeyboardToolbar(
    onInsertText: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
) {
    val colors = LocalIdeColors.current
    Row(
        modifier = Modifier
            .height(40.dp)
            .fillMaxWidth()
            .background(colors.surface)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(4.dp))
        TOOLBAR_ACTIONS.forEach { action ->
            when (action) {
                is ToolbarAction.Separator -> {
                    VerticalDivider(
                        modifier  = Modifier.height(20.dp).padding(horizontal = 2.dp),
                        thickness = 1.dp,
                        color     = colors.separator,
                    )
                }
                is ToolbarAction.Insert -> {
                    ToolbarButton(label = action.label, onClick = { onInsertText(action.text) })
                }
                is ToolbarAction.Command -> {
                    ToolbarButton(label = action.label, onClick = { onExecuteCommand(action.commandId) })
                }
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun ToolbarButton(label: String, onClick: () -> Unit) {
    val colors = LocalIdeColors.current
    TextButton(
        onClick  = onClick,
        modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}
