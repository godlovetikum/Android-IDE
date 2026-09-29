// AppShellViewModel coordinates application-scope state while delegating project,
// metadata, and lifecycle ownership to their respective services.
package dev.android.ide.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.android.ide.data.ProjectRepository
import dev.android.ide.project.ProjectMetadataAdapterImpl
import dev.android.ide.project.ProjectStorageAdapterImpl
import dev.android.ide.project.ProjectRegistryStore
import dev.android.ide.contracts.Surface
import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.ProjectLocation
import dev.android.ide.contracts.OperationReport
import dev.android.ide.lifecycle.LifecycleCoordinatorImpl
import dev.android.ide.project.ProjectStateService
import dev.android.ide.project.ProjectRestoreResult
import dev.android.ide.project.ProjectAcquisitionService
import dev.android.ide.project.CreateProjectTemplate
import dev.android.ide.project.ProjectDetailsResult
import dev.android.ide.project.ProjectDetailsService
import dev.android.ide.data.model.ProjectDetails
import dev.android.ide.project.ProjectOperationsService
import dev.android.ide.saf.SafRepository
import dev.android.ide.runtime.RuntimeStateStore
import dev.android.ide.runtime.TerminalRuntimeAdapterImpl
import com.termux.terminal.TerminalSession
import dev.android.ide.contracts.RuntimeCapabilities
import dev.android.ide.contracts.SessionDescriptor
import dev.android.ide.contracts.SessionAvailability
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
    val operationReport: OperationReport? = null,
    val registryWarning: String? = null,
    val restoring: Boolean = false,
    val refreshingProjects: Boolean = false,
    val operationInProgress: Boolean = false,
    val projectDetails: ProjectDetails? = null,
    val folderInspection: FolderInspection? = null,
    val acquiredProjectId: String? = null,
    val detailsLoading: Boolean = false,
    val exitConfirmationVisible: Boolean = false,
    val runtimeCapabilities: RuntimeCapabilities? = null,
    val terminalSessions: List<SessionDescriptor> = emptyList(),
    val selectedTerminalSessionId: String? = null,
    val terminalOutput: String = "",
    val terminalFeedback: OperationReport? = null,
    val terminalOperationInProgress: Boolean = false,
    val installedRuntimePackages: List<dev.android.ide.contracts.RuntimePackage> = emptyList(),
    val navigationPromptVisible: Boolean = false,
    val pendingNavigation: Surface? = null,
    val pendingBackNavigation: Boolean = false,
)

data class FolderInspection(
    val path: String,
    val capabilityState: CapabilityState,
    val readable: Boolean,
    val writable: Boolean,
    val alreadyRegistered: Boolean,
    val containmentVerified: Boolean,
    val overlapsRegisteredProject: Boolean,
    val explanation: String?,
)

