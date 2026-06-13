// android-ide/android/java/dev/androidide/viewmodel/IdeViewModel.kt
//
// Single ViewModel for the entire IDE session.
// Bridges SAF, Monaco editor bridge, project registry, session, and theme.

package dev.androidide.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.androidide.data.CrashRecoveryRepository
import dev.androidide.data.EditorSettingsRepository
import dev.androidide.data.ProjectRepository
import dev.androidide.data.SessionRepository
import dev.androidide.data.ThemeRepository
import dev.androidide.data.model.AppTheme
import dev.androidide.data.model.EditorSettings
import dev.androidide.data.model.Project
import dev.androidide.data.model.VolumeKeyMode
import dev.androidide.editor.EditorInbound
import dev.androidide.editor.EditorOutbound
import dev.androidide.saf.SafRepository
import dev.androidide.viewmodel.model.AppScreen
import dev.androidide.viewmodel.model.EditorTab
import dev.androidide.viewmodel.model.FileNode
import dev.androidide.viewmodel.model.FileOpDialog
import dev.androidide.viewmodel.model.FileSearchResult
import dev.androidide.viewmodel.model.IdeUiState
import dev.androidide.viewmodel.model.findNode
import dev.androidide.viewmodel.model.pathTo
import dev.androidide.viewmodel.model.removeNode
import dev.androidide.viewmodel.model.replaceNode
import dev.androidide.viewmodel.model.setChildren
import dev.androidide.viewmodel.model.sortedForTree
import dev.androidide.viewmodel.model.toggleExpanded
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class IdeViewModel(application: Application) : AndroidViewModel(application) {

    private val safRepository      = SafRepository(application)
    private val projectRepository  = ProjectRepository(application)
    private val sessionRepository  = SessionRepository(application)
    private val themeRepository    = ThemeRepository(application)
    private val editorSettingsRepo = EditorSettingsRepository(application)
    private val crashRecovery      = CrashRecoveryRepository(application)

    private val _uiState = MutableStateFlow(
        IdeUiState(
            appTheme       = themeRepository.get(),
            recentProjects = projectRepository.getAll(),
            editorSettings = editorSettingsRepo.getEditorSettings(),
            volumeKeyMode  = editorSettingsRepo.getVolumeKeyMode(),
        )
    )
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    /**
     * Outbound Monaco commands.
     * EditorPane collects this flow and forwards each command to Monaco via EditorBridge.
     * extraBufferCapacity=16 prevents dropped commands before EditorPane attaches its collector.
     */
    private val _editorCommand = MutableSharedFlow<EditorOutbound>(extraBufferCapacity = 16)
    val editorCommand: SharedFlow<EditorOutbound> = _editorCommand.asSharedFlow()

    /**
     * Unsaved editor content keyed by tab ID.
     * Kept outside IdeUiState to avoid full Compose recomposition on every keystroke.
     */
    private val pendingContent = mutableMapOf<String, String>()

    init {
        crashRecovery.markSessionStart()
        checkCrashRecovery()
        restoreSession()
    }

    override fun onCleared() {
        super.onCleared()
        crashRecovery.markCleanExit()
        saveSession()
    }

    // ── Theme helpers ───────────────────────────────────────────────────────

    private fun isSystemDark(): Boolean =
        (getApplication<Application>().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * Resolves the Monaco theme name from the current editor theme setting.
     * "system" follows the device dark-mode state. "dark" / "light" are explicit.
     */
    private fun resolveMonacoTheme(): String {
        val settings = _uiState.value.editorSettings
        return when (settings.editorTheme) {
            "light"  -> "light"
            "dark"   -> "dark"
            else     -> if (isSystemDark()) "dark" else "light"
        }
    }

    // ── Crash recovery ──────────────────────────────────────────────────────

    private fun checkCrashRecovery() {
        if (!crashRecovery.isPreviousSessionDirty()) return
        val entries = crashRecovery.getUnsavedEntries()
        if (entries.isEmpty()) return
        _uiState.update { it.copy(recoveryEntries = entries) }
    }

    fun restoreFromCrash() {
        val entries = _uiState.value.recoveryEntries
        _uiState.update { it.copy(recoveryEntries = emptyList()) }
        var firstId: String? = null
        entries.forEach { entry ->
            val tab = EditorTab(
                id          = entry.tabId,
                documentUri = entry.documentUri,
                displayName = entry.displayName,
                language    = languageForExtension(entry.displayName.substringAfterLast('.', "")),
                content     = entry.content,
                isDirty     = true,
                isActive    = false,
            )
            if (firstId == null) firstId = tab.id
            pendingContent[tab.id] = entry.content
            _uiState.update { state ->
                state.copy(openTabs = state.openTabs + tab, currentScreen = AppScreen.EDITOR)
            }
        }
        firstId?.let { selectTab(it) }
    }

    fun dismissCrashRecovery() {
        crashRecovery.clearAll()
        _uiState.update { it.copy(recoveryEntries = emptyList()) }
    }

    // ── Session ────────────────────────────────────────────────────────────

    private fun restoreSession() {
        val projectUri = sessionRepository.getProjectUri() ?: return
        val tabUris    = sessionRepository.getOpenTabUris()
        val activeUri  = sessionRepository.getActiveTabUri()
        val screenName = sessionRepository.getScreenName()

        val screen = screenName?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
        if (screen != null) _uiState.update { it.copy(currentScreen = screen) }

        viewModelScope.launch { openProjectInternal(projectUri) }
        tabUris.forEach { uri ->
            viewModelScope.launch { openFileInternal(uri, markActive = uri == activeUri) }
        }
    }

    fun saveSession() {
        val state = _uiState.value
        sessionRepository.save(
            projectUri   = state.projectRootUri,
            openTabUris  = state.openTabs.filter { !it.isBlank }.map { it.documentUri },
            activeTabUri = state.openTabs.firstOrNull { it.isActive && !it.isBlank }?.documentUri,
            screenName   = state.currentScreen.name,
        )
    }

    // ── Navigation ─────────────────────────────────────────────────────────

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
        saveSession()
    }

    // ── App theme ───────────────────────────────────────────────────────────

    fun setTheme(theme: AppTheme) {
        themeRepository.set(theme)
        _uiState.update { it.copy(appTheme = theme) }
        if (_uiState.value.isEditorReady) {
            sendEditorCommand(EditorOutbound.SetTheme(resolveMonacoTheme()))
        }
    }

    // ── Editor settings ─────────────────────────────────────────────────────

    fun setEditorSettings(settings: EditorSettings) {
        val previousTheme = _uiState.value.editorSettings.editorTheme
        editorSettingsRepo.setEditorSettings(settings)
        _uiState.update { it.copy(editorSettings = settings) }
        sendEditorCommand(EditorOutbound.SetEditorOptions(
            tabSize     = settings.tabSize,
            wordWrap    = settings.wordWrap,
            lineNumbers = settings.lineNumbers,
            fontSize    = settings.fontSize,
        ))
        if (_uiState.value.isEditorReady && settings.editorTheme != previousTheme) {
            sendEditorCommand(EditorOutbound.SetTheme(resolveMonacoTheme()))
        }
    }

    fun setVolumeKeyMode(mode: VolumeKeyMode) {
        editorSettingsRepo.setVolumeKeyMode(mode)
        _uiState.update { it.copy(volumeKeyMode = mode) }
    }

    // ── Volume keys ─────────────────────────────────────────────────────────

    fun onVolumeUp() {
        when (_uiState.value.volumeKeyMode) {
            VolumeKeyMode.HORIZONTAL -> sendEditorCommand(EditorOutbound.ExecuteCommand("cursorLeft"))
            VolumeKeyMode.VERTICAL   -> sendEditorCommand(EditorOutbound.ExecuteCommand("cursorUp"))
            VolumeKeyMode.DISABLED   -> return
        }
    }

    fun onVolumeDown() {
        when (_uiState.value.volumeKeyMode) {
            VolumeKeyMode.HORIZONTAL -> sendEditorCommand(EditorOutbound.ExecuteCommand("cursorRight"))
            VolumeKeyMode.VERTICAL   -> sendEditorCommand(EditorOutbound.ExecuteCommand("cursorDown"))
            VolumeKeyMode.DISABLED   -> return
        }
    }

    fun sendEditorCommand(command: EditorOutbound) {
        _editorCommand.tryEmit(command)
    }

    // ── Project management ─────────────────────────────────────────────────

    fun openProject(treeUriString: String) {
        viewModelScope.launch { openProjectInternal(treeUriString) }
    }

    private suspend fun openProjectInternal(treeUriString: String) {
        val name = extractProjectName(treeUriString)
        _uiState.update { it.copy(projectName = name, projectRootUri = treeUriString, currentScreen = AppScreen.EDITOR) }
        projectRepository.upsert(Project(name = name, uri = treeUriString))
        _uiState.update { it.copy(recentProjects = projectRepository.getAll()) }
        val nodes = safRepository.listChildren(treeUriString)
        _uiState.update { it.copy(fileTree = nodes.sortedForTree()) }
    }

    /**
     * Create a new blank project folder in the app's workspace directory with the given [name],
     * then open it as the active project.
     *
     * No folder picker is shown — the project is created automatically under
     * [getExternalFilesDir("Projects")], falling back to [filesDir]/projects if external storage
     * is unavailable. No special runtime permission is required for these app-owned locations.
     */
    fun createBlankProject(name: String) {
        val trimmed = name.trim().ifEmpty { "Project" }
        val projectsDir = getApplication<Application>().getExternalFilesDir("Projects")
            ?: File(getApplication<Application>().filesDir, "projects")
        projectsDir.mkdirs()
        val newDir = File(projectsDir, trimmed)
        if (!newDir.exists() && !newDir.mkdirs()) {
            _uiState.update { it.copy(statusMessage = "Could not create project folder") }
            return
        }
        val newDirUri = Uri.fromFile(newDir).toString()
        viewModelScope.launch { openProjectInternal(newDirUri) }
    }

    /**
     * Update the display name stored in the project registry.
     * Does NOT rename the filesystem folder.
     */
    fun renameProjectInRegistry(uri: String, newName: String) {
        projectRepository.upsert(Project(name = newName, uri = uri))
        _uiState.update { it.copy(recentProjects = projectRepository.getAll()) }
    }

    /** Placeholder — project duplication will be implemented in a later phase. */
    fun duplicateProject(uri: String) {
        _uiState.update { it.copy(statusMessage = "Duplicate project not yet implemented") }
    }

    fun removeProjectFromRegistry(uri: String) {
        projectRepository.remove(uri)
        _uiState.update { it.copy(recentProjects = projectRepository.getAll()) }
    }

    fun closeCurrentProject() {
        _uiState.value.openTabs.forEach { pendingContent.remove(it.id) }
        _uiState.update { state ->
            state.copy(
                projectName    = "",
                projectRootUri = null,
                fileTree       = emptyList(),
                openTabs       = emptyList(),
                activeTabId    = null,
                isEditorReady  = false,
                currentScreen  = AppScreen.PROJECTS,
            )
        }
        saveSession()
    }

    fun refreshProject() {
        val rootUri = _uiState.value.projectRootUri ?: return
        viewModelScope.launch {
            val nodes = safRepository.listChildren(rootUri)
            _uiState.update { it.copy(fileTree = nodes.sortedForTree()) }
        }
    }

    // ── File tree ──────────────────────────────────────────────────────────

    fun toggleDirectory(documentUri: String) {
        _uiState.update { state -> state.copy(fileTree = state.fileTree.toggleExpanded(documentUri)) }
        val node = _uiState.value.fileTree.findNode(documentUri)
        if (node != null && node.isExpanded && node.isDirectory && node.children.isEmpty()) {
            viewModelScope.launch {
                val children = safRepository.listChildren(documentUri)
                _uiState.update { state ->
                    state.copy(fileTree = state.fileTree.setChildren(documentUri, children.sortedForTree()))
                }
            }
        }
    }

    // ── File tree clipboard ─────────────────────────────────────────────────

    fun copyFileNode(node: FileNode) = _uiState.update { it.copy(clipboard = node, clipboardIsCut = false) }
    fun cutFileNode(node: FileNode)  = _uiState.update { it.copy(clipboard = node, clipboardIsCut = true) }
    fun clearClipboard()             = _uiState.update { it.copy(clipboard = null, clipboardIsCut = false) }

    fun pasteFileNode(targetDir: FileNode) {
        val source = _uiState.value.clipboard ?: return
        val isCut  = _uiState.value.clipboardIsCut
        viewModelScope.launch {
            if (isCut) {
                val sourceParent = source.parentDocumentUri ?: run {
                    _uiState.update { it.copy(statusMessage = "Move failed: unknown parent") }
                    return@launch
                }
                val newUri = safRepository.moveDocument(source.documentUri, sourceParent, targetDir.documentUri) ?: run {
                    _uiState.update { it.copy(statusMessage = "Move failed") }
                    return@launch
                }
                _uiState.update { state ->
                    state.copy(
                        openTabs       = state.openTabs.map { tab ->
                            if (tab.documentUri == source.documentUri) tab.copy(documentUri = newUri) else tab
                        },
                        clipboard      = null,
                        clipboardIsCut = false,
                        statusMessage  = "Moved ${source.displayName}",
                    )
                }
                refreshDirectory(sourceParent)
                refreshDirectory(targetDir.documentUri)
            } else {
                safRepository.copyDocument(source.documentUri, targetDir.documentUri) ?: run {
                    _uiState.update { it.copy(statusMessage = "Copy failed") }
                    return@launch
                }
                refreshDirectory(targetDir.documentUri)
                _uiState.update { it.copy(statusMessage = "Copied ${source.displayName}") }
            }
        }
    }

    // ── Import / Export ────────────────────────────────────────────────────

    /**
     * Copy each file from [sourceUris] into [targetDirUri].
     * Requires read permission on each source URI (caller must have called
     * ContentResolver.takePersistableUriPermission before invoking this).
     */
    fun importFiles(targetDirUri: String, sourceUris: List<String>) {
        viewModelScope.launch {
            var count = 0
            sourceUris.forEach { uri ->
                val bytes = safRepository.readFile(uri) ?: return@forEach
                val name  = safRepository.getDisplayName(uri)
                    ?: uri.substringAfterLast('/', "imported_file")
                val created = safRepository.createFile(targetDirUri, name, mimeTypeForName(name))
                if (created != null && safRepository.writeFile(created, bytes)) count++
            }
            refreshDirectory(targetDirUri)
            _uiState.update { it.copy(statusMessage = "Imported $count file(s)") }
        }
    }

    /** Placeholder — ZIP export will be implemented in a later phase. */
    fun exportDirectory(node: FileNode) {
        _uiState.update { it.copy(statusMessage = "Export not yet implemented") }
    }

    /** Placeholder — ZIP export will be implemented in a later phase. */
    fun exportProject() {
        _uiState.update { it.copy(statusMessage = "Export not yet implemented") }
    }

    // ── Editor tabs ────────────────────────────────────────────────────────

    fun newBlankTab() {
        val tabId = UUID.randomUUID().toString()
        val tab = EditorTab(
            id          = tabId,
            documentUri = "blank://new/$tabId",
            displayName = "untitled",
            language    = "plaintext",
            content     = "",
            isActive    = true,
            isBlank     = true,
        )
        _uiState.update { state ->
            state.copy(
                openTabs      = state.openTabs.map { it.copy(isActive = false) } + tab,
                activeTabId   = tabId,
                currentScreen = AppScreen.EDITOR,
            )
        }
    }

    fun openFile(documentUri: String) {
        viewModelScope.launch { openFileInternal(documentUri, markActive = true) }
    }

    private suspend fun openFileInternal(documentUri: String, markActive: Boolean) {
        val existing = _uiState.value.openTabs.find { it.documentUri == documentUri }
        if (existing != null) {
            if (markActive) selectTab(existing.id)
            return
        }
        val bytes = safRepository.readFile(documentUri) ?: return
        val content = String(bytes, Charsets.UTF_8)
        val displayName = _uiState.value.fileTree.findNode(documentUri)?.displayName
            ?: safRepository.getDisplayName(documentUri)
            ?: displayNameFromUri(documentUri)
        val language = languageForExtension(displayName.substringAfterLast('.', ""))

        val newTab = EditorTab(
            documentUri = documentUri,
            displayName = displayName,
            language    = language,
            content     = content,
            isActive    = markActive,
        )
        _uiState.update { state ->
            val tabs = if (markActive) {
                state.openTabs.map { it.copy(isActive = false) } + newTab
            } else {
                state.openTabs + newTab
            }
            state.copy(
                openTabs      = tabs,
                activeTabId   = if (markActive) newTab.id else state.activeTabId,
                currentScreen = AppScreen.EDITOR,
            )
        }
    }

    fun selectTab(tabId: String) {
        _uiState.update { state ->
            state.copy(
                openTabs    = state.openTabs.map { it.copy(isActive = it.id == tabId) },
                activeTabId = tabId,
            )
        }
    }

    /** Close a tab immediately, discarding unsaved changes without confirmation. */
    fun closeTab(tabId: String) {
        val tab = _uiState.value.openTabs.find { it.id == tabId }
        pendingContent.remove(tabId)
        crashRecovery.clearUnsavedContent(tabId)
        _uiState.update { state ->
            val remaining   = state.openTabs.filter { it.id != tabId }
            val newActiveId = if (state.activeTabId == tabId) remaining.lastOrNull()?.id else state.activeTabId
            state.copy(
                openTabs    = remaining.map { it.copy(isActive = it.id == newActiveId) },
                activeTabId = newActiveId,
            )
        }
        tab?.let { sendEditorCommand(EditorOutbound.CloseTab(it.documentUri)) }
    }

    /**
     * Close with dirty-check.
     * Shows UnsavedClose confirmation if the tab has unsaved changes.
     */
    fun closeTabSafe(tabId: String) {
        val tab = _uiState.value.openTabs.find { it.id == tabId } ?: return
        if (tab.isDirty) {
            _uiState.update { it.copy(fileOpDialog = FileOpDialog.UnsavedClose(tabId, tab.displayName)) }
        } else {
            closeTab(tabId)
        }
    }

    /** User chose "Save & Close" in the UnsavedClose dialog. */
    fun saveAndCloseTab(tabId: String) {
        val tab     = _uiState.value.openTabs.find { it.id == tabId } ?: run {
            _uiState.update { it.copy(fileOpDialog = null) }
            return
        }
        val content = pendingContent[tabId]
        _uiState.update { it.copy(fileOpDialog = null) }
        if (content == null || tab.isBlank) { closeTab(tabId); return }
        viewModelScope.launch {
            val ok = safRepository.writeFile(tab.documentUri, content.toByteArray(Charsets.UTF_8))
            if (ok) {
                pendingContent.remove(tabId)
                crashRecovery.clearUnsavedContent(tabId)
            }
            closeTab(tabId)
        }
    }

    /** User chose "Discard" in the UnsavedClose dialog. */
    fun confirmCloseTab(tabId: String) {
        _uiState.update { it.copy(fileOpDialog = null) }
        closeTab(tabId)
    }

    fun closeOtherTabs(tabId: String) {
        _uiState.value.openTabs.filter { it.id != tabId }.forEach { closeTab(it.id) }
    }

    fun closeAllTabs() {
        _uiState.value.openTabs.toList().forEach { closeTab(it.id) }
    }

    fun saveAllFiles() {
        _uiState.value.openTabs.filter { it.isDirty && !it.isBlank }.forEach { saveFile(it.documentUri) }
    }

    // ── Exit confirmation ───────────────────────────────────────────────────

    fun requestExit(): Boolean {
        val hasDirty = _uiState.value.openTabs.any { it.isDirty }
        if (hasDirty) {
            _uiState.update { it.copy(showExitConfirmation = true) }
            return true
        }
        return false
    }

    fun dismissExitConfirmation() = _uiState.update { it.copy(showExitConfirmation = false) }

    fun saveAllAndExit(onReady: () -> Unit) {
        saveAllFiles()
        _uiState.update { it.copy(showExitConfirmation = false) }
        crashRecovery.markCleanExit()
        onReady()
    }

    // ── Editor bridge ──────────────────────────────────────────────────────

    fun onEditorReady() {
        _uiState.update { it.copy(isEditorReady = true) }
        val settings = _uiState.value.editorSettings
        sendEditorCommand(EditorOutbound.SetEditorOptions(
            tabSize     = settings.tabSize,
            wordWrap    = settings.wordWrap,
            lineNumbers = settings.lineNumbers,
            fontSize    = settings.fontSize,
        ))
        sendEditorCommand(EditorOutbound.SetTheme(resolveMonacoTheme()))
        sendEditorCommand(EditorOutbound.ForceLayout)
    }

    fun onEditorMessage(message: EditorInbound) {
        when (message) {
            is EditorInbound.Ready -> onEditorReady()

            is EditorInbound.ContentChanged -> {
                val tab = _uiState.value.openTabs.find { it.documentUri == message.path } ?: return
                pendingContent[tab.id] = message.content
                _uiState.update { state ->
                    state.copy(openTabs = state.openTabs.map {
                        if (it.id == tab.id) it.copy(isDirty = true) else it
                    })
                }
                crashRecovery.saveUnsavedContent(tab.id, tab.documentUri, tab.displayName, message.content)
                if (_uiState.value.editorSettings.autoSave) {
                    saveFile(message.path)
                }
            }

            is EditorInbound.CursorMoved -> {
                _uiState.update { it.copy(cursorLine = message.line, cursorColumn = message.column) }
            }

            is EditorInbound.FileSaved -> saveFile(message.path)
        }
    }

    // ── Save ───────────────────────────────────────────────────────────────

    fun saveActiveFile() {
        val active = _uiState.value.openTabs.firstOrNull { it.isActive } ?: return
        if (active.isBlank) return
        saveFile(active.documentUri)
    }

    fun saveTabById(tabId: String) {
        val tab = _uiState.value.openTabs.find { it.id == tabId } ?: return
        if (tab.isBlank) return
        saveFile(tab.documentUri)
    }

    private fun saveFile(documentUri: String) {
        val tab     = _uiState.value.openTabs.find { it.documentUri == documentUri } ?: return
        if (tab.isBlank) return
        val content = pendingContent[tab.id] ?: return

        _uiState.update { state ->
            state.copy(openTabs = state.openTabs.map {
                if (it.id == tab.id) it.copy(isSaving = true) else it
            })
        }
        viewModelScope.launch {
            val ok = safRepository.writeFile(documentUri, content.toByteArray(Charsets.UTF_8))
            if (ok) {
                pendingContent.remove(tab.id)
                crashRecovery.clearUnsavedContent(tab.id)
                _uiState.update { state ->
                    state.copy(
                        openTabs      = state.openTabs.map {
                            if (it.id == tab.id) it.copy(isDirty = false, isSaving = false) else it
                        },
                        statusMessage = "Saved",
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        openTabs      = state.openTabs.map {
                            if (it.id == tab.id) it.copy(isSaving = false) else it
                        },
                        statusMessage = "Save failed",
                    )
                }
            }
        }
    }

    fun saveActiveFileAs(newUri: String) {
        val active  = _uiState.value.openTabs.firstOrNull { it.isActive } ?: return
        val content = pendingContent[active.id] ?: active.content ?: return
        viewModelScope.launch {
            val ok = safRepository.writeFile(newUri, content.toByteArray(Charsets.UTF_8))
            if (!ok) { _uiState.update { it.copy(statusMessage = "Save As failed") }; return@launch }
            val newName = safRepository.getDisplayName(newUri) ?: displayNameFromUri(newUri)
            val newLang = languageForExtension(newName.substringAfterLast('.', ""))
            pendingContent.remove(active.id)
            crashRecovery.clearUnsavedContent(active.id)
            _uiState.update { state ->
                state.copy(
                    openTabs      = state.openTabs.map {
                        if (it.id == active.id) it.copy(
                            documentUri = newUri, displayName = newName,
                            language = newLang, isDirty = false, isBlank = false,
                        ) else it
                    },
                    statusMessage = "Saved as $newName",
                )
            }
        }
    }

    // ── Preview / Run ──────────────────────────────────────────────────────

    /**
     * Toggle the preview panel for the active tab.
     * Supports HTML (raw render) and Markdown (converted to styled HTML).
     * Other file types show a status message.
     */
    fun togglePreview() {
        val active = _uiState.value.openTabs.firstOrNull { it.isActive }
        if (active == null) {
            _uiState.update { it.copy(statusMessage = "No active file to preview") }
            return
        }
        val supportedLanguages = setOf("html", "markdown")
        if (active.language !in supportedLanguages) {
            _uiState.update { it.copy(statusMessage = "Preview not supported for ${active.language} files") }
            return
        }
        if (_uiState.value.isPreviewVisible) {
            _uiState.update { it.copy(isPreviewVisible = false) }
        } else {
            val content = pendingContent[active.id] ?: active.content.orEmpty()
            val htmlContent = when (active.language) {
                "html"     -> content
                "markdown" -> markdownToPreviewHtml(content)
                else       -> return
            }
            _uiState.update { it.copy(isPreviewVisible = true, previewHtmlContent = htmlContent) }
        }
    }

    // ── File search ────────────────────────────────────────────────────────

    fun showFileSearch() {
        _uiState.update { it.copy(isSearchVisible = true, fileSearchQuery = "", fileSearchResults = emptyList()) }
    }

    fun hideFileSearch() {
        _uiState.update { it.copy(isSearchVisible = false, fileSearchQuery = "", fileSearchResults = emptyList()) }
    }

    /**
     * Search file names (not content) in the loaded file tree.
     * Results are updated synchronously — no I/O.
     */
    fun searchFiles(query: String) {
        _uiState.update { it.copy(fileSearchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(fileSearchResults = emptyList()) }
            return
        }
        val results = mutableListOf<FileSearchResult>()
        fun searchNodes(nodes: List<FileNode>, path: String) {
            for (node in nodes) {
                val nodePath = if (path.isEmpty()) node.displayName else "$path/${node.displayName}"
                if (!node.isDirectory && node.displayName.contains(query, ignoreCase = true)) {
                    results += FileSearchResult(
                        documentUri  = node.documentUri,
                        displayName  = node.displayName,
                        relativePath = nodePath,
                    )
                }
                if (node.isDirectory && node.children.isNotEmpty()) {
                    searchNodes(node.children, nodePath)
                }
            }
        }
        searchNodes(_uiState.value.fileTree, "")
        _uiState.update { it.copy(fileSearchResults = results) }
    }

    // ── Reveal active file ─────────────────────────────────────────────────

    /**
     * Expand all parent directories in the file tree that contain the active file,
     * making the active file visible in the sidebar.
     * Only operates on the currently-loaded (non-lazy) subtree.
     */
    fun revealActiveFile() {
        val activeUri = _uiState.value.openTabs.firstOrNull { it.isActive }?.documentUri ?: run {
            _uiState.update { it.copy(statusMessage = "No active file") }
            return
        }
        val path = findPathToNode(_uiState.value.fileTree, activeUri)
        if (path == null) {
            _uiState.update { it.copy(statusMessage = "File not found in tree — try expanding folders first") }
            return
        }
        for (dirUri in path) {
            val node = _uiState.value.fileTree.findNode(dirUri)
            if (node != null && node.isDirectory && !node.isExpanded) {
                _uiState.update { state ->
                    state.copy(fileTree = state.fileTree.toggleExpanded(dirUri))
                }
            }
        }
    }

    private fun findPathToNode(
        nodes: List<FileNode>,
        targetUri: String,
        path: List<String> = emptyList(),
    ): List<String>? {
        for (node in nodes) {
            if (node.documentUri == targetUri) return path
            if (node.isDirectory && node.children.isNotEmpty()) {
                val found = findPathToNode(node.children, targetUri, path + node.documentUri)
                if (found != null) return found
            }
        }
        return null
    }

    // ── Multi-selection ────────────────────────────────────────────────────

    fun enterSelectionMode() {
        _uiState.update { it.copy(isMultiSelectMode = true, selectedUris = emptySet()) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isMultiSelectMode = false, selectedUris = emptySet()) }
    }

    fun toggleNodeSelection(uri: String) {
        _uiState.update { state ->
            val updated = if (uri in state.selectedUris) state.selectedUris - uri else state.selectedUris + uri
            // Enter selection mode automatically on first selection.
            state.copy(isMultiSelectMode = updated.isNotEmpty(), selectedUris = updated)
        }
    }

    // ── Clipboard path copy ────────────────────────────────────────────────

    /**
     * Copy the display path of [documentUri] to the system clipboard.
     * Uses the tree's relative path if the node is loaded; falls back to the raw URI.
     */
    fun copyPathToClipboard(documentUri: String) {
        val path = _uiState.value.fileTree.pathTo(documentUri) ?: Uri.decode(documentUri)
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("File Path", path))
        _uiState.update { it.copy(statusMessage = "Path copied") }
    }

    // ── Remove project (with confirmation) ────────────────────────────────

    /** Ask the user to confirm removing [uri] from the project registry. */
    fun requestRemoveProject(uri: String) {
        _uiState.update { it.copy(confirmRemoveProjectUri = uri) }
    }

    /** User confirmed — remove from registry and close if currently open. */
    fun confirmRemoveProject() {
        val uri = _uiState.value.confirmRemoveProjectUri ?: return
        projectRepository.remove(uri)
        _uiState.update { state ->
            val wasCurrent = state.projectRootUri == uri
            state.copy(
                recentProjects          = projectRepository.getAll(),
                confirmRemoveProjectUri = null,
                projectRootUri          = if (wasCurrent) null else state.projectRootUri,
                projectName             = if (wasCurrent) "" else state.projectName,
                fileTree                = if (wasCurrent) emptyList() else state.fileTree,
                statusMessage           = "Project removed",
            )
        }
    }

    /** User cancelled — dismiss the confirmation dialog. */
    fun cancelRemoveProject() {
        _uiState.update { it.copy(confirmRemoveProjectUri = null) }
    }

    // ── File operations ────────────────────────────────────────────────────

    fun showRenameDialog(node: FileNode)         = _uiState.update { it.copy(fileOpDialog = FileOpDialog.Rename(node)) }
    fun showDeleteDialog(node: FileNode)         = _uiState.update { it.copy(fileOpDialog = FileOpDialog.Delete(node)) }
    fun showCreateFileDialog(parent: FileNode)   = _uiState.update { it.copy(fileOpDialog = FileOpDialog.CreateFile(parent)) }
    fun showCreateFolderDialog(parent: FileNode) = _uiState.update { it.copy(fileOpDialog = FileOpDialog.CreateFolder(parent)) }
    fun showDuplicateDialog(node: FileNode)      = _uiState.update { it.copy(fileOpDialog = FileOpDialog.Duplicate(node)) }
    fun dismissFileOpDialog()                    = _uiState.update { it.copy(fileOpDialog = null) }

    fun renameNode(node: FileNode, newName: String) {
        viewModelScope.launch {
            val newUri = safRepository.renameDocument(node.documentUri, newName) ?: run {
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Rename failed") }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    openTabs      = state.openTabs.map {
                        if (it.documentUri == node.documentUri) it.copy(documentUri = newUri, displayName = newName) else it
                    },
                    fileTree      = state.fileTree.replaceNode(node.documentUri, node.copy(documentUri = newUri, displayName = newName)),
                    fileOpDialog  = null,
                    statusMessage = "Renamed to $newName",
                )
            }
        }
    }

    fun deleteNode(node: FileNode) {
        viewModelScope.launch {
            if (safRepository.deleteDocument(node.documentUri)) {
                val tab = _uiState.value.openTabs.find { it.documentUri == node.documentUri }
                if (tab != null) closeTab(tab.id)
                _uiState.update { state ->
                    state.copy(
                        fileTree      = state.fileTree.removeNode(node.documentUri),
                        fileOpDialog  = null,
                        statusMessage = "Deleted ${node.displayName}",
                    )
                }
            } else {
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Delete failed") }
            }
        }
    }

    fun createFileInDirectory(parentNode: FileNode, name: String) {
        viewModelScope.launch {
            safRepository.createFile(parentNode.documentUri, name, mimeTypeForName(name)) ?: run {
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Create failed") }
                return@launch
            }
            refreshDirectory(parentNode.documentUri)
            _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Created $name") }
        }
    }

    fun createFolderInDirectory(parentNode: FileNode, name: String) {
        viewModelScope.launch {
            safRepository.createFile(parentNode.documentUri, name, "vnd.android.document/directory") ?: run {
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Create failed") }
                return@launch
            }
            refreshDirectory(parentNode.documentUri)
            _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Created folder $name") }
        }
    }

    fun createFileAtRoot(name: String) {
        val rootUri = _uiState.value.projectRootUri ?: run {
            _uiState.update { it.copy(statusMessage = "No project open") }
            return
        }
        viewModelScope.launch {
            safRepository.createFile(rootUri, name, mimeTypeForName(name)) ?: run {
                _uiState.update { it.copy(statusMessage = "Create failed") }
                return@launch
            }
            refreshProject()
            _uiState.update { it.copy(statusMessage = "Created $name") }
        }
    }

    fun createFolderAtRoot(name: String) {
        val rootUri = _uiState.value.projectRootUri ?: run {
            _uiState.update { it.copy(statusMessage = "No project open") }
            return
        }
        viewModelScope.launch {
            safRepository.createFile(rootUri, name, "vnd.android.document/directory") ?: run {
                _uiState.update { it.copy(statusMessage = "Create failed") }
                return@launch
            }
            refreshProject()
            _uiState.update { it.copy(statusMessage = "Created folder $name") }
        }
    }

    fun duplicateFile(node: FileNode, newName: String) {
        val parentUri = node.parentDocumentUri ?: run {
            _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Cannot duplicate: no parent") }
            return
        }
        viewModelScope.launch {
            val bytes  = safRepository.readFile(node.documentUri) ?: run {
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Duplicate: read error") }
                return@launch
            }
            val newUri = safRepository.createFile(parentUri, newName, mimeTypeForName(newName)) ?: run {
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Duplicate: create error") }
                return@launch
            }
            safRepository.writeFile(newUri, bytes)
            refreshDirectory(parentUri)
            _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Duplicated as $newName") }
        }
    }

    private fun refreshDirectory(directoryUri: String) {
        viewModelScope.launch {
            val children = safRepository.listChildren(directoryUri)
            _uiState.update { state ->
                if (directoryUri == state.projectRootUri) {
                    state.copy(fileTree = children.sortedForTree())
                } else {
                    state.copy(fileTree = state.fileTree.setChildren(directoryUri, children.sortedForTree()))
                }
            }
        }
    }

    // ── Markdown preview converter ─────────────────────────────────────────

    /**
     * Convert a Markdown string to a self-contained HTML page for the preview WebView.
     * Supports headings, bold, italic, inline code, fenced code blocks,
     * blockquotes, unordered lists, horizontal rules, and links/images.
     */
    private fun markdownToPreviewHtml(markdown: String): String {
        val sb = StringBuilder()
        sb.append(
            """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;max-width:760px;margin:0 auto;padding:16px 20px;background:#fff;color:#24292e;line-height:1.6}
h1,h2{border-bottom:1px solid #eaecef;padding-bottom:.3em}
pre{background:#f6f8fa;border:1px solid #e1e4e8;border-radius:6px;padding:16px;overflow-x:auto}
code{font-family:'SFMono-Regular',Consolas,monospace;background:#f0f0f0;padding:.2em .4em;border-radius:3px;font-size:.9em}
pre code{background:none;padding:0;font-size:1em}
blockquote{margin:0;padding:0 1em;color:#6a737d;border-left:4px solid #dfe2e5}
hr{border:none;border-top:1px solid #e1e4e8;margin:16px 0}
img{max-width:100%}a{color:#0366d6}
ul,ol{padding-left:2em}
</style></head><body>"""
        )
        val lines = markdown.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("```")) {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    codeLines += lines[i]; i++
                }
                sb.append("<pre><code>").append(codeLines.joinToString("\n").escHtml()).append("</code></pre>\n")
                i++; continue
            }
            sb.append(when {
                line.startsWith("######") -> "<h6>${line.removePrefix("######").trim().mdInline()}</h6>"
                line.startsWith("#####")  -> "<h5>${line.removePrefix("#####").trim().mdInline()}</h5>"
                line.startsWith("####")   -> "<h4>${line.removePrefix("####").trim().mdInline()}</h4>"
                line.startsWith("###")    -> "<h3>${line.removePrefix("###").trim().mdInline()}</h3>"
                line.startsWith("##")     -> "<h2>${line.removePrefix("##").trim().mdInline()}</h2>"
                line.startsWith("#")      -> "<h1>${line.removePrefix("#").trim().mdInline()}</h1>"
                line.startsWith("- ") || line.startsWith("* ") ->
                    "<ul><li>${line.substring(2).trim().mdInline()}</li></ul>"
                line.matches(Regex("\\d+\\.\\s.*")) ->
                    "<ol><li>${line.substringAfter(". ").trim().mdInline()}</li></ol>"
                line.startsWith("> ") -> "<blockquote>${line.removePrefix("> ").mdInline()}</blockquote>"
                line.matches(Regex("[-*_]{3,}\\s*")) -> "<hr/>"
                line.isBlank() -> "<br/>"
                else -> "<p>${line.mdInline()}</p>"
            }).append('\n')
            i++
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun String.mdInline(): String {
        var s = this.escHtml()
        s = s.replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "<strong><em>$1</em></strong>")
        s = s.replace(Regex("\\*\\*(.*?)\\*\\*"),       "<strong>$1</strong>")
        s = s.replace(Regex("__(.*?)__"),               "<strong>$1</strong>")
        s = s.replace(Regex("\\*(.*?)\\*"),             "<em>$1</em>")
        s = s.replace(Regex("`(.*?)`"),                 "<code>$1</code>")
        s = s.replace(Regex("!\\[(.*?)]\\((.*?)\\)"),   "<img alt='$1' src='$2'>")
        s = s.replace(Regex("\\[(.*?)]\\((.*?)\\)"),    "<a href='$2'>$1</a>")
        return s
    }

    private fun String.escHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ── Private helpers ────────────────────────────────────────────────────

    private fun extractProjectName(treeUriString: String): String = try {
        Uri.decode(treeUriString).substringAfterLast('/').ifEmpty { "Project" }
    } catch (_: Exception) { "Project" }

    private fun displayNameFromUri(documentUri: String): String = try {
        Uri.decode(documentUri).substringAfterLast('/').ifEmpty { "file" }
    } catch (_: Exception) { "file" }

    private fun mimeTypeForName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "kt", "kts"         -> "text/x-kotlin"
        "java"              -> "text/x-java"
        "xml"               -> "text/xml"
        "json"              -> "application/json"
        "md"                -> "text/markdown"
        "html", "htm"       -> "text/html"
        "css"               -> "text/css"
        "js", "mjs"         -> "text/javascript"
        "ts"                -> "text/typescript"
        "py"                -> "text/x-python"
        "sh", "bash"        -> "text/x-sh"
        "c", "h"            -> "text/x-csrc"
        "cpp", "cc", "hpp"  -> "text/x-c++src"
        "rs"                -> "text/x-rust"
        "go"                -> "text/x-go"
        "yaml", "yml"       -> "text/x-yaml"
        "toml"              -> "text/x-toml"
        "sql"               -> "text/x-sql"
        "gradle"            -> "text/x-groovy"
        else                -> "text/plain"
    }

    private fun languageForExtension(ext: String): String = when (ext.lowercase()) {
        "kt", "kts"               -> "kotlin"
        "java"                    -> "java"
        "xml"                     -> "xml"
        "json"                    -> "json"
        "md"                      -> "markdown"
        "gradle"                  -> "groovy"
        "py"                      -> "python"
        "js", "mjs", "cjs"        -> "javascript"
        "ts", "mts", "cts"        -> "typescript"
        "html", "htm"             -> "html"
        "css"                     -> "css"
        "sh", "bash"              -> "shell"
        "c", "h"                  -> "c"
        "cpp", "cc", "cxx", "hpp" -> "cpp"
        "rs"                      -> "rust"
        "go"                      -> "go"
        "rb"                      -> "ruby"
        "swift"                   -> "swift"
        "toml"                    -> "toml"
        "yaml", "yml"             -> "yaml"
        "sql"                     -> "sql"
        "proto"                   -> "proto"
        else                      -> "plaintext"
    }
}
