// android-ide/android/java/dev/androidide/viewmodel/model/IdeUiState.kt
//
// Single source of truth for all observable IDE UI state.

package dev.androidide.viewmodel.model

import dev.androidide.data.model.AppTheme
import dev.androidide.data.model.Project

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
    /** Non-null when a file operation dialog (rename / delete / create) is visible. */
    val fileOpDialog: FileOpDialog? = null,

    // ── Theme ──────────────────────────────────────────────────────────────
    val appTheme: AppTheme = AppTheme.DARK,
)
