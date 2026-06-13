// android-ide/android/java/dev/androidide/ui/IdeScreen.kt
//
// Root screen — always active. Handles three content areas (PROJECTS / EDITOR / SETTINGS)
// with a persistent sidebar for navigation and file management.
//
// Layout modes:
//   Wide (≥ 600dp) — permanent 240dp sidebar column + content area
//   Narrow (< 600dp) — ModalNavigationDrawer (gesturesEnabled = false) + content area
//
// The sidebar contains:
//   1. Large navigation buttons (Projects, Editor, Git [disabled], Terminal [disabled], Settings)
//   2. Files header (shown when a project is open) — search, reveal, new file, new folder, more
//   3. FileTreePanel (shown when a project is open)
//
// imePadding() is applied to the editor content area so it shrinks when the soft keyboard appears.

package dev.androidide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.editor.EditorOutbound
import dev.androidide.ui.components.EditorPane
import dev.androidide.ui.components.EditorTabBar
import dev.androidide.ui.components.FileTreePanel
import dev.androidide.ui.components.IdeStatusBar
import dev.androidide.ui.screen.ProjectsScreen
import dev.androidide.ui.screen.SettingsScreen
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.AppScreen
import dev.androidide.viewmodel.model.FileNode
import dev.androidide.viewmodel.model.FileOpDialog
import dev.androidide.viewmodel.model.IdeUiState
import dev.androidide.viewmodel.model.pathTo
import kotlinx.coroutines.launch

// ── IdeScreen ─────────────────────────────────────────────────────────────────

