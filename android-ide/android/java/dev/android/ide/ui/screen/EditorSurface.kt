package dev.android.ide.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.android.ide.app.AppShellState
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.contracts.Surface
import dev.android.ide.ui.components.EditorPane
import dev.android.ide.ui.components.EditorTabBar
import dev.android.ide.ui.components.FileTreePanel
import dev.android.ide.ui.theme.LocalIdeColors
import dev.android.ide.viewmodel.IdeViewModel
import dev.android.ide.viewmodel.model.EditorTab
import dev.android.ide.viewmodel.model.FileNode
import dev.android.ide.viewmodel.model.FileOpDialog
import dev.android.ide.viewmodel.model.IdeUiState
import dev.android.ide.viewmodel.model.findNode
import dev.android.ide.viewmodel.model.pathTo
import dev.android.ide.editor.EditorOutbound

private const val DIRECTORY_MIME = "vnd.android.document/directory"

@Composable
fun EditorSurface(
    shellState: AppShellState,
    shellViewModel: AppShellViewModel,
    ideViewModel: IdeViewModel,
    onOpenGlobalNavigation: () -> Unit,
    editorPanelRequest: Long = 0L,
    onOpenSettings: () -> Unit,
    onExportProject: (String) -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by ideViewModel.uiState.collectAsState()
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    var panelVisible by rememberSaveable { mutableStateOf(true) }
    var contentPanel by rememberSaveable { mutableStateOf(EditorPanel.FILES) }
    var importTargetUri by remember { mutableStateOf<String?>(null) }
    val rootNode = remember(state.projectRootUri, state.projectName) {
        state.projectRootUri?.let { FileNode(it, state.projectName, DIRECTORY_MIME) }
    }
    val importFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val target = importTargetUri
        if (target != null && uris.isNotEmpty()) ideViewModel.importFiles(target, uris.map(Uri::toString))
        importTargetUri = null
    }
    val project = shellState.projects.firstOrNull { it.id == shellState.selectedProjectId }

    LaunchedEffect(project?.id, project?.location?.stableId) {
        project?.location?.stableId?.let(ideViewModel::openProject)
    }

    LaunchedEffect(state.isSearchVisible, state.isContentSearchVisible) {
        if (state.isSearchVisible || state.isContentSearchVisible) panelVisible = true
    }
    LaunchedEffect(editorPanelRequest) {
        if (editorPanelRequest > 0L) panelVisible = true
    }

    BackHandler(enabled = panelVisible && !isWide) { panelVisible = false }

    if (state.projectRootUri == null && !shellState.restoring) {
        EditorEmptyState(
            projectName = project?.name,
            onOpenProjects = { shellViewModel.navigate(Surface.PROJECTS) },
            modifier = modifier,
        )
        EditorDialogHost(state, ideViewModel)
        return
    }

    Row(modifier.fillMaxSize().background(LocalIdeColors.current.background)) {
        if (panelVisible) {
            EditorSidebar(
                state = state,
                contentPanel = contentPanel,
                onPanelSelected = { contentPanel = it },
                onFileSelected = { uri -> ideViewModel.openFile(uri); if (!isWide) panelVisible = false },
                onToggleDirectory = ideViewModel::toggleDirectory,
                onRefresh = ideViewModel::refreshProject,
                onLocate = ideViewModel::revealActiveFile,
                onSearchFiles = ideViewModel::showFileSearch,
                onSearchContent = { contentPanel = EditorPanel.CONTENT_SEARCH; ideViewModel.showContentSearch() },
                onHideFileSearch = ideViewModel::hideFileSearch,
                onHideContentSearch = ideViewModel::hideContentSearch,
                onImport = { uri -> importTargetUri = uri; importFiles.launch(arrayOf("*/*")) },
                onExportProject = { shellState.selectedProjectId?.let(onExportProject) },
                onShowDetails = { shellState.selectedProjectId?.let(shellViewModel::showProjectDetails) },
                onDeleteProject = shellViewModel::permanentlyDeleteSelectedProject,
                onRemoveProject = shellViewModel::removeSelectedProject,
                onOpenSettings = onOpenSettings,
                onFeedback = onFeedback,
                rootNode = rootNode,
                ideViewModel = ideViewModel,
                modifier = Modifier.widthIn(max = if (isWide) 360.dp else 390.dp).fillMaxHeight(),
            )
            if (isWide) HorizontalDivider(Modifier.width(1.dp).fillMaxHeight())
        }
        EditorWorkspace(
            state = state,
            ideViewModel = ideViewModel,
            fileTree = state.fileTree,
            onOpenGlobalNavigation = onOpenGlobalNavigation,
            onOpenSettings = onOpenSettings,
            onTogglePanel = { panelVisible = !panelVisible },
            onFeedback = onFeedback,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
    EditorDialogHost(state, ideViewModel)
}

private enum class EditorPanel { FILES, FILENAME_SEARCH, CONTENT_SEARCH }

@Composable
private fun EditorSidebar(
    state: IdeUiState,
    contentPanel: EditorPanel,
    onPanelSelected: (EditorPanel) -> Unit,
    onFileSelected: (String) -> Unit,
    onToggleDirectory: (String) -> Unit,
    onRefresh: () -> Unit,
    onLocate: () -> Unit,
    onSearchFiles: () -> Unit,
    onSearchContent: () -> Unit,
    onHideFileSearch: () -> Unit,
    onHideContentSearch: () -> Unit,
    onImport: (String) -> Unit,
    onExportProject: () -> Unit,
    onShowDetails: () -> Unit,
    onDeleteProject: () -> Unit,
    onRemoveProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onFeedback: (String) -> Unit,
    rootNode: FileNode?,
    ideViewModel: IdeViewModel,
    modifier: Modifier,
) {
    val colors = LocalIdeColors.current
    val root = rootNode ?: return
    Column(modifier.background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SidebarIcon(Icons.Default.FolderOpen, "Files", contentPanel == EditorPanel.FILES) { onPanelSelected(EditorPanel.FILES) }
            SidebarIcon(Icons.Default.Search, "Filename search", contentPanel == EditorPanel.FILENAME_SEARCH) { onPanelSelected(EditorPanel.FILENAME_SEARCH); onSearchFiles() }
            SidebarIcon(Icons.Default.Source, "Content search", contentPanel == EditorPanel.CONTENT_SEARCH) { onPanelSelected(EditorPanel.CONTENT_SEARCH); onSearchContent() }
            SidebarIcon(Icons.Default.Terminal, "Terminal (Phase 3)", false) { onFeedback("Open Terminal") }
            SidebarIcon(Icons.Default.Tune, "Editor settings", false) { onOpenSettings() }
        }
        HorizontalDivider(color = colors.separator)
        when (contentPanel) {
            EditorPanel.FILES -> FileTreePanel(
                nodes = state.fileTree,
                clipboardItems = state.clipboardItems,
                clipboardIsCut = state.clipboardIsCut,
                projectName = state.projectName,
                activeTabDocumentUri = state.openTabs.firstOrNull { it.isActive }?.documentUri,
                locateTargetUri = state.locateTargetUri,
                locateRequestToken = state.locateRequestToken,
                onLocateConsumed = ideViewModel::clearLocateRequest,
                hideGitFolder = state.editorSettings.hideGitFolder,
                isMultiSelectMode = state.isMultiSelectMode,
                selectedUris = state.selectedUris,
                isSearchVisible = false,
                isContentSearchVisible = false,
                fileSearchQuery = state.fileSearchQuery,
                fileSearchResults = state.fileSearchResults,
                contentSearchQuery = state.contentSearchQuery,
                contentSearchResults = state.contentSearchResults,
                onFileClick = onFileSelected,
                onFileDoubleClick = ideViewModel::openFilePermanent,
                onDirToggle = onToggleDirectory,
                onShowRenameDialog = ideViewModel::showRenameDialog,
                onShowDeleteDialog = ideViewModel::showDeleteDialog,
                onShowCreateFileDialog = ideViewModel::showCreateFileDialog,
                onShowCreateFolderDialog = ideViewModel::showCreateFolderDialog,
                onShowDuplicateDialog = ideViewModel::showDuplicateDialog,
                onCopyNode = ideViewModel::copyFileNode,
                onCutNode = ideViewModel::cutFileNode,
                onPasteInto = ideViewModel::pasteFileNode,
                onImportFilesAt = { onImport(it.documentUri) },
                onExportDirectory = ideViewModel::exportDirectory,
                onNewFileAtRoot = { ideViewModel.showCreateFileDialog(root) },
                onNewFolderAtRoot = { ideViewModel.showCreateFolderDialog(root) },
                onImportFilesAtRoot = { onImport(root.documentUri) },
                onExportProject = onExportProject,
                onRefresh = onRefresh,
                onShowProjectDetails = onShowDetails,
                onDeleteProject = onDeleteProject,
                onRemoveProject = onRemoveProject,
                onPasteAtRoot = { ideViewModel.pasteFileNode(root) },
                onCopyPath = ideViewModel::copyPathToClipboard,
                onToggleNodeSelection = ideViewModel::toggleNodeSelection,
                onExitSelectionMode = ideViewModel::exitSelectionMode,
                onSearchQueryChange = ideViewModel::searchFiles,
                onContentSearchQueryChange = ideViewModel::searchProjectContents,
                onHideFileSearch = onHideFileSearch,
                onHideContentSearch = onHideContentSearch,
                onSearchFileSelect = onFileSelected,
                modifier = Modifier.weight(1f),
            )
            EditorPanel.FILENAME_SEARCH -> FileTreePanel(
                nodes = state.fileTree, clipboardItems = state.clipboardItems, clipboardIsCut = state.clipboardIsCut,
                projectName = state.projectName, activeTabDocumentUri = state.openTabs.firstOrNull { it.isActive }?.documentUri,
                locateTargetUri = state.locateTargetUri, locateRequestToken = state.locateRequestToken, onLocateConsumed = ideViewModel::clearLocateRequest,
                hideGitFolder = state.editorSettings.hideGitFolder, isMultiSelectMode = false, selectedUris = emptySet(),
                isSearchVisible = true, isContentSearchVisible = false, fileSearchQuery = state.fileSearchQuery, fileSearchResults = state.fileSearchResults,
                contentSearchQuery = state.contentSearchQuery, contentSearchResults = state.contentSearchResults, onFileClick = onFileSelected, onFileDoubleClick = ideViewModel::openFilePermanent,
                onDirToggle = onToggleDirectory, onShowRenameDialog = ideViewModel::showRenameDialog, onShowDeleteDialog = ideViewModel::showDeleteDialog,
                onShowCreateFileDialog = ideViewModel::showCreateFileDialog, onShowCreateFolderDialog = ideViewModel::showCreateFolderDialog, onShowDuplicateDialog = ideViewModel::showDuplicateDialog,
                onCopyNode = ideViewModel::copyFileNode, onCutNode = ideViewModel::cutFileNode, onPasteInto = ideViewModel::pasteFileNode, onImportFilesAt = { onImport(it.documentUri) }, onExportDirectory = ideViewModel::exportDirectory,
                onNewFileAtRoot = { ideViewModel.showCreateFileDialog(root) }, onNewFolderAtRoot = { ideViewModel.showCreateFolderDialog(root) }, onImportFilesAtRoot = { onImport(root.documentUri) }, onExportProject = onExportProject,
                onRefresh = onRefresh, onShowProjectDetails = onShowDetails, onDeleteProject = onDeleteProject, onRemoveProject = onRemoveProject, onPasteAtRoot = { ideViewModel.pasteFileNode(root) }, onCopyPath = ideViewModel::copyPathToClipboard,
                onToggleNodeSelection = ideViewModel::toggleNodeSelection, onExitSelectionMode = ideViewModel::exitSelectionMode, onSearchQueryChange = ideViewModel::searchFiles, onContentSearchQueryChange = ideViewModel::searchProjectContents,
                onHideFileSearch = { onHideFileSearch(); onPanelSelected(EditorPanel.FILES) }, onHideContentSearch = onHideContentSearch, onSearchFileSelect = onFileSelected, modifier = Modifier.weight(1f),
            )
            EditorPanel.CONTENT_SEARCH -> FileTreePanel(
                nodes = state.fileTree, clipboardItems = state.clipboardItems, clipboardIsCut = state.clipboardIsCut,
                projectName = state.projectName, activeTabDocumentUri = state.openTabs.firstOrNull { it.isActive }?.documentUri,
                locateTargetUri = state.locateTargetUri, locateRequestToken = state.locateRequestToken, onLocateConsumed = ideViewModel::clearLocateRequest,
                hideGitFolder = state.editorSettings.hideGitFolder, isMultiSelectMode = false, selectedUris = emptySet(),
                isSearchVisible = false, isContentSearchVisible = true, fileSearchQuery = state.fileSearchQuery, fileSearchResults = state.fileSearchResults,
                contentSearchQuery = state.contentSearchQuery, contentSearchResults = state.contentSearchResults, onFileClick = onFileSelected, onFileDoubleClick = ideViewModel::openFilePermanent,
                onDirToggle = onToggleDirectory, onShowRenameDialog = ideViewModel::showRenameDialog, onShowDeleteDialog = ideViewModel::showDeleteDialog,
                onShowCreateFileDialog = ideViewModel::showCreateFileDialog, onShowCreateFolderDialog = ideViewModel::showCreateFolderDialog, onShowDuplicateDialog = ideViewModel::showDuplicateDialog,
                onCopyNode = ideViewModel::copyFileNode, onCutNode = ideViewModel::cutFileNode, onPasteInto = ideViewModel::pasteFileNode, onImportFilesAt = { onImport(it.documentUri) }, onExportDirectory = ideViewModel::exportDirectory,
                onNewFileAtRoot = { ideViewModel.showCreateFileDialog(root) }, onNewFolderAtRoot = { ideViewModel.showCreateFolderDialog(root) }, onImportFilesAtRoot = { onImport(root.documentUri) }, onExportProject = onExportProject,
                onRefresh = onRefresh, onShowProjectDetails = onShowDetails, onDeleteProject = onDeleteProject, onRemoveProject = onRemoveProject, onPasteAtRoot = { ideViewModel.pasteFileNode(root) }, onCopyPath = ideViewModel::copyPathToClipboard,
                onToggleNodeSelection = ideViewModel::toggleNodeSelection, onExitSelectionMode = ideViewModel::exitSelectionMode, onSearchQueryChange = ideViewModel::searchFiles, onContentSearchQueryChange = ideViewModel::searchProjectContents,
                onHideFileSearch = onHideFileSearch, onHideContentSearch = { onHideContentSearch(); onPanelSelected(EditorPanel.FILES) }, onSearchFileSelect = onFileSelected, modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SidebarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalIdeColors.current
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = if (selected) colors.accent else colors.textSecondary)
    }
}

@Composable
private fun EditorWorkspace(
    state: IdeUiState,
    ideViewModel: IdeViewModel,
    fileTree: List<FileNode>,
    onOpenGlobalNavigation: () -> Unit,
    onOpenSettings: () -> Unit,
    onTogglePanel: () -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalIdeColors.current
    val activeTab = state.openTabs.firstOrNull { it.isActive }
    var moreOpen by remember { mutableStateOf(false) }
    Column(modifier.background(colors.background)) {
        EditorTopBar(
            state = state, activeTab = activeTab, fileTree = fileTree,
            onOpenGlobalNavigation = onOpenGlobalNavigation, onTogglePanel = onTogglePanel,
            onFind = ideViewModel::showEditorFind, onReplace = ideViewModel::showEditorReplace,
            onSave = ideViewModel::saveActiveFile,
            onPreview = { if (state.isPreviewVisible) ideViewModel.togglePreview() else ideViewModel.requestRun() },
            onMore = { moreOpen = true },
            onOpenFile = ideViewModel::openFile,
            onFeedback = onFeedback,
            onOpenSettings = onOpenSettings,
        )
        if (state.openTabs.isNotEmpty()) {
            EditorTabBar(
                tabs = state.openTabs, onTabSelected = ideViewModel::selectTab, onTabCloseSafe = ideViewModel::closeTabSafe,
                onTabSave = ideViewModel::saveTabById, onTabPin = ideViewModel::pinTab, onCloseOthers = ideViewModel::closeOtherTabs,
                onCloseAll = ideViewModel::closeAllTabs, onNewBlankTab = ideViewModel::newBlankTab,
            )
            HorizontalDivider(color = colors.separator)
        }
        EditorPane(
            activeTab = activeTab, activeTabContent = activeTab?.let { ideViewModel.editorContentForTab(it.id, it.content) },
            isEditorReady = state.isEditorReady, editorBindRevision = state.editorBindRevision, isPreviewVisible = state.isPreviewVisible,
            previewHtmlContent = state.previewHtmlContent, previewLayout = state.editorSettings.previewLayout, editorCommands = ideViewModel.editorCommand,
            onEditorReady = ideViewModel::onEditorReady, onEditorRendererGone = ideViewModel::onEditorRendererGone, onEditorMessage = ideViewModel::onEditorMessage,
            onInsertText = { ideViewModel.sendEditorCommand(EditorOutbound.InsertText(it)) }, onExecuteCommand = { ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand(it)) },
            onPasteFromClipboard = ideViewModel::pasteFromKotlinClipboard, hasEditorSelection = state.hasEditorSelection,
            showKeyboardToolbar = state.editorSettings.showKeyboardToolbar, showSymbolBar = state.editorSettings.showSymbolBar, customSymbols = state.editorSettings.customSymbols,
            tabCursorPositions = state.tabCursorPositions, tabScrollPositions = state.tabScrollPositions, modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (state.editorSettings.showStatusBar) EditorStatusBar(state, activeTab)
    }
    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
        DropdownMenuItem(text = { Text("Save As") }, onClick = { moreOpen = false; ideViewModel.showSaveAsDialog() })
        DropdownMenuItem(text = { Text("Refresh project files") }, onClick = { moreOpen = false; ideViewModel.refreshProject() })
        DropdownMenuItem(text = { Text("Close other tabs") }, onClick = { moreOpen = false; activeTab?.let { ideViewModel.closeOtherTabs(it.id) } })
        DropdownMenuItem(text = { Text("Copy file path") }, onClick = { moreOpen = false; activeTab?.let { ideViewModel.copyPathToClipboard(it.documentUri) } })
        DropdownMenuItem(text = { Text("Locate current file") }, onClick = { moreOpen = false; ideViewModel.revealActiveFile() })
        DropdownMenuItem(text = { Text("Editor settings") }, onClick = { moreOpen = false; onOpenSettings() })
    }
}

