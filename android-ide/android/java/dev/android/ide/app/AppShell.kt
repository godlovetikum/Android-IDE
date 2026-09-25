// AppShell keeps application navigation and visible domain boundaries in one place.
// It does not own project files or the runtime state exposed by those domains.
package dev.android.ide.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.Surface as AppSurface
import java.text.DateFormat
import java.util.Date

@Composable
fun AppShell(viewModel: AppShellViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val context = LocalContext.current
    var sidebarVisible by rememberSaveable { mutableStateOf(true) }
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var createName by rememberSaveable { mutableStateOf("") }
    var createDescription by rememberSaveable { mutableStateOf("") }
    var createDestinationUri by rememberSaveable { mutableStateOf<String?>(null) }
    var zipDialogVisible by rememberSaveable { mutableStateOf(false) }
    var zipUri by rememberSaveable { mutableStateOf<String?>(null) }
    var zipName by rememberSaveable { mutableStateOf("") }
    var zipDescription by rememberSaveable { mutableStateOf("") }
    var zipDestinationUri by rememberSaveable { mutableStateOf<String?>(null) }
    var permanentDeleteDialogVisible by rememberSaveable { mutableStateOf(false) }
    var batchPermanentDeleteDialogVisible by rememberSaveable { mutableStateOf(false) }
    var duplicateDialogVisible by rememberSaveable { mutableStateOf(false) }
    var duplicateName by rememberSaveable { mutableStateOf("") }
    var duplicateDestinationUri by rememberSaveable { mutableStateOf<String?>(null) }
    var relocateDialogVisible by rememberSaveable { mutableStateOf(false) }
    var relocateName by rememberSaveable { mutableStateOf("") }
    var relocateDestinationUri by rememberSaveable { mutableStateOf<String?>(null) }
    var renameDialogVisible by rememberSaveable { mutableStateOf(false) }
    var renameName by rememberSaveable { mutableStateOf("") }
    var selectedProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var batchExportProjectIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }.onSuccess {
                viewModel.importExistingFolder(uri.toString())
            }.onFailure {
                viewModel.reportStatus("Android did not grant persistent access to the selected folder")
            }
        }
    }

    val createDestinationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }.onSuccess {
                createDestinationUri = uri.toString()
            }.onFailure {
                viewModel.reportStatus("Android did not grant persistent access to the selected destination")
            }
        }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onSuccess {
                zipUri = uri.toString()
                zipName = ""
                zipDescription = ""
                zipDestinationUri = null
                zipDialogVisible = true
            }.onFailure {
                viewModel.reportStatus("Android did not grant access to the selected ZIP archive")
            }
        }
    }

    val zipDestinationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onSuccess {
                zipDestinationUri = uri.toString()
            }.onFailure {
                viewModel.reportStatus("Android did not grant persistent access to the ZIP destination")
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.exportSelectedProject(uri.toString())
    }

    val shareExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportSelectedProject(uri.toString()) { completed ->
                if (completed) {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "Share project ZIP"))
                }
            }
        }
    }

    val duplicateDestinationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) duplicateDestinationUri = uri.toString()
    }

    val relocateDestinationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) relocateDestinationUri = uri.toString()
    }

    val batchExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null && batchExportProjectIds.isNotEmpty()) {
            viewModel.exportProjects(batchExportProjectIds, uri.toString())
            batchExportProjectIds = emptyList()
            selectedProjectIds = emptySet()
        }
    }

    fun show(surface: AppSurface) {
        if (surface != AppSurface.PROJECTS) selectedProjectIds = emptySet()
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
            onOpenProject = { projectId ->
                selectedProjectIds = emptySet()
                viewModel.openProject(projectId)
            },
            onCreateProject = {
                createName = ""
                createDescription = ""
                createDestinationUri = null
                createDialogVisible = true
            },
            onImportProject = { importLauncher.launch(null) },
            onImportZip = { zipLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            onRequestPermanentDelete = { permanentDeleteDialogVisible = true },
            onRequestBatchPermanentDelete = { batchPermanentDeleteDialogVisible = true },
            onExportProject = { exportLauncher.launch("project.zip") },
            onShareProject = { shareExportLauncher.launch("project.zip") },
            onCopyStoragePath = { path ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Project storage path", path))
                viewModel.reportStatus("Project storage path copied")
            },
            onCopyRemoteUrls = { urls ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Project remote URLs", urls))
                viewModel.reportStatus("Project remote URLs copied")
            },
            onExportSelectedProjects = {
                batchExportProjectIds = selectedProjectIds.toList()
                batchExportLauncher.launch(null)
            },
            onCopySelectedPaths = {
                val text = state.projects
                    .filter { it.id in selectedProjectIds }
                    .joinToString("\n") { project ->
                        "${project.name}: ${project.location.userVisiblePath ?: project.location.stableId}"
                    }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Project storage paths", text))
                viewModel.reportStatus("Project storage paths copied")
            },
            onDuplicateProject = {
                duplicateName = ""
                duplicateDestinationUri = null
                duplicateDialogVisible = true
            },
            onRelocateProject = {
                relocateName = ""
                relocateDestinationUri = null
                relocateDialogVisible = true
            },
            selectedProjectIds = selectedProjectIds,
            onToggleProjectSelection = { projectId ->
                selectedProjectIds = if (projectId in selectedProjectIds) {
                    selectedProjectIds - projectId
                } else {
                    selectedProjectIds + projectId
                }
            },
            onClearProjectSelection = { selectedProjectIds = emptySet() },
            onRemoveSelectedProjects = {
                val ids = selectedProjectIds.toList()
                selectedProjectIds = emptySet()
                viewModel.removeProjectsFromRegistry(ids)
            },
            viewModel = viewModel,
            onExit = onExit,
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

    if (createDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (!state.operationInProgress) createDialogVisible = false },
            title = { Text("Create project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        label = { Text("Project name") },
                        singleLine = true,
                        enabled = !state.operationInProgress,
                    )
                    OutlinedTextField(
                        value = createDescription,
                        onValueChange = { createDescription = it },
                        label = { Text("Description") },
                        enabled = !state.operationInProgress,
                    )
                    Text(
                        createDestinationUri ?: "Choose a destination folder",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { createDestinationLauncher.launch(null) },
                        enabled = !state.operationInProgress,
                    ) { Text("Choose destination") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        createDestinationUri?.let { destination ->
                            viewModel.createBlankProject(destination, createName, createDescription)
                            createDialogVisible = false
                        }
                    },
                    enabled = !state.operationInProgress &&
                        createName.isNotBlank() && createDestinationUri != null,
                ) {
                    if (state.operationInProgress) CircularProgressIndicator() else Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { createDialogVisible = false },
                    enabled = !state.operationInProgress,
                ) { Text("Cancel") }
            },
        )
    }

    if (zipDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (!state.operationInProgress) zipDialogVisible = false },
            title = { Text("Import ZIP project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = zipName,
                        onValueChange = { zipName = it },
                        label = { Text("Project name") },
                        singleLine = true,
                        enabled = !state.operationInProgress,
                    )
                    OutlinedTextField(
                        value = zipDescription,
                        onValueChange = { zipDescription = it },
                        label = { Text("Description") },
                        enabled = !state.operationInProgress,
                    )
                    Text(
                        zipDestinationUri ?: "Choose a destination folder",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { zipDestinationLauncher.launch(null) },
                        enabled = !state.operationInProgress,
                    ) { Text("Choose destination") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val archive = zipUri
                        val destination = zipDestinationUri
                        if (archive != null && destination != null) {
                            viewModel.importZip(archive, destination, zipName, zipDescription)
                            zipDialogVisible = false
                        }
                    },
                    enabled = !state.operationInProgress &&
                        zipName.isNotBlank() && zipUri != null && zipDestinationUri != null,
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(
                    onClick = { zipDialogVisible = false },
                    enabled = !state.operationInProgress,
                ) { Text("Cancel") }
            },
        )
    }

    if (permanentDeleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (!state.operationInProgress) permanentDeleteDialogVisible = false },
            title = { Text("Permanently delete project?") },
            text = {
                val selected = state.projects.firstOrNull { it.id == state.selectedProjectId }
                Text(
                    "Project: ${selected?.name ?: "Unknown"}\n" +
                        "Location: ${selected?.location?.userVisiblePath ?: selected?.location?.stableId ?: "Unavailable"}\n\n" +
                        "The selected project and all of its contents will be removed. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        permanentDeleteDialogVisible = false
                        viewModel.permanentlyDeleteSelectedProject()
                    },
                    enabled = !state.operationInProgress,
                ) { Text("Delete permanently") }
            },
            dismissButton = {
                TextButton(
                    onClick = { permanentDeleteDialogVisible = false },
                    enabled = !state.operationInProgress,
                ) { Text("Cancel") }
            },
        )
    }

    if (batchPermanentDeleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (!state.operationInProgress) batchPermanentDeleteDialogVisible = false },
            title = { Text("Permanently delete selected projects?") },
            text = {
                val selectedProjects = state.projects.filter { it.id in selectedProjectIds }
                Text(
                    selectedProjects.joinToString("\n") { project ->
                        "${project.name}: ${project.location.userVisiblePath ?: project.location.stableId}"
                    } + "\n\nThis permanently removes the selected project(s). " +
                        "Registered child projects must be deleted before their parents."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.permanentlyDeleteProjects(selectedProjectIds.toList())
                        selectedProjectIds = emptySet()
                        batchPermanentDeleteDialogVisible = false
                    },
                    enabled = !state.operationInProgress,
                ) { Text("Delete permanently") }
            },
            dismissButton = {
                TextButton(
                    onClick = { batchPermanentDeleteDialogVisible = false },
                    enabled = !state.operationInProgress,
                ) { Text("Cancel") }
            },
        )
    }

    if (renameDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (!state.operationInProgress) renameDialogVisible = false },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("New project name") },
                    singleLine = true,
                    enabled = !state.operationInProgress,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameSelectedProject(renameName)
                        renameDialogVisible = false
                    },
                    enabled = !state.operationInProgress && renameName.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) { Text("Cancel") }
            },
        )
    }

    if (duplicateDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (!state.operationInProgress) duplicateDialogVisible = false },
            title = { Text("Duplicate project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = duplicateName,
                        onValueChange = { duplicateName = it },
                        label = { Text("New project name") },
                        singleLine = true,
                        enabled = !state.operationInProgress,
                    )
                    Text(duplicateDestinationUri ?: "Choose a destination folder")
                    Button(
                        onClick = { duplicateDestinationLauncher.launch(null) },
                        enabled = !state.operationInProgress,
                    ) { Text("Choose destination") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        duplicateDestinationUri?.let { destination ->
                            viewModel.duplicateSelectedProject(destination, duplicateName)
                            duplicateDialogVisible = false
                        }
                    },
                    enabled = !state.operationInProgress &&
                        duplicateName.isNotBlank() && duplicateDestinationUri != null,
                ) { Text("Duplicate") }
            },
            dismissButton = {
                TextButton(onClick = { duplicateDialogVisible = false }) { Text("Cancel") }
            },
        )
    }

    if (relocateDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (!state.operationInProgress) relocateDialogVisible = false },
            title = { Text("Change project location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = relocateName,
                        onValueChange = { relocateName = it },
                        label = { Text("Project name") },
                        singleLine = true,
                        enabled = !state.operationInProgress,
                    )
                    Text(relocateDestinationUri ?: "Choose a destination folder")
                    Button(
                        onClick = { relocateDestinationLauncher.launch(null) },
                        enabled = !state.operationInProgress,
                    ) { Text("Choose destination") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        relocateDestinationUri?.let { destination ->
                            viewModel.relocateSelectedProject(destination, relocateName)
                            relocateDialogVisible = false
                        }
                    },
                    enabled = !state.operationInProgress &&
                        relocateName.isNotBlank() && relocateDestinationUri != null,
                ) { Text("Move project") }
            },
            dismissButton = {
                TextButton(onClick = { relocateDialogVisible = false }) { Text("Cancel") }
            },
        )
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
                text = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (surface == current) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .clickable { onNavigate(surface) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = if (surface == current) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
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
    onCreateProject: () -> Unit,
    onImportProject: () -> Unit,
    onImportZip: () -> Unit,
    onRequestPermanentDelete: () -> Unit,
    onRequestBatchPermanentDelete: () -> Unit,
    onExportProject: () -> Unit,
    onShareProject: () -> Unit,
    onCopyStoragePath: (String) -> Unit,
    onCopyRemoteUrls: (String) -> Unit,
    onExportSelectedProjects: () -> Unit,
    onCopySelectedPaths: () -> Unit,
    onDuplicateProject: () -> Unit,
    onRelocateProject: () -> Unit,
    selectedProjectIds: Set<String>,
    onToggleProjectSelection: (String) -> Unit,
    onClearProjectSelection: () -> Unit,
    onRemoveSelectedProjects: () -> Unit,
    viewModel: AppShellViewModel,
    onExit: () -> Unit,
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
                Button(
                    onClick = viewModel::refreshProjectList,
                    enabled = !state.operationInProgress,
                ) { Text("Refresh projects") }
                if (selectedProjectIds.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${selectedProjectIds.size} selected")
                        Button(
                            onClick = onRemoveSelectedProjects,
                            enabled = !state.operationInProgress,
                        ) { Text("Remove from registry") }
                        Button(
                            onClick = onRequestBatchPermanentDelete,
                            enabled = !state.operationInProgress,
                        ) { Text("Delete permanently") }
                        Button(
                            onClick = onExportSelectedProjects,
                            enabled = !state.operationInProgress,
                        ) { Text("Export ZIPs") }
                        Button(
                            onClick = onCopySelectedPaths,
                            enabled = !state.operationInProgress,
                        ) { Text("Copy paths") }
                        TextButton(
                            onClick = onClearProjectSelection,
                            enabled = !state.operationInProgress,
                        ) { Text("Cancel") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCreateProject, enabled = !state.operationInProgress) {
                        Text("Create project")
                    }
                    Button(onClick = onImportProject, enabled = !state.operationInProgress) {
                        Text("Import folder")
                    }
                    Button(onClick = onImportZip, enabled = !state.operationInProgress) {
                        Text("Import ZIP")
                    }
                }
                if (state.operationInProgress) {
                    Text("Importing project…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.projects.isEmpty()) Text("No registered projects")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            selected = project.id in selectedProjectIds,
                            selectionMode = selectedProjectIds.isNotEmpty(),
                            onOpenProject = onOpenProject,
                            onToggleSelection = onToggleProjectSelection,
                        )
                    }
                }
            }
            AppSurface.PROJECT_DETAILS -> {
                Text("Project Details", style = MaterialTheme.typography.headlineMedium)
                val selected = state.projects.firstOrNull { it.id == state.selectedProjectId }
                    Text(selected?.name ?: "No project selected")
                    Text(selected?.location?.displayLabel ?: "Location unavailable")
                    selected?.location?.capabilityExplanation?.let { explanation ->
                        Text(explanation, color = MaterialTheme.colorScheme.error)
                    }
                Button(
                    onClick = viewModel::refreshSelectedProjectDetails,
                    enabled = !state.detailsLoading,
                ) { Text("Refresh details") }
                Button(
                    onClick = {
                        renameName = selected?.name.orEmpty()
                        renameDialogVisible = true
                    },
                    enabled = !state.operationInProgress && selected != null,
                ) { Text("Rename project") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::removeSelectedProject,
                        enabled = !state.operationInProgress,
                    ) { Text("Remove from registry") }
                    Button(
                        onClick = onRequestPermanentDelete,
                        enabled = !state.operationInProgress,
                    ) { Text("Delete permanently") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExportProject, enabled = !state.operationInProgress) {
                        Text("Export ZIP")
                    }
                    Button(onClick = onShareProject, enabled = !state.operationInProgress) {
                        Text("Share ZIP")
                    }
                    Button(
                        onClick = { state.projectDetails?.storagePath?.let(onCopyStoragePath) },
                        enabled = !state.operationInProgress && state.projectDetails != null,
                    ) { Text("Copy path") }
                    Button(
                        onClick = {
                            val urls = state.projectDetails?.git?.remotes
                                ?.joinToString("\n") { "${it.name}: ${it.url}" }
                            if (!urls.isNullOrBlank()) onCopyRemoteUrls(urls)
                        },
                        enabled = !state.operationInProgress &&
                            !state.projectDetails?.git?.remotes.isNullOrEmpty(),
                    ) { Text("Copy remotes") }
                    Button(onClick = onDuplicateProject, enabled = !state.operationInProgress) {
                        Text("Duplicate")
                    }
                    Button(onClick = onRelocateProject, enabled = !state.operationInProgress) {
                        Text("Change location")
                    }
                }
                if (state.detailsLoading) {
                    Text("Inspecting project contents…")
                }
                state.projectDetails?.let { details ->
                    Text(details.description.ifBlank { "No description" })
                    DetailLine("Storage provider", details.storageProvider)
                    DetailLine("Storage path", details.storagePath)
                    DetailLine("Files", details.fileCount.toString())
                    DetailLine("Folders", details.folderCount.toString())
                    DetailLine("Total size", formatBytes(details.totalBytes))
                    DetailLine("Created", formatTimestamp(details.creationTimeMs))
                    DetailLine("Last modified", formatTimestamp(details.lastModifiedTimeMs))
                    DetailLine("Capability", details.project.capabilityState.name)
                    details.git?.currentBranch?.let { branch ->
                        DetailLine("Git branch", branch)
                    }
                    if (details.languageBytes.isNotEmpty()) {
                        Text("Content by language", style = MaterialTheme.typography.titleMedium)
                        details.languageBytes.entries
                            .sortedByDescending { it.value }
                            .take(5)
                            .forEach { (language, bytes) ->
                                DetailLine(language, formatBytes(bytes))
                            }
                    }
                }
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
private fun ProjectRow(
    project: ProjectIdentity,
    selected: Boolean,
    selectionMode: Boolean,
    onOpenProject: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelection(project.id)
                    else onOpenProject(project.id)
                },
                onLongClick = { onToggleSelection(project.id) },
            )
            .padding(vertical = 8.dp),
    ) {
        Text(project.name, style = MaterialTheme.typography.titleMedium)
        if (project.description.isNotBlank()) {
            Text(project.description, style = MaterialTheme.typography.bodyMedium)
        }
        Text(project.location.displayLabel, style = MaterialTheme.typography.bodySmall)
        Text(project.location.capabilityState.name, style = MaterialTheme.typography.labelSmall)
        project.location.capabilityExplanation?.let { explanation ->
            Text(explanation, color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = { menuVisible = true }) { Text("Actions") }
        DropdownMenu(
            expanded = menuVisible,
            onDismissRequest = { menuVisible = false },
        ) {
            DropdownMenuItem(
                text = { Text("Open details") },
                onClick = {
                    menuVisible = false
                    onOpenProject(project.id)
                },
            )
            DropdownMenuItem(
                text = { Text(if (selected) "Remove from selection" else "Select for batch actions") },
                onClick = {
                    menuVisible = false
                    onToggleSelection(project.id)
                },
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}

private fun formatTimestamp(timeMs: Long?): String = timeMs?.let {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
} ?: "Unavailable"

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
