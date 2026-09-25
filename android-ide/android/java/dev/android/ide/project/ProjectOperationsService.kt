// ProjectOperationsService owns Phase 2 mutations and lifecycle transitions.
// Every destructive path uses exact-name preflight, provider verification, and
// registry updates only after the authoritative storage operation succeeds.
package dev.android.ide.project

import dev.android.ide.contracts.ErrorCategory
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.ProjectLocation
import dev.android.ide.contracts.ProjectLocationKind
import dev.android.ide.contracts.ProjectRegistryAdapter
import dev.android.ide.saf.DocumentPresence
import dev.android.ide.saf.SafeMutationResult
import java.time.Instant

class ProjectOperationsService(
    private val registry: ProjectRegistryAdapter,
    private val storage: ProjectStorageAdapterImpl,
    private val metadata: ProjectMetadataAdapterImpl,
) {
    suspend fun removeFromRegistry(projectId: String): OperationReport {
        val project = registered(projectId) ?: return blocked("Project is not registered")
        val removed = registry.remove(project.id)
        return if (removed.outcome == OperationOutcome.COMPLETE) {
            complete("Project removed from the registry", project.id)
        } else {
            removed
        }
    }

    suspend fun permanentlyDelete(projectId: String): OperationReport {
        val project = registered(projectId) ?: return blocked("Project is not registered")
        val registeredChildren = registry.listRegistered().filter { it.id != project.id }.filter {
            storage.isSameOrDescendant(project.location.stableId, it.location.stableId) == true
        }
        if (registeredChildren.isNotEmpty()) {
            return blocked(
                "Delete the registered child project(s) before deleting this parent: " +
                    registeredChildren.joinToString { it.name },
                ErrorCategory.DESTINATION_CONFLICT,
            )
        }
        val capabilities = storage.inspect(project.location)
        if (!capabilities.canDelete) {
            return blocked(
                capabilities.explanation ?: "The project location cannot be deleted",
                ErrorCategory.UNSUPPORTED_PROVIDER_CAPABILITY,
            )
        }
        if (!storage.deleteDocument(project.location.stableId)) {
            return failed("The project could not be permanently deleted", ErrorCategory.PERMISSION_LOST)
        }
        when (storage.documentPresence(project.location.stableId)) {
            DocumentPresence.ABSENT -> Unit
            DocumentPresence.EXISTS -> return failed(
                "The project still exists after the delete request",
                ErrorCategory.EXTERNAL_FILE_CHANGE,
            )
            DocumentPresence.INACCESSIBLE -> return OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "The delete result could not be verified because access was lost",
                errorCategory = ErrorCategory.PERMISSION_LOST,
                recoveryHint = "Restore access and verify whether the project location remains",
            )
        }
        val removed = registry.remove(project.id)
        return if (removed.outcome == OperationOutcome.COMPLETE) {
            complete("Project permanently deleted", project.id)
        } else {
            OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "Project data was deleted but the registry could not be updated",
                errorCategory = ErrorCategory.MALFORMED_METADATA,
                recoveryHint = "Refresh the project registry before using this project again",
            )
        }
    }

    suspend fun duplicateProject(
        projectId: String,
        destinationParentUri: String,
        newName: String,
        description: String? = null,
    ): OperationReport {
        val source = registered(projectId) ?: return blocked("Project is not registered")
        val cleanName = validName(newName) ?: return blocked("Project name is invalid")
        val preflight = preflightDestination(destinationParentUri)
        if (!preflight.allowed) return preflight.report
        val copied = storage.copyExact(source.location.stableId, destinationParentUri, cleanName)
        val rootUri = when (copied) {
            is SafeMutationResult.Created -> copied.documentUri
            SafeMutationResult.Duplicate -> return blocked("The destination already contains that project", ErrorCategory.DESTINATION_CONFLICT)
            SafeMutationResult.InspectionFailed -> return failed("The destination could not be inspected", ErrorCategory.PERMISSION_LOST)
            SafeMutationResult.Failed -> return failed("The project could not be duplicated", ErrorCategory.PERMISSION_LOST)
            is SafeMutationResult.Partial -> return partialMutationReport(copied)
        }
        val identity = identity(rootUri, cleanName, description ?: source.description, source.location.kind)
        val initialized = metadata.ensurePortableState(identity)
        if (initialized.outcome != OperationOutcome.COMPLETE) {
            storage.deleteDocument(rootUri)
            return initialized
        }
        val written = metadata.writeIdentity(identity)
        if (written.outcome != OperationOutcome.COMPLETE) {
            storage.deleteDocument(rootUri)
            return written
        }
        val verified = storage.inspect(identity.location)
        if (!verified.readable || !verified.writable) {
            storage.deleteDocument(rootUri)
            return failed("The duplicated project could not be verified", ErrorCategory.PERMISSION_LOST)
        }
        return registry.register(identity)
    }

    suspend fun renameProject(projectId: String, newName: String): OperationReport {
        val source = registered(projectId) ?: return blocked("Project is not registered")
        val cleanName = validName(newName) ?: return blocked("Project name is invalid")
        if (cleanName == source.name) return blocked("The project already has that name")
        val renamedUri = storage.renameDocument(source.location.stableId, cleanName)
            ?: return failed("The project could not be renamed", ErrorCategory.PERMISSION_LOST)
        if (storage.getDisplayName(renamedUri) != cleanName) {
            storage.renameDocument(renamedUri, source.name)
            return failed("The provider did not confirm the project rename", ErrorCategory.EXTERNAL_FILE_CHANGE)
        }
        val renamedIdentity = source.copy(
            id = renamedUri,
            name = cleanName,
            location = source.location.copy(
                stableId = renamedUri,
                displayLabel = cleanName,
                userVisiblePath = renamedUri,
            ),
            lastOpenedAt = Instant.now(),
        )
        val metadataResult = metadata.writeIdentity(renamedIdentity)
        if (metadataResult.outcome != OperationOutcome.COMPLETE) {
            storage.renameDocument(renamedUri, source.name)
            return metadataResult
        }
        val removed = registry.remove(source.id)
        if (removed.outcome != OperationOutcome.COMPLETE) {
            return OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "Project was renamed but the previous registry entry remains",
                errorCategory = ErrorCategory.MALFORMED_METADATA,
                recoveryHint = "Refresh the project registry and remove the stale entry",
            )
        }
        val registered = registry.register(renamedIdentity)
        return if (registered.outcome == OperationOutcome.COMPLETE) {
            complete("Project renamed", renamedUri)
        } else {
            OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "Project was renamed but the new registry entry could not be written",
                errorCategory = ErrorCategory.MALFORMED_METADATA,
                recoveryHint = "Refresh the registry and import the renamed project if necessary",
            )
        }
    }

    suspend fun relocateProject(
        projectId: String,
        destinationParentUri: String,
        newName: String,
        destinationKind: ProjectLocationKind = ProjectLocationKind.USER_VISIBLE_LOCAL,
    ): OperationReport {
        val source = registered(projectId) ?: return blocked("Project is not registered")
        val cleanName = validName(newName) ?: return blocked("Project name is invalid")
        if (storage.isSameOrDescendant(source.location.stableId, destinationParentUri) == true) {
            return blocked("A project cannot be relocated inside itself", ErrorCategory.DESTINATION_CONFLICT)
        }
        val preflight = preflightDestination(destinationParentUri, projectId)
        if (!preflight.allowed) return preflight.report
        val copied = storage.copyExact(source.location.stableId, destinationParentUri, cleanName)
        val rootUri = when (copied) {
            is SafeMutationResult.Created -> copied.documentUri
            SafeMutationResult.Duplicate -> return blocked("The destination already contains that project", ErrorCategory.DESTINATION_CONFLICT)
            SafeMutationResult.InspectionFailed -> return failed("The destination could not be inspected", ErrorCategory.PERMISSION_LOST)
            SafeMutationResult.Failed -> return failed("The project could not be copied for relocation", ErrorCategory.PERMISSION_LOST)
            is SafeMutationResult.Partial -> return partialMutationReport(copied)
        }
        val identity = identity(rootUri, cleanName, source.description, destinationKind)
        val initialized = metadata.ensurePortableState(identity)
        if (initialized.outcome != OperationOutcome.COMPLETE) {
            storage.deleteDocument(rootUri)
            return initialized
        }
        val written = metadata.writeIdentity(identity)
        if (written.outcome != OperationOutcome.COMPLETE) {
            storage.deleteDocument(rootUri)
            return written
        }
        val verified = storage.inspect(identity.location)
        if (!verified.readable || !verified.writable) {
            storage.deleteDocument(rootUri)
            return failed("The relocation destination could not be verified", ErrorCategory.PERMISSION_LOST)
        }
        if (!storage.deleteDocument(source.location.stableId) ||
            storage.documentPresence(source.location.stableId) != DocumentPresence.ABSENT
        ) {
            val cleaned = storage.deleteDocument(rootUri) &&
                storage.documentPresence(rootUri) == DocumentPresence.ABSENT
            return if (cleaned) {
                failed("The original project was retained because source deletion failed", ErrorCategory.PERMISSION_LOST)
            } else {
                OperationReport(
                    outcome = OperationOutcome.PARTIAL,
                    message = "Relocation copied the project but cleanup was incomplete",
                    errorCategory = ErrorCategory.PERMISSION_LOST,
                    recoveryHint = "Inspect both locations before retrying relocation",
                )
            }
        }
        val removed = registry.remove(source.id)
        if (removed.outcome != OperationOutcome.COMPLETE) {
            return OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "The project was relocated but the old registry entry remains",
                errorCategory = ErrorCategory.MALFORMED_METADATA,
                recoveryHint = "Refresh the registry and remove the stale entry",
            )
        }
        return registry.register(identity)
    }

    suspend fun exportProject(projectId: String, destinationFileUri: String): OperationReport {
        val project = registered(projectId) ?: return blocked("Project is not registered")
        val result = storage.exportZip(project.location.stableId, destinationFileUri)
            ?: return failed("The project could not be exported as ZIP", ErrorCategory.PERMISSION_LOST)
        return complete("Project exported (${result.fileCount} files, ${result.totalBytes} bytes)", project.id)
    }

    suspend fun exportProjects(projectIds: List<String>, destinationParentUri: String): OperationReport {
        if (projectIds.isEmpty()) return blocked("No projects were selected")
        val preflight = preflightDestination(destinationParentUri)
        if (!preflight.allowed) return preflight.report
        val exported = mutableListOf<String>()
        val failedIds = mutableListOf<String>()
        projectIds.forEach { projectId ->
            val project = registered(projectId)
            if (project == null) {
                failedIds += projectId
                return@forEach
            }
            val fileName = validName(project.name)?.let { "$it.zip" }
                ?: "project-${project.id.hashCode().toString().replace('-', 'n')}.zip"
            val destination = when (val created = storage.createFileWithExactName(
                destinationParentUri,
                fileName,
                "application/zip",
            )) {
                is dev.android.ide.saf.ExactCreateResult.Created -> created.documentUri
                else -> {
                    failedIds += projectId
                    return@forEach
                }
            }
            if (storage.exportZip(project.location.stableId, destination) == null) {
                storage.deleteDocument(destination)
                failedIds += projectId
            } else {
                exported += projectId
            }
        }
        return if (failedIds.isEmpty()) {
            OperationReport(
                OperationOutcome.COMPLETE,
                "Exported ${exported.size} project archive(s)",
                affectedIds = exported,
            )
        } else {
            OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "Exported ${exported.size} project archive(s); ${failedIds.size} failed",
                errorCategory = ErrorCategory.DESTINATION_CONFLICT,
                affectedIds = exported,
                recoveryHint = "Choose an empty destination or resolve the archive-name conflicts before retrying",
            )
        }
    }

    suspend fun copyProjectToPrivateWorkspace(
        projectId: String,
        workspaceParentUri: String,
        name: String,
    ): OperationReport = blocked(
        "The private development workspace is unavailable until the runtime adapter is initialized",
        ErrorCategory.UNAVAILABLE_RUNTIME,
    )

    suspend fun moveProjectToPrivateWorkspace(
        projectId: String,
        workspaceParentUri: String,
        name: String,
    ): OperationReport = blocked(
        "The private development workspace is unavailable until the runtime adapter is initialized",
        ErrorCategory.UNAVAILABLE_RUNTIME,
    )

    suspend fun copyEntry(sourceUri: String, targetParentUri: String, newName: String? = null): OperationReport =
        mutationReport(storage.copyExact(sourceUri, targetParentUri, newName), "copied")

    suspend fun moveEntry(sourceUri: String, sourceParentUri: String, targetParentUri: String): OperationReport =
        mutationReport(storage.moveExact(sourceUri, sourceParentUri, targetParentUri), "moved")

    suspend fun renameEntry(sourceUri: String, newName: String): OperationReport {
        if (validName(newName) == null) return blocked("The new name is invalid")
        val renamed = storage.renameDocument(sourceUri, newName)
            ?: return failed("The item could not be renamed", ErrorCategory.PERMISSION_LOST)
        return if (storage.getDisplayName(renamed) == newName) {
            complete("Item renamed", renamed)
        } else {
            failed("The provider did not confirm the new name", ErrorCategory.EXTERNAL_FILE_CHANGE)
        }
    }

    suspend fun deleteEntry(uri: String): OperationReport {
        if (!storage.deleteDocument(uri) || storage.documentExists(uri)) {
            return failed("The item could not be deleted", ErrorCategory.PERMISSION_LOST)
        }
        return complete("Item deleted", uri)
    }

    suspend fun batchDelete(uris: List<String>): OperationReport {
        if (uris.isEmpty()) return blocked("No items were selected")
        val failedIds = mutableListOf<String>()
        uris.forEach { uri ->
            if (!storage.deleteDocument(uri) || storage.documentExists(uri)) failedIds += uri
        }
        return if (failedIds.isEmpty()) {
            OperationReport(OperationOutcome.COMPLETE, "Deleted ${uris.size} item(s)", affectedIds = uris)
        } else {
            OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "Deleted ${uris.size - failedIds.size} item(s); ${failedIds.size} failed",
                errorCategory = ErrorCategory.PERMISSION_LOST,
                affectedIds = uris - failedIds.toSet(),
                recoveryHint = "Review the remaining selected items and retry individually",
            )
        }
    }

    suspend fun batchRemoveFromRegistry(projectIds: List<String>): OperationReport {
        if (projectIds.isEmpty()) return blocked("No projects were selected")
        val removed = mutableListOf<String>()
        val failedIds = mutableListOf<String>()
        projectIds.forEach { projectId ->
            if (removeFromRegistry(projectId).outcome == OperationOutcome.COMPLETE) removed += projectId
            else failedIds += projectId
        }
        return if (failedIds.isEmpty()) {
            OperationReport(
                OperationOutcome.COMPLETE,
                "Removed ${removed.size} project(s) from the registry",
                affectedIds = removed,
            )
        } else {
            OperationReport(
                outcome = OperationOutcome.PARTIAL,
                message = "Removed ${removed.size} project(s); ${failedIds.size} failed",
                affectedIds = removed,
                recoveryHint = "Review the projects that remain registered and retry individually",
            )
        }
    }

    suspend fun batchPermanentlyDelete(projectIds: List<String>): OperationReport {
        if (projectIds.isEmpty()) return blocked("No projects were selected")
        val pending = projectIds.toMutableSet()
        val deleted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val registered = registry.listRegistered()
            val candidate = pending.firstOrNull { id ->
                val project = registered.firstOrNull { it.id == id } ?: return@firstOrNull true
                registered.none { child ->
                    child.id != project.id &&
                        storage.isSameOrDescendant(project.location.stableId, child.location.stableId) == true
                }
            }
            if (candidate == null) {
                failed += pending
                break
            }
            val report = permanentlyDelete(candidate)
            pending.remove(candidate)
            if (report.outcome == OperationOutcome.COMPLETE) deleted += candidate else failed += candidate
        }
        return when {
            failed.isEmpty() -> OperationReport(
                OperationOutcome.COMPLETE,
                "Permanently deleted ${deleted.size} project(s)",
                affectedIds = deleted,
            )
            deleted.isEmpty() -> OperationReport(
                OperationOutcome.FAILED,
                "No selected projects could be permanently deleted",
                errorCategory = ErrorCategory.DESTINATION_CONFLICT,
                recoveryHint = "Delete registered child projects before their parents",
            )
            else -> OperationReport(
                OperationOutcome.PARTIAL,
                "Permanently deleted ${deleted.size} project(s); ${failed.size} failed",
                errorCategory = ErrorCategory.DESTINATION_CONFLICT,
                affectedIds = deleted,
                recoveryHint = "Delete registered child projects before retrying their parents",
            )
        }
    }

    private suspend fun registered(projectId: String): ProjectIdentity? =
        registry.listRegistered().firstOrNull { it.id == projectId }

    private suspend fun preflightDestination(
        destinationUri: String,
        excludedProjectId: String? = null,
    ): DestinationPreflight {
        val location = ProjectLocation(
            kind = ProjectLocationKind.USER_VISIBLE_LOCAL,
            stableId = destinationUri,
            displayLabel = destinationUri,
            userVisiblePath = destinationUri,
        )
        val capabilities = storage.inspect(location)
        if (!capabilities.canCreate || !capabilities.writable) {
            return DestinationPreflight(false, blocked(
                capabilities.explanation ?: "The destination is not writable",
                ErrorCategory.UNSUPPORTED_PROVIDER_CAPABILITY,
            ))
        }
        val conflict = registry.listRegistered()
            .filter { it.id != excludedProjectId }
            .any {
                storage.isSameOrDescendant(it.location.stableId, destinationUri) == true ||
                    storage.isSameOrDescendant(destinationUri, it.location.stableId) == true
            }
        return if (conflict) {
            DestinationPreflight(false, blocked(
                "The destination conflicts with an existing registered project",
                ErrorCategory.DESTINATION_CONFLICT,
            ))
        } else DestinationPreflight(true, complete("Destination preflight passed", destinationUri))
    }

    private data class DestinationPreflight(val allowed: Boolean, val report: OperationReport)

    private fun identity(uri: String, name: String, description: String, kind: ProjectLocationKind) = ProjectIdentity(
        id = uri,
        name = name,
        description = description.trim(),
        location = ProjectLocation(kind, uri, uri, uri),
        registeredAt = Instant.now(),
        lastOpenedAt = Instant.now(),
    )

    private fun validName(value: String): String? {
        val name = value.trim()
        return name.takeIf {
            it.isNotEmpty() && it != "." && it != ".." &&
                !it.contains('/') && !it.contains('\\') && !it.any(Char::isISOControl)
        }
    }

    private fun partialMutationReport(result: SafeMutationResult.Partial) = OperationReport(
        outcome = OperationOutcome.PARTIAL,
        message = "The operation partially completed; source and destination may both remain",
        errorCategory = ErrorCategory.PERMISSION_LOST,
        affectedIds = listOf(result.sourceUri, result.destinationUri),
        recoveryHint = result.recoveryHint,
    )

    private fun mutationReport(result: SafeMutationResult, verb: String): OperationReport = when (result) {
        is SafeMutationResult.Created -> complete("Item $verb", result.documentUri)
        is SafeMutationResult.Partial -> partialMutationReport(result)
        SafeMutationResult.Duplicate -> blocked("The destination already contains an item with that name", ErrorCategory.DESTINATION_CONFLICT)
        SafeMutationResult.InspectionFailed -> failed("The destination could not be inspected", ErrorCategory.PERMISSION_LOST)
        SafeMutationResult.Failed -> failed("The item could not be $verb", ErrorCategory.PERMISSION_LOST)
    }

    private fun complete(message: String, id: String? = null) = OperationReport(
        outcome = OperationOutcome.COMPLETE,
        message = message,
        affectedIds = id?.let(::listOf) ?: emptyList(),
    )

    private fun blocked(message: String, category: ErrorCategory? = null) = OperationReport(
        outcome = OperationOutcome.BLOCKED,
        message = message,
        errorCategory = category,
    )

    private fun failed(message: String, category: ErrorCategory) = OperationReport(
        outcome = OperationOutcome.FAILED,
        message = message,
        errorCategory = category,
    )
}