@Composable
private fun EditorTopBar(
    state: IdeUiState,
    activeTab: EditorTab?,
    fileTree: List<FileNode>,
    onOpenGlobalNavigation: () -> Unit,
    onTogglePanel: () -> Unit,
    onFind: () -> Unit,
    onReplace: () -> Unit,
    onSave: () -> Unit,
    onPreview: () -> Unit,
    onMore: () -> Unit,
    onOpenFile: (String) -> Unit,
    onFeedback: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalIdeColors.current
    var pathOpen by remember { mutableStateOf(false) }
    val path = activeTab?.let { fileTree.pathTo(it.documentUri) } ?: state.projectName
    val node = activeTab?.let { fileTree.findNode(it.documentUri) }
    val siblings = node?.parentDocumentUri?.let { fileTree.findNode(it)?.children } ?: fileTree
    val canPreview = activeTab?.let { isPreviewable(it.displayName) } == true || containsPreviewable(fileTree)
    Row(Modifier.fillMaxWidth().height(56.dp).background(colors.surface).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenGlobalNavigation) { Icon(Icons.Default.Menu, "Open application navigation", tint = colors.textPrimary) }
        IconButton(onClick = onTogglePanel) { Icon(Icons.Default.FolderOpen, "Toggle contextual sidebar", tint = colors.textSecondary) }
        Box(Modifier.weight(1f)) {
            Text(
                text = path.ifBlank { "No file selected" }, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { if (activeTab != null) pathOpen = true }.padding(horizontal = 6.dp, vertical = 10.dp),
            )
            DropdownMenu(expanded = pathOpen, onDismissRequest = { pathOpen = false }) {
                siblings.forEach { sibling ->
                    DropdownMenuItem(text = { Text(sibling.displayName) }, onClick = { pathOpen = false; if (!sibling.isDirectory) onOpenFile(sibling.documentUri) })
                }
            }
        }
        IconButton(onClick = onFind, enabled = activeTab != null) { Icon(Icons.Default.Search, "Find in document", tint = colors.textSecondary) }
        IconButton(onClick = onReplace, enabled = activeTab != null) { Icon(Icons.Default.Source, "Find and replace in document", tint = colors.textSecondary) }
        IconButton(onClick = onSave, enabled = activeTab != null && !state.editorSettings.autoSave) { Icon(Icons.Default.Save, "Save active document", tint = colors.textSecondary) }
        IconButton(onClick = onPreview, enabled = canPreview) { Icon(Icons.Default.PlayArrow, "Preview or run", tint = if (canPreview) colors.accent else colors.textDisabled) }
        IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "More editor actions", tint = colors.textSecondary) }
    }
}

