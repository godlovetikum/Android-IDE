package dev.android.ide.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import dev.android.ide.app.AppShellState
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.CapabilityState
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

@Composable
fun ProjectsSurface(
    state: AppShellState,
    viewModel: AppShellViewModel,
    onCreateProject: (String?) -> Unit,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onCloneGit: () -> Unit,
    onExportProject: (String) -> Unit,
    onExportProjects: (Set<String>) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onRelocateProject: (String) -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun copyPaths(ids: Set<String>) {
        val paths = state.projects.filter { it.id in ids }.mapNotNull { it.location.userVisiblePath ?: it.location.displayLabel }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
            ClipData.newPlainText("Project storage paths", paths.joinToString("\n")),
        )
        viewModel.reportStatus("Copied ${paths.size} project storage path(s)")
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(ProjectsSurfaceSort.NAME) }
    var sortOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var filterMode by rememberSaveable { mutableStateOf(ProjectFilter.ALL) }
    var addActionsExpanded by remember { mutableStateOf(false) }
    val fabScale by animateFloatAsState(if (addActionsExpanded) 1.08f else 1f, label = "project-fab-scale")
    var confirmBatchRemove by remember { mutableStateOf(false) }
    var confirmBatchExport by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var batchDeleteCode by remember { mutableStateOf(Random.nextInt(100, 1000).toString()) }
    var enteredBatchDeleteCode by remember { mutableStateOf("") }
    val visibleProjects = state.projects
        .filter { it.name.contains(searchQuery, ignoreCase = true) }
        .filter { project ->
            val summary = state.projectSummaries[project.id]
            when (filterMode) {
                ProjectFilter.ALL -> true
                ProjectFilter.GIT -> summary?.hasGit == true
                ProjectFilter.AVAILABLE -> project.location.capabilityState == CapabilityState.SUPPORTED && summary?.status == null
                ProjectFilter.ATTENTION -> project.location.capabilityState != CapabilityState.SUPPORTED || summary?.status != null
            }
        }
        .let { projects ->
            when (sortMode) {
                ProjectsSurfaceSort.NAME -> projects.sortedBy { it.name.lowercase() }
                ProjectsSurfaceSort.LAST_OPENED -> projects.sortedByDescending { it.lastOpenedAt ?: Instant.MIN }
                ProjectsSurfaceSort.REGISTERED -> projects.sortedByDescending { it.registeredAt }
                ProjectsSurfaceSort.FILE_COUNT -> projects.sortedByDescending { state.projectSummaries[it.id]?.fileCount ?: -1 }
                ProjectsSurfaceSort.SIZE -> projects.sortedByDescending { state.projectSummaries[it.id]?.totalBytes ?: -1L }
            }
        }

    BackHandler(enabled = addActionsExpanded) { addActionsExpanded = false }
    Box(modifier.fillMaxSize()) {
    if (addActionsExpanded) {
        Box(Modifier.fillMaxSize().clickable { addActionsExpanded = false })
    }
    Column(Modifier.fillMaxSize().padding(20.dp).padding(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::refreshProjectList, enabled = !state.operationInProgress) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh projects")
            }
            Box {
                IconButton(onClick = { sortOpen = true }) { Icon(Icons.Default.Sort, contentDescription = "Sort projects") }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    ProjectsSurfaceSort.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { sortMode = option; sortOpen = false })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search projects") },
                singleLine = true,
            )
            Box {
                IconButton(onClick = { filterOpen = true }) { Icon(Icons.Default.FilterList, contentDescription = "Filter projects") }
                DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                    ProjectFilter.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { filterMode = option; filterOpen = false })
                    }
                }
            }
        }
        state.registryWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.statusMessage != null && !state.operationInProgress) {
            val statusColor = when (state.operationReport?.outcome) {
                dev.android.ide.contracts.OperationOutcome.COMPLETE -> MaterialTheme.colorScheme.primary
                dev.android.ide.contracts.OperationOutcome.BLOCKED,
                dev.android.ide.contracts.OperationOutcome.FAILED,
                dev.android.ide.contracts.OperationOutcome.PARTIAL,
                dev.android.ide.contracts.OperationOutcome.INTERRUPTED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(state.statusMessage!!, color = statusColor, style = MaterialTheme.typography.bodySmall)
        }
        if (state.selectedProjectIds.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${state.selectedProjectIds.size} selected")
                TextButton(onClick = { confirmBatchRemove = true }) { Text("Remove from Registry") }
                TextButton(onClick = { batchDeleteCode = Random.nextInt(100, 1000).toString(); enteredBatchDeleteCode = ""; confirmBatchDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Permanently Delete") }
                TextButton(onClick = { confirmBatchExport = true }) { Text("Export or Share") }
                TextButton(onClick = { copyPaths(state.selectedProjectIds) }) { Text("Copy Storage Path") }
                TextButton(onClick = viewModel::clearProjectSelection) { Text("Cancel") }
            }
        }
        if (confirmBatchDelete) {
            AlertDialog(
                onDismissRequest = { if (!state.operationInProgress) confirmBatchDelete = false },
                title = { Text("Permanently delete selected projects?") },
                text = {
                    Column {
                        Text("This permanently removes ${state.selectedProjectIds.size} selected project location(s). This action cannot be undone.")
                        Text("Selected projects: ${state.projects.filter { it.id in state.selectedProjectIds }.joinToString { it.name }}")
                        Text("Type $batchDeleteCode to confirm")
                        OutlinedTextField(enteredBatchDeleteCode, { enteredBatchDeleteCode = it }, label = { Text("Confirmation code") }, singleLine = true)
                    }
                },
                confirmButton = { Button(onClick = { viewModel.permanentlyDeleteProjects(state.selectedProjectIds.toList()) }, enabled = enteredBatchDeleteCode == batchDeleteCode && !state.operationInProgress, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { if (state.operationInProgress) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp); Text(if (state.operationInProgress) "Deleting…" else "Delete permanently") } },
                dismissButton = { TextButton(onClick = { confirmBatchDelete = false }, enabled = !state.operationInProgress) { Text("Cancel") } },
            )
        }
        if (confirmBatchRemove) {
            AlertDialog(
                onDismissRequest = { if (!state.operationInProgress) confirmBatchRemove = false },
                title = { Text("Remove selected projects from registry?") },
                text = { Text("${state.selectedProjectIds.size} project record(s) will be removed from Android IDE. User files, Git data, and locations remain unchanged.") },
                confirmButton = { Button(onClick = { viewModel.removeProjectsFromRegistry(state.selectedProjectIds.toList()) }, enabled = !state.operationInProgress) { if (state.operationInProgress) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp); Text(if (state.operationInProgress) "Removing…" else "Remove from Registry") } },
                dismissButton = { TextButton(onClick = { confirmBatchRemove = false }, enabled = !state.operationInProgress) { Text("Cancel") } },
            )
        }
        if (confirmBatchExport) {
            val selectedNames = state.projects.filter { it.id in state.selectedProjectIds }.joinToString { it.name }
            AlertDialog(
                onDismissRequest = { confirmBatchExport = false },
                title = { Text("Export selected projects as ZIP") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Selected projects: $selectedNames\n\nOne ZIP archive per project will be created in a destination you choose. Source projects remain unchanged.")
                        state.operationReport?.let { report ->
                            Text(report.message, color = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        if (state.operationInProgress) Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            Text("Exporting…")
                        }
                    }
                },
                confirmButton = { Button(onClick = { onExportProjects(state.selectedProjectIds) }, enabled = !state.operationInProgress) { Text("Choose export location") } },
                dismissButton = { TextButton(onClick = { confirmBatchExport = false }, enabled = !state.operationInProgress) { Text(if (state.operationInProgress) "Please wait" else "Cancel") } },
            )
        }
        if (visibleProjects.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (searchQuery.isBlank()) "No registered projects" else "No matching projects")
                if (searchQuery.isNotBlank()) {
                    TextButton(onClick = { onCreateProject(searchQuery) }) {
                        Text("Create a new project named ${searchQuery.trim()}")
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleProjects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        summary = state.projectSummaries[project.id],
                        onOpen = viewModel::openProject,
                        onDetails = viewModel::showProjectDetails,
                        onSelect = viewModel::selectProject,
                        onFeedback = onFeedback,
                        onRefresh = viewModel::refreshProjectList,
                        onRemove = viewModel::removeSelectedProject,
                        onDelete = viewModel::permanentlyDeleteSelectedProject,
                        onRename = viewModel::renameSelectedProject,
                        selected = project.id in state.selectedProjectIds,
                        onToggleSelection = viewModel::toggleProjectSelection,
                        onCopyPath = { copyPaths(setOf(it)) },
                        onExport = onExportProject,
                        onDuplicate = onDuplicateProject,
                        onRelocate = onRelocateProject,
                        operationInProgress = state.operationInProgress,
                    )
                }
            }
        }
    }
    if (state.operationInProgress) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)).clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text("Working…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    FloatingActionButton(
        onClick = { if (!state.operationInProgress) addActionsExpanded = !addActionsExpanded },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).graphicsLayer { scaleX = fabScale; scaleY = fabScale },
    ) { androidx.compose.material3.Icon(if (addActionsExpanded) Icons.Default.Close else Icons.Default.Add, contentDescription = if (addActionsExpanded) "Close project actions" else "Add project") }
    if (addActionsExpanded) {
        Card(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 84.dp).animateContentSize(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            ProjectAcquisitionAction(Icons.Default.Code, "Create blank project") { addActionsExpanded = false; onCreateProject(null) }
            ProjectAcquisitionAction(Icons.Default.FolderOpen, "Import existing folder") { addActionsExpanded = false; onImportFolder() }
            ProjectAcquisitionAction(Icons.Default.Archive, "Import ZIP archive") { addActionsExpanded = false; onImportZip() }
            ProjectAcquisitionAction(Icons.Default.MergeType, "Clone remote Git repository") { addActionsExpanded = false; onCloneGit() }
        }
        }
    }
    }
}

