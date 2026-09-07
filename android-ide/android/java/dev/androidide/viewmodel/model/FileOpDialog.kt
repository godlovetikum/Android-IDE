// android-ide/android/java/dev/androidide/viewmodel/model/FileOpDialog.kt
//
// Sealed class representing the currently-active file-operation dialog.
// Stored in IdeUiState.fileOpDialog — null when no dialog is visible.

package dev.androidide.viewmodel.model

sealed class FileOpDialog {
    /** Shown when a binary document is selected for opening in the text editor. */
    data class BinaryOpenError(val fileName: String) : FileOpDialog()

    /** Rename dialog for [node]. [errorMessage] is shown inline when non-null. */
    data class Rename(val node: FileNode, val errorMessage: String? = null) : FileOpDialog()

    /** Delete confirmation dialog for [node] or the snapshotted [selectedNodes]. */
    data class Delete(
        val node: FileNode,
        val selectedNodes: List<FileNode> = emptyList(),
    ) : FileOpDialog()

    /** "Create file" dialog; new file will be created inside [parentNode]. [errorMessage] is shown inline when non-null. */
    data class CreateFile(
        val parentNode: FileNode,
        val errorMessage: String? = null,
        val isSubmitting: Boolean = false,
    ) : FileOpDialog()

    /** "Create folder" dialog; new folder will be created inside [parentNode]. [errorMessage] is shown inline when non-null. */
    data class CreateFolder(
        val parentNode: FileNode,
        val errorMessage: String? = null,
        val isSubmitting: Boolean = false,
    ) : FileOpDialog()

    /**
     * Duplicate dialog for [node].
     * Pre-fills the name field with "copy_<displayName>". [errorMessage] is shown inline when non-null.
     */
    data class Duplicate(val node: FileNode, val errorMessage: String? = null) : FileOpDialog()

    /**
     * Confirm-close dialog shown when the user tries to close a tab that has
     * unsaved changes. Options: Save and close, Discard and close, Cancel.
     */
    data class UnsavedClose(val tabId: String, val displayName: String) : FileOpDialog()

    /**
     * F004: Save-As dialog — allows the user to choose a project-relative path
     * for saving the active file's content.  The [suggestedName] pre-fills the
     * text field with the current tab's display name.
     */
    data class SaveAs(val suggestedName: String) : FileOpDialog()
}
