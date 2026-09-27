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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.android.ide.ui.screen.ProjectsSurface

@Composable
fun AppShell(
    viewModel: AppShellViewModel,
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
    var navigationOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var phaseFeedback by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(
        enabled = navigationOpen || phaseFeedback != null ||
            state.exitConfirmationVisible || state.navigationStack.size > 1,
    ) {
        when {
            state.exitConfirmationVisible -> viewModel.dismissExitConfirmation()
            phaseFeedback != null -> phaseFeedback = null
            navigationOpen && moreOpen -> moreOpen = false
            navigationOpen -> { navigationOpen = false; moreOpen = false }
            !viewModel.back() -> viewModel.requestExitConfirmation()
        }
    }

    fun navigate(surface: Surface) {
        viewModel.navigate(surface)
        navigationOpen = false
        moreOpen = false
    }

    val navigation: @Composable (Modifier) -> Unit = { modifier ->
        ContextualNavigation(
            modifier = modifier,
            state = state,
            moreOpen = moreOpen,
            onMore = { moreOpen = !moreOpen },
            onNavigate = ::navigate,
            onOpenProject = { viewModel.openProject(it); navigationOpen = false },
            onCreateProject = { onCreateProject(); navigationOpen = false; moreOpen = false },
            onImportZip = { onImportZip(); navigationOpen = false; moreOpen = false },
            onCloneGit = { onCloneGit(); navigationOpen = false; moreOpen = false },
            onFeedback = { phaseFeedback = it },
        )
    }
    val surface: @Composable (Modifier) -> Unit = { modifier ->
        SurfaceHost(
            modifier = modifier,
            state = state,
            viewModel = viewModel,
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
            onFeedback = { phaseFeedback = it },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(surfaceTitle(state.surface)) },
                navigationIcon = {
                        IconButton(onClick = {
                            navigationOpen = !navigationOpen
                            if (!navigationOpen) moreOpen = false
                        }) {
                        Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                    }
                },
            )
        },
    ) { padding ->
        if (navigationOpen) {
            navigation(Modifier.padding(padding).fillMaxSize())
        } else {
            surface(Modifier.padding(padding).fillMaxSize())
        }
    }

    if (state.exitConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExitConfirmation,
            title = { Text("Exit Android IDE?") },
            text = { Text("You are at Home. Exit the application?") },
            confirmButton = {
                Button(onClick = { viewModel.dismissExitConfirmation(); viewModel.exit(onExit) }) { Text("Exit") }
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
    moreOpen: Boolean,
    onMore: () -> Unit,
    onNavigate: (Surface) -> Unit,
    onOpenProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    onImportZip: () -> Unit,
    onCloneGit: () -> Unit,
    onFeedback: (String) -> Unit,
) {
    val editorActions = listOf("Project Filename Search", "Project Content Search", "Create file", "Create folder", "Import files", "Locate Current File")
    val terminalActions = listOf("New session", "Close session", "Close all sessions", "Rename session", "Zoom")
    val extensionActions = listOf("Install extension", "Update extension", "Remove extension", "Extension permissions")
    val settingGroups = listOf("Application and display", "Editor", "Terminal and runtime", "Browser", "Credentials and security", "Storage and permissions")
    LazyColumn(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 120.dp),
    ) {
        item { Text("Quick navigation", style = MaterialTheme.typography.labelLarge) }
        item { NavigationItem("Home", state.surface == Surface.HOME) { onNavigate(Surface.HOME) } }
        item { NavigationItem("Editor", state.surface == Surface.EDITOR) { onNavigate(Surface.EDITOR) } }
        item { NavigationItem("Terminal", state.surface == Surface.TERMINAL) { onNavigate(Surface.TERMINAL) } }
        item { NavigationItem("Browser", state.surface == Surface.BROWSER) { onNavigate(Surface.BROWSER) } }
        item { NavigationItem("Git", state.surface == Surface.GIT) { onNavigate(Surface.GIT) } }
        item { NavigationItem(if (moreOpen) "Hide more" else "More", false, onMore) }
        if (moreOpen) {
            item { NavigationItem("Projects", state.surface == Surface.PROJECTS) { onNavigate(Surface.PROJECTS) } }
            item { NavigationItem("Extensions", state.surface == Surface.EXTENSIONS) { onNavigate(Surface.EXTENSIONS) } }
            item { NavigationItem("Settings", state.surface == Surface.SETTINGS) { onNavigate(Surface.SETTINGS) } }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        when (state.surface) {
            Surface.PROJECTS -> {
                item { Text("Project management", style = MaterialTheme.typography.labelLarge) }
                item { TextButton(onClick = { onNavigate(Surface.PROJECTS) }, Modifier.fillMaxWidth()) { Text("Projects") } }
                if (state.projects.isNotEmpty()) {
                    item { Text("Recent projects", style = MaterialTheme.typography.labelLarge) }
                    items(state.projects.take(5), key = { it.id }) { project -> ProjectContextItem(project, onOpenProject) }
                }
            }
            Surface.EDITOR -> ContextActionItems(editorActions, onFeedback)
            Surface.TERMINAL -> ContextActionItems(terminalActions, onFeedback)
            Surface.EXTENSIONS -> ContextActionItems(extensionActions, onFeedback)
            Surface.SETTINGS -> ContextActionItems(settingGroups, onFeedback)
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
        item { TextButton(onClick = { onFeedback(action) }, Modifier.fillMaxWidth()) { Text(action) } }
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
private fun NavigationItem(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(if (selected) "●  $label" else label)
    }
}

@Composable
private fun SurfaceHost(
    modifier: Modifier,
    state: AppShellState,
    viewModel: AppShellViewModel,
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
    onFeedback: (String) -> Unit,
) {
    when (state.surface) {
        Surface.HOME -> HomeSurface(onNavigate = onNavigate, onExit = onRequestExit)
        Surface.PROJECTS -> ProjectsSurface(state, viewModel, onCreateProject, onImportFolder, onImportZip, onCloneGit, onExportProject, onExportProjects, onDuplicateProject, onRelocateProject, onFeedback, modifier)
        Surface.PROJECT_DETAILS -> ProjectDetailsSurface(state, viewModel, onExportProject, onDuplicateProject, onRelocateProject, modifier)
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
