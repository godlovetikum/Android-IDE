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
    val selectedProjectId: String? = null,
    val previousSurface: Surface? = null,
    val statusMessage: String? = null,
    val restoring: Boolean = false,
    val operationInProgress: Boolean = false,
    val projectDetails: ProjectDetails? = null,
    val detailsLoading: Boolean = false,
)

class AppShellViewModel(application: Application) : AndroidViewModel(application) {
    private val saf = SafRepository(application)
    private val registry = ProjectRegistryStore(ProjectRepository(application))
    private val storage = ProjectStorageAdapterImpl(saf)
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
            else current.copy(
                surface = surface,
                previousSurface = current.surface,
                statusMessage = null,
            )
        }
        applicationState.recordSurface(surface)
    }

    fun back(): Boolean {
        val current = _state.value
        val target = current.previousSurface ?: when (current.surface) {
            Surface.HOME -> Surface.HOME
            Surface.PROJECT_DETAILS -> Surface.PROJECTS
            else -> Surface.HOME
        }
        if (target == current.surface) return false
        navigate(target)
        return true
    }

    fun exit(onComplete: () -> Unit) {
        viewModelScope.launch {
            lifecycle.onExplicitExit()
            onComplete()
        }
    }

    fun openProject(projectId: String) {
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
                        surface = Surface.PROJECT_DETAILS,
                        previousSurface = Surface.PROJECTS,
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

    fun importExistingFolder(projectRootUri: String) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null) }
            val report = acquisition.importExistingFolder(projectRootUri, null, null)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message) }
            refreshProjects()
        }
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

    fun duplicateSelectedProject(destinationParentUri: String, name: String) {
        val projectId = _state.value.selectedProjectId ?: return
        runProjectOperation { operations.duplicateProject(projectId, destinationParentUri, name) }
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
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message) }
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
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message) }
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
        _state.update { it.copy(projects = projects.registeredProjects()) }
    }

    private suspend fun restoreLastProject() {
        val lastProjectId = applicationState.lastProjectId() ?: return
        projects.restore(lastProjectId)
        refreshProjects()
    }
}