data class ProjectSummary(
    val fileCount: Int? = null,
    val totalBytes: Long? = null,
    val hasGit: Boolean? = null,
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
    private val terminalRuntime = TerminalRuntimeAdapterImpl(application)
    private var restoreJob: kotlinx.coroutines.Job? = null
    private var restoreGeneration = 0L
    private var projectRefreshGeneration = 0L
    private var folderInspectionGeneration = 0L

    private val _state = MutableStateFlow(AppShellState())
    val state: StateFlow<AppShellState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            lifecycle.restore()
            refreshProjects()
            restoreLastProject()
        }
        viewModelScope.launch {
            runtimeState.ensureReady()
            terminalRuntime.initialize()
            _state.update { it.copy(runtimeCapabilities = terminalRuntime.capabilities()) }
            refreshTerminalSessions()
            refreshRuntimePackages()
        }
    }

    fun foreground() {
        viewModelScope.launch { lifecycle.onForeground() }
    }

    fun background() {
        viewModelScope.launch { lifecycle.onBackground() }
    }

    fun refreshTerminalSessions() {
        viewModelScope.launch {
            val sessions = terminalRuntime.listSessions()
            _state.update {
                it.copy(
                    terminalSessions = sessions,
                    selectedTerminalSessionId = it.selectedTerminalSessionId?.takeIf { id -> sessions.any { session -> session.id == id && session.availability == SessionAvailability.AVAILABLE } }
                        ?: sessions.firstOrNull { session -> session.availability == SessionAvailability.AVAILABLE }?.id,
                    runtimeCapabilities = terminalRuntime.capabilities(),
                )
            }
        }
    }

    fun createTerminalSession(workingDirectory: String? = null, name: String = "") {
        viewModelScope.launch {
            val selected = _state.value.selectedProjectId
            val project = _state.value.projects.firstOrNull { it.id == selected }
            val access = project?.let { terminalRuntime.inspectProjectAccess(it) }
            if (access != null && !access.available) {
                _state.update { it.copy(terminalFeedback = OperationReport(dev.android.ide.contracts.OperationOutcome.BLOCKED, access.explanation ?: "Terminal access is unavailable", dev.android.ide.contracts.ErrorCategory.UNAVAILABLE_RUNTIME)) }
                return@launch
            }
            val session = terminalRuntime.createSession(
                workingDirectory ?: project?.let { terminalRuntime.workingDirectory(it) },
                name.trim().ifBlank { "Untitled session" },
            )
            val locationNotice = access?.explanation?.let { explanation ->
                OperationReport(
                    dev.android.ide.contracts.OperationOutcome.COMPLETE,
                    "Terminal opened in its command-line home workspace instead of the project folder. $explanation",
                )
            }
            _state.update { it.copy(selectedTerminalSessionId = session.id, terminalFeedback = locationNotice) }
            refreshTerminalSessions()
        }
    }

    fun resizeTerminal(columns: Int, rows: Int) {
        val sessionId = _state.value.selectedTerminalSessionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(terminalFeedback = terminalRuntime.resize(sessionId, columns, rows)) }
        }
    }

    fun refreshRuntimePackages() {
        viewModelScope.launch {
            _state.update { it.copy(installedRuntimePackages = terminalRuntime.installedPackages()) }
        }
    }

    fun installRuntimePackages(packageNames: List<String>) {
        val requested = packageNames.map(String::trim).filter(String::isNotBlank).distinct()
        if (requested.isEmpty()) {
            _state.update { it.copy(terminalFeedback = OperationReport(dev.android.ide.contracts.OperationOutcome.BLOCKED, "Choose at least one package", dev.android.ide.contracts.ErrorCategory.PACKAGE_FAILURE)) }
            return
        }
        viewModelScope.launch {
            val capabilities = terminalRuntime.capabilities()
            if (!capabilities.packageManagerAvailable) {
                _state.update { it.copy(terminalFeedback = OperationReport(dev.android.ide.contracts.OperationOutcome.BLOCKED, "The terminal package manager is unavailable", dev.android.ide.contracts.ErrorCategory.UNAVAILABLE_RUNTIME)) }
                return@launch
            }
            _state.update { it.copy(terminalOperationInProgress = true, terminalFeedback = OperationReport(dev.android.ide.contracts.OperationOutcome.BLOCKED, "Installing selected packages…", dev.android.ide.contracts.ErrorCategory.PACKAGE_FAILURE)) }
            val report = terminalRuntime.installPackages(requested)
            _state.update { it.copy(terminalOperationInProgress = false, terminalFeedback = report) }
            refreshRuntimePackages()
        }
    }

    fun terminalSession(sessionId: String): TerminalSession? = terminalRuntime.terminalSession(sessionId)
    fun bindTerminalView(sessionId: String, invalidate: () -> Unit) = terminalRuntime.bindTerminalView(sessionId, invalidate)
    fun unbindTerminalView(sessionId: String) = terminalRuntime.unbindTerminalView(sessionId)

    fun selectTerminalSession(sessionId: String) {
        val session = _state.value.terminalSessions.firstOrNull { it.id == sessionId } ?: return
        if (session.availability != SessionAvailability.AVAILABLE) return
        _state.update { it.copy(selectedTerminalSessionId = sessionId, terminalOutput = terminalRuntime.outputSnapshot(sessionId)) }
    }

    fun sendTerminalInput(input: String) {
        sendTerminalInput(input.toByteArray(Charsets.UTF_8))
    }

    fun sendTerminalInput(input: ByteArray) {
        val sessionId = _state.value.selectedTerminalSessionId ?: return
        viewModelScope.launch {
            val report = terminalRuntime.sendInput(sessionId, input)
            _state.update { it.copy(terminalFeedback = report) }
            refreshTerminalOutput()
        }
    }

    fun refreshTerminalOutput() {
        val sessionId = _state.value.selectedTerminalSessionId ?: return
        viewModelScope.launch {
            terminalRuntime.readOutput(sessionId)
            _state.update { it.copy(terminalOutput = terminalRuntime.outputSnapshot(sessionId)) }
        }
    }

    fun interruptTerminalSession() {
        val sessionId = _state.value.selectedTerminalSessionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(terminalFeedback = terminalRuntime.interrupt(sessionId)) }
            refreshTerminalSessions()
        }
    }

    fun closeTerminalSession(sessionId: String) {
        viewModelScope.launch {
            _state.update { it.copy(terminalFeedback = terminalRuntime.closeSession(sessionId)) }
            refreshTerminalSessions()
        }
    }

    fun renameTerminalSession(sessionId: String, name: String) {
        viewModelScope.launch {
            val report = terminalRuntime.renameSession(sessionId, name)
            _state.update { it.copy(terminalFeedback = report) }
            if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) refreshTerminalSessions()
        }
    }

    fun closeAllTerminalSessions() {
        viewModelScope.launch {
            _state.update { it.copy(terminalFeedback = terminalRuntime.closeAllSessions()) }
            refreshTerminalSessions()
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun reportStatus(message: String) {
        _state.update { it.copy(statusMessage = message, operationReport = null) }
    }

    fun copyProjectRemoteUrls(projectId: String) {
        viewModelScope.launch {
            _state.update { it.copy(detailsLoading = true, statusMessage = null) }
            when (val result = detailsService.load(projectId)) {
                is ProjectDetailsResult.Loaded -> {
                    val remotes = result.details.git?.remotes.orEmpty()
                    val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java)
                    if (remotes.isEmpty()) {
                        reportStatus("No Git remote URLs are configured for this project")
                    } else if (clipboard == null) {
                        reportStatus("The system clipboard is unavailable")
                    } else {
                        val text = remotes.joinToString("\n") { remote ->
                            "${remote.name}\t${remote.url.substringBefore('?').substringBefore('#')}"
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("Git remote URLs", text))
                        reportStatus("Copied ${remotes.size} Git remote URL(s)")
                    }
                }
                is ProjectDetailsResult.Unavailable -> reportStatus(result.reason)
            }
            _state.update { it.copy(detailsLoading = false) }
        }
    }

    fun clearOperationFeedback() {
        _state.update { it.copy(statusMessage = null, operationReport = null, acquiredProjectId = null) }
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
        val generation = ++folderInspectionGeneration
        _state.update { it.copy(folderInspection = null) }
        viewModelScope.launch {
            val location = ProjectLocation(projectRootUri, projectRootUri, projectRootUri)
            val capabilities = storage.inspectProjectStorage(location)
            val registered = registry.listRegistered()
            val alreadyRegistered = registered.any { it.location.stableId == projectRootUri }
            var containmentVerified = true
            var overlapsRegisteredProject = false
            registered.filterNot { it.location.stableId == projectRootUri }.forEach { project ->
                val existingContainsSelected = storage.isSameOrDescendant(
                    project.location.stableId,
                    projectRootUri,
                )
                val selectedContainsExisting = storage.isSameOrDescendant(
                    projectRootUri,
                    project.location.stableId,
                )
                if (existingContainsSelected == null || selectedContainsExisting == null) {
                    containmentVerified = false
                }
                if (existingContainsSelected == true || selectedContainsExisting == true) {
                    overlapsRegisteredProject = true
                }
            }
            if (generation != folderInspectionGeneration) return@launch
            _state.update {
                it.copy(folderInspection = FolderInspection(
                    path = projectRootUri,
                    capabilityState = capabilities.state,
                    readable = capabilities.readable,
                    writable = capabilities.writable,
                    alreadyRegistered = alreadyRegistered,
                    containmentVerified = containmentVerified,
                    overlapsRegisteredProject = overlapsRegisteredProject,
                    explanation = capabilities.explanation,
                ))
            }
        }
    }

    fun refreshProjectList() {
        viewModelScope.launch {
            _state.update { it.copy(refreshingProjects = true, statusMessage = null, operationReport = null) }
            try {
                refreshProjects()
            } finally {
                _state.update { it.copy(refreshingProjects = false) }
            }
        }
    }

    fun navigate(surface: Surface) {
        if (_state.value.operationInProgress) {
            _state.update { it.copy(navigationPromptVisible = true, pendingNavigation = surface, pendingBackNavigation = false) }
            return
        }
        restoreGeneration++
        restoreJob?.cancel()
        _state.update { current ->
            if (current.surface == surface) current.copy(restoring = false, detailsLoading = false)
            else if (surface == Surface.HOME) current.copy(
                surface = Surface.HOME,
                navigationStack = listOf(Surface.HOME),
                selectedProjectIds = emptySet(),
                statusMessage = null,
                restoring = false,
                detailsLoading = false,
            ) else current.copy(
                surface = surface,
                navigationStack = current.navigationStack + surface,
                selectedProjectIds = if (surface == Surface.PROJECTS) current.selectedProjectIds else emptySet(),
                statusMessage = null,
                restoring = false,
                detailsLoading = false,
            )
        }
        applicationState.recordSurface(surface)
    }

    fun back(): Boolean {
        val current = _state.value
        if (current.operationInProgress) {
            _state.update { it.copy(navigationPromptVisible = true, pendingNavigation = null, pendingBackNavigation = true) }
            return true
        }
        if (current.navigationStack.size <= 1) return false
        restoreGeneration++
        restoreJob?.cancel()
        val stack = current.navigationStack.dropLast(1)
        _state.update {
            it.copy(
                surface = stack.last(),
                navigationStack = stack,
                statusMessage = null,
                restoring = false,
                detailsLoading = false,
            )
        }
        applicationState.recordSurface(stack.last())
        return true
    }

    fun dismissNavigationPrompt() {
        _state.update { it.copy(navigationPromptVisible = false, pendingNavigation = null, pendingBackNavigation = false) }
    }

    fun continuePendingNavigation() {
        val current = _state.value
        if (current.pendingBackNavigation) {
            _state.update { it.copy(navigationPromptVisible = false, pendingNavigation = null, pendingBackNavigation = false) }
            if (current.navigationStack.size > 1) {
                val stack = current.navigationStack.dropLast(1)
                _state.update { it.copy(surface = stack.last(), navigationStack = stack, restoring = false, detailsLoading = false) }
                applicationState.recordSurface(stack.last())
            }
            return
        }
        val destination = current.pendingNavigation ?: return dismissNavigationPrompt()
        if (_state.value.operationInProgress) return
        _state.update { it.copy(navigationPromptVisible = false, pendingNavigation = null, pendingBackNavigation = false) }
        navigate(destination)
    }

    fun exit(onComplete: () -> Unit) {
        viewModelScope.launch {
            terminalRuntime.shutdown()
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
        val generation = ++restoreGeneration
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    restoring = true,
                    detailsLoading = true,
                    projectDetails = null,
                    selectedProjectId = projectId,
                )
            }
            val result = projects.restore(projectId)
            if (generation != restoreGeneration) return@launch
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
                val details = detailsService.load(result.identity.id)
                if (generation != restoreGeneration) return@launch
                when (details) {
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
            if (generation != restoreGeneration) return@launch
            refreshProjects()
        }
    }

    fun refreshSelectedProjectDetails() {
        val projectId = _state.value.selectedProjectId ?: return
        val generation = restoreGeneration
        viewModelScope.launch {
            _state.update { it.copy(detailsLoading = true, statusMessage = null, operationReport = null) }
            val details = detailsService.load(projectId)
            if (generation != restoreGeneration || _state.value.selectedProjectId != projectId) return@launch
            when (details) {
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

    fun createBlankProject(
        destinationParentUri: String,
        name: String,
        description: String,
        template: CreateProjectTemplate = CreateProjectTemplate.FROM_SCRATCH,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = acquisition.createBlankProject(destinationParentUri, name, description, template)
            _state.update {
                it.copy(
                    operationInProgress = false,
                    statusMessage = report.message,
                    operationReport = report,
                    acquiredProjectId = report.affectedIds.firstOrNull().takeIf { report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE },
                )
            }
            refreshProjects()
        }
    }

    fun importExistingFolder(projectRootUri: String, name: String? = null, description: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = acquisition.importExistingFolder(projectRootUri, name, description)
            _state.update {
                it.copy(
                    operationInProgress = false,
                    statusMessage = report.message,
                    operationReport = report,
                    acquiredProjectId = report.affectedIds.firstOrNull().takeIf { report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE },
                )
            }
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
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = acquisition.importZip(archiveUri, destinationParentUri, name, description)
            _state.update {
                it.copy(
                    operationInProgress = false,
                    statusMessage = report.message,
                    operationReport = report,
                    acquiredProjectId = report.affectedIds.firstOrNull().takeIf { report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE },
                )
            }
            refreshProjects()
        }
    }

    fun removeSelectedProject() {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = operations.removeFromRegistry(projectId)
            _state.update {
                it.copy(
                    operationInProgress = false,
                    statusMessage = report.message,
                    operationReport = report,
                    projectDetails = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) null else it.projectDetails,
                    selectedProjectId = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) null else it.selectedProjectId,
                    surface = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) Surface.PROJECTS else it.surface,
                )
            }
            refreshProjects()
        }
    }

    fun permanentlyDeleteSelectedProject() {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = operations.permanentlyDelete(projectId)
            _state.update {
                it.copy(
                    operationInProgress = false,
                    statusMessage = report.message,
                    operationReport = report,
                    projectDetails = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) null else it.projectDetails,
                    selectedProjectId = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) null else it.selectedProjectId,
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
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = operations.exportProject(projectId, destinationFileUri)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message, operationReport = report) }
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
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = operations.batchRemoveFromRegistry(projectIds)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message, operationReport = report, selectedProjectIds = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) emptySet() else it.selectedProjectIds) }
            refreshProjects()
        }
    }

    fun exportProjects(projectIds: List<String>, destinationParentUri: String) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = operations.exportProjects(projectIds, destinationParentUri)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message, operationReport = report) }
            refreshProjects()
        }
    }

    fun permanentlyDeleteProjects(projectIds: List<String>) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = operations.batchPermanentlyDelete(projectIds)
            _state.update { it.copy(operationInProgress = false, statusMessage = report.message, operationReport = report, selectedProjectIds = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) emptySet() else it.selectedProjectIds) }
            refreshProjects()
        }
    }

    private fun runProjectOperation(
        replaceSelection: Boolean = false,
        operation: suspend () -> dev.android.ide.contracts.OperationReport,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(operationInProgress = true, statusMessage = null, operationReport = null) }
            val report = operation()
            _state.update {
                it.copy(
                    operationInProgress = false,
                    statusMessage = report.message,
                    operationReport = report,
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
        val generation = ++projectRefreshGeneration
        val registered = projects.registeredProjects()
        if (generation != projectRefreshGeneration) return
        val currentIds = registered.map { it.id }.toSet()
        val warning = registry.warning()
        _state.update { it.copy(projects = registered, projectSummaries = emptyMap(), registryWarning = warning) }
        val summaries = registered.associate { project ->
            project.id to when (val result = detailsService.load(project.id)) {
                is ProjectDetailsResult.Loaded -> ProjectSummary(
                    fileCount = result.details.fileCount,
                    totalBytes = result.details.totalBytes,
                    hasGit = result.details.git != null,
                )
                is ProjectDetailsResult.Unavailable -> ProjectSummary(
                    status = result.reason,
                )
            }
        }
        if (generation == projectRefreshGeneration && _state.value.projects.map { it.id }.toSet() == currentIds) {
            _state.update { it.copy(projectSummaries = summaries) }
        }
    }

    private suspend fun restoreLastProject() {
        val lastProjectId = applicationState.lastProjectId() ?: return
        projects.restore(lastProjectId)
        refreshProjects()
    }
}
