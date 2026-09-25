// ProjectDetailsService computes details from the authoritative project location.
// Metrics are intentionally not persisted because the user-visible project can
// change outside the application between openings.
package dev.android.ide.project

import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.ProjectRegistryAdapter
import dev.android.ide.data.model.Project
import dev.android.ide.data.model.ProjectDetails

class ProjectDetailsService(
    private val registry: ProjectRegistryAdapter,
    private val storage: ProjectStorageAdapterImpl,
) {
    suspend fun load(projectId: String): ProjectDetailsResult {
        val identity = registry.listRegistered().firstOrNull { it.id == projectId }
            ?: return ProjectDetailsResult.Unavailable("Project is not registered")
        val capabilities = storage.inspect(identity.location)
        if (capabilities.state != CapabilityState.SUPPORTED) {
            return ProjectDetailsResult.Unavailable(
                capabilities.explanation ?: "Project location is unavailable",
            )
        }
        val metadata = storage.projectMetadata(identity.location)
            ?: return ProjectDetailsResult.Unavailable(
                "Project contents could not be inspected completely; metrics were not estimated",
            )
        val project = Project(
            name = identity.name,
            description = identity.description,
            uri = identity.location.userVisiblePath ?: identity.location.stableId,
            lastOpenedMs = identity.lastOpenedAt?.toEpochMilli() ?: identity.registeredAt.toEpochMilli(),
            createdMs = identity.registeredAt.toEpochMilli(),
            locationKind = identity.location.kind,
            stableLocationId = identity.location.stableId,
            locationLabel = identity.location.displayLabel,
            capabilityState = capabilities.state,
        )
        return ProjectDetailsResult.Loaded(
            ProjectDetails(
                project = project,
                description = metadata.description.ifBlank { identity.description },
                creationTimeMs = metadata.creationTimeMs ?: identity.registeredAt.toEpochMilli(),
                lastModifiedTimeMs = metadata.lastModifiedTimeMs,
                storageProvider = metadata.storageProvider,
                storagePath = metadata.storagePath,
                fileCount = metadata.fileCount,
                folderCount = metadata.folderCount,
                totalBytes = metadata.totalBytes,
                languageBytes = metadata.languageBytes,
                git = metadata.git,
            ),
        )
    }
}

sealed interface ProjectDetailsResult {
    data class Loaded(val details: ProjectDetails) : ProjectDetailsResult
    data class Unavailable(val reason: String) : ProjectDetailsResult
}
