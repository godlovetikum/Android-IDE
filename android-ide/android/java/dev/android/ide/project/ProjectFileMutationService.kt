package dev.android.ide.project

import dev.android.ide.saf.ExactCreateResult
import dev.android.ide.saf.SafeMutationResult
import dev.android.ide.viewmodel.model.FileNode

/**
 * Single domain entry point for project file and folder mutations.
 *
 * Provider-specific behavior stays in the storage adapter. Callers such as the
 * project manager and editor use this facade rather than embedding SAF calls,
 * so exact-name checks, copy/move semantics, and deletion verification remain
 * consistent across the application.
 */
class ProjectFileMutationService(private val storage: ProjectStorageAdapterImpl) {
    suspend fun createFolder(parentUri: String, name: String): ExactCreateResult =
        storage.createDirectoryWithExactName(parentUri, name)

    suspend fun createFile(parentUri: String, name: String, mimeType: String): ExactCreateResult =
        storage.createFileWithExactName(parentUri, name, mimeType)

    suspend fun copy(sourceUri: String, targetParentUri: String, newName: String? = null): SafeMutationResult =
        storage.copyExact(sourceUri, targetParentUri, newName)

    suspend fun move(sourceUri: String, sourceParentUri: String, targetParentUri: String): SafeMutationResult =
        storage.moveExact(sourceUri, sourceParentUri, targetParentUri)

    suspend fun moveAndRename(
        sourceUri: String,
        sourceParentUri: String,
        targetParentUri: String,
        newName: String,
    ): SafeMutationResult = storage.moveAndRenameExact(
        sourceUri,
        sourceParentUri,
        targetParentUri,
        newName,
    )

    suspend fun rename(sourceUri: String, newName: String): String? =
        storage.renameDocument(sourceUri, newName)

    /** Deletes only the selected entry and reports success only after absence is verified. */
    suspend fun delete(sourceUri: String): Boolean =
        storage.deleteDocument(sourceUri) &&
            storage.documentPresence(sourceUri) == dev.android.ide.saf.DocumentPresence.ABSENT

    suspend fun deleteChildIfPresent(parentUri: String, childName: String): Boolean =
        storage.deleteChildIfPresent(parentUri, childName)

    suspend fun exists(sourceUri: String): Boolean = storage.documentExists(sourceUri)

    suspend fun listChildren(parentUri: String): List<FileNode> = storage.listChildren(parentUri)

    suspend fun inspectChildren(parentUri: String): dev.android.ide.saf.ChildrenInspectionResult =
        storage.inspectChildren(parentUri)

    suspend fun read(sourceUri: String): ByteArray? = storage.readDocument(sourceUri)

    suspend fun write(sourceUri: String, content: ByteArray): Boolean =
        storage.writeDocument(sourceUri, content)
}
