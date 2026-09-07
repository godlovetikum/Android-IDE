package dev.androidide.saf

sealed class PathResolutionResult {
    data class Resolved(
        val parentUri: String,
        val leafName: String,
        val createdIntermediateUris: List<String>,
    ) : PathResolutionResult()

    data object EmptyPath : PathResolutionResult()
    data class BlockedByFile(val createdIntermediateUris: List<String>) : PathResolutionResult()
    data class IntermediateCreationFailed(val createdIntermediateUris: List<String>) : PathResolutionResult()
    data class IntermediateNameMismatch(val createdIntermediateUris: List<String>) : PathResolutionResult()
}
