package dev.android.ide.ui

import android.app.Activity
import android.graphics.Color
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.project.CreateProjectTemplate
import dev.android.ide.ui.shell.AppShell
import dev.android.ide.ui.screen.humanReadableStorageLocation
import dev.android.ide.ui.theme.AndroidIDETheme
import dev.android.ide.viewmodel.IdeViewModel

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
    var createTemplate by remember { mutableStateOf(CreateProjectTemplate.FROM_SCRATCH) }

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
    var gitCloneVisible by remember { mutableStateOf(false) }
    var gitRepository by remember { mutableStateOf("") }
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
                createTemplate = CreateProjectTemplate.FROM_SCRATCH
                createReviewVisible = false
                createVisible = true
            },
            onImportFolder = { openFolder.launch(null) },
            onImportZip = { openZip.launch(arrayOf("application/zip", "application/octet-stream")) },
            onCloneGit = { feedback = null; gitRepository = ""; gitCloneVisible = true },
            onExportProject = { projectId -> viewModel.clearOperationFeedback(); pendingExportProjectId = projectId; exportReviewVisible = true },
            onExportProjects = { projectIds -> pendingBatchExportIds = projectIds; batchExportDestinationPicker.launch(null) },
            onDuplicateProject = { projectId ->
                viewModel.clearOperationFeedback()
                val project = shellState.projects.firstOrNull { it.id == projectId }
                operationKind = ProjectOperationKind.DUPLICATE
                operationProjectId = projectId
                operationName = project?.name?.let { "Copy of $it" }.orEmpty()
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

        if (createVisible || createReviewVisible) {
            CreateNewProjectDialog(
                state = shellState,
                viewModel = viewModel,
                name = createName,
                description = createDescription,
                destination = createDestination,
                template = createTemplate,
                reviewVisible = createReviewVisible,
                onNameChange = { createName = it },
                onDescriptionChange = { createDescription = it },
                onDestination = { createDestinationPicker.launch(null) },
                onTemplateChange = { createTemplate = it },
                onReviewVisibleChange = {
                    createReviewVisible = it
                    createVisible = !it
                },
                onDismiss = { createVisible = false; createReviewVisible = false },
                onOpenProject = { createVisible = false; createReviewVisible = false; viewModel.openProject(it) },
                onLocation = ::humanReadableStorageLocation,
                validName = ::projectNameIsValid,
            )
        }
        if (folderReviewVisible) {
            LoadExistingProjectDialog(
                state = shellState,
                viewModel = viewModel,
                folderUri = folderUri,
                description = folderDescription,
                folderName = folderName,
                onDescriptionChange = { folderDescription = it },
                onCancel = { folderReviewVisible = false; viewModel.dismissAcquisitionPrompt() },
                onOpenProject = { folderReviewVisible = false; viewModel.openProject(it) },
                onLocation = ::humanReadableStorageLocation,
                validName = ::projectNameIsValid,
            )
        }
        if (zipReviewVisible) {
            ImportZipProjectDialog(
                state = shellState,
                viewModel = viewModel,
                archiveUri = zipUri,
                name = zipName,
                description = zipDescription,
                destination = zipDestination,
                onNameChange = { zipName = it },
                onDescriptionChange = { zipDescription = it },
                onDestination = { zipDestinationPicker.launch(null) },
                onCancel = { zipReviewVisible = false; viewModel.dismissAcquisitionPrompt() },
                onOpenProject = { zipReviewVisible = false; viewModel.openProject(it) },
                onLocation = ::humanReadableStorageLocation,
                validName = ::projectNameIsValid,
            )
        }
        CloneGitProjectDialog(
            visible = gitCloneVisible,
            busy = shellState.operationInProgress,
            feedback = feedback,
            repository = gitRepository,
            onRepositoryChange = { gitRepository = it },
            onClone = { feedback = "Coming soon: Git repository cloning is not wired yet." },
            onDismiss = { gitCloneVisible = false; feedback = null },
        )
        val exportProject = shellState.projects.firstOrNull { it.id == pendingExportProjectId }
        ProjectExportDialog(
            visible = exportReviewVisible,
            projectName = exportProject?.name,
            storageLocation = exportProject?.location?.userVisiblePath
                ?.let(::humanReadableStorageLocation)
                ?: exportProject?.location?.displayLabel?.let(::humanReadableStorageLocation),
            operationReport = shellState.operationReport,
            operationInProgress = shellState.operationInProgress,
            onChooseDestination = { suggestedFileName -> exportFilePicker.launch(suggestedFileName) },
            onDismiss = { exportReviewVisible = false; pendingExportProjectId = null },
        )
        operationKind?.let { kind ->
            val project = shellState.projects.firstOrNull { it.id == operationProjectId }
            ProjectOperationDialog(
                kind = kind,
                projectName = project?.name,
                sourceLocation = project?.location?.userVisiblePath
                    ?.let(::humanReadableStorageLocation)
                    ?: project?.location?.displayLabel?.let(::humanReadableStorageLocation),
                name = operationName,
                description = operationDescription,
                destination = operationDestination?.let(::humanReadableStorageLocation),
                operationReport = shellState.operationReport,
                operationInProgress = shellState.operationInProgress,
                onNameChange = { operationName = it },
                onDescriptionChange = { operationDescription = it },
                onChooseDestination = { operationDestinationPicker.launch(null) },
                onSubmit = {
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
                },
                onDismiss = { operationKind = null },
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

private fun persistDocumentPermission(context: android.content.Context, uri: android.net.Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
