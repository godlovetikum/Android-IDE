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
import dev.androidide.data.model.GitDetails
import dev.androidide.data.model.GitRemote
import dev.androidide.editor.EditorLanguageRegistry
import dev.androidide.viewmodel.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class ExactCreateResult {
    data class Created(val documentUri: String) : ExactCreateResult()
    data object Duplicate : ExactCreateResult()
    data object InspectionFailed : ExactCreateResult()
    data object Failed : ExactCreateResult()
}

data class DirectoryStats(
    val fileCount: Int,
    val folderCount: Int,
    val totalBytes: Long,
)

data class ZipExportResult(
    val fileCount: Int,
    val totalBytes: Long,
)

data class ProjectStorageMetadata(
    val creationTimeMs: Long?,
    val lastModifiedTimeMs: Long?,
    val storageProvider: String,
    val storagePath: String,
    val fileCount: Int,
    val folderCount: Int,
    val totalBytes: Long,
    val languageBytes: Map<String, Long>,
    val git: GitDetails?,
)

private data class MetadataEntry(
    val relativePath: String,
    val uri: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModifiedMs: Long?,
)

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
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )

            val nodes = mutableListOf<FileNode>()

            val cursor = resolver.query(childrenUri, projection, null, null, null)
                ?: return@withContext ChildrenInspectionResult.Failed("SAF returned no directory listing")
            cursor.use { cursor ->
                val idIdx   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childDocId  = cursor.getString(idIdx) ?: continue
                    val displayName = cursor.getString(nameIdx) ?: ""
                    val mimeType    = cursor.getString(mimeIdx) ?: "application/octet-stream"
                    val size        = if (cursor.isNull(sizeIdx)) 0L else cursor.getLong(sizeIdx)
                    val lastModifiedMs = if (modifiedIdx >= 0 && !cursor.isNull(modifiedIdx)) {
                        cursor.getLong(modifiedIdx)
                    } else {
                        null
                    }

                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)

                    nodes += FileNode(
                        documentUri       = docUri.toString(),
                        displayName       = displayName,
                        mimeType          = mimeType,
                        size              = size,
                        lastModifiedMs    = lastModifiedMs,
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
                    lastModifiedMs    = file.takeIf { it.isFile }?.lastModified(),
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
                    lastModifiedMs = file.lastModified(),
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

    /**
     * Build the complete metadata used by project search, sorting, and details.
     * The traversal is provider-backed and fails closed if any directory cannot
     * be inspected, so a partial scan is never presented as authoritative.
     */
    suspend fun projectMetadata(rootUriString: String): ProjectStorageMetadata? =
        withContext(Dispatchers.IO) {
            val entries = mutableListOf<MetadataEntry>()

            suspend fun walk(directoryUri: String, prefix: String): Boolean {
                val children = when (val inspection = inspectChildren(directoryUri)) {
                    is ChildrenInspectionResult.Success -> inspection.children
                    is ChildrenInspectionResult.Failed -> return false
                }
                children.forEach { child ->
                    val relativePath = if (prefix.isEmpty()) {
                        child.displayName
                    } else {
                        "$prefix/${child.displayName}"
                    }
                    entries += MetadataEntry(
                        relativePath = relativePath,
                        uri = child.documentUri,
                        isDirectory = child.isDirectory,
                        size = child.size.coerceAtLeast(0L),
                        lastModifiedMs = child.lastModifiedMs,
                    )
                    if (child.isDirectory && !walk(child.documentUri, relativePath)) {
                        return false
                    }
                }
                return true
            }

            if (!walk(rootUriString, "")) return@withContext null

            val rootTimes = rootTimes(rootUriString)
            val files = entries.filterNot { it.isDirectory }
            val languageBytes = files
                .filterNot { it.relativePath == ".git" || it.relativePath.startsWith(".git/") }
                .groupingBy { languageLabel(it.relativePath) }
                .fold(0L) { total, entry -> total + entry.size }
                .toSortedMap()

            ProjectStorageMetadata(
                creationTimeMs = rootTimes.first,
                lastModifiedTimeMs = listOfNotNull(
                    rootTimes.second,
                    entries.mapNotNull { it.lastModifiedMs }.maxOrNull(),
                ).maxOrNull(),
                storageProvider = storageProvider(rootUriString),
                storagePath = storagePath(rootUriString),
                fileCount = files.size,
                folderCount = entries.count { it.isDirectory },
                totalBytes = files.sumOf { it.size },
                languageBytes = languageBytes,
                git = gitDetails(entries),
            )
        }

    /**
     * Calculate recursive statistics for a directory. A failed provider
     * inspection returns null instead of reporting a misleading partial total.
     */
    suspend fun directoryStats(rootUriString: String): DirectoryStats? = withContext(Dispatchers.IO) {
        var fileCount = 0
        var folderCount = 0
        var totalBytes = 0L

        suspend fun walk(directoryUri: String): Boolean {
            val children = when (val inspection = inspectChildren(directoryUri)) {
                is ChildrenInspectionResult.Success -> inspection.children
                is ChildrenInspectionResult.Failed -> return false
            }
            folderCount += children.count { it.isDirectory }
            children.forEach { child ->
                if (child.isDirectory) {
                    if (!walk(child.documentUri)) return false
                } else {
                    fileCount++
                    totalBytes += child.size.coerceAtLeast(0L)
                }
            }
            return true
        }

        if (!walk(rootUriString)) return@withContext null
        DirectoryStats(fileCount, folderCount, totalBytes)
    }

    /**
     * Returns true when [candidateUriString] is the same directory as, or
     * appears below, [rootUriString]. Null means the provider could not be
     * inspected safely.
     */
    suspend fun isSameOrDescendant(
        rootUriString: String,
        candidateUriString: String,
    ): Boolean? = withContext(Dispatchers.IO) {
        if (isFileUri(rootUriString) && isFileUri(candidateUriString)) {
            val root = fileFromUri(rootUriString)?.canonicalFile ?: return@withContext null
            val candidate = fileFromUri(candidateUriString)?.canonicalFile ?: return@withContext null
            return@withContext candidate == root || candidate.toPath().startsWith(root.toPath())
        }

        fun identity(uriString: String): String? = runCatching {
            val uri = Uri.parse(uriString)
            if (DocumentsContract.isTreeUri(uri)) {
                DocumentsContract.getTreeDocumentId(uri)
            } else {
                DocumentsContract.getDocumentId(uri)
            }
        }.getOrNull()

        val candidateIdentity = identity(candidateUriString) ?: return@withContext null
        suspend fun contains(directoryUri: String): Boolean? {
            if (identity(directoryUri) == candidateIdentity) return true
            val children = when (val inspection = inspectChildren(directoryUri)) {
                is ChildrenInspectionResult.Success -> inspection.children
                is ChildrenInspectionResult.Failed -> return null
            }
            for (child in children) {
                if (identity(child.documentUri) == candidateIdentity) return true
                if (child.isDirectory) {
                    when (val found = contains(child.documentUri)) {
                        true -> return true
                        null -> return null
                        false -> Unit
                    }
                }
            }
            return false
        }
        contains(rootUriString)
    }

    /**
     * Write a directory tree as a ZIP archive to a user-selected destination.
     * The archive contains the selected directory's contents relative to its root.
     */
    suspend fun exportZip(
        sourceUriString: String,
        destinationUriString: String,
    ): ZipExportResult? = withContext(Dispatchers.IO) {
        try {
            val output = if (isFileUri(destinationUriString)) {
                fileFromUri(destinationUriString)?.outputStream()
            } else {
                resolver.openOutputStream(Uri.parse(destinationUriString), "w")
            } ?: return@withContext null

            var fileCount = 0
            var totalBytes = 0L
            ZipOutputStream(output).use { zip ->
                suspend fun addDirectory(directoryUri: String, prefix: String): Boolean {
                    val children = when (val inspection = inspectChildren(directoryUri)) {
                        is ChildrenInspectionResult.Success -> inspection.children
                        is ChildrenInspectionResult.Failed -> return false
                    }
                    for (child in children) {
                        val entryName = prefix + child.displayName
                        if (child.isDirectory) {
                            zip.putNextEntry(ZipEntry("$entryName/"))
                            zip.closeEntry()
                            if (!addDirectory(child.documentUri, "$entryName/")) return false
                        } else {
                            val bytes = readFile(child.documentUri) ?: return false
                            zip.putNextEntry(ZipEntry(entryName))
                            zip.write(bytes)
                            zip.closeEntry()
                            fileCount++
                            totalBytes += bytes.size.toLong()
                        }
                    }
                    return true
                }

                if (!addDirectory(sourceUriString, "")) return@withContext null
            }
            ZipExportResult(fileCount, totalBytes)
        } catch (e: Exception) {
            Log.e(TAG, "exportZip failed: $sourceUriString → $destinationUriString", e)
            null
        }
    }

    private fun languageLabel(path: String): String {
        val fileName = path.substringAfterLast('/')
        return if (EditorLanguageRegistry.iconKindForFileName(fileName) == FileIconKind.IMAGE) {
            "Images"
        } else {
            when (EditorLanguageRegistry.languageForFileName(fileName)) {
                EditorLanguageRegistry.PLAIN_TEXT -> "Text"
                "javascript" -> "JavaScript"
                "typescript" -> "TypeScript"
                "markdown" -> "Markdown"
                "dockerfile" -> "Dockerfile"
                "make" -> "Make"
                "shell" -> "Shell"
                else -> EditorLanguageRegistry.languageForFileName(fileName)
                    .replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun rootTimes(rootUriString: String): Pair<Long?, Long?> {
        if (isFileUri(rootUriString)) {
            val file = fileFromUri(rootUriString) ?: return null to null
            return runCatching {
                val attributes = Files.readAttributes(
                    file.toPath(),
                    BasicFileAttributes::class.java,
                )
                attributes.creationTime().toMillis() to attributes.lastModifiedTime().toMillis()
            }.getOrElse { null to file.lastModified().takeIf { it > 0L } }
        }
        val modified = queryLongColumn(rootUriString, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return null to modified
    }

    private fun storageProvider(uriString: String): String {
        if (isFileUri(uriString)) return "App-local file system"
        return Uri.parse(uriString).authority
            ?.substringBefore(".documents")
            ?.replace('.', ' ')
            ?.replaceFirstChar { it.uppercase() }
            ?: "Android Storage Access Framework"
    }

    private fun storagePath(uriString: String): String {
        if (isFileUri(uriString)) return fileFromUri(uriString)?.absolutePath ?: uriString
        return runCatching {
            val uri = Uri.parse(uriString)
            val documentId = if (DocumentsContract.isTreeUri(uri)) {
                DocumentsContract.getTreeDocumentId(uri)
            } else {
                DocumentsContract.getDocumentId(uri)
            }
            Uri.decode(documentId)
        }.getOrElse { uriString }
    }

    private suspend fun gitDetails(entries: List<MetadataEntry>): GitDetails? {
        if (entries.none { it.relativePath == ".git" }) return null
        val files = entries.filterNot { it.isDirectory }.associateBy { it.relativePath }
        val headText = readUtf8(files[".git/HEAD"]?.uri)?.trim()
        val branchRef = headText
            ?.takeIf { it.startsWith("ref: ") }
            ?.removePrefix("ref: ")
            ?.trim()
        val branch = branchRef?.removePrefix("refs/heads/")
        val packedRefs = readUtf8(files[".git/packed-refs"]?.uri)
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("^") }
            .mapNotNull { line ->
                val parts = line.split(' ', limit = 2)
                if (parts.size == 2 && parts[1].startsWith("refs/heads/")) {
                    parts[1].removePrefix("refs/heads/") to parts[0]
                } else {
                    null
                }
            }
            .toMap()
        val branchRefs = entries
            .filter { !it.isDirectory && it.relativePath.startsWith(".git/refs/heads/") }
            .associate {
                it.relativePath.removePrefix(".git/refs/heads/") to
                    (readUtf8(it.uri)?.trim().orEmpty())
            }
        val branches = (branchRefs.keys + packedRefs.keys)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val headCommit = when {
            branchRef != null -> branchRefs[branch.orEmpty()] ?: packedRefs[branch.orEmpty()]
            !headText.isNullOrBlank() && !headText.startsWith("ref: ") -> headText
            else -> null
        }?.takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) }

        val remotes = parseGitRemotes(readUtf8(files[".git/config"]?.uri).orEmpty())
        val commit = headCommit?.let {
            parseCommit(readUtf8Bytes(files[".git/objects/${it.take(2)}/${it.drop(2)}"]?.uri))
        }

        return GitDetails(
            currentBranch = branch,
            branches = branches,
            remotes = remotes,
            headCommit = headCommit,
            latestCommitMessage = commit?.first,
            latestCommitTimeMs = commit?.second,
        )
    }

    private fun parseGitRemotes(config: String): List<GitRemote> {
        var current: String? = null
        val remotes = mutableListOf<GitRemote>()
        config.lineSequence().forEach { raw ->
            val line = raw.trim()
            val section = Regex("""\[remote\s+"([^"]+)"\]""").matchEntire(line)
            if (section != null) {
                current = section.groupValues[1]
            } else if (current != null && line.startsWith("url")) {
                val url = line.substringAfter('=', "").trim()
                if (url.isNotEmpty()) {
                    remotes += GitRemote(current!!, url)
                    current = null
                }
            } else if (line.startsWith("[") && !line.startsWith("[remote")) {
                current = null
            }
        }
        return remotes
    }

    private fun parseCommit(bytes: ByteArray?): Pair<String?, Long?>? {
        if (bytes == null) return null
        return runCatching {
            val inflated = InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            val text = inflated.toString(StandardCharsets.UTF_8)
            val body = text.substringAfter("\u0000", "")
            val message = body.substringAfter("\n\n", "").trim()
                .lineSequence()
                .firstOrNull()
                ?.takeIf { it.isNotBlank() }
            val timestamp = Regex("""^committer .+ (\d+) [+-]\d+$""", RegexOption.MULTILINE)
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?.times(1000L)
            message to timestamp
        }.getOrNull()
    }

    private suspend fun readUtf8(uri: String?): String? =
        uri?.let { readFile(it)?.toString(StandardCharsets.UTF_8) }

    private suspend fun readUtf8Bytes(uri: String?): ByteArray? =
        uri?.let { readFile(it) }

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

    private fun queryLongColumn(documentUriString: String, column: String): Long? {
        return try {
            resolver.query(
                Uri.parse(documentUriString),
                arrayOf(column),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryLongColumn($column) failed for $documentUriString: ${e.message}", e)
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
