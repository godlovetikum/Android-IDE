// android-ide/android/java/dev/androidide/viewmodel/model/FileNode.kt
//
// Represents a single node in the SAF-backed file tree.

package dev.androidide.viewmodel.model

/**
 * A file or directory entry in the project's SAF document tree.
 *
 * [documentUri]        Full SAF document URI — stable identifier for all ContentResolver ops.
 *                      NOTE: URIs can change after a rename (see SafRepository.renameDocument).
 * [displayName]        Human-readable name shown in the file tree.
 * [mimeType]           MIME type from DocumentsContract.
 *                      Directory nodes: "vnd.android.document/directory".
 * [size]               File size in bytes; 0 for directories.
 * [parentDocumentUri]  SAF URI of the parent directory. Used for create/duplicate/move operations.
 *                      Null only for tree root nodes where the parent is the project root.
 * [children]           Lazily loaded — empty until the node is expanded.
 * [isExpanded]         Whether the directory is open in the file tree UI.
 * [isDirectory]        Derived from mimeType.
 */
data class FileNode(
    val documentUri: String,
    val displayName: String,
    val mimeType: String,
    val size: Long = 0L,
    val parentDocumentUri: String? = null,
    val children: List<FileNode> = emptyList(),
    val isExpanded: Boolean = false,
) {
    val isDirectory: Boolean
        get() = mimeType == "vnd.android.document/directory"
}

// ── File tree manipulation helpers ────────────────────────────────────────────

fun List<FileNode>.toggleExpanded(documentUri: String): List<FileNode> = map { node ->
    when {
        node.documentUri == documentUri -> node.copy(isExpanded = !node.isExpanded)
        node.children.isNotEmpty() -> node.copy(children = node.children.toggleExpanded(documentUri))
        else -> node
    }
}

fun List<FileNode>.setChildren(documentUri: String, children: List<FileNode>): List<FileNode> = map { node ->
    when {
        node.documentUri == documentUri -> node.copy(children = children, isExpanded = true)
        node.children.isNotEmpty() -> node.copy(children = node.children.setChildren(documentUri, children))
        else -> node
    }
}

fun List<FileNode>.findNode(documentUri: String): FileNode? {
    for (node in this) {
        if (node.documentUri == documentUri) return node
        val found = node.children.findNode(documentUri)
        if (found != null) return found
    }
    return null
}

/**
 * Replace a node identified by [oldDocumentUri] with [updatedNode].
 * Used after rename (the URI may change).
 */
fun List<FileNode>.replaceNode(oldDocumentUri: String, updatedNode: FileNode): List<FileNode> = map { node ->
    when {
        node.documentUri == oldDocumentUri -> updatedNode
        node.children.isNotEmpty() -> node.copy(children = node.children.replaceNode(oldDocumentUri, updatedNode))
        else -> node
    }
}

/**
 * Remove the node with [documentUri] from the tree.
 */
fun List<FileNode>.removeNode(documentUri: String): List<FileNode> = mapNotNull { node ->
    when {
        node.documentUri == documentUri -> null
        node.children.isNotEmpty() -> node.copy(children = node.children.removeNode(documentUri))
        else -> node
    }
}

fun List<FileNode>.sortedForTree(): List<FileNode> =
    sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.displayName.lowercase() })
        .map { if (it.isDirectory) it.copy(children = it.children.sortedForTree()) else it }

/**
 * Return the display-name path to [documentUri] relative to the tree roots,
 * always prefixed with "/" so paths look like "/src/pages/home.html".
 *
 * Returns null if [documentUri] is not found in the tree.
 * The [prefix] parameter is used internally for recursion.
 */
fun List<FileNode>.pathTo(documentUri: String, prefix: String = ""): String? {
    for (node in this) {
        val current = "$prefix/${node.displayName}"
        if (node.documentUri == documentUri) return current
        if (node.isDirectory) {
            val found = node.children.pathTo(documentUri, current)
            if (found != null) return found
        }
    }
    return null
}

/**
 * Return the list of [FileNode] objects on the path from a tree root down to
 * (but not including) the node with [documentUri], in root-first order.
 *
 * Used by breadcrumb path navigation so each ancestor segment has a known URI.
 * Returns an empty list if the node is a root-level entry or is not found.
 */
fun List<FileNode>.ancestorsOf(documentUri: String, acc: List<FileNode> = emptyList()): List<FileNode>? {
    for (node in this) {
        if (node.documentUri == documentUri) return acc
        if (node.isDirectory) {
            val found = node.children.ancestorsOf(documentUri, acc + node)
            if (found != null) return found
        }
    }
    return null
}
