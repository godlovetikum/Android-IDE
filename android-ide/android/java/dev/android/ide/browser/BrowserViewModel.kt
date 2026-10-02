package dev.android.ide.browser

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.util.UUID

private const val PREFS = "browser_state"
private const val TABS_KEY = "tabs"
private const val SELECTED_TAB_KEY = "selected_tab"
private const val DOWNLOADS_KEY = "downloads"
private const val SEARCH_ENGINE_KEY = "search_engine"
private const val CUSTOM_SEARCH_KEY = "custom_search"
private const val HOME_PAGE_KEY = "home_page"
private const val DOWNLOAD_FOLDER_KEY = "download_folder"
private const val THEME_KEY = "theme"
private const val DEVTOOLS_KEY = "developer_tools_enabled"
private const val DESKTOP_SITE_KEY = "desktop_site_default"
private const val DEFAULT_HOME = "about:blank"
private const val DEFAULT_SEARCH = "duckduckgo"

private data class PendingDuplicateDownload(
    val name: String,
    val sourceUrl: String,
    val mimeType: String,
    val body: java.io.InputStream,
)

private val SEARCH_ENGINES = mapOf(
    "google" to "https://www.google.com/search?q=%s",
    "bing" to "https://www.bing.com/search?q=%s",
    "duckduckgo" to "https://duckduckgo.com/?q=%s",
    "brave" to "https://search.brave.com/search?q=%s",
    "startpage" to "https://www.startpage.com/sp/search?query=%s",
)

data class BrowserSettings(
    val searchEngine: String = DEFAULT_SEARCH,
    val customSearchUrl: String = "",
    val homePage: String = DEFAULT_HOME,
    val downloadFolder: String? = null,
    val theme: String = "system",
    val developerToolsEnabled: Boolean = true,
    val desktopSiteDefault: Boolean = false,
)

data class BrowserDownload(
    val id: String,
    val name: String,
    val uri: String,
    val sourceUrl: String,
    val mimeType: String,
    val size: Long,
    val timestamp: Long,
    val available: Boolean = true,
)

data class BrowserTabUi(
    val id: String,
    val url: String,
    val title: String,
    val host: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val viewportWidth: Int?,
    val previewPath: String?,
)

data class BrowserUiState(
    val tabs: List<BrowserTabUi> = emptyList(),
    val selectedTabId: String? = null,
    val address: String = "",
    val loading: Boolean = false,
    val loadProgress: Int = 0,
    val viewportWidth: Int? = null,
    val downloads: List<BrowserDownload> = emptyList(),
    val settings: BrowserSettings = BrowserSettings(),
    val developerToolsOpen: Boolean = false,
    val permissionPrompt: BrowserPermissionPrompt? = null,
    val duplicateDownload: DuplicateDownloadPrompt? = null,
    val error: String? = null,
)

data class BrowserPermissionPrompt(val origin: String, val permission: String)
data class DuplicateDownloadPrompt(val name: String, val sourceUrl: String, val existingUri: String)

internal data class BrowserTabRuntime(
    val id: String,
    val session: GeckoSession,
    var url: String,
    var title: String,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var sessionState: String? = null,
    var viewportWidth: Int? = null,
    var previewPath: String? = null,
)

