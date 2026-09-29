package dev.android.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.android.ide.app.AppShellState
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.project.CreateProjectTemplate

@Composable
internal fun CreateNewProjectDialog(
    state: AppShellState,
    viewModel: AppShellViewModel,
    name: String,
    description: String,
    destination: String?,
    template: CreateProjectTemplate,
    reviewVisible: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDestination: () -> Unit,
    onTemplateChange: (CreateProjectTemplate) -> Unit,
    onReviewVisibleChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenProject: (String) -> Unit,
    onLocation: (String) -> String,
    validName: (String) -> Boolean,
) {
    if (!reviewVisible) {
        AlertDialog(
            onDismissRequest = { if (!state.operationInProgress) onDismiss() },
            title = { Text("Create new project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreateTemplateChoices(template, onTemplateChange, state.operationInProgress)
                    OutlinedTextField(name, onNameChange, label = { Text("Project name") }, enabled = !state.operationInProgress, singleLine = true)
                    OutlinedTextField(description, onDescriptionChange, label = { Text("Description") }, enabled = !state.operationInProgress, minLines = 2)
                    AcquisitionPicker(destination, onLocation, "Choose storage location", "Choose another location", onDestination, state.operationInProgress)
                    AcquisitionStatus(
                        message = when {
                            state.operationInProgress -> "Creating project…"
                            name.isBlank() -> "Enter a project name"
                            !validName(name) -> "Use a valid project name"
                            destination == null -> "Choose a storage location"
                            else -> "Ready"
                        },
                        busy = state.operationInProgress,
                    )
                }
            },
            confirmButton = { Button(onClick = { onReviewVisibleChange(true) }, enabled = !state.operationInProgress && validName(name) && destination != null) { Text("Review") } },
            dismissButton = { TextButton(onClick = onDismiss, enabled = !state.operationInProgress) { Text("Cancel") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = { if (!state.operationInProgress) onReviewVisibleChange(false) },
            title = { Text("Review project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(template.title, style = MaterialTheme.typography.bodyMedium)
                    if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
                    destination?.let { Text(onLocation(it), style = MaterialTheme.typography.bodySmall) }
                    AcquisitionStatus(state.operationReport?.message ?: "Ready", state.operationInProgress)
                }
            },
            confirmButton = {
                val acquiredId = state.acquiredProjectId
                Button(onClick = {
                    if (acquiredId != null) {
                        viewModel.dismissAcquisitionPrompt()
                        onOpenProject(acquiredId)
                    } else {
                        destination?.let { viewModel.createBlankProject(it, name, description, template) }
                    }
                }, enabled = !state.operationInProgress && (acquiredId != null || (validName(name) && destination != null))) {
                    Text(if (acquiredId != null) "Open project" else "Create project")
                }
            },
            dismissButton = { TextButton(onClick = { if (state.acquiredProjectId == null) onReviewVisibleChange(false) else { viewModel.dismissAcquisitionPrompt(); onDismiss() } }, enabled = !state.operationInProgress) { Text(if (state.acquiredProjectId == null) "Back" else "Close") } },
        )
    }
}

@Composable
private fun CreateTemplateChoices(selected: CreateProjectTemplate, onSelect: (CreateProjectTemplate) -> Unit, disabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Starting point", style = MaterialTheme.typography.titleSmall)
        CreateProjectTemplate.values().forEach { template ->
            TextButton(onClick = { onSelect(template) }, enabled = !disabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (selected == template) "✓ ${template.title}" else template.title)
            }
        }
    }
}

