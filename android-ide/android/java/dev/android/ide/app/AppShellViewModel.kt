// AppShellViewModel coordinates application-scope state while delegating project,
// metadata, and lifecycle ownership to their respective services.
package dev.android.ide.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.android.ide.data.ProjectRepository
import dev.android.ide.project.ProjectMetadataAdapterImpl
import dev.android.ide.project.ProjectStorageAdapterImpl
import dev.android.ide.project.ProjectRegistryStore
import dev.android.ide.contracts.Surface
import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.ProjectLocation
import dev.android.ide.contracts.ProjectLocationKind
import dev.android.ide.lifecycle.LifecycleCoordinatorImpl
import dev.android.ide.project.ProjectStateService
import dev.android.ide.project.ProjectRestoreResult
import dev.android.ide.project.ProjectAcquisitionService
import dev.android.ide.project.ProjectDetailsResult
import dev.android.ide.project.ProjectDetailsService
import dev.android.ide.data.model.ProjectDetails
import dev.android.ide.project.ProjectOperationsService
import dev.android.ide.saf.SafRepository
import dev.android.ide.runtime.RuntimeStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppShellState(
    val surface: Surface = Surface.HOME,
    val projects: List<dev.android.ide.contracts.ProjectIdentity> = emptyList(),
    val projectSummaries: Map<String, ProjectSummary> = emptyMap(),
    val selectedProjectId: String? = null,
    val selectedProjectIds: Set<String> = emptySet(),
    val navigationStack: List<Surface> = listOf(Surface.HOME),
    val statusMessage: String? = null,
    val restoring: Boolean = false,
    val operationInProgress: Boolean = false,
    val projectDetails: ProjectDetails? = null,
    val folderInspection: FolderInspection? = null,
    val acquiredProjectId: String? = null,
    val detailsLoading: Boolean = false,
    val exitConfirmationVisible: Boolean = false,
)

data class FolderInspection(
    val path: String,
    val capabilityState: CapabilityState,
    val readable: Boolean,
    val writable: Boolean,
    val alreadyRegistered: Boolean,
    val nestedInRegisteredProject: Boolean,
    val explanation: String?,
)

data class ProjectSummary(
    val fileCount: Int? = null,
    val totalBytes: Long? = null,
    val hasGit: Boolean = false,
    val status: String? = null,
)

class AppShellViewModel(application: Application) : AndroidViewModel(application) {
    private val saf = SafRepository(application)
    private val storage = ProjectStorageAdapterImpl(saf)
    private val registry = ProjectRegistryStore(ProjectRepository(application), storage)
    private val metadata = ProjectMetadataAdapterImpl(saf)
    private val projects = ProjectStateService(registry, storage, metadata)
    private val acquisition = ProjectAcquisitionService(registry, storage, metadata)
    private val detailsService = ProjectDetailsService(registry, storage)
    private val operations = ProjectOperationsService(registry, storage, metadata)
    private val lifecycle = LifecycleCoordinatorImpl(application)
    private val applicationState = ApplicationStateStore(application)
    private val runtimeState = RuntimeStateStore(application)

