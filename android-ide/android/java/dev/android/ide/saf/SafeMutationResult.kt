package dev.android.ide.saf

sealed class SafeMutationResult {
    data class Created(val documentUri: String) : SafeMutationResult()
    data class Partial(
        val sourceUri: String,
        val destinationUri: String,
        val recoveryHint: String,
    ) : SafeMutationResult()
    data object Duplicate : SafeMutationResult()
    data object InspectionFailed : SafeMutationResult()
    data object Failed : SafeMutationResult()
}
