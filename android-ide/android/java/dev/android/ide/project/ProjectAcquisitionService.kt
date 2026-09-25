// ProjectAcquisitionService owns reviewed, verified project acquisition workflows.
// It registers a project only after its selected location and portable metadata
// have been verified. It does not create an app-private shadow project.
package dev.android.ide.project

import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.ErrorCategory
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.ProjectLocation
import dev.android.ide.contracts.ProjectLocationKind
import dev.android.ide.contracts.ProjectMetadataAdapter
import dev.android.ide.contracts.ProjectRegistryAdapter
import dev.android.ide.saf.ExactCreateResult
import dev.android.ide.saf.DocumentPresence
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipInputStream

class ProjectAcquisitionService(
    private val registry: ProjectRegistryAdapter,
    private val storage: ProjectStorageAdapterImpl,
    private val metadata: ProjectMetadataAdapter,
) {
    private data class ArchiveEntry(
        val path: String,
        val directory: Boolean,
        val bytes: ByteArray,
    )

    private companion object {
        const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
        const val MAX_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L
        const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
        const val MAX_ENTRY_COUNT = 10_000
        const val MIME_DIRECTORY = "vnd.android.document/directory"
    }
    suspend fun createBlankProject(
        destinationParentUri: String,
        name: String,
        description: String,
    ): OperationReport {
        val cleanName = validateProjectName(name)
            ?: return blocked("Project name must be a single non-empty folder name")
        val parent = projectLocation(destinationParentUri)
        val capabilities = storage.inspect(parent)
        if (!capabilities.canCreate || capabilities.state != CapabilityState.SUPPORTED) {
            return blocked(
                capabilities.explanation ?: "The selected destination cannot create projects",
                ErrorCategory.UNSUPPORTED_PROVIDER_CAPABILITY,
            )
        }
        if (containsRegisteredProject(destinationParentUri)) {
            return blocked(
                "The selected destination is inside an existing registered project",
                ErrorCategory.DESTINATION_CONFLICT,
            )
        }
        val rootUri = when (val created = storage.createDirectoryWithExactName(destinationParentUri, cleanName)) {
            is ExactCreateResult.Created -> created.documentUri
            ExactCreateResult.Duplicate ->
                return blocked("A project already exists at the selected destination", ErrorCategory.DESTINATION_CONFLICT)
            ExactCreateResult.InspectionFailed ->
                return blocked("The destination could not be inspected", ErrorCategory.PERMISSION_LOST)
            ExactCreateResult.Failed ->
                return blocked("The project directory could not be created", ErrorCategory.PERMISSION_LOST)
        }
        val identity = identity(rootUri, cleanName, description, Instant.now())
        val prepared = metadata.ensurePortableState(identity)
        if (prepared.outcome != OperationOutcome.COMPLETE) {
            return cleanupCreatedRoot(rootUri, prepared.copy(
                message = "The new project could not initialize its portable metadata; no project was registered",
            ))
        }
        val written = metadata.writeIdentity(identity)
        if (written.outcome != OperationOutcome.COMPLETE) {
            return cleanupCreatedRoot(rootUri, written.copy(
                message = "The new project identity could not be written; no project was registered",
            ))
        }
        val verified = storage.inspect(identity.location)
        if (verified.state != CapabilityState.SUPPORTED) {
            return cleanupCreatedRoot(
                rootUri,
                blocked("The new project could not be verified after creation", ErrorCategory.PERMISSION_LOST),
            )
        }
        return registry.register(identity)
    }

    suspend fun importExistingFolder(
        projectRootUri: String,
        name: String?,
        description: String?,
    ): OperationReport {
        val location = projectLocation(projectRootUri)
        val capabilities = storage.inspect(location)
        if (capabilities.state != CapabilityState.SUPPORTED) {
            return blocked(
                capabilities.explanation ?: "The selected folder is unavailable",
                ErrorCategory.PERMISSION_LOST,
            )
        }
        val registered = registry.listRegistered()
        if (registered.any { it.location.stableId == projectRootUri }) {
            return blocked("This folder is already registered as a project", ErrorCategory.DESTINATION_CONFLICT)
        }
        if (registered.any { project ->
                storage.isSameOrDescendant(project.location.stableId, projectRootUri) == true ||
                    storage.isSameOrDescendant(projectRootUri, project.location.stableId) == true
            }) {
            return blocked(
                "The selected folder would contain or be contained by another registered project",
                ErrorCategory.DESTINATION_CONFLICT,
            )
        }
        val existing = metadata.readIdentity(location)
        val projectName = validateProjectName(name ?: existing?.name ?: storage.getDisplayName(projectRootUri))
            ?: return blocked("The selected folder does not have a valid project name")
        val identity = identity(
            projectRootUri,
            projectName,
            description ?: existing?.description.orEmpty(),
            existing?.registeredAt ?: Instant.now(),
        )
        val prepared = metadata.ensurePortableState(identity)
        if (prepared.outcome != OperationOutcome.COMPLETE) return prepared
        val written = metadata.writeIdentity(identity)
        if (written.outcome != OperationOutcome.COMPLETE) return written
        val verified = storage.inspect(identity.location)
        if (verified.state != CapabilityState.SUPPORTED) {
            return blocked("The imported project could not be verified after metadata initialization", ErrorCategory.PERMISSION_LOST)
        }
        return registry.register(identity)
    }

    suspend fun importZip(
        archiveUri: String,
        destinationParentUri: String,
        name: String,
        description: String,
    ): OperationReport {
        val cleanName = validateProjectName(name)
            ?: return blocked("Project name must be a single non-empty folder name")
        val parent = projectLocation(destinationParentUri)
        val capabilities = storage.inspect(parent)
        if (!capabilities.canCreate || capabilities.state != CapabilityState.SUPPORTED) {
            return blocked(
                capabilities.explanation ?: "The selected destination cannot create projects",
                ErrorCategory.UNSUPPORTED_PROVIDER_CAPABILITY,
            )
        }
        if (containsRegisteredProject(destinationParentUri)) {
            return blocked(
                "The selected destination is inside an existing registered project",
                ErrorCategory.DESTINATION_CONFLICT,
            )
        }
        val archiveBytes = storage.readDocument(archiveUri)
            ?: return blocked("The selected ZIP archive could not be read", ErrorCategory.PERMISSION_LOST)
        if (archiveBytes.size.toLong() > MAX_ARCHIVE_BYTES) {
            return blocked("The ZIP archive exceeds the 64 MiB import limit", ErrorCategory.INVALID_ARCHIVE)
        }
        val entries = parseArchive(archiveBytes)
            ?: return blocked("The ZIP archive is invalid, unsafe, or exceeds import limits", ErrorCategory.INVALID_ARCHIVE)
        val rootUri = when (val created = storage.createDirectoryWithExactName(destinationParentUri, cleanName)) {
            is ExactCreateResult.Created -> created.documentUri
            ExactCreateResult.Duplicate ->
                return blocked("A project already exists at the selected destination", ErrorCategory.DESTINATION_CONFLICT)
            ExactCreateResult.InspectionFailed ->
                return blocked("The destination could not be inspected", ErrorCategory.PERMISSION_LOST)
            ExactCreateResult.Failed ->
                return blocked("The project directory could not be created", ErrorCategory.PERMISSION_LOST)
        }
        val createdUris = mutableListOf<String>()
        var extractionFailure: String? = null
        try {
            for (entry in entries.sortedWith(compareBy<ArchiveEntry> { it.path.count { ch -> ch == '/' } }.thenBy { it.path })) {
                val segments = entry.path.split('/').filter(String::isNotEmpty)
                var parentUri = rootUri
                segments.dropLast(1).forEach { segment ->
                    val existing = storage.findChild(parentUri, segment)
                    parentUri = if (existing?.isDirectory == true) {
                        existing.documentUri
                    } else if (existing == null) {
                        when (val created = storage.createFileWithExactName(parentUri, segment, MIME_DIRECTORY)) {
                            is ExactCreateResult.Created -> created.documentUri.also { createdUris += it }
                            else -> error("The archive directory could not be created")
                        }
                    } else {
                        error("The archive contains a file and directory with the same path")
                    }
                }
                if (segments.isEmpty()) continue
                val leaf = segments.last()
                if (entry.directory) {
                    val existing = storage.findChild(parentUri, leaf)
                    if (existing != null && !existing.isDirectory) {
                        error("The archive contains a file and directory with the same path")
                    }
                    if (existing == null) {
                        when (val created = storage.createFileWithExactName(parentUri, leaf, MIME_DIRECTORY)) {
                            is ExactCreateResult.Created -> createdUris += created.documentUri
                            else -> error("The archive directory could not be created")
                        }
                    }
                } else {
                    when (val created = storage.createFileWithExactName(parentUri, leaf, "application/octet-stream")) {
                        is ExactCreateResult.Created -> {
                            createdUris += created.documentUri
                            if (!storage.writeDocument(created.documentUri, entry.bytes)) {
                                error("The archive file could not be written")
                            }
                            val written = storage.readDocument(created.documentUri)
                            if (written == null || !written.contentEquals(entry.bytes)) {
                                error("The extracted file could not be verified")
                            }
                        }
                        else -> error("The archive file could not be created without replacing existing data")
                    }
                }
            }
        } catch (error: Exception) {
            extractionFailure = error.message ?: "extraction failed"
        }
        if (extractionFailure != null) {
            return cleanupFailedImport(rootUri, createdUris, extractionFailure)
        }
        val identity = identity(rootUri, cleanName, description, Instant.now())
        val prepared = metadata.ensurePortableState(identity)
        if (prepared.outcome != OperationOutcome.COMPLETE) {
            return cleanupFailedImport(rootUri, createdUris, prepared.message)
        }
        val written = metadata.writeIdentity(identity)
        if (written.outcome != OperationOutcome.COMPLETE) {
            return cleanupFailedImport(rootUri, createdUris, written.message)
        }
        val verified = storage.inspect(identity.location)
        if (verified.state != CapabilityState.SUPPORTED) {
            return cleanupFailedImport(rootUri, createdUris, "The extracted project could not be verified")
        }
        return registry.register(identity)
    }

    private fun parseArchive(bytes: ByteArray): List<ArchiveEntry>? {
        val raw = mutableListOf<ArchiveEntry>()
        val paths = mutableSetOf<String>()
        var uncompressedBytes = 0L
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (raw.size >= MAX_ENTRY_COUNT) return null
                    val path = safeArchivePath(entry.name) ?: return null
                    if (!paths.add(path)) return null
                    val entryBytes = if (entry.isDirectory) {
                        ByteArray(0)
                    } else {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytesRead = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count == -1) break
                            entryBytesRead += count
                            uncompressedBytes += count
                            if (entryBytesRead > MAX_ENTRY_BYTES ||
                                uncompressedBytes > MAX_UNCOMPRESSED_BYTES
                            ) return null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    raw += ArchiveEntry(path, entry.isDirectory, entryBytes)
                }
            }
            if (raw.isEmpty()) return null
            val topLevel = raw.map { it.path.substringBefore('/') }.distinct()
            val stripRoot = topLevel.size == 1 && raw.any { it.path.contains('/') }
            raw.mapNotNull { entry ->
                val path = if (stripRoot) entry.path.substringAfter('/', "") else entry.path
                if (path.isEmpty()) null else entry.copy(path = path)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun safeArchivePath(rawPath: String): String? {
        val path = rawPath.replace('\\', '/').trimEnd('/')
        if (path.isEmpty()) return null
        if (path.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(path)) return null
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." || it.any(Char::isISOControl) }) return null
        return segments.joinToString("/")
    }

    private suspend fun cleanupFailedImport(
        rootUri: String,
        createdUris: List<String>,
        reason: String?,
    ): OperationReport {
        var cleaned = true
        createdUris.asReversed().forEach {
            if (!storage.deleteDocument(it) || storage.documentPresence(it) != DocumentPresence.ABSENT) {
                cleaned = false
            }
        }
        if (!storage.deleteDocument(rootUri) || storage.documentPresence(rootUri) != DocumentPresence.ABSENT) {
            cleaned = false
        }
        return if (cleaned) {
            blocked("ZIP import was rejected and created data was removed: ${reason ?: "extraction failed"}", ErrorCategory.INVALID_ARCHIVE)
        } else {
            OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "ZIP import failed and cleanup was incomplete: ${reason ?: "extraction failed"}",
                errorCategory = ErrorCategory.INVALID_ARCHIVE,
                recoveryHint = "Inspect the destination and remove the incomplete project before retrying",
            )
        }
    }

    private suspend fun cleanupCreatedRoot(rootUri: String, report: OperationReport): OperationReport {
        val cleaned = storage.deleteDocument(rootUri) &&
            storage.documentPresence(rootUri) == DocumentPresence.ABSENT
        return if (cleaned) report else report.copy(
            outcome = OperationOutcome.PARTIAL,
            message = "${report.message}; cleanup of the unregistered project was incomplete",
            recoveryHint = "Inspect and remove the created project directory before retrying",
        )
    }

    private suspend fun containsRegisteredProject(destinationParentUri: String): Boolean =
        registry.listRegistered().any { project ->
            storage.isSameOrDescendant(project.location.stableId, destinationParentUri) == true ||
                storage.isSameOrDescendant(destinationParentUri, project.location.stableId) == true
        }

    private fun projectLocation(uri: String) = ProjectLocation(
        kind = ProjectLocationKind.USER_VISIBLE_LOCAL,
        stableId = uri,
        displayLabel = uri,
        userVisiblePath = uri,
        capabilityState = CapabilityState.NOT_YET_CHECKED,
    )

    private fun identity(
        uri: String,
        name: String,
        description: String,
        registeredAt: Instant,
    ) = ProjectIdentity(
        id = uri,
        name = name,
        description = description.trim(),
        location = projectLocation(uri),
        registeredAt = registeredAt,
        lastOpenedAt = Instant.now(),
    )

    private fun validateProjectName(value: String?): String? {
        val name = value?.trim().orEmpty()
        if (name.isEmpty() || name == "." || name == "..") return null
        if (name.contains('/') || name.contains('\\') || name.any { it.isISOControl() }) return null
        return name
    }

    private fun blocked(message: String, category: ErrorCategory? = null) = OperationReport(
        outcome = OperationOutcome.BLOCKED,
        message = message,
        errorCategory = category,
    )
}
