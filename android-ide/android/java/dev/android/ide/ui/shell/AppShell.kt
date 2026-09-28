package dev.android.ide.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import dev.android.ide.app.AppShellState
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.Surface
import dev.android.ide.ui.screen.HomeSurface
import dev.android.ide.ui.screen.ProjectDetailsSurface
import dev.android.ide.ui.screen.EditorSurface
import dev.android.ide.ui.screen.ProjectsSurface
import dev.android.ide.ui.screen.SettingsScreen
import dev.android.ide.ui.screen.TerminalSurface
import dev.android.ide.viewmodel.IdeViewModel
import kotlinx.coroutines.launch

@Composable
fun AppShell(
    viewModel: AppShellViewModel,
    ideViewModel: IdeViewModel,
    onExit: () -> Unit,
    onCreateProject: (String?) -> Unit,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onCloneGit: () -> Unit,
    onExportProject: (String) -> Unit,
    onExportProjects: (Set<String>) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onRelocateProject: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val ideState by ideViewModel.uiState.collectAsState()
    var editorNavigationPrompt by rememberSaveable { mutableStateOf(false) }
    var pendingEditorNavigation by rememberSaveable { mutableStateOf<Surface?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun dismissEditorIme() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var phaseFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSection by rememberSaveable { mutableStateOf<String?>(null) }
    var editorPanelRequest by rememberSaveable { mutableStateOf(0L) }
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val surfaceStateHolder = rememberSaveableStateHolder()

    BackHandler(
        enabled = drawerState.isOpen || phaseFeedback != null || editorNavigationPrompt || state.operationInProgress ||
            state.exitConfirmationVisible || state.navigationStack.size > 1,
    ) {
        when {
            state.exitConfirmationVisible -> viewModel.dismissExitConfirmation()
            editorNavigationPrompt -> { editorNavigationPrompt = false; pendingEditorNavigation = null }
            phaseFeedback != null -> phaseFeedback = null
            drawerState.isOpen -> { moreOpen = false; coroutineScope.launch { drawerState.close() } }
            !viewModel.back() -> viewModel.requestExitConfirmation()
        }
    }

    fun navigate(surface: Surface) {
        dismissEditorIme()
        if (ideState.fileMutationLoading) {
            pendingEditorNavigation = surface
            editorNavigationPrompt = true
            return
        }
        viewModel.navigate(surface)
        moreOpen = false
        coroutineScope.launch { drawerState.close() }
    }
    val surface: @Composable (Modifier) -> Unit = { modifier ->
        SurfaceHost(
            modifier = modifier,
            state = state,
            viewModel = viewModel,
            ideViewModel = ideViewModel,
            onNavigate = ::navigate,
            onCreateProject = onCreateProject,
            onImportFolder = onImportFolder,
            onImportZip = onImportZip,
            onCloneGit = onCloneGit,
            onExportProject = onExportProject,
            onExportProjects = onExportProjects,
            onDuplicateProject = onDuplicateProject,
            onRelocateProject = onRelocateProject,
            onRequestExit = viewModel::requestExitConfirmation,
            onOpenNavigation = {
                dismissEditorIme()
                coroutineScope.launch { drawerState.open() }
            },
            onFeedback = { phaseFeedback = it },
            settingsSection = settingsSection,
            onSettingsSectionConsumed = { settingsSection = null },
            editorPanelRequest = editorPanelRequest,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                ContextualNavigation(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    ideViewModel = ideViewModel,
                    moreOpen = moreOpen,
                    onMore = { moreOpen = !moreOpen },
                    onNavigate = ::navigate,
                    onOpenProject = { viewModel.openProject(it); coroutineScope.launch { drawerState.close() } },
                    onCreateProject = { onCreateProject(null); moreOpen = false; coroutineScope.launch { drawerState.close() } },
                    onImportFolder = { onImportFolder(); moreOpen = false; coroutineScope.launch { drawerState.close() } },
                    onImportZip = { onImportZip(); moreOpen = false; coroutineScope.launch { drawerState.close() } },
                    onCloneGit = { onCloneGit(); moreOpen = false; coroutineScope.launch { drawerState.close() } },
                    onFeedback = { phaseFeedback = it },
                    onSettingsSection = { section -> settingsSection = section; navigate(Surface.SETTINGS) },
                    onDismissDrawer = { coroutineScope.launch { drawerState.close() } },
                    onOpenEditorPanel = { editorPanelRequest += 1; coroutineScope.launch { drawerState.close() } },
                )
            }
        },
    ) {
    Scaffold(
        topBar = {
            if (state.surface != Surface.EDITOR && state.surface != Surface.SETTINGS) {
                TopAppBar(
                    title = { Text(surfaceTitle(state.surface)) },
                            navigationIcon = {
                                IconButton(onClick = {
                            coroutineScope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() }
                            if (drawerState.isOpen) moreOpen = false
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                        }
                    },
                )
            }
        },
    ) { padding ->
        surfaceStateHolder.SaveableStateProvider(state.surface.name) {
            surface(Modifier.padding(padding).fillMaxSize())
        }
    }
    }

    if (state.exitConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExitConfirmation,
            title = { Text("Exit Android IDE application?") },
            text = { Text("Are you sure you want to exit the Android IDE application?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissExitConfirmation(); viewModel.exit(onExit) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Exit") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissExitConfirmation) { Text("Cancel") } },
        )
    }
    if (editorNavigationPrompt) {
        AlertDialog(
            onDismissRequest = { editorNavigationPrompt = false; pendingEditorNavigation = null },
            title = { Text("File operation in progress") },
            text = { Text("A file operation is still running. Stay here to keep its progress visible, or leave it running in the background and continue.") },
            confirmButton = {
                Button(onClick = {
                    val destination = pendingEditorNavigation
                    editorNavigationPrompt = false
                    pendingEditorNavigation = null
                    if (destination != null) viewModel.navigate(destination)
                }) { Text("Leave running") }
            },
            dismissButton = { TextButton(onClick = { editorNavigationPrompt = false; pendingEditorNavigation = null }) { Text("Keep waiting") } },
        )
    }
    if (state.navigationPromptVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNavigationPrompt,
            title = { Text("Operation in progress") },
            text = { Text("An operation is still running. Stay here to keep its progress visible, or leave it running in the background and continue.") },
            confirmButton = { Button(onClick = viewModel::continuePendingNavigation) { Text("Leave running") } },
            dismissButton = { TextButton(onClick = viewModel::dismissNavigationPrompt) { Text("Keep waiting") } },
        )
    }
    phaseFeedback?.let { action ->
        AlertDialog(
            onDismissRequest = { phaseFeedback = null },
            title = { Text(action) },
            text = {
                Text("$action not available. Coming soon (phase ${placeholderPhase(action)})")
            },
            confirmButton = { TextButton(onClick = { phaseFeedback = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun ContextualNavigation(
    modifier: Modifier,
    state: AppShellState,
    ideViewModel: IdeViewModel,
    moreOpen: Boolean,
    onMore: () -> Unit,
    onNavigate: (Surface) -> Unit,
    onOpenProject: (String) -> Unit,
    onCreateProject: (String?) -> Unit,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onCloneGit: () -> Unit,
    onFeedback: (String) -> Unit,
    onSettingsSection: (String) -> Unit,
    onDismissDrawer: () -> Unit,
    onOpenEditorPanel: () -> Unit,
) {
    val navigationActive = state.surface in setOf(Surface.HOME, Surface.PROJECTS, Surface.EXTENSIONS, Surface.PROJECT_DETAILS)
    LazyColumn(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                NavigationTopItem(Icons.Default.Home, "Navigation", navigationActive) { onNavigate(Surface.HOME) }
                NavigationTopItem(Icons.Default.Code, "Editor", state.surface == Surface.EDITOR) { onNavigate(Surface.EDITOR) }
                NavigationTopItem(Icons.Default.Terminal, "Terminal", state.surface == Surface.TERMINAL) { onNavigate(Surface.TERMINAL) }
                NavigationTopItem(Icons.Default.Language, "Browser", state.surface == Surface.BROWSER) { onNavigate(Surface.BROWSER) }
                NavigationTopItem(Icons.Default.MergeType, "Git", state.surface == Surface.GIT) { onNavigate(Surface.GIT) }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        when {
            state.surface == Surface.PROJECTS -> {
                item { Text("Project management", style = MaterialTheme.typography.labelLarge) }
                item { NavigationItem(Icons.Default.Add, "Create blank project", false) { onCreateProject(null) } }
                item { NavigationItem(Icons.Default.FolderOpen, "Import existing folder", false) { onImportFolder() } }
                item { NavigationItem(Icons.Default.FolderOpen, "Import ZIP archive", false) { onImportZip() } }
                item { NavigationItem(Icons.Default.MergeType, "Clone remote Git repository", false) { onCloneGit() } }
                if (state.projects.isNotEmpty()) {
                    item { Text("Recent projects", style = MaterialTheme.typography.labelLarge) }
                    items(state.projects.take(5), key = { it.id }) { project -> ProjectContextItem(project, onOpenProject) }
                }
            }
            navigationActive -> {
                item { Text("Navigation", style = MaterialTheme.typography.labelLarge) }
                item { NavigationItem(Icons.Default.Home, "Home", state.surface == Surface.HOME) { onNavigate(Surface.HOME) } }
                item { NavigationItem(Icons.Default.FolderOpen, "Projects", state.surface == Surface.PROJECTS) { onNavigate(Surface.PROJECTS) } }
                item { NavigationItem(Icons.Default.Extension, "Extensions", state.surface == Surface.EXTENSIONS) { onNavigate(Surface.EXTENSIONS) } }
                item { NavigationItem(Icons.Default.Settings, "Settings", state.surface == Surface.SETTINGS) { onNavigate(Surface.SETTINGS) } }
            }
            state.surface == Surface.EDITOR -> {
                item { Text("Editor context", style = MaterialTheme.typography.labelLarge) }
                item { NavigationItem(Icons.Default.FolderOpen, "Project file tree", true) { onOpenEditorPanel() } }
                item { NavigationItem(Icons.Default.Search, "Search filenames", false) { ideViewModel.showFileSearch(); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.Source, "Search project contents", false) { ideViewModel.showContentSearch(); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.FolderOpen, "Locate current file", false) { ideViewModel.revealActiveFile(); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.Settings, "Editor settings", false) { onSettingsSection("Editor"); onDismissDrawer() } }
            }
            state.surface == Surface.SETTINGS -> {
                item { Text("Settings categories", style = MaterialTheme.typography.labelLarge) }
                item { NavigationItem(Icons.Default.Settings, "App Theme", false) { onSettingsSection("App Theme") } }
                item { NavigationItem(Icons.Default.Code, "Editor", false) { onSettingsSection("Editor") } }
                item { NavigationItem(Icons.Default.FolderOpen, "File Tree", false) { onSettingsSection("File Tree") } }
                item { NavigationItem(Icons.Default.FolderOpen, "Project Storage", false) { onSettingsSection("Project Storage") } }
                item { NavigationItem(Icons.Default.Terminal, "Controls", false) { onSettingsSection("Controls") } }
                item { UnavailableSidebarFeature("Credentials and security", 8) }
            }
            state.surface == Surface.TERMINAL -> {
                item { Text("Terminal sessions", style = MaterialTheme.typography.labelLarge) }
                item { NavigationItem(Icons.Default.Add, "New terminal session", false) { viewModel.createTerminalSession(); onDismissDrawer() } }
                state.terminalSessions.forEach { session ->
                    item { NavigationItem(Icons.Default.Terminal, session.workingDirectory ?: "Session ${session.id.take(6)}", session.id == state.selectedTerminalSessionId) { viewModel.selectTerminalSession(session.id); onDismissDrawer() } }
                }
                item { NavigationItem(Icons.Default.Close, "Close all sessions", false) { viewModel.closeAllTerminalSessions(); onDismissDrawer() } }
            }
            state.surface == Surface.BROWSER -> item { UnavailableSidebarFeature("Browser", 5) }
            state.surface == Surface.GIT -> item { UnavailableSidebarFeature("Git", 6) }
            else -> item { UnavailableSidebarFeature(surfaceTitle(state.surface), placeholderPhase(surfaceTitle(state.surface))) }
        }
    }
}

@Composable
private fun ProjectContextItem(project: ProjectIdentity, onOpenProject: (String) -> Unit) {
    TextButton(onClick = { onOpenProject(project.id) }, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(project.name, style = MaterialTheme.typography.titleSmall)
            Text(if (project.description.isBlank()) "No description" else project.description, maxLines = 2)
            Text(relativeOpened(project.lastOpenedAt), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NavigationItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavigationTopItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp)) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UnavailableSidebarFeature(feature: String, phase: Int) {
    Text("$feature not available. Coming soon (phase $phase)", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SurfaceHost(
    modifier: Modifier,
    state: AppShellState,
    viewModel: AppShellViewModel,
    ideViewModel: IdeViewModel,
    onNavigate: (Surface) -> Unit,
    onCreateProject: (String?) -> Unit,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onCloneGit: () -> Unit,
    onExportProject: (String) -> Unit,
    onExportProjects: (Set<String>) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onRelocateProject: (String) -> Unit,
    onRequestExit: () -> Unit,
    onOpenNavigation: () -> Unit,
    onFeedback: (String) -> Unit,
    settingsSection: String?,
    onSettingsSectionConsumed: () -> Unit,
    editorPanelRequest: Long,
) {
    val ideState by ideViewModel.uiState.collectAsState()
    when (state.surface) {
        Surface.HOME -> HomeSurface(onNavigate = onNavigate, onExit = onRequestExit, onFeedback = onFeedback)
        Surface.PROJECTS -> ProjectsSurface(state, viewModel, onCreateProject, onImportFolder, onImportZip, onCloneGit, onExportProject, onExportProjects, onDuplicateProject, onRelocateProject, onFeedback, modifier)
        Surface.PROJECT_DETAILS -> ProjectDetailsSurface(state, viewModel, onExportProject, onDuplicateProject, onRelocateProject, modifier)
        Surface.EDITOR -> EditorSurface(
            shellState = state,
            shellViewModel = viewModel,
            ideViewModel = ideViewModel,
            onOpenGlobalNavigation = onOpenNavigation,
            editorPanelRequest = editorPanelRequest,
            onOpenSettings = { onNavigate(Surface.SETTINGS) },
            onExportProject = onExportProject,
            onFeedback = onFeedback,
            modifier = modifier,
        )
        Surface.TERMINAL -> TerminalSurface(state, viewModel, modifier)
        Surface.SETTINGS -> SettingsScreen(
            uiState = ideState,
            ideViewModel = ideViewModel,
            onNavigationIconClick = onOpenNavigation,
            scrollToSection = settingsSection,
            onScrollConsumed = onSettingsSectionConsumed,
        )
        else -> DomainPlaceholderSurface(surfaceTitle(state.surface), modifier, onFeedback)
    }
}

@Composable
private fun DomainPlaceholderSurface(title: String, modifier: Modifier, onFeedback: (String) -> Unit) {
    Column(modifier.padding(20.dp)) {
        Text("$title not available. Coming soon (phase ${placeholderPhase(title)})", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun placeholderPhase(label: String): Int = when (label) {
    "Editor", "Find", "Replace", "Save", "Save As", "Preview", "Project Filename Search",
    "Project Content Search", "Create file", "Create folder", "Import files", "Locate Current File" -> 4
    "Terminal", "New session", "Close session", "Close all sessions", "Rename session", "Zoom",
    "Open Terminal" -> 3
    "Browser", "New tab", "Back", "Forward", "Reload", "Downloads", "Developer tools", "Viewport",
    "Open Browser or Preview" -> 5
    "Git", "Open Git", "Status", "Changed files", "Stage", "Unstage", "Commit", "History", "Branches", "Fetch", "Pull", "Push", "Global Git Settings", "Manage Credentials", "Provider management", "Credential-store configuration", "SSH and known-host configuration", "Global ignore rules" -> 6
    "Extensions", "Install extension", "Update", "Remove", "Permissions", "Extension permissions" -> 8
    else -> 8
}

private fun surfaceTitle(surface: Surface): String = when (surface) {
    Surface.HOME -> "Home"
    Surface.PROJECTS -> "Projects"
    Surface.PROJECT_DETAILS -> "Project Details"
    Surface.EDITOR -> "Editor"
    Surface.TERMINAL -> "Terminal"
    Surface.BROWSER -> "Browser"
    Surface.GIT -> "Git"
    Surface.EXTENSIONS -> "Extensions"
    Surface.SETTINGS -> "Settings"
}

private fun relativeOpened(lastOpenedAt: java.time.Instant?): String {
    if (lastOpenedAt == null) return "Never opened"
    val minutes = java.time.Duration.between(lastOpenedAt, java.time.Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "Opened just now"
        minutes < 60 -> "Opened ${minutes}m ago"
        minutes < 1_440 -> "Opened ${minutes / 60}h ago"
        else -> "Opened ${minutes / 1_440}d ago"
    }
}
