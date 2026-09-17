// android-ide/android/java/dev/androidide/viewmodel/model/FileSearchResult.kt
//
// Represents a single result from a project-content search.

package dev.androidide.viewmodel.model

/**
 * A matching file found by scanning text content across the project.
 *
 * [documentUri]  SAF or file:// URI — used to open the file on selection.
 * [displayName]  The file's name.
 * [relativePath] Full display path relative to the project root (e.g. "src/main/Main.kt").
 * [matchPreview] A short line containing the matching text, when available.
 */
data class FileSearchResult(
    val documentUri: String,
    val displayName: String,
    val relativePath: String,
    val matchPreview: String = "",
)
