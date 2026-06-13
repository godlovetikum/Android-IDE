// android-ide/android/java/dev/androidide/ui/components/EditorPane.kt
//
// Monaco editor WebView + keyboard toolbar + symbol shortcut bar + optional live-preview WebView.
//
// Layout: Column
//   Content area (editor ± preview) — weight(1f)
//   SymbolBar    (optional, above keyboard toolbar)
//   KeyboardToolbar (optional, 13 fixed-action icon buttons)
//
// Preview crash safety:
//   WebViewClient.onRenderProcessGone returns true to prevent app termination.
//   Preview content is loaded via loadDataWithBaseURL (no URL-length limit issues
//   that affect data: scheme URLs with large base64 payloads).
//
// Architecture decisions:
//   Both WebViews are wrapped in remember{} so they survive recompositions.
//   editor commands are driven via evaluateJavascript() by EditorBridge.

package dev.androidide.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.androidide.data.model.EditorSettings
import dev.androidide.data.model.PreviewLayout
import dev.androidide.editor.EditorBridge
import dev.androidide.editor.EditorInbound
import dev.androidide.editor.EditorOutbound
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.model.EditorTab
import kotlinx.coroutines.flow.SharedFlow

// SetJavaScriptEnabled: Monaco requires JS.
// JavascriptInterface: EditorBridge.onMessage IS annotated @JavascriptInterface; lint produces a
//   false-positive through the generic remember<T>{}.
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "WebViewClientOnReceivedSslError")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorPane(
    activeTab: EditorTab?,
    isEditorReady: Boolean,
    isPreviewVisible: Boolean,
    previewHtmlContent: String,
    previewLayout: PreviewLayout,
    editorCommands: SharedFlow<EditorOutbound>,
    onEditorReady: () -> Unit,
    onEditorMessage: (EditorInbound) -> Unit,
    onInsertText: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    showKeyboardToolbar: Boolean = true,
    showSymbolBar: Boolean = true,
    customSymbols: List<String> = EditorSettings.DEFAULT_SYMBOLS,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors  = LocalIdeColors.current

    // ── EditorBridge — survives recompositions ─────────────────────────────
    val editorBridge: EditorBridge = remember { EditorBridge() }

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
    var previewCrashed by remember { mutableStateOf(false) }
    val previewWebView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    // Errors in the render thread are non-fatal here; the WebView still displays.
                }

                // API 26+ — returns true to tell Android we handled the crash gracefully.
                // Without this override, a render process crash terminates the entire app.
                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail,
                ): Boolean {
                    previewCrashed = true
                    return true
                }
            }
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

    // ── Load preview content when it changes ───────────────────────────────
    // Uses loadDataWithBaseURL instead of loadUrl("data:...") to avoid the
    // URL-length limit that causes crashes with large base64-encoded pages.
    LaunchedEffect(previewHtmlContent, isPreviewVisible) {
        if (isPreviewVisible && previewHtmlContent.isNotEmpty()) {
            previewCrashed = false
            previewWebView.post {
                runCatching {
                    previewWebView.loadDataWithBaseURL(
                        "about:blank",
                        previewHtmlContent,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            }
        }
    }

    // ── Orientation detection for responsive preview layout ─────────────────
    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.screenWidthDp > configuration.screenHeightDp

    // ── Layout ──────────────────────────────────────────────────────────────
    Column(modifier = modifier.background(colors.background)) {

        when {
            !isPreviewVisible -> {
                AndroidView(
                    factory  = { editorWebView },
                    update   = { /* no-op — all updates via evaluateJavascript */ },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            isLandscape -> {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory  = { editorWebView },
                        update   = { },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(colors.separator))
                    if (previewCrashed) {
                        PreviewErrorBox(modifier = Modifier.weight(1f).fillMaxHeight())
                    } else {
                        AndroidView(
                            factory  = { previewWebView },
                            update   = { },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }

            else -> {
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (previewLayout == PreviewLayout.EDITOR_ABOVE) {
                        AndroidView(
                            factory  = { editorWebView },
                            update   = { },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.separator))
                        if (previewCrashed) {
                            PreviewErrorBox(modifier = Modifier.weight(1f).fillMaxWidth())
                        } else {
                            AndroidView(
                                factory  = { previewWebView },
                                update   = { },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                    } else {
                        if (previewCrashed) {
                            PreviewErrorBox(modifier = Modifier.weight(1f).fillMaxWidth())
                        } else {
                            AndroidView(
                                factory  = { previewWebView },
                                update   = { },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                        Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(colors.separator))
                        AndroidView(
                            factory  = { editorWebView },
                            update   = { },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // Symbol shortcut bar — shown above keyboard toolbar when a tab is active
        if (activeTab != null && showSymbolBar) {
            HorizontalDivider(thickness = 1.dp, color = colors.separator)
            SymbolBar(
                symbols         = customSymbols,
                onInsertSymbol  = onInsertText,
            )
        }

        // Keyboard toolbar — 13 fixed-action icon buttons
        if (activeTab != null && showKeyboardToolbar) {
            HorizontalDivider(thickness = 1.dp, color = colors.separator)
            KeyboardToolbar(
                onInsertText     = onInsertText,
                onExecuteCommand = onExecuteCommand,
            )
        }
    }
}

// ── Preview error placeholder ─────────────────────────────────────────────────

@Composable
private fun PreviewErrorBox(modifier: Modifier = Modifier) {
    val colors = LocalIdeColors.current
    Box(
        modifier         = modifier.background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = "Preview unavailable.\nThe render process terminated.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDisabled,
        )
    }
}

// ── Symbol shortcut bar ───────────────────────────────────────────────────────

@Composable
private fun SymbolBar(
    symbols: List<String>,
    onInsertSymbol: (String) -> Unit,
) {
    val colors = LocalIdeColors.current
    Row(
        modifier = Modifier
            .height(36.dp)
            .fillMaxWidth()
            .background(colors.surface)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(4.dp))
        symbols.forEach { symbol ->
            TextButton(
                onClick        = { onInsertSymbol(symbol) },
                modifier       = Modifier.height(32.dp).widthIn(min = 32.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(
                    text  = symbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

// ── Keyboard toolbar ──────────────────────────────────────────────────────────

private data class KeyboardAction(
    val icon: ImageVector,
    val label: String,
    val commandId: String,
)

private val KEYBOARD_TOOLBAR_ACTIONS = listOf(
    KeyboardAction(Icons.Default.FormatIndentIncrease, "Indent",      "editor.action.indentLines"),
    KeyboardAction(Icons.Default.KeyboardArrowUp,      "Cursor Up",   "cursorUp"),
    KeyboardAction(Icons.Default.KeyboardArrowDown,    "Cursor Down", "cursorDown"),
    KeyboardAction(Icons.Default.KeyboardArrowLeft,    "Cursor Left", "cursorLeft"),
    KeyboardAction(Icons.Default.KeyboardArrowRight,   "Cursor Right","cursorRight"),
    KeyboardAction(Icons.Default.Undo,                 "Undo",        "undo"),
    KeyboardAction(Icons.Default.Redo,                 "Redo",        "redo"),
    KeyboardAction(Icons.Default.ContentCut,           "Cut",         "editor.action.clipboardCutAction"),
    KeyboardAction(Icons.Default.ContentCopy,          "Copy",        "editor.action.clipboardCopyAction"),
    KeyboardAction(Icons.Default.ContentPaste,         "Paste",       "editor.action.clipboardPasteAction"),
    KeyboardAction(Icons.Default.SelectAll,            "Select All",  "editor.action.selectAll"),
    KeyboardAction(Icons.Default.Keyboard,             "Show Keyboard","focusEditor"),
    KeyboardAction(Icons.Default.KeyboardHide,         "Hide Keyboard","blurEditor"),
)

@OptIn(ExperimentalMaterial3Api::class)
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
        KEYBOARD_TOOLBAR_ACTIONS.forEach { action ->
            ToolbarIconButton(
                icon             = action.icon,
                label            = action.label,
                onExecuteCommand = onExecuteCommand,
                commandId        = action.commandId,
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    label: String,
    commandId: String,
    onExecuteCommand: (String) -> Unit,
) {
    val colors = LocalIdeColors.current
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        },
        state = tooltipState,
    ) {
        IconButton(
            onClick  = { onExecuteCommand(commandId) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = colors.textSecondary,
                modifier           = Modifier.size(18.dp),
            )
        }
    }
}
