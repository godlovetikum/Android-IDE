package dev.android.ide.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    onCreateProject: () -> Unit,
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
    var addProjectOpen by remember { mutableStateOf(false) }
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

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(20.dp).padding(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        Text("Your projects")
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search projects") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sort: ${sortMode.label}")
            TextButton(onClick = { sortOpen = true }) { Text("Change") }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                ProjectsSurfaceSort.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { sortMode = option; sortOpen = false })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Filter: ${filterMode.label}")
            TextButton(onClick = { filterOpen = true }) { Text("Change") }
            DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                ProjectFilter.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { filterMode = option; filterOpen = false })
                }
            }
        }
        TextButton(onClick = viewModel::refreshProjectList) { Text("Refresh") }
        state.registryWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.operationInProgress) Text("Refreshing projects…")
        state.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.selectedProjectIds.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${state.selectedProjectIds.size} selected")
                TextButton(onClick = { confirmBatchRemove = true }) { Text("Remove from Registry") }
                TextButton(onClick = { batchDeleteCode = Random.nextInt(100, 1000).toString(); enteredBatchDeleteCode = ""; confirmBatchDelete = true }) { Text("Permanently Delete") }
                TextButton(onClick = { confirmBatchExport = true }) { Text("Export or Share") }
                TextButton(onClick = { copyPaths(state.selectedProjectIds) }) { Text("Copy Storage Path") }
                TextButton(onClick = viewModel::clearProjectSelection) { Text("Cancel") }
            }
        }
        if (confirmBatchDelete) {
            AlertDialog(
                onDismissRequest = { confirmBatchDelete = false },
                title = { Text("Permanently delete selected projects?") },
                text = {
                    Column {
                        Text("This permanently removes ${state.selectedProjectIds.size} selected project location(s). This action cannot be undone.")
                        Text("Selected projects: ${state.projects.filter { it.id in state.selectedProjectIds }.joinToString { it.name }}")
                        Text("Type $batchDeleteCode to confirm")
                        OutlinedTextField(enteredBatchDeleteCode, { enteredBatchDeleteCode = it }, label = { Text("Confirmation code") }, singleLine = true)
                    }
                },
                confirmButton = { Button(onClick = { confirmBatchDelete = false; viewModel.permanentlyDeleteProjects(state.selectedProjectIds.toList()) }, enabled = enteredBatchDeleteCode == batchDeleteCode) { Text("Delete permanently") } },
                dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text("Cancel") } },
            )
        }
        if (confirmBatchRemove) {
            AlertDialog(
                onDismissRequest = { confirmBatchRemove = false },
                title = { Text("Remove selected projects from registry?") },
                text = { Text("${state.selectedProjectIds.size} project record(s) will be removed from Android IDE. User files, Git data, and locations remain unchanged.") },
                confirmButton = { Button(onClick = { confirmBatchRemove = false; viewModel.removeProjectsFromRegistry(state.selectedProjectIds.toList()) }) { Text("Remove from Registry") } },
                dismissButton = { TextButton(onClick = { confirmBatchRemove = false }) { Text("Cancel") } },
            )
        }
        if (confirmBatchExport) {
            val selectedNames = state.projects.filter { it.id in state.selectedProjectIds }.joinToString { it.name }
            AlertDialog(
                onDismissRequest = { confirmBatchExport = false },
                title = { Text("Export selected projects as ZIP") },
                text = { Text("Selected projects: $selectedNames\n\nOne ZIP archive per project will be created in a destination you choose. Source projects remain unchanged.") },
                confirmButton = { Button(onClick = { confirmBatchExport = false; onExportProjects(state.selectedProjectIds) }) { Text("Choose export location") } },
                dismissButton = { TextButton(onClick = { confirmBatchExport = false }) { Text("Cancel") } },
            )
        }
        if (visibleProjects.isEmpty()) {
            Text(if (searchQuery.isBlank()) "No registered projects" else "No matching projects")
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
                    )
                }
            }
        }
    }
    FloatingActionButton(
        onClick = { addProjectOpen = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    ) { androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = "Add project") }
    if (addProjectOpen) {
        AlertDialog(
            onDismissRequest = { addProjectOpen = false },
            title = { Text("Add project") },
            text = {
                Column {
                    TextButton(onClick = { addProjectOpen = false; onCreateProject() }, Modifier.fillMaxWidth()) { Text("Create blank project") }
                    TextButton(onClick = { addProjectOpen = false; onImportFolder() }, Modifier.fillMaxWidth()) { Text("Import existing folder") }
                    TextButton(onClick = { addProjectOpen = false; onImportZip() }, Modifier.fillMaxWidth()) { Text("Import ZIP archive") }
                    TextButton(onClick = { addProjectOpen = false; onCloneGit() }, Modifier.fillMaxWidth()) { Text("Clone remote Git repository") }
                }
            },
            confirmButton = { TextButton(onClick = { addProjectOpen = false }) { Text("Cancel") } },
        )
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
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteCode by remember { mutableStateOf(Random.nextInt(100, 1000).toString()) }
    var enteredDeleteCode by remember { mutableStateOf("") }
    var renameVisible by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(project.name) }
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (selected) onToggleSelection(project.id) else onOpen(project.id) },
            onLongClick = { onToggleSelection(project.id) },
        ).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                if (selected) Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(if (project.description.isBlank()) "No description" else project.description, maxLines = 2)
                Text("Files: ${summary?.fileCount ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall)
                Text("Size: ${summary?.totalBytes?.let(::formatBytes) ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall)
                if (summary?.hasGit == true) Text("Git", style = MaterialTheme.typography.labelSmall)
                Text(relativeLastOpened(project.lastOpenedAt), style = MaterialTheme.typography.labelSmall)
                summary?.status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
            }
            TextButton(onClick = { onSelect(project.id); menuOpen = true }) { Text("More") }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Project details") }, onClick = { menuOpen = false; onDetails(project.id) })
            DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; onSelect(project.id); onRefresh() })
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameValue = project.name; renameVisible = true })
            DropdownMenuItem(text = { Text("Change Location") }, onClick = { menuOpen = false; onRelocate(project.id) })
            DropdownMenuItem(text = { Text("Copy & Duplicate") }, onClick = { menuOpen = false; onDuplicate(project.id) })
            DropdownMenuItem(text = { Text("Export or Share") }, onClick = { menuOpen = false; onExport(project.id) })
            DropdownMenuItem(text = { Text("Copy Storage Path") }, onClick = { menuOpen = false; onCopyPath(project.id) })
            DropdownMenuItem(text = { Text("Git Remote Details") }, onClick = { menuOpen = false; onDetails(project.id) })
            DropdownMenuItem(text = { Text("Open in Editor") }, onClick = { menuOpen = false; onOpen(project.id) })
            DropdownMenuItem(text = { Text("Open Git") }, onClick = { menuOpen = false; onFeedback("Open Git") })
            DropdownMenuItem(text = { Text("Open Terminal") }, onClick = { menuOpen = false; onFeedback("Open Terminal") })
            DropdownMenuItem(text = { Text("Open Browser or Preview") }, onClick = { menuOpen = false; onFeedback("Open Browser or Preview") })
            DropdownMenuItem(text = { Text("Remove from Registry") }, onClick = { menuOpen = false; onSelect(project.id); confirmRemove = true })
            DropdownMenuItem(text = { Text("Permanently Delete") }, onClick = { menuOpen = false; onSelect(project.id); deleteCode = Random.nextInt(100, 1000).toString(); enteredDeleteCode = ""; confirmDelete = true })
        }
        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("Remove project from registry?") },
                text = { Text("${project.name} will be removed from Android IDE, but its files, Git data, and location will remain unchanged.\n\nLocation: ${project.location.userVisiblePath ?: project.location.displayLabel}") },
                confirmButton = { Button(onClick = { confirmRemove = false; onRemove() }) { Text("Remove from Registry") } },
                dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
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
                        Text("Location: ${project.location.userVisiblePath ?: project.location.displayLabel}")
                        Text("Type $deleteCode to confirm")
                        OutlinedTextField(enteredDeleteCode, { enteredDeleteCode = it }, label = { Text("Confirmation code") }, singleLine = true)
                    }
                },
                confirmButton = { Button(onClick = { confirmDelete = false; onDelete() }, enabled = enteredDeleteCode == deleteCode) { Text("Delete permanently") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
        if (renameVisible) {
            AlertDialog(
                onDismissRequest = { renameVisible = false },
                title = { Text("Rename project") },
                text = { OutlinedTextField(renameValue, { renameValue = it }, label = { Text("Project name") }, singleLine = true) },
                confirmButton = {
                    Button(onClick = { renameVisible = false; onSelect(project.id); onRename(renameValue) }, enabled = renameValue.isNotBlank()) { Text("Rename") }
                },
                dismissButton = { TextButton(onClick = { renameVisible = false }) { Text("Cancel") } },
            )
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
    if (lastOpenedAt == null) return "Never opened"
    val seconds = Duration.between(lastOpenedAt, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "Opened just now"
        seconds < 3600 -> "Opened ${seconds / 60}m ago"
        seconds < 86_400 -> "Opened ${seconds / 3600}h ago"
        seconds < 2_592_000 -> "Opened ${seconds / 86_400}d ago"
        seconds < 31_536_000 -> "Opened ${seconds / 2_592_000}mo ago"
        else -> "Opened ${seconds / 31_536_000}y ago"
    }
}
