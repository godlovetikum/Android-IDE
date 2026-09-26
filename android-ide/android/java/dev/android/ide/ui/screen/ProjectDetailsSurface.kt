package dev.android.ide.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.android.ide.app.AppShellState
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.Surface
import kotlin.random.Random

@Composable
fun ProjectDetailsSurface(
    state: AppShellState,
    viewModel: AppShellViewModel,
    onExportProject: (String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onRelocateProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = state.projects.firstOrNull { it.id == state.selectedProjectId }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renameVisible by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(project?.name.orEmpty()) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var deleteCode by remember { mutableStateOf(Random.nextInt(100, 1000).toString()) }
    var enteredDeleteCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(project?.name ?: "Project Details", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = { menuOpen = true }) { Text("More") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; viewModel.refreshSelectedProjectDetails() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameValue = project?.name.orEmpty(); renameVisible = true })
                DropdownMenuItem(text = { Text("Change Location") }, onClick = { menuOpen = false; project?.id?.let(onRelocateProject) })
                DropdownMenuItem(text = { Text("Copy & Duplicate") }, onClick = { menuOpen = false; project?.id?.let(onDuplicateProject) })
                DropdownMenuItem(text = { Text("Export or Share") }, onClick = { menuOpen = false; project?.id?.let(onExportProject) })
                DropdownMenuItem(text = { Text("Copy Storage Path") }, onClick = {
                    menuOpen = false
                    val path = project?.location?.userVisiblePath ?: project?.location?.displayLabel
                    if (path != null) {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Project storage path", path))
                        viewModel.reportStatus("Copied project storage path")
                    } else {
                        actionFeedback = "Copy Storage Path"
                    }
                })
                DropdownMenuItem(text = { Text("Copy Remote URLs") }, onClick = { menuOpen = false; actionFeedback = "Copy Remote URLs" })
                DropdownMenuItem(text = { Text("Open in Editor") }, onClick = { menuOpen = false; viewModel.navigate(Surface.EDITOR) })
                DropdownMenuItem(text = { Text("Open Git") }, onClick = { menuOpen = false; viewModel.navigate(Surface.GIT) })
                DropdownMenuItem(text = { Text("Open Terminal") }, onClick = { menuOpen = false; viewModel.navigate(Surface.TERMINAL) })
                DropdownMenuItem(text = { Text("Open Browser or Preview") }, onClick = { menuOpen = false; viewModel.navigate(Surface.BROWSER) })
                DropdownMenuItem(text = { Text("Remove from Registry") }, onClick = { menuOpen = false; confirmRemove = true })
                DropdownMenuItem(text = { Text("Permanently Delete") }, onClick = { menuOpen = false; deleteCode = Random.nextInt(100, 1000).toString(); enteredDeleteCode = ""; confirmDelete = true })
            }
        }
        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("Remove project from registry?") },
                text = { Text("${project?.name ?: "This project"} will be removed from Android IDE, but its files, Git data, and location will remain unchanged.\n\nLocation: ${project?.location?.userVisiblePath ?: project?.location?.displayLabel ?: "Unavailable"}") },
                confirmButton = { TextButton(onClick = { confirmRemove = false; viewModel.removeSelectedProject() }) { Text("Remove from Registry") } },
                dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
            )
        }
        if (renameVisible) {
            AlertDialog(
                onDismissRequest = { renameVisible = false },
                title = { Text("Rename project") },
                text = { androidx.compose.material3.OutlinedTextField(renameValue, { renameValue = it }, label = { Text("Project name") }, singleLine = true) },
                confirmButton = { TextButton(onClick = { renameVisible = false; viewModel.renameSelectedProject(renameValue) }, enabled = renameValue.isNotBlank()) { Text("Rename") } },
                dismissButton = { TextButton(onClick = { renameVisible = false }) { Text("Cancel") } },
            )
        }
        actionFeedback?.let { action ->
            AlertDialog(
                onDismissRequest = { actionFeedback = null },
                title = { Text(action) },
                text = { Text("Coming soon (phase 6)") },
                confirmButton = { TextButton(onClick = { actionFeedback = null }) { Text("OK") } },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Permanently delete ${project?.name ?: "this project"}?") },
                text = {
                    Column {
                        Text("This permanently removes the project data from its selected storage location. This action cannot be undone.")
                        Text("Project: ${project?.name ?: "Unavailable"}")
                        Text("Location: ${project?.location?.userVisiblePath ?: project?.location?.displayLabel ?: "Unavailable"}")
                        Text("Type $deleteCode to confirm")
                        androidx.compose.material3.OutlinedTextField(enteredDeleteCode, { enteredDeleteCode = it }, label = { Text("Confirmation code") }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; viewModel.permanentlyDeleteSelectedProject() }, enabled = enteredDeleteCode == deleteCode) { Text("Delete permanently") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
        Text(project?.description?.ifBlank { "No description" } ?: "No project selected")
        Text(project?.location?.userVisiblePath ?: project?.location?.displayLabel ?: "Location unavailable")
        state.projectDetails?.let { details ->
            DetailLine("Files", details.fileCount.toString())
            DetailLine("Folders", details.folderCount.toString())
            DetailLine("Size", formatBytes(details.totalBytes))
            DetailLine("Provider", details.storageProvider)
            DetailLine("Availability", availabilityLabel(details.project.capabilityState))
            details.git?.currentBranch?.let { DetailLine("Git branch", it) }
        } ?: Text("Details are being inspected or are unavailable.")
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value)
    }
}

private fun availabilityLabel(state: CapabilityState): String = when (state) {
    CapabilityState.SUPPORTED -> "Available"
    CapabilityState.NOT_YET_CHECKED -> "Checking availability"
    CapabilityState.UNSUPPORTED -> "Not supported"
    CapabilityState.UNAVAILABLE -> "Unavailable"
    CapabilityState.PERMISSION_LOST -> "Permission needed"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return "%.1f %s".format(value, units[index])
}
