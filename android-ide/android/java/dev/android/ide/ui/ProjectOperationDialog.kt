package dev.android.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport

internal enum class ProjectOperationKind { DUPLICATE, RELOCATE }

/** Shared review surface for project duplication and storage relocation. */
@Composable
internal fun ProjectOperationDialog(
    kind: ProjectOperationKind,
    projectName: String?,
    sourceLocation: String?,
    name: String,
    description: String,
    destination: String?,
    operationReport: OperationReport?,
    operationInProgress: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onChooseDestination: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val completed = operationReport?.outcome == OperationOutcome.COMPLETE && !operationInProgress
    AlertDialog(
        onDismissRequest = { if (!operationInProgress) onDismiss() },
        title = { Text(if (completed) "Operation complete" else if (kind == ProjectOperationKind.DUPLICATE) "Copy and duplicate project" else "Change project location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (completed) {
                    Text(operationReport!!.message, color = MaterialTheme.colorScheme.primary)
                } else {
                    sourceLocation?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text(if (kind == ProjectOperationKind.DUPLICATE) "Project name" else "Display name") }, enabled = !operationInProgress, singleLine = true)
                    if (kind == ProjectOperationKind.DUPLICATE) OutlinedTextField(value = description, onValueChange = onDescriptionChange, label = { Text("Description") }, enabled = !operationInProgress)
                    destination?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = onChooseDestination, enabled = !operationInProgress) { Text(if (destination == null) "Choose destination" else "Choose another location") }
                    operationReport?.let { report -> Text(report.message, color = MaterialTheme.colorScheme.error) }
                    if (operationInProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier, strokeWidth = 2.dp)
                            Text("Working…")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = if (completed) onDismiss else onSubmit, enabled = !operationInProgress && (completed || (projectName != null && name.isNotBlank() && destination != null))) {
                Text(if (completed) "Done" else if (operationInProgress) "Working…" else if (kind == ProjectOperationKind.DUPLICATE) "Copy and duplicate" else "Change location")
            }
        },
        dismissButton = {
            if (!completed) TextButton(onClick = onDismiss, enabled = !operationInProgress) { Text(if (operationInProgress) "Please wait" else "Cancel") }
        },
    )
}