@Composable
private fun ProjectAcquisitionAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private enum class ProjectsSurfaceSort(val label: String) {
    NAME("Name"),
    LAST_OPENED("Last opened"),
    REGISTERED("Registration time"),
    FILE_COUNT("File count"),
    SIZE("Project size"),
}

private enum class ProjectFilter(val label: String) {
    ALL("All projects"),
    GIT("Git projects"),
    AVAILABLE("Available"),
    ATTENTION("Needs attention"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    project: ProjectIdentity,
    summary: dev.android.ide.app.ProjectSummary?,
    onOpen: (String) -> Unit,
    onDetails: (String) -> Unit,
    onSelect: (String) -> Unit,
    onFeedback: (String) -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    selected: Boolean,
    onToggleSelection: (String) -> Unit,
    onCopyPath: (String) -> Unit,
    onExport: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onRelocate: (String) -> Unit,
    operationInProgress: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteCode by remember { mutableStateOf(Random.nextInt(100, 1000).toString()) }
    var enteredDeleteCode by remember { mutableStateOf("") }
    var renameVisible by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(project.name) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (selected) onToggleSelection(project.id) else onOpen(project.id) },
            onLongClick = { onToggleSelection(project.id) },
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (selected) Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                Text(project.name, style = MaterialTheme.typography.titleLarge)
                Text(if (project.description.isBlank()) "No description" else project.description, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (summary?.hasGit == true) Text("Git", style = MaterialTheme.typography.labelSmall)
                    Text("${summary?.fileCount ?: "Unavailable"} files", style = MaterialTheme.typography.bodySmall)
                    Text(summary?.totalBytes?.let(::formatBytes) ?: "Unavailable", style = MaterialTheme.typography.bodySmall)
                }
                summary?.status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                Text(relativeLastOpened(project.lastOpenedAt), style = MaterialTheme.typography.labelSmall)
            }
            Box {
                IconButton(onClick = { onSelect(project.id); menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Project actions") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Info, null) }, text = { Text("Project details") }, onClick = { menuOpen = false; onDetails(project.id) })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Refresh, null) }, text = { Text("Refresh") }, onClick = { menuOpen = false; onSelect(project.id); onRefresh() })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Edit, null) }, text = { Text("Rename") }, onClick = { menuOpen = false; renameValue = project.name; renameVisible = true })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, text = { Text("Change Location") }, onClick = { menuOpen = false; onRelocate(project.id) })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, text = { Text("Copy & Duplicate") }, onClick = { menuOpen = false; onDuplicate(project.id) })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Share, null) }, text = { Text("Export or Share") }, onClick = { menuOpen = false; onExport(project.id) })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Code, null) }, text = { Text("Copy Storage Path") }, onClick = { menuOpen = false; onCopyPath(project.id) })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.MergeType, null) }, text = { Text("Git Remote Details") }, onClick = { menuOpen = false; onDetails(project.id) })
                    HorizontalDivider()
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Code, null) }, text = { Text("Open in Editor") }, onClick = { menuOpen = false; onOpen(project.id) })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.MergeType, null) }, text = { Text("Open Git") }, onClick = { menuOpen = false; onFeedback("Open Git") })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Terminal, null) }, text = { Text("Open Terminal") }, onClick = { menuOpen = false; onFeedback("Open Terminal") })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, text = { Text("Open Browser or Preview") }, onClick = { menuOpen = false; onFeedback("Open Browser or Preview") })
                    HorizontalDivider()
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Delete, null) }, text = { Text("Remove from Registry", color = MaterialTheme.colorScheme.secondary) }, onClick = { menuOpen = false; onSelect(project.id); confirmRemove = true })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, text = { Text("Permanently Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onSelect(project.id); deleteCode = Random.nextInt(100, 1000).toString(); enteredDeleteCode = ""; confirmDelete = true })
                }
            }
        }
        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("Remove project from registry?") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${project.name} will be removed from Android IDE, but its files, Git data, and location will remain unchanged.\n\nLocation: ${humanReadableStorageLocation(project.location.userVisiblePath ?: project.location.displayLabel)}") }; if (operationInProgress) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp); Text("Removing…") } },
                confirmButton = { Button(onClick = onRemove, enabled = !operationInProgress) { Text(if (operationInProgress) "Removing…" else "Remove from Registry") } },
                dismissButton = { TextButton(onClick = { confirmRemove = false }, enabled = !operationInProgress) { Text(if (operationInProgress) "Please wait" else "Cancel") } },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Permanently delete ${project.name}?") },
                text = {
                    Column {
                        Text("This permanently removes the project data from its selected storage location. This action cannot be undone.")
                        Text("Project: ${project.name}")
                        Text("Location: ${humanReadableStorageLocation(project.location.userVisiblePath ?: project.location.displayLabel)}")
                        Text("Type $deleteCode to confirm")
                        OutlinedTextField(enteredDeleteCode, { enteredDeleteCode = it }, label = { Text("Confirmation code") }, singleLine = true, enabled = !operationInProgress)
                        if (operationInProgress) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp); Text("Deleting…") }
                    }
                },
                confirmButton = { Button(onClick = onDelete, enabled = enteredDeleteCode == deleteCode && !operationInProgress, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(if (operationInProgress) "Deleting…" else "Delete permanently") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !operationInProgress) { Text(if (operationInProgress) "Please wait" else "Cancel") } },
            )
        }
        if (renameVisible) {
            AlertDialog(
                onDismissRequest = { renameVisible = false },
                title = { Text("Rename project") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(renameValue, { renameValue = it }, label = { Text("Project name") }, singleLine = true, enabled = !operationInProgress); if (operationInProgress) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp); Text("Renaming…") } } },
                confirmButton = {
                    Button(onClick = { onSelect(project.id); onRename(renameValue) }, enabled = renameValue.isNotBlank() && !operationInProgress) { Text(if (operationInProgress) "Renaming…" else "Rename") }
                },
                dismissButton = { TextButton(onClick = { renameVisible = false }, enabled = !operationInProgress) { Text(if (operationInProgress) "Please wait" else "Cancel") } },
            )
        }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024.0 && index < units.lastIndex) { value /= 1024.0; index++ }
    return "%.1f %s".format(value, units[index])
}

private fun relativeLastOpened(lastOpenedAt: Instant?): String {
    if (lastOpenedAt == null) return "Never"
    val seconds = Duration.between(lastOpenedAt, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        seconds < 2_592_000 -> "${seconds / 86_400}d ago"
        seconds < 31_536_000 -> "${seconds / 2_592_000}mo ago"
        else -> "${seconds / 31_536_000}y ago"
    }
}
