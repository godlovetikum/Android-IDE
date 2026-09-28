package dev.android.ide.ui

import android.app.Activity
import android.graphics.Color
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.ui.shell.AppShell
import dev.android.ide.ui.screen.humanReadableStorageLocation
import dev.android.ide.ui.theme.AndroidIDETheme
import dev.android.ide.viewmodel.IdeViewModel

private enum class ProjectOperationKind { DUPLICATE, RELOCATE }

private enum class AcquisitionVerdict { INPUT_REQUIRED, READY, CHECKING, BLOCKED, COMPLETE }

private fun reportVerdict(report: dev.android.ide.contracts.OperationReport?): AcquisitionVerdict? = report?.let {
    when (it.outcome) {
        dev.android.ide.contracts.OperationOutcome.COMPLETE -> AcquisitionVerdict.COMPLETE
        dev.android.ide.contracts.OperationOutcome.BLOCKED,
        dev.android.ide.contracts.OperationOutcome.FAILED,
        dev.android.ide.contracts.OperationOutcome.PARTIAL,
        dev.android.ide.contracts.OperationOutcome.INTERRUPTED -> AcquisitionVerdict.BLOCKED
        else -> AcquisitionVerdict.INPUT_REQUIRED
    }
}

@Composable
private fun AcquisitionInputs(content: @Composable () -> Unit) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text("Your input", style = MaterialTheme.typography.titleSmall)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun AcquisitionFeedback(verdict: AcquisitionVerdict, message: String, busy: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (verdict) {
        AcquisitionVerdict.BLOCKED -> colors.errorContainer to colors.onErrorContainer
        AcquisitionVerdict.COMPLETE -> colors.primaryContainer to colors.onPrimaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
            Text("Android IDE", style = MaterialTheme.typography.labelSmall, color = content)
            RowWithProgress(busy, content)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

@Composable
private fun RowWithProgress(busy: Boolean, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (busy) {
            CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp, color = color)
        }
        Text("Status", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private fun projectNameIsValid(name: String): Boolean =
    name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != "." && name != ".."

/** Composition root: owns Android launchers and reviewed acquisition workflow state. */
@Composable
fun AppRoot(viewModel: AppShellViewModel, ideViewModel: IdeViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val shellState by viewModel.state.collectAsState()
    val ideState by ideViewModel.uiState.collectAsState()
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
            viewModel.clearOperationFeedback()
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
            viewModel.clearOperationFeedback()
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
    }
    val batchExportDestinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && pendingBatchExportIds.isNotEmpty()) {
            persistTreePermission(context, uri)
            viewModel.exportProjects(pendingBatchExportIds.toList(), uri.toString())
        }
        pendingBatchExportIds = emptySet()
    }

    val baseDensity = LocalDensity.current
    val uiScale = ideState.editorSettings.uiFontScale
    val systemDark = isSystemInDarkTheme()
    val resolvedDark = when (ideState.appTheme) {
        dev.android.ide.data.model.AppTheme.DARK -> true
        dev.android.ide.data.model.AppTheme.LIGHT -> false
        dev.android.ide.data.model.AppTheme.SYSTEM -> systemDark
    }
    val rootView = LocalView.current
    SideEffect {
        val activity = rootView.context as? Activity
        activity?.window?.let { window ->
            window.statusBarColor = if (resolvedDark) Color.rgb(30, 30, 30) else Color.WHITE
            window.navigationBarColor = if (resolvedDark) Color.BLACK else Color.WHITE
            androidx.core.view.WindowCompat.getInsetsController(window, rootView).apply {
                isAppearanceLightStatusBars = !resolvedDark
                isAppearanceLightNavigationBars = !resolvedDark
            }
        }
    }
    AndroidIDETheme(appTheme = ideState.appTheme) {
        CompositionLocalProvider(
            LocalDensity provides androidx.compose.ui.unit.Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale * uiScale,
            ),
        ) {
        AppShell(
            viewModel = viewModel,
            ideViewModel = ideViewModel,
            onExit = onExit,
            onCreateProject = { initialName ->
                viewModel.clearOperationFeedback()
                createName = initialName.orEmpty()
                createDescription = ""
                createDestination = null
                createReviewVisible = false
                createVisible = true
            },
            onImportFolder = { openFolder.launch(null) },
            onImportZip = { openZip.launch(arrayOf("application/zip", "application/octet-stream")) },
            onCloneGit = { feedback = "Coming soon (phase 6): Git repository cloning is not currently wired." },
            onExportProject = { projectId -> viewModel.clearOperationFeedback(); pendingExportProjectId = projectId; exportReviewVisible = true },
            onExportProjects = { projectIds -> pendingBatchExportIds = projectIds; batchExportDestinationPicker.launch(null) },
            onDuplicateProject = { projectId ->
                viewModel.clearOperationFeedback()
                val project = shellState.projects.firstOrNull { it.id == projectId }
                operationKind = ProjectOperationKind.DUPLICATE
                operationProjectId = projectId
                operationName = project?.name.orEmpty()
                operationDescription = project?.description.orEmpty()
                operationDestination = null
            },
            onRelocateProject = { projectId ->
                viewModel.clearOperationFeedback()
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
                        AcquisitionInputs {
                            OutlinedTextField(createName, { createName = it }, label = { Text("Project name") }, supportingText = { Text("Use one folder name") }, isError = createName.isNotBlank() && !projectNameIsValid(createName), enabled = !shellState.operationInProgress, singleLine = true)
                            OutlinedTextField(createDescription, { createDescription = it }, label = { Text("Description (optional)") }, enabled = !shellState.operationInProgress)
                            PickerResult("Storage location", createDestination)
                            Button(onClick = { createDestinationPicker.launch(null) }, enabled = !shellState.operationInProgress) { Text("Choose storage location") }
                        }
                        Spacer(Modifier.padding(2.dp))
                        AcquisitionFeedback(
                            if (shellState.operationInProgress) AcquisitionVerdict.CHECKING else if (createName.isBlank() || createDestination == null) AcquisitionVerdict.INPUT_REQUIRED else if (!projectNameIsValid(createName)) AcquisitionVerdict.BLOCKED else AcquisitionVerdict.READY,
                            when {
                                shellState.operationInProgress -> "Creating the project…"
                                createName.isBlank() -> "Enter a project name to continue"
                                !projectNameIsValid(createName) -> "Use a single valid folder name"
                                createDestination == null -> "Choose a storage location to continue"
                                else -> "Ready to review the project destination"
                            },
                            shellState.operationInProgress,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { createVisible = false; createReviewVisible = true },
                        enabled = !shellState.operationInProgress && projectNameIsValid(createName) && createDestination != null,
                    ) { Text("Review") }
                },
                dismissButton = { TextButton(onClick = { createVisible = false }, enabled = !shellState.operationInProgress) { Text("Cancel") } },
            )
        }
        if (createReviewVisible) {
            AlertDialog(
                onDismissRequest = { if (!shellState.operationInProgress) createReviewVisible = false },
                title = { Text("Review project") },
                text = {
                    Column {
                        AcquisitionInputs {
                            Text("Project name: $createName")
                            Text("Description: ${createDescription.ifBlank { "Not provided" }}")
                            PickerResult("Target storage location", createDestination)
                        }
                        AcquisitionFeedback(reportVerdict(shellState.operationReport) ?: AcquisitionVerdict.READY, shellState.operationReport?.message ?: "Ready to create after the destination conflict check", shellState.operationInProgress)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val destination = createDestination
                        if (destination != null) {
                            viewModel.createBlankProject(destination, createName, createDescription)
                        }
                    }, enabled = shellState.operationReport == null && !shellState.operationInProgress && projectNameIsValid(createName) && createDestination != null) { Text("Create project") }
                },
                dismissButton = { TextButton(onClick = { createReviewVisible = false; createVisible = true }, enabled = !shellState.operationInProgress) { Text("Back") } },
            )
        }
        if (folderReviewVisible) {
            AlertDialog(
                onDismissRequest = { if (!shellState.operationInProgress) folderReviewVisible = false },
                title = { Text("Review folder import") },
                text = {
                    Column {
                        AcquisitionInputs {
                            PickerResult("Selected folder", folderUri)
                            OutlinedTextField(folderName, { folderName = it }, label = { Text("Project name") }, enabled = !shellState.operationInProgress, singleLine = true)
                            OutlinedTextField(folderDescription, { folderDescription = it }, label = { Text("Description (optional)") }, enabled = !shellState.operationInProgress)
                        }
                        val inspection = shellState.folderInspection
                        val folderVerdict = when {
                            shellState.operationInProgress -> AcquisitionVerdict.CHECKING
                            inspection == null -> AcquisitionVerdict.CHECKING
                            !inspection.readable || !inspection.writable || inspection.alreadyRegistered || !inspection.containmentVerified || inspection.overlapsRegisteredProject -> AcquisitionVerdict.BLOCKED
                            !projectNameIsValid(folderName) -> AcquisitionVerdict.INPUT_REQUIRED
                            else -> AcquisitionVerdict.READY
                        }
                        AcquisitionFeedback(reportVerdict(shellState.operationReport) ?: folderVerdict, shellState.operationReport?.message ?: when {
                            shellState.operationInProgress -> "Registering the selected folder…"
                            inspection == null -> "Checking whether this folder can be registered…"
                            !inspection.readable || !inspection.writable -> "Couldn't verify the target location"
                            inspection.alreadyRegistered -> "This folder is already registered"
                            !inspection.containmentVerified -> "Couldn't verify the target location"
                            inspection.overlapsRegisteredProject -> "This folder overlaps another project"
                            !projectNameIsValid(folderName) -> "Enter a valid project name to continue"
                            else -> "Ready to register this folder in place"
                        }, shellState.operationInProgress || inspection == null)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val uri = folderUri
                        if (uri != null) {
                            viewModel.importExistingFolder(uri, folderName, folderDescription)
                        }
                    }, enabled = shellState.operationReport == null && !shellState.operationInProgress && projectNameIsValid(folderName) && shellState.folderInspection?.let {
                        it.readable && it.writable && !it.alreadyRegistered &&
                            it.containmentVerified && !it.overlapsRegisteredProject
                    } == true) { Text("Import project") }
                },
                dismissButton = { TextButton(onClick = { folderReviewVisible = false }, enabled = !shellState.operationInProgress) { Text("Cancel") } },
            )
        }
        if (zipReviewVisible) {
            val zipReady = zipUri != null && projectNameIsValid(zipName) && zipDestination != null
            AlertDialog(
                onDismissRequest = { if (!shellState.operationInProgress) zipReviewVisible = false },
                title = { Text("Review ZIP import") },
                text = {
                    Column {
                        AcquisitionInputs {
                            PickerResult("ZIP archive", zipUri)
                            OutlinedTextField(zipName, { zipName = it }, label = { Text("Project name") }, enabled = !shellState.operationInProgress, singleLine = true)
                            OutlinedTextField(zipDescription, { zipDescription = it }, label = { Text("Description (optional)") }, enabled = !shellState.operationInProgress)
                            PickerResult("Storage location", zipDestination)
                            Button(onClick = { zipDestinationPicker.launch(null) }, enabled = !shellState.operationInProgress) { Text("Choose storage location") }
                        }
                        AcquisitionFeedback(reportVerdict(shellState.operationReport) ?: if (shellState.operationInProgress) AcquisitionVerdict.CHECKING else if (zipReady) AcquisitionVerdict.READY else AcquisitionVerdict.INPUT_REQUIRED, shellState.operationReport?.message ?: when {
                            shellState.operationInProgress -> "Validating and importing the archive…"
                            zipUri == null -> "Choose a ZIP archive to continue"
                            !projectNameIsValid(zipName) -> "Enter a valid project name to continue"
                            zipDestination == null -> "Choose a storage location to continue"
                            else -> "Ready to validate the archive and destination"
                        }, shellState.operationInProgress)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val archive = zipUri
                        val destination = zipDestination
                        if (archive != null && destination != null) {
                            viewModel.importZip(archive, destination, zipName, zipDescription)
                        }
                    }, enabled = shellState.operationReport == null && !shellState.operationInProgress && zipReady) { Text("Import project") }
                },
                dismissButton = { TextButton(onClick = { zipReviewVisible = false }, enabled = !shellState.operationInProgress) { Text("Cancel") } },
            )
        }
        if (exportReviewVisible) {
            val project = shellState.projects.firstOrNull { it.id == pendingExportProjectId }
            AlertDialog(
                onDismissRequest = { if (!shellState.operationInProgress) { exportReviewVisible = false; pendingExportProjectId = null } },
                title = { Text("Export project as ZIP") },
                text = {
                    Column {
                        Text("Source: ${humanReadableStorageLocation(project?.location?.userVisiblePath ?: project?.location?.displayLabel)}")
                        Text("Archive name: ${project?.name ?: "project"}.zip")
                        Text("The project is copied without changing the source. Choose a permitted destination in the next step.")
                        shellState.operationReport?.let { report ->
                            Text(report.message, color = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        if (shellState.operationInProgress) {
                            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                                Text("Exporting…")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        exportFilePicker.launch("${project?.name ?: "project"}.zip")
                    }, enabled = project != null && !shellState.operationInProgress) { Text("Choose export location") }
                },
                dismissButton = { TextButton(onClick = { exportReviewVisible = false; pendingExportProjectId = null }, enabled = !shellState.operationInProgress) { Text(if (shellState.operationInProgress) "Please wait" else "Cancel") } },
            )
        }
        operationKind?.let { kind ->
            val project = shellState.projects.firstOrNull { it.id == operationProjectId }
            AlertDialog(
                onDismissRequest = { if (!shellState.operationInProgress) operationKind = null },
                title = { Text(if (kind == ProjectOperationKind.DUPLICATE) "Copy & Duplicate project" else "Change project location") },
                text = {
                    Column {
                        Text("Source: ${humanReadableStorageLocation(project?.location?.userVisiblePath ?: project?.location?.displayLabel)}")
                        OutlinedTextField(operationName, { operationName = it }, label = { Text("Project name") }, enabled = !shellState.operationInProgress, singleLine = true)
                        if (kind == ProjectOperationKind.DUPLICATE) {
                            OutlinedTextField(operationDescription, { operationDescription = it }, label = { Text("Description") }, enabled = !shellState.operationInProgress)
                        }
                        PickerResult("Destination parent", operationDestination)
                        Button(onClick = { operationDestinationPicker.launch(null) }, enabled = !shellState.operationInProgress) { Text("Choose destination parent") }
                        Text(if (kind == ProjectOperationKind.DUPLICATE) {
                            "The original remains unchanged. The destination will be checked for conflicts and project containment before copying."
                        } else {
                            "The project is copied and verified before the original is removed. A failed cleanup is reported as partial."
                        })
                        shellState.operationReport?.let { report -> Text(report.message, color = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                        if (shellState.operationInProgress) { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp); Text("Working…") } }
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
                        }
                    }, enabled = project != null && operationName.isNotBlank() && operationDestination != null && !shellState.operationInProgress) {
                        Text(if (shellState.operationInProgress) "Working…" else if (kind == ProjectOperationKind.DUPLICATE) "Copy & Duplicate" else "Change Location")
                    }
                },
                dismissButton = { TextButton(onClick = { operationKind = null }, enabled = !shellState.operationInProgress) { Text(if (shellState.operationInProgress) "Please wait" else "Cancel") } },
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
}

private fun persistTreePermission(context: android.content.Context, uri: android.net.Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

@Composable
private fun PickerResult(label: String, rawValue: String?) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (rawValue == null) colors.surfaceVariant else colors.primaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label.uppercase(), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                text = if (rawValue == null) "Not selected" else humanReadableStorageLocation(rawValue),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = if (rawValue == null) colors.onSurfaceVariant else colors.onPrimaryContainer,
            )
        }
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
