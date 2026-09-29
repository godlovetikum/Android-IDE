package dev.android.ide.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.android.ide.app.AppShellState
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.contracts.Surface
import dev.android.ide.ui.components.EditorPane
import dev.android.ide.ui.components.EditorProjectActionsMenu
import dev.android.ide.ui.components.EditorTabBar
import dev.android.ide.ui.components.FileTreePanel
import dev.android.ide.ui.theme.LocalIdeColors
import dev.android.ide.viewmodel.IdeViewModel
import dev.android.ide.viewmodel.model.EditorTab
import dev.android.ide.viewmodel.model.FileNode
import dev.android.ide.viewmodel.model.FileSearchResult
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
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val dialog = state.fileOpDialog as? FileOpDialog.Export
        if (uri != null && dialog != null) ideViewModel.exportDirectory(dialog.node, uri.toString())
    }
    val project = shellState.projects.firstOrNull { it.id == shellState.selectedProjectId }

    LaunchedEffect(project?.id, project?.location?.stableId) {
        project?.location?.stableId?.let(ideViewModel::openProject)
    }

    if (state.projectRootUri == null && !shellState.restoring) {
        EditorEmptyState(
            projectName = project?.name,
            onOpenProjects = { shellViewModel.navigate(Surface.PROJECTS) },
            modifier = modifier,
        )
        EditorDialogHost(state, ideViewModel, onChooseExportDestination = { dialog -> exportZip.launch("${dialog.node.displayName}.zip") })
        return
    }

    EditorWorkspace(
        state = state,
        ideViewModel = ideViewModel,
        fileTree = state.fileTree,
        onOpenGlobalNavigation = onOpenGlobalNavigation,
        onOpenSettings = onOpenSettings,
        onFeedback = onFeedback,
        modifier = modifier.fillMaxSize().background(LocalIdeColors.current.background),
    )
    if (state.fileMutationLoading) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).clickable { },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Surface(
                tonalElevation = 6.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Working…", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    EditorDialogHost(state, ideViewModel, onChooseExportDestination = { dialog -> exportZip.launch("${dialog.node.displayName}.zip") })
}

@Composable
private fun EditorSidebarActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(58.dp)
            .widthIn(min = 68.dp)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = shape,
            )
            .semantics {
                role = Role.Button
                this.selected = selected
            },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

enum class EditorPanel { FILES, FILENAME_SEARCH, CONTENT_SEARCH }

