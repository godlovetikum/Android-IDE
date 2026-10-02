package dev.android.ide.ui.screen

import android.view.ViewGroup
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.android.ide.browser.BrowserDownload
import dev.android.ide.browser.BrowserTabUi
import dev.android.ide.browser.BrowserViewModel
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

private const val MAX_VIEWPORT_WIDTH = 2400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserSurface(
    onOpenNavigation: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onApplicationBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val browser: BrowserViewModel = viewModel()
    val state by browser.uiState.collectAsState()
    var addressText by remember { mutableStateOf(state.address) }
    var addressFocused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var tabsOpen by remember { mutableStateOf(false) }
    var downloadsOpen by remember { mutableStateOf(false) }
    var viewportOpen by remember { mutableStateOf(false) }
    var viewportText by remember { mutableStateOf("") }
    var viewportError by remember { mutableStateOf<String?>(null) }
    var urlRowVisible by remember { mutableStateOf(true) }
    var errorVisible by remember { mutableStateOf(false) }
    var previousScrollY by remember { mutableIntStateOf(0) }
    var tabSearchQuery by remember { mutableStateOf("") }
    val visibleGeckoView = remember { mutableStateOf<org.mozilla.geckoview.GeckoView?>(null) }

    LaunchedEffect(state.address) { addressText = state.address }
    LaunchedEffect(state.error) { if (state.error != null) errorVisible = true }
    BackHandler(enabled = state.selectedTabId != null) {
        val selectedTab = state.tabs.firstOrNull { it.id == state.selectedTabId }
        if (selectedTab?.canGoBack == true) browser.goBack() else onApplicationBack()
    }

    val browserColorScheme = when (state.settings.theme) {
        "dark" -> darkColorScheme()
        "light" -> lightColorScheme()
        else -> null
    }
    if (browserColorScheme != null) MaterialTheme(colorScheme = browserColorScheme) {
        BrowserSurfaceContent(modifier, state, browser, visibleGeckoView, addressText, { addressText = it }, addressFocused, { addressFocused = it }, state.history, browser::clearHistoryEntry, { browser.createTab() }, { state.selectedTabId?.let { browser.captureTabPreview(it, visibleGeckoView.value) }; tabsOpen = true }, { onOpenNavigation() }, { onOpenSettings() }, tabSearchQuery, { tabSearchQuery = it }, { urlRowVisible = it }, urlRowVisible, menuOpen, { menuOpen = it }, tabsOpen, { tabsOpen = it }, downloadsOpen, { downloadsOpen = it }, viewportOpen, { viewportOpen = it }, viewportText, { viewportText = it }, viewportError, { viewportError = it }, errorVisible, { errorVisible = it }, previousScrollY, { previousScrollY = it })
    } else {
        BrowserSurfaceContent(modifier, state, browser, visibleGeckoView, addressText, { addressText = it }, addressFocused, { addressFocused = it }, state.history, browser::clearHistoryEntry, { browser.createTab() }, { state.selectedTabId?.let { browser.captureTabPreview(it, visibleGeckoView.value) }; tabsOpen = true }, { onOpenNavigation() }, { onOpenSettings() }, tabSearchQuery, { tabSearchQuery = it }, { urlRowVisible = it }, urlRowVisible, menuOpen, { menuOpen = it }, tabsOpen, { tabsOpen = it }, downloadsOpen, { downloadsOpen = it }, viewportOpen, { viewportOpen = it }, viewportText, { viewportText = it }, viewportError, { viewportError = it }, errorVisible, { errorVisible = it }, previousScrollY, { previousScrollY = it })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserSurfaceContent(
    modifier: Modifier,
    state: dev.android.ide.browser.BrowserUiState,
    browser: BrowserViewModel,
    visibleGeckoView: androidx.compose.runtime.MutableState<org.mozilla.geckoview.GeckoView?>,
    addressText: String,
    setAddressText: (String) -> Unit,
    addressFocused: Boolean,
    setAddressFocused: (Boolean) -> Unit,
    history: List<String>,
    clearHistoryEntry: (String) -> Unit,
    createTab: () -> Unit,
    openTabs: () -> Unit,
    onOpenNavigation: () -> Unit,
    onOpenSettings: () -> Unit,
    tabSearchQuery: String,
    setTabSearchQuery: (String) -> Unit,
    setUrlRowVisible: (Boolean) -> Unit,
    urlRowVisible: Boolean,
    menuOpen: Boolean,
    setMenuOpen: (Boolean) -> Unit,
    tabsOpen: Boolean,
    setTabsOpen: (Boolean) -> Unit,
    downloadsOpen: Boolean,
    setDownloadsOpen: (Boolean) -> Unit,
    viewportOpen: Boolean,
    setViewportOpen: (Boolean) -> Unit,
    viewportText: String,
    setViewportText: (String) -> Unit,
    viewportError: String?,
    setViewportError: (String?) -> Unit,
    errorVisible: Boolean,
    setErrorVisible: (Boolean) -> Unit,
    previousScrollY: Int,
    setPreviousScrollY: (Int) -> Unit,
) {
    val density = LocalDensity.current
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Android IDE Browser") },
            navigationIcon = { IconButton(onClick = onOpenNavigation) { Icon(Icons.Default.Menu, "Open Android IDE navigation") } },
            actions = {
                IconButton(onClick = { if (state.loading) browser.stop() else browser.reload() }, enabled = state.selectedTabId != null) {
                    Icon(Icons.Default.Refresh, if (state.loading) "Stop loading" else "Refresh")
                }
                IconButton(onClick = { setMenuOpen(true) }) { Icon(Icons.Default.MoreVert, "Browser menu") }
                IconButton(onClick = openTabs) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.tabs.size.toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
                IconButton(onClick = createTab) { Icon(Icons.Default.Add, "New tab") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { setMenuOpen(false) }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.DeveloperMode, null) },
                        text = { Text(if (state.developerToolsOpen) "Hide developer tools" else "Show developer tools") },
                        onClick = { setMenuOpen(false); browser.toggleDeveloperTools() },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Download, null) },
                        text = { Text("Downloads") },
                        onClick = { setMenuOpen(false); browser.reconcileDownloads(); setDownloadsOpen(true) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Settings, null) },
                        text = { Text("Custom viewport") },
                        onClick = {
                            setMenuOpen(false)
                            setViewportText(state.viewportWidth?.toString().orEmpty())
                            setViewportError(null)
                            setViewportOpen(true)
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Settings, null) },
                        text = { Text("Browser settings") },
                        onClick = { setMenuOpen(false); onOpenSettings() },
                    )
                }
            },
        )
        AnimatedVisibility(
            visible = urlRowVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = browser::goHome) { Icon(Icons.Default.Home, "Home page") }
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = setAddressText,
                        modifier = Modifier.weight(1f).onFocusChanged { setAddressFocused(it.isFocused) },
                        singleLine = true,
                        label = { Text("Search or enter address") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { browser.navigate(addressText); setAddressFocused(false) }),
                        trailingIcon = if (addressFocused && addressText.isNotEmpty()) ({
                            IconButton(onClick = { setAddressText("") }) { Icon(Icons.Default.Close, "Clear address") }
                        }) else null,
                    )
                    IconButton(onClick = browser::goBack, enabled = state.tabs.firstOrNull { it.id == state.selectedTabId }?.canGoBack == true) { Icon(Icons.Default.ArrowBack, "Back") }
                    IconButton(onClick = browser::goForward, enabled = state.tabs.firstOrNull { it.id == state.selectedTabId }?.canGoForward == true) { Icon(Icons.Default.ArrowForward, "Forward") }
                }
                if (addressFocused && history.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            history.take(8).forEach { entry ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { setAddressText(entry) }.padding(start = 12.dp)) {
                                    Text(entry, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { clearHistoryEntry(entry) }) { Icon(Icons.Default.Close, "Remove from history") }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.loading) Text("Loading ${state.loadProgress}%", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall)
        state.downloadProgress?.let { progress ->
            val label = if (progress.totalBytes > 0) "Downloading ${progress.name} · ${(progress.bytes * 100 / progress.totalBytes).toInt()}%" else "Downloading ${progress.name}…"
            Text(label, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall)
        }
        Box(modifier = Modifier.fillMaxSize().clickable { setUrlRowVisible(true) }) {
            state.selectedTabId?.let { tabId ->
                AndroidView(
                    factory = { browserContext ->
                        SwipeRefreshLayout(browserContext).apply {
                            val geckoView = org.mozilla.geckoview.GeckoView(browserContext)
                            visibleGeckoView.value = geckoView
                            addView(geckoView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            setOnChildScrollUpCallback { _, _ -> geckoView.canScrollVertically(-1) }
                            setOnRefreshListener {
                                browser.reload()
                            }
                        }
                    },
                    update = { refreshContainer ->
                        val view = refreshContainer.getChildAt(0) as? org.mozilla.geckoview.GeckoView
                        if (view != null) {
                            visibleGeckoView.value = view
                            val desiredWidth = state.viewportWidth?.let { (it * density.density).roundToInt() }
                            val params = view.layoutParams
                            val targetWidth = desiredWidth ?: ViewGroup.LayoutParams.MATCH_PARENT
                            if (params.width != targetWidth) {
                                params.width = targetWidth
                                view.layoutParams = params
                                view.requestLayout()
                                refreshContainer.requestLayout()
                            }
                            browser.bind(view, tabId) { scrollY ->
                                setUrlRowVisible(when {
                                    scrollY <= 0 -> true
                                    scrollY > previousScrollY + 2 -> false
                                    scrollY < previousScrollY - 2 -> true
                                    else -> urlRowVisible
                                })
                                setPreviousScrollY(scrollY)
                            }
                        }
                        if (!state.loading) refreshContainer.isRefreshing = false
                    },
                    modifier = if (state.viewportWidth == null) Modifier.fillMaxSize() else Modifier.width(state.viewportWidth.dp).fillMaxHeight(),
                )
            }
        }
    }

    if (tabsOpen) {
        ModalBottomSheet(onDismissRequest = { setTabsOpen(false) }) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Open tabs (${state.tabs.size})", style = MaterialTheme.typography.titleLarge)
                TextField(
                    value = tabSearchQuery,
                    onValueChange = setTabSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search your tabs") },
                )
                val visibleTabs = state.tabs.filter { tab ->
                    val query = tabSearchQuery.trim()
                    query.isBlank() || tab.title.contains(query, ignoreCase = true) || tab.host.contains(query, ignoreCase = true) || tab.url.contains(query, ignoreCase = true)
                }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleTabs, key = { it.id }) { tab ->
                    TabPreviewCard(tab, tab.id == state.selectedTabId, onSelect = { browser.selectTab(tab.id); setTabsOpen(false) }, onClose = { browser.closeTab(tab.id) })
                }
            }
            }
        }
    }
    if (downloadsOpen) DownloadsDialog(state.downloads, browser, onDismiss = { setDownloadsOpen(false) })
    if (viewportOpen) {
        AlertDialog(
            onDismissRequest = { setViewportOpen(false) },
            title = { Text("Custom viewport width") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current: ${state.viewportWidth?.let { "$it px" } ?: "Device width"}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = viewportText, onValueChange = { setViewportText(it.filter(Char::isDigit).take(4)); setViewportError(null) }, label = { Text("320–$MAX_VIEWPORT_WIDTH px") }, singleLine = true)
                    viewportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val width = viewportText.toIntOrNull()
                    if (width == null || width !in 320..MAX_VIEWPORT_WIDTH) setViewportError("Enter a width from 320 to $MAX_VIEWPORT_WIDTH pixels.")
                    else { browser.setViewportWidth(width); setViewportOpen(false) }
                }) { Text("Apply") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { browser.resetViewport(); setViewportOpen(false) }) { Text("Reset") }
                    TextButton(onClick = { setViewportOpen(false) }) { Text("Cancel") }
                }
            },
        )
    }
    state.permissionPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { browser.answerPermission(false) },
            title = { Text("Site permission") },
            text = { Text("${prompt.origin} requested ${prompt.permission}.") },
            confirmButton = { TextButton(onClick = { browser.answerPermission(true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { browser.answerPermission(false) }) { Text("Deny") } },
        )
    }
    state.duplicateDownload?.let { prompt ->
        AlertDialog(
            onDismissRequest = { browser.confirmDuplicateDownload(false) },
            title = { Text("File already exists") },
            text = { Text("${prompt.name} is already present in the download list. Replace it?") },
            confirmButton = { TextButton(onClick = { browser.confirmDuplicateDownload(true) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { browser.confirmDuplicateDownload(false) }) { Text("Keep existing") } },
        )
    }
    if (errorVisible) {
        AlertDialog(onDismissRequest = { setErrorVisible(false); browser.clearError() }, title = { Text("Browser error") }, text = { Text(state.error.orEmpty()) }, confirmButton = { TextButton(onClick = { setErrorVisible(false); browser.clearError() }) { Text("OK") } })
    }
}

