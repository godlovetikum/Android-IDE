package dev.android.ide.saf

import android.content.Context
import android.net.Uri
import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.FileSystemNode
import dev.android.ide.contracts.FileSystemResolution
import dev.android.ide.contracts.ProjectFileSystemAdapter
import dev.android.ide.contracts.ProjectLocation
import java.io.File

/**
 * First application-wide filesystem boundary.
 *
 * Project and runtime domains exchange the stable virtualPath and retain the
 * provider URI only at this adapter boundary. A localPath is optional: SAF
 * file operations can work without one, while Termux and language servers need
 * it to execute against the selected project rather than a substituted home.
 */
class SafBackedFileSystem(context: Context) : ProjectFileSystemAdapter {
    private val appContext = context.applicationContext
    private val saf = SafRepository(appContext)

    override suspend fun resolve(location: ProjectLocation): FileSystemResolution {
        val providerUri = location.userVisiblePath ?: location.stableId
        val localPath = saf.localFilesystemPath(providerUri)
        val capabilities = saf.inspectProjectStorage(location)
        val readable = capabilities.readable
        val writable = capabilities.writable
        val terminalAccessible = localPath
            ?.let(::File)
            ?.let { it.isDirectory && it.canRead() && it.canWrite() }
            ?: false
        val node = if (readable) {
            FileSystemNode(
                virtualPath = virtualPath(providerUri),
                providerUri = providerUri,
                localPath = localPath,
                displayLabel = location.displayLabel,
                isDirectory = true,
                providerAuthority = Uri.parse(providerUri).authority,
            )
        } else {
            null
        }
        return FileSystemResolution(
            node = node,
            readable = readable,
            writable = writable,
            terminalAccessible = terminalAccessible,
            explanation = when {
                capabilities.state == CapabilityState.PERMISSION_LOST ->
                    capabilities.explanation ?: "Permission to the selected filesystem location was lost"
                !readable -> capabilities.explanation ?: "The selected filesystem location cannot be read"
                !terminalAccessible -> "The location is available for file operations, but the terminal cannot access a local working path"
                !writable -> capabilities.explanation ?: "The selected filesystem location cannot be written"
                else -> null
            },
        )
    }

    override suspend fun mountedRoots(): List<FileSystemNode> = buildList {
        val androidIdeRoot = AndroidIdeDocumentsProvider.rootTreeUri()
        if (saf.documentPresence(androidIdeRoot) == DocumentPresence.EXISTS && saf.isDirectoryDocument(androidIdeRoot)) {
            add(
                FileSystemNode(
                    virtualPath = virtualPath(androidIdeRoot),
                    providerUri = androidIdeRoot,
                    localPath = saf.localFilesystemPath(androidIdeRoot),
                    displayLabel = "Android IDE",
                    isDirectory = true,
                    providerAuthority = Uri.parse(androidIdeRoot).authority,
                ),
            )
        }
        appContext.contentResolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .filterNot { it == androidIdeRoot }
            .distinct()
            .forEach { grantedUri ->
                if (saf.documentPresence(grantedUri) != DocumentPresence.EXISTS || !saf.isDirectoryDocument(grantedUri)) return@forEach
                val label = saf.getDisplayName(grantedUri) ?: "Mounted storage"
                add(
                    FileSystemNode(
                        virtualPath = virtualPath(grantedUri),
                        providerUri = grantedUri,
                        localPath = saf.localFilesystemPath(grantedUri),
                        displayLabel = label,
                        isDirectory = true,
                        providerAuthority = Uri.parse(grantedUri).authority,
                    ),
                )
            }
    }

    override suspend fun children(parent: FileSystemNode): List<FileSystemNode> =
        saf.listChildren(parent.providerUri).map { child ->
            FileSystemNode(
                virtualPath = virtualPath(child.documentUri),
                providerUri = child.documentUri,
                localPath = saf.localFilesystemPath(child.documentUri),
                displayLabel = child.displayName,
                isDirectory = child.isDirectory,
                parentVirtualPath = parent.virtualPath,
                providerAuthority = Uri.parse(child.documentUri).authority,
            )
        }

    fun virtualPath(providerUri: String): String =
        "androidide://filesystem/${Uri.encode(providerUri)}"
}