@Composable
fun IdeScreen(
    ideViewModel: IdeViewModel,
    uiState: IdeUiState,
    onOpenProjectFolder: () -> Unit,
    onCreateBlankProject: () -> Unit,
    onSaveAs: () -> Unit,
    onImportFilesAt: (FileNode) -> Unit,
    onImportFilesAtRoot: () -> Unit,
) {
    val colors        = LocalIdeColors.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWide        = screenWidthDp >= 600
    val activeTab     = uiState.openTabs.firstOrNull { it.isActive }

    // Back handler: when on EDITOR, confirm exit or go to Projects
    BackHandler(enabled = uiState.currentScreen == AppScreen.EDITOR) {
        if (!ideViewModel.requestExit()) {
            ideViewModel.navigateTo(AppScreen.PROJECTS)
        }
    }

    // ── Shared root FileNode helpers ───────────────────────────────────────
    val rootNode = FileNode(
        documentUri = uiState.projectRootUri ?: "",
        displayName = "",
        mimeType    = "vnd.android.document/directory",
    )

    // ── Shared FileTreePanel composable (avoids duplication in wide/narrow) ─
    val fileTreePanelContent: @Composable (Modifier) -> Unit = { mod ->
        FileTreePanel(
            nodes                    = uiState.fileTree,
            clipboard                = uiState.clipboard,
            clipboardIsCut           = uiState.clipboardIsCut,
            projectName              = uiState.projectName,
            activeTabDocumentUri     = activeTab?.documentUri,
            hideGitFolder            = uiState.editorSettings.hideGitFolder,
            isMultiSelectMode        = uiState.isMultiSelectMode,
            selectedUris             = uiState.selectedUris,
            isSearchVisible          = uiState.isSearchVisible,
            fileSearchQuery          = uiState.fileSearchQuery,
            fileSearchResults        = uiState.fileSearchResults,
            onFileClick              = { uri ->
                if (uiState.isMultiSelectMode) ideViewModel.toggleNodeSelection(uri)
                else { ideViewModel.openFile(uri); ideViewModel.navigateTo(AppScreen.EDITOR) }
            },
            onDirToggle              = { uri ->
                if (uiState.isMultiSelectMode) ideViewModel.toggleNodeSelection(uri)
                else ideViewModel.toggleDirectory(uri)
            },
            onShowRenameDialog       = ideViewModel::showRenameDialog,
            onShowDeleteDialog       = ideViewModel::showDeleteDialog,
            onShowCreateFileDialog   = ideViewModel::showCreateFileDialog,
            onShowCreateFolderDialog = ideViewModel::showCreateFolderDialog,
            onShowDuplicateDialog    = ideViewModel::showDuplicateDialog,
            onCopyNode               = ideViewModel::copyFileNode,
            onCutNode                = ideViewModel::cutFileNode,
            onPasteInto              = ideViewModel::pasteFileNode,
            onImportFilesAt          = onImportFilesAt,
            onExportDirectory        = ideViewModel::exportDirectory,
            onNewFileAtRoot          = { ideViewModel.showCreateFileDialog(rootNode) },
            onNewFolderAtRoot        = { ideViewModel.showCreateFolderDialog(rootNode) },
            onImportFilesAtRoot      = onImportFilesAtRoot,
            onExportProject          = ideViewModel::exportProject,
            onRefresh                = ideViewModel::refreshProject,
            onRenameProject          = { /* TODO Phase 2: rename project via dialog */ },
            onRemoveProject          = {
                uiState.projectRootUri?.let { ideViewModel.requestRemoveProject(it) }
            },
            onCopyPath               = ideViewModel::copyPathToClipboard,
            onToggleNodeSelection    = ideViewModel::toggleNodeSelection,
            onExitSelectionMode      = ideViewModel::exitSelectionMode,
            onSearchQueryChange      = ideViewModel::searchFiles,
            onSearchFileSelect       = { uri ->
                ideViewModel.hideFileSearch()
                ideViewModel.openFile(uri)
                ideViewModel.navigateTo(AppScreen.EDITOR)
            },
            modifier                 = mod,
        )
    }

    // ── Sidebar composable (shared between wide/narrow) ────────────────────
    val sidebarContent: @Composable (Modifier) -> Unit = { mod ->
        Column(
            modifier = mod
                .background(colors.surface)
                .fillMaxHeight(),
        ) {
            SidebarNavPanel(
                currentScreen      = uiState.currentScreen,
                onNavigateProjects = { ideViewModel.navigateTo(AppScreen.PROJECTS) },
                onNavigateEditor   = { ideViewModel.navigateTo(AppScreen.EDITOR) },
                onNavigateSettings = { ideViewModel.navigateTo(AppScreen.SETTINGS) },
            )
            if (uiState.projectRootUri != null) {
                HorizontalDivider(thickness = 1.dp, color = colors.separator)
                FilesHeader(
                    isSearchVisible    = uiState.isSearchVisible,
                    onShowFileSearch   = ideViewModel::showFileSearch,
                    onHideFileSearch   = ideViewModel::hideFileSearch,
                    onRevealActiveFile = ideViewModel::revealActiveFile,
                    onNewFile          = { ideViewModel.showCreateFileDialog(rootNode) },
                    onNewFolder        = { ideViewModel.showCreateFolderDialog(rootNode) },
                    onImportFiles      = onImportFilesAtRoot,
                    onRefresh          = ideViewModel::refreshProject,
                    onExportProject    = ideViewModel::exportProject,
                    onRenameProject    = { /* TODO Phase 2 */ },
                    onRemoveProject    = {
                        uiState.projectRootUri?.let { ideViewModel.requestRemoveProject(it) }
                    },
                )
                fileTreePanelContent(Modifier.fillMaxSize())
            }
        }
    }

    // ── Content composable ────────────────────────────────────────────────
    val mainContent: @Composable (onToggleSidebar: (() -> Unit)?, Modifier) -> Unit =
        { onToggleSidebar, mod ->
            when (uiState.currentScreen) {
                AppScreen.EDITOR -> {
                    Column(modifier = mod.background(colors.background)) {
                        IdeTopBar(
                            projectName      = uiState.projectName,
                            activeTab        = activeTab,
                            fileTree         = uiState.fileTree,
                            isPreviewVisible = uiState.isPreviewVisible,
                            autoSave         = uiState.editorSettings.autoSave,
                            onSave           = ideViewModel::saveActiveFile,
                            onSaveAs         = onSaveAs,
                            onFind           = { ideViewModel.sendEditorCommand(EditorOutbound.ShowFind) },
                            onReplace        = { ideViewModel.sendEditorCommand(EditorOutbound.ShowReplace) },
                            onTogglePreview  = ideViewModel::togglePreview,
                            onOpenFile       = { uri ->
                                ideViewModel.openFile(uri)
                            },
                            onMenuClick      = onToggleSidebar,
                        )
                        EditorContent(
                            uiState      = uiState,
                            ideViewModel = ideViewModel,
                            modifier     = Modifier.weight(1f).fillMaxWidth().imePadding(),
                        )
                        IdeStatusBar(
                            cursorLine    = uiState.cursorLine,
                            cursorColumn  = uiState.cursorColumn,
                            fileName      = activeTab?.displayName ?: "",
                            language      = activeTab?.language ?: "",
                            statusMessage = uiState.statusMessage,
                        )
                    }
                }
                AppScreen.PROJECTS -> {
                    ProjectsScreen(
                        uiState              = uiState,
                        ideViewModel         = ideViewModel,
                        onOpenProjectFolder  = onOpenProjectFolder,
                        onCreateBlankProject = onCreateBlankProject,
                        onNavigationIconClick = onToggleSidebar,
                    )
                }
                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        uiState              = uiState,
                        ideViewModel         = ideViewModel,
                        onNavigationIconClick = onToggleSidebar,
                    )
                }
            }
        }

    // ── Layout ─────────────────────────────────────────────────────────────
    if (isWide) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Permanent sidebar
            sidebarContent(Modifier.width(240.dp).fillMaxHeight())
            VerticalDivider(thickness = 1.dp, color = colors.separator)
            // Content area
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                mainContent(null, Modifier.fillMaxSize())
            }
        }
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope       = rememberCoroutineScope()
        val toggleDrawer: () -> Unit = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }

        ModalNavigationDrawer(
            drawerState     = drawerState,
            gesturesEnabled = false,
            drawerContent   = {
                ModalDrawerSheet(
                    modifier             = Modifier.width(280.dp),
                    drawerContainerColor = colors.surface,
                ) {
                    sidebarContent(Modifier.fillMaxSize())
                }
            },
        ) {
            mainContent(toggleDrawer, Modifier.fillMaxSize())
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────
    FileOpDialogHost(dialog = uiState.fileOpDialog, ideViewModel = ideViewModel)

    if (uiState.showExitConfirmation) {
        ExitConfirmDialog(
            onSaveAll = { ideViewModel.saveAllAndExit { } },
            onDiscard = {
                ideViewModel.dismissExitConfirmation()
                ideViewModel.closeAllTabs()
                ideViewModel.navigateTo(AppScreen.PROJECTS)
            },
            onCancel  = ideViewModel::dismissExitConfirmation,
        )
    }

    if (uiState.recoveryEntries.isNotEmpty()) {
        CrashRecoveryDialog(
            count     = uiState.recoveryEntries.size,
            onRestore = ideViewModel::restoreFromCrash,
            onDismiss = ideViewModel::dismissCrashRecovery,
        )
    }
}

