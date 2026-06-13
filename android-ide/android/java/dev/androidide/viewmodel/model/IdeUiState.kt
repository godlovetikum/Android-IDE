// android-ide/android/java/dev/androidide/viewmodel/model/IdeUiState.kt
//
// Single source of truth for all observable IDE UI state.

package dev.androidide.viewmodel.model

import dev.androidide.data.model.AppTheme
import dev.androidide.data.model.EditorSettings
import dev.androidide.data.model.Project
import dev.androidide.data.model.RecoveryEntry
import dev.androidide.data.model.VolumeKeyMode

data class IdeUiState(
    // ── Navigation ─────────────────────────────────────────────────────────
    /** Which top-level screen is visible. */
    val currentScreen: AppScreen = AppScreen.PROJECTS,

    // ── Project ────────────────────────────────────────────────────────────
    /** Display name shown in the top app bar. */
    val projectName: String = "",

    /** SAF tree URI of the open project root; null if no project is open. */
    val projectRootUri: String? = null,

    /** Recently-opened project registry, most-recent first. */
    val recentProjects: List<Project> = emptyList(),

    // ── File tree ──────────────────────────────────────────────────────────
    /** Root nodes of the file tree sidebar. */
    val fileTree: List<FileNode> = emptyList(),

    // ── File search ────────────────────────────────────────────────────────
    /** Whether the file-name search panel is visible in the sidebar. */
    val isSearchVisible: Boolean = false,

    /** Current search query (file name search only). */
    val fileSearchQuery: String = "",

    /** Matching files for the current search query. */
    val fileSearchResults: List<FileSearchResult> = emptyList(),

    // ── Multi-selection ────────────────────────────────────────────────────
    /** Whether multi-select mode is active in the file tree. */
    val isMultiSelectMode: Boolean = false,

    /** Set of document URIs currently selected in multi-select mode. */
    val selectedUris: Set<String> = emptySet(),

    // ── Editor ─────────────────────────────────────────────────────────────
    /** All open editor tabs. */
    val openTabs: List<EditorTab> = emptyList(),

    /** ID of the currently active tab. */
    val activeTabId: String? = null,

    /** True once Monaco sends the "ready" message. */
    val isEditorReady: Boolean = false,

    /** Whether the live-preview WebView is visible. */
    val isPreviewVisible: Boolean = false,

    /**
     * Raw HTML content for the preview WebView.
     * Passed directly to WebView.loadDataWithBaseURL — not base64 encoded.
     * Empty when preview is not active.
     */
    val previewHtmlContent: String = "",

    // ── Cursor ─────────────────────────────────────────────────────────────
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,

    // ── Status ─────────────────────────────────────────────────────────────
    /** Transient status bar message (e.g. "Saved", "Renamed"). */
    val statusMessage: String = "",

    // ── File operation dialog ──────────────────────────────────────────────
    /** Non-null when a file operation dialog is visible. */
    val fileOpDialog: FileOpDialog? = null,

    // ── Theme ──────────────────────────────────────────────────────────────
    val appTheme: AppTheme = AppTheme.DARK,

    // ── Editor settings ────────────────────────────────────────────────────
    /** Persisted editor display and behaviour settings. */
    val editorSettings: EditorSettings = EditorSettings(),

    // ── Volume keys ─────────────────────────────────────────────────────────
    /** Controls how hardware volume keys behave when the editor is focused. */
    val volumeKeyMode: VolumeKeyMode = VolumeKeyMode.HORIZONTAL,

    // ── File tree clipboard ─────────────────────────────────────────────────
    /** FileNode pending a paste operation; null when the clipboard is empty. */
    val clipboard: FileNode? = null,

    /** True when [clipboard] was cut (will be moved); false for copy. */
    val clipboardIsCut: Boolean = false,

    // ── Exit confirmation ───────────────────────────────────────────────────
    /**
     * True when the user pressed Back while at least one tab has unsaved changes.
     * Triggers the "You have unsaved changes. Exit anyway?" dialog.
     */
    val showExitConfirmation: Boolean = false,

    // ── Remove project confirmation ─────────────────────────────────────────
    /**
     * Non-null when the user has requested to remove a project from the registry.
     * The value is the project URI pending removal.
     * Triggers the remove confirmation dialog.
     */
    val confirmRemoveProjectUri: String? = null,

    // ── Crash recovery ──────────────────────────────────────────────────────
    /**
     * Non-empty when the previous session did not exit cleanly and there are
     * persisted unsaved content entries to offer for restoration.
     */
    val recoveryEntries: List<RecoveryEntry> = emptyList(),
)
