// ProjectStateService owns project registration, capability reporting, and
// metadata initialization without taking ownership of user project contents.
package dev.android.ide.project

import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.LocationCapabilities
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.ProjectLocation
import dev.android.ide.contracts.ProjectLocationKind
import dev.android.ide.contracts.ProjectMetadataAdapter
import dev.android.ide.contracts.ProjectRegistryAdapter
import dev.android.ide.contracts.ProjectStorageAdapter
import java.time.Instant

class ProjectStateService(
    private val registry: ProjectRegistryAdapter,
    private val storage: ProjectStorageAdapter,
    private val metadata: ProjectMetadataAdapter,
) {
    suspend fun registeredProjects(): List<ProjectIdentity> = registry.listRegistered().map { existing ->
        val capabilities = storage.inspectCapabilities(existing.location)
        val refreshed = existing.copy(
            location = existing.location.copy(
                capabilityState = capabilities.state,
                capabilityExplanation = capabilities.explanation,
            ),
        )
        if (capabilities.state == CapabilityState.SUPPORTED &&
            existing.location.capabilityState != capabilities.state
        ) {
            registry.register(refreshed)
        } else if (capabilities.state != CapabilityState.SUPPORTED) {
            registry.markUnavailable(
                existing.id,
                capabilities.explanation ?: "Project location is unavailable",
                capabilities.state,
            )
        }
        refreshed
    }

    suspend fun inspect(location: ProjectLocation): LocationCapabilities = storage.inspectCapabilities(location)

    suspend fun restore(projectId: String): ProjectRestoreResult {
        val existing = registry.listRegistered().firstOrNull { it.id == projectId }
            ?: return ProjectRestoreResult.Unavailable("Project is not registered")
        val capabilities = storage.inspectCapabilities(existing.location)
        if (capabilities.state != CapabilityState.SUPPORTED) {
            registry.markUnavailable(
                projectId,
                capabilities.explanation ?: "Project location is unavailable",
                capabilities.state,
            )
            return ProjectRestoreResult.Unavailable(
                capabilities.explanation ?: "Project location is unavailable",
            )
        }
        val identity = metadata.readIdentity(existing.location) ?: existing
        val initialized = metadata.ensurePortableState(identity)
        if (initialized.outcome != OperationOutcome.COMPLETE) {
            registry.markUnavailable(projectId, initialized.message)
            return ProjectRestoreResult.Unavailable(initialized.message)
        }
        val restoredIdentity = identity.copy(
            location = identity.location.copy(
                capabilityState = capabilities.state,
                capabilityExplanation = capabilities.explanation,
            ),
            lastOpenedAt = Instant.now(),
        )
        registry.register(restoredIdentity)
        return ProjectRestoreResult.Restored(restoredIdentity, capabilities)
    }

    suspend fun register(
        location: ProjectLocation,
        name: String,
        description: String,
    ): OperationReport {
        val capabilities = storage.inspectCapabilities(location)
        if (capabilities.state != CapabilityState.SUPPORTED) {
            return OperationReport(
                outcome = OperationOutcome.BLOCKED,
                message = capabilities.explanation ?: "Project location is unavailable",
            )
        }
        val identity = ProjectIdentity(
            id = location.stableId,
            name = name,
            description = description,
            location = location.copy(
                capabilityState = capabilities.state,
                capabilityExplanation = capabilities.explanation,
            ),
            registeredAt = Instant.now(),
            lastOpenedAt = Instant.now(),
        )
        val metadataResult = metadata.ensurePortableState(identity)
        if (metadataResult.outcome != OperationOutcome.COMPLETE) return metadataResult
        metadata.writeIdentity(identity)
        return registry.register(identity)
    }
}

sealed interface ProjectRestoreResult {
    data class Restored(
        val identity: ProjectIdentity,
        val capabilities: LocationCapabilities,
    ) : ProjectRestoreResult

    data class Unavailable(val reason: String) : ProjectRestoreResult
}
