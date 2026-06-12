// android-ide/android/java/dev/androidide/viewmodel/model/FileOpDialog.kt
//
// Sealed class representing the currently-active file-operation dialog.
// Stored in IdeUiState.fileOpDialog — null when no dialog is visible.

package dev.androidide.viewmodel.model

sealed class FileOpDialog {
    /** Rename dialog for [node]. */
    data class Rename(val node: FileNode) : FileOpDialog()

    /** Delete confirmation dialog for [node]. */
    data class Delete(val node: FileNode) : FileOpDialog()

    /** "Create file" dialog; the new file will be created inside [parentNode]. */
    data class CreateFile(val parentNode: FileNode) : FileOpDialog()

    /** "Create folder" dialog; the new folder will be created inside [parentNode]. */
    data class CreateFolder(val parentNode: FileNode) : FileOpDialog()

    /**
     * Duplicate dialog for [node].
     * Pre-fills the name field with "Copy of <displayName>".
     */
    data class Duplicate(val node: FileNode) : FileOpDialog()
}
