package dev.android.ide.saf

/**
 * Storage scope used when resolving normalized path segments.
 *
 * [startUri] is the directory from which resolution begins. [boundaryUri],
 * when supplied, limits the walk and every created directory to that storage
 * subtree. The caller decides what the boundary means (project, workspace,
 * archive, or another domain-specific scope).
 */
data class PathResolutionScope(
    val startUri: String,
    val boundaryUri: String? = null,
)
