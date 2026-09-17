package dev.androidide.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.androidide.data.model.GitDetails
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.model.IdeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailsScreen(
    uiState: IdeUiState,
    onBack: () -> Unit,
    onDuplicate: (String) -> Unit = {},
    onExport: (String) -> Unit = {},
    onMove: (String) -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    onRemove: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
) {
    val colors = LocalIdeColors.current
    val details = uiState.projectDetails
    val clipboard = LocalClipboardManager.current
    var actionsOpen by remember(details?.project?.uri) { mutableStateOf(false) }
    var renameOpen by remember(details?.project?.uri) { mutableStateOf(false) }
    var renameText by remember(details?.project?.uri) { mutableStateOf(details?.project?.name.orEmpty()) }
    Scaffold(topBar = {
        SmallTopAppBar(
            title = { Text(details?.project?.name ?: "Project details") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = {
                if (details != null) {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { actionsOpen = true }) {
                            Icon(Icons.Default.MoreVert, "Project actions")
                        }
                        DropdownMenu(
                            expanded = actionsOpen,
                            onDismissRequest = { actionsOpen = false },
                        ) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                actionsOpen = false
                                renameText = details.project.name
                                renameOpen = true
                            })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                actionsOpen = false
                                onDuplicate(details.project.uri)
                            })
                            DropdownMenuItem(text = { Text("Export") }, onClick = {
                                actionsOpen = false
                                onExport(details.project.uri)
                            })
                            DropdownMenuItem(text = { Text("Move") }, onClick = {
                                actionsOpen = false
                                onMove(details.project.uri)
                            })
                            DropdownMenuItem(text = { Text("Remove from registry") }, onClick = {
                                actionsOpen = false
                                onRemove(details.project.uri)
                            })
                            DropdownMenuItem(text = { Text("Delete permanently") }, onClick = {
                                actionsOpen = false
                                onDelete(details.project.uri)
                            })
                        }
                    }
                }
            },
        )
    }) { padding ->
        when {
            uiState.projectDetailsLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = colors.accent)
                Text("Loading project details…", color = colors.textSecondary, modifier = Modifier.padding(top = 12.dp))
            }
            details == null -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Project details are unavailable", color = colors.textSecondary)
                TextButton(onClick = onBack) { Text("Back") }
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailSection("Identity") {
                    CopyableDetail("Display name", details.project.name, clipboard::setText)
                    DetailValue("Description", details.description.ifBlank { "No description" })
                    CopyableDetail("Storage provider", details.storageProvider, clipboard::setText)
                    CopyableDetail("Location", details.storagePath, clipboard::setText)
                }
                DetailSection("Timeline") {
                    DetailValue("Created", formatDate(details.creationTimeMs))
                    DetailValue("Last modified", formatDate(details.lastModifiedTimeMs))
                    DetailValue("Last opened", formatDate(details.project.lastOpenedMs))
                }
                DetailSection("Contents") {
                    DetailValue("Files", details.fileCount.toString())
                    DetailValue("Folders", details.folderCount.toString())
                    DetailValue("Total size", formatBytes(details.totalBytes))
                }
                DetailSection("Source languages") {
                    if (details.languageBytes.isEmpty()) DetailValue("Detected languages", "None")
                    else {
                        val total = details.languageBytes.values.sum().coerceAtLeast(1L)
                        details.languageBytes.entries.sortedByDescending { it.value }.forEach { (language, bytes) ->
                            DetailValue(language, "${formatBytes(bytes)} · ${(bytes * 100 / total)}%")
                        }
                    }
                }
                GitSection(details.git, clipboard::setText)
            }
        }
    }
    if (renameOpen && details != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename project") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Display name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(details.project.uri, renameText.trim())
                        renameOpen = false
                    },
                    enabled = renameText.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = LocalIdeColors.current.textSecondary)
        Text(value, color = LocalIdeColors.current.textPrimary)
    }
}

@Composable
private fun CopyableDetail(label: String, value: String, onCopy: (AnnotatedString) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = LocalIdeColors.current.textSecondary)
            Text(value, color = LocalIdeColors.current.textPrimary)
        }
        IconButton(onClick = { onCopy(AnnotatedString(value)) }) {
            Icon(Icons.Default.ContentCopy, "Copy $label")
        }
    }
}

@Composable
private fun GitSection(git: GitDetails?, onCopy: (AnnotatedString) -> Unit) {
    DetailSection("Git") {
        if (git == null) DetailValue("Repository", "Not detected")
        else {
            CopyableDetail("Current branch", git.currentBranch ?: "Detached HEAD", onCopy)
            CopyableDetail("HEAD commit", git.headCommit ?: "Unknown", onCopy)
            DetailValue("Branches", git.branches.size.toString())
            DetailValue("Remotes", git.remotes.size.toString())
            git.latestCommitMessage?.let { DetailValue("Latest commit", it) }
        }
    }
}

private fun formatDate(timestampMs: Long?): String = timestampMs?.let {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
} ?: "Unknown"

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