@Composable
private fun TabPreviewCard(tab: BrowserTabUi, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val preview = remember(tab.previewPath) {
        tab.previewPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    Card(onClick = onSelect, colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().height(235.dp)) {
            Box(
                Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface),
            ) {
                if (preview != null) {
                    Image(preview, contentDescription = "Snapshot of ${tab.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Language, contentDescription = null)
                        Text("Preview not available", style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, "Close tab") }
            }
            Text(tab.title.ifBlank { "New tab" }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), maxLines = 2, style = MaterialTheme.typography.titleSmall)
            Text(tab.host, modifier = Modifier.padding(horizontal = 10.dp), maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DownloadsDialog(downloads: List<BrowserDownload>, browser: BrowserViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Downloads", style = MaterialTheme.typography.titleLarge)
            if (downloads.isEmpty()) {
                Text("No downloads have been recorded.", modifier = Modifier.padding(vertical = 24.dp))
            } else {
                downloads.asReversed().take(50).forEach { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.width(56.dp).height(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Downloaded file")
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                Text(if (item.available) "Available" else "Unavailable", style = MaterialTheme.typography.labelMedium, color = if (item.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                Text(item.mimeType, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (item.available) {
                                        TextButton(onClick = {
                                            val intent = browser.openDownload(item.id)
                                            if (intent == null) browser.reportError("The downloaded file is no longer available")
                                            else runCatching { context.startActivity(intent) }.onFailure { browser.reportError("No application can open this file") }
                                        }) { Text("Open") }
                                        TextButton(onClick = { browser.deleteDownload(item.id) }) { Text("Delete") }
                                    } else {
                                        TextButton(onClick = { browser.removeUnavailableDownload(item.id) }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
        }
    }
}
