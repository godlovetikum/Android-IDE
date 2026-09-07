// android-ide/android/java/dev/androidide/saf/SafRepository.kt
//
// Storage Access Framework operations — Kotlin coroutine-based.
//
// Supports two URI schemes:
//   content://  — standard SAF tree/document URIs (from ACTION_OPEN_DOCUMENT_TREE).
//   file://     — app-local file URIs (workspace projects created without SAF picker).
//
// SAF URI shapes:
//   Tree URI:     content://com.android.externalstorage.documents/tree/primary%3AMyProject
//   Document URI: content://com.android.externalstorage.documents/document/primary%3AMyProject%2FMain.kt
//
// Usage:
//   Instantiate once in IdeViewModel (which holds Application context).
//   All methods are safe to call concurrently from Dispatchers.IO.

package dev.androidide.saf

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import dev.androidide.viewmodel.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

sealed class ExactCreateResult {
    data class Created(val documentUri: String) : ExactCreateResult()
    data object Duplicate : ExactCreateResult()
    data object InspectionFailed : ExactCreateResult()
    data object Failed : ExactCreateResult()
}

class SafRepository(private val context: Context) {

    companion object {
        private const val TAG      = "SafRepository"
        private const val MIME_DIR = "vnd.android.document/directory"
    }

    private val resolver get() = context.contentResolver

    // ── URI type helpers ───────────────────────────────────────────────────

    private fun isFileUri(uriString: String) = uriString.startsWith("file://")

    private fun fileFromUri(uriString: String): File? =
        Uri.parse(uriString).path?.let { File(it) }

    // ── Directory listing ──────────────────────────────────────────────────

