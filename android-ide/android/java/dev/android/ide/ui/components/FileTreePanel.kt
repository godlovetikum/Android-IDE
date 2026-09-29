// android-ide/android/java/dev/android/ide/ui/components/FileTreePanel.kt
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
//   • Project-content search panel (controlled by isSearchVisible)
//
// Clipboard:
//   clipboardItems: List<FileNode> replaces the old clipboard: FileNode? to support
//   multi-item cut/copy from multi-select mode. The paste menu item is shown
//   whenever the clipboard is non-empty.

package dev.android.ide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.android.ide.editor.EditorLanguageRegistry
import dev.android.ide.editor.FileIconKind
import dev.android.ide.ui.theme.LocalIdeColors
import dev.android.ide.viewmodel.model.FileNode
import dev.android.ide.viewmodel.model.FileSearchResult
import dev.android.ide.viewmodel.model.ancestorsOf

@Composable
fun FileTreePanel(
    // ── Data ──────────────────────────────────────────────────────────────
    nodes: List<FileNode>,
    /** Files/folders pending paste. Empty = clipboard is clear. */
    clipboardItems: List<FileNode>,
    clipboardIsCut: Boolean,
    projectName: String,
    activeTabDocumentUri: String?,
    locateTargetUri: String?,
    locateRequestToken: Long,
    onLocateConsumed: () -> Unit,
    hideGitFolder: Boolean,
    isMultiSelectMode: Boolean,
    selectedUris: Set<String>,
    isSearchVisible: Boolean,
    isContentSearchVisible: Boolean,
    fileSearchQuery: String,
    fileSearchResults: List<FileSearchResult>,
    contentSearchQuery: String,
    contentSearchResults: List<FileSearchResult>,
    // ── File-level callbacks ───────────────────────────────────────────────
    onFileClick: (String) -> Unit,
    onFileDoubleClick: (String) -> Unit,
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
    onShowProjectDetails: () -> Unit,
    onDeleteProject: () -> Unit,
    onRemoveProject: () -> Unit,
    onPasteAtRoot: () -> Unit,
    // ── Search / selection / path callbacks ───────────────────────────────
    onCopyPath: (String) -> Unit,
    onToggleNodeSelection: (String) -> Unit,
    onExitSelectionMode: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onContentSearchQueryChange: (String) -> Unit,
    onReplaceProjectContents: (String, String) -> Unit,
    onHideFileSearch: () -> Unit,
    onHideContentSearch: () -> Unit,
    onSearchFileSelect: (FileSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current
    val treeListState = remember { LazyListState() }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeTrailingPadding = with(density) { (imeBottomPx * 1.2f).toDp() }

    when {
        // ── Filename and project-content search panels ──────────────────────
        isSearchVisible || isContentSearchVisible -> {
            val query = if (isContentSearchVisible) contentSearchQuery else fileSearchQuery
            val results = if (isContentSearchVisible) contentSearchResults else fileSearchResults
            var replaceOpen by rememberSaveable(isContentSearchVisible) { mutableStateOf(false) }
            var replaceQuery by rememberSaveable { mutableStateOf("") }
            Column(modifier = modifier) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value         = query,
                        onValueChange = if (isContentSearchVisible) onContentSearchQueryChange else onSearchQueryChange,
                        modifier      = Modifier.weight(1f),
                        placeholder   = { Text(if (isContentSearchVisible) "Find in project…" else "Find filenames…", style = MaterialTheme.typography.bodyMedium) },
                        singleLine    = true,
                        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        textStyle     = MaterialTheme.typography.bodyMedium,
                    )
                    if (isContentSearchVisible) {
                        TextButton(onClick = { replaceOpen = !replaceOpen }) {
                            Text(if (replaceOpen) "Hide replace" else "Replace")
                        }
                    }
                }
                if (isContentSearchVisible && replaceOpen) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Replace with…") },
                            singleLine = true,
                        )
                        Button(
                            onClick = { onReplaceProjectContents(query, replaceQuery) },
                            enabled = query.isNotBlank() && results.isNotEmpty(),
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("Replace all") }
                    }
                }
                if (results.isEmpty() && query.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            text  = if (isContentSearchVisible) "No project content matching \u201c$query\u201d" else "No filenames matching \u201c$query\u201d",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textDisabled,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = imeTrailingPadding),
                    ) {
                        items(results, key = { it.documentUri }) { result ->
                            SearchResultRow(result = result, onSelect = onSearchFileSelect)
                        }
                    }
                }
                TextButton(
                    onClick = if (isContentSearchVisible) onHideContentSearch else onHideFileSearch,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("Close search") }
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
            val flatNodes = flattenTree(filteredNodes)
            val activeAncestorUris = activeTabDocumentUri
                ?.let { nodes.ancestorsOf(it).orEmpty().map(FileNode::documentUri).toSet() }
                .orEmpty()
            LaunchedEffect(locateRequestToken, locateTargetUri, flatNodes) {
                val targetIndex = locateTargetUri?.let { uri ->
                    flatNodes.indexOfFirst { it.first.documentUri == uri }
                } ?: -1
                val headerItems = (if (isMultiSelectMode) 1 else 0) +
                    (if (projectName.isNotEmpty()) 1 else 0)
                if (targetIndex >= 0) {
                    treeListState.animateScrollToItem(targetIndex + headerItems)
                    onLocateConsumed()
                }
            }
            LazyColumn(
                state = treeListState,
                modifier = modifier,
                contentPadding = PaddingValues(bottom = imeTrailingPadding),
            ) {
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
                            isActivePath   = activeTabDocumentUri != null,
                            clipboardItems = clipboardItems,
                            onNewFile      = onNewFileAtRoot,
                            onNewFolder    = onNewFolderAtRoot,
                            onImportFiles  = onImportFilesAtRoot,
                            onExport       = onExportProject,
                            onShowDetails  = onShowProjectDetails,
                            onDelete       = onDeleteProject,
                            onRemove       = onRemoveProject,
                            onPasteAtRoot  = onPasteAtRoot,
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = colors.separator)
                    }
                }

                // File tree items
                    items(
                    items = flatNodes,
                    key   = { (node, _) -> node.documentUri },
                ) { (node, depth) ->
                    FileTreeRow(
                        node                     = node,
                        depth                    = depth,
                        clipboardItems           = clipboardItems,
                        clipboardIsCut            = clipboardIsCut,
                        isActive                 = node.documentUri == activeTabDocumentUri,
                        isActiveAncestor         = node.documentUri in activeAncestorUris,
                        isSelected               = node.documentUri in selectedUris,
                        isMultiSelectMode        = isMultiSelectMode,
                        onFileClick              = onFileClick,
                        onFileDoubleClick        = onFileDoubleClick,
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
                        onShowDuplicateDialog    = onShowDuplicateDialog,
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
    onSelect: (FileSearchResult) -> Unit,
) {
    val colors = LocalIdeColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onSelect(result) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        FileTypeBadge(result.displayName, muted = false, accent = false)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = result.displayName,
                style    = MaterialTheme.typography.bodyMedium,
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
            result.matchLine?.let { line ->
                Text(
                    text = "Line $line",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                )
            }
            if (result.matchPreview.isNotBlank()) {
                Text(
                    text = result.matchPreview,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Root project node ──────────────────────────────────────────────────────────

@Composable
private fun RootProjectNode(
    projectName: String,
    isActivePath: Boolean,
    clipboardItems: List<FileNode>,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onExport: () -> Unit,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit,
    onRemove: () -> Unit,
    onPasteAtRoot: () -> Unit,
) {
    val colors     = LocalIdeColors.current
    var menuOpen   by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActivePath) colors.activeHighlight.copy(alpha = 0.22f) else Color.Transparent)
            .padding(start = 8.dp, end = 0.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.FolderOpen,
            contentDescription = null,
            tint               = if (isActivePath) colors.accent else colors.accentLight,
            modifier           = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text     = projectName,
            style    = MaterialTheme.typography.labelMedium,
            color    = if (isActivePath) colors.accent else colors.textPrimary,
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
            EditorProjectActionsMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onNewFile = onNewFile,
                onNewFolder = onNewFolder,
                onImportFiles = onImportFiles,
                onExportProject = onExport,
                onRefresh = onRefresh,
                onShowDetails = onShowDetails,
                onDeleteProject = onDelete,
                onRemoveProject = onRemove,
                onPasteAtRoot = onPasteAtRoot.takeIf { clipboardItems.isNotEmpty() },
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
    clipboardItems: List<FileNode>,
    clipboardIsCut: Boolean,
    isActive: Boolean,
    isActiveAncestor: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onFileClick: (String) -> Unit,
    onFileDoubleClick: (String) -> Unit,
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
    onShowDuplicateDialog: (FileNode) -> Unit,
) {
    val colors   = LocalIdeColors.current
    var menuOpen by remember { mutableStateOf(false) }

    // Node is "pending paste" if it appears in the clipboard.
    val isInClipboard = clipboardItems.any { it.documentUri == node.documentUri }

    val rowBackground = when {
        isSelected     -> colors.activeHighlight
        isActive       -> colors.activeHighlight.copy(alpha = 0.6f)
        isActiveAncestor -> colors.activeHighlight.copy(alpha = 0.22f)
        else           -> Color.Transparent
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
                onDoubleClick = {
                    if (!isMultiSelectMode && !node.isDirectory) onFileDoubleClick(node.documentUri)
                },
                onLongClick = { menuOpen = true },
            )
            .padding(end = 0.dp, top = 3.dp, bottom = 3.dp),
    ) {
        repeat(depth) { level ->
            Canvas(Modifier.width(16.dp).height(36.dp)) {
                drawLine(
                    color = if (isActive || isActiveAncestor) colors.accent.copy(alpha = 0.7f) else colors.separator,
                    start = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                    strokeWidth = if ((isActive || isActiveAncestor) && level == depth - 1) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
        }

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
            Spacer(Modifier.width(4.dp))
        }

        // Use one leading slot for both the folder chevron and file icon.
        // This keeps filenames aligned at the same depth without reserving
        // an additional, invisible folder-icon slot.
        if (node.isDirectory) {
            Icon(
                imageVector = if (node.isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = if (node.isExpanded) "Collapse" else "Expand",
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        } else {
            FileTypeBadge(
                displayName = node.displayName,
                muted = isInClipboard && clipboardIsCut,
                accent = isInClipboard,
            )
        }

        Spacer(Modifier.width(4.dp))

        Text(
            text  = node.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isInClipboard && clipboardIsCut -> colors.textDisabled
                isActive                        -> colors.accent
                isActiveAncestor                -> colors.accent
                else                            -> colors.textPrimary
            },
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
                val hasClipboard = clipboardItems.isNotEmpty()

                if (node.isDirectory) {
                    // ── Folder menu ──────────────────────────────────────
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Article, null) },
                        text    = { Text("New File") },
                        onClick = { menuOpen = false; onShowCreateFileDialog(node) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        text    = { Text("New Folder") },
                        onClick = { menuOpen = false; onShowCreateFolderDialog(node) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                        text    = { Text("Import Files") },
                        onClick = { menuOpen = false; onImportFilesAt(node) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Code, null) },
                        text    = { Text("Duplicate") },
                        onClick = { menuOpen = false; onShowDuplicateDialog(node) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        text    = { Text("Rename") },
                        onClick = { menuOpen = false; onShowRenameDialog(node) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Code, null) },
                        text    = { Text("Copy Path") },
                        onClick = { menuOpen = false; onCopyPath(node.documentUri) },
                    )
                    DropdownMenuItem(
                        text    = { Text("Export\u2026") },
                        onClick = { menuOpen = false; onExportDirectory(node) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Code, null) },
                        text    = { Text("Copy") },
                        onClick = { menuOpen = false; onCopyNode(node) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Code, null) },
                        text    = { Text("Cut") },
                        onClick = { menuOpen = false; onCutNode(node) },
                    )
                    if (hasClipboard) {
                        DropdownMenuItem(
                            text = {
                                val verb  = if (clipboardIsCut) "Move" else "Copy"
                                val count = clipboardItems.size
                                if (count == 1) {
                                    Text("$verb \u201c${clipboardItems[0].displayName}\u201d here")
                                } else {
                                    Text("$verb $count items here")
                                }
                            },
                            onClick = { menuOpen = false; onPasteInto(node) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Close, null, tint = LocalIdeColors.current.error) },
                        text    = { Text("Delete", color = LocalIdeColors.current.error) },
                        onClick = { menuOpen = false; onShowDeleteDialog(node) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                        text    = { Text("Select") },
                        onClick = { menuOpen = false; onSelect(node.documentUri) },
                    )
                } else {
                    // ── File menu ────────────────────────────────────────
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        text    = { Text("Rename") },
                        onClick = { menuOpen = false; onShowRenameDialog(node) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Code, null) },
                        text    = { Text("Duplicate") },
                        onClick = { menuOpen = false; onShowDuplicateDialog(node) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Code, null) },
                        text    = { Text("Copy Path") },
                        onClick = { menuOpen = false; onCopyPath(node.documentUri) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Code, null) },
                        text    = { Text("Copy") },
                        onClick = { menuOpen = false; onCopyNode(node) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Code, null) },
                        text    = { Text("Cut") },
                        onClick = { menuOpen = false; onCutNode(node) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Close, null, tint = LocalIdeColors.current.error) },
                        text    = { Text("Delete", color = LocalIdeColors.current.error) },
                        onClick = { menuOpen = false; onShowDeleteDialog(node) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                        text    = { Text("Select") },
                        onClick = { menuOpen = false; onSelect(node.documentUri) },
                    )
                }
            }
        }
    }
}

