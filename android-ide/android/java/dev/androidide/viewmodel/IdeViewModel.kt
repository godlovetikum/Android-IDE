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
import dev.androidide.editor.EditorLanguageRegistry
import dev.androidide.editor.EditorOutbound
import dev.androidide.saf.ChildrenInspectionResult
import dev.androidide.saf.ExactCreateResult
import dev.androidide.saf.PathResolutionResult
import dev.androidide.saf.SafeMutationResult
import dev.androidide.saf.SafRepository
import dev.androidide.viewmodel.model.AppScreen
import dev.androidide.viewmodel.model.EditorTab
import dev.androidide.viewmodel.model.FileNode
import dev.androidide.viewmodel.model.FileOpDialog
import dev.androidide.viewmodel.model.FileSearchResult
import dev.androidide.viewmodel.model.IdeUiState
import dev.androidide.viewmodel.model.ProjectSwitchRequest
import dev.androidide.viewmodel.model.NormalizedPathResult
import dev.androidide.viewmodel.model.ancestorsOf
import dev.androidide.viewmodel.model.findNode
import dev.androidide.viewmodel.model.normalizeProjectPath
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        // Read the previous session marker before marking this process as active.
        // Otherwise every launch looks like an unclean exit.
        val previousSessionDirty = crashRecovery.isPreviousSessionDirty()
        crashRecovery.markSessionStart()
        restoreSession(previousSessionDirty)
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

    private fun checkCrashRecovery(projectRootUri: String, previousSessionDirty: Boolean) {
        if (!previousSessionDirty) return
        val entries = crashRecovery.getUnsavedEntries(projectRootUri)
        if (entries.isEmpty()) return
        _uiState.update { it.copy(recoveryEntries = entries) }
    }

    fun restoreFromCrash() {
        val state = _uiState.value
        val projectRootUri = state.projectRootUri ?: return
        val entries = state.recoveryEntries.filter { it.projectRootUri == projectRootUri }
        if (entries.isEmpty()) return

        val restoredTabs = state.openTabs.toMutableList()
        var firstId: String? = null
        entries.forEach { entry ->
            // Replace an already-open Monaco model with the recovered text.
            // Normal rebinds preserve existing models; recovery is explicit.
            sendEditorCommand(EditorOutbound.CloseTab(entry.documentUri))
            val existingIndex = restoredTabs.indexOfFirst { it.documentUri == entry.documentUri }
            val existing = restoredTabs.getOrNull(existingIndex)
            val tabId = existing?.id ?: if (
                restoredTabs.none { it.id == entry.tabId }
            ) {
                entry.tabId
            } else {
                UUID.randomUUID().toString()
            }
            val tab = (existing ?: EditorTab(
                id          = tabId,
                documentUri = entry.documentUri,
                displayName = entry.displayName,
                language    = EditorLanguageRegistry.languageForFileName(entry.displayName),
            )).copy(
                displayName = entry.displayName,
                language    = EditorLanguageRegistry.languageForFileName(entry.displayName),
                content     = entry.content,
                isDirty     = true,
                isTemporary = false,
                isActive    = false,
            )
            if (existingIndex >= 0) {
                restoredTabs[existingIndex] = tab
            } else {
                restoredTabs += tab
            }
            if (firstId == null) firstId = tab.id
            pendingContent[tab.id] = entry.content
            // Keep the draft alive under the tab ID actually used by this
            // session, while the old recovery record remains project-scoped.
            crashRecovery.saveUnsavedContent(
                projectRootUri = projectRootUri,
                tabId          = tab.id,
                documentUri    = tab.documentUri,
                displayName    = tab.displayName,
                content        = entry.content,
            )
            if (entry.tabId != tab.id) {
                crashRecovery.clearUnsavedContent(projectRootUri, entry.tabId)
            }
        }
        _uiState.update {
            it.copy(
                openTabs          = restoredTabs,
                recoveryEntries   = emptyList(),
                currentScreen     = AppScreen.EDITOR,
                editorBindRevision = it.editorBindRevision + 1,
            )
        }
        firstId?.let { selectTab(it) }
    }

    fun dismissCrashRecovery() {
        _uiState.value.projectRootUri?.let { crashRecovery.clearProject(it) }
        _uiState.update { it.copy(recoveryEntries = emptyList()) }
    }

    // ── Session — per-project scoped ────────────────────────────────────────

    private fun restoreSession(previousSessionDirty: Boolean) {
        val projectUri = sessionRepository.getProjectUri() ?: return
        val screenName = sessionRepository.getScreenName()

        val screen = screenName?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
        if (screen != null) _uiState.update { it.copy(currentScreen = screen) }

        // F020: validate that the saved project URI still has a persisted SAF permission
        // grant before attempting to open it.  Grants can be revoked after device reboot
        // (if the provider does not survive reboot) or an explicit permission reset.
        val hasPermission = getApplication<Application>().contentResolver
            .persistedUriPermissions
            .any { it.uri.toString() == projectUri && it.isReadPermission && it.isWritePermission }
        if (!hasPermission) {
            _uiState.update {
                it.copy(statusMessage = "Previous project no longer accessible — re-open it from Projects")
            }
            return
        }

        // Load cursor and scroll positions before tabs open so the positions are
        // available in IdeUiState when EditorPane's LaunchedEffect fires.
        val cursorPositions = sessionRepository.getCursorPositionsForProject(projectUri)
        val scrollPositions = sessionRepository.getScrollPositionsForProject(projectUri)
        if (cursorPositions.isNotEmpty() || scrollPositions.isNotEmpty()) {
            _uiState.update { it.copy(
                tabCursorPositions = cursorPositions,
                tabScrollPositions = scrollPositions,
            )}
        }

        // F002: single sequential coroutine prevents race where parallel launches
        // reset openTabs mid-flight (openProjectInternal wipes tabs; parallel
        // openFileInternal calls may have already added tabs before the wipe).
        viewModelScope.launch {
            openProjectInternal(projectUri)
            checkCrashRecovery(projectUri, previousSessionDirty)
        }
    }

    // F022: surface a status-bar message from click handlers that have no ViewModel action yet.
    fun noteStatusMessage(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    fun saveSession() {
        val state = _uiState.value
        sessionRepository.save(
            projectUri      = state.projectRootUri,
            openTabUris     = state.openTabs.filter { !it.isBlank }.map { it.documentUri },
            activeTabUri    = state.openTabs.firstOrNull { it.isActive && !it.isBlank }?.documentUri,
            screenName      = state.currentScreen.name,
            cursorPositions = state.tabCursorPositions,
            scrollPositions = state.tabScrollPositions,
        )
    }

    /**
     * Save the current project's workspace state before switching to another project.
     * Called internally whenever openProjectInternal runs with a different URI.
     */
    private fun saveCurrentProjectSession() {
        val state = _uiState.value
        val projectUri = state.projectRootUri ?: return
        sessionRepository.saveTabsForProject(
            projectUri      = projectUri,
            openTabUris     = state.openTabs.filter { !it.isBlank }.map { it.documentUri },
            activeTabUri    = state.openTabs.firstOrNull { it.isActive && !it.isBlank }?.documentUri,
            cursorPositions = state.tabCursorPositions,
            scrollPositions = state.tabScrollPositions,
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
            tabSize                = settings.tabSize,
            wordWrap               = settings.wordWrap,
            lineNumbers            = settings.lineNumbers,
            fontSize               = settings.fontSize,
            renderWhitespace       = settings.renderWhitespace,
            minimapEnabled         = settings.minimapEnabled,
            scrollBeyondLastLine   = settings.scrollBeyondLastLine,
            cursorStyle            = settings.cursorStyle,
            bracketPairColorization= settings.bracketPairColorization,
            autoClosingBrackets    = settings.autoClosingBrackets,
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
        val currentState = _uiState.value
        if (treeUriString == currentState.projectRootUri) {
            navigateTo(AppScreen.EDITOR)
            return
        }
        if (currentState.openTabs.any { it.isDirty }) {
            _uiState.update {
                it.copy(
                    projectSwitchRequest = ProjectSwitchRequest(
                        projectUri  = treeUriString,
                        projectName = extractProjectName(treeUriString),
                    ),
                )
            }
            return
        }
        switchProjectNow(treeUriString)
    }

    fun cancelProjectSwitch() {
        _uiState.update { it.copy(projectSwitchRequest = null) }
    }

    fun discardAndSwitchProject() {
        val target = _uiState.value.projectSwitchRequest ?: return
        val currentProject = _uiState.value.projectRootUri
        if (currentProject != null) {
            crashRecovery.clearProject(currentProject)
        }
        _uiState.value.openTabs.forEach { pendingContent.remove(it.id) }
        switchProjectNow(target.projectUri)
    }

    fun saveAndSwitchProject() {
        val target = _uiState.value.projectSwitchRequest ?: return
        viewModelScope.launch {
            if (!saveDirtyTabsForProject()) return@launch
            switchProjectNow(target.projectUri)
        }
    }

    private fun switchProjectNow(treeUriString: String) {
        saveCurrentProjectSession()
        _uiState.update { it.copy(projectSwitchRequest = null) }
        viewModelScope.launch { openProjectInternal(treeUriString) }
    }

    private suspend fun saveDirtyTabsForProject(): Boolean {
        val state = _uiState.value
        val projectRootUri = state.projectRootUri
        for (tab in state.openTabs.filter { it.isDirty && !it.isBlank }) {
            val content = pendingContent[tab.id] ?: run {
                _uiState.update {
                    it.copy(statusMessage = "Save failed — draft content was not available")
                }
                return false
            }
            val ok = safRepository.writeFile(
                tab.documentUri,
                content.toByteArray(Charsets.UTF_8),
            )
            if (!ok) {
                _uiState.update { it.copy(statusMessage = "Save failed — project was not switched") }
                return false
            }
            pendingContent.remove(tab.id)
            if (projectRootUri != null) {
                crashRecovery.clearUnsavedContent(projectRootUri, tab.id)
            }
        }
        _uiState.update { current ->
            current.copy(
                openTabs = current.openTabs.map {
                    if (it.isDirty && !it.isBlank) it.copy(isDirty = false, isSaving = false) else it
                },
            )
        }
        return true
    }

    private suspend fun openProjectInternal(treeUriString: String) {
        val name = extractProjectName(treeUriString)
        // F019: dispose all Monaco models from the previous project before clearing
        // tabs — prevents stale models from leaking into the new project (same filename
        // in both projects would reuse the old model and show wrong content).
        sendEditorCommand(EditorOutbound.CloseAllModels)
        // Close tabs from the previous project; restore tabs for the new project.
        _uiState.value.openTabs.forEach { pendingContent.remove(it.id) }
        _uiState.update { it.copy(
            projectName    = name,
            projectRootUri = treeUriString,
            currentScreen  = AppScreen.EDITOR,
            openTabs       = emptyList(),
            activeTabId    = null,
            recoveryEntries = emptyList(),
        ) }
        projectRepository.upsert(Project(name = name, uri = treeUriString))
        _uiState.update { it.copy(recentProjects = projectRepository.getAll()) }
        val nodes = safRepository.listChildren(treeUriString)
        _uiState.update { it.copy(fileTree = nodes.sortedForTree()) }

        // Restore this project's workspace state.
        val tabUris   = sessionRepository.getOpenTabUrisForProject(treeUriString)
        val activeUri = sessionRepository.getActiveTabUriForProject(treeUriString)
        tabUris.forEach { uri ->
            openFileInternal(uri, markActive = uri == activeUri)
        }
        if (_uiState.value.activeTabId == null) {
            _uiState.value.openTabs.firstOrNull()?.id?.let(::selectTab)
        }
    }

    /**
     * Create a new blank project folder using [defaultProjectDir] from settings
     * (falling back to the app-specific external storage directory).
     *
     * The new project gets a minimal `package.json` template so it behaves like
     * a Node project out of the box.
     */
    fun createBlankProject(name: String) {
        val trimmed = name.trim().ifEmpty { "Project" }
        val settings = _uiState.value.editorSettings

        // Determine the parent directory.
        val projectsDir = when {
            settings.defaultProjectDir.isNotEmpty() -> File(settings.defaultProjectDir)
            else -> getApplication<Application>().getExternalFilesDir("Projects")
                ?: File(getApplication<Application>().filesDir, "projects")
        }
        projectsDir.mkdirs()

        val newDir = File(projectsDir, trimmed)
        if (!newDir.exists() && !newDir.mkdirs()) {
            _uiState.update { it.copy(statusMessage = "Could not create project folder") }
            return
        }

        // Write a minimal package.json template.
        val packageJson = File(newDir, "package.json")
        if (!packageJson.exists()) {
            packageJson.writeText(
                """{
  "name": "${trimmed.lowercase().replace(Regex("[^a-z0-9-]"), "-")}",
  "version": "1.0.0",
  "description": "",
  "main": "index.js",
  "scripts": {
    "start": "node index.js"
  },
  "keywords": [],
  "author": "",
  "license": "ISC"
}
""",
                Charsets.UTF_8,
            )
        }

        val newDirUri = Uri.fromFile(newDir).toString()
        openProject(newDirUri)
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
        saveCurrentProjectSession()
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
        viewModelScope.launch { refreshProjectNow() }
    }

    private suspend fun refreshProjectNow() {
        val rootUri = _uiState.value.projectRootUri ?: return
        when (val inspection = safRepository.inspectChildren(rootUri)) {
            is ChildrenInspectionResult.Success -> {
                val refreshed = inspection.children.sortedForTree()
                val merged = mergeRefreshedTree(_uiState.value.fileTree, refreshed)
                _uiState.update { it.copy(fileTree = merged) }
            }
            is ChildrenInspectionResult.Failed ->
                _uiState.update { it.copy(statusMessage = "Could not refresh the project tree") }
        }
    }

    /** Replace inspected metadata while retaining expansion and already-loaded children. */
    private suspend fun mergeRefreshedTree(
        previous: List<FileNode>,
        refreshed: List<FileNode>,
    ): List<FileNode> = refreshed.map { current ->
            val old = previous.firstOrNull { it.documentUri == current.documentUri }
            if (old != null && current.isDirectory) {
                val children = if (old.isExpanded) {
                    when (val inspection = safRepository.inspectChildren(current.documentUri)) {
                        is ChildrenInspectionResult.Success ->
                            mergeRefreshedTree(old.children, inspection.children.sortedForTree())
                        is ChildrenInspectionResult.Failed -> old.children
                    }
                } else {
                    old.children
                }
                current.copy(
                    children = children,
                    isExpanded = old.isExpanded,
                )
            } else current
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

    // ── File tree clipboard — supports multi-item ───────────────────────────

    /**
     * Copy [node] (or all selected nodes if in multi-select mode) to the clipboard.
     * Exits multi-select mode immediately so the user can navigate to a destination.
     */
    fun copyFileNode(node: FileNode) {
        val items = selectedNodesForOperation(node)
        _uiState.update { it.copy(
            clipboardItems  = items,
            clipboardIsCut  = false,
            isMultiSelectMode = false,
            selectedUris    = emptySet(),
        ) }
    }

    /**
     * Cut [node] (or all selected nodes if in multi-select mode) to the clipboard.
     * Exits multi-select mode immediately so the user can navigate to a destination.
     */
    fun cutFileNode(node: FileNode) {
        val items = selectedNodesForOperation(node)
        _uiState.update { it.copy(
            clipboardItems  = items,
            clipboardIsCut  = true,
            isMultiSelectMode = false,
            selectedUris    = emptySet(),
        ) }
    }

    fun clearClipboard() = _uiState.update { it.copy(clipboardItems = emptyList(), clipboardIsCut = false) }

    fun clearLocateRequest() {
        _uiState.update { it.copy(locateTargetUri = null) }
    }

    /**
     * Paste all items in [clipboardItems] into [targetDir].
     * Supports cut (move) and copy. Works within a project, across projects,
     * into the project root, and into nested folders.
     */
    fun pasteFileNode(targetDir: FileNode) {
        val items  = _uiState.value.clipboardItems
        val isCut  = _uiState.value.clipboardIsCut
        if (items.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            items.forEach { source ->
                if (source.isDirectory && (source.documentUri == targetDir.documentUri || containsDocumentUri(source, targetDir.documentUri))) {
                    _uiState.update { it.copy(statusMessage = "Cannot paste a folder into itself or one of its subfolders") }
                    return@forEach
                }
                if (isCut) {
                    val sourceParent = source.parentDocumentUri ?: run {
                        _uiState.update { it.copy(statusMessage = "Move failed: unknown parent for ${source.displayName}") }
                        return@forEach
                    }
                    val moved = safRepository.moveDocumentWithExactName(source.documentUri, sourceParent, targetDir.documentUri)
                    val newUri = (moved as? SafeMutationResult.Created)?.documentUri ?: run {
                        val reason = when (moved) {
                            SafeMutationResult.Duplicate -> "already exists in the destination"
                            SafeMutationResult.InspectionFailed -> "destination could not be inspected"
                            else -> "provider rejected the operation"
                        }
                        _uiState.update { it.copy(statusMessage = "Move failed for ${source.displayName}: $reason") }
                        return@forEach
                    }
                    reconcileOpenTabReference(source.documentUri, newUri)
                    successCount++
                } else {
                    val copied = safRepository.copyDocumentWithExactName(source.documentUri, targetDir.documentUri)
                    if (copied !is SafeMutationResult.Created) {
                        val reason = when (copied) {
                            SafeMutationResult.Duplicate -> "already exists in the destination"
                            SafeMutationResult.InspectionFailed -> "destination could not be inspected"
                            else -> "provider rejected the operation"
                        }
                        _uiState.update { it.copy(statusMessage = "Copy failed for ${source.displayName}: $reason") }
                        return@forEach
                    }
                    successCount++
                }
            }
            refreshProjectNow()
            val verb = if (isCut) "Moved" else "Copied"
            _uiState.update { it.copy(
                clipboardItems = emptyList(),
                clipboardIsCut = false,
                statusMessage  = "$verb $successCount item(s)",
            ) }
        }
    }

    private fun containsDocumentUri(node: FileNode, documentUri: String): Boolean =
        node.children.any { it.documentUri == documentUri || containsDocumentUri(it, documentUri) }

    // ── Import / Export ────────────────────────────────────────────────────

    fun importFiles(targetDirUri: String, sourceUris: List<String>) {
        viewModelScope.launch {
            var count = 0
            sourceUris.forEach { uri ->
                val name  = safRepository.getDisplayName(uri)
                    ?: uri.substringAfterLast('/', "imported_file")
                when (safRepository.copyDocumentWithExactName(uri, targetDirUri, name)) {
                    is SafeMutationResult.Created -> count++
                    else -> Unit
                }
            }
            refreshProjectNow()
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
        // C011: single-tap opens a temporary (preview) tab.
        viewModelScope.launch { openFileInternal(documentUri, markActive = true, temporary = true) }
    }

    /** C011: double-tap on a file tree item opens (or upgrades) a permanent tab. */
    fun openFilePermanent(documentUri: String) {
        viewModelScope.launch { openFileInternal(documentUri, markActive = true, temporary = false) }
    }

    private suspend fun openFileInternal(
        documentUri: String,
        markActive: Boolean,
        temporary: Boolean = false,
    ) {
        val existing = _uiState.value.openTabs.find { it.documentUri == documentUri }
        if (existing != null) {
            // C011: pinning action (temporary=false) upgrades a preview tab to permanent.
            if (!temporary && existing.isTemporary) pinTab(existing.id)
            if (markActive) selectTab(existing.id)
            return
        }

        // C011: single-tap replaces any existing temporary (preview) tab before opening a new one.
        if (temporary) {
            val oldTemp = _uiState.value.openTabs.firstOrNull { it.isTemporary }
            if (oldTemp != null) {
                pendingContent.remove(oldTemp.id)
                _uiState.update { state ->
                    val remaining  = state.openTabs.filter { it.id != oldTemp.id }
                    val newActive  = if (state.activeTabId == oldTemp.id) remaining.lastOrNull()?.id else state.activeTabId
                    state.copy(
                        openTabs    = remaining.map { it.copy(isActive = it.id == newActive) },
                        activeTabId = newActive,
                    )
                }
            }
        }

        val bytes = safRepository.readFile(documentUri) ?: return

        // F005-A: size guard — reject files larger than 5 MB to prevent OOM in
        // Monaco and the Kotlin string allocation that precedes it.
        if (bytes.size > 5 * 1024 * 1024) {
            _uiState.update { it.copy(statusMessage = "Cannot open: file is ${bytes.size / 1_048_576} MB (5 MB limit)") }
            return
        }
        val displayName = _uiState.value.fileTree.findNode(documentUri)?.displayName
            ?: safRepository.getDisplayName(documentUri)
            ?: displayNameFromUri(documentUri)
        // F005-B: binary detection — scan the first 8 KB for null bytes.
        // Binary content (.apk, .class, compiled assets) corrupts Monaco's text model.
        if (bytes.take(8192).any { it == 0.toByte() }) {
            _uiState.update { it.copy(fileOpDialog = FileOpDialog.BinaryOpenError(displayName)) }
            return
        }

        val content = String(bytes, Charsets.UTF_8)
        val language = EditorLanguageRegistry.languageForFileName(displayName)

        val newTab = EditorTab(
            documentUri = documentUri,
            displayName = displayName,
            language    = language,
            content     = content,
            isActive    = markActive,
            isTemporary = temporary,
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
                hasEditorSelection = false,
            )
        }
    }

    fun selectTab(tabId: String) {
        _uiState.update { state ->
            state.copy(
                openTabs    = state.openTabs.map { it.copy(isActive = it.id == tabId) },
                activeTabId = tabId,
                hasEditorSelection = false,
            )
        }
    }

    /** C011: make a preview tab permanent so it survives the next single-tap. */
    fun pinTab(tabId: String) {
        _uiState.update { state ->
            state.copy(openTabs = state.openTabs.map {
                if (it.id == tabId) it.copy(isTemporary = false) else it
            })
        }
    }

    /** Close a tab immediately, discarding unsaved changes without confirmation. */
    fun closeTab(tabId: String) {
        val tab = _uiState.value.openTabs.find { it.id == tabId }
        pendingContent.remove(tabId)
        _uiState.value.projectRootUri?.let { projectRootUri ->
            crashRecovery.clearUnsavedContent(projectRootUri, tabId)
        }
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

    fun closeTabSafe(tabId: String) {
        val tab = _uiState.value.openTabs.find { it.id == tabId } ?: return
        if (tab.isDirty) {
            _uiState.update { it.copy(fileOpDialog = FileOpDialog.UnsavedClose(tabId, tab.displayName)) }
        } else {
            closeTab(tabId)
        }
    }

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
                _uiState.value.projectRootUri?.let { projectRootUri ->
                    crashRecovery.clearUnsavedContent(projectRootUri, tabId)
                }
            }
            closeTab(tabId)
        }
    }

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
        viewModelScope.launch {
            if (!saveDirtyTabsForProject()) return@launch
            _uiState.update { it.copy(showExitConfirmation = false) }
            crashRecovery.markCleanExit()
            onReady()
        }
    }

    // ── Editor bridge ──────────────────────────────────────────────────────

    fun onEditorReady() {
        _uiState.update {
            it.copy(
                isEditorReady    = true,
                editorBindRevision = it.editorBindRevision + 1,
            )
        }
        val settings = _uiState.value.editorSettings
        sendEditorCommand(EditorOutbound.SetEditorOptions(
            tabSize                = settings.tabSize,
            wordWrap               = settings.wordWrap,
            lineNumbers            = settings.lineNumbers,
            fontSize               = settings.fontSize,
            renderWhitespace       = settings.renderWhitespace,
            minimapEnabled         = settings.minimapEnabled,
            scrollBeyondLastLine   = settings.scrollBeyondLastLine,
            cursorStyle            = settings.cursorStyle,
            bracketPairColorization= settings.bracketPairColorization,
            autoClosingBrackets    = settings.autoClosingBrackets,
        ))
        sendEditorCommand(EditorOutbound.SetTheme(resolveMonacoTheme()))
        sendEditorCommand(EditorOutbound.ForceLayout)
    }

    /** Marks the native editor as unavailable until the WebView sends Ready again. */
    fun onEditorRendererGone() {
        _uiState.update {
            it.copy(
                isEditorReady    = false,
                editorBindRevision = it.editorBindRevision + 1,
            )
        }
    }

    /** Returns the latest draft when Monaco needs to be rebound after a layout change. */
    fun editorContentForTab(tabId: String, fallback: String?): String =
        pendingContent[tabId] ?: fallback.orEmpty()

    fun onEditorMessage(message: EditorInbound) {
        when (message) {
            is EditorInbound.Ready -> onEditorReady()

            is EditorInbound.ContentChanged -> {
                val tab = _uiState.value.openTabs.find { it.documentUri == message.path } ?: return
                pendingContent[tab.id] = message.content
                _uiState.update { state ->
                    state.copy(openTabs = state.openTabs.map {
                        // C011: first edit pins the preview tab permanently
                        if (it.id == tab.id) it.copy(isDirty = true, isTemporary = false) else it
                    })
                }
                val projectRootUri = _uiState.value.projectRootUri ?: return
                crashRecovery.saveUnsavedContent(
                    projectRootUri = projectRootUri,
                    tabId          = tab.id,
                    documentUri    = tab.documentUri,
                    displayName    = tab.displayName,
                    content        = message.content,
                )
                if (_uiState.value.editorSettings.autoSave) {
                    saveFile(message.path)
                }
            }

            is EditorInbound.CursorMoved -> {
                _uiState.update { state ->
                    val activeUri = state.openTabs.firstOrNull { it.isActive }?.documentUri
                    val updatedCursors = if (activeUri != null)
                        state.tabCursorPositions + (activeUri to Pair(message.line, message.column))
                    else state.tabCursorPositions
                    state.copy(
                        cursorLine         = message.line,
                        cursorColumn       = message.column,
                        tabCursorPositions = updatedCursors,
                    )
                }
            }

            is EditorInbound.SelectionChanged ->
                _uiState.update { it.copy(hasEditorSelection = message.hasSelection) }

            is EditorInbound.FileSaved -> saveFile(message.path)

            // F016: Monaco posted selected text; write it to the Android clipboard.
            // isCut=true means Monaco has already deleted the selection — nothing else
            // to do on the Kotlin side after placing the text in the clipboard.
            is EditorInbound.TextCopied -> {
                val clipboard = getApplication<Application>()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("code", message.text))
            }

            is EditorInbound.ScrollPositionReport -> {
                _uiState.update { state ->
                    val activeUri = state.openTabs.firstOrNull { it.isActive }?.documentUri
                    val updatedScrolls = if (activeUri != null)
                        state.tabScrollPositions + (activeUri to message.scrollTop)
                    else state.tabScrollPositions
                    state.copy(tabScrollPositions = updatedScrolls)
                }
            }
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
                _uiState.value.projectRootUri?.let { projectRootUri ->
                    crashRecovery.clearUnsavedContent(projectRootUri, tab.id)
                }
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
            val newLang = EditorLanguageRegistry.languageForFileName(newName)
            pendingContent.remove(active.id)
            _uiState.value.projectRootUri?.let { projectRootUri ->
                crashRecovery.clearUnsavedContent(projectRootUri, active.id)
            }
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

    // ── Run — project-scoped entry point ──────────────────────────────────
    //
    // requestRun is the single entry point for the Run action.
    // It is PROJECT-scoped, not file-scoped.
    //
    // Phase 1 dispatch:
    //   HTML and Markdown files → preview provider (togglePreview).
    //   All other file types   → status message; no crash.
    //
    // Phase 2+ extensibility:
    //   Add new when-branches here for live server, terminal execution, etc.
    //   Each branch should launch its provider safely (coroutine + runCatching).
    //   The Run action must never terminate the application under any failure.
    fun requestRun() {
        val state  = _uiState.value
        val active = state.openTabs.firstOrNull { it.isActive }

        // Active file is directly previewable — just toggle the preview.
        if (active != null && active.language in listOf("html", "markdown")) {
            togglePreview()
            return
        }

        // F006: project-scoped run — search the in-memory (expanded) file tree for
        // the first .html file.  When found, open it as the active tab so the user
        // can press Run once more to preview it.  This covers the common case where
        // a .css or .js file is active inside an HTML project.
        val htmlNode = findFirstHtmlNode(state.fileTree)
        if (htmlNode != null) {
            viewModelScope.launch {
                openFileInternal(htmlNode.documentUri, markActive = true, temporary = true)
                _uiState.update {
                    it.copy(statusMessage = "Opened ${htmlNode.displayName} — press Run to preview")
                }
            }
            return
        }

        _uiState.update {
            it.copy(statusMessage = when {
                active == null              -> "Open a file to run or preview"
                state.projectRootUri == null -> "Open a project first"
                else -> "No HTML or Markdown files found — open one to preview"
            })
        }
    }

    /** Recursively searches [nodes] for the first non-directory .html file. */
    private fun findFirstHtmlNode(nodes: List<FileNode>): FileNode? {
        for (node in nodes) {
            if (!node.isDirectory && node.displayName.endsWith(".html", ignoreCase = true)) return node
            if (node.isDirectory) { findFirstHtmlNode(node.children)?.let { return it } }
        }
        return null
    }

    // ── Preview provider ───────────────────────────────────────────────────
    //
    // Called by requestRun for HTML and Markdown files.
    // Runs in a viewModelScope coroutine so the main thread is never blocked.
    // Markdown rendering is offloaded to Dispatchers.Default.
    // All failure paths surface a user-facing statusMessage — the app never
    // crashes due to an exception inside this function.
    fun togglePreview() {
        viewModelScope.launch {
            // Hide preview if already visible.
            if (_uiState.value.isPreviewVisible) {
                _uiState.update { it.copy(isPreviewVisible = false) }
                return@launch
            }
            val active = _uiState.value.openTabs.firstOrNull { it.isActive }
            if (active == null) {
                _uiState.update { it.copy(statusMessage = "No active file to preview") }
                return@launch
            }
            val content = pendingContent[active.id] ?: active.content.orEmpty()

            // Generate HTML on the Default dispatcher — never blocks the main thread.
            // runCatching contains any exception from markdownToPreviewHtml or any
            // future provider; the user sees a status message instead of a crash.
            val htmlResult = runCatching {
                withContext(Dispatchers.Default) {
                    when (active.language) {
                        "html"     -> content
                        "markdown" -> markdownToPreviewHtml(content)
                        else       -> null
                    }
                }
            }

            val htmlContent = htmlResult.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        statusMessage = "Preview failed: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
                return@launch
            } ?: return@launch   // null = unsupported language (already handled by requestRun)

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
                        relativePath = "/$nodePath",
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

    fun revealActiveFile() {
        val activeTab = _uiState.value.openTabs.firstOrNull { it.isActive } ?: run {
            _uiState.update { it.copy(statusMessage = "No active file") }
            return
        }
        val activeUri = activeTab.documentUri
        val activeDisplayName = activeTab.displayName
        val rootUri = _uiState.value.projectRootUri ?: run {
            _uiState.update { it.copy(statusMessage = "No project is open") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Locating active file…") }
            val cachedPath = findCachedAncestorPath(_uiState.value.fileTree, activeUri)
            if (cachedPath != null) {
                _uiState.update { state ->
                    var tree = state.fileTree
                    cachedPath.forEach { directoryUri ->
                        val node = tree.findNode(directoryUri)
                        if (node != null && node.isDirectory && !node.isExpanded) {
                            tree = tree.toggleExpanded(directoryUri)
                        }
                    }
                    state.copy(
                        fileTree = tree,
                        locateTargetUri = activeUri,
                        locateRequestToken = state.locateRequestToken + 1,
                        statusMessage = "Located $activeDisplayName",
                    )
                }
                return@launch
            }
            when (val result = discoverPathFromRoot(rootUri, activeUri)) {
                is LocateResult.Found -> {
                    var tree = _uiState.value.fileTree
                    result.discoveredChildren[rootUri]?.let {
                        tree = mergeTreeChildren(tree, it.sortedForTree())
                    }
                    result.discoveredChildren
                        .filterKeys { it != rootUri }
                        .forEach { (parentUri, children) ->
                            tree = tree.setChildren(parentUri, children.sortedForTree())
                        }
                    _uiState.update { state ->
                        state.copy(
                            fileTree = tree,
                            locateTargetUri = activeUri,
                            locateRequestToken = state.locateRequestToken + 1,
                            statusMessage = "Located ${result.displayName}",
                        )
                    }
                }
                LocateResult.NotFound ->
                    _uiState.update { it.copy(statusMessage = "Active file was not found in the project") }
                is LocateResult.Failed ->
                    _uiState.update { it.copy(statusMessage = "Could not inspect the project while locating the file") }
            }
        }
    }

    private fun findCachedAncestorPath(
        nodes: List<FileNode>,
        targetUri: String,
        ancestors: List<String> = emptyList(),
    ): List<String>? {
        for (node in nodes) {
            if (node.documentUri == targetUri) return ancestors
            if (node.isDirectory && node.children.isNotEmpty()) {
                val found = findCachedAncestorPath(
                    nodes = node.children,
                    targetUri = targetUri,
                    ancestors = ancestors + node.documentUri,
                )
                if (found != null) return found
            }
        }
        return null
    }

    private fun mergeTreeChildren(
        previous: List<FileNode>,
        refreshed: List<FileNode>,
    ): List<FileNode> = refreshed.map { current ->
        val old = previous.firstOrNull { it.documentUri == current.documentUri }
        if (old != null && current.isDirectory) {
            current.copy(
                children = old.children,
                isExpanded = old.isExpanded,
            )
        } else {
            current
        }
    }.sortedForTree()

    private sealed class LocateResult {
        data class Found(
            val displayName: String,
            val discoveredChildren: LinkedHashMap<String, List<FileNode>>,
        ) : LocateResult()
        data object NotFound : LocateResult()
        data object Failed : LocateResult()
    }

    private suspend fun discoverPathFromRoot(rootUri: String, targetUri: String): LocateResult {
        val visited = mutableSetOf<String>()
        var inspectionFailed = false

        suspend fun visit(directoryUri: String): LocateResult {
            if (!visited.add(directoryUri)) return LocateResult.NotFound
            val children = when (val inspection = safRepository.inspectChildren(directoryUri)) {
                is ChildrenInspectionResult.Success -> inspection.children
                is ChildrenInspectionResult.Failed -> {
                    inspectionFailed = true
                    return LocateResult.NotFound
                }
            }
            children.firstOrNull { it.documentUri == targetUri }?.let { target ->
                return LocateResult.Found(
                    displayName = target.displayName,
                    discoveredChildren = linkedMapOf(directoryUri to children),
                )
            }
            for (child in children) {
                if (!child.isDirectory) continue
                when (val nested = visit(child.documentUri)) {
                    is LocateResult.Found -> {
                        val pathChildren = linkedMapOf<String, List<FileNode>>()
                        pathChildren[directoryUri] = children
                        pathChildren.putAll(nested.discoveredChildren)
                        return LocateResult.Found(nested.displayName, pathChildren)
                    }
                    is LocateResult.Failed -> Unit
                    LocateResult.NotFound -> Unit
                }
            }
            return if (inspectionFailed) LocateResult.Failed else LocateResult.NotFound
        }

        return visit(rootUri)
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
            // Enter selection mode automatically on first selection; exit when all deselected.
            state.copy(isMultiSelectMode = updated.isNotEmpty(), selectedUris = updated)
        }
    }

    /**
     * Resolve the current multi-selection for a destructive or clipboard operation.
     * If a selected folder contains another selected node, only the folder is kept;
     * this prevents duplicate copy/cut/delete requests for the same subtree.
     */
    private fun selectedNodesForOperation(fallback: FileNode): List<FileNode> {
        val state = _uiState.value
        val candidates = if (state.isMultiSelectMode && state.selectedUris.isNotEmpty()) {
            state.selectedUris.mapNotNull { state.fileTree.findNode(it) }
        } else {
            listOf(fallback)
        }
        val selectedUris = candidates.map { it.documentUri }.toSet()
        return candidates.filter { node ->
            var parentUri = node.parentDocumentUri
            var hasSelectedAncestor = false
            while (parentUri != null) {
                if (parentUri in selectedUris) {
                    hasSelectedAncestor = true
                    break
                }
                parentUri = state.fileTree.findNode(parentUri)?.parentDocumentUri
            }
            !hasSelectedAncestor
        }
    }

    private fun affectedUris(node: FileNode): Set<String> = buildSet {
        add(node.documentUri)
        node.children.forEach { addAll(affectedUris(it)) }
    }

    private fun reconcileOpenTabReference(oldUri: String, newUri: String, newName: String? = null) {
        val rootUri = _uiState.value.projectRootUri
        val tabs = _uiState.value.openTabs.filter { it.documentUri == oldUri }
        _uiState.update { state ->
            state.copy(openTabs = state.openTabs.map { tab ->
                if (tab.documentUri == oldUri) {
                    tab.copy(documentUri = newUri, displayName = newName ?: tab.displayName)
                } else tab
            })
        }
        if (rootUri != null) {
            tabs.forEach { tab ->
                pendingContent[tab.id]?.let { content ->
                    crashRecovery.saveUnsavedContent(
                        rootUri,
                        tab.id,
                        newUri,
                        newName ?: tab.displayName,
                        content,
                    )
                }
            }
        }
    }

    // ── Clipboard path copy ────────────────────────────────────────────────

    /**
     * Copy the absolute display path of [documentUri] to the system clipboard.
     * Paths always start with "/" (e.g. "/src/pages/home.html").
     */
    fun copyPathToClipboard(documentUri: String) {
        val path = _uiState.value.fileTree.pathTo(documentUri)
            ?: run {
                // pathTo returns null for root-level nodes; decode URI as fallback.
                val raw = Uri.decode(documentUri).substringAfterLast('/')
                "/$raw"
            }
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("File Path", path))
        _uiState.update { it.copy(statusMessage = "Path copied: $path") }
    }

    /**
     * Read the Android clipboard text and insert it into Monaco as a text operation.
     * This avoids the WebView clipboard API (which requires permission and is slow).
     */
    fun pasteFromKotlinClipboard() {
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(getApplication())?.toString()
        if (!text.isNullOrEmpty()) {
            sendEditorCommand(EditorOutbound.InsertText(text))
        }
    }

    // ── Remove project (with confirmation) ────────────────────────────────

    fun requestRemoveProject(uri: String) {
        _uiState.update { it.copy(confirmRemoveProjectUri = uri) }
    }

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

    fun cancelRemoveProject() {
        _uiState.update { it.copy(confirmRemoveProjectUri = null) }
    }

    // ── File operations ────────────────────────────────────────────────────

    fun showRenameDialog(node: FileNode)         = _uiState.update { it.copy(fileOpDialog = FileOpDialog.Rename(node)) }
    fun showDeleteDialog(node: FileNode)         = _uiState.update {
        it.copy(fileOpDialog = FileOpDialog.Delete(node, selectedNodesForOperation(node)))
    }
    fun showCreateFileDialog(parent: FileNode)   = _uiState.update { it.copy(fileOpDialog = FileOpDialog.CreateFile(parent)) }
    fun showCreateFolderDialog(parent: FileNode) = _uiState.update { it.copy(fileOpDialog = FileOpDialog.CreateFolder(parent)) }
    fun showDuplicateDialog(node: FileNode)      = _uiState.update { it.copy(fileOpDialog = FileOpDialog.Duplicate(node)) }
    fun dismissFileOpDialog()                    = _uiState.update { it.copy(fileOpDialog = null) }

    fun renameNode(node: FileNode, newName: String) {
        val rootUri = _uiState.value.projectRootUri ?: run {
            _uiState.update { it.copy(statusMessage = "No project open") }
            return
        }
        val sourceParentUri = node.parentDocumentUri ?: rootUri
        val input = newName.trim()
        val baseSegments = if (input.startsWith('/') || input.startsWith('\\')) {
            emptyList()
        } else {
            projectRelativeSegments(sourceParentUri, rootUri) ?: run {
                _uiState.update { it.copy(statusMessage = "Could not determine the current folder") }
                return
            }
        }
        when (val normalized = normalizeProjectPath(input, baseSegments)) {
            NormalizedPathResult.AboveProjectRoot ->
                _uiState.update { it.copy(statusMessage = "The path cannot go above the project root") }
            NormalizedPathResult.MissingFinalName,
            NormalizedPathResult.InvalidComponent ->
                _uiState.update { it.copy(statusMessage = "Enter a valid rename path") }
            is NormalizedPathResult.Success -> viewModelScope.launch {
                when (val resolved = safRepository.resolveOrCreatePathSafely(rootUri, normalized.segments)) {
                    is PathResolutionResult.Resolved -> {
                        val result = safRepository.moveAndRenameDocumentWithExactName(
                            sourceUriString = node.documentUri,
                            sourceParentUriString = sourceParentUri,
                            targetParentUriString = resolved.parentUri,
                            newName = resolved.leafName,
                        )
                        when (result) {
                            is SafeMutationResult.Created -> {
                                refreshProjectNow()
                                reconcileOpenTabReference(node.documentUri, result.documentUri, resolved.leafName)
                                _uiState.update { state ->
                                    state.copy(fileOpDialog = null, statusMessage = "Renamed to ${resolved.leafName}")
                                }
                            }
                            SafeMutationResult.Duplicate -> {
                                safRepository.rollbackCreatedDirectories(resolved.createdIntermediateUris)
                                _uiState.update { state ->
                                    state.copy(fileOpDialog = (state.fileOpDialog as? FileOpDialog.Rename)?.copy(errorMessage = "\u201c${resolved.leafName}\u201d already exists"))
                                }
                            }
                            SafeMutationResult.InspectionFailed -> {
                                safRepository.rollbackCreatedDirectories(resolved.createdIntermediateUris)
                                _uiState.update { it.copy(statusMessage = "Could not inspect the rename destination") }
                            }
                            SafeMutationResult.Failed -> {
                                safRepository.rollbackCreatedDirectories(resolved.createdIntermediateUris)
                                _uiState.update { it.copy(statusMessage = "Rename failed") }
                            }
                        }
                    }
                    is PathResolutionResult.BlockedByFile -> {
                        safRepository.rollbackCreatedDirectories(resolved.createdIntermediateUris)
                        _uiState.update { it.copy(statusMessage = "A file blocks part of the rename path") }
                    }
                    is PathResolutionResult.IntermediateCreationFailed -> {
                        safRepository.rollbackCreatedDirectories(resolved.createdIntermediateUris)
                        _uiState.update { it.copy(statusMessage = "Could not create the rename path") }
                    }
                    is PathResolutionResult.IntermediateNameMismatch -> {
                        safRepository.rollbackCreatedDirectories(resolved.createdIntermediateUris)
                        _uiState.update { it.copy(statusMessage = "A rename folder could not be created exactly") }
                    }
                    PathResolutionResult.EmptyPath -> _uiState.update { it.copy(statusMessage = "Could not resolve the rename path") }
                }
            }
        }
    }

    fun deleteNode(node: FileNode, selectedNodes: List<FileNode> = emptyList()) {
        val nodes = if (selectedNodes.isEmpty()) listOf(node) else selectedNodes
        val allAffectedUris = nodes.flatMap { affectedUris(it) }.toSet()
        viewModelScope.launch {
            var deletedCount = 0
            val deletedUris = mutableSetOf<String>()
            nodes.forEach { selectedNode ->
                if (safRepository.deleteDocument(selectedNode.documentUri)) {
                    deletedCount++
                    deletedUris += allAffectedUris.intersect(affectedUris(selectedNode))
                }
            }
            _uiState.value.openTabs
                .filter { it.documentUri in deletedUris }
                .forEach { closeTab(it.id) }
            refreshProjectNow()
            _uiState.update { it.copy(
                fileOpDialog = null,
                isMultiSelectMode = false,
                selectedUris = emptySet(),
                statusMessage = if (deletedCount == nodes.size) {
                    "Deleted $deletedCount item(s)"
                } else {
                    "Deleted $deletedCount of ${nodes.size} item(s)"
                },
            ) }
        }
    }

    fun createFileInDirectory(parentNode: FileNode, name: String) {
        createEntryFromInput(parentNode, name, isDirectory = false)
    }

    fun createFolderInDirectory(parentNode: FileNode, name: String) {
        createEntryFromInput(parentNode, name, isDirectory = true)
    }

    fun createFileAtRoot(name: String) {
        val rootUri = _uiState.value.projectRootUri ?: run {
            _uiState.update { it.copy(statusMessage = "No project open") }
            return
        }
        createEntryFromInput(
            FileNode(rootUri, "", "vnd.android.document/directory"),
            name,
            isDirectory = false,
        )
    }

    fun createFolderAtRoot(name: String) {
        val rootUri = _uiState.value.projectRootUri ?: run {
            _uiState.update { it.copy(statusMessage = "No project open") }
            return
        }
        createEntryFromInput(
            FileNode(rootUri, "", "vnd.android.document/directory"),
            name,
            isDirectory = true,
        )
    }

    private fun createEntryFromInput(parentNode: FileNode, rawName: String, isDirectory: Boolean) {
        val rootUri = _uiState.value.projectRootUri
        if (rootUri == null) {
            setCreateError(isDirectory, "No project is open")
            return
        }
        val baseSegments = if (rawName.trim().startsWith('/') || rawName.trim().startsWith('\\')) {
            emptyList()
        } else {
            projectRelativeSegments(parentNode.documentUri, rootUri)
                ?: run {
                    setCreateError(isDirectory, "Could not determine the selected folder")
                    return
                }
        }
        when (val normalized = normalizeProjectPath(rawName, baseSegments)) {
            is NormalizedPathResult.Success -> {
                markCreateSubmitting(isDirectory)
                viewModelScope.launch {
                    when (val resolved = safRepository.resolveOrCreatePathSafely(rootUri, normalized.segments)) {
                        is PathResolutionResult.Resolved -> createEntry(
                            parentUri = resolved.parentUri,
                            name = resolved.leafName,
                            isDirectory = isDirectory,
                            openFileAfterCreate = !isDirectory,
                            createdIntermediateUris = resolved.createdIntermediateUris,
                            submittingAlreadyMarked = true,
                        )
                        PathResolutionResult.EmptyPath -> setCreateError(isDirectory, "Enter a file or folder name")
                        is PathResolutionResult.BlockedByFile -> {
                            safRepository.rollbackCreatedDirectories(resolved.createdIntermediateUris)
                            setCreateError(isDirectory, "A file blocks part of this path")
                        }
                        is PathResolutionResult.IntermediateCreationFailed -> {
                            safRepository.rollbackCreatedDirectories(resolved.createdIntermediateUris)
                            setCreateError(isDirectory, "Could not create the required folders")
                        }
                        is PathResolutionResult.IntermediateNameMismatch -> {
                            safRepository.rollbackCreatedDirectories(resolved.createdIntermediateUris)
                            setCreateError(isDirectory, "A required folder could not be created with its exact name")
                        }
                    }
                }
            }
            NormalizedPathResult.AboveProjectRoot ->
                setCreateError(isDirectory, "The path cannot go above the project root")
            NormalizedPathResult.MissingFinalName ->
                setCreateError(isDirectory, "Enter a file or folder name")
            NormalizedPathResult.InvalidComponent ->
                setCreateError(isDirectory, "Enter a valid file or folder path")
        }
    }

    private fun projectRelativeSegments(documentUri: String, rootUri: String): List<String>? {
        if (documentUri == rootUri) return emptyList()
        fun find(nodes: List<FileNode>, prefix: List<String>): List<String>? {
            for (node in nodes) {
                val next = prefix + node.displayName
                if (node.documentUri == documentUri) return next
                if (node.isDirectory) {
                    val found = find(node.children, next)
                    if (found != null) return found
                }
            }
            return null
        }
        return find(_uiState.value.fileTree, emptyList())
    }

    private fun createEntry(
        parentUri: String,
        name: String,
        isDirectory: Boolean,
        openFileAfterCreate: Boolean,
        createdIntermediateUris: List<String> = emptyList(),
        submittingAlreadyMarked: Boolean = false,
    ) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank() || normalizedName.contains('/') || normalizedName.contains('\\')) {
            setCreateError(isDirectory, "Enter a valid name without path separators")
            return
        }
        if (!submittingAlreadyMarked) markCreateSubmitting(isDirectory)
        viewModelScope.launch {
            val result = safRepository.createFileWithExactName(
                parentUriString = parentUri,
                displayName = normalizedName,
                mimeType = if (isDirectory) {
                    "vnd.android.document/directory"
                } else {
                    EditorLanguageRegistry.mimeTypeForFileName(normalizedName)
                },
            )
            when (result) {
                ExactCreateResult.Duplicate -> {
                    safRepository.rollbackCreatedDirectories(createdIntermediateUris)
                    setCreateError(
                        isDirectory,
                        "A ${if (isDirectory) "folder" else "file"} already exist with this name. Use a different name",
                    )
                }
                ExactCreateResult.InspectionFailed -> {
                    safRepository.rollbackCreatedDirectories(createdIntermediateUris)
                    setCreateError(isDirectory, "Could not inspect the target folder")
                }
                ExactCreateResult.Failed -> {
                    safRepository.rollbackCreatedDirectories(createdIntermediateUris)
                    setCreateError(isDirectory, "Could not create ${if (isDirectory) "folder" else "file"}")
                }
                is ExactCreateResult.Created -> {
                    EditorLanguageRegistry.templateForFileName(normalizedName)?.let { template ->
                        if (!safRepository.writeFile(result.documentUri, template.toByteArray(Charsets.UTF_8))) {
                            safRepository.deleteDocument(result.documentUri)
                            safRepository.rollbackCreatedDirectories(createdIntermediateUris)
                            setCreateError(isDirectory, "Could not initialize the HTML file")
                            return@launch
                        }
                    }
                    refreshProjectNow()
                    _uiState.update { state ->
                        state.copy(fileOpDialog = null, statusMessage = "Created $normalizedName")
                    }
                    if (openFileAfterCreate) openFile(result.documentUri)
                }
            }
        }
    }

    private fun markCreateSubmitting(isDirectory: Boolean) {
        _uiState.update { state ->
            val dialog = if (isDirectory) {
                (state.fileOpDialog as? FileOpDialog.CreateFolder)?.copy(errorMessage = null, isSubmitting = true)
            } else {
                (state.fileOpDialog as? FileOpDialog.CreateFile)?.copy(errorMessage = null, isSubmitting = true)
            }
            state.copy(fileOpDialog = dialog ?: state.fileOpDialog)
        }
    }

    private fun setCreateError(isDirectory: Boolean, message: String) {
        _uiState.update { state ->
            val dialog = if (isDirectory) {
                (state.fileOpDialog as? FileOpDialog.CreateFolder)?.copy(errorMessage = message, isSubmitting = false)
            } else {
                (state.fileOpDialog as? FileOpDialog.CreateFile)?.copy(errorMessage = message, isSubmitting = false)
            }
            state.copy(fileOpDialog = dialog ?: state.fileOpDialog, statusMessage = message)
        }
    }

    fun duplicateFile(node: FileNode, newName: String) {
        val parentUri = node.parentDocumentUri ?: run {
            _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Cannot duplicate: no parent") }
            return
        }
        // F007: reject duplicate name in the same parent directory.
        val parentSiblings = _uiState.value.fileTree.findNode(parentUri)?.children
            ?: _uiState.value.fileTree
        if (parentSiblings.any { it.displayName.equals(newName, ignoreCase = true) }) {
            _uiState.update { state ->
                state.copy(fileOpDialog = (state.fileOpDialog as? FileOpDialog.Duplicate)?.copy(errorMessage = "\u201c$newName\u201d already exists in this folder"))
            }
            return
        }
        viewModelScope.launch {
            val newUri = (safRepository.copyDocumentWithExactName(node.documentUri, parentUri, newName) as? SafeMutationResult.Created)?.documentUri ?: run {
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Duplicate: create error") }
                return@launch
            }
            refreshProjectNow()
            openFilePermanent(newUri)
            _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Duplicated as $newName") }
        }
    }

    // ── F003: SAF-backed path navigator ───────────────────────────────────────

    /**
     * Load the immediate children of [parentUri] directly from SAF.
     * Called by IdeTopBar's path-navigator dropdown, so it always returns
     * live data regardless of which tree nodes are expanded.
     */
    suspend fun loadNavChildren(parentUri: String): List<FileNode> =
        safRepository.listChildren(parentUri)

    // ── F004: Project-relative Save As ────────────────────────────────────────

    /** Open the inline Save-As dialog pre-filled with the active tab name. */
    fun showSaveAsDialog() {
        val name = _uiState.value.openTabs.firstOrNull { it.isActive }?.displayName ?: "untitled"
        _uiState.update { it.copy(fileOpDialog = FileOpDialog.SaveAs(name)) }
    }

    /**
     * Write active file content to [relativePath] within the project root.
     * Supports "newname.kt" (creates in root) or "src/utils/Foo.kt" (creates
     * intermediate dirs if needed).
     */
    fun saveAsAtPath(relativePath: String) {
        val rootUri = _uiState.value.projectRootUri ?: run {
            _uiState.update { it.copy(statusMessage = "No project open") }
            return
        }
        val active  = _uiState.value.openTabs.firstOrNull { it.isActive } ?: return
        val content = pendingContent[active.id] ?: active.content ?: return
        val normalized = normalizeProjectPath(relativePath, emptyList())
        val segments = when (normalized) {
            is NormalizedPathResult.Success -> normalized.segments
            NormalizedPathResult.AboveProjectRoot -> {
                _uiState.update { it.copy(statusMessage = "The path cannot go above the project root") }
                return
            }
            NormalizedPathResult.MissingFinalName,
            NormalizedPathResult.InvalidComponent -> {
                _uiState.update { it.copy(statusMessage = "Invalid path") }
                return
            }
        }
        viewModelScope.launch {
            val resolved = safRepository.resolveOrCreatePathSafely(rootUri, segments)
            val path = resolved as? PathResolutionResult.Resolved ?: run {
                val created = when (resolved) {
                    is PathResolutionResult.BlockedByFile -> resolved.createdIntermediateUris
                    is PathResolutionResult.IntermediateCreationFailed -> resolved.createdIntermediateUris
                    is PathResolutionResult.IntermediateNameMismatch -> resolved.createdIntermediateUris
                    else -> emptyList()
                }
                safRepository.rollbackCreatedDirectories(created)
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Save As: could not resolve path") }
                return@launch
            }
            val (targetParentUri, leafName) = path.parentUri to path.leafName
            val created = safRepository.createFileWithExactName(
                targetParentUri,
                leafName,
                EditorLanguageRegistry.mimeTypeForFileName(leafName),
            )
            val newUri = (created as? ExactCreateResult.Created)?.documentUri ?: run {
                safRepository.rollbackCreatedDirectories(path.createdIntermediateUris)
                val message = if (created is ExactCreateResult.Duplicate) {
                    "\u201c$leafName\u201d already exists — choose a different name"
                } else {
                    "Save As: could not inspect or create the target"
                }
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = message) }
                return@launch
            }
            val ok = safRepository.writeFile(newUri, content.toByteArray(Charsets.UTF_8))
            if (!ok) {
                safRepository.deleteDocument(newUri)
                safRepository.rollbackCreatedDirectories(path.createdIntermediateUris)
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Save As: write failed") }
                return@launch
            }
            val newLang = EditorLanguageRegistry.languageForFileName(leafName)
            pendingContent.remove(active.id)
            crashRecovery.clearUnsavedContent(rootUri, active.id)
            refreshProjectNow()
            _uiState.update { state ->
                state.copy(
                    openTabs     = state.openTabs.map {
                        if (it.id == active.id) it.copy(
                            documentUri = newUri, displayName = leafName,
                            language = newLang, isDirty = false, isBlank = false,
                        ) else it
                    },
                    fileOpDialog  = null,
                    statusMessage = "Saved as $leafName",
                )
            }
        }
    }

    // ── Markdown preview converter ─────────────────────────────────────────

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

}
