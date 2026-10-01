package dev.android.ide.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.android.ide.app.AppShellState
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.contracts.Surface

/** CLI-first Git entry point; project-scoped actions start in the selected project directory. */
@Composable
fun GitSurface(
    state: AppShellState,
    viewModel: AppShellViewModel,
    onOpenNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = state.projects.firstOrNull { it.id == state.selectedProjectId }
    val summary = project?.let { state.projectSummaries[it.id] }
    val details = state.projectDetails?.takeIf { it.project.stableLocationId == project?.id }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Git") },
            navigationIcon = { androidx.compose.material3.IconButton(onClick = onOpenNavigation) { Icon(Icons.Default.Menu, "Open sidebar") } },
        )
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (project == null) {
                Text("Select a project to inspect its repository status.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { viewModel.navigate(Surface.TERMINAL) }) {
                    Icon(Icons.Default.Terminal, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Terminal")
                }
            } else {
                Text(project.name, style = MaterialTheme.typography.headlineSmall)
                ProjectGitStatusCard(
                    hasGit = summary?.hasGit,
                    git = details?.git,
                )
                state.terminalFeedback?.takeIf { it.outcome != dev.android.ide.contracts.OperationOutcome.COMPLETE }?.let { report ->
                    Text(report.message, color = dev.android.ide.ui.theme.operationStatusColor(report.outcome), style = MaterialTheme.typography.bodySmall)
                } ?: state.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { viewModel.openTerminalForProject(project.id) }) {
                    Icon(Icons.Default.Terminal, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open project in Terminal")
                }
                Text(
                    "Use the project terminal for Git commands. Built-in staging, commit, branch, fetch, pull, and push controls are not implemented yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