    /** Read children for mutation paths; failures are never represented as an empty directory. */
    suspend fun inspectChildren(parentUriString: String): ChildrenInspectionResult = withContext(Dispatchers.IO) {
        if (isFileUri(parentUriString)) return@withContext listChildrenFileResult(parentUriString)
        try {
            val parentUri = Uri.parse(parentUriString)

            val treeUri: Uri
            val docId: String
            if (DocumentsContract.isTreeUri(parentUri)) {
                treeUri = parentUri
                docId = if (parentUri.pathSegments.contains("document")) {
                    DocumentsContract.getDocumentId(parentUri)
                } else {
                    DocumentsContract.getTreeDocumentId(parentUri)
                }
            } else {
                treeUri = parentUri
                docId = DocumentsContract.getDocumentId(parentUri)
            }

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            )

            val nodes = mutableListOf<FileNode>()

            val cursor = resolver.query(childrenUri, projection, null, null, null)
                ?: return@withContext ChildrenInspectionResult.Failed("SAF returned no directory listing")
            cursor.use { cursor ->
                val idIdx   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val childDocId  = cursor.getString(idIdx) ?: continue
                    val displayName = cursor.getString(nameIdx) ?: ""
                    val mimeType    = cursor.getString(mimeIdx) ?: "application/octet-stream"
                    val size        = if (cursor.isNull(sizeIdx)) 0L else cursor.getLong(sizeIdx)

                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)

                    nodes += FileNode(
                        documentUri       = docUri.toString(),
                        displayName       = displayName,
                        mimeType          = mimeType,
                        size              = size,
                        parentDocumentUri = parentUriString,
                    )
                }
            }

            ChildrenInspectionResult.Success(nodes.sortedWith(
                compareByDescending<FileNode> { it.isDirectory }.thenBy { it.displayName.lowercase() }
            ))
        } catch (e: Exception) {
            Log.e(TAG, "listChildren failed for $parentUriString: ${e.message}", e)
            ChildrenInspectionResult.Failed(e.message)
        }
    }

    /** List children for UI/read-only callers; mutation paths must use inspectChildren. */
    suspend fun listChildren(parentUriString: String): List<FileNode> =
        (inspectChildren(parentUriString) as? ChildrenInspectionResult.Success)?.children ?: emptyList()

    private fun listChildrenFile(parentUriString: String): List<FileNode> {
        return try {
            val dir = fileFromUri(parentUriString) ?: return emptyList()
            if (!dir.isDirectory) return emptyList()
            (dir.listFiles() ?: return emptyList()).map { file ->
                FileNode(
                    documentUri       = Uri.fromFile(file).toString(),
                    displayName       = file.name,
                    mimeType          = if (file.isDirectory) MIME_DIR else "application/octet-stream",
                    size              = if (file.isFile) file.length() else 0L,
                    parentDocumentUri = parentUriString,
                )
            }.sortedWith(
                compareByDescending<FileNode> { it.isDirectory }.thenBy { it.displayName.lowercase() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "listChildrenFile failed for $parentUriString: ${e.message}", e)
            emptyList()
        }
    }

    private fun listChildrenFileResult(parentUriString: String): ChildrenInspectionResult {
        return try {
            val dir = fileFromUri(parentUriString)
                ?: return ChildrenInspectionResult.Failed("Invalid file URI")
            if (!dir.isDirectory) return ChildrenInspectionResult.Failed("Parent is not a directory")
            val files = dir.listFiles() ?: return ChildrenInspectionResult.Failed("Directory listing failed")
            ChildrenInspectionResult.Success(files.map { file ->
                FileNode(
                    documentUri = Uri.fromFile(file).toString(),
                    displayName = file.name,
                    mimeType = if (file.isDirectory) MIME_DIR else "application/octet-stream",
                    size = if (file.isFile) file.length() else 0L,
                    parentDocumentUri = parentUriString,
                )
            }.sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.displayName.lowercase() }))
        } catch (e: Exception) {
            Log.e(TAG, "listChildrenFile failed for $parentUriString: ${e.message}", e)
            ChildrenInspectionResult.Failed(e.message)
        }
    }

    // ── File reading ───────────────────────────────────────────────────────

    /**
     * Read the full content of a SAF document URI or file:// URI as a byte array.
     *
     * @param documentUriString  SAF document URI or file:// URI
     * @return                   File bytes, or null on failure.
     */
    suspend fun readFile(documentUriString: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (isFileUri(documentUriString)) {
                fileFromUri(documentUriString)?.readBytes()
            } else {
                resolver.openInputStream(Uri.parse(documentUriString))?.use { stream ->
                    stream.readAllBytesCompat()
                } ?: run {
                    Log.e(TAG, "readFile: openInputStream returned null for $documentUriString")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readFile failed for $documentUriString: ${e.message}", e)
            null
        }
    }

    // ── File writing ───────────────────────────────────────────────────────

    /**
     * Write [data] to a document, replacing its entire content.
     *
     * @param documentUriString  SAF document URI or file:// URI
     * @param data               Bytes to write
     * @return                   true on success, false on failure.
     */
    suspend fun writeFile(documentUriString: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isFileUri(documentUriString)) {
                val file = fileFromUri(documentUriString) ?: return@withContext false
                file.parentFile?.mkdirs()
                file.writeBytes(data)
                true
            } else {
                resolver.openOutputStream(Uri.parse(documentUriString), "wt")?.use { stream ->
                    stream.write(data)
                    stream.flush()
                    true
                } ?: run {
                    Log.e(TAG, "writeFile: openOutputStream returned null for $documentUriString")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeFile failed for $documentUriString: ${e.message}", e)
            false
        }
    }

    // ── Document creation ──────────────────────────────────────────────────

    /**
     * Create a new document or directory inside a parent.
     *
     * [parentUriString] may be a SAF tree URI, a SAF document URI, or a file:// URI.
     * Pass mimeType = "vnd.android.document/directory" to create a sub-folder.
     *
     * @return  Document URI of the created file, or null on failure.
     */
    suspend fun createFile(
        parentUriString: String,
        displayName: String,
        mimeType: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (isFileUri(parentUriString)) {
                val parentDir = fileFromUri(parentUriString) ?: return@withContext null
                val newFile = File(parentDir, displayName)
                val success = if (mimeType == MIME_DIR) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    newFile.createNewFile()
                }
                if (success) Uri.fromFile(newFile).toString() else null
            } else {
                val parentUri = Uri.parse(parentUriString)
                val docUri = if (DocumentsContract.isTreeUri(parentUri) &&
                    !parentUri.pathSegments.contains("document")) {
                    DocumentsContract.buildDocumentUriUsingTree(
                        parentUri, DocumentsContract.getTreeDocumentId(parentUri)
                    )
                } else {
                    parentUri
                }
                DocumentsContract.createDocument(resolver, docUri, mimeType, displayName)?.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "createFile failed (parent=$parentUriString, name=$displayName): ${e.message}", e)
            null
        }
    }

    /**
     * Create a child only when [displayName] is still available in the live
     * parent directory. Some SAF providers silently rename collisions, so the
     * created display name is verified and an unexpected entry is removed.
     */
    suspend fun createFileWithExactName(
        parentUriString: String,
        displayName: String,
        mimeType: String,
    ): ExactCreateResult = withContext(Dispatchers.IO) {
        val existing = (inspectChildren(parentUriString) as? ChildrenInspectionResult.Success)?.children
            ?: return@withContext ExactCreateResult.InspectionFailed
        if (existing.any { existingNode ->
                namesMatchExactly(existingNode.displayName, displayName) &&
                    conflictsWithRequestedEntry(existingNode, mimeType)
            }) {
            return@withContext ExactCreateResult.Duplicate
        }

        val createdUri = createFile(parentUriString, displayName, mimeType)
            ?: return@withContext ExactCreateResult.Failed
        val createdName = getDisplayName(createdUri)
        val siblingsAfterCreate = (inspectChildren(parentUriString) as? ChildrenInspectionResult.Success)?.children
            ?: run {
                return@withContext ExactCreateResult.InspectionFailed
            }
        val siblingConflict = siblingsAfterCreate.any {
            it.documentUri != createdUri &&
                namesMatchExactly(it.displayName, displayName) &&
                conflictsWithRequestedEntry(it, mimeType)
        }
        if (createdName == null) {
            return@withContext ExactCreateResult.InspectionFailed
        }
        if (createdName != displayName || siblingConflict) {
            deleteDocument(createdUri)
            return@withContext ExactCreateResult.Duplicate
        }
        ExactCreateResult.Created(createdUri)
    }

    /** File names are compared as complete names; prefixes such as `.env` and `.env.example` never match. */
    private fun namesMatchExactly(existingName: String, requestedName: String): Boolean =
        existingName.equals(requestedName, ignoreCase = true)

    /**
     * Files and folders share the same provider namespace, but folder creation
     * deliberately permits a same-named file when that file has a real extension.
     * A no-extension file such as `.env` remains a blocking entry.
     */
    private fun conflictsWithRequestedEntry(existing: FileNode, requestedMimeType: String): Boolean {
        if (requestedMimeType != MIME_DIR) return true
        if (existing.isDirectory) return true
        return !hasFileExtension(existing.displayName)
    }

    private fun hasFileExtension(displayName: String): Boolean {
        val dot = displayName.lastIndexOf('.')
        return dot > 0 && dot < displayName.lastIndex
    }

    // ── Copy ───────────────────────────────────────────────────────────────

    /**
     * Copy a document to [targetParentUriString], optionally renaming it to [newName].
     *
     * Implemented as: read → createFile → write. Works across providers.
     *
     * @return  Document URI of the new copy, or null on failure.
     */
    suspend fun copyDocument(
        sourceUriString: String,
        targetParentUriString: String,
        newName: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bytes       = readFile(sourceUriString) ?: return@withContext null
            val displayName = newName
                ?: getDisplayName(sourceUriString)
                ?: return@withContext null
            val mimeType    = if (isFileUri(sourceUriString)) {
                "application/octet-stream"
            } else {
                queryStringColumn(sourceUriString, DocumentsContract.Document.COLUMN_MIME_TYPE) ?: "text/plain"
            }
            val newUri = createFile(targetParentUriString, displayName, mimeType)
                ?: return@withContext null
            if (writeFile(newUri, bytes)) newUri else null
        } catch (e: Exception) {
            Log.e(TAG, "copyDocument failed: $sourceUriString → $targetParentUriString", e)
            null
        }
    }

    suspend fun copyDocumentWithExactName(
        sourceUriString: String,
        targetParentUriString: String,
        newName: String? = null,
    ): SafeMutationResult = withContext(Dispatchers.IO) {
        val displayName = newName ?: getDisplayName(sourceUriString)
            ?: return@withContext SafeMutationResult.Failed
        val sourceIsDirectory = if (isFileUri(sourceUriString)) {
            fileFromUri(sourceUriString)?.isDirectory == true
        } else {
            queryStringColumn(sourceUriString, DocumentsContract.Document.COLUMN_MIME_TYPE) == MIME_DIR
        }
        if (sourceIsDirectory) {
            return@withContext copyDirectoryWithExactName(sourceUriString, targetParentUriString, displayName)
        }
        val bytes = readFile(sourceUriString) ?: return@withContext SafeMutationResult.Failed
        when (val created = createFileWithExactName(
            targetParentUriString,
            displayName,
            if (isFileUri(sourceUriString)) "application/octet-stream"
            else queryStringColumn(sourceUriString, DocumentsContract.Document.COLUMN_MIME_TYPE) ?: "text/plain",
        )) {
            is ExactCreateResult.Created -> {
                if (writeFile(created.documentUri, bytes)) SafeMutationResult.Created(created.documentUri)
                else {
                    deleteDocument(created.documentUri)
                    SafeMutationResult.Failed
                }
            }
            ExactCreateResult.Duplicate -> SafeMutationResult.Duplicate
            ExactCreateResult.InspectionFailed -> SafeMutationResult.InspectionFailed
            ExactCreateResult.Failed -> SafeMutationResult.Failed
        }
    }

    private suspend fun copyDirectoryWithExactName(
        sourceUriString: String,
        targetParentUriString: String,
        displayName: String,
    ): SafeMutationResult {
        val createdRoot = when (val created = createFileWithExactName(
            targetParentUriString,
            displayName,
            MIME_DIR,
        )) {
            is ExactCreateResult.Created -> created.documentUri
            ExactCreateResult.Duplicate -> return SafeMutationResult.Duplicate
            ExactCreateResult.InspectionFailed -> return SafeMutationResult.InspectionFailed
            ExactCreateResult.Failed -> return SafeMutationResult.Failed
        }
        val children = when (val inspection = inspectChildren(sourceUriString)) {
            is ChildrenInspectionResult.Success -> inspection.children
            is ChildrenInspectionResult.Failed -> {
                deleteDocument(createdRoot)
                return SafeMutationResult.InspectionFailed
            }
        }
        for (child in children) {
            when (copyDocumentWithExactName(child.documentUri, createdRoot)) {
                is SafeMutationResult.Created -> Unit
                else -> {
                    deleteDocument(createdRoot)
                    return SafeMutationResult.Failed
                }
            }
        }
        return SafeMutationResult.Created(createdRoot)
    }

    suspend fun moveDocumentWithExactName(
        sourceUriString: String,
        sourceParentUriString: String,
        targetParentUriString: String,
    ): SafeMutationResult = withContext(Dispatchers.IO) {
        val name = getDisplayName(sourceUriString) ?: return@withContext SafeMutationResult.Failed
        val sourceMime = if (isFileUri(sourceUriString)) {
            if (fileFromUri(sourceUriString)?.isDirectory == true) MIME_DIR else "application/octet-stream"
        } else {
            queryStringColumn(sourceUriString, DocumentsContract.Document.COLUMN_MIME_TYPE)
        }
        val targetChildren = when (val inspection = inspectChildren(targetParentUriString)) {
            is ChildrenInspectionResult.Success -> inspection.children
            is ChildrenInspectionResult.Failed -> return@withContext SafeMutationResult.InspectionFailed
        }
        if (targetChildren.any {
                it.documentUri != sourceUriString &&
                    namesMatchExactly(it.displayName, name) &&
                    conflictsWithRequestedEntry(it, sourceMime ?: "application/octet-stream")
            }) {
            return@withContext SafeMutationResult.Duplicate
        }
        if (sourceMime != MIME_DIR) {
            return@withContext when (val copied = copyDocumentWithExactName(
                sourceUriString,
                targetParentUriString,
                name,
            )) {
                is SafeMutationResult.Created -> {
                    if (deleteDocument(sourceUriString)) copied else SafeMutationResult.Failed
                }
                else -> copied
            }
        }
        val moved = moveDocument(sourceUriString, sourceParentUriString, targetParentUriString)
            ?: return@withContext when (val copied = copyDocumentWithExactName(
                sourceUriString,
                targetParentUriString,
                name,
            )) {
                is SafeMutationResult.Created -> {
                    if (deleteDocument(sourceUriString)) copied else SafeMutationResult.Failed
                }
                else -> copied
            }
        val movedName = getDisplayName(moved)
        if (movedName != name) return@withContext SafeMutationResult.Failed
        SafeMutationResult.Created(moved)
    }

    suspend fun moveAndRenameDocumentWithExactName(
        sourceUriString: String,
        sourceParentUriString: String,
        targetParentUriString: String,
        newName: String,
    ): SafeMutationResult = withContext(Dispatchers.IO) {
        val originalName = getDisplayName(sourceUriString)
            ?: return@withContext SafeMutationResult.Failed
        val targetChildren = when (val inspection = inspectChildren(targetParentUriString)) {
            is ChildrenInspectionResult.Success -> inspection.children
            is ChildrenInspectionResult.Failed -> return@withContext SafeMutationResult.InspectionFailed
        }
        if (targetChildren.any {
                it.documentUri != sourceUriString && it.displayName.equals(newName, ignoreCase = true)
            }) return@withContext SafeMutationResult.Duplicate

        if (sourceParentUriString == targetParentUriString) {
            val renamed = renameDocument(sourceUriString, newName)
                ?: return@withContext SafeMutationResult.Failed
            return@withContext if (getDisplayName(renamed) == newName) {
                SafeMutationResult.Created(renamed)
            } else {
                SafeMutationResult.Failed
            }
        }

        val moved = moveDocument(sourceUriString, sourceParentUriString, targetParentUriString)
            ?: return@withContext SafeMutationResult.Failed
        val renamed = renameDocument(moved, newName)
            ?: run {
                moveDocument(moved, targetParentUriString, sourceParentUriString)
                return@withContext SafeMutationResult.Failed
            }
        if (getDisplayName(renamed) == newName) {
            SafeMutationResult.Created(renamed)
        } else {
            val restored = renameDocument(renamed, originalName)
            if (restored != null) moveDocument(restored, targetParentUriString, sourceParentUriString)
            SafeMutationResult.Failed
        }
    }

    // ── Move ───────────────────────────────────────────────────────────────

    /**
     * Move a document to [targetParentUriString].
     *
     * Uses [DocumentsContract.moveDocument] on API 24+ for SAF URIs.
     * Falls back to copy + delete on older APIs or if native move fails.
     * For file:// URIs, uses File.renameTo first, then copy+delete fallback.
     *
     * @return  New document URI, or null on failure.
     */
    suspend fun moveDocument(
        sourceUriString: String,
        sourceParentUriString: String,
        targetParentUriString: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (isFileUri(sourceUriString)) {
                val src = fileFromUri(sourceUriString) ?: return@withContext null
                val dst = fileFromUri(targetParentUriString)?.let { File(it, src.name) }
                    ?: return@withContext null
                if (src.renameTo(dst)) return@withContext Uri.fromFile(dst).toString()
                // Fallback: copy + delete
                val newUri = copyDocument(sourceUriString, targetParentUriString)
                    ?: return@withContext null
                deleteDocument(sourceUriString)
                return@withContext newUri
            }
            // SAF path: try native move first (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val moved = runCatching {
                    DocumentsContract.moveDocument(
                        resolver,
                        Uri.parse(sourceUriString),
                        Uri.parse(sourceParentUriString),
                        Uri.parse(targetParentUriString),
                    )?.toString()
                }.getOrNull()
                if (moved != null) return@withContext moved
            }
            // Fallback: copy then delete the original
            val newUri = copyDocument(sourceUriString, targetParentUriString)
                ?: return@withContext null
            deleteDocument(sourceUriString)
            newUri
        } catch (e: Exception) {
            Log.e(TAG, "moveDocument failed: $sourceUriString → $targetParentUriString", e)
            null
        }
    }

    // ── Deletion ───────────────────────────────────────────────────────────

    /**
     * Delete a document (file or directory, recursively for file:// directories).
     *
     * @param documentUriString  SAF document URI or file:// URI
     * @return                   true if deleted, false on failure.
     */
    suspend fun deleteDocument(documentUriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isFileUri(documentUriString)) {
                fileFromUri(documentUriString)?.deleteRecursively() ?: false
            } else {
                DocumentsContract.deleteDocument(resolver, Uri.parse(documentUriString))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteDocument failed for $documentUriString: ${e.message}", e)
            false
        }
    }

    // ── Rename ─────────────────────────────────────────────────────────────

    /**
     * Rename a document.
     *
     * IMPORTANT (SAF): Android may assign a different URI after renaming. Always use
     * the returned URI for subsequent operations, not the original.
     *
     * @param documentUriString  SAF document URI or file:// URI
     * @param newDisplayName     New display name
     * @return                   New document URI, or null on failure.
     */
    suspend fun renameDocument(documentUriString: String, newDisplayName: String): String? =
        withContext(Dispatchers.IO) {
            try {
                if (isFileUri(documentUriString)) {
                    val file = fileFromUri(documentUriString) ?: return@withContext null
                    val newFile = File(file.parent ?: return@withContext null, newDisplayName)
                    if (file.renameTo(newFile)) Uri.fromFile(newFile).toString() else null
                } else {
                    DocumentsContract.renameDocument(
                        resolver,
                        Uri.parse(documentUriString),
                        newDisplayName,
                    )?.toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "renameDocument failed for $documentUriString: ${e.message}", e)
                null
            }
        }

    // ── Metadata ───────────────────────────────────────────────────────────

    /**
     * Get the display name of a document.
     */
    suspend fun getDisplayName(documentUriString: String): String? =
        withContext(Dispatchers.IO) {
            if (isFileUri(documentUriString)) {
                fileFromUri(documentUriString)?.name
            } else {
                queryStringColumn(documentUriString, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            }
        }

    // ── Private helpers ────────────────────────────────────────────────────

    private suspend fun queryStringColumn(documentUriString: String, column: String): String? =
        withContext(Dispatchers.IO) {
            try {
                resolver.query(
                    Uri.parse(documentUriString),
                    arrayOf(column),
                    null, null, null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "queryStringColumn($column) failed for $documentUriString: ${e.message}", e)
                null
            }
        }

    // ── Project-path resolution ───────────────────────────────────────────────

    /**
     * Walk [segments] from [treeUriString], creating intermediate directories as
     * needed. Returns [Pair(parentDirUri, leafName)] so the caller can create or
     * check for the final path component without creating it here.
     * Returns null on any SAF failure.
     *
     * Example: resolveOrCreatePath(rootUri, ["src","utils","Foo.kt"])
     *   → ensures src/ and src/utils/ exist, returns (src/utils URI, "Foo.kt")
     */
    suspend fun resolveOrCreatePath(
        treeUriString: String,
        segments: List<String>,
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        when (val result = resolveOrCreatePathSafely(treeUriString, segments)) {
            is PathResolutionResult.Resolved -> Pair(result.parentUri, result.leafName)
            else -> null
        }
    }

    /**
     * Resolve all intermediate components before the final item is created.
     * Existing directories are reused case-insensitively; an existing file
     * blocks the path. Missing directories use the exact-name creation path so
     * SAF providers cannot silently create renamed siblings.
     */
    suspend fun resolveOrCreatePathSafely(
        treeUriString: String,
        segments: List<String>,
    ): PathResolutionResult = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext PathResolutionResult.EmptyPath

        var currentUri = treeUriString
        val created = mutableListOf<String>()
        for (segment in segments.dropLast(1)) {
            val children = when (val inspection = inspectChildren(currentUri)) {
                is ChildrenInspectionResult.Success -> inspection.children
                is ChildrenInspectionResult.Failed ->
                    return@withContext PathResolutionResult.IntermediateCreationFailed(created.toList())
            }
            val matching = children.firstOrNull { it.displayName.equals(segment, ignoreCase = true) }
            if (matching != null) {
                if (!matching.isDirectory) {
                    return@withContext PathResolutionResult.BlockedByFile(created.toList())
                }
                currentUri = matching.documentUri
                continue
            }

            when (val result = createFileWithExactName(currentUri, segment, MIME_DIR)) {
                is ExactCreateResult.Created -> {
                    val createdNode = when (val inspection = inspectChildren(currentUri)) {
                        is ChildrenInspectionResult.Success -> inspection.children.firstOrNull {
                            it.documentUri == result.documentUri
                        }
                        is ChildrenInspectionResult.Failed -> {
                            return@withContext PathResolutionResult.IntermediateNameMismatch(created.toList())
                        }
                    }
                    if (createdNode == null || !createdNode.isDirectory ||
                        !createdNode.displayName.equals(segment, ignoreCase = false)
                    ) {
                        deleteDocument(result.documentUri)
                        return@withContext PathResolutionResult.IntermediateNameMismatch(created.toList())
                    }
                    created += result.documentUri
                    currentUri = result.documentUri
                }
                ExactCreateResult.Duplicate -> {
                    return@withContext PathResolutionResult.IntermediateCreationFailed(created.toList())
                }
                ExactCreateResult.InspectionFailed -> {
                    return@withContext PathResolutionResult.IntermediateCreationFailed(created.toList())
                }
                ExactCreateResult.Failed -> {
                    return@withContext PathResolutionResult.IntermediateCreationFailed(created.toList())
                }
            }
        }
        PathResolutionResult.Resolved(currentUri, segments.last(), created.toList())
    }

    /** Delete only directories created by the current path-resolution attempt. */
    suspend fun rollbackCreatedDirectories(documentUris: List<String>): Boolean {
        var allDeleted = true
        for (uri in documentUris.asReversed()) {
            val children = when (val inspection = inspectChildren(uri)) {
                is ChildrenInspectionResult.Success -> inspection.children
                is ChildrenInspectionResult.Failed -> {
                    allDeleted = false
                    continue
                }
            }
            if (children.isNotEmpty() || !deleteDocument(uri)) allDeleted = false
        }
        return allDeleted
    }
}

// ── InputStream extension ─────────────────────────────────────────────────────

/**
 * Read all bytes from the stream. Compatible with API 26+.
 */
@Throws(IOException::class)
private fun InputStream.readAllBytesCompat(): ByteArray {
    val buf = ByteArrayOutputStream(8192)
    val chunk = ByteArray(8192)
    var n: Int
    while (read(chunk).also { n = it } != -1) {
        buf.write(chunk, 0, n)
    }
    return buf.toByteArray()
}
