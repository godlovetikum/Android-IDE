// android-ide/android/java/dev/androidide/saf/SafRepository.kt
//
// Storage Access Framework operations — Kotlin coroutine-based.
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
import java.io.IOException
import java.io.InputStream

class SafRepository(private val context: Context) {

    companion object {
        private const val TAG = "SafRepository"
        private const val MIME_DIR = "vnd.android.document/directory"
    }

    private val resolver get() = context.contentResolver

    // ── Directory listing ──────────────────────────────────────────────────

    /**
     * List the immediate children of a SAF tree or document URI.
     *
     * Pass a tree URI (from Intent.ACTION_OPEN_DOCUMENT_TREE) to list the
     * project root. Pass a child directory document URI to list subdirectories.
     *
     * @param parentUriString  SAF tree or document URI string
     * @return                 List of [FileNode], sorted: directories first.
     *                         Empty on failure.
     */
    suspend fun listChildren(parentUriString: String): List<FileNode> = withContext(Dispatchers.IO) {
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

    // ── File reading ───────────────────────────────────────────────────────

    /**
     * Read the full content of a SAF document URI as a byte array.
     *
     * @param documentUriString  SAF document URI
     * @return                   File bytes, or null on failure.
     */
    suspend fun readFile(documentUriString: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(Uri.parse(documentUriString))?.use { stream ->
                stream.readAllBytesCompat()
            } ?: run {
                Log.e(TAG, "readFile: openInputStream returned null for $documentUriString")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "readFile failed for $documentUriString: ${e.message}", e)
            null
        }
    }

    // ── File writing ───────────────────────────────────────────────────────

    /**
     * Write [data] to a SAF document, replacing its entire content.
     *
     * Uses open mode "wt" (write + truncate). The previous content is
     * discarded before writing.
     *
     * @param documentUriString  SAF document URI
     * @param data               Bytes to write
     * @return                   true on success, false on failure.
     */
    suspend fun writeFile(documentUriString: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.openOutputStream(Uri.parse(documentUriString), "wt")?.use { stream ->
                stream.write(data)
                stream.flush()
                true
            } ?: run {
                Log.e(TAG, "writeFile: openOutputStream returned null for $documentUriString")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeFile failed for $documentUriString: ${e.message}", e)
            false
        }
    }

    // ── Document creation ──────────────────────────────────────────────────

    /**
     * Create a new document inside a SAF directory.
     *
     * [parentUriString] may be either a plain tree URI (from ACTION_OPEN_DOCUMENT_TREE)
     * or a document URI (from buildDocumentUriUsingTree). Both are handled correctly.
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
            val mimeType    = queryStringColumn(
                sourceUriString, DocumentsContract.Document.COLUMN_MIME_TYPE
            ) ?: "text/plain"
            val newUri      = createFile(targetParentUriString, displayName, mimeType)
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
     * Uses [DocumentsContract.moveDocument] on API 24+ when available.
     * Falls back to copy + delete on older APIs or if native move fails.
     *
     * @param sourceUriString        SAF document URI to move.
     * @param sourceParentUriString  SAF document URI of the source's parent directory
     *                               (required by the native moveDocument API).
     * @param targetParentUriString  SAF document URI of the target directory.
     * @return                       New document URI, or null on failure.
     */
    suspend fun moveDocument(
        sourceUriString: String,
        sourceParentUriString: String,
        targetParentUriString: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Try native move first (API 24+)
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
     * Delete a SAF document (file or empty directory).
     *
     * @param documentUriString  SAF document URI
     * @return                   true if deleted, false on failure.
     */
    suspend fun deleteDocument(documentUriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(resolver, Uri.parse(documentUriString))
        } catch (e: Exception) {
            Log.e(TAG, "deleteDocument failed for $documentUriString: ${e.message}", e)
            false
        }
    }

    // ── Rename ─────────────────────────────────────────────────────────────

    /**
     * Rename a SAF document.
     *
     * IMPORTANT: Android may assign a different URI after renaming. Always use
     * the returned URI for subsequent operations, not the original.
     *
     * @param documentUriString  SAF document URI to rename
     * @param newDisplayName     New display name
     * @return                   New document URI, or null on failure.
     */
    suspend fun renameDocument(documentUriString: String, newDisplayName: String): String? =
        withContext(Dispatchers.IO) {
            try {
                DocumentsContract.renameDocument(
                    resolver,
                    Uri.parse(documentUriString),
                    newDisplayName,
                )?.toString()
            } catch (e: Exception) {
                Log.e(TAG, "renameDocument failed for $documentUriString: ${e.message}", e)
                null
            }
        }

    // ── Metadata ───────────────────────────────────────────────────────────

    /**
     * Get the display name of a SAF document.
     */
    suspend fun getDisplayName(documentUriString: String): String? =
        queryStringColumn(documentUriString, DocumentsContract.Document.COLUMN_DISPLAY_NAME)

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
