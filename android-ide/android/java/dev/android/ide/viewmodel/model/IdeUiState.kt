// android-ide/android/java/dev/android/ide/viewmodel/model/IdeUiState.kt
//
// Single source of truth for all observable IDE UI state.

package dev.android.ide.viewmodel.model

import dev.android.ide.data.model.AppTheme
import dev.android.ide.data.model.EditorSettings
import dev.android.ide.data.model.Project
import dev.android.ide.data.model.ProjectDetails
import dev.android.ide.data.model.RecoveryEntry
import dev.android.ide.data.model.VolumeKeyMode

data class IdeUiState(
    // ── Navigation ─────────────────────────────────────────────────────────
    /** Which top-level screen is visible. */
    val currentScreen: AppScreen = AppScreen.HOME,
    /** Previous top-level surface used when dismissing a child surface. */
    val previousScreen: AppScreen? = null,

    // ── Project ────────────────────────────────────────────────────────────
    /** Display name shown in the top app bar. */
    val projectName: String = "",

    /** SAF tree URI of the open project root; null if no project is open. */
    val projectRootUri: String? = null,

    /** Recently-opened project registry, most-recent first. */
    val recentProjects: List<Project> = emptyList(),

    /** Computed metadata for the project-details dialog. */
    val projectDetails: ProjectDetails? = null,

    /** True while project details are being calculated from storage. */
    val projectDetailsLoading: Boolean = false,

    /** Cached metadata used for project-list sorting and detail screens. */
    val projectDetailsByUri: Map<String, ProjectDetails> = emptyMap(),

    /** True while project-list metadata is being refreshed. */
    val projectMetadataLoading: Boolean = false,

    // ── File tree ──────────────────────────────────────────────────────────
    /** Root nodes of the file tree sidebar. */
    val fileTree: List<FileNode> = emptyList(),

    /** True while the file tree is being inspected or refreshed. */
    val fileTreeLoading: Boolean = false,

    /** True while a provider-backed copy, cut, or paste mutation is running. */
    val fileMutationLoading: Boolean = false,

    /** URI requested by the sidebar locate action. */
    val locateTargetUri: String? = null,

    /** Incremented for each locate request, including repeated requests for one file. */
    val locateRequestToken: Long = 0L,

    // ── File search ────────────────────────────────────────────────────────
    /** Whether filename search is visible in the sidebar. */
    val isSearchVisible: Boolean = false,

    /** Whether whole-project content search is visible in the sidebar. */
    val isContentSearchVisible: Boolean = false,

    /** Current filename query. */
    val fileSearchQuery: String = "",

    /** Matching files for the filename query. */
    val fileSearchResults: List<FileSearchResult> = emptyList(),

    /** Current case-insensitive content query scanned across project text files. */
    val contentSearchQuery: String = "",

    /** Files containing the current project-content query. */
    val contentSearchResults: List<FileSearchResult> = emptyList(),

    /** True while Monaco's find or replace widget is the active editor overlay. */
    val isEditorSearchVisible: Boolean = false,

    // ── Multi-selection ────────────────────────────────────────────────────
    /** Whether multi-select mode is active in the file tree. */
    val isMultiSelectMode: Boolean = false,

    /** Set of document URIs currently selected in multi-select mode. */
    val selectedUris: Set<String> = emptySet(),

    // ── File tree clipboard ─────────────────────────────────────────────────
    /**
     * Files/folders pending a paste operation.
     * Supports both single-item and multi-item clipboard from multi-select.
     * Empty list means clipboard is empty.
     */
    val clipboardItems: List<FileNode> = emptyList(),

    /** True when [clipboardItems] were cut (will be moved); false for copy. */
    val clipboardIsCut: Boolean = false,

    // ── Editor ─────────────────────────────────────────────────────────────
    /** All open editor tabs. */
    val openTabs: List<EditorTab> = emptyList(),

    /** ID of the currently active tab. */
    val activeTabId: String? = null,

    /** Whether Monaco currently has a non-empty text selection. */
    val hasEditorSelection: Boolean = false,

    /** True once Monaco sends the "ready" message. */
    val isEditorReady: Boolean = false,

    /** True while the active file is being bound into Monaco. */
    val editorFileLoading: Boolean = false,

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

    // ── Per-tab cursor and scroll position maps ─────────────────────────────
    // Keyed by documentUri. Persisted per-project in .dev-android-ide/workspace.json.
    val tabCursorPositions: Map<String, Pair<Int, Int>> = emptyMap(),
    val tabScrollPositions: Map<String, Int>            = emptyMap(),

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

    /** URI and one-time code for permanent project deletion. */
    val confirmDeleteProjectUri: String? = null,
    val confirmDeleteProjectCode: String? = null,

    /** Non-null when switching projects requires a dirty-tab decision. */
    val projectSwitchRequest: ProjectSwitchRequest? = null,

    // ── Crash recovery ──────────────────────────────────────────────────────
    /**
     * Non-empty when the previous session did not exit cleanly and there are
     * persisted unsaved content entries to offer for restoration.
     */
    val recoveryEntries: List<RecoveryEntry> = emptyList(),

    /** Incremented when Monaco must bind the active tab again. */
    val editorBindRevision: Long = 0L,
)
