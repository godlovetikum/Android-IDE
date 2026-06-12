// android-ide/android/java/dev/androidide/viewmodel/IdeViewModel.kt
//
// Single ViewModel for the entire IDE session.
// Bridges SAF, Monaco editor bridge, project registry, session, and theme.

package dev.androidide.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.androidide.data.ProjectRepository
import dev.androidide.data.SessionRepository
import dev.androidide.data.ThemeRepository
import dev.androidide.data.model.AppTheme
import dev.androidide.data.model.Project
import dev.androidide.editor.EditorInbound
import dev.androidide.saf.SafRepository
import dev.androidide.viewmodel.model.AppScreen
import dev.androidide.viewmodel.model.EditorTab
import dev.androidide.viewmodel.model.FileNode
import dev.androidide.viewmodel.model.FileOpDialog
import dev.androidide.viewmodel.model.IdeUiState
import dev.androidide.viewmodel.model.findNode
import dev.androidide.viewmodel.model.removeNode
import dev.androidide.viewmodel.model.replaceNode
import dev.androidide.viewmodel.model.setChildren
import dev.androidide.viewmodel.model.sortedForTree
import dev.androidide.viewmodel.model.toggleExpanded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class IdeViewModel(application: Application) : AndroidViewModel(application) {

    private val safRepository     = SafRepository(application)
    private val projectRepository = ProjectRepository(application)
    private val sessionRepository = SessionRepository(application)
    private val themeRepository   = ThemeRepository(application)

    private val _uiState = MutableStateFlow(
        IdeUiState(
            appTheme       = themeRepository.get(),
            recentProjects = projectRepository.getAll(),
        )
    )
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    // Pending (unsaved) editor content keyed by tab ID.
    // Stored separately from IdeUiState to avoid recomposing the tree on keystrokes.
    private val pendingContent = mutableMapOf<String, String>()

    init {
        restoreSession()
    }

    override fun onCleared() {
        super.onCleared()
        saveSession()
    }

    // ── Session ────────────────────────────────────────────────────────────

    private fun restoreSession() {
        val projectUri = sessionRepository.getProjectUri() ?: return
        val tabUris    = sessionRepository.getOpenTabUris()
        val activeUri  = sessionRepository.getActiveTabUri()
        val screenName = sessionRepository.getScreenName()

        // Restore navigation screen
        val screen = screenName?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
        if (screen != null) _uiState.update { it.copy(currentScreen = screen) }

        // Re-open the last project (loads file tree in background)
        viewModelScope.launch { openProjectInternal(projectUri) }

        // Re-open tabs in parallel — SAF reads don't need the file tree
        tabUris.forEach { uri ->
            viewModelScope.launch { openFileInternal(uri, markActive = uri == activeUri) }
        }
    }

    fun saveSession() {
        val state = _uiState.value
        sessionRepository.save(
            projectUri   = state.projectRootUri,
            openTabUris  = state.openTabs.map { it.documentUri },
            activeTabUri = state.openTabs.firstOrNull { it.isActive }?.documentUri,
            screenName   = state.currentScreen.name,
        )
    }

    // ── Navigation ─────────────────────────────────────────────────────────

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
        saveSession()
    }

    // ── Theme ──────────────────────────────────────────────────────────────

    fun setTheme(theme: AppTheme) {
        themeRepository.set(theme)
        _uiState.update { it.copy(appTheme = theme) }
    }

    // ── Project management ─────────────────────────────────────────────────

    /**
     * Open a project from a SAF tree URI obtained via Intent.ACTION_OPEN_DOCUMENT_TREE.
     * Registers it in the recent-projects registry and navigates to the editor.
     */
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
     * Remove a project from the registry without touching its files on disk.
     */
    fun removeProjectFromRegistry(uri: String) {
        projectRepository.remove(uri)
        _uiState.update { it.copy(recentProjects = projectRepository.getAll()) }
    }

    /**
     * Close the current project and return to the Projects screen.
     */
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

    // ── File tree ──────────────────────────────────────────────────────────

    fun toggleDirectory(documentUri: String) {
        _uiState.update { state ->
            state.copy(fileTree = state.fileTree.toggleExpanded(documentUri))
        }
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

    // ── Editor tabs ────────────────────────────────────────────────────────

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

    fun closeTab(tabId: String) {
        pendingContent.remove(tabId)
        _uiState.update { state ->
            val remaining   = state.openTabs.filter { it.id != tabId }
            val newActiveId = if (state.activeTabId == tabId) remaining.lastOrNull()?.id else state.activeTabId
            state.copy(
                openTabs    = remaining.map { it.copy(isActive = it.id == newActiveId) },
                activeTabId = newActiveId,
            )
        }
    }

    // ── Editor bridge ──────────────────────────────────────────────────────

    fun onEditorReady() {
        _uiState.update { it.copy(isEditorReady = true) }
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
        saveFile(active.documentUri)
    }

    private fun saveFile(documentUri: String) {
        val tab     = _uiState.value.openTabs.find { it.documentUri == documentUri } ?: return
        val content = pendingContent[tab.id] ?: return

        viewModelScope.launch {
            val ok = safRepository.writeFile(documentUri, content.toByteArray(Charsets.UTF_8))
            if (ok) {
                pendingContent.remove(tab.id)
                _uiState.update { state ->
                    state.copy(
                        openTabs      = state.openTabs.map { if (it.id == tab.id) it.copy(isDirty = false) else it },
                        statusMessage = "Saved",
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = "Save failed") }
            }
        }
    }

    // ── File operations ────────────────────────────────────────────────────

    fun showRenameDialog(node: FileNode)        = _uiState.update { it.copy(fileOpDialog = FileOpDialog.Rename(node)) }
    fun showDeleteDialog(node: FileNode)        = _uiState.update { it.copy(fileOpDialog = FileOpDialog.Delete(node)) }
    fun showCreateFileDialog(parent: FileNode)  = _uiState.update { it.copy(fileOpDialog = FileOpDialog.CreateFile(parent)) }
    fun showCreateFolderDialog(parent: FileNode)= _uiState.update { it.copy(fileOpDialog = FileOpDialog.CreateFolder(parent)) }
    fun showDuplicateDialog(node: FileNode)     = _uiState.update { it.copy(fileOpDialog = FileOpDialog.Duplicate(node)) }
    fun dismissFileOpDialog()                   = _uiState.update { it.copy(fileOpDialog = null) }

    fun renameNode(node: FileNode, newName: String) {
        viewModelScope.launch {
            val newUri = safRepository.renameDocument(node.documentUri, newName) ?: run {
                _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Rename failed") }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    openTabs = state.openTabs.map {
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

    fun duplicateFile(node: FileNode, newName: String) {
        val parentUri = node.parentDocumentUri ?: run {
            _uiState.update { it.copy(fileOpDialog = null, statusMessage = "Cannot duplicate: no parent") }
            return
        }
        viewModelScope.launch {
            val bytes = safRepository.readFile(node.documentUri) ?: run {
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
