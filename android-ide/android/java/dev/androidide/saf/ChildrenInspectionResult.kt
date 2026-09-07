package dev.androidide.saf

import dev.androidide.viewmodel.model.FileNode

sealed class ChildrenInspectionResult {
    data class Success(val children: List<FileNode>) : ChildrenInspectionResult()
    data class Failed(val reason: String? = null) : ChildrenInspectionResult()
}
