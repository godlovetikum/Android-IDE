// android-ide/android/java/dev/android/ide/ui/components/EditorPane.kt
//
// Monaco editor WebView + keyboard toolbar + symbol shortcut bar.
//
// Layout: Column
//   Content area (editor) — weight(1f)
//   SymbolBar    (optional, above keyboard toolbar) — horizontally scrollable symbol chips
//   KeyboardToolbar (optional, fixed five-item pages of icon buttons, NO horizontal scroll)
//
// Keyboard toolbar pages:
//
// *Paste reads from the Android ClipboardManager via Kotlin (onPasteFromClipboard)
//  instead of the WebView clipboard API, which is slow and permission-gated.
//
// Crash safety:
//
// Editor focus:
//   isFocusable / isFocusableInTouchMode are set on the WebView so tapping the
//   editor requests native focus, making the soft keyboard appear immediately.

package dev.android.ide.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import dev.android.ide.data.model.EditorSettings
import dev.android.ide.editor.EditorBridge
import dev.android.ide.editor.EditorInbound
import dev.android.ide.editor.EditorOutbound
import dev.android.ide.ui.theme.LocalIdeColors
import dev.android.ide.viewmodel.model.EditorTab
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// SetJavaScriptEnabled: Monaco requires JS.
// JavascriptInterface: EditorBridge.onMessage IS annotated @JavascriptInterface; lint produces a
//   false-positive through the generic remember<T>{}.
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "WebViewClientOnReceivedSslError")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorPane(
    activeTab: EditorTab?,
    activeTabContent: String? = null,
    isEditorReady: Boolean,
    editorBindRevision: Long = 0L,
    editorCommands: SharedFlow<EditorOutbound>,
    onEditorRendererGone: () -> Unit = {},
    onEditorMessage: (EditorInbound) -> Unit,
    onInsertText: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    hasEditorSelection: Boolean = false,
    showKeyboardToolbar: Boolean = true,
    showSymbolBar: Boolean = true,
    keyboardToolbarOrder: List<String> = EditorSettings.DEFAULT_KEYBOARD_TOOLBAR_ORDER,
    tabCursorPositions: Map<String, Pair<Int, Int>> = emptyMap(),
    tabScrollPositions: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors  = LocalIdeColors.current
    val rendererGone by rememberUpdatedState(onEditorRendererGone)

    // ── EditorBridge — survives recompositions ─────────────────────────────
    val editorBridge: EditorBridge = remember { EditorBridge() }

    DisposableEffect(onEditorMessage) {
        editorBridge.messageListener = onEditorMessage
        onDispose { editorBridge.messageListener = null }
    }

    // ── Editor crash state ─────────────────────────────────────────────────
    // True when the Monaco render process terminates unexpectedly.
    // onRenderProcessGone sets this flag (returning true prevents app termination).
    // The EditorCrashedBox shown when true has a Reload button that calls
    // loadUrl() to start a fresh renderer — the WebView object itself stays valid.
    var editorCrashed by remember { mutableStateOf(false) }

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
                allowFileAccessFromFileURLs      = false
                allowUniversalAccessFromFileURLs = false
                cacheMode                         = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                useWideViewPort                  = false
                loadWithOverviewMode             = false
                setSupportZoom(false)
            }
            isScrollbarFadingEnabled     = false
            isVerticalScrollBarEnabled   = false
            isHorizontalScrollBarEnabled = false
            addJavascriptInterface(editorBridge, "AndroidBridge")
            webChromeClient = WebChromeClient()
            webViewClient   = object : WebViewClient() {
                // API 26+ — return true to prevent app termination when the Monaco
                // render process crashes (e.g. heavy syntax highlighting, large file, OOM).
                // After returning true the WebView object is still valid; loadUrl() starts
                // a new renderer process, restoring the editor without killing the app.
                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail,
                ): Boolean {
                    editorCrashed = true
                    rendererGone()
                    return true
                }
            }
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
            var touchDownTime = 0L
            var touchDownX = 0f
            var touchDownY = 0f
            var touchMoved = false
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Keep vertical and horizontal editor scrolling inside WebView/Monaco.
                        // Do not let a parent drawer, pager, or sidebar consume the gesture.
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        touchDownTime = event.eventTime
                        touchDownX = event.x
                        touchDownY = event.y
                        touchMoved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - touchDownX
                        val dy = event.y - touchDownY
                        if ((dx * dx) + (dy * dy) > touchSlop * touchSlop) {
                            touchMoved = true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        // Only a short, stationary tap should request focus and
                        // open the IME. Long-press and drag gestures belong to
                        // Android/WebView text selection and must not be
                        // interrupted by a second focus request.
                        val isTap = !touchMoved &&
                            event.eventTime - touchDownTime < longPressTimeout
                        if (isTap) {
                            v.requestFocus()
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                    as InputMethodManager
                            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        touchMoved = false
                    }
                }
                false   // do not consume the event — let WebView handle it
            }
            loadUrl("file:///android_asset/editor/index.html")
        }
    }

    // ── Editor view helper — shows editor WebView or crash placeholder ──────
    //
    // When editorCrashed is true, the Monaco renderer has terminated. The app
    // remains alive (onRenderProcessGone returned true), and the WebView object
    // is still valid. Tapping Reload calls loadUrl() to start a fresh renderer.
    val editorView: @Composable (Modifier) -> Unit = { mod ->
        val layoutAwareModifier = mod.onSizeChanged { size: IntSize ->
            if (isEditorReady && size.width > 0 && size.height > 0) {
                editorBridge.send(editorWebView, EditorOutbound.ForceLayout)
            }
        }
        if (editorCrashed) {
            EditorCrashedBox(
                modifier = layoutAwareModifier,
                onReload = {
                    editorCrashed = false
                    editorWebView.post {
                        editorWebView.loadUrl("file:///android_asset/editor/index.html")
                    }
                },
            )
        } else {
            AndroidView(factory = { editorWebView }, update = {}, modifier = layoutAwareModifier)
        }
    }

    // ── Forward ViewModel editor commands to Monaco ─────────────────────────
    LaunchedEffect(Unit) {
        editorCommands.collect { command ->
            editorBridge.send(editorWebView, command)
        }
    }

    // ── Load active file into Monaco when tab or readiness changes ──────────
    val configuration = LocalConfiguration.current
    val layoutKey = "${configuration.screenWidthDp}x${configuration.screenHeightDp}"

    LaunchedEffect(activeTab?.id, isEditorReady, editorBindRevision) {
        val content = activeTabContent ?: activeTab?.content
        if (isEditorReady && activeTab != null && content != null) {
            editorBridge.send(
                editorWebView,
                EditorOutbound.LoadFile(
                    path     = activeTab.documentUri,
                    content  = content,
                    language = activeTab.language,
                ),
            )
            // Restore cursor position for this tab (if previously saved).
            val cursor = tabCursorPositions[activeTab.documentUri]
            if (cursor != null && (cursor.first > 1 || cursor.second > 1)) {
                editorBridge.send(editorWebView, EditorOutbound.SetCursorPosition(cursor.first, cursor.second))
            }
            // Restore scroll position last — overrides any scroll caused by revealCursor.
            val scroll = tabScrollPositions[activeTab.documentUri]
            if (scroll != null && scroll > 0) {
                editorBridge.send(editorWebView, EditorOutbound.SetScrollPosition(scroll))
            }
        }
    }

    // Layout changes (rotation, IME, sidebar width) only require a viewport
    // relayout; they must not reload the model or reparse the file.
    LaunchedEffect(layoutKey, isEditorReady) {
        if (isEditorReady) editorBridge.send(editorWebView, EditorOutbound.ForceLayout)
    }

    Column(modifier = modifier.fillMaxSize()) {
        editorView(Modifier.weight(1f).fillMaxWidth())

        // Symbol shortcut bar — shown above keyboard toolbar when a tab is active
        if (activeTab != null && showSymbolBar) {
            HorizontalDivider(thickness = 1.dp, color = colors.separator)
            SymbolBar(symbols = EditorSettings.DEFAULT_SYMBOLS, onInsertSymbol = onInsertText)
        }

        // Keyboard toolbar — fixed five-item pages, no horizontal scrolling
        if (activeTab != null && showKeyboardToolbar) {
            HorizontalDivider(thickness = 1.dp, color = colors.separator)
            KeyboardToolbar(
                onExecuteCommand     = onExecuteCommand,
                onPasteFromClipboard = onPasteFromClipboard,
                hasEditorSelection   = hasEditorSelection,
                actionOrder          = keyboardToolbarOrder,
                onToggleKeyboard     = { shouldShow ->
                    editorWebView.requestFocus()
                    val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
                    if (shouldShow) {
                        inputMethodManager?.showSoftInput(editorWebView, InputMethodManager.SHOW_IMPLICIT)
                    } else {
                        inputMethodManager?.hideSoftInputFromWindow(editorWebView.windowToken, 0)
                    }
                },
            )
        }
    }
}