// ── Editor content area (tab bar + editor pane) ───────────────────────────────

@Composable
private fun EditorContent(
    uiState: IdeUiState,
    ideViewModel: IdeViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current
    val s      = uiState.editorSettings
    Column(modifier = modifier) {
        if (uiState.openTabs.isNotEmpty()) {
            EditorTabBar(
                tabs           = uiState.openTabs,
                onTabSelected  = ideViewModel::selectTab,
                onTabCloseSafe = ideViewModel::closeTabSafe,
                onTabSave      = ideViewModel::saveTabById,
                onCloseOthers  = ideViewModel::closeOtherTabs,
                onCloseAll     = ideViewModel::closeAllTabs,
                onNewBlankTab  = ideViewModel::newBlankTab,
            )
            HorizontalDivider(thickness = 1.dp, color = colors.separator)
        }
        EditorPane(
            activeTab           = uiState.openTabs.firstOrNull { it.isActive },
            isEditorReady       = uiState.isEditorReady,
            isPreviewVisible    = uiState.isPreviewVisible,
            previewHtmlContent  = uiState.previewHtmlContent,
            previewLayout       = s.previewLayout,
            editorCommands      = ideViewModel.editorCommand,
            onEditorReady       = ideViewModel::onEditorReady,
            onEditorMessage     = ideViewModel::onEditorMessage,
            onInsertText        = { text -> ideViewModel.sendEditorCommand(EditorOutbound.InsertText(text)) },
            onExecuteCommand    = { cmd  -> ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand(cmd)) },
            showKeyboardToolbar = s.showKeyboardToolbar,
            showSymbolBar       = s.showSymbolBar,
            customSymbols       = s.customSymbols,
            modifier            = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

// ── Sidebar nav panel ─────────────────────────────────────────────────────────

@Composable
private fun SidebarNavPanel(
    currentScreen: AppScreen,
    onNavigateProjects: () -> Unit,
    onNavigateEditor: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    val colors = LocalIdeColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        SidebarNavButton(
            icon      = Icons.Default.FolderOpen,
            label     = "Projects",
            selected  = currentScreen == AppScreen.PROJECTS,
            onClick   = onNavigateProjects,
        )
        SidebarNavButton(
            icon      = Icons.Default.Code,
            label     = "Editor",
            selected  = currentScreen == AppScreen.EDITOR,
            onClick   = onNavigateEditor,
        )
        SidebarNavButton(
            icon     = Icons.Default.MergeType,
            label    = "Git",
            selected = false,
            enabled  = false,
            onClick  = {},
        )
        SidebarNavButton(
            icon     = Icons.Default.Terminal,
            label    = "Terminal",
            selected = false,
            enabled  = false,
            onClick  = {},
        )
        SidebarNavButton(
            icon     = Icons.Default.Settings,
            label    = "Settings",
            selected = currentScreen == AppScreen.SETTINGS,
            onClick  = onNavigateSettings,
        )
    }
}

@Composable
private fun SidebarNavButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalIdeColors.current
    val tint = when {
        !enabled -> colors.textDisabled
        selected -> colors.accent
        else     -> colors.textSecondary
    }
    val bg = if (selected) colors.activeHighlight else androidx.compose.ui.graphics.Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .height(44.dp)
            .run { if (enabled) clickable(onClick = onClick) else this }
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = tint,
            modifier           = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
}

