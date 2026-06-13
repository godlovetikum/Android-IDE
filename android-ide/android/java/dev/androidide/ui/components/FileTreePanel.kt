// android-ide/android/java/dev/androidide/ui/components/FileTreePanel.kt
//
// Sidebar file tree — shows the SAF-backed project directory tree.
//
// Features:
//   • Root project node with its own action menu
//   • File menu: Rename, Copy Path, Copy, Cut, Delete, Select
//   • Folder menu: New File, New Folder, Import, Rename, Copy Path, Export, Copy, Cut, Paste, Delete
//   • Active file highlighting
//   • Multi-selection mode with exit button
//   • .git folder filtering (controlled by hideGitFolder)
//   • File name search panel (controlled by isSearchVisible)

package dev.androidide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.model.FileNode
import dev.androidide.viewmodel.model.FileSearchResult

@Composable
fun FileTreePanel(
    // ── Data ──────────────────────────────────────────────────────────────
    nodes: List<FileNode>,
    clipboard: FileNode?,
    clipboardIsCut: Boolean,
    projectName: String,
    activeTabDocumentUri: String?,
    hideGitFolder: Boolean,
    isMultiSelectMode: Boolean,
    selectedUris: Set<String>,
    isSearchVisible: Boolean,
    fileSearchQuery: String,
    fileSearchResults: List<FileSearchResult>,
    // ── File-level callbacks ───────────────────────────────────────────────
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
    // ── Root-level callbacks ───────────────────────────────────────────────
    onNewFileAtRoot: () -> Unit,
    onNewFolderAtRoot: () -> Unit,
    onImportFilesAtRoot: () -> Unit,
    onExportProject: () -> Unit,
    onRefresh: () -> Unit,
    onRenameProject: () -> Unit,
    onRemoveProject: () -> Unit,
    // ── Search / selection / path callbacks ───────────────────────────────
    onCopyPath: (String) -> Unit,
    onToggleNodeSelection: (String) -> Unit,
    onExitSelectionMode: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFileSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current

    when {
        // ── File-name search results panel ─────────────────────────────────
        isSearchVisible -> {
            Column(modifier = modifier) {
                OutlinedTextField(
                    value         = fileSearchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier      = Modifier.fillMaxWidth().padding(8.dp),
                    placeholder   = { Text("Search files…", style = MaterialTheme.typography.bodySmall) },
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    textStyle     = MaterialTheme.typography.bodySmall,
                )
                if (fileSearchResults.isEmpty() && fileSearchQuery.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            text  = "No files matching \u201c$fileSearchQuery\u201d",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDisabled,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(fileSearchResults, key = { it.documentUri }) { result ->
                            SearchResultRow(result = result, onSelect = onSearchFileSelect)
                        }
                    }
                }
            }
        }

        // ── Empty state ────────────────────────────────────────────────────
        nodes.isEmpty() -> {
            Box(
                modifier         = modifier.padding(16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text  = "No project open.\nOpen or create a project to see files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDisabled,
                )
            }
        }

        // ── File tree ──────────────────────────────────────────────────────
        else -> {
            val filteredNodes = if (hideGitFolder) nodes.filterNot { it.displayName == ".git" } else nodes
            LazyColumn(modifier = modifier) {
                // Exit selection mode banner
                if (isMultiSelectMode) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.activeHighlight)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text     = "${selectedUris.size} selected",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = colors.accent,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onExitSelectionMode, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector        = Icons.Default.Close,
                                    contentDescription = "Exit selection mode",
                                    tint               = colors.accent,
                                    modifier           = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // Root project node
                if (projectName.isNotEmpty()) {
                    item {
                        RootProjectNode(
                            projectName    = projectName,
                            onNewFile      = onNewFileAtRoot,
                            onNewFolder    = onNewFolderAtRoot,
                            onImportFiles  = onImportFilesAtRoot,
                            onExport       = onExportProject,
                            onRename       = onRenameProject,
                            onRemove       = onRemoveProject,
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = colors.separator)
                    }
                }

                // File tree items
                items(
                    items = flattenTree(filteredNodes),
                    key   = { (node, _) -> node.documentUri },
                ) { (node, depth) ->
                    FileTreeRow(
                        node                     = node,
                        depth                    = depth,
                        clipboard                = clipboard,
                        clipboardIsCut           = clipboardIsCut,
                        isActive                 = node.documentUri == activeTabDocumentUri,
                        isSelected               = node.documentUri in selectedUris,
                        isMultiSelectMode        = isMultiSelectMode,
                        onFileClick              = onFileClick,
                        onDirToggle              = onDirToggle,
                        onShowRenameDialog       = onShowRenameDialog,
                        onShowDeleteDialog       = onShowDeleteDialog,
                        onShowCreateFileDialog   = onShowCreateFileDialog,
                        onShowCreateFolderDialog = onShowCreateFolderDialog,
                        onCopyNode               = onCopyNode,
                        onCutNode                = onCutNode,
                        onPasteInto              = onPasteInto,
                        onImportFilesAt          = onImportFilesAt,
                        onExportDirectory        = onExportDirectory,
                        onCopyPath               = onCopyPath,
                        onSelect                 = onToggleNodeSelection,
                    )
                }
            }
        }
    }
}

