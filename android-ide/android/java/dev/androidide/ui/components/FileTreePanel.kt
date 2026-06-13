// android-ide/android/java/dev/androidide/ui/components/FileTreePanel.kt
//
// Sidebar file tree — shows the SAF-backed project directory tree.
// Long-pressing any node OR tapping ••• shows a context menu with file
// management actions including Copy / Cut / Paste / Import / Export.
// A root project actions header row sits above the tree nodes.

package dev.androidide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
    clipboard: FileNode?,
    clipboardIsCut: Boolean,
    onFileClick: (String) -> Unit,
    onDirToggle: (String) -> Unit,
    onShowRenameDialog: (FileNode) -> Unit,
    onShowDeleteDialog: (FileNode) -> Unit,
    onShowCreateFileDialog: (FileNode) -> Unit,
    onShowCreateFolderDialog: (FileNode) -> Unit,
    onShowDuplicateDialog: (FileNode) -> Unit,
    onCopyNode: (FileNode) -> Unit,
    onCutNode: (FileNode) -> Unit,
    onPasteInto: (FileNode) -> Unit,
    onImportFilesAt: (FileNode) -> Unit,
    onExportDirectory: (FileNode) -> Unit,
    onNewFileAtRoot: () -> Unit,
    onNewFolderAtRoot: () -> Unit,
    onImportFilesAtRoot: () -> Unit,
    onExportProject: () -> Unit,
    onRefresh: () -> Unit,
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
        LazyColumn(modifier = modifier) {
            // Root project actions header
            item {
                ProjectActionsHeader(
                    clipboard            = clipboard,
                    clipboardIsCut       = clipboardIsCut,
                    onNewFile            = onNewFileAtRoot,
                    onNewFolder          = onNewFolderAtRoot,
                    onImportFiles        = onImportFilesAtRoot,
                    onExport             = onExportProject,
                    onRefresh            = onRefresh,
                )
            }

            items(
                items = flattenTree(nodes),
                key   = { (node, _) -> node.documentUri },
            ) { (node, depth) ->
                FileTreeRow(
                    node                     = node,
                    depth                    = depth,
                    clipboard                = clipboard,
                    clipboardIsCut           = clipboardIsCut,
                    onFileClick              = onFileClick,
                    onDirToggle              = onDirToggle,
                    onShowRenameDialog       = onShowRenameDialog,
                    onShowDeleteDialog       = onShowDeleteDialog,
                    onShowCreateFileDialog   = onShowCreateFileDialog,
                    onShowCreateFolderDialog = onShowCreateFolderDialog,
                    onShowDuplicateDialog    = onShowDuplicateDialog,
                    onCopyNode               = onCopyNode,
                    onCutNode                = onCutNode,
                    onPasteInto              = onPasteInto,
                    onImportFilesAt          = onImportFilesAt,
                    onExportDirectory        = onExportDirectory,
                )
            }
        }
    }
}

// ── Root project actions header ────────────────────────────────────────────────

@Composable
private fun ProjectActionsHeader(
    clipboard: FileNode?,
    clipboardIsCut: Boolean,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onExport: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalIdeColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        IconButton(onClick = onNewFile, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = "New file",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onNewFolder, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector        = Icons.Default.CreateNewFolder,
                contentDescription = "New folder",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onImportFiles, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector        = Icons.Default.FileUpload,
                contentDescription = "Import files",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onExport, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector        = Icons.Default.FileDownload,
                contentDescription = "Export project",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector        = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}

// ── Tree row ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeRow(
    node: FileNode,
    depth: Int,
    clipboard: FileNode?,
    clipboardIsCut: Boolean,
    onFileClick: (String) -> Unit,
    onDirToggle: (String) -> Unit,
    onShowRenameDialog: (FileNode) -> Unit,
    onShowDeleteDialog: (FileNode) -> Unit,
    onShowCreateFileDialog: (FileNode) -> Unit,
    onShowCreateFolderDialog: (FileNode) -> Unit,
    onShowDuplicateDialog: (FileNode) -> Unit,
    onCopyNode: (FileNode) -> Unit,
    onCutNode: (FileNode) -> Unit,
    onPasteInto: (FileNode) -> Unit,
    onImportFilesAt: (FileNode) -> Unit,
    onExportDirectory: (FileNode) -> Unit,
) {
    val colors   = LocalIdeColors.current
    var menuOpen by remember { mutableStateOf(false) }

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
                start  = (8 + depth * 14).dp,
                end    = 0.dp,
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
            modifier = Modifier.weight(1f),
        )

        // ••• button with context menu
        Box {
            IconButton(
                onClick  = { menuOpen = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.MoreVert,
                    contentDescription = "File options",
                    tint               = colors.textDisabled,
                    modifier           = Modifier.size(14.dp),
                )
            }

            DropdownMenu(
                expanded         = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                if (node.isDirectory) {
                    DropdownMenuItem(
                        text    = { Text("New File Here") },
                        onClick = { menuOpen = false; onShowCreateFileDialog(node) },
                    )
                    DropdownMenuItem(
                        text    = { Text("New Folder Here") },
                        onClick = { menuOpen = false; onShowCreateFolderDialog(node) },
                    )
                    DropdownMenuItem(
                        text    = { Text("Import Files Here") },
                        onClick = { menuOpen = false; onImportFilesAt(node) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text    = { Text("Duplicate") },
                        onClick = { menuOpen = false; onShowDuplicateDialog(node) },
                    )
                    if (clipboard != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                val op = if (clipboardIsCut) "Move" else "Paste Copy of"
                                Text("$op \u201c${clipboard.displayName}\u201d here")
                            },
                            onClick = { menuOpen = false; onPasteInto(node) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text    = { Text("Export\u2026") },
                        onClick = { menuOpen = false; onExportDirectory(node) },
                    )
                    HorizontalDivider()
                } else {
                    DropdownMenuItem(
                        text    = { Text("Duplicate") },
                        onClick = { menuOpen = false; onShowDuplicateDialog(node) },
                    )
                }
                DropdownMenuItem(
                    text    = { Text("Copy") },
                    onClick = { menuOpen = false; onCopyNode(node) },
                )
                DropdownMenuItem(
                    text    = { Text("Cut") },
                    onClick = { menuOpen = false; onCutNode(node) },
                )
                HorizontalDivider()
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
