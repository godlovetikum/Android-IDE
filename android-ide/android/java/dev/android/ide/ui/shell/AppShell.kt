package dev.android.ide.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import dev.android.ide.viewmodel.IdeViewModel
import kotlinx.coroutines.launch

@Composable
fun AppShell(
    viewModel: AppShellViewModel,
    ideViewModel: IdeViewModel,
    onExit: () -> Unit,
    onCreateProject: () -> Unit,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onCloneGit: () -> Unit,
    onExportProject: (String) -> Unit,
    onExportProjects: (Set<String>) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onRelocateProject: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var phaseFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSection by rememberSaveable { mutableStateOf<String?>(null) }
    var editorPanelRequest by rememberSaveable { mutableStateOf(0L) }
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val surfaceStateHolder = rememberSaveableStateHolder()

    BackHandler(
        enabled = drawerState.isOpen || phaseFeedback != null ||
            state.exitConfirmationVisible || state.navigationStack.size > 1,
    ) {
        when {
            state.exitConfirmationVisible -> viewModel.dismissExitConfirmation()
            phaseFeedback != null -> phaseFeedback = null
            drawerState.isOpen -> { moreOpen = false; coroutineScope.launch { drawerState.close() } }
            !viewModel.back() -> viewModel.requestExitConfirmation()
        }
    }

    fun navigate(surface: Surface) {
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
            onOpenNavigation = { coroutineScope.launch { drawerState.open() } },
            onFeedback = { phaseFeedback = it },
            settingsSection = settingsSection,
            onSettingsSectionConsumed = { settingsSection = null },
            editorPanelRequest = editorPanelRequest,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
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
                    onCreateProject = { onCreateProject(); moreOpen = false; coroutineScope.launch { drawerState.close() } },
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
    phaseFeedback?.let { action ->
        AlertDialog(
            onDismissRequest = { phaseFeedback = null },
            title = { Text(action) },
            text = {
                Text("Coming soon (phase ${placeholderPhase(action)})")
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
    onCreateProject: () -> Unit,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onCloneGit: () -> Unit,
    onFeedback: (String) -> Unit,
    onSettingsSection: (String) -> Unit,
    onDismissDrawer: () -> Unit,
    onOpenEditorPanel: () -> Unit,
) {
    val terminalActions = listOf("New session", "Close session", "Close all sessions", "Rename session", "Zoom")
    val extensionActions = listOf("Install extension", "Update extension", "Remove extension", "Extension permissions")
    LazyColumn(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 120.dp),
    ) {
        item { Text("Quick navigation", style = MaterialTheme.typography.labelLarge) }
        item { NavigationItem(Icons.Default.Home, "Home", state.surface == Surface.HOME) { onNavigate(Surface.HOME) } }
        item { NavigationItem(Icons.Default.Code, "Editor", state.surface == Surface.EDITOR) { onNavigate(Surface.EDITOR) } }
        item { NavigationItem(Icons.Default.Terminal, "Terminal", state.surface == Surface.TERMINAL) { onNavigate(Surface.TERMINAL) } }
        item { NavigationItem(Icons.Default.Language, "Browser", state.surface == Surface.BROWSER) { onNavigate(Surface.BROWSER) } }
        item { NavigationItem(Icons.Default.MergeType, "Git", state.surface == Surface.GIT) { onNavigate(Surface.GIT) } }
        item { NavigationItem(Icons.Default.MoreHoriz, if (moreOpen) "Hide more" else "More", false, onMore) }
        if (moreOpen) {
            item { NavigationItem(Icons.Default.FolderOpen, "Projects", state.surface == Surface.PROJECTS) { onNavigate(Surface.PROJECTS) } }
            item { NavigationItem(Icons.Default.Extension, "Extensions", state.surface == Surface.EXTENSIONS) { onNavigate(Surface.EXTENSIONS) } }
            item { NavigationItem(Icons.Default.Settings, "Settings", state.surface == Surface.SETTINGS) { onNavigate(Surface.SETTINGS) } }
            item { NavigationItem(Icons.Default.Settings, "Credentials and security", false) { onFeedback("Credentials and security settings"); onDismissDrawer() } }
            item { NavigationItem(Icons.Default.Settings, "Display", false) { onSettingsSection("App Theme"); onDismissDrawer() } }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        when (state.surface) {
            Surface.PROJECTS -> {
                item { Text("Project management", style = MaterialTheme.typography.labelLarge) }
                item { TextButton(onClick = { onNavigate(Surface.PROJECTS) }, Modifier.fillMaxWidth()) { Text("Projects") } }
                item { NavigationItem(Icons.Default.Add, "Create blank project", false) { onCreateProject() } }
                item { NavigationItem(Icons.Default.FolderOpen, "Import existing folder", false) { onImportFolder() } }
                item { NavigationItem(Icons.Default.FolderOpen, "Import ZIP archive", false) { onImportZip() } }
                item { NavigationItem(Icons.Default.MergeType, "Clone remote Git repository", false) { onCloneGit() } }
                if (state.projects.isNotEmpty()) {
                    item { Text("Recent projects", style = MaterialTheme.typography.labelLarge) }
                    items(state.projects.take(5), key = { it.id }) { project -> ProjectContextItem(project, onOpenProject) }
                }
            }
            Surface.EDITOR -> {
                item { Text("Editor context", style = MaterialTheme.typography.labelLarge) }
                item { NavigationItem(Icons.Default.FolderOpen, "Project file tree", true) { onOpenEditorPanel() } }
                item { NavigationItem(Icons.Default.Search, "Search filenames", false) { ideViewModel.showFileSearch(); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.Source, "Search project contents", false) { ideViewModel.showContentSearch(); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.FolderOpen, "Locate current file", false) { ideViewModel.revealActiveFile(); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.Settings, "Editor settings", false) { onSettingsSection("Editor"); onDismissDrawer() } }
            }
            Surface.TERMINAL -> ContextActionItems(terminalActions, onFeedback)
            Surface.EXTENSIONS -> ContextActionItems(extensionActions, onFeedback)
            Surface.SETTINGS -> {
                item { Text("Settings groups", style = MaterialTheme.typography.labelLarge) }
                item { NavigationItem(Icons.Default.Settings, "App theme", false) { onSettingsSection("App Theme"); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.Settings, "UI font size", false) { onSettingsSection("UI Font Size"); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.Code, "Editor", false) { onSettingsSection("Editor"); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.FolderOpen, "File tree", false) { onSettingsSection("File Tree"); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.FolderOpen, "Project storage", false) { onSettingsSection("Project Storage"); onDismissDrawer() } }
                item { NavigationItem(Icons.Default.Terminal, "Terminal and runtime", false) { onFeedback("Terminal and runtime settings") } }
                item { NavigationItem(Icons.Default.Extension, "Credentials and security", false) { onFeedback("Credentials and security settings") } }
            }
            else -> Unit
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ContextActionItems(
    actions: List<String>,
    onFeedback: (String) -> Unit,
) {
    item { Text("Actions", style = MaterialTheme.typography.labelLarge) }
    actions.forEach { action ->
        item {
            Card(
                onClick = { onFeedback(action) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(action, style = MaterialTheme.typography.titleSmall)
                    Text("Structured placeholder — planned for phase ${placeholderPhase(action)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
private fun SurfaceHost(
    modifier: Modifier,
    state: AppShellState,
    viewModel: AppShellViewModel,
    ideViewModel: IdeViewModel,
    onNavigate: (Surface) -> Unit,
    onCreateProject: () -> Unit,
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
        Surface.HOME -> HomeSurface(onNavigate = onNavigate, onExit = onRequestExit)
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
    val actions = when (title) {
        "Editor" -> listOf("Find", "Replace", "Save", "Save As", "Preview")
        "Terminal" -> listOf("New session", "Close session", "Close all sessions", "Zoom")
        "Browser" -> listOf("New tab", "Back", "Forward", "Reload", "Downloads", "Developer tools", "Viewport", "More")
        "Git" -> listOf("Status", "Changed files", "Stage", "Unstage", "Commit", "History", "Branches", "Fetch", "Pull", "Push", "Global Git Settings", "Manage Credentials", "Provider management", "Credential-store configuration", "SSH and known-host configuration", "Global ignore rules")
        "Extensions" -> listOf("Install extension", "Update", "Remove", "Permissions", "More")
        "Settings" -> listOf("Application and display", "Editor", "Terminal and runtime", "Browser", "Credentials and security", "Storage and permissions", "More")
        else -> emptyList()
    }
    MaterialSurface(
        modifier = modifier.padding(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text("Coming soon (phase ${placeholderPhase(title)})", style = MaterialTheme.typography.titleMedium)
            actions.forEach { action ->
                TextButton(onClick = { onFeedback(action) }, Modifier.fillMaxWidth()) { Text(action) }
            }
        }
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
