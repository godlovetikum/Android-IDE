// android-ide/android/java/dev/androidide/ui/components/EditorPane.kt
//
// Monaco editor WebView + keyboard toolbar + symbol shortcut bar + optional live-preview WebView.
//
// Layout: Column
//   Content area (editor ± preview) — weight(1f)
//   SymbolBar    (optional, above keyboard toolbar) — horizontally scrollable symbol chips
//   KeyboardToolbar (optional, 2-page pager of icon buttons, NO horizontal scroll)
//
// Keyboard toolbar pages:
//   Page 1: Indent, ↑, ↓, ←, →, Undo, Redo       (7 actions)
//   Page 2: Cut, Copy, Paste*, Select All, Show KB, Hide KB  (6 actions)
//
// *Paste reads from the Android ClipboardManager via Kotlin (onPasteFromClipboard)
//  instead of the WebView clipboard API, which is slow and permission-gated.
//
// Preview crash safety:
//   WebViewClient.onRenderProcessGone returns true to prevent app termination.
//   Preview content is loaded via loadDataWithBaseURL (no URL-length limit issues
//   that affect data: scheme URLs with large base64 payloads).
//
// Editor focus:
//   isFocusable / isFocusableInTouchMode are set on the WebView so tapping the
//   editor requests native focus, making the soft keyboard appear immediately.

package dev.androidide.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onPasteFromClipboard: () -> Unit,
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
            // isFocusable / isFocusableInTouchMode: required so that tapping the
            // editor requests native focus and the soft keyboard appears immediately.
            isFocusable              = true
            isFocusableInTouchMode   = true
            isClickable              = true
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
            setOnTouchListener { v, _ ->
                v.requestFocus()
                false   // do not consume the event — let WebView handle it
            }
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
                    // Non-fatal render errors are handled gracefully via the crash guard below.
                }

                // API 26+ — returning true prevents app termination on render crash (BUG-006).
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

    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.screenWidthDp > configuration.screenHeightDp

    // ── Layout ──────────────────────────────────────────────────────────────
    Column(modifier = modifier.background(colors.background)) {

        when {
            !isPreviewVisible -> {
                AndroidView(
                    factory  = { editorWebView },
                    update   = { },
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
            SymbolBar(symbols = customSymbols, onInsertSymbol = onInsertText)
        }

        // Keyboard toolbar — 2-page pager, no horizontal scrolling
        if (activeTab != null && showKeyboardToolbar) {
            HorizontalDivider(thickness = 1.dp, color = colors.separator)
            KeyboardToolbar(
                onInsertText         = onInsertText,
                onExecuteCommand     = onExecuteCommand,
                onPasteFromClipboard = onPasteFromClipboard,
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

// ── Keyboard toolbar — 2-page HorizontalPager (no horizontal scroll) ──────────

private data class KeyboardAction(
    val icon: ImageVector,
    val label: String,
    val commandId: String?,         // null for special Kotlin-side actions
    val isPaste: Boolean = false,   // triggers onPasteFromClipboard instead of executeCommand
)

// Page 1: navigation + undo/redo (7 actions)
private val TOOLBAR_PAGE_1 = listOf(
    KeyboardAction(Icons.Default.FormatIndentIncrease, "Indent",       "editor.action.indentLines"),
    KeyboardAction(Icons.Default.KeyboardArrowUp,      "Cursor Up",    "cursorUp"),
    KeyboardAction(Icons.Default.KeyboardArrowDown,    "Cursor Down",  "cursorDown"),
    KeyboardAction(Icons.Default.KeyboardArrowLeft,    "Cursor Left",  "cursorLeft"),
    KeyboardAction(Icons.Default.KeyboardArrowRight,   "Cursor Right", "cursorRight"),
    KeyboardAction(Icons.Default.Undo,                 "Undo",         "undo"),
    KeyboardAction(Icons.Default.Redo,                 "Redo",         "redo"),
)

// Page 2: clipboard + selection + keyboard (6 actions)
private val TOOLBAR_PAGE_2 = listOf(
    KeyboardAction(Icons.Default.ContentCut,    "Cut",          "editor.action.clipboardCutAction"),
    KeyboardAction(Icons.Default.ContentCopy,   "Copy",         "editor.action.clipboardCopyAction"),
    KeyboardAction(Icons.Default.ContentPaste,  "Paste",        null, isPaste = true),
    KeyboardAction(Icons.Default.SelectAll,     "Select All",   "editor.action.selectAll"),
    KeyboardAction(Icons.Default.Keyboard,      "Show Keyboard","focusEditor"),
    KeyboardAction(Icons.Default.KeyboardHide,  "Hide Keyboard","blurEditor"),
)

private val TOOLBAR_PAGES = listOf(TOOLBAR_PAGE_1, TOOLBAR_PAGE_2)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardToolbar(
    onInsertText: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
) {
    val colors     = LocalIdeColors.current
    val pagerState = rememberPagerState(pageCount = { TOOLBAR_PAGES.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        HorizontalPager(state = pagerState) { pageIndex ->
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TOOLBAR_PAGES[pageIndex].forEach { action ->
                    ToolbarIconButton(
                        icon             = action.icon,
                        label            = action.label,
                        isPaste          = action.isPaste,
                        commandId        = action.commandId,
                        onExecuteCommand = onExecuteCommand,
                        onPaste          = onPasteFromClipboard,
                    )
                }
            }
        }
        // Page indicator dots
        Row(
            modifier              = Modifier.fillMaxWidth().height(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            repeat(TOOLBAR_PAGES.size) { idx ->
                val selected = pagerState.currentPage == idx
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 6.dp else 4.dp)
                        .background(
                            color = if (selected) colors.accent else colors.textDisabled,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    label: String,
    commandId: String?,
    isPaste: Boolean,
    onExecuteCommand: (String) -> Unit,
    onPaste: () -> Unit,
) {
    val colors       = LocalIdeColors.current
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
            onClick  = {
                when {
                    isPaste    -> onPaste()
                    commandId != null -> onExecuteCommand(commandId)
                }
            },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = colors.textSecondary,
                modifier           = Modifier.size(24.dp),
            )
        }
    }
}