// ── Editor crash placeholder ──────────────────────────────────────────────────
//
// Shown in place of the Monaco WebView when its renderer process has terminated.
// The Reload button calls loadUrl() on the existing WebView object to start a
// fresh renderer — the app remains alive throughout.

@Composable
private fun EditorCrashedBox(modifier: Modifier = Modifier, onReload: () -> Unit) {
    val colors = LocalIdeColors.current
    Column(
        modifier         = modifier.background(colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text  = "Editor unavailable.\nThe render process terminated.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDisabled,
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onReload) {
            Icon(
                imageVector        = Icons.Default.Refresh,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Reload Editor")
        }
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

// ── Keyboard toolbar — one ordered list, rendered five actions per page ────────

@Composable
private fun KeyboardToolbar(
    onExecuteCommand: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    hasEditorSelection: Boolean,
    actionOrder: List<String>,
    onToggleKeyboard: (Boolean) -> Unit,
) {
    val colors = LocalIdeColors.current
    val actionCatalog = EDITOR_TOOLBAR_ACTIONS.associateBy { it.id }
    val orderedActions = actionOrder
        .distinct()
        .mapNotNull(actionCatalog::get)
        .ifEmpty {
            EditorSettings.DEFAULT_KEYBOARD_TOOLBAR_ORDER.mapNotNull(actionCatalog::get)
        }
    val pages = orderedActions.chunked(KEYBOARD_TOOLBAR_PAGE_SIZE)
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pageIndex = selectedPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    val density = LocalDensity.current
    val keyboardShowing = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(actionOrder) {
        selectedPage = selectedPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { selectedPage = (pageIndex - 1).coerceAtLeast(0) },
                enabled = pageIndex > 0,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous toolbar page")
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                pages.getOrElse(pageIndex) { emptyList() }.forEach { action ->
                    ToolbarIconButton(
                        action = action,
                        icon = if (action.isKeyboardToggle && keyboardShowing) {
                            Icons.Default.KeyboardHide
                        } else {
                            action.icon
                        },
                        enabled = !action.requiresSelection || hasEditorSelection,
                        onExecuteCommand = onExecuteCommand,
                        onPaste = onPasteFromClipboard,
                        onCustomClick = if (action.isKeyboardToggle) {
                            { onToggleKeyboard(!keyboardShowing) }
                        } else null,
                    )
                }
            }
            IconButton(
                onClick = { selectedPage = (pageIndex + 1).coerceAtMost(pages.lastIndex.coerceAtLeast(0)) },
                enabled = pageIndex < pages.lastIndex,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next toolbar page")
            }
        }
        if (pages.size > 1) {
            Text(
                text = "Page ${pageIndex + 1} of ${pages.size}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarIconButton(
    action: EditorToolbarAction,
    icon: ImageVector,
    enabled: Boolean = true,
    onExecuteCommand: (String) -> Unit,
    onPaste: () -> Unit,
    onCustomClick: (() -> Unit)? = null,
) {
    val colors = LocalIdeColors.current
    val tooltipState = rememberTooltipState()

    fun performAction() {
        when {
            onCustomClick != null -> onCustomClick()
            action.isPaste -> onPaste()
            action.commandId != null -> onExecuteCommand(action.commandId)
        }
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(action.label, style = MaterialTheme.typography.labelSmall)
            }
        },
        state = tooltipState,
    ) {
        IconButton(
            onClick = ::performAction,
            enabled = enabled,
            modifier = Modifier
                .size(44.dp)
                .semantics {
                    role = Role.Button
                    onClick(action.label) { performAction(); true }
                }
                .pointerInput(enabled, action.repeatable, action.commandId) {
                    if (!enabled || !action.repeatable) return@pointerInput
                    kotlinx.coroutines.coroutineScope {
                        val gestureScope = this
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false)
                                val repeatJob = gestureScope.launch {
                                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                                    while (true) {
                                        performAction()
                                        delay(70L)
                                    }
                                }
                                waitForUpOrCancellation()
                                repeatJob.cancel()
                            }
                        }
                    }
                },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = action.label,
                tint = if (enabled) colors.textSecondary else colors.textDisabled,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
