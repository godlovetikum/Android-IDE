package dev.android.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport

/**
 * Single project-export review surface.
 *
 * Project export is deliberately initiated through this component regardless of
 * whether the request came from Projects, Project Details, or the editor tree.
 * The composition root owns launchers; this component owns the user-facing copy
 * and progress/feedback presentation.
 */
@Composable
fun ProjectExportDialog(
    visible: Boolean,
    projectName: String?,
    storageLocation: String?,
    operationReport: OperationReport?,
    operationInProgress: Boolean,
    onChooseDestination: (suggestedFileName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val name = projectName?.takeIf { it.isNotBlank() } ?: "project"
    AlertDialog(
        onDismissRequest = { if (!operationInProgress) onDismiss() },
        title = { Text("Export project as ZIP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                storageLocation?.takeIf { it.isNotBlank() }?.let { Text(it) }
                operationReport?.let { report ->
                    Text(
                        report.message,
                        color = if (report.outcome == OperationOutcome.COMPLETE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                if (operationInProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        Text("Exporting…")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onChooseDestination("$name.zip") },
                enabled = projectName != null && !operationInProgress,
            ) {
                Text("Choose export location")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !operationInProgress) {
                Text(if (operationInProgress) "Please wait" else "Cancel")
            }
        },
    )
}