// ── File type icon ─────────────────────────────────────────────────────────────

/**
 *
 * Priority: image formats → text/docs → code → generic.
 * Uses only icons confirmed present in material-icons-extended.
 */
@Composable
private fun FileTypeBadge(displayName: String, muted: Boolean, accent: Boolean) {
    val kind = EditorLanguageRegistry.iconKindForFileName(displayName)
    val icon = when (kind) {
        FileIconKind.IMAGE -> Icons.Default.Image
        FileIconKind.TEXT -> Icons.Default.Article
        FileIconKind.HTML,
        FileIconKind.CSS,
        FileIconKind.JAVASCRIPT,
        FileIconKind.TYPESCRIPT,
        FileIconKind.CODE -> Icons.Default.Code
        FileIconKind.GENERIC -> Icons.Default.InsertDriveFile
    }
    val color = when (kind) {
        FileIconKind.HTML -> Color(0xFFE44D26)
        FileIconKind.CSS -> Color(0xFF2965F1)
        FileIconKind.JAVASCRIPT -> Color(0xFFF0DB4F)
        FileIconKind.TYPESCRIPT -> Color(0xFF3178C6)
        FileIconKind.IMAGE -> Color(0xFF8E6AC8)
        FileIconKind.TEXT -> Color(0xFF6B7280)
        FileIconKind.CODE -> Color(0xFF4F8CC9)
        FileIconKind.GENERIC -> Color(0xFF6B7280)
    }
        Surface(
        color = color.copy(alpha = if (muted) 0.28f else if (accent) 0.55f else 0.9f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.size(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = when (kind) {
                    FileIconKind.HTML -> "HTML file"
                    FileIconKind.CSS -> "CSS file"
                    FileIconKind.JAVASCRIPT -> "JavaScript file"
                    FileIconKind.TYPESCRIPT -> "TypeScript file"
                    FileIconKind.IMAGE -> "Image file"
                    FileIconKind.TEXT -> "Text file"
                    FileIconKind.CODE -> "Code file"
                    FileIconKind.GENERIC -> "File"
                },
                tint = if (kind == FileIconKind.JAVASCRIPT) Color.Black else Color.White,
                modifier = Modifier.size(16.dp),
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
