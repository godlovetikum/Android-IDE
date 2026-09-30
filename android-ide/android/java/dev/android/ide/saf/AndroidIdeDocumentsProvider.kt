package dev.android.ide.saf

import android.database.Cursor
import android.database.MatrixCursor
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Exposes only Android IDE's durable user-files root through SAF.
 *
 * The Termux bootstrap, package database, PTYs, and runtime state live outside
 * this root and are intentionally never returned by this provider.
 */
class AndroidIdeDocumentsProvider : DocumentsProvider() {
    private val rootDirectory: File
        get() = requireNotNull(context).filesDir.resolve(USER_FILES_DIRECTORY)

    override fun onCreate(): Boolean {
        rootDirectory.mkdirs()
        return rootDirectory.isDirectory
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(resolveProjection(projection, ROOT_PROJECTION))
        rootDirectory.mkdirs()
        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
            add(DocumentsContract.Root.COLUMN_TITLE, "Android IDE files")
            add(DocumentsContract.Root.COLUMN_SUMMARY, "Development files accessible through SAF")
            add(DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                    DocumentsContract.Root.FLAG_SUPPORTS_RECENTS or
                    DocumentsContract.Root.FLAG_SUPPORTS_SEARCH)
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*\n${DocumentsContract.Document.MIME_TYPE_DIR}")
            add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, rootDirectory.usableSpace)
            add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_save)
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = fileForDocumentId(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return MatrixCursor(resolveProjection(projection, DOCUMENT_PROJECTION)).also { cursor ->
            appendDocument(cursor, documentId, file)
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val parent = fileForDocumentId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        val result = MatrixCursor(resolveProjection(projection, DOCUMENT_PROJECTION))
        parent.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { child ->
                appendDocument(result, documentIdForFile(child), child)
            }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = fileForDocumentId(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(file, modeFlags(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileForDocumentId(parentDocumentId)
        if (!parent.isDirectory || displayName.isBlank() || displayName.contains('/') || displayName.contains('\\')) {
            throw FileNotFoundException("Invalid document name")
        }
        val target = File(parent, displayName)
        if (target.exists()) throw FileNotFoundException("Document already exists: $displayName")
        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) target.mkdirs() else target.createNewFile()
        if (!created) throw IOException("Could not create document: $displayName")
        return documentIdForFile(target)
    }

    override fun deleteDocument(documentId: String) {
        val file = fileForDocumentId(documentId)
        if (file == rootDirectory || !file.exists() || !file.deleteRecursively()) {
            throw FileNotFoundException("Could not delete document: $documentId")
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = fileForDocumentId(documentId)
        if (file == rootDirectory || displayName.isBlank() || displayName.contains('/') || displayName.contains('\\')) {
            throw FileNotFoundException("Invalid document name")
        }
        val target = File(file.parentFile, displayName)
        if (target.exists() || !file.renameTo(target)) throw IOException("Could not rename document")
        return documentIdForFile(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = runCatching {
        val parent = fileForDocumentId(parentDocumentId).canonicalFile.toPath()
        val child = fileForDocumentId(documentId).canonicalFile.toPath()
        child.startsWith(parent) && child != parent
    }.getOrDefault(false)

    override fun queryRecentDocuments(rootId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(resolveProjection(projection, DOCUMENT_PROJECTION))

    private fun appendDocument(cursor: MatrixCursor, documentId: String, file: File) {
        val isDirectory = file.isDirectory
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name.ifBlank { "Android IDE files" })
            add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                if (isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else mimeType(file.name))
            add(DocumentsContract.Document.COLUMN_FLAGS,
                (if (isDirectory) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0) or
                    DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME)
            add(DocumentsContract.Document.COLUMN_SIZE, if (isDirectory) 0L else file.length())
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(DocumentsContract.Document.COLUMN_ICON, android.R.drawable.ic_menu_save)
        }
    }

    private fun fileForDocumentId(documentId: String): File {
        val root = rootDirectory.canonicalFile
        val file = if (documentId == ROOT_DOCUMENT_ID) root else File(root, documentId).canonicalFile
        if (!file.toPath().startsWith(root.toPath())) throw FileNotFoundException("Document is outside provider root")
        return file
    }

    private fun documentIdForFile(file: File): String =
        file.canonicalFile.toPath().let { rootDirectory.canonicalFile.toPath().relativize(it).toString() }
            .replace(File.separatorChar, '/')
            .ifBlank { ROOT_DOCUMENT_ID }

    private fun modeFlags(mode: String): Int = when (mode) {
        "r" -> ParcelFileDescriptor.MODE_READ_ONLY
        "w" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_APPEND
        "rw" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
        "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        else -> throw IllegalArgumentException("Unsupported document mode: $mode")
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs", "cjs" -> "text/javascript"
        "kt", "kts" -> "text/x-kotlin"
        "java" -> "text/x-java"
        "py" -> "text/x-python"
        "md" -> "text/markdown"
        "xml" -> "text/xml"
        else -> "text/plain"
    }

    private fun resolveProjection(projection: Array<out String>?, defaults: Array<String>): Array<String> =
        projection?.toList()?.toTypedArray() ?: defaults

    companion object {
        const val AUTHORITY = "dev.android.ide.documents"
        const val ROOT_ID = "android-ide-files"
        const val ROOT_DOCUMENT_ID = "root"
        const val USER_FILES_DIRECTORY = "android-ide-files"

        fun isProviderUri(uriString: String): Boolean =
            Uri.parse(uriString).authority == AUTHORITY

        /** Resolve only provider-owned document URIs; never accept an arbitrary path. */
        fun localFileForUri(context: Context, uriString: String): File? = runCatching {
            val uri = Uri.parse(uriString)
            if (uri.authority != AUTHORITY) return@runCatching null
            val documentId = when {
                DocumentsContract.isTreeUri(uri) -> DocumentsContract.getTreeDocumentId(uri)
                DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.getDocumentId(uri)
                else -> return@runCatching null
            }
            val root = context.filesDir.resolve(USER_FILES_DIRECTORY).canonicalFile
            val candidate = if (documentId == ROOT_DOCUMENT_ID) root else File(root, documentId).canonicalFile
            candidate.takeIf { it.toPath().startsWith(root.toPath()) }
        }.getOrNull()

        fun rootTreeUri(): String = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_DOCUMENT_ID).toString()

        val ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            DocumentsContract.Root.COLUMN_ICON,
        )
        val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_ICON,
        )

    }
}
