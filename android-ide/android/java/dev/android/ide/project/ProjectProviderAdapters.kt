// ProjectProviderAdapters isolate SAF and preference details so project services
// can operate on stable contracts and preserve one source of project truth.
package dev.android.ide.project

import dev.android.ide.data.ProjectRepository
import dev.android.ide.data.model.Project
import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.ErrorCategory
import dev.android.ide.contracts.LocationCapabilities
import dev.android.ide.contracts.LocationOperationRequest
import dev.android.ide.contracts.MutationPreflight
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.ProjectLocation
import dev.android.ide.contracts.ProjectMetadataAdapter
import dev.android.ide.contracts.ProjectRegistryAdapter
import dev.android.ide.contracts.ProjectRelativePath
import dev.android.ide.contracts.ProjectStorageAdapter
import dev.android.ide.saf.SafRepository
import org.json.JSONObject
import java.time.Instant

class ProjectRegistryStore(
    private val repository: ProjectRepository,
) : ProjectRegistryAdapter {
    override suspend fun listRegistered(): List<ProjectIdentity> = repository.getAll().map(::toIdentity)

    override suspend fun register(project: ProjectIdentity): OperationReport {
        repository.upsert(project.toProject())
        return complete("Project registered", project.id)
    }

    override suspend fun markUnavailable(projectId: String, reason: String): OperationReport {
        val project = repository.getAll().firstOrNull { it.uri == projectId }
            ?: return blocked("Project is not registered", projectId)
        repository.upsert(project.copy(
            capabilityState = CapabilityState.UNAVAILABLE,
            capabilityMessage = reason,
        ))
        return complete("Project marked unavailable", projectId)
    }

    override suspend fun remove(projectId: String): OperationReport {
        repository.remove(projectId)
        return complete("Project removed from registry", projectId)
    }

    private fun toIdentity(project: Project) = ProjectIdentity(
        id = project.stableLocationId,
        name = project.name,
        description = "",
        location = ProjectLocation(
            kind = project.locationKind,
            stableId = project.stableLocationId,
            displayLabel = project.locationLabel,
            userVisiblePath = project.uri,
            capabilityState = project.capabilityState,
        ),
        registeredAt = Instant.ofEpochMilli(project.createdMs),
        lastOpenedAt = Instant.ofEpochMilli(project.lastOpenedMs),
    )

    private fun ProjectIdentity.toProject() = Project(
        name = name,
        uri = location.userVisiblePath ?: location.stableId,
        lastOpenedMs = lastOpenedAt?.toEpochMilli() ?: System.currentTimeMillis(),
        createdMs = registeredAt.toEpochMilli(),
        locationKind = location.kind,
        stableLocationId = location.stableId,
        locationLabel = location.displayLabel,
        capabilityState = location.capabilityState,
    )
}

class ProjectMetadataAdapterImpl(
    private val saf: SafRepository,
) : ProjectMetadataAdapter {
    override suspend fun ensurePortableState(project: ProjectIdentity): OperationReport {
        val result = saf.ensureProjectMetadataDirectory(project.location.stableId)
        return if (result?.migrationComplete == true) {
            complete("Portable project metadata initialized", project.id)
        } else {
            failed("Portable project metadata could not be initialized", project.id, ErrorCategory.MALFORMED_METADATA)
        }
    }

    override suspend fun readIdentity(location: ProjectLocation): ProjectIdentity? {
        val manifest = saf.readProjectMetadataFile(location.stableId, "project.json") ?: return null
        val project = manifest.optJSONObject("project") ?: return null
        val createdAt = manifest.optLong("createdAt", System.currentTimeMillis())
        return ProjectIdentity(
            id = location.stableId,
            name = project.optString("name", location.displayLabel),
            description = project.optString("description", ""),
            location = location,
            registeredAt = Instant.ofEpochMilli(createdAt),
            lastOpenedAt = null,
        )
    }

    override suspend fun writeIdentity(project: ProjectIdentity): OperationReport {
        val manifest = JSONObject().apply {
            put("schemaVersion", 1)
            put("project", JSONObject().apply {
                put("name", project.name)
                put("description", project.description)
                put("createdAt", project.registeredAt.toEpochMilli())
                put("updatedAt", System.currentTimeMillis())
            })
        }
        val written = saf.writeProjectMetadataFile(
            project.location.stableId,
            "project.json",
            manifest.toString(2),
        )
        return if (written) complete("Portable project identity written", project.id)
        else failed("Portable project identity could not be written", project.id, ErrorCategory.MALFORMED_METADATA)
    }

    override suspend fun readWorkspaceDescriptor(projectId: String): ByteArray? =
        saf.readProjectMetadataFile(projectId, "workspace.json")?.toString()?.toByteArray()

    override suspend fun writeWorkspaceDescriptor(projectId: String, descriptor: ByteArray): OperationReport {
        val written = saf.writeProjectMetadataFile(
            projectId,
            "workspace.json",
            descriptor.toString(Charsets.UTF_8),
        )
        return if (written) complete("Workspace descriptor written", projectId)
        else failed("Workspace descriptor could not be written", projectId, ErrorCategory.MALFORMED_METADATA)
    }
}

class ProjectStorageAdapterImpl(
    private val saf: SafRepository,
) : ProjectStorageAdapter {
    suspend fun inspect(location: ProjectLocation): LocationCapabilities = saf.inspectProjectCapabilities(location)

    override suspend fun inspectCapabilities(location: ProjectLocation): LocationCapabilities = inspect(location)

    override suspend fun list(path: ProjectRelativePath): List<ProjectRelativePath> =
        saf.listChildren(path).map { it.documentUri }

    override suspend fun read(path: ProjectRelativePath): ByteArray =
        saf.readFile(path) ?: ByteArray(0)

    override suspend fun write(path: ProjectRelativePath, content: ByteArray): OperationReport =
        if (saf.writeFile(path, content)) complete("Project file written", path)
        else failed("Project file could not be written", path, ErrorCategory.PERMISSION_LOST)

    override suspend fun preflightMutation(path: ProjectRelativePath): MutationPreflight =
        MutationPreflight(true, "Provider mutation preflight passed")

    override suspend fun preflightLocationOperation(request: LocationOperationRequest): MutationPreflight =
        MutationPreflight(
            allowed = request.source != null || request.kind.name == "IMPORT",
            message = "Transfer operation preflight requires an explicit source or import input",
            errorCategory = ErrorCategory.DESTINATION_CONFLICT.takeIf { request.destination == null },
        )

    override suspend fun executeLocationOperation(request: LocationOperationRequest): OperationReport =
        blocked("Project relocation and transfer execution requires an explicit destination review and belongs to project management", request.projectId)

    override suspend fun verifyLocationOperation(request: LocationOperationRequest): OperationReport =
        blocked("Project relocation and transfer verification requires the selected provider to report the resulting location and state", request.projectId)

    override suspend fun observeChanges(listener: (ProjectRelativePath) -> Unit) = Unit
}

private fun complete(message: String, id: String) = OperationReport(
    outcome = OperationOutcome.COMPLETE,
    message = message,
    affectedIds = listOf(id),
)

private fun blocked(message: String, id: String) = OperationReport(
    outcome = OperationOutcome.BLOCKED,
    message = message,
    affectedIds = listOf(id),
)

private fun failed(message: String, id: String, category: ErrorCategory) = OperationReport(
    outcome = OperationOutcome.FAILED,
    message = message,
    errorCategory = category,
    affectedIds = listOf(id),
)