/** Application-owned browser domain. It survives navigation away from the Browser surface. */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val runtime = GeckoRuntime.create(context)
    private val sessions = linkedMapOf<String, BrowserTabRuntime>()
    private var pendingDuplicateBody: PendingDuplicateDownload? = null
    private var pendingPermissionResult: GeckoResult<Int>? = null
    private val _uiState = MutableStateFlow(BrowserUiState(settings = readSettings()))
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        installDeveloperTools()
        restoreTabs()
        reconcileDownloads()
    }

    private fun installDeveloperTools() {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/devtools/",
            "android-ide-devtools@android-ide",
        ).accept({}, { error -> setError("Developer tools could not be installed: ${error?.message ?: error}") })
    }

    private fun restoreTabs() {
        val stored = runCatching { JSONArray(preferences.getString(TABS_KEY, "[]")) }.getOrDefault(JSONArray())
        if (stored.length() == 0) {
            createTab()
            return
        }
        for (index in 0 until stored.length()) {
            val item = stored.optJSONObject(index) ?: continue
            createTab(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                url = item.optString("url", DEFAULT_HOME),
                title = item.optString("title", "New tab"),
                savedState = item.optString("state").takeIf(String::isNotBlank),
                viewportWidth = item.optInt("viewportWidth", 0).takeIf { it in 320..2400 },
                previewPath = item.optString("previewPath").takeIf { it.isNotBlank() },
                select = false,
            )
        }
        val selected = preferences.getString(SELECTED_TAB_KEY, null)
        selectTab(selected?.takeIf(sessions::containsKey) ?: sessions.keys.first())
    }

    fun createTab(url: String = DEFAULT_HOME) {
        createTab(UUID.randomUUID().toString(), url, "New tab", null, null, null, true)
    }

    private fun createTab(
        id: String,
        url: String,
        title: String,
        savedState: String?,
        viewportWidth: Int?,
        previewPath: String?,
        select: Boolean,
    ) {
        val sessionSettings = GeckoSessionSettings.Builder()
            .userAgentMode(if (_uiState.value.settings.desktopSiteDefault) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .build()
        val session = GeckoSession(sessionSettings)
        val tab = BrowserTabRuntime(id, session, url, title, sessionState = savedState, viewportWidth = viewportWidth, previewPath = previewPath)
        sessions[id] = tab
        attachDelegates(tab)
        session.open(runtime)
        savedState?.let { GeckoSession.SessionState.fromString(it)?.let(session::restoreState) }
        if (savedState == null || url == DEFAULT_HOME) session.loadUri(url)
        if (select || _uiState.value.selectedTabId == null) selectTab(id) else publish()
    }

    private fun attachDelegates(tab: BrowserTabRuntime) {
        tab.session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: List<PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                tab.url = url ?: DEFAULT_HOME
                publish()
                persistTabs()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack = canGoBack; publish() }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canGoForward = canGoForward; publish() }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val id = UUID.randomUUID().toString()
                createTab(id, uri, "New tab", null, null, null, true)
                return GeckoResult.fromValue(sessions.getValue(id).session)
            }
        })
        tab.session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.url = url
                _uiState.value = _uiState.value.copy(loading = true, loadProgress = 0)
                publish()
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                _uiState.value = _uiState.value.copy(loading = progress < 100, loadProgress = progress)
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                _uiState.value = _uiState.value.copy(loading = false, loadProgress = 100)
                persistTabs()
            }
            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                tab.sessionState = sessionState.toString()
                persistTabs()
            }
        })
        tab.session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title?.takeIf(String::isNotBlank) ?: tab.url.hostOrNewTab()
                publish()
                persistTabs()
            }
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) { beginDownload(response) }
        })
        tab.session.setPermissionDelegate(object : PermissionDelegate {
            override fun onContentPermissionRequest(session: GeckoSession, permission: PermissionDelegate.ContentPermission): GeckoResult<Int> {
                val origin = permission.uri
                val name = permissionName(permission.permission)
                val result = GeckoResult<Int>()
                pendingPermissionResult = result
                _uiState.value = _uiState.value.copy(permissionPrompt = BrowserPermissionPrompt(origin, name))
                return result
            }
        })
    }

    fun answerPermission(allow: Boolean) {
        pendingPermissionResult?.complete(if (allow) PermissionDelegate.ContentPermission.VALUE_ALLOW else PermissionDelegate.ContentPermission.VALUE_DENY)
        pendingPermissionResult = null
        _uiState.value = _uiState.value.copy(permissionPrompt = null)
        if (!allow) setError("The site permission was denied")
    }

    private fun beginDownload(response: WebResponse) {
        val body = response.body
        if (body == null) { setError("The download did not provide a readable response"); return }
        val name = response.uri.substringAfterLast('/').substringBefore('?').ifBlank { "download-${System.currentTimeMillis()}" }
        val existing = _uiState.value.downloads.firstOrNull { it.name == name && it.available }
        if (existing != null) {
            pendingDuplicateBody = PendingDuplicateDownload(name, response.uri, response.headers["Content-Type"] ?: "application/octet-stream", body)
            _uiState.value = _uiState.value.copy(duplicateDownload = DuplicateDownloadPrompt(name, response.uri, existing.uri))
            return
        }
        writeDownload(name, response.uri, response.headers["Content-Type"] ?: "application/octet-stream", body)
    }

    fun confirmDuplicateDownload(replace: Boolean) {
        val prompt = _uiState.value.duplicateDownload ?: return
        val pending = pendingDuplicateBody
        pendingDuplicateBody = null
        _uiState.value = _uiState.value.copy(duplicateDownload = null)
        if (pending == null) return
        if (replace) {
            if (!deleteStoredFile(Uri.parse(prompt.existingUri))) {
                pending.body.close()
                setError("The existing downloaded file could not be replaced")
                return
            }
            writeDownload(pending.name, pending.sourceUrl, pending.mimeType, pending.body)
        } else {
            val suffix = prompt.name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".${it}" }
            val stem = prompt.name.removeSuffix(suffix)
            writeDownload("$stem (${System.currentTimeMillis()})$suffix", pending.sourceUrl, pending.mimeType, pending.body)
        }
    }

    private fun writeDownload(name: String, sourceUrl: String, mimeType: String, body: java.io.InputStream) {
        val resolver = context.contentResolver
        val configuredFolder = _uiState.value.settings.downloadFolder
        val destination = runCatching {
            if (configuredFolder == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    })
                } else {
                    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
                    val file = File(directory, name)
                    resolver.insert(MediaStore.Files.getContentUri("external"), ContentValues().apply {
                        put(MediaStore.MediaColumns.DATA, file.absolutePath)
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    })
                }
            } else {
                val treeUri = Uri.parse(configuredFolder)
                val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                DocumentsContract.createDocument(resolver, parent, mimeType, name)
            }
        }.getOrNull()
        if (destination == null) { body.close(); setError("Choose an available download folder in Browser settings"); return }
        runCatching {
            resolver.openOutputStream(destination)?.use { output -> body.use { input -> input.copyTo(output) } } ?: error("Destination is not writable")
            if (configuredFolder == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) resolver.update(destination, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            val download = BrowserDownload(UUID.randomUUID().toString(), name, destination.toString(), sourceUrl, mimeType, querySize(destination), System.currentTimeMillis())
            val updated = _uiState.value.downloads + download
            _uiState.value = _uiState.value.copy(downloads = updated)
            persistDownloads(updated)
        }.onFailure {
            body.close()
            resolver.delete(destination, null, null)
            setError("Download failed: ${it.message}")
        }
    }

    fun reconcileDownloads() {
        val stored = readDownloads().map { it.copy(available = runCatching { context.contentResolver.openFileDescriptor(Uri.parse(it.uri), "r")?.use { true } ?: false }.getOrDefault(false)) }
        _uiState.value = _uiState.value.copy(downloads = stored)
    }

    /** Returns an ACTION_VIEW intent only for a file that is currently present. */
    fun openDownload(downloadId: String): Intent? {
        val download = _uiState.value.downloads.firstOrNull { it.id == downloadId } ?: return null
        if (!download.available) return null
        val uri = Uri.parse(download.uri)
        val present = runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true }.getOrDefault(false)
        if (!present) {
            reconcileDownloads()
            return null
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, download.mimeType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Deletes the local file for an available download, then removes its record. */
    fun deleteDownload(downloadId: String): Boolean {
        val download = _uiState.value.downloads.firstOrNull { it.id == downloadId } ?: return false
        if (download.available) {
            val deleted = deleteStoredFile(Uri.parse(download.uri))
            if (!deleted) {
                reconcileDownloads()
                setError("The downloaded file could not be deleted")
                return false
            }
        }
        removeDownloadRecord(downloadId)
        return true
    }

    /** Removes an unavailable record without attempting to delete a missing file. */
    fun removeUnavailableDownload(downloadId: String): Boolean {
        val download = _uiState.value.downloads.firstOrNull { it.id == downloadId } ?: return false
        if (download.available) return false
        removeDownloadRecord(downloadId)
        return true
    }

    fun reportError(message: String) { setError(message) }

    private fun removeDownloadRecord(downloadId: String) {
        val updated = _uiState.value.downloads.filterNot { it.id == downloadId }
        _uiState.value = _uiState.value.copy(downloads = updated)
        persistDownloads(updated)
    }

    private fun deleteStoredFile(uri: Uri): Boolean = runCatching {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    }.getOrDefault(false)

    private fun querySize(uri: Uri): Long = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L }.getOrDefault(0L)

    fun selectTab(tabId: String) {
        if (!sessions.containsKey(tabId)) return
        preferences.edit().putString(SELECTED_TAB_KEY, tabId).apply()
        publish(selectedId = tabId)
    }

    fun closeTab(tabId: String) {
        sessions.remove(tabId)?.session?.close()
        if (sessions.isEmpty()) createTab()
        else if (_uiState.value.selectedTabId == tabId) selectTab(sessions.keys.last())
        persistTabs()
        publish()
    }

    fun navigate(address: String) {
        val tab = sessions[_uiState.value.selectedTabId] ?: return
        val entered = address.trim()
        if (entered.isEmpty()) return
        val target = resolveAddress(entered)
        tab.url = target
        tab.session.loadUri(target)
        _uiState.value = _uiState.value.copy(address = target, error = null)
        persistTabs()
    }

    fun goHome() { navigate(_uiState.value.settings.homePage) }
    fun goBack() { sessions[_uiState.value.selectedTabId]?.session?.goBack() }
    fun goForward() { sessions[_uiState.value.selectedTabId]?.session?.goForward() }
    fun reload() { sessions[_uiState.value.selectedTabId]?.session?.reload() }
    fun stop() { sessions[_uiState.value.selectedTabId]?.session?.stop() }

    fun setViewportWidth(width: Int) {
        sessions[_uiState.value.selectedTabId]?.let { tab ->
            tab.viewportWidth = width.takeIf { it in 320..2400 }
            persistTabs()
            publish()
        }
    }

    fun toggleDeveloperTools() {
        val open = !_uiState.value.developerToolsOpen
        _uiState.value = _uiState.value.copy(developerToolsOpen = open)
    }

    fun setDeveloperToolsEnabled(enabled: Boolean) {
        updateSettings { it.copy(developerToolsEnabled = enabled) }
        if (!enabled) _uiState.value = _uiState.value.copy(developerToolsOpen = false)
    }

    fun setSearchEngine(engine: String) { updateSettings { it.copy(searchEngine = engine) } }
    fun setCustomSearchUrl(url: String) { updateSettings { it.copy(customSearchUrl = url) } }
    fun setHomePage(url: String) { updateSettings { it.copy(homePage = url.ifBlank { DEFAULT_HOME }) } }
    fun setTheme(theme: String) { updateSettings { it.copy(theme = theme) } }
    fun setDesktopSiteDefault(enabled: Boolean) { updateSettings { it.copy(desktopSiteDefault = enabled) } }
    fun setDownloadFolder(uri: String?) { updateSettings { it.copy(downloadFolder = uri) } }
    fun clearBrowserData() {
        sessions.values.forEach { it.session.purgeHistory() }
        preferences.edit().remove(TABS_KEY).remove(DOWNLOADS_KEY).apply()
        _uiState.value = _uiState.value.copy(downloads = emptyList())
    }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun session(tabId: String): GeckoSession? = sessions[tabId]?.session
    fun bind(view: GeckoView, tabId: String, onScroll: (Int) -> Unit = {}) {
        sessions[tabId]?.session?.let { session ->
            view.setSession(session)
            session.setScrollDelegate(object : GeckoSession.ScrollDelegate {
                override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) { onScroll(scrollY) }
            })
        }
    }

    /** Captures the currently rendered tab when the tab switcher is opened. */
    fun captureTabPreview(tabId: String, view: View?) {
        val tab = sessions[tabId] ?: return
        if (view == null || view.width <= 0 || view.height <= 0) return
        runCatching {
            val targetWidth = view.width.coerceAtMost(720)
            val scale = targetWidth.toFloat() / view.width.toFloat()
            val targetHeight = (view.height * scale).toInt().coerceIn(1, 1280)
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                scale(scale, scale)
                view.draw(this)
            }
            val directory = java.io.File(context.cacheDir, "browser_tab_previews").apply { mkdirs() }
            val file = java.io.File(directory, "${tab.id}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 85, it) }
            bitmap.recycle()
            tab.previewPath = file.absolutePath
            persistTabs()
            publish()
        }
    }

    private fun resolveAddress(value: String): String {
        val hasScheme = value.contains("://") || value.startsWith("about:") || value.startsWith("file:")
        val looksLikeHost = value == "localhost" || value.startsWith("localhost:") || value.matches(Regex("^[0-9.]+(:[0-9]+)?(/.*)?$")) || value.contains('.')
        return if (hasScheme) value else if (looksLikeHost) "https://$value" else {
            val template = SEARCH_ENGINES[_uiState.value.settings.searchEngine] ?: _uiState.value.settings.customSearchUrl
            (template.ifBlank { SEARCH_ENGINES.getValue(DEFAULT_SEARCH) }).replace("%s", Uri.encode(value))
        }
    }

    private fun publish(selectedId: String? = _uiState.value.selectedTabId) {
        val selected = sessions[selectedId]
        _uiState.value = _uiState.value.copy(
            tabs = sessions.values.map { BrowserTabUi(it.id, it.url, it.title, it.url.hostOrNewTab(), it.canGoBack, it.canGoForward, it.viewportWidth, it.previewPath) },
            selectedTabId = selectedId,
            address = selected?.url?.takeUnless { it == DEFAULT_HOME }.orEmpty(),
            viewportWidth = selected?.viewportWidth,
        )
    }

    private fun persistTabs() {
        val array = JSONArray()
        sessions.values.forEach { tab -> array.put(JSONObject().apply {
            put("id", tab.id); put("url", tab.url); put("title", tab.title); put("viewportWidth", tab.viewportWidth ?: 0); tab.previewPath?.let { put("previewPath", it) }; tab.sessionState?.let { put("state", it) }
        }) }
        preferences.edit().putString(TABS_KEY, array.toString()).apply()
    }

    private fun readSettings() = BrowserSettings(
        searchEngine = preferences.getString(SEARCH_ENGINE_KEY, DEFAULT_SEARCH) ?: DEFAULT_SEARCH,
        customSearchUrl = preferences.getString(CUSTOM_SEARCH_KEY, "") ?: "",
        homePage = preferences.getString(HOME_PAGE_KEY, DEFAULT_HOME) ?: DEFAULT_HOME,
        downloadFolder = preferences.getString(DOWNLOAD_FOLDER_KEY, null),
        theme = preferences.getString(THEME_KEY, "system") ?: "system",
        developerToolsEnabled = preferences.getBoolean(DEVTOOLS_KEY, true),
        desktopSiteDefault = preferences.getBoolean(DESKTOP_SITE_KEY, false),
    )

    private fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) {
        val next = transform(_uiState.value.settings)
        preferences.edit().putString(SEARCH_ENGINE_KEY, next.searchEngine).putString(CUSTOM_SEARCH_KEY, next.customSearchUrl)
            .putString(HOME_PAGE_KEY, next.homePage).putString(THEME_KEY, next.theme).putBoolean(DEVTOOLS_KEY, next.developerToolsEnabled)
            .putBoolean(DESKTOP_SITE_KEY, next.desktopSiteDefault).apply()
        if (next.downloadFolder == null) preferences.edit().remove(DOWNLOAD_FOLDER_KEY).apply() else preferences.edit().putString(DOWNLOAD_FOLDER_KEY, next.downloadFolder).apply()
        _uiState.value = _uiState.value.copy(settings = next)
    }

    private fun readDownloads(): List<BrowserDownload> = runCatching {
        val array = JSONArray(preferences.getString(DOWNLOADS_KEY, "[]"))
        List(array.length()) { index -> array.getJSONObject(index).let { BrowserDownload(it.getString("id"), it.getString("name"), it.getString("uri"), it.getString("sourceUrl"), it.optString("mimeType", "application/octet-stream"), it.optLong("size"), it.optLong("timestamp")) } }
    }.getOrDefault(emptyList())

    private fun persistDownloads(downloads: List<BrowserDownload>) {
        val array = JSONArray(); downloads.forEach { item -> array.put(JSONObject().apply { put("id", item.id); put("name", item.name); put("uri", item.uri); put("sourceUrl", item.sourceUrl); put("mimeType", item.mimeType); put("size", item.size); put("timestamp", item.timestamp) }) }
        preferences.edit().putString(DOWNLOADS_KEY, array.toString()).apply()
    }

    private fun setError(message: String) { _uiState.value = _uiState.value.copy(error = message) }

    private fun permissionName(permission: Int): String = when (permission) {
        PermissionDelegate.PERMISSION_GEOLOCATION -> "location"
        PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notifications"
        PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "persistent storage"
        PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE, PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> "autoplay"
        else -> "additional site access"
    }

    override fun onCleared() {
        persistTabs(); persistDownloads(_uiState.value.downloads)
        sessions.values.forEach { it.session.close() }
        runtime.shutdown()
        super.onCleared()
    }
}

private fun String.hostOrNewTab(): String = runCatching { Uri.parse(this).host?.takeIf(String::isNotBlank) ?: "New tab" }.getOrDefault("New tab")
