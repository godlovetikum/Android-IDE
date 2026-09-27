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
import androidx.compose.material3.CircularProgressIndicator
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
import java.net.URI
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
        if (state.operationInProgress) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                Text("Project operation in progress…")
            }
        }
        if (state.detailsLoading) Text("Refreshing project details…", color = MaterialTheme.colorScheme.secondary)
        state.operationReport?.let { report ->
            Text(
                report.message,
                color = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) {
                    MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.error,
            )
            report.recoveryHint?.let { Text("Recovery: $it", color = MaterialTheme.colorScheme.error) }
        } ?: state.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(project?.name ?: "Project Details", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = { menuOpen = true }) { Text("More") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Refresh") }, enabled = !state.operationInProgress && !state.detailsLoading, onClick = { menuOpen = false; viewModel.refreshSelectedProjectDetails() })
                DropdownMenuItem(text = { Text("Rename") }, enabled = !state.operationInProgress, onClick = { menuOpen = false; renameValue = project?.name.orEmpty(); renameVisible = true })
                DropdownMenuItem(text = { Text("Change Location") }, enabled = !state.operationInProgress, onClick = { menuOpen = false; project?.id?.let(onRelocateProject) })
                DropdownMenuItem(text = { Text("Copy & Duplicate") }, enabled = !state.operationInProgress, onClick = { menuOpen = false; project?.id?.let(onDuplicateProject) })
                DropdownMenuItem(text = { Text("Export or Share") }, enabled = !state.operationInProgress, onClick = { menuOpen = false; project?.id?.let(onExportProject) })
                DropdownMenuItem(text = { Text("Copy Storage Path") }, onClick = {
                    menuOpen = false
                    val path = project?.location?.userVisiblePath ?: project?.location?.displayLabel
                    if (path != null) {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Project storage path", path))
                            viewModel.reportStatus("Copied project storage path")
                        } else actionFeedback = "The system clipboard is unavailable."
                    } else {
                        actionFeedback = "The project storage path is unavailable. Refresh project details and try again."
                    }
                })
                DropdownMenuItem(text = { Text("Copy Remote URLs") }, onClick = {
                    menuOpen = false
                    val git = state.projectDetails?.git
                    if (state.detailsLoading) {
                        actionFeedback = "Git details are still loading. Refresh the project details and try again."
                    } else if (git == null || git.remotes.isEmpty()) {
                        actionFeedback = "No Git remote URLs are configured for this project."
                    } else {
                        val payload = git.remotes.joinToString("\n") { remote ->
                            "${remote.name}\t${safeClipboardRemote(remote.url)}"
                        }
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        if (clipboard == null) {
                            actionFeedback = "The system clipboard is unavailable."
                        } else {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Git remote URLs", payload))
                            actionFeedback = "Copied ${git.remotes.size} Git remote URL(s). Embedded URL credentials and query parameters were omitted."
                        }
                    }
                })
                DropdownMenuItem(text = { Text("Open in Editor") }, onClick = { menuOpen = false; viewModel.navigate(Surface.EDITOR) })
                DropdownMenuItem(text = { Text("Open Git") }, onClick = { menuOpen = false; viewModel.navigate(Surface.GIT) })
                DropdownMenuItem(text = { Text("Open Terminal") }, onClick = { menuOpen = false; viewModel.navigate(Surface.TERMINAL) })
                DropdownMenuItem(text = { Text("Open Browser or Preview") }, onClick = { menuOpen = false; viewModel.navigate(Surface.BROWSER) })
                DropdownMenuItem(text = { Text("Remove from Registry") }, enabled = !state.operationInProgress, onClick = { menuOpen = false; confirmRemove = true })
                DropdownMenuItem(text = { Text("Permanently Delete") }, enabled = !state.operationInProgress, onClick = { menuOpen = false; deleteCode = Random.nextInt(100, 1000).toString(); enteredDeleteCode = ""; confirmDelete = true })
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
                title = { Text("Project action") },
                text = { Text(action) },
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
            DetailLine("Project storage", availabilityLabel(details.storageCapabilities.state))
            DetailLine("Read / update", capabilityLabel(details.storageCapabilities.readable && details.storageCapabilities.writable))
            DetailLine("Create", capabilityLabel(details.storageCapabilities.canCreate))
            DetailLine("Rename", capabilityLabel(details.storageCapabilities.canRename))
            DetailLine("Delete", capabilityLabel(details.storageCapabilities.canDelete))
            DetailLine("Change observation", capabilityLabel(details.storageCapabilities.canObserveChanges))
            details.git?.currentBranch?.let { DetailLine("Git branch", it) }
        } ?: Text("Details are being inspected or are unavailable.")
    }
}

private fun safeClipboardRemote(rawUrl: String): String {
    val withoutQueryOrFragment = rawUrl.trim().substringBefore('?').substringBefore('#')
    val withoutScpPassword = withoutQueryOrFragment.replace(
        Regex("^([^/@:]+):[^/@]+@"),
        "\$1@",
    )
    val uri = runCatching { URI(withoutScpPassword) }.getOrNull()
        ?: return withoutScpPassword.replace(Regex("(?<=://)[^/@]+@"), "")
    if (uri.scheme == null || uri.host == null) {
        return withoutScpPassword.replace(Regex("(?<=://)[^/@]+@"), "")
    }
    val safeUserInfo = uri.userInfo?.takeIf { uri.scheme.equals("ssh", ignoreCase = true) && it == "git" }
    return runCatching {
        URI(uri.scheme, safeUserInfo, uri.host, uri.port, uri.path, null, null).toASCIIString()
    }.getOrDefault(withoutScpPassword.replace(Regex("(?<=://)[^/@]+@"), ""))
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value)
    }
}

private fun availabilityLabel(state: CapabilityState): String = when (state) {
    CapabilityState.SUPPORTED -> "Available through storage provider"
    CapabilityState.NOT_YET_CHECKED -> "Checking storage access"
    CapabilityState.UNSUPPORTED -> "Storage provider not supported"
    CapabilityState.UNAVAILABLE -> "Storage location unavailable"
    CapabilityState.PERMISSION_LOST -> "Permission needed"
}

private fun capabilityLabel(available: Boolean): String =
    if (available) "Available" else "Unavailable for this provider"

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
