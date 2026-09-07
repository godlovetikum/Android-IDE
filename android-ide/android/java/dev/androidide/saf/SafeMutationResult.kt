package dev.androidide.saf

sealed class SafeMutationResult {
    data class Created(val documentUri: String) : SafeMutationResult()
    data object Duplicate : SafeMutationResult()
    data object InspectionFailed : SafeMutationResult()
    data object Failed : SafeMutationResult()
}
