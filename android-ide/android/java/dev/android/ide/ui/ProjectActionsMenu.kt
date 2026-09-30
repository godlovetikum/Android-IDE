package dev.android.ide.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** One grouped project action menu shared by Projects and Project Details. */
@Composable
fun ProjectActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    onRefresh: () -> Unit,
    onChangeDisplayName: () -> Unit,
    onChangeLocation: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onCopyPath: () -> Unit,
    onCopyRemoteUrls: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenGit: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
    onRemoveFromRegistry: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDetails: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        onDetails?.let { openDetails ->
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                text = { Text("Project details") },
                enabled = enabled,
                onClick = { onDismiss(); openDetails() },
            )
        }
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            text = { Text("Refresh") },
            enabled = enabled,
            onClick = { onDismiss(); onRefresh() },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            text = { Text("Change display name") },
            enabled = enabled,
            onClick = { onDismiss(); onChangeDisplayName() },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
            text = { Text("Change location") },
            enabled = enabled,
            onClick = { onDismiss(); onChangeLocation() },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            text = { Text("Copy and duplicate") },
            enabled = enabled,
            onClick = { onDismiss(); onDuplicate() },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            text = { Text("Export or share") },
            enabled = enabled,
            onClick = { onDismiss(); onExport() },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
            text = { Text("Copy storage path") },
            enabled = enabled,
            onClick = { onDismiss(); onCopyPath() },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
            text = { Text("Open in editor") },
            enabled = enabled,
            onClick = { onDismiss(); onOpenEditor() },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null) },
            text = { Text("Open terminal") },
            enabled = enabled,
            onClick = { onDismiss(); onOpenTerminal() },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
            text = { Text("Open browser or preview") },
            enabled = enabled,
            onClick = { onDismiss(); onOpenBrowser() },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            text = { Text("Remove from registry", color = MaterialTheme.colorScheme.secondary) },
            enabled = enabled,
            onClick = { onDismiss(); onRemoveFromRegistry() },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) },
            enabled = enabled,
            onClick = { onDismiss(); onDeletePermanently() },
        )
    }
}
