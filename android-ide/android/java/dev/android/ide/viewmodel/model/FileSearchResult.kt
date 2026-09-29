// android-ide/android/java/dev/android/ide/viewmodel/model/FileSearchResult.kt
//
// Represents a single result from a project-content search.

package dev.android.ide.viewmodel.model

/**
 * A matching file found by scanning text content across the project.
 *
 * [documentUri]  SAF or file:// URI — used to open the file on selection.
 * [displayName]  The file's name.
 * [relativePath] Full display path relative to the project root (e.g. "src/main/Main.kt").
 * [matchPreview] A short line containing the matching text, when available.
 * [matchLine] One-based line number for the displayed match, when available.
 * [matchColumn] One-based column of the exact match, when available.
 * [matchLength] Length of the exact match, used to select/reveal it in Monaco.
 * [isDirectory] True for filename-search folder results.
 */
data class FileSearchResult(
    val documentUri: String,
    val displayName: String,
    val relativePath: String,
    val matchPreview: String = "",
    val matchLine: Int? = null,
    val matchColumn: Int? = null,
    val matchLength: Int? = null,
    val isDirectory: Boolean = false,
    /** Start and length of the exact match within [matchPreview], when available. */
    val previewMatchStart: Int = -1,
    val previewMatchLength: Int = 0,
)
