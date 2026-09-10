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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.data.model.Project
import dev.androidide.data.model.ProjectDetails
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.IdeUiState
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import kotlin.math.abs

private enum class ProjectSort {
    RECENT,
    NAME_ASC,
    NAME_DESC,
    MODIFIED,
    CREATED,
    SIZE,
    FILE_COUNT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    uiState: IdeUiState,
    ideViewModel: IdeViewModel,
    onOpenProjectFolder: () -> Unit,
    onCreateBlankProject: () -> Unit,
    onExportProject: (String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onMoveProject: (String) -> Unit,
    onNavigationIconClick: (() -> Unit)? = null,
) {
    val colors = LocalIdeColors.current

    // Rename dialog state
    var renamingUri  by remember { mutableStateOf<String?>(null) }
    var renameText   by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProjectSort.RECENT) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ideViewModel.refreshProjectMetadata()
    }

    val visibleProjects = remember(
        uiState.recentProjects,
        uiState.projectDetailsByUri,
        searchQuery,
        sort,
    ) {
        val filtered = uiState.recentProjects.filter {
            val storagePath = uiState.projectDetailsByUri[it.uri]?.storagePath.orEmpty()
            searchQuery.isBlank() ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.uri.contains(searchQuery, ignoreCase = true) ||
                storagePath.contains(searchQuery, ignoreCase = true)
        }
        val details = uiState.projectDetailsByUri
        when (sort) {
            ProjectSort.RECENT -> filtered.sortedWith(
                compareByDescending<Project> { it.lastOpenedMs }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
                    .thenBy { it.uri },
            )
            ProjectSort.NAME_ASC -> filtered.sortedWith(
                compareBy<Project> { it.name.lowercase(Locale.getDefault()) }.thenBy { it.uri },
            )
            ProjectSort.NAME_DESC -> filtered.sortedWith(
                compareByDescending<Project> { it.name.lowercase(Locale.getDefault()) }.thenBy { it.uri },
            )
            ProjectSort.MODIFIED -> filtered.sortedWith(
                compareByDescending<Project> { details[it.uri]?.lastModifiedTimeMs ?: 0L }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
                    .thenBy { it.uri },
            )
            ProjectSort.CREATED -> filtered.sortedWith(
                compareByDescending<Project> { details[it.uri]?.creationTimeMs ?: it.createdMs }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
                    .thenBy { it.uri },
            )
            ProjectSort.SIZE -> filtered.sortedWith(
                compareByDescending<Project> { details[it.uri]?.totalBytes ?: 0L }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
                    .thenBy { it.uri },
            )
            ProjectSort.FILE_COUNT -> filtered.sortedWith(
                compareByDescending<Project> { details[it.uri]?.fileCount ?: 0 }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
                    .thenBy { it.uri },
            )
        }
    }

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
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            imageVector        = Icons.Default.Search,
                            contentDescription = "Search projects",
                            tint               = if (searchVisible) colors.accent else colors.textSecondary,
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                imageVector        = Icons.Default.Sort,
                                contentDescription = "Sort projects",
                                tint               = colors.textSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Recently opened") },
                                onClick = { sort = ProjectSort.RECENT; sortMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Name A–Z") },
                                onClick = { sort = ProjectSort.NAME_ASC; sortMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Name Z–A") },
                                onClick = { sort = ProjectSort.NAME_DESC; sortMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Last modified") },
                                onClick = { sort = ProjectSort.MODIFIED; sortMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Creation date") },
                                onClick = { sort = ProjectSort.CREATED; sortMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Size") },
                                onClick = { sort = ProjectSort.SIZE; sortMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("File count") },
                                onClick = { sort = ProjectSort.FILE_COUNT; sortMenuOpen = false },
                            )
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (searchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text("Search projects") },
                    placeholder = { Text("Name or storage path") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (uiState.recentProjects.isEmpty()) {
                EmptyProjectsState(
                    onOpenFolder = onOpenProjectFolder,
                    onCreateBlankProject = onCreateBlankProject,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (visibleProjects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No projects match \"$searchQuery\"", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleProjects, key = { it.uri }) { project ->
                        ProjectItem(
                            project = project,
                            details = uiState.projectDetailsByUri[project.uri],
                            isActive = project.uri == uiState.projectRootUri,
                            onClick = { ideViewModel.openProject(project.uri) },
                            onDetails = { ideViewModel.showProjectDetails(project.uri) },
                            onRename = {
                                renamingUri = project.uri
                                renameText = project.name
                            },
                            onDuplicate = { onDuplicateProject(project.uri) },
                            onExport = { onExportProject(project.uri) },
                            onMove = { onMoveProject(project.uri) },
                            onRemove = { ideViewModel.requestRemoveProject(project.uri) },
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = onCreateBlankProject, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("New Project")
                            }
                            OutlinedButton(onClick = onOpenProjectFolder, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open Folder")
                            }
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

    if (uiState.projectDetailsLoading) {
        AlertDialog(
            onDismissRequest = ideViewModel::dismissProjectDetails,
            title = { Text("Project details") },
            text = { CircularProgressIndicator() },
            confirmButton = {
                TextButton(onClick = ideViewModel::dismissProjectDetails) { Text("Cancel") }
            },
        )
    } else {
        uiState.projectDetails?.let { details ->
            AlertDialog(
                onDismissRequest = ideViewModel::dismissProjectDetails,
                title = { Text(details.project.name) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DetailLine("Storage provider", details.storageProvider)
                        DetailLine("Location", details.storagePath)
                        DetailLine("Last opened", formatRelativeDate(details.project.lastOpenedMs))
                        DetailLine("Created", formatDateTime(details.creationTimeMs))
                        DetailLine("Last modified", formatDateTime(details.lastModifiedTimeMs))
                        DetailLine("Files", details.fileCount.toString())
                        DetailLine("Folders", details.folderCount.toString())
                        DetailLine("Size", formatBytes(details.totalBytes))
                        Text(
                            "Source languages",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val languageTotal = details.languageBytes.values.sum()
                        if (details.languageBytes.isEmpty()) {
                            Text("No source files detected", style = MaterialTheme.typography.bodySmall)
                        } else {
                            details.languageBytes.entries
                                .sortedByDescending { it.value }
                                .forEach { (language, bytes) ->
                                    val percentage = if (languageTotal == 0L) {
                                        0
                                    } else {
                                        ((bytes * 100L) / languageTotal).toInt()
                                    }
                                    DetailLine(
                                        language,
                                        "$percentage% • ${formatBytes(bytes)}",
                                    )
                                }
                        }
                        Text("Git", style = MaterialTheme.typography.titleSmall)
                        details.git?.let { git ->
                            DetailLine("Branch", git.currentBranch ?: "Detached HEAD")
                            DetailLine("HEAD", git.headCommit?.take(12) ?: "Unavailable")
                            DetailLine(
                                "Branches",
                                if (git.branches.isEmpty()) "None detected" else git.branches.joinToString(", "),
                            )
                            if (git.remotes.isEmpty()) {
                                DetailLine("Remotes", "None configured")
                            } else {
                                git.remotes.forEach { remote ->
                                    DetailLine("Remote ${remote.name}", remote.url)
                                }
                            }
                            DetailLine("Latest commit", git.latestCommitMessage ?: "Unavailable")
                            git.latestCommitTimeMs?.let {
                                DetailLine("Commit time", formatDateTime(it))
                            }
                        } ?: Text("Not a Git repository", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = ideViewModel::dismissProjectDetails) { Text("Close") }
                },
            )
        }
    }
}

@Composable
private fun ProjectItem(
    project: Project,
    details: ProjectDetails?,
    isActive: Boolean,
    onClick: () -> Unit,
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onMove: () -> Unit,
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
                text  = buildString {
                    append("Last opened ${formatRelativeDate(project.lastOpenedMs)}")
                    details?.let {
                        append(" • ${it.fileCount} files • ${formatBytes(it.totalBytes)}")
                    }
                },
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
                    text    = { Text("Details") },
                    onClick = { menuOpen = false; onDetails() },
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
                DropdownMenuItem(
                    text    = { Text("Move storage\u2026") },
                    onClick = { menuOpen = false; onMove() },
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

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatRelativeDate(timestampMs: Long): String {
    val delta = abs(System.currentTimeMillis() - timestampMs)
    return when {
        delta < 60_000L -> "just now"
        delta < 3_600_000L -> "${delta / 60_000L} min ago"
        delta < 86_400_000L -> "${delta / 3_600_000L} hr ago"
        delta < 604_800_000L -> "${delta / 86_400_000L} day(s) ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
    }
}

private fun formatDateTime(timestampMs: Long?): String =
    timestampMs?.takeIf { it > 0L }?.let {
        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(it))
    } ?: "Unavailable"

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    else -> "${bytes / (1024L * 1024L * 1024L)} GB"
}