@Composable
fun EditorSidebar(
    state: IdeUiState,
    contentPanel: EditorPanel,
    onPanelSelected: (EditorPanel) -> Unit,
    onFileSelected: (String) -> Unit,
    onSearchResultSelected: (FileSearchResult) -> Unit,
    onToggleDirectory: (String) -> Unit,
    onRefresh: () -> Unit,
    onLocate: () -> Unit,
    onSearchFiles: () -> Unit,
    onSearchContent: () -> Unit,
    onHideFileSearch: () -> Unit,
    onHideContentSearch: () -> Unit,
    onImport: (String) -> Unit,
    onExportDirectory: (FileNode) -> Unit,
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
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorSidebarActionTile(
                icon = Icons.Default.FolderOpen,
                label = "Files",
                selected = contentPanel == EditorPanel.FILES,
                onClick = { onPanelSelected(EditorPanel.FILES); onHideFileSearch(); onHideContentSearch() },
            )
            EditorSidebarActionTile(
                icon = Icons.Default.Search,
                label = "Search",
                selected = state.isSearchVisible,
                onClick = {
                    if (state.isSearchVisible) onHideFileSearch() else onSearchFiles()
                    onPanelSelected(if (state.isSearchVisible) EditorPanel.FILES else EditorPanel.FILENAME_SEARCH)
                },
            )
            EditorSidebarActionTile(
                icon = Icons.Default.Code,
                label = "Contents",
                selected = state.isContentSearchVisible,
                onClick = {
                    if (state.isContentSearchVisible) onHideContentSearch() else onSearchContent()
                    onPanelSelected(if (state.isContentSearchVisible) EditorPanel.FILES else EditorPanel.CONTENT_SEARCH)
                },
            )
            EditorSidebarActionTile(Icons.Default.MyLocation, "Locate", onClick = onLocate)
            EditorSidebarActionTile(Icons.Default.Description, "New file", onClick = { ideViewModel.showCreateFileDialog(root) })
            EditorSidebarActionTile(Icons.Default.CreateNewFolder, "New folder", onClick = { ideViewModel.showCreateFolderDialog(root) })
            var moreOpen by remember { mutableStateOf(false) }
            Box {
                EditorSidebarActionTile(Icons.Default.MoreVert, "More", onClick = { moreOpen = true })
                EditorProjectActionsMenu(
                    expanded = moreOpen,
                    onDismiss = { moreOpen = false },
                    onNewFile = { ideViewModel.showCreateFileDialog(root) },
                    onNewFolder = { ideViewModel.showCreateFolderDialog(root) },
                    onImportFiles = { onImport(root.documentUri) },
                    onExportProject = onExportProject,
                    onRefresh = onRefresh,
                    onShowDetails = onShowDetails,
                    onDeleteProject = onDeleteProject,
                    onRemoveProject = onRemoveProject,
                )
            }
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
                onExportDirectory = onExportDirectory,
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
                onReplaceProjectContents = ideViewModel::replaceProjectContents,
                onHideFileSearch = onHideFileSearch,
                onHideContentSearch = onHideContentSearch,
                onSearchFileSelect = { result -> onFileSelected(result.documentUri) },
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
                onCopyNode = ideViewModel::copyFileNode, onCutNode = ideViewModel::cutFileNode, onPasteInto = ideViewModel::pasteFileNode, onImportFilesAt = { onImport(it.documentUri) }, onExportDirectory = onExportDirectory,
                onNewFileAtRoot = { ideViewModel.showCreateFileDialog(root) }, onNewFolderAtRoot = { ideViewModel.showCreateFolderDialog(root) }, onImportFilesAtRoot = { onImport(root.documentUri) }, onExportProject = onExportProject,
                onRefresh = onRefresh, onShowProjectDetails = onShowDetails, onDeleteProject = onDeleteProject, onRemoveProject = onRemoveProject, onPasteAtRoot = { ideViewModel.pasteFileNode(root) }, onCopyPath = ideViewModel::copyPathToClipboard,
                onToggleNodeSelection = ideViewModel::toggleNodeSelection, onExitSelectionMode = ideViewModel::exitSelectionMode, onSearchQueryChange = ideViewModel::searchFiles, onContentSearchQueryChange = ideViewModel::searchProjectContents,
                onReplaceProjectContents = ideViewModel::replaceProjectContents,
                onHideFileSearch = { onHideFileSearch(); onPanelSelected(EditorPanel.FILES) }, onHideContentSearch = onHideContentSearch, onSearchFileSelect = { result -> onFileSelected(result.documentUri) }, modifier = Modifier.weight(1f),
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
                onCopyNode = ideViewModel::copyFileNode, onCutNode = ideViewModel::cutFileNode, onPasteInto = ideViewModel::pasteFileNode, onImportFilesAt = { onImport(it.documentUri) }, onExportDirectory = onExportDirectory,
                onNewFileAtRoot = { ideViewModel.showCreateFileDialog(root) }, onNewFolderAtRoot = { ideViewModel.showCreateFolderDialog(root) }, onImportFilesAtRoot = { onImport(root.documentUri) }, onExportProject = onExportProject,
                onRefresh = onRefresh, onShowProjectDetails = onShowDetails, onDeleteProject = onDeleteProject, onRemoveProject = onRemoveProject, onPasteAtRoot = { ideViewModel.pasteFileNode(root) }, onCopyPath = ideViewModel::copyPathToClipboard,
                onToggleNodeSelection = ideViewModel::toggleNodeSelection, onExitSelectionMode = ideViewModel::exitSelectionMode, onSearchQueryChange = ideViewModel::searchFiles, onContentSearchQueryChange = ideViewModel::searchProjectContents,
                onReplaceProjectContents = ideViewModel::replaceProjectContents,
                onHideFileSearch = onHideFileSearch, onHideContentSearch = { onHideContentSearch(); onPanelSelected(EditorPanel.FILES) }, onSearchFileSelect = onSearchResultSelected, modifier = Modifier.weight(1f),
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
    onFeedback: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalIdeColors.current
    val activeTab = state.openTabs.firstOrNull { it.isActive }
    var moreOpen by remember { mutableStateOf(false) }
    Column(modifier.background(colors.background)) {
        EditorTopBar(
            state = state, activeTab = activeTab, fileTree = fileTree,
            projectRootUri = state.projectRootUri,
            onOpenGlobalNavigation = onOpenGlobalNavigation,
            onFind = {
                ideViewModel.showEditorFind()
                ideViewModel.sendEditorCommand(EditorOutbound.ShowFind)
            },
            onSave = {
                if (activeTab?.isBlank == true) ideViewModel.showSaveAsDialog() else ideViewModel.saveActiveFile()
            },
            onMore = { moreOpen = true },
            onOpenFile = ideViewModel::openFile,
            loadNavChildren = ideViewModel::loadNavChildren,
            onFeedback = onFeedback,
            onOpenSettings = onOpenSettings,
        )
        if (state.isEditorSearchVisible) {
            Row(
                Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Find", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                TextButton(onClick = { ideViewModel.sendEditorCommand(EditorOutbound.ShowReplace) }) {
                    Text("Replace")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    ideViewModel.sendEditorCommand(EditorOutbound.CloseSearch)
                    ideViewModel.dismissEditorSearch()
                }) { Text("Close") }
            }
        }
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
            isEditorReady = state.isEditorReady, editorBindRevision = state.editorBindRevision, editorCommands = ideViewModel.editorCommand,
            onEditorRendererGone = ideViewModel::onEditorRendererGone, onEditorMessage = ideViewModel::onEditorMessage,
            onInsertText = { ideViewModel.sendEditorCommand(EditorOutbound.InsertText(it)) }, onExecuteCommand = { ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand(it)) },
            onPasteFromClipboard = ideViewModel::pasteFromKotlinClipboard, hasEditorSelection = state.hasEditorSelection,
            showKeyboardToolbar = state.editorSettings.showKeyboardToolbar, showSymbolBar = state.editorSettings.showSymbolBar,
            keyboardToolbarOrder = state.editorSettings.keyboardToolbarOrder,
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
    projectRootUri: String?,
    onOpenGlobalNavigation: () -> Unit,
    onFind: () -> Unit,
    onSave: () -> Unit,
    onMore: () -> Unit,
    onOpenFile: (String) -> Unit,
    loadNavChildren: suspend (String) -> List<FileNode>,
    onFeedback: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalIdeColors.current
    var pathOpen by remember { mutableStateOf(false) }
    val path = activeTab?.let { fileTree.pathTo(it.documentUri) } ?: state.projectName
    val node = activeTab?.let { fileTree.findNode(it.documentUri) }
    val activeParentUri = node?.parentDocumentUri ?: projectRootUri
    var navCurrentUri by remember { mutableStateOf<String?>(null) }
    var navItems by remember { mutableStateOf(emptyList<FileNode>()) }
    var navLoading by remember { mutableStateOf(false) }
    LaunchedEffect(pathOpen, activeParentUri, navCurrentUri) {
        if (!pathOpen) {
            navCurrentUri = null
            navItems = emptyList()
            return@LaunchedEffect
        }
        if (navCurrentUri == null) {
            navCurrentUri = activeParentUri
            return@LaunchedEffect
        }
        val targetUri = navCurrentUri ?: return@LaunchedEffect
        navLoading = true
        navItems = loadNavChildren(targetUri)
        navLoading = false
    }
    Row(Modifier.fillMaxWidth().height(56.dp).background(colors.surface).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenGlobalNavigation) { Icon(Icons.Default.Menu, "Open application navigation", tint = colors.textPrimary) }
        Box(Modifier.weight(1f)) {
            Text(
                text = path.ifBlank { "No file selected" }, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { if (activeTab != null) pathOpen = true }.padding(horizontal = 6.dp, vertical = 10.dp),
            )
            DropdownMenu(expanded = pathOpen, onDismissRequest = { pathOpen = false }) {
                val currentUri = navCurrentUri ?: activeParentUri
                val parentUri = currentUri?.let { uri -> fileTree.findNode(uri)?.parentDocumentUri }
                DropdownMenuItem(
                    text = { Text("..", color = colors.accent) },
                    enabled = parentUri != null,
                    onClick = {
                        parentUri?.let { navCurrentUri = it }
                    },
                )
                if (navLoading) {
                    DropdownMenuItem(text = { Text("Loading…", color = colors.textDisabled) }, onClick = {}, enabled = false)
                } else {
                    navItems.forEach { sibling ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (sibling.isDirectory) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (sibling.isDirectory) "${sibling.displayName}/" else sibling.displayName, color = if (sibling.documentUri == activeTab?.documentUri) colors.accent else colors.textPrimary)
                                }
                            },
                            onClick = {
                                if (sibling.isDirectory) navCurrentUri = sibling.documentUri
                                else { pathOpen = false; onOpenFile(sibling.documentUri) }
                            },
                        )
                    }
                }
            }
        }
        IconButton(onClick = onFind, enabled = activeTab != null) { Icon(Icons.Default.Search, "Find in document", tint = colors.textSecondary) }
        IconButton(onClick = onSave, enabled = activeTab != null && !state.editorSettings.autoSave) { Icon(Icons.Default.Save, "Save active document", tint = colors.textSecondary) }
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
                Text("The editor opens files from the project's selected location.", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenProjects) { Text("Open Projects") }
            }
        }
    }
}

