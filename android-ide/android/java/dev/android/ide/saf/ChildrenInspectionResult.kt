package dev.android.ide.saf

import dev.android.ide.viewmodel.model.FileNode

sealed class ChildrenInspectionResult {
    data class Success(val children: List<FileNode>) : ChildrenInspectionResult()
    data class Failed(val reason: String? = null) : ChildrenInspectionResult()
}
