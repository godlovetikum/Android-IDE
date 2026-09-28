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
import dev.android.ide.contracts.ProjectMetadataAdapter
import dev.android.ide.contracts.ProjectRegistryAdapter
import dev.android.ide.saf.ExactCreateResult
import dev.android.ide.saf.DocumentPresence
import dev.android.ide.saf.StagedDocumentResult
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.util.zip.ZipInputStream

class ProjectAcquisitionService(
    private val registry: ProjectRegistryAdapter,
    private val storage: ProjectStorageAdapterImpl,
    private val metadata: ProjectMetadataAdapter,
) {
    private data class ArchiveEntry(
        val archivePath: String,
        val path: String?,
        val directory: Boolean,
        val uncompressedSize: Long,
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
        val capabilities = storage.inspectProjectStorage(parent)
        if (!capabilities.canCreate || capabilities.state != CapabilityState.SUPPORTED) {
            return blocked(
                capabilities.explanation ?: "The selected destination cannot create projects",
                ErrorCategory.UNSUPPORTED_PROVIDER_CAPABILITY,
            )
        }
        val parentContainment = preflightDestinationParent(destinationParentUri)
        if (!parentContainment.allowed) return blocked(parentContainment.message, parentContainment.errorCategory)
        val rootUri = when (val created = storage.createDirectoryWithExactName(destinationParentUri, cleanName)) {
            is ExactCreateResult.Created -> created.documentUri
            is ExactCreateResult.Partial -> return OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "Project creation left an unexpected destination item",
                errorCategory = ErrorCategory.PERMISSION_LOST,
                affectedIds = listOf(created.documentUri),
                recoveryHint = created.recoveryHint,
            )
            ExactCreateResult.Duplicate ->
                return blocked("A project already exists at the selected destination", ErrorCategory.DESTINATION_CONFLICT)
            ExactCreateResult.InspectionFailed ->
                return blocked("The destination could not be inspected", ErrorCategory.PERMISSION_LOST)
            ExactCreateResult.Failed ->
                return blocked("The project directory could not be created", ErrorCategory.PERMISSION_LOST)
        }
        val identity = identity(rootUri, cleanName, description, Instant.now())
        val containment = registry.preflightRegistration(identity)
        if (!containment.allowed) {
            return cleanupCreatedRoot(
                rootUri,
                blocked(containment.message, containment.errorCategory ?: ErrorCategory.DESTINATION_CONFLICT),
            )
        }
        val prepared = metadata.ensurePortableState(identity)
        if (prepared.outcome != OperationOutcome.COMPLETE) {
            return cleanupCreatedRoot(rootUri, prepared.copy(
                message = "The new project could not initialize its portable metadata; no project was registered",
            ))
        }
        val written = metadata.writeIdentity(identity)
        if (written.outcome != OperationOutcome.COMPLETE) {
            return cleanupCreatedRoot(rootUri, written.copy(
                message = "The project identity could not be written; no project was registered",
            ))
        }
        val packageName = cleanName.lowercase().replace(Regex("[^a-z0-9-]"), "-")
        val templates = listOf(
            "package.json" to """{
  "name": "$packageName",
  "version": "1.0.0",
  "description": "",
  "main": "index.js",
  "scripts": { "start": "node index.js" },
  "keywords": [],
  "author": "",
  "license": "ISC"
}
""",
            "README.md" to """# $cleanName

${description.trim().ifEmpty { "This project was created with Android IDE." }}

## Attribution

This project was created with [Android IDE](https://github.com/godlovetikum/Android-IDE).

- **Author:** [godlovetikum](https://github.com/godlovetikum)
- **Android IDE repository:** https://github.com/godlovetikum/Android-IDE

## Getting started

Install dependencies:

```bash
npm install
```

Start the project:

```bash
npm start
```

## Project

The project entry point is `index.js`. Update this README with the purpose, setup requirements, and deployment instructions for the application as it evolves.
""",
            ".gitignore" to """node_modules/
dist/
build/
.DS_Store
""",
        )
        for ((fileName, content) in templates) {
            val created = storage.createFileWithExactName(
                rootUri,
                fileName,
                when (fileName) {
                    "README.md" -> "text/markdown"
                    "package.json" -> "application/json"
                    else -> "text/plain"
                },
            )
            val fileUri = (created as? ExactCreateResult.Created)?.documentUri
                ?: return cleanupCreatedRoot(
                    rootUri,
                    blocked("The starter file $fileName could not be created; no project was registered", ErrorCategory.PERMISSION_LOST),
                )
            if (!storage.writeDocument(fileUri, content.toByteArray(Charsets.UTF_8))) {
                return cleanupCreatedRoot(
                    rootUri,
                    blocked("The starter file $fileName could not be written; no project was registered", ErrorCategory.PERMISSION_LOST),
                )
            }
            if (storage.readDocument(fileUri)?.toString(Charsets.UTF_8) != content) {
                return cleanupCreatedRoot(
                    rootUri,
                    blocked("The starter file $fileName could not be verified; no project was registered", ErrorCategory.PERMISSION_LOST),
                )
            }
        }
        val verified = storage.inspectProjectStorage(identity.location)
        if (verified.state != CapabilityState.SUPPORTED) {
            return cleanupCreatedRoot(
                rootUri,
                blocked("The new project could not be verified after creation", ErrorCategory.PERMISSION_LOST),
            )
        }
        return registerCreatedProject(identity, rootUri)
    }

    suspend fun importExistingFolder(
        projectRootUri: String,
        name: String?,
        description: String?,
    ): OperationReport {
        val location = projectLocation(projectRootUri)
        val capabilities = storage.inspectProjectStorage(location)
        if (capabilities.state != CapabilityState.SUPPORTED) {
            return blocked(
                capabilities.explanation ?: "The selected folder is unavailable",
                ErrorCategory.PERMISSION_LOST,
            )
        }
        if (registry.listRegistered().any { it.location.stableId == projectRootUri }) {
            return blocked("This folder is already registered as a project", ErrorCategory.DESTINATION_CONFLICT)
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
        val containment = registry.preflightRegistration(identity)
        if (!containment.allowed) {
            return blocked(containment.message, containment.errorCategory ?: ErrorCategory.DESTINATION_CONFLICT)
        }
        val prepared = metadata.ensurePortableState(identity)
        if (prepared.outcome != OperationOutcome.COMPLETE) return prepared
        val written = metadata.writeIdentity(identity)
        if (written.outcome != OperationOutcome.COMPLETE) return written
        if (!metadataIdentityMatches(identity)) {
            return OperationReport(
                outcome = OperationOutcome.FAILED,
                message = "The selected folder's portable identity could not be verified; it was not registered and its metadata may have changed",
                errorCategory = ErrorCategory.MALFORMED_METADATA,
                affectedIds = listOf(projectRootUri),
                recoveryHint = "Inspect project.json and retry registration after the metadata is consistent",
            )
        }
        val verified = storage.inspectProjectStorage(identity.location)
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
        val capabilities = storage.inspectProjectStorage(parent)
        if (!capabilities.canCreate || capabilities.state != CapabilityState.SUPPORTED) {
            return blocked(
                capabilities.explanation ?: "The selected destination cannot create projects",
                ErrorCategory.UNSUPPORTED_PROVIDER_CAPABILITY,
            )
        }
        val parentContainment = preflightDestinationParent(destinationParentUri)
        if (!parentContainment.allowed) return blocked(parentContainment.message, parentContainment.errorCategory)
        val staged = when (val result = storage.safStageDocument(archiveUri, MAX_ARCHIVE_BYTES)) {
            is StagedDocumentResult.Staged -> result
            StagedDocumentResult.TooLarge -> return blocked("The ZIP archive exceeds the 64 MiB import limit", ErrorCategory.INVALID_ARCHIVE)
            StagedDocumentResult.Failed -> return blocked("The selected ZIP archive could not be read", ErrorCategory.PERMISSION_LOST)
        }
        try {
            val entries = planArchive(staged.file)
                ?: return blocked("The ZIP archive is invalid, unsafe, or exceeds import limits", ErrorCategory.INVALID_ARCHIVE)
            val rootUri = when (val created = storage.createDirectoryWithExactName(destinationParentUri, cleanName)) {
                is ExactCreateResult.Created -> created.documentUri
                is ExactCreateResult.Partial -> return OperationReport(
                    outcome = OperationOutcome.PARTIAL,
                    message = "ZIP import left an unexpected destination item before extraction",
                    errorCategory = ErrorCategory.PERMISSION_LOST,
                    affectedIds = listOf(created.documentUri),
                    recoveryHint = created.recoveryHint,
                )
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
                ZipInputStream(BufferedInputStream(FileInputStream(staged.file))).use { zip ->
                    var index = 0
                    while (true) {
                        val zipEntry = zip.nextEntry ?: break
                        val entry = entries.getOrNull(index++) ?: error("The archive changed while being imported")
                        if (safeArchivePath(zipEntry.name) != entry.archivePath || zipEntry.isDirectory != entry.directory) {
                            error("The archive changed while being imported")
                        }
                        val targetPath = entry.path
                        if (targetPath == null) {
                            zip.closeEntry()
                            continue
                        }
                        val segments = targetPath.split('/')
                        var parentUri = rootUri
                        segments.dropLast(1).forEach { segment ->
                            val existing = storage.findChild(parentUri, segment)
                            parentUri = if (existing?.isDirectory == true) {
                                existing.documentUri
                            } else if (existing == null) {
                                when (val created = storage.createFileWithExactName(parentUri, segment, MIME_DIRECTORY)) {
                                    is ExactCreateResult.Created -> created.documentUri.also { createdUris += it }
                                    else -> error("The archive directory could not be created exactly")
                                }
                            } else {
                                error("The archive contains a file and directory with the same path")
                            }
                        }
                        val leaf = segments.last()
                        if (entry.directory) {
                            val existing = storage.findChild(parentUri, leaf)
                            if (existing != null && !existing.isDirectory) error("The archive contains a file and directory with the same path")
                            if (existing == null) {
                                when (val created = storage.createFileWithExactName(parentUri, leaf, MIME_DIRECTORY)) {
                                    is ExactCreateResult.Created -> createdUris += created.documentUri
                                    else -> error("The archive directory could not be created exactly")
                                }
                            }
                            zip.closeEntry()
                        } else {
                            val created = storage.createFileWithExactName(parentUri, leaf, "application/octet-stream")
                            val documentUri = (created as? ExactCreateResult.Created)?.documentUri
                                ?: error("The archive file could not be created without replacing existing data")
                            createdUris += documentUri
                            if (!storage.safWriteDocumentFromStreamVerified(
                                    documentUri,
                                    zip,
                                    MAX_ENTRY_BYTES,
                                    entry.uncompressedSize,
                                )
                            ) {
                                error("The archive file could not be written and verified")
                            }
                            zip.closeEntry()
                        }
                    }
                    if (index != entries.size) error("The archive changed while being imported")
                }
            } catch (error: Exception) {
                extractionFailure = error.message ?: "extraction failed"
            }
            if (extractionFailure != null) return cleanupFailedImport(rootUri, createdUris, extractionFailure)
            val identity = identity(rootUri, cleanName, description, Instant.now())
            val containment = registry.preflightRegistration(identity)
            if (!containment.allowed) {
                return cleanupFailedImport(rootUri, createdUris, containment.message)
            }
            val prepared = metadata.ensurePortableState(identity)
            if (prepared.outcome != OperationOutcome.COMPLETE) return cleanupFailedImport(rootUri, createdUris, prepared.message)
            val written = metadata.writeIdentity(identity)
            if (written.outcome != OperationOutcome.COMPLETE) return cleanupFailedImport(rootUri, createdUris, written.message)
            val verified = storage.inspectProjectStorage(identity.location)
            if (verified.state != CapabilityState.SUPPORTED) {
                return cleanupFailedImport(rootUri, createdUris, "The extracted project could not be verified")
            }
            return registerCreatedProject(identity, rootUri)
        } finally {
            staged.file.delete()
        }
    }

    private fun planArchive(file: File): List<ArchiveEntry>? {
        val raw = mutableListOf<ArchiveEntry>()
        val paths = mutableSetOf<String>()
        var uncompressedBytes = 0L
        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (raw.size >= MAX_ENTRY_COUNT) return null
                    val path = safeArchivePath(entry.name) ?: return null
                    if (!paths.add(path)) return null
                    var entryBytesRead = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytesRead += count
                        uncompressedBytes += count
                        if (entryBytesRead > MAX_ENTRY_BYTES || uncompressedBytes > MAX_UNCOMPRESSED_BYTES) return null
                    }
                    if (entry.isDirectory && entryBytesRead != 0L) return null
                    raw += ArchiveEntry(path, path, entry.isDirectory, entryBytesRead)
                    zip.closeEntry()
                }
            }
            if (raw.isEmpty()) return null
            val topLevel = raw.map { it.archivePath.substringBefore('/') }.distinct()
            val stripRoot = topLevel.size == 1 && raw.any { it.archivePath.contains('/') }
            val syntheticRoot = topLevel.singleOrNull()
            if (stripRoot && raw.any { it.archivePath == syntheticRoot && !it.directory }) return null
            val normalized = raw.map { entry ->
                val path = if (stripRoot) entry.archivePath.substringAfter('/', "") else entry.archivePath
                entry.copy(path = path.takeIf { it.isNotEmpty() })
            }
            val normalizedPaths = normalized.mapNotNull { it.path }
            if (normalizedPaths.size != normalizedPaths.toSet().size) return null
            val filePaths = normalized.filterNot { it.directory }.mapNotNull { it.path }.toSet()
            if (filePaths.any { file -> normalizedPaths.any { it.startsWith("$file/") } }) return null
            normalized
        } catch (_: Exception) {
            null
        }
    }

    private fun safeArchivePath(rawPath: String): String? {
        val path = rawPath.replace('\\', '/').trimEnd('/')
        if (path.isEmpty()) return null
        if (path.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(path)) return null
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
            storage.deleteDocument(it)
            if (storage.documentPresence(it) != DocumentPresence.ABSENT) cleaned = false
        }
        storage.deleteDocument(rootUri)
        if (storage.documentPresence(rootUri) != DocumentPresence.ABSENT) cleaned = false
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
        storage.deleteDocument(rootUri)
        val cleaned = storage.documentPresence(rootUri) == DocumentPresence.ABSENT
        return if (cleaned) report else report.copy(
            outcome = OperationOutcome.PARTIAL,
            message = "${report.message}; cleanup of the unregistered project was incomplete",
            recoveryHint = "Inspect and remove the created project directory before retrying",
        )
    }

    private suspend fun registerCreatedProject(
        identity: ProjectIdentity,
        rootUri: String,
    ): OperationReport {
        if (!metadataIdentityMatches(identity)) {
            return cleanupCreatedRoot(
                rootUri,
                failed("The new project identity could not be read back", rootUri, ErrorCategory.MALFORMED_METADATA),
            )
        }
        val registered = registry.register(identity)
        if (registered.outcome == OperationOutcome.COMPLETE) return registered
        storage.deleteDocument(rootUri)
        val cleaned = storage.documentPresence(rootUri) == DocumentPresence.ABSENT
        return if (cleaned) {
            registered.copy(message = "The project was not registered; created data was removed: ${registered.message}")
        } else {
            OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "The project could not be registered and cleanup was incomplete",
                errorCategory = ErrorCategory.MALFORMED_METADATA,
                recoveryHint = "Inspect and remove the unregistered project before retrying",
            )
        }
    }

    private suspend fun preflightDestinationParent(
        destinationParentUri: String,
    ): dev.android.ide.contracts.MutationPreflight {
        registry.listRegistered().forEach { project ->
            val destinationInsideProject = storage.isSameOrDescendant(project.location.stableId, destinationParentUri)
            if (destinationInsideProject == null) {
                return dev.android.ide.contracts.MutationPreflight(
                    allowed = false,
                    message = "The selected destination cannot be verified against registered project ${project.name}",
                    errorCategory = ErrorCategory.PERMISSION_LOST,
                )
            }
            // The selected value is a parent folder. It may contain registered
            // sibling projects; the final requested child name is checked by
            // exact creation and registration containment after review.
            if (destinationInsideProject) {
                return dev.android.ide.contracts.MutationPreflight(
                    allowed = false,
                    message = "The selected destination overlaps registered project ${project.name}",
                    errorCategory = ErrorCategory.DESTINATION_CONFLICT,
                )
            }
        }
        return dev.android.ide.contracts.MutationPreflight(true, "Destination containment checks passed")
    }

    private fun projectLocation(uri: String) = ProjectLocation(
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

    private fun failed(message: String, affectedId: String, category: ErrorCategory) = OperationReport(
        outcome = OperationOutcome.FAILED,
        message = message,
        errorCategory = category,
        affectedIds = listOf(affectedId),
    )

    private suspend fun metadataIdentityMatches(expected: ProjectIdentity): Boolean {
        val actual = metadata.readIdentity(expected.location) ?: return false
        return actual.id == expected.id && actual.name == expected.name &&
            actual.description == expected.description &&
            actual.location.stableId == expected.location.stableId
    }
}
