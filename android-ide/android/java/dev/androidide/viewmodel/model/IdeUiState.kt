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

    // ── Editor ─────────────────────────────────────────────────────────────
    /** All open editor tabs. */
    val openTabs: List<EditorTab> = emptyList(),

    /** ID of the currently active tab. */
    val activeTabId: String? = null,

    /** True once Monaco sends the "ready" message. */
    val isEditorReady: Boolean = false,

    /** Whether the live-preview WebView is visible. */
    val isPreviewVisible: Boolean = false,

    /** URL loaded in the preview WebView. */
    val previewUrl: String = "about:blank",

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

    // ── Crash recovery ──────────────────────────────────────────────────────
    /**
     * Non-empty when the previous session did not exit cleanly and there are
     * persisted unsaved content entries to offer for restoration.
     */
    val recoveryEntries: List<RecoveryEntry> = emptyList(),
)
