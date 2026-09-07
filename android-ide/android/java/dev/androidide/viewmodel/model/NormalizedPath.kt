package dev.androidide.viewmodel.model

/**
 * Result of normalizing a user-entered project path before any filesystem
 * operation is attempted.
 */
sealed class NormalizedPathResult {
    data class Success(val segments: List<String>) : NormalizedPathResult()
    data object AboveProjectRoot : NormalizedPathResult()
    data object MissingFinalName : NormalizedPathResult()
    data object InvalidComponent : NormalizedPathResult()
}

/**
 * Normalize [rawPath] lexically from [relativeBase].
 *
 * A leading slash makes the path project-root-relative. Otherwise the path is
 * relative to [relativeBase], which is already expressed from the project
 * root. Dot components are ignored and dot-dot components remove one existing
 * segment. The operation fails only when dot-dot would move above the project
 * root, or when no final item name remains after normalization.
 */
fun normalizeProjectPath(
    rawPath: String,
    relativeBase: List<String>,
): NormalizedPathResult {
    val input = rawPath.trim()
    if (input.isEmpty() || input.indexOf('\u0000') >= 0) {
        return NormalizedPathResult.MissingFinalName
    }

    val isRootRelative = input.startsWith('/') || input.startsWith('\\')
    val normalized = if (isRootRelative) {
        mutableListOf()
    } else {
        relativeBase.toMutableList()
    }

    val components = input
        .replace('\\', '/')
        .split('/')

    for (component in components) {
        when (component) {
            "", "." -> Unit
            ".." -> {
                if (normalized.isEmpty()) return NormalizedPathResult.AboveProjectRoot
                normalized.removeAt(normalized.lastIndex)
            }
            else -> {
                if (component.any { it == '\u0000' }) {
                    return NormalizedPathResult.InvalidComponent
                }
                normalized += component
            }
        }
    }

    if (normalized.isEmpty()) return NormalizedPathResult.MissingFinalName
    return NormalizedPathResult.Success(normalized)
}