    private val _state = MutableStateFlow(AppShellState())
    val state: StateFlow<AppShellState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runtimeState.ensureReady()
            lifecycle.restore()
            refreshProjects()
            restoreLastProject()
        }
    }

    fun foreground() {
        viewModelScope.launch { lifecycle.onForeground() }
    }

    fun background() {
        viewModelScope.launch { lifecycle.onBackground() }
    }

    fun reportStatus(message: String) {
        _state.update { it.copy(statusMessage = message) }
    }

    fun selectProject(projectId: String) {
        _state.update { it.copy(selectedProjectId = projectId) }
    }

    fun toggleProjectSelection(projectId: String) {
        _state.update { current ->
            val next = current.selectedProjectIds.toMutableSet().apply {
                if (!add(projectId)) remove(projectId)
            }
            current.copy(selectedProjectIds = next, selectedProjectId = projectId)
        }
    }

    fun clearProjectSelection() {
        _state.update { it.copy(selectedProjectIds = emptySet()) }
    }

    fun inspectExistingFolder(projectRootUri: String) {
        viewModelScope.launch {
            val location = ProjectLocation(ProjectLocationKind.USER_VISIBLE_LOCAL, projectRootUri, projectRootUri, projectRootUri)
            val capabilities = storage.inspect(location)
            val registered = registry.listRegistered()
            val alreadyRegistered = registered.any { it.location.stableId == projectRootUri }
            val nested = registered.any { project ->
                storage.isSameOrDescendant(project.location.stableId, projectRootUri) == true
            }
            _state.update {
                it.copy(folderInspection = FolderInspection(
                    path = projectRootUri,
                    capabilityState = capabilities.state,
                    readable = capabilities.readable,
                    writable = capabilities.writable,
                    alreadyRegistered = alreadyRegistered,
                    nestedInRegisteredProject = nested,
                    explanation = capabilities.explanation,
                ))
            }
        }
    }

    fun refreshProjectList() {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            refreshProjects()
            _state.update { it.copy(operationInProgress = false) }
        }
    }

    fun navigate(surface: Surface) {
        _state.update { current ->
            if (current.surface == surface) current
            else if (surface == Surface.HOME) current.copy(
                surface = Surface.HOME,
                navigationStack = listOf(Surface.HOME),
                selectedProjectIds = emptySet(),
                statusMessage = null,
            ) else current.copy(
                surface = surface,
                navigationStack = current.navigationStack + surface,
                selectedProjectIds = if (surface == Surface.PROJECTS) current.selectedProjectIds else emptySet(),
                statusMessage = null,
            )
        }
        applicationState.recordSurface(surface)
    }

    fun back(): Boolean {
        val current = _state.value
        if (current.navigationStack.size <= 1) return false
        val stack = current.navigationStack.dropLast(1)
        _state.update { it.copy(surface = stack.last(), navigationStack = stack, statusMessage = null) }
        applicationState.recordSurface(stack.last())
        return true
    }

    fun exit(onComplete: () -> Unit) {
        viewModelScope.launch {
            lifecycle.onExplicitExit()
            onComplete()
        }
    }

    fun requestExitConfirmation() {
        _state.update { it.copy(exitConfirmationVisible = true) }
    }

    fun dismissExitConfirmation() {
        _state.update { it.copy(exitConfirmationVisible = false) }
    }

    fun openProject(projectId: String) {
        restoreProject(projectId, Surface.EDITOR, loadDetails = false)
    }

    fun showProjectDetails(projectId: String) {
        restoreProject(projectId, Surface.PROJECT_DETAILS, loadDetails = true)
    }

    private fun restoreProject(projectId: String, destination: Surface, loadDetails: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    restoring = true,
                    detailsLoading = true,
                    projectDetails = null,
                    selectedProjectId = projectId,
                )
            }
            val result = projects.restore(projectId)
            when (result) {
                is ProjectRestoreResult.Restored -> _state.update {
                    it.copy(
                        restoring = false,
                        selectedProjectId = result.identity.id,
                        surface = destination,
                        navigationStack = it.navigationStack + destination,
                        statusMessage = null,
                    )
                }
                is ProjectRestoreResult.Unavailable -> _state.update {
                    it.copy(
                        restoring = false,
                        detailsLoading = false,
                        projectDetails = null,
                        statusMessage = result.reason,
                    )
                }
            }
            if (result is ProjectRestoreResult.Restored) {
                applicationState.recordProject(result.identity.id)
            }
            if (result is ProjectRestoreResult.Restored && loadDetails) {
                when (val details = detailsService.load(result.identity.id)) {
                    is ProjectDetailsResult.Loaded -> _state.update {
                        it.copy(projectDetails = details.details, detailsLoading = false)
                    }
                    is ProjectDetailsResult.Unavailable -> _state.update {
                        it.copy(
                            detailsLoading = false,
                            projectDetails = null,
                            statusMessage = details.reason,
                        )
                    }
                }
            } else {
                _state.update { it.copy(detailsLoading = false, projectDetails = null) }
            }
            refreshProjects()
        }
    }

    fun refreshSelectedProjectDetails() {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            _state.update { it.copy(detailsLoading = true, statusMessage = null) }
            when (val details = detailsService.load(projectId)) {
                is ProjectDetailsResult.Loaded -> _state.update {
                    it.copy(projectDetails = details.details, detailsLoading = false)
                }
                is ProjectDetailsResult.Unavailable -> _state.update {
                    it.copy(
                        detailsLoading = false,
                        projectDetails = null,
                        statusMessage = details.reason,
                    )
                }
            }
        }
    }

    fun createBlankProject(destinationParentUri: String, name: String, description: String) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = acquisition.createBlankProject(destinationParentUri, name, description)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message) }
            refreshProjects()
        }
    }

    fun importExistingFolder(projectRootUri: String, name: String? = null, description: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = acquisition.importExistingFolder(projectRootUri, name, description)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message, acquiredProjectId = report.affectedIds.firstOrNull()) }
            refreshProjects()
        }
    }

    fun dismissAcquisitionPrompt() {
        _state.update { it.copy(acquiredProjectId = null) }
    }

    fun importZip(
        archiveUri: String,
        destinationParentUri: String,
        name: String,
        description: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = acquisition.importZip(archiveUri, destinationParentUri, name, description)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message) }
            refreshProjects()
        }
    }

    fun removeSelectedProject() {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = operations.removeFromRegistry(projectId)
            _state.update {
                it.copy(
                    operationInProgress = false,
                    statusMessage = report.message,
                    projectDetails = null,
                    selectedProjectId = null,
                    surface = Surface.PROJECTS,
                )
            }
            refreshProjects()
        }
    }

    fun permanentlyDeleteSelectedProject() {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = operations.permanentlyDelete(projectId)
            _state.update {
                it.copy(
                    operationInProgress = false,
                    statusMessage = report.message,
                    projectDetails = null,
                    selectedProjectId = null,
                    surface = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) {
                        Surface.PROJECTS
                    } else it.surface,
                )
            }
            refreshProjects()
        }
    }

    fun exportSelectedProject(
        destinationFileUri: String,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = operations.exportProject(projectId, destinationFileUri)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message) }
            refreshProjects()
            onComplete(report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE)
        }
    }

    fun duplicateSelectedProject(destinationParentUri: String, name: String, description: String? = null) {
        val projectId = _state.value.selectedProjectId ?: return
        runProjectOperation { operations.duplicateProject(projectId, destinationParentUri, name, description) }
    }

    fun relocateSelectedProject(destinationParentUri: String, name: String) {
        val projectId = _state.value.selectedProjectId ?: return
        runProjectOperation(replaceSelection = true) {
            operations.relocateProject(projectId, destinationParentUri, name)
        }
    }

    fun renameSelectedProject(name: String) {
        val projectId = _state.value.selectedProjectId ?: return
        runProjectOperation(replaceSelection = true) {
            operations.renameProject(projectId, name)
        }
    }

    fun removeProjectsFromRegistry(projectIds: List<String>) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = operations.batchRemoveFromRegistry(projectIds)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message, selectedProjectIds = emptySet()) }
            refreshProjects()
        }
    }

    fun exportProjects(projectIds: List<String>, destinationParentUri: String) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = operations.exportProjects(projectIds, destinationParentUri)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message) }
            refreshProjects()
        }
    }

    fun permanentlyDeleteProjects(projectIds: List<String>) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = operations.batchPermanentlyDelete(projectIds)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message, selectedProjectIds = emptySet()) }
            refreshProjects()
        }
    }

    private fun runProjectOperation(
        replaceSelection: Boolean = false,
        operation: suspend () -> dev.android.ide.contracts.OperationReport,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = operation()
            _state.update {
                it.copy(
                    operationInProgress = false,
                    statusMessage = report.message,
                    selectedProjectId = if (
                        replaceSelection &&
                        report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE
                    ) report.affectedIds.firstOrNull() ?: it.selectedProjectId else it.selectedProjectId,
                )
            }
            refreshProjects()
            if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) {
                refreshSelectedProjectDetails()
            }
        }
    }

    private suspend fun refreshProjects() {
        val registered = projects.registeredProjects()
        _state.update { it.copy(projects = registered, projectSummaries = emptyMap()) }
        viewModelScope.launch {
            val summaries = registered.associate { project ->
                project.id to when (val result = detailsService.load(project.id)) {
                    is ProjectDetailsResult.Loaded -> ProjectSummary(
                        fileCount = result.details.fileCount,
                        totalBytes = result.details.totalBytes,
                        hasGit = result.details.git != null,
                    )
                    is ProjectDetailsResult.Unavailable -> ProjectSummary(
                        status = when (project.location.capabilityState) {
                            dev.android.ide.contracts.CapabilityState.PERMISSION_LOST -> "Permission needed"
                            dev.android.ide.contracts.CapabilityState.UNSUPPORTED -> "Not supported"
                            else -> "Unavailable"
                        },
                    )
                }
            }
            _state.update { it.copy(projectSummaries = summaries) }
        }
    }

    private suspend fun restoreLastProject() {
        val lastProjectId = applicationState.lastProjectId() ?: return
        projects.restore(lastProjectId)
        refreshProjects()
    }
}