@Composable
private fun EditorDialogHost(state: IdeUiState, ideViewModel: IdeViewModel, onChooseExportDestination: (FileOpDialog.Export) -> Unit) {
    when (val dialog = state.fileOpDialog) {
        is FileOpDialog.BinaryOpenError -> AlertDialog(onDismissRequest = ideViewModel::dismissFileOpDialog, title = { Text("File cannot be edited") }, text = { Text("${dialog.fileName} is not a supported text document. Use the Browser or another provider when available.") }, confirmButton = { TextButton(onClick = ideViewModel::dismissFileOpDialog) { Text("OK") } })
        is FileOpDialog.Delete -> {
            val path = state.fileTree.pathTo(dialog.node.documentUri) ?: "/${dialog.node.displayName}"
            AlertDialog(onDismissRequest = { if (!dialog.isSubmitting) ideViewModel.dismissFileOpDialog() }, title = { Text("Delete ${dialog.node.displayName}?") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Project path: $path") ; Text("This permanently removes the selected item and its contents. This action cannot be undone.") ; dialog.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }; if (dialog.isSubmitting) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp); Text("Deleting…") } } }, confirmButton = { TextButton(onClick = { ideViewModel.deleteNode(dialog.node, dialog.selectedNodes) }, enabled = !dialog.isSubmitting) { Text(if (dialog.isSubmitting) "Deleting…" else if (dialog.errorMessage != null) "Retry delete" else "Delete") } }, dismissButton = { TextButton(onClick = ideViewModel::dismissFileOpDialog, enabled = !dialog.isSubmitting) { Text(if (dialog.isSubmitting) "Please wait" else "Cancel") } })
        }
        is FileOpDialog.UnsavedClose -> AlertDialog(onDismissRequest = ideViewModel::dismissFileOpDialog, title = { Text("Unsaved changes") }, text = { Text("${dialog.displayName} has unsaved changes. Choose how to close it.") }, confirmButton = { TextButton(onClick = { ideViewModel.saveAndCloseTab(dialog.tabId) }) { Text("Save and close") } }, dismissButton = { Row { TextButton(onClick = { ideViewModel.confirmCloseTab(dialog.tabId) }) { Text("Discard") }; TextButton(onClick = ideViewModel::dismissFileOpDialog) { Text("Cancel") } } })
        is FileOpDialog.Rename -> EditorTextDialog("Rename ${dialog.node.displayName}", "New name or path", dialog.node.displayName, dialog.errorMessage, ideViewModel::dismissFileOpDialog, dialog.isSubmitting) { ideViewModel.renameNode(dialog.node, it) }
        is FileOpDialog.Duplicate -> EditorTextDialog("Duplicate ${dialog.node.displayName}", "New name or path", "Copy of ${dialog.node.displayName}", dialog.errorMessage, ideViewModel::dismissFileOpDialog, dialog.isSubmitting) { ideViewModel.duplicateFile(dialog.node, it) }
        is FileOpDialog.Export -> AlertDialog(onDismissRequest = { if (!dialog.isSubmitting) ideViewModel.dismissFileOpDialog() }, title = { Text("Export ${dialog.node.displayName}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("The selected ${if (dialog.node.isDirectory) "folder" else "file"} will be exported as a ZIP without changing the source.") ; dialog.resultMessage?.let { Text(it, color = if (dialog.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }; if (dialog.isSubmitting) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp); Text("Exporting…") } } }, confirmButton = { Button(onClick = { onChooseExportDestination(dialog) }, enabled = !dialog.isSubmitting) { Text(if (dialog.failed) "Retry export" else "Choose export location") } }, dismissButton = { TextButton(onClick = ideViewModel::dismissFileOpDialog, enabled = !dialog.isSubmitting) { Text(if (dialog.isSubmitting) "Please wait" else "Cancel") } })
        is FileOpDialog.CreateFile -> EditorTextDialog("Create file", "File path", "", dialog.errorMessage, ideViewModel::dismissFileOpDialog, dialog.isSubmitting) { ideViewModel.createFileInDirectory(dialog.parentNode, it) }
        is FileOpDialog.CreateFolder -> EditorTextDialog("Create folder", "Folder path", "", dialog.errorMessage, ideViewModel::dismissFileOpDialog, dialog.isSubmitting) { ideViewModel.createFolderInDirectory(dialog.parentNode, it) }
        is FileOpDialog.SaveAs -> EditorTextDialog("Save As", "Project-relative path", dialog.suggestedName, dialog.errorMessage, ideViewModel::dismissFileOpDialog, dialog.isSubmitting) { ideViewModel.saveAsAtPath(it) }
        is FileOpDialog.ReplaceAll -> AlertDialog(
            onDismissRequest = { if (!dialog.isSubmitting) ideViewModel.dismissFileOpDialog() },
            title = { Text("Replace project content?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Replace all occurrences of \"${dialog.find}\" with \"${dialog.replacement}\"?")
                    Text("${dialog.matches} match(es) across ${dialog.files} file(s) will be changed.")
                    dialog.resultMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (dialog.isSubmitting) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        Text("Replacing…")
                    }
                }
            },
            confirmButton = {
                Button(onClick = ideViewModel::confirmReplaceProjectContents, enabled = !dialog.isSubmitting) {
                    Text(if (dialog.isSubmitting) "Replacing…" else "Replace all")
                }
            },
            dismissButton = {
                TextButton(onClick = ideViewModel::dismissFileOpDialog, enabled = !dialog.isSubmitting) {
                    Text(if (dialog.isSubmitting) "Please wait" else "Cancel")
                }
            },
        )
        null -> Unit
    }
}

@Composable
private fun EditorTextDialog(title: String, label: String, initial: String, error: String?, onDismiss: () -> Unit, submitting: Boolean = false, onConfirm: (String) -> Unit) {
    var value by remember(title, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value, { value = it }, label = { Text(label) }, supportingText = { Text("Relative to the selected project folder, or an absolute path inside it.") }, enabled = !submitting, singleLine = true); if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); if (submitting) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp); Text("Working…") } } },
        confirmButton = { Button(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }, enabled = value.isNotBlank() && !submitting) { Text(if (submitting) "Working…" else "Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") } },
    )
}
