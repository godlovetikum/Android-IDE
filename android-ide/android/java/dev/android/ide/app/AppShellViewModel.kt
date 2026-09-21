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
)

class AppShellViewModel(application: Application) : AndroidViewModel(application) {
    private val saf = SafRepository(application)
    private val registry = ProjectRegistryStore(ProjectRepository(application))
    private val storage = ProjectStorageAdapterImpl(saf)
    private val metadata = ProjectMetadataAdapterImpl(saf)
    private val projects = ProjectStateService(registry, storage, metadata)
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
            _state.update { it.copy(restoring = true, selectedProjectId = projectId) }
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
                    it.copy(restoring = false, statusMessage = result.reason)
                }
            }
            if (result is ProjectRestoreResult.Restored) {
                applicationState.recordProject(result.identity.id)
            }
            refreshProjects()
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