@Composable
internal fun LoadExistingProjectDialog(
    state: AppShellState,
    viewModel: AppShellViewModel,
    folderUri: String?,
    description: String,
    folderName: String,
    onDescriptionChange: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenProject: (String) -> Unit,
    onLocation: (String) -> String,
    validName: (String) -> Boolean,
) {
    val inspection = state.folderInspection
    val ready = inspection?.let { it.readable && it.writable && !it.alreadyRegistered && it.containmentVerified && !it.overlapsRegisteredProject } == true
    val acquiredId = state.acquiredProjectId
    AlertDialog(
        onDismissRequest = { if (!state.operationInProgress) onCancel() },
        title = { Text("Load an existing project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                folderUri?.let { Text(onLocation(it), style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(description, onDescriptionChange, label = { Text("Description") }, enabled = !state.operationInProgress, minLines = 2)
                AcquisitionStatus(
                    message = state.operationReport?.message ?: when {
                        state.operationInProgress -> "Loading project…"
                        inspection == null -> "Inspecting project location…"
                        !ready -> "Choose another folder"
                        else -> "Ready"
                    },
                    busy = state.operationInProgress || inspection == null,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (acquiredId != null) {
                    viewModel.dismissAcquisitionPrompt()
                    onOpenProject(acquiredId)
                } else if (folderUri != null) {
                    viewModel.importExistingFolder(folderUri, folderName, description)
                }
            }, enabled = !state.operationInProgress && (acquiredId != null || (folderUri != null && validName(folderName) && ready))) {
                Text(if (acquiredId != null) "Open project" else "Load project")
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !state.operationInProgress) { Text(if (acquiredId != null) "Close" else "Cancel") } },
    )
}

@Composable
internal fun ImportZipProjectDialog(
    state: AppShellState,
    viewModel: AppShellViewModel,
    archiveUri: String?,
    name: String,
    description: String,
    destination: String?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDestination: () -> Unit,
    onCancel: () -> Unit,
    onOpenProject: (String) -> Unit,
    onLocation: (String) -> String,
    validName: (String) -> Boolean,
) {
    val ready = archiveUri != null && validName(name) && destination != null
    val acquiredId = state.acquiredProjectId
    AlertDialog(
        onDismissRequest = { if (!state.operationInProgress) onCancel() },
        title = { Text("Import ZIP project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                archiveUri?.let { Text(onLocation(it), style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(name, onNameChange, label = { Text("Project name") }, enabled = !state.operationInProgress, singleLine = true)
                OutlinedTextField(description, onDescriptionChange, label = { Text("Description") }, enabled = !state.operationInProgress, minLines = 2)
                AcquisitionPicker(destination, onLocation, "Choose storage location", "Choose another location", onDestination, state.operationInProgress)
                AcquisitionStatus(
                    state.operationReport?.message ?: when {
                        state.operationInProgress -> "Importing project…"
                        archiveUri == null -> "Choose a ZIP archive"
                        !validName(name) -> "Enter a valid project name"
                        destination == null -> "Choose a storage location"
                        else -> "Ready"
                    },
                    state.operationInProgress,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (acquiredId != null) {
                    viewModel.dismissAcquisitionPrompt()
                    onOpenProject(acquiredId)
                } else if (archiveUri != null && destination != null) {
                    viewModel.importZip(archiveUri, destination, name, description)
                }
            }, enabled = !state.operationInProgress && (acquiredId != null || ready)) {
                Text(if (acquiredId != null) "Open project" else "Import project")
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !state.operationInProgress) { Text(if (acquiredId != null) "Close" else "Cancel") } },
    )
}

@Composable
internal fun CloneGitProjectDialog(
    visible: Boolean,
    busy: Boolean,
    feedback: String?,
    repository: String,
    onRepositoryChange: (String) -> Unit,
    onClone: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Clone Git repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(repository, onRepositoryChange, label = { Text("Repository URL") }, enabled = !busy, singleLine = true)
                feedback?.let { Text(it, color = if (it.startsWith("Coming soon")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = onClone, enabled = repository.isNotBlank() && !busy) { Text(if (busy) "Cloning…" else "Clone project") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun AcquisitionPicker(
    value: String?,
    format: (String) -> String,
    emptyLabel: String,
    selectedLabel: String,
    onPick: () -> Unit,
    disabled: Boolean,
) {
    value?.let { Text(format(it), style = MaterialTheme.typography.bodySmall) }
    Button(onClick = onPick, enabled = !disabled) { Text(if (value == null) emptyLabel else selectedLabel) }
}

@Composable
private fun AcquisitionStatus(message: String, busy: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
