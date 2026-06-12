// android-ide/android/java/dev/androidide/ui/screen/ProjectsScreen.kt
//
// Project management screen.
// Shows the list of recently-opened projects and lets the user open a new folder.

package dev.androidide.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.data.model.Project
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.IdeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    uiState: IdeUiState,
    ideViewModel: IdeViewModel,
    onOpenProjectFolder: () -> Unit,
) {
    val colors = LocalIdeColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects", color = colors.textPrimary) },
                actions = {
                    IconButton(onClick = onOpenProjectFolder) {
                        Icon(
                            imageVector        = Icons.Default.Add,
                            contentDescription = "Open folder",
                            tint               = colors.accent,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = colors.surface,
                    titleContentColor      = colors.textPrimary,
                    actionIconContentColor = colors.accent,
                ),
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        if (uiState.recentProjects.isEmpty()) {
            EmptyProjectsState(
                onOpenFolder   = onOpenProjectFolder,
                modifier       = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top    = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.recentProjects, key = { it.uri }) { project ->
                    ProjectItem(
                        project    = project,
                        isActive   = project.uri == uiState.projectRootUri,
                        onClick    = { ideViewModel.openProject(project.uri) },
                        onRemove   = { ideViewModel.removeProjectFromRegistry(project.uri) },
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick  = onOpenProjectFolder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open another folder…")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectItem(
    project: Project,
    isActive: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalIdeColors.current
    val bg     = if (isActive) colors.activeHighlight else colors.background

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.Folder,
            contentDescription = null,
            tint               = colors.accentLight,
            modifier           = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = project.name,
                style    = MaterialTheme.typography.bodyLarge,
                color    = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text  = "Last opened ${formatDate(project.lastOpenedMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        if (isActive) {
            Spacer(Modifier.width(8.dp))
            Text(
                text  = "OPEN",
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onRemove) {
            Icon(
                imageVector        = Icons.Default.Delete,
                contentDescription = "Remove from list",
                tint               = colors.textDisabled,
                modifier           = Modifier.size(18.dp),
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = colors.separator)
}

@Composable
private fun EmptyProjectsState(
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier            = modifier.padding(32.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.Folder,
            contentDescription = null,
            tint               = colors.textDisabled,
            modifier           = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text  = "No projects yet",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Open a folder to start coding",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenFolder) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open Folder")
        }
    }
}

private fun formatDate(timestampMs: Long): String = runCatching {
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
}.getOrElse { "—" }
