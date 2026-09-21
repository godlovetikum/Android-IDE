// AppShell keeps application navigation and visible domain boundaries in one place.
// It does not own project files or the runtime state exposed by those domains.
package dev.android.ide.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.Surface as AppSurface

@Composable
fun AppShell(viewModel: AppShellViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    var sidebarVisible by rememberSaveable { mutableStateOf(true) }

    fun show(surface: AppSurface) {
        viewModel.navigate(surface)
        sidebarVisible = false
    }

    val sidebar: @Composable (Modifier) -> Unit = { modifier ->
        AppSidebar(
            modifier = modifier,
            current = state.surface,
            onNavigate = ::show,
        )
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        AppContent(
            modifier = modifier,
            state = state,
            onNavigate = ::show,
            onOpenProject = viewModel::openProject,
        )
    }

    if (isWide) {
        Row(Modifier.fillMaxSize()) {
            sidebar(Modifier.width(240.dp).fillMaxHeight())
            Divider(modifier = Modifier.fillMaxHeight())
            content(Modifier.weight(1f).fillMaxHeight())
        }
    } else if (sidebarVisible) {
        sidebar(Modifier.fillMaxSize())
    } else {
        Column(Modifier.fillMaxSize()) {
            Button(
                onClick = { sidebarVisible = true },
                modifier = Modifier.padding(8.dp),
            ) { Text("Navigation") }
            content(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun AppSidebar(
    modifier: Modifier,
    current: AppSurface,
    onNavigate: (AppSurface) -> Unit,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Android IDE", style = MaterialTheme.typography.titleLarge)
        Text("Application shell", style = MaterialTheme.typography.labelMedium)
        Divider()
        listOf(
            AppSurface.HOME to "Home",
            AppSurface.PROJECTS to "Projects",
            AppSurface.EDITOR to "Editor",
            AppSurface.TERMINAL to "Terminal",
            AppSurface.BROWSER to "Browser",
            AppSurface.GIT to "Git",
            AppSurface.EXTENSIONS to "Extensions",
            AppSurface.SETTINGS to "Settings",
        ).forEach { (surface, label) ->
            Text(
                text = if (surface == current) "• $label" else label,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(surface) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AppContent(
    modifier: Modifier,
    state: AppShellState,
    onNavigate: (AppSurface) -> Unit,
    onOpenProject: (String) -> Unit,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state.surface) {
            AppSurface.HOME -> {
                Text("Home", style = MaterialTheme.typography.headlineMedium)
                Text("A navigation and orientation surface. Workspace state is not summarized here.")
                Button(onClick = { onNavigate(AppSurface.PROJECTS) }) { Text("Open Projects") }
                Button(onClick = { viewModel.exit(onExit) }) { Text("Exit") }
            }
            AppSurface.PROJECTS -> {
                Text("Projects", style = MaterialTheme.typography.headlineMedium)
                if (state.projects.isEmpty()) Text("No registered projects")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectRow(project, onOpenProject)
                    }
                }
            }
            AppSurface.PROJECT_DETAILS -> {
                Text("Project Details", style = MaterialTheme.typography.headlineMedium)
                val selected = state.projects.firstOrNull { it.id == state.selectedProjectId }
                Text(selected?.name ?: "No project selected")
                Text(selected?.location?.displayLabel ?: "Location unavailable")
            }
            else -> {
                Text(surfaceTitle(state.surface), style = MaterialTheme.typography.headlineMedium)
                Text("This domain is unavailable until its owning service is implemented.")
            }
        }
        if (state.surface != AppSurface.HOME) {
            Button(onClick = { viewModel.back() }) { Text("Back") }
        }
        state.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.restoring) Text("Restoring project state…")
    }
}

@Composable
private fun ProjectRow(project: ProjectIdentity, onOpenProject: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenProject(project.id) }
            .padding(vertical = 8.dp),
    ) {
        Text(project.name, style = MaterialTheme.typography.titleMedium)
        Text(project.location.displayLabel, style = MaterialTheme.typography.bodySmall)
        Text(project.location.capabilityState.name, style = MaterialTheme.typography.labelSmall)
    }
}

private fun surfaceTitle(surface: AppSurface): String = when (surface) {
    AppSurface.HOME -> "Home"
    AppSurface.PROJECTS -> "Projects"
    AppSurface.PROJECT_DETAILS -> "Project Details"
    AppSurface.EDITOR -> "Editor"
    AppSurface.TERMINAL -> "Terminal"
    AppSurface.BROWSER -> "Browser"
    AppSurface.GIT -> "Git"
    AppSurface.EXTENSIONS -> "Extensions"
    AppSurface.SETTINGS -> "Settings"
}
