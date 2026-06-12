// android-ide/android/java/dev/androidide/viewmodel/IdeViewModel.kt
//
// IDE state management — bridges SAF, editor bridge, and Compose UI.
//
// Migration note (2026-06-12):
//   Replaces the Rust UI state management in src/ui.rs and the module event
//   dispatchers in modules/editor/src/lib.rs and modules/filesystem/src/lib.rs.
//   Uses AndroidViewModel (not ViewModel) to access Application context safely.
//
// Architecture decision MD-002:
//   AndroidViewModel holds Application reference for SAF ContentResolver access.
//   This avoids leaking an Activity reference.

package dev.androidide.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.androidide.editor.EditorInbound
import dev.androidide.saf.SafRepository
import dev.androidide.viewmodel.model.EditorTab
import dev.androidide.viewmodel.model.FileNode
import dev.androidide.viewmodel.model.IdeUiState
import dev.androidide.viewmodel.model.findNode
import dev.androidide.viewmodel.model.setChildren
import dev.androidide.viewmodel.model.sortedForTree
import dev.androidide.viewmodel.model.toggleExpanded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class IdeViewModel(application: Application) : AndroidViewModel(application) {

    private val safRepository = SafRepository(application)

    private val _uiState = MutableStateFlow(IdeUiState())
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    // Pending (unsaved) content keyed by tab ID.
    // Separate from IdeUiState to avoid re-composing the full tree on every keystroke.
    private val pendingContent = mutableMapOf<String, String>()

    // ── Project operations ─────────────────────────────────────────────────

    /**
     * Open a project from a SAF tree URI obtained via Intent.ACTION_OPEN_DOCUMENT_TREE.
     * Loads the root directory listing into the file tree.
     */
    fun openProject(treeUriString: String) {
        val projectName = extractProjectName(treeUriString)
        _uiState.update { it.copy(projectName = projectName, projectRootUri = treeUriString) }

        viewModelScope.launch {
            val nodes = safRepository.listChildren(treeUriString)
            _uiState.update { it.copy(fileTree = nodes.sortedForTree()) }
        }
    }

    // ── File tree ──────────────────────────────────────────────────────────

    /**
     * Toggle the expanded/collapsed state of a directory node.
     * If expanding and the directory has no loaded children, fetches them from SAF.
     */
    fun toggleDirectory(documentUri: String) {
        _uiState.update { state ->
            state.copy(fileTree = state.fileTree.toggleExpanded(documentUri))
        }

        // After toggling, check if the node is now expanded and needs children.
        val node = _uiState.value.fileTree.findNode(documentUri)
        if (node != null && node.isExpanded && node.isDirectory && node.children.isEmpty()) {
            viewModelScope.launch {
                val children = safRepository.listChildren(documentUri)
                _uiState.update { state ->
                    state.copy(
                        fileTree = state.fileTree.setChildren(documentUri, children.sortedForTree()),
                    )
                }
            }
        }
    }

    // ── Editor tabs ────────────────────────────────────────────────────────

    /**
     * Open a file in the editor. If the file is already open, activates its tab.
     * Reads file content from SAF on Dispatchers.IO.
     */
    fun openFile(documentUri: String) {
        val existing = _uiState.value.openTabs.find { it.documentUri == documentUri }
        if (existing != null) {
            selectTab(existing.id)
            return
        }

        viewModelScope.launch {
            val bytes = safRepository.readFile(documentUri) ?: return@launch
            val content = String(bytes, Charsets.UTF_8)
            val displayName = displayNameFromUri(documentUri)
            val language = languageForExtension(displayName.substringAfterLast('.', ""))

            val newTab = EditorTab(
                documentUri = documentUri,
                displayName = displayName,
                language    = language,
                content     = content,
                isActive    = true,
            )

            _uiState.update { state ->
                val tabs = state.openTabs.map { it.copy(isActive = false) } + newTab
                state.copy(openTabs = tabs, activeTabId = newTab.id)
            }
        }
    }

    /**
     * Switch the active tab to [tabId].
     */
    fun selectTab(tabId: String) {
        _uiState.update { state ->
            state.copy(
                openTabs  = state.openTabs.map { it.copy(isActive = it.id == tabId) },
                activeTabId = tabId,
            )
        }
    }

    /**
     * Close a tab. If it was the active tab, activates the previous one.
     * Discards pending (unsaved) content.
     */
    fun closeTab(tabId: String) {
        pendingContent.remove(tabId)

        _uiState.update { state ->
            val remaining  = state.openTabs.filter { it.id != tabId }
            val newActiveId = if (state.activeTabId == tabId) remaining.lastOrNull()?.id else state.activeTabId
            state.copy(
                openTabs    = remaining.map { it.copy(isActive = it.id == newActiveId) },
                activeTabId = newActiveId,
            )
        }
    }

    // ── Editor bridge events ───────────────────────────────────────────────

    /**
     * Called when Monaco sends the "ready" message.
     * After the editor is ready, IdeScreen's LaunchedEffect will send
     * a loadFile message for the current active tab.
     */
    fun onEditorReady() {
        _uiState.update { it.copy(isEditorReady = true) }
    }

    /**
     * Dispatch an inbound message from [EditorBridge].
     * Called from the EditorBridge callback (background thread) —
     * MutableStateFlow.update is thread-safe.
     */
    fun onEditorMessage(message: EditorInbound) {
        when (message) {
            is EditorInbound.Ready -> onEditorReady()

            is EditorInbound.ContentChanged -> {
                val tab = _uiState.value.openTabs.find { it.documentUri == message.path } ?: return
                pendingContent[tab.id] = message.content
                _uiState.update { state ->
                    state.copy(
                        openTabs = state.openTabs.map {
                            if (it.id == tab.id) it.copy(isDirty = true) else it
                        },
                    )
                }
            }

            is EditorInbound.CursorMoved -> {
                _uiState.update {
                    it.copy(cursorLine = message.line, cursorColumn = message.column)
                }
            }

            is EditorInbound.FileSaved -> saveFile(message.path)
        }
    }

    // ── Save ───────────────────────────────────────────────────────────────

    /**
     * Save the currently active file if it has pending changes.
     */
    fun saveActiveFile() {
        val activeTab = _uiState.value.openTabs.firstOrNull { it.isActive } ?: return
        saveFile(activeTab.documentUri)
    }

    private fun saveFile(documentUri: String) {
        val tab     = _uiState.value.openTabs.find { it.documentUri == documentUri } ?: return
        val content = pendingContent[tab.id] ?: return

        viewModelScope.launch {
            val success = safRepository.writeFile(documentUri, content.toByteArray(Charsets.UTF_8))
            if (success) {
                pendingContent.remove(tab.id)
                _uiState.update { state ->
                    state.copy(
                        openTabs = state.openTabs.map {
                            if (it.id == tab.id) it.copy(isDirty = false) else it
                        },
                        statusMessage = "Saved",
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = "Save failed") }
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /** Extract a readable project name from a SAF tree URI. */
    private fun extractProjectName(treeUriString: String): String = try {
        // SAF tree URIs encode the path as the last path segment after URL-decoding.
        val decoded = Uri.decode(treeUriString)
        decoded.substringAfterLast('/').ifEmpty { "Project" }
    } catch (_: Exception) {
        "Project"
    }

    /** Extract the display name from a SAF document URI. */
    private fun displayNameFromUri(documentUri: String): String = try {
        val decoded = Uri.decode(documentUri)
        decoded.substringAfterLast('/').substringAfterLast('%').ifEmpty { "file" }
    } catch (_: Exception) {
        "file"
    }

    /** Map a file extension to a Monaco language identifier. */
    private fun languageForExtension(ext: String): String = when (ext.lowercase()) {
        "kt", "kts"                     -> "kotlin"
        "java"                          -> "java"
        "xml"                           -> "xml"
        "json"                          -> "json"
        "md"                            -> "markdown"
        "gradle"                        -> "groovy"
        "py"                            -> "python"
        "js", "mjs", "cjs"             -> "javascript"
        "ts", "mts", "cts"             -> "typescript"
        "html", "htm"                   -> "html"
        "css"                           -> "css"
        "sh", "bash"                    -> "shell"
        "c", "h"                        -> "c"
        "cpp", "cc", "cxx", "hpp"       -> "cpp"
        "rs"                            -> "rust"
        "go"                            -> "go"
        "rb"                            -> "ruby"
        "swift"                         -> "swift"
        "toml"                          -> "toml"
        "yaml", "yml"                   -> "yaml"
        "sql"                           -> "sql"
        "proto"                         -> "proto"
        else                            -> "plaintext"
    }
}
