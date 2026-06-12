// android-ide/android/java/dev/androidide/viewmodel/model/FileNode.kt
//
// Represents a single node in the SAF-backed file tree.
//
// Migration note (2026-06-12):
//   Replaces the Rust FileNode struct in modules/filesystem/src/tree.rs.
//   Same fields; Kotlin data class instead of Rust struct.

package dev.androidide.viewmodel.model

/**
 * A file or directory entry in the project's SAF document tree.
 *
 * [documentUri]  Full SAF document URI — use as the stable identifier for all
 *                ContentResolver operations. NOTE: URIs can change after a
 *                rename (see SafRepository.renameDocument).
 * [displayName]  Human-readable name shown in the file tree.
 * [mimeType]     MIME type from DocumentsContract. Directory nodes have
 *                mimeType = "vnd.android.document/directory".
 * [size]         File size in bytes; 0 for directories.
 * [isDirectory]  Derived from mimeType. True for directory nodes.
 * [children]     Lazily loaded — empty until the node is expanded.
 * [isExpanded]   Whether the directory is open in the file tree UI.
 */
data class FileNode(
    val documentUri: String,
    val displayName: String,
    val mimeType: String,
    val size: Long = 0L,
    val children: List<FileNode> = emptyList(),
    val isExpanded: Boolean = false,
) {
    val isDirectory: Boolean
        get() = mimeType == "vnd.android.document/directory"
}

// ── File tree manipulation helpers ────────────────────────────────────────────

/**
 * Toggle the expanded state of the node with [documentUri].
 * Operates recursively through the entire tree.
 */
fun List<FileNode>.toggleExpanded(documentUri: String): List<FileNode> = map { node ->
    when {
        node.documentUri == documentUri -> node.copy(isExpanded = !node.isExpanded)
        node.children.isNotEmpty() -> node.copy(children = node.children.toggleExpanded(documentUri))
        else -> node
    }
}

/**
 * Set the children of the node with [documentUri].
 * Used when a directory is expanded and its children are loaded from SAF.
 */
fun List<FileNode>.setChildren(documentUri: String, children: List<FileNode>): List<FileNode> = map { node ->
    when {
        node.documentUri == documentUri -> node.copy(children = children, isExpanded = true)
        node.children.isNotEmpty() -> node.copy(children = node.children.setChildren(documentUri, children))
        else -> node
    }
}

/**
 * Find a node by [documentUri], searching recursively.
 */
fun List<FileNode>.findNode(documentUri: String): FileNode? {
    for (node in this) {
        if (node.documentUri == documentUri) return node
        val found = node.children.findNode(documentUri)
        if (found != null) return found
    }
    return null
}

/**
 * Sort nodes: directories first, then files, both alphabetically.
 */
fun List<FileNode>.sortedForTree(): List<FileNode> =
    sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.displayName.lowercase() })
        .map { if (it.isDirectory) it.copy(children = it.children.sortedForTree()) else it }
