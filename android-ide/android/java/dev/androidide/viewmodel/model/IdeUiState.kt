// android-ide/android/java/dev/androidide/viewmodel/model/IdeUiState.kt
//
// Single source of truth for the IDE's observable UI state.
// Emitted as a StateFlow from IdeViewModel and collected by IdeScreen.

package dev.androidide.viewmodel.model

/**
 * Complete UI state for the IDE screen.
 *
 * All fields are immutable; IdeViewModel emits a new copy for each change.
 */
data class IdeUiState(
    /** Display name shown in the top app bar (extracted from the project root URI). */
    val projectName: String = "",

    /** SAF tree URI of the open project root; null if no project is open. */
    val projectRootUri: String? = null,

    /** Root nodes of the file tree sidebar. Directories carry lazy-loaded children. */
    val fileTree: List<FileNode> = emptyList(),

    /** All open editor tabs. Exactly one may have isActive = true. */
    val openTabs: List<EditorTab> = emptyList(),

    /** ID of the currently active tab (mirrors openTabs.first { it.isActive }.id). */
    val activeTabId: String? = null,

    /** True once Monaco sends the "ready" message after its first load. */
    val isEditorReady: Boolean = false,

    /** Whether the live-preview WebView panel is visible alongside the editor. */
    val isPreviewVisible: Boolean = false,

    /** URL loaded in the preview WebView. */
    val previewUrl: String = "about:blank",

    /** Current cursor line number (1-based); updated via cursorMoved messages. */
    val cursorLine: Int = 1,

    /** Current cursor column (1-based). */
    val cursorColumn: Int = 1,

    /** Transient status message shown in the status bar (e.g. "Saved", "Save failed"). */
    val statusMessage: String = "",
)
