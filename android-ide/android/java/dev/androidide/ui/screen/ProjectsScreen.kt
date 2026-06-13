// android-ide/android/java/dev/androidide/ui/screen/ProjectsScreen.kt
//
// Project management screen.
// Lists recent projects with per-project actions and provides buttons to
// open an existing folder or create a new blank project.

package dev.androidide.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onCreateBlankProject: () -> Unit,
    onNavigationIconClick: (() -> Unit)? = null,
) {
    val colors = LocalIdeColors.current

    // Rename dialog state
    var renamingUri  by remember { mutableStateOf<String?>(null) }
    var renameText   by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects", color = colors.textPrimary) },
                navigationIcon = {
                    if (onNavigationIconClick != null) {
                        IconButton(onClick = onNavigationIconClick) {
                            Icon(
                                imageVector        = Icons.Default.Menu,
                                contentDescription = "Open sidebar",
                                tint               = colors.accent,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onCreateBlankProject) {
                        Icon(
                            imageVector        = Icons.Default.CreateNewFolder,
                            contentDescription = "New blank project",
                            tint               = colors.accent,
                        )
                    }
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
                onOpenFolder        = onOpenProjectFolder,
                onCreateBlankProject = onCreateBlankProject,
                modifier            = Modifier.fillMaxSize().padding(innerPadding),
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
                        project     = project,
                        isActive    = project.uri == uiState.projectRootUri,
                        onClick     = { ideViewModel.openProject(project.uri) },
                        onRename    = { renamingUri = project.uri; renameText = project.name },
                        onDuplicate = { ideViewModel.duplicateProject(project.uri) },
                        onExport    = { ideViewModel.exportProject() },
                        onRemove    = { ideViewModel.removeProjectFromRegistry(project.uri) },
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick  = onCreateBlankProject,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("New Project")
                        }
                        OutlinedButton(
                            onClick  = onOpenProjectFolder,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Folder")
                        }
                    }
                }
            }
        }
    }

    // Rename dialog
    if (renamingUri != null) {
        AlertDialog(
            onDismissRequest = { renamingUri = null },
            title   = { Text("Rename Project") },
            text    = {
                OutlinedTextField(
                    value         = renameText,
                    onValueChange = { renameText = it },
                    label         = { Text("Project name") },
                    singleLine    = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick  = {
                        ideViewModel.renameProjectInRegistry(renamingUri!!, renameText.trim())
                        renamingUri = null
                    },
                    enabled  = renameText.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renamingUri = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectItem(
    project: Project,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors   = LocalIdeColors.current
    val bg       = if (isActive) colors.activeHighlight else colors.background
    var menuOpen by remember { mutableStateOf(false) }

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

        // ••• overflow menu
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector        = Icons.Default.MoreVert,
                    contentDescription = "Project options",
                    tint               = colors.textDisabled,
                    modifier           = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded         = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text    = { Text("Open") },
                    onClick = { menuOpen = false; onClick() },
                )
                DropdownMenuItem(
                    text    = { Text("Rename\u2026") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text    = { Text("Duplicate") },
                    onClick = { menuOpen = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text    = { Text("Export\u2026") },
                    onClick = { menuOpen = false; onExport() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text    = { Text("Remove From Registry") },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
    HorizontalDivider(thickness = 1.dp, color = colors.separator)
}

@Composable
private fun EmptyProjectsState(
    onOpenFolder: () -> Unit,
    onCreateBlankProject: () -> Unit,
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
            text  = "Open an existing folder or create a new project",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreateBlankProject) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New Blank Project")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenFolder) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open Folder")
        }
    }
}

private fun formatDate(timestampMs: Long): String = runCatching {
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
}.getOrElse { "\u2014" }
