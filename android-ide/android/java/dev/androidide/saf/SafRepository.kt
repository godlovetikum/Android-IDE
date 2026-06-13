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

    /**
     * List the immediate children of a tree/document URI or a file:// directory URI.
     *
     * @param parentUriString  SAF tree/document URI or file:// URI string
     * @return                 List of [FileNode], sorted: directories first.
     *                         Empty on failure.
     */
    suspend fun listChildren(parentUriString: String): List<FileNode> = withContext(Dispatchers.IO) {
        if (isFileUri(parentUriString)) return@withContext listChildrenFile(parentUriString)
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

            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
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
            } ?: Log.e(TAG, "listChildren: null cursor for $parentUriString")

            nodes.sortedWith(
                compareByDescending<FileNode> { it.isDirectory }.thenBy { it.displayName.lowercase() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "listChildren failed for $parentUriString: ${e.message}", e)
            emptyList()
        }
    }

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
