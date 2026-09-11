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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    onCreateBlankProject: (String?) -> Unit,
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
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        ideViewModel.refreshProjectMetadata()
    }
    LaunchedEffect(searchVisible) {
        if (searchVisible) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
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
                    if (uiState.recentProjects.isNotEmpty()) {
                        IconButton(onClick = { onCreateBlankProject(null) }) {
                            Icon(Icons.Default.CreateNewFolder, "New blank project", tint = colors.accent)
                        }
                        IconButton(onClick = onOpenProjectFolder) {
                            Icon(Icons.Default.Add, "Open folder", tint = colors.accent)
                        }
                    }
                    if (uiState.recentProjects.isNotEmpty()) IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            imageVector        = Icons.Default.Search,
                            contentDescription = "Search projects",
                            tint               = if (searchVisible) colors.accent else colors.textSecondary,
                        )
                    }
                    if (uiState.recentProjects.isNotEmpty()) Box {
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
                            Text(
                                text = "Sort as",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            HorizontalDivider()
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
            if (uiState.projectMetadataLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (searchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text("Search projects") },
                    placeholder = { Text("Name or storage path") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = if (visibleProjects.isEmpty()) {
                            "No projects found"
                        } else {
                            "${visibleProjects.size} project${if (visibleProjects.size == 1) "" else "s"} found"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (uiState.recentProjects.isEmpty()) {
                EmptyProjectsState(
                    onOpenFolder = onOpenProjectFolder,
                    onCreateBlankProject = { onCreateBlankProject(null) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (visibleProjects.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No projects found", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                        "No project matches \"$searchQuery\".",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = { onCreateBlankProject(searchQuery.trim()) },
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create \"${searchQuery.trim()}\"")
                    }
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
                            OutlinedButton(onClick = { onCreateBlankProject(null) }, modifier = Modifier.weight(1f)) {
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
            title   = { Text("Rename Display Name") },
            text    = {
                OutlinedTextField(
                    value         = renameText,
                    onValueChange = { renameText = it },
                    label         = { Text("Display name") },
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
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renamingUri = null }) { Text("Cancel") } },
        )
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
                text  = details?.storagePath ?: project.uri,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            details?.let {
                Text(
                    text = "Size: ${formatBytes(it.totalBytes)}   Files: ${it.fileCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
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
        Column(horizontalAlignment = Alignment.End) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "Project options", tint = colors.textDisabled, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded         = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                DropdownMenuItem(
                    text    = { Text("Details") },
                    onClick = { menuOpen = false; onDetails() },
                )
                DropdownMenuItem(
                    text    = { Text("Edit display name\u2026") },
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
            Text(
                text = "Last opened ${formatRelativeDate(project.lastOpenedMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
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