// ── Search result row ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    result: FileSearchResult,
    onSelect: (String) -> Unit,
) {
    val colors = LocalIdeColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onSelect(result.documentUri) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint               = colors.textSecondary,
            modifier           = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = result.displayName,
                style    = MaterialTheme.typography.bodySmall,
                color    = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text     = result.relativePath,
                style    = MaterialTheme.typography.labelSmall,
                color    = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Root project node ──────────────────────────────────────────────────────────

@Composable
private fun RootProjectNode(
    projectName: String,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors     = LocalIdeColors.current
    var menuOpen   by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 0.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.FolderOpen,
            contentDescription = null,
            tint               = colors.accentLight,
            modifier           = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text     = projectName,
            style    = MaterialTheme.typography.labelMedium,
            color    = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector        = Icons.Default.MoreVert,
                    contentDescription = "Project actions",
                    tint               = colors.textDisabled,
                    modifier           = Modifier.size(14.dp),
                )
            }
            DropdownMenu(
                expanded         = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text    = { Text("New File") },
                    onClick = { menuOpen = false; onNewFile() },
                )
                DropdownMenuItem(
                    text    = { Text("New Folder") },
                    onClick = { menuOpen = false; onNewFolder() },
                )
                DropdownMenuItem(
                    text    = { Text("Import Files") },
                    onClick = { menuOpen = false; onImportFiles() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text    = { Text("Export Project\u2026") },
                    onClick = { menuOpen = false; onExport() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text    = { Text("Rename Project") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text    = { Text("Remove from List", color = LocalIdeColors.current.error) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
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
    isActive: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onFileClick: (String) -> Unit,
    onDirToggle: (String) -> Unit,
    onShowRenameDialog: (FileNode) -> Unit,
    onShowDeleteDialog: (FileNode) -> Unit,
    onShowCreateFileDialog: (FileNode) -> Unit,
    onShowCreateFolderDialog: (FileNode) -> Unit,
    onCopyNode: (FileNode) -> Unit,
    onCutNode: (FileNode) -> Unit,
    onPasteInto: (FileNode) -> Unit,
    onImportFilesAt: (FileNode) -> Unit,
    onExportDirectory: (FileNode) -> Unit,
    onCopyPath: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors   = LocalIdeColors.current
    var menuOpen by remember { mutableStateOf(false) }

    val rowBackground = when {
        isSelected -> colors.activeHighlight
        isActive   -> colors.activeHighlight.copy(alpha = 0.6f)
        else       -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) onSelect(node.documentUri)
                    else if (node.isDirectory) onDirToggle(node.documentUri)
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
        // Multi-select checkbox
        if (isMultiSelectMode) {
            Icon(
                imageVector        = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "Deselect" else "Select",
                tint               = if (isSelected) colors.accent else colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        } else {
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
            color    = if (isActive) colors.accent else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // ••• context menu
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
                    // ── Folder menu ──────────────────────────────────────
                    DropdownMenuItem(
                        text    = { Text("New File") },
                        onClick = { menuOpen = false; onShowCreateFileDialog(node) },
                    )
                    DropdownMenuItem(
                        text    = { Text("New Folder") },
                        onClick = { menuOpen = false; onShowCreateFolderDialog(node) },
                    )
                    DropdownMenuItem(
                        text    = { Text("Import Files") },
                        onClick = { menuOpen = false; onImportFilesAt(node) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text    = { Text("Rename") },
                        onClick = { menuOpen = false; onShowRenameDialog(node) },
                    )
                    DropdownMenuItem(
                        text    = { Text("Copy Path") },
                        onClick = { menuOpen = false; onCopyPath(node.documentUri) },
                    )
                    DropdownMenuItem(
                        text    = { Text("Export\u2026") },
                        onClick = { menuOpen = false; onExportDirectory(node) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text    = { Text("Copy") },
                        onClick = { menuOpen = false; onCopyNode(node) },
                    )
                    DropdownMenuItem(
                        text    = { Text("Cut") },
                        onClick = { menuOpen = false; onCutNode(node) },
                    )
                    if (clipboard != null) {
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
                        text    = { Text("Delete", color = LocalIdeColors.current.error) },
                        onClick = { menuOpen = false; onShowDeleteDialog(node) },
                    )
                } else {
                    // ── File menu ────────────────────────────────────────
                    DropdownMenuItem(
                        text    = { Text("Rename") },
                        onClick = { menuOpen = false; onShowRenameDialog(node) },
                    )
                    DropdownMenuItem(
                        text    = { Text("Copy Path") },
                        onClick = { menuOpen = false; onCopyPath(node.documentUri) },
                    )
                    HorizontalDivider()
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
                        text    = { Text("Delete", color = LocalIdeColors.current.error) },
                        onClick = { menuOpen = false; onShowDeleteDialog(node) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text    = { Text("Select") },
                        onClick = { menuOpen = false; onSelect(node.documentUri) },
                    )
                }
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
