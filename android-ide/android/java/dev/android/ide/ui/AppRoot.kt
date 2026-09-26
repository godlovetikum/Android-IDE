package dev.android.ide.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.ui.shell.AppShell
import dev.android.ide.ui.theme.AndroidIDETheme

private enum class ProjectOperationKind { DUPLICATE, RELOCATE }

/** Composition root: owns Android launchers and reviewed acquisition workflow state. */
@Composable
fun AppRoot(viewModel: AppShellViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val shellState by viewModel.state.collectAsState()
    var createVisible by remember { mutableStateOf(false) }
    var createReviewVisible by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var createDescription by remember { mutableStateOf("") }
    var createDestination by remember { mutableStateOf<String?>(null) }

    var folderUri by remember { mutableStateOf<String?>(null) }
    var folderReviewVisible by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var folderDescription by remember { mutableStateOf("") }

    var zipUri by remember { mutableStateOf<String?>(null) }
    var zipReviewVisible by remember { mutableStateOf(false) }
    var zipName by remember { mutableStateOf("") }
    var zipDescription by remember { mutableStateOf("") }
    var zipDestination by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var operationKind by remember { mutableStateOf<ProjectOperationKind?>(null) }
    var operationProjectId by remember { mutableStateOf<String?>(null) }
    var operationName by remember { mutableStateOf("") }
    var operationDescription by remember { mutableStateOf("") }
    var operationDestination by remember { mutableStateOf<String?>(null) }
    var exportReviewVisible by remember { mutableStateOf(false) }
    var pendingExportProjectId by remember { mutableStateOf<String?>(null) }
    var pendingBatchExportIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistTreePermission(context, uri)
            folderUri = uri.toString()
            folderName = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: ""
            folderDescription = ""
            viewModel.inspectExistingFolder(uri.toString())
            folderReviewVisible = true
        }
    }
    val openZip = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            persistDocumentPermission(context, uri)
            zipUri = uri.toString()
            zipName = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".zip") ?: "Imported project"
            zipDescription = ""
            zipDestination = null
            zipReviewVisible = true
        }
    }
    val createDestinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistTreePermission(context, uri)
            createDestination = uri.toString()
        }
    }
    val zipDestinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistTreePermission(context, uri)
            zipDestination = uri.toString()
        }
    }
    val operationDestinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistTreePermission(context, uri)
            operationDestination = uri.toString()
        }
    }
    val exportFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val projectId = pendingExportProjectId
        if (uri != null && projectId != null) {
            viewModel.selectProject(projectId)
            viewModel.exportSelectedProject(uri.toString())
        }
        pendingExportProjectId = null
    }
    val batchExportDestinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && pendingBatchExportIds.isNotEmpty()) {
            persistTreePermission(context, uri)
            viewModel.exportProjects(pendingBatchExportIds.toList(), uri.toString())
        }
        pendingBatchExportIds = emptySet()
    }

    AndroidIDETheme {
        AppShell(
            viewModel = viewModel,
            onExit = onExit,
            onCreateProject = {
                createName = ""
                createDescription = ""
                createDestination = null
                createReviewVisible = false
                createVisible = true
            },
            onImportFolder = { openFolder.launch(null) },
            onImportZip = { openZip.launch(arrayOf("application/zip", "application/octet-stream")) },
            onCloneGit = { feedback = "Clone Git repository is scheduled for phase 6." },
            onExportProject = { projectId -> pendingExportProjectId = projectId; exportReviewVisible = true },
            onExportProjects = { projectIds -> pendingBatchExportIds = projectIds; batchExportDestinationPicker.launch(null) },
            onDuplicateProject = { projectId ->
                val project = shellState.projects.firstOrNull { it.id == projectId }
                operationKind = ProjectOperationKind.DUPLICATE
                operationProjectId = projectId
                operationName = project?.name.orEmpty()
                operationDescription = project?.description.orEmpty()
                operationDestination = null
            },
            onRelocateProject = { projectId ->
                val project = shellState.projects.firstOrNull { it.id == projectId }
                operationKind = ProjectOperationKind.RELOCATE
                operationProjectId = projectId
                operationName = project?.name.orEmpty()
                operationDescription = project?.description.orEmpty()
                operationDestination = null
            },
        )

        if (createVisible) {
            AlertDialog(
                onDismissRequest = { if (!shellState.operationInProgress) createVisible = false },
                title = { Text("Create blank project") },
                text = {
                    Column {
                        OutlinedTextField(createName, { createName = it }, label = { Text("Project name") }, singleLine = true)
                        OutlinedTextField(createDescription, { createDescription = it }, label = { Text("Description") })
                        Text(if (createDestination == null) "No destination selected" else "Destination selected")
                        Button(onClick = { createDestinationPicker.launch(null) }) { Text("Choose destination") }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { createReviewVisible = true },
                        enabled = createName.isNotBlank() && createDestination != null,
                    ) { Text("Review") }
                },
                dismissButton = { TextButton(onClick = { createVisible = false }) { Text("Cancel") } },
            )
        }
        if (createReviewVisible) {
            AlertDialog(
                onDismissRequest = { createReviewVisible = false },
                title = { Text("Review project") },
                text = {
                    Column {
                        Text("Name: $createName")
                        Text("Description: ${createDescription.ifBlank { "No description" }}")
                        Text("Target: ${createDestination.orEmpty()}")
                        Text("The destination will be checked for conflicts before anything is created.")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val destination = createDestination
                        if (destination != null) {
                            viewModel.createBlankProject(destination, createName, createDescription)
                            createReviewVisible = false
                            createVisible = false
                        }
                    }) { Text("Create project") }
                },
                dismissButton = { TextButton(onClick = { createReviewVisible = false }) { Text("Back") } },
            )
        }
        if (folderReviewVisible) {
            AlertDialog(
                onDismissRequest = { folderReviewVisible = false },
                title = { Text("Review folder import") },
                text = {
                    Column {
                        Text("Selected folder: ${folderUri.orEmpty()}")
                        shellState.folderInspection?.let { inspection ->
                            Text("Access: ${if (inspection.readable && inspection.writable) "Readable and writable" else "Unavailable"}")
                            Text("Registration: ${if (inspection.alreadyRegistered) "Already registered" else "Not registered"}")
                            Text("Project containment: ${if (inspection.nestedInRegisteredProject) "Conflicts with a registered project" else "No registered-project conflict detected"}")
                            inspection.explanation?.let { Text(it) }
                        }
                        OutlinedTextField(folderName, { folderName = it }, label = { Text("Project name") }, singleLine = true)
                        OutlinedTextField(folderDescription, { folderDescription = it }, label = { Text("Description") })
                        Text("The selected folder will be inspected and registered in place. No replacement folder will be created.")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val uri = folderUri
                        if (uri != null) {
                            viewModel.importExistingFolder(uri, folderName, folderDescription)
                            folderReviewVisible = false
                        }
                    }, enabled = folderName.isNotBlank() && shellState.folderInspection?.let {
                        it.readable && it.writable && !it.alreadyRegistered && !it.nestedInRegisteredProject
                    } == true) { Text("Import project") }
                },
                dismissButton = { TextButton(onClick = { folderReviewVisible = false }) { Text("Cancel") } },
            )
        }
        if (zipReviewVisible) {
            AlertDialog(
                onDismissRequest = { zipReviewVisible = false },
                title = { Text("Review ZIP import") },
                text = {
                    Column {
                        Text("Archive: ${zipUri.orEmpty()}")
                        OutlinedTextField(zipName, { zipName = it }, label = { Text("Project name") }, singleLine = true)
                        OutlinedTextField(zipDescription, { zipDescription = it }, label = { Text("Description") })
                        Text(if (zipDestination == null) "No extraction destination selected" else "Extraction destination selected")
                        Button(onClick = { zipDestinationPicker.launch(null) }) { Text("Choose destination") }
                        Text("The archive and destination will be validated for unsafe paths and conflicts before extraction.")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val archive = zipUri
                        val destination = zipDestination
                        if (archive != null && destination != null) {
                            viewModel.importZip(archive, destination, zipName, zipDescription)
                            zipReviewVisible = false
                        }
                    }, enabled = zipName.isNotBlank() && zipDestination != null) { Text("Import project") }
                },
                dismissButton = { TextButton(onClick = { zipReviewVisible = false }) { Text("Cancel") } },
            )
        }
        if (exportReviewVisible) {
            val project = shellState.projects.firstOrNull { it.id == pendingExportProjectId }
            AlertDialog(
                onDismissRequest = { exportReviewVisible = false; pendingExportProjectId = null },
                title = { Text("Export project as ZIP") },
                text = {
                    Column {
                        Text("Source: ${project?.location?.userVisiblePath ?: project?.location?.displayLabel ?: "Unavailable"}")
                        Text("Archive name: ${project?.name ?: "project"}.zip")
                        Text("The project is copied without changing the source. Choose a permitted destination in the next step.")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        exportReviewVisible = false
                        exportFilePicker.launch("${project?.name ?: "project"}.zip")
                    }, enabled = project != null) { Text("Choose export location") }
                },
                dismissButton = { TextButton(onClick = { exportReviewVisible = false; pendingExportProjectId = null }) { Text("Cancel") } },
            )
        }
        operationKind?.let { kind ->
            val project = shellState.projects.firstOrNull { it.id == operationProjectId }
            AlertDialog(
                onDismissRequest = { operationKind = null },
                title = { Text(if (kind == ProjectOperationKind.DUPLICATE) "Copy & Duplicate project" else "Change project location") },
                text = {
                    Column {
                        Text("Source: ${project?.location?.userVisiblePath ?: project?.location?.displayLabel ?: "Unavailable"}")
                        OutlinedTextField(operationName, { operationName = it }, label = { Text("Project name") }, singleLine = true)
                        if (kind == ProjectOperationKind.DUPLICATE) {
                            OutlinedTextField(operationDescription, { operationDescription = it }, label = { Text("Description") })
                        }
                        Text(if (operationDestination == null) "No destination parent selected" else "Destination parent selected")
                        Button(onClick = { operationDestinationPicker.launch(null) }) { Text("Choose destination parent") }
                        Text(if (kind == ProjectOperationKind.DUPLICATE) {
                            "The original remains unchanged. The destination will be checked for conflicts and project containment before copying."
                        } else {
                            "The project is copied and verified before the original is removed. A failed cleanup is reported as partial."
                        })
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val id = operationProjectId
                        val destination = operationDestination
                        if (id != null && destination != null) {
                            viewModel.selectProject(id)
                            if (kind == ProjectOperationKind.DUPLICATE) {
                                viewModel.duplicateSelectedProject(destination, operationName, operationDescription)
                            } else {
                                viewModel.relocateSelectedProject(destination, operationName)
                            }
                            operationKind = null
                        }
                    }, enabled = project != null && operationName.isNotBlank() && operationDestination != null) {
                        Text(if (kind == ProjectOperationKind.DUPLICATE) "Copy & Duplicate" else "Change Location")
                    }
                },
                dismissButton = { TextButton(onClick = { operationKind = null }) { Text("Cancel") } },
            )
        }
        shellState.acquiredProjectId?.let { projectId ->
            AlertDialog(
                onDismissRequest = viewModel::dismissAcquisitionPrompt,
                title = { Text("Project imported") },
                text = { Text("The selected folder was registered successfully. Open it now?") },
                confirmButton = {
                    Button(onClick = { viewModel.dismissAcquisitionPrompt(); viewModel.openProject(projectId) }) { Text("Open project") }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissAcquisitionPrompt) { Text("Not now") } },
            )
        }
        feedback?.let { message ->
            AlertDialog(
                onDismissRequest = { feedback = null },
                title = { Text("Git clone") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { feedback = null }) { Text("OK") } },
            )
        }
    }
}

private fun persistTreePermission(context: android.content.Context, uri: android.net.Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

private fun persistDocumentPermission(context: android.content.Context, uri: android.net.Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
