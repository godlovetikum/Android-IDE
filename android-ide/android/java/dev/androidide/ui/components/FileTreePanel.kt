// android-ide/android/java/dev/androidide/ui/components/FileTreePanel.kt
//
// Sidebar file tree — shows the SAF-backed project directory tree.
// Long-pressing any node shows a context menu with file management actions.

package dev.androidide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.model.FileNode

@Composable
fun FileTreePanel(
    nodes: List<FileNode>,
    onFileClick: (String) -> Unit,
    onDirToggle: (String) -> Unit,
    onShowRenameDialog: (FileNode) -> Unit,
    onShowDeleteDialog: (FileNode) -> Unit,
    onShowCreateFileDialog: (FileNode) -> Unit,
    onShowCreateFolderDialog: (FileNode) -> Unit,
    onShowDuplicateDialog: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current

    if (nodes.isEmpty()) {
        Box(
            modifier         = modifier.padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text  = "No project open.\nTap the folder icon to open a project.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDisabled,
            )
        }
    } else {
        LazyColumn(modifier = modifier.padding(vertical = 4.dp)) {
            items(
                items = flattenTree(nodes),
                key   = { (node, _) -> node.documentUri },
            ) { (node, depth) ->
                FileTreeRow(
                    node                    = node,
                    depth                   = depth,
                    onFileClick             = onFileClick,
                    onDirToggle             = onDirToggle,
                    onShowRenameDialog      = onShowRenameDialog,
                    onShowDeleteDialog      = onShowDeleteDialog,
                    onShowCreateFileDialog  = onShowCreateFileDialog,
                    onShowCreateFolderDialog= onShowCreateFolderDialog,
                    onShowDuplicateDialog   = onShowDuplicateDialog,
                )
            }
        }
    }
}

// ── Row ────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeRow(
    node: FileNode,
    depth: Int,
    onFileClick: (String) -> Unit,
    onDirToggle: (String) -> Unit,
    onShowRenameDialog: (FileNode) -> Unit,
    onShowDeleteDialog: (FileNode) -> Unit,
    onShowCreateFileDialog: (FileNode) -> Unit,
    onShowCreateFolderDialog: (FileNode) -> Unit,
    onShowDuplicateDialog: (FileNode) -> Unit,
) {
    val colors = LocalIdeColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick     = {
                        if (node.isDirectory) onDirToggle(node.documentUri)
                        else onFileClick(node.documentUri)
                    },
                    onLongClick = { menuOpen = true },
                )
                .padding(
                    start  = (12 + depth * 14).dp,
                    end    = 8.dp,
                    top    = 3.dp,
                    bottom = 3.dp,
                ),
        ) {
            // Expand/collapse chevron or spacer
            if (node.isDirectory) {
                Icon(
                    imageVector        = if (node.isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (node.isExpanded) "Collapse" else "Expand",
                    tint               = colors.textSecondary,
                    modifier           = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
            } else {
                Spacer(Modifier.width(16.dp))
            }

            Icon(
                imageVector = when {
                    node.isDirectory && node.isExpanded -> Icons.Default.FolderOpen
                    node.isDirectory                    -> Icons.Default.Folder
                    else                                -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                tint               = if (node.isDirectory) colors.accentLight else colors.textSecondary,
                modifier           = Modifier.size(14.dp),
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text     = node.displayName,
                style    = MaterialTheme.typography.bodyMedium,
                color    = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Context menu (shown on long press)
        DropdownMenu(
            expanded          = menuOpen,
            onDismissRequest  = { menuOpen = false },
        ) {
            if (node.isDirectory) {
                DropdownMenuItem(
                    text    = { Text("Create File") },
                    onClick = { menuOpen = false; onShowCreateFileDialog(node) },
                )
                DropdownMenuItem(
                    text    = { Text("Create Folder") },
                    onClick = { menuOpen = false; onShowCreateFolderDialog(node) },
                )
                HorizontalDivider()
            } else {
                DropdownMenuItem(
                    text    = { Text("Duplicate") },
                    onClick = { menuOpen = false; onShowDuplicateDialog(node) },
                )
            }
            DropdownMenuItem(
                text    = { Text("Rename") },
                onClick = { menuOpen = false; onShowRenameDialog(node) },
            )
            DropdownMenuItem(
                text    = { Text("Delete", color = colors.error) },
                onClick = { menuOpen = false; onShowDeleteDialog(node) },
            )
        }
    }
}

// ── Tree flattening ────────────────────────────────────────────────────────────

private fun flattenTree(nodes: List<FileNode>, depth: Int = 0): List<Pair<FileNode, Int>> {
    val result = mutableListOf<Pair<FileNode, Int>>()
    for (node in nodes) {
        result += node to depth
        if (node.isDirectory && node.isExpanded) {
            result += flattenTree(node.children, depth + 1)
        }
    }
    return result
}
