// android-ide/android/java/dev/androidide/viewmodel/model/FileSearchResult.kt
//
// Represents a single result from a file-name search in the file tree.

package dev.androidide.viewmodel.model

/**
 * A matching file found during a file-name search.
 *
 * [documentUri]  SAF or file:// URI — used to open the file on selection.
 * [displayName]  The file's name (the matched portion).
 * [relativePath] Full display path relative to the project root (e.g. "src/main/Main.kt").
 */
data class FileSearchResult(
    val documentUri: String,
    val displayName: String,
    val relativePath: String,
)