@Composable
private fun EditorStatusBar(state: IdeUiState, activeTab: EditorTab?) {
    val colors = LocalIdeColors.current
    Row(Modifier.fillMaxWidth().height(30.dp).background(colors.surface).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Ln ${state.cursorLine}, Col ${state.cursorColumn}", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Text("Sp: ${state.editorSettings.tabSize}", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(14.dp))
        Text(activeTab?.language ?: "Plain Text", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(14.dp))
        Text("UTF-8", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EditorEmptyState(projectName: String?, onOpenProjects: () -> Unit, modifier: Modifier) {
    val colors = LocalIdeColors.current
    Box(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.FolderOpen, null, tint = colors.accentLight, modifier = Modifier.size(44.dp))
            Text(if (projectName == null) "Open a project to start editing" else "Restoring $projectName…", style = MaterialTheme.typography.titleMedium)
            if (projectName == null) {
                Text("The editor opens project-owned files through the selected storage provider.", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenProjects) { Text("Open Projects") }
            }
        }
    }
}

private fun isPreviewable(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("html", "htm", "md", "markdown", "txt", "svg", "png", "jpg", "jpeg", "gif", "webp", "pdf", "mp4")

private fun containsPreviewable(nodes: List<FileNode>): Boolean = nodes.any { (it.isDirectory && containsPreviewable(it.children)) || isPreviewable(it.displayName) }

@Composable
private fun EditorDialogHost(state: IdeUiState, ideViewModel: IdeViewModel) {
    when (val dialog = state.fileOpDialog) {
        is FileOpDialog.BinaryOpenError -> AlertDialog(onDismissRequest = ideViewModel::dismissFileOpDialog, title = { Text("File cannot be edited") }, text = { Text("${dialog.fileName} is not a supported text document. Use the Browser or another provider when available.") }, confirmButton = { TextButton(onClick = ideViewModel::dismissFileOpDialog) { Text("OK") } })
        is FileOpDialog.Delete -> AlertDialog(onDismissRequest = ideViewModel::dismissFileOpDialog, title = { Text("Delete ${dialog.node.displayName}?") }, text = { Text("This removes the selected project file or folder through the storage provider. The operation cannot be undone.") }, confirmButton = { TextButton(onClick = { ideViewModel.deleteNode(dialog.node, dialog.selectedNodes) }) { Text("Delete") } }, dismissButton = { TextButton(onClick = ideViewModel::dismissFileOpDialog) { Text("Cancel") } })
        is FileOpDialog.UnsavedClose -> AlertDialog(onDismissRequest = ideViewModel::dismissFileOpDialog, title = { Text("Unsaved changes") }, text = { Text("${dialog.displayName} has unsaved changes. Choose how to close it.") }, confirmButton = { TextButton(onClick = { ideViewModel.saveAndCloseTab(dialog.tabId) }) { Text("Save and close") } }, dismissButton = { Row { TextButton(onClick = { ideViewModel.confirmCloseTab(dialog.tabId) }) { Text("Discard") }; TextButton(onClick = ideViewModel::dismissFileOpDialog) { Text("Cancel") } } })
        is FileOpDialog.Rename -> EditorTextDialog("Rename ${dialog.node.displayName}", "New name or project-relative path", dialog.node.displayName, dialog.errorMessage, ideViewModel::dismissFileOpDialog) { ideViewModel.renameNode(dialog.node, it) }
        is FileOpDialog.Duplicate -> EditorTextDialog("Duplicate ${dialog.node.displayName}", "New name", "copy_${dialog.node.displayName}", dialog.errorMessage, ideViewModel::dismissFileOpDialog) { ideViewModel.duplicateFile(dialog.node, it) }
        is FileOpDialog.CreateFile -> EditorTextDialog("Create file", "File name or relative path", "", dialog.errorMessage, ideViewModel::dismissFileOpDialog, dialog.isSubmitting) { ideViewModel.createFileInDirectory(dialog.parentNode, it) }
        is FileOpDialog.CreateFolder -> EditorTextDialog("Create folder", "Folder name or relative path", "", dialog.errorMessage, ideViewModel::dismissFileOpDialog, dialog.isSubmitting) { ideViewModel.createFolderInDirectory(dialog.parentNode, it) }
        is FileOpDialog.SaveAs -> EditorTextDialog("Save As", "Project-relative path", dialog.suggestedName, null, ideViewModel::dismissFileOpDialog) { ideViewModel.saveAsAtPath(it) }
        null -> Unit
    }
}

@Composable
private fun EditorTextDialog(title: String, label: String, initial: String, error: String?, onDismiss: () -> Unit, submitting: Boolean = false, onConfirm: (String) -> Unit) {
    var value by remember(title, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Input", style = MaterialTheme.typography.labelMedium); OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true); if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { Button(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }, enabled = value.isNotBlank() && !submitting) { Text(if (submitting) "Working…" else "Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") } },
    )
}