// ── Files header ─────────────────────────────────────────────────────────────

@Composable
private fun FilesHeader(
    isSearchVisible: Boolean,
    onShowFileSearch: () -> Unit,
    onHideFileSearch: () -> Unit,
    onRevealActiveFile: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onRefresh: () -> Unit,
    onExportProject: () -> Unit,
    onRenameProject: () -> Unit,
    onRemoveProject: () -> Unit,
) {
    val colors   = LocalIdeColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            text     = "FILES",
            style    = MaterialTheme.typography.labelSmall,
            color    = colors.textDisabled,
            modifier = Modifier.weight(1f),
        )
        // Search toggle
        IconButton(
            onClick  = if (isSearchVisible) onHideFileSearch else onShowFileSearch,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector        = Icons.Default.Search,
                contentDescription = if (isSearchVisible) "Close search" else "Search files",
                tint               = if (isSearchVisible) colors.accent else colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
        // Reveal active file
        IconButton(onClick = onRevealActiveFile, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector        = Icons.Default.MyLocation,
                contentDescription = "Reveal active file",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
        // New file
        IconButton(onClick = onNewFile, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = "New file",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
        // New folder
        IconButton(onClick = onNewFolder, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector        = Icons.Default.CreateNewFolder,
                contentDescription = "New folder",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
        // More menu
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector        = Icons.Default.MoreVert,
                    contentDescription = "More file actions",
                    tint               = colors.textSecondary,
                    modifier           = Modifier.size(16.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                    text    = { Text("Refresh") },
                    onClick = { menuOpen = false; onRefresh() },
                )
                DropdownMenuItem(
                    text    = { Text("Export Project\u2026") },
                    onClick = { menuOpen = false; onExportProject() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text    = { Text("Rename Project") },
                    onClick = { menuOpen = false; onRenameProject() },
                )
                DropdownMenuItem(
                    text    = { Text("Remove from List", color = LocalIdeColors.current.error) },
                    onClick = { menuOpen = false; onRemoveProject() },
                )
            }
        }
    }
}

// ── Top app bar ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeTopBar(
    projectName: String,
    activeTab: dev.androidide.viewmodel.model.EditorTab?,
    fileTree: List<FileNode>,
    isPreviewVisible: Boolean,
    autoSave: Boolean,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onFind: () -> Unit,
    onReplace: () -> Unit,
    onTogglePreview: () -> Unit,
    onOpenFile: (String) -> Unit,
    onMenuClick: (() -> Unit)?,
) {
    val colors          = LocalIdeColors.current
    var overflowOpen    by remember { mutableStateOf(false) }
    var pathDropdownOpen by remember { mutableStateOf(false) }

    val fileName = activeTab?.displayName ?: "Android IDE"
    val filePath = when {
        activeTab != null       -> fileTree.pathTo(activeTab.documentUri) ?: projectName
        projectName.isNotEmpty() -> projectName
        else                    -> ""
    }

    // Siblings for the path dropdown quick-switcher
    val siblings = remember(activeTab?.documentUri, fileTree) {
        activeTab?.documentUri?.let { uri ->
            fun List<FileNode>.findSiblings(target: String): List<FileNode>? {
                for (node in this) {
                    if (node.isDirectory && node.isExpanded) {
                        if (node.children.any { it.documentUri == target }) return node.children
                        node.children.findSiblings(target)?.let { return it }
                    }
                }
                return null
            }
            fileTree.findSiblings(uri)
        } ?: emptyList()
    }

    TopAppBar(
        title = {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Text(
                    text     = fileName,
                    style    = MaterialTheme.typography.titleSmall,
                    color    = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (filePath.isNotEmpty()) {
                    Box {
                        Text(
                            text     = filePath,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.run {
                                if (siblings.isNotEmpty()) {
                                    combinedClickableModifier(onClick = { pathDropdownOpen = true })
                                } else this
                            },
                        )
                        if (siblings.isNotEmpty()) {
                            DropdownMenu(
                                expanded         = pathDropdownOpen,
                                onDismissRequest = { pathDropdownOpen = false },
                            ) {
                                siblings.forEach { sibling ->
                                    DropdownMenuItem(
                                        text    = { Text(sibling.displayName) },
                                        onClick = {
                                            pathDropdownOpen = false
                                            if (!sibling.isDirectory) onOpenFile(sibling.documentUri)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        navigationIcon = {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector        = Icons.Default.Menu,
                        contentDescription = "Open sidebar",
                        tint               = colors.accent,
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }
        },
        actions = {
            // Save (hidden when autoSave is on)
            if (!autoSave) {
                IconButton(onClick = onSave, enabled = activeTab != null) {
                    Icon(
                        imageVector        = Icons.Default.Save,
                        contentDescription = "Save",
                        tint               = if (activeTab != null) colors.textSecondary else colors.textDisabled,
                    )
                }
            }
            // Run / Preview — always visible, always enabled
            IconButton(onClick = onTogglePreview) {
                Icon(
                    imageVector        = Icons.Default.PlayArrow,
                    contentDescription = if (isPreviewVisible) "Hide preview" else "Preview / Run",
                    tint               = if (isPreviewVisible) colors.accent else colors.textSecondary,
                )
            }
            // Overflow menu
            Box {
                IconButton(onClick = { overflowOpen = true }) {
                    Icon(
                        imageVector        = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint               = colors.textSecondary,
                    )
                }
                DropdownMenu(
                    expanded         = overflowOpen,
                    onDismissRequest = { overflowOpen = false },
                ) {
                    DropdownMenuItem(
                        text        = { Text("Save As\u2026") },
                        leadingIcon = { Icon(Icons.Default.SaveAs, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick     = { overflowOpen = false; onSaveAs() },
                    )
                    DropdownMenuItem(
                        text        = { Text("Find") },
                        leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick     = { overflowOpen = false; onFind() },
                    )
                    DropdownMenuItem(
                        text        = { Text("Find & Replace") },
                        leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick     = { overflowOpen = false; onReplace() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor         = colors.surface,
            titleContentColor      = colors.textPrimary,
            actionIconContentColor = colors.textSecondary,
        ),
        modifier = Modifier.height(52.dp),
    )
}

// Simple clickable Modifier extension for Text path-dropdown trigger
private fun Modifier.combinedClickableModifier(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

// ── File operation dialogs ─────────────────────────────────────────────────────

@Composable
private fun FileOpDialogHost(
    dialog: FileOpDialog?,
    ideViewModel: IdeViewModel,
) {
    when (dialog) {
        is FileOpDialog.Rename -> RenameDialog(
            node      = dialog.node,
            onConfirm = { name -> ideViewModel.renameNode(dialog.node, name) },
            onDismiss = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.Delete -> DeleteConfirmDialog(
            node      = dialog.node,
            onConfirm = { ideViewModel.deleteNode(dialog.node) },
            onDismiss = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.CreateFile -> NameInputDialog(
            title        = "New File",
            label        = "File name",
            initialValue = "",
            onConfirm    = { name -> ideViewModel.createFileInDirectory(dialog.parentNode, name) },
            onDismiss    = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.CreateFolder -> NameInputDialog(
            title        = "New Folder",
            label        = "Folder name",
            initialValue = "",
            onConfirm    = { name -> ideViewModel.createFolderInDirectory(dialog.parentNode, name) },
            onDismiss    = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.Duplicate -> NameInputDialog(
            title        = "Duplicate",
            label        = "New name",
            initialValue = "Copy of ${dialog.node.displayName}",
            onConfirm    = { name -> ideViewModel.duplicateFile(dialog.node, name) },
            onDismiss    = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.UnsavedClose -> UnsavedCloseDialog(
            displayName = dialog.displayName,
            onSaveClose = { ideViewModel.saveAndCloseTab(dialog.tabId) },
            onDiscard   = { ideViewModel.confirmCloseTab(dialog.tabId) },
            onCancel    = ideViewModel::dismissFileOpDialog,
        )
        null -> Unit
    }
}

// ── Dialog composables ─────────────────────────────────────────────────────────

@Composable
private fun RenameDialog(node: FileNode, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(node.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Rename") },
        text    = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("New name") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteConfirmDialog(node: FileNode, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Delete") },
        text    = {
            Text("Permanently delete \u201c${node.displayName}\u201d?" +
                if (node.isDirectory) "\n\nAll files inside will also be deleted." else "")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = LocalIdeColors.current.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameInputDialog(
    title: String,
    label: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(title) },
        text    = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UnsavedCloseDialog(
    displayName: String,
    onSaveClose: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title   = { Text("Unsaved Changes") },
        text    = { Text("\u201c$displayName\u201d has unsaved changes. Save before closing?") },
        confirmButton   = { TextButton(onClick = onSaveClose) { Text("Save & Close") } },
        dismissButton   = {
            Row {
                TextButton(onClick = onDiscard) { Text("Discard", color = LocalIdeColors.current.error) }
                TextButton(onClick = onCancel)  { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ExitConfirmDialog(
    onSaveAll: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title   = { Text("Unsaved Changes") },
        text    = { Text("You have unsaved changes. Save all before exiting?") },
        confirmButton   = { TextButton(onClick = onSaveAll) { Text("Save All & Exit") } },
        dismissButton   = {
            Row {
                TextButton(onClick = onDiscard) { Text("Discard", color = LocalIdeColors.current.error) }
                TextButton(onClick = onCancel)  { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun CrashRecoveryDialog(
    count: Int,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Restore Work?") },
        text    = {
            Text("$count unsaved file(s) were found from a previous session that ended unexpectedly. Restore them?")
        },
        confirmButton   = { TextButton(onClick = onRestore) { Text("Restore") } },
        dismissButton   = { TextButton(onClick = onDismiss) { Text("Discard") } },
    )
}
