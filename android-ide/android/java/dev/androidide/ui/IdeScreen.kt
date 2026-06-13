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
//   1. Compact nav icon row (48dp tall) — Projects, Editor, Git, Terminal, Settings
//   2. Files header (shown when a project is open) — search, reveal, new file, new folder, more
//   3. FileTreePanel (dominates remaining height)
//
// Sidebar auto-close: when a file is opened on a narrow screen the drawer closes
// automatically, the editor receives focus, and the keyboard can appear.
//
// gesturesEnabled=false: prevents horizontal swipe in the Monaco editor from
// accidentally opening the drawer.
//
// imePadding() is applied to the editor content area so it shrinks when the
// soft keyboard appears.

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
import dev.androidide.viewmodel.model.ancestorsOf
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

    // ── Shared FileTreePanel composable ────────────────────────────────────
    //
    // [onCloseDrawer] is called after opening a file on narrow screens so the
    // sidebar collapses automatically and the editor is immediately visible.
    val fileTreePanelContent: @Composable (Modifier, onCloseDrawer: (() -> Unit)?) -> Unit =
        { mod, onCloseDrawer ->
            FileTreePanel(
                nodes                    = uiState.fileTree,
                clipboardItems           = uiState.clipboardItems,
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
                    else {
                        ideViewModel.openFile(uri)
                        ideViewModel.navigateTo(AppScreen.EDITOR)
                        onCloseDrawer?.invoke()
                    }
                },
                onFileDoubleClick        = { uri ->
                    // C011: double-tap opens a permanent (non-preview) tab
                    ideViewModel.openFilePermanent(uri)
                    ideViewModel.navigateTo(AppScreen.EDITOR)
                    onCloseDrawer?.invoke()
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
                    onCloseDrawer?.invoke()
                },
                modifier                 = mod,
            )
        }

    // ── Sidebar composable (shared between wide/narrow) ────────────────────
    val sidebarContent: @Composable (Modifier, onCloseDrawer: (() -> Unit)?) -> Unit =
        { mod, onCloseDrawer ->
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
                HorizontalDivider(thickness = 1.dp, color = colors.separator)
                // C003: Sidebar content is screen-aware.
                // The file tree is only relevant when the Editor is the active screen.
                // Projects and Settings manage their own content in the main area.
                when (uiState.currentScreen) {
                    AppScreen.EDITOR -> {
                        if (uiState.projectRootUri != null) {
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
                            fileTreePanelContent(Modifier.fillMaxSize(), onCloseDrawer)
                        } else {
                            SidebarNoProjectHint(onOpenProject = onOpenProjectFolder)
                        }
                    }
                    AppScreen.PROJECTS, AppScreen.SETTINGS -> {
                        // These screens own their content in the main area.
                        // Sidebar shows only the compact nav strip above.
                    }
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
                            onTogglePreview  = ideViewModel::requestRun,
                            onOpenFile       = { uri -> ideViewModel.openFile(uri) },
                            onRevealInTree   = { uri ->
                                ideViewModel.revealActiveFile()
                                onToggleSidebar?.invoke()
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
            sidebarContent(Modifier.width(240.dp).fillMaxHeight(), null)
            VerticalDivider(thickness = 1.dp, color = colors.separator)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                mainContent(null, Modifier.fillMaxSize())
            }
        }
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope       = rememberCoroutineScope()
        val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
        val toggleDrawer: () -> Unit = {
            scope.launch {
                if (drawerState.isClosed) drawerState.open() else drawerState.close()
            }
        }

        ModalNavigationDrawer(
            drawerState     = drawerState,
            gesturesEnabled = false,    // prevent swipe conflicts with Monaco horizontal scroll
            drawerContent   = {
                ModalDrawerSheet(
                    modifier             = Modifier.width(280.dp),
                    drawerContainerColor = colors.surface,
                ) {
                    sidebarContent(Modifier.fillMaxSize(), closeDrawer)
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
                onTabPin       = ideViewModel::pinTab,
                onCloseOthers  = ideViewModel::closeOtherTabs,
                onCloseAll     = ideViewModel::closeAllTabs,
                onNewBlankTab  = ideViewModel::newBlankTab,
            )
            HorizontalDivider(thickness = 1.dp, color = colors.separator)
        }
        EditorPane(
            activeTab               = uiState.openTabs.firstOrNull { it.isActive },
            isEditorReady           = uiState.isEditorReady,
            isPreviewVisible        = uiState.isPreviewVisible,
            previewHtmlContent      = uiState.previewHtmlContent,
            previewLayout           = s.previewLayout,
            editorCommands          = ideViewModel.editorCommand,
            onEditorReady           = ideViewModel::onEditorReady,
            onEditorMessage         = ideViewModel::onEditorMessage,
            onInsertText            = { text -> ideViewModel.sendEditorCommand(EditorOutbound.InsertText(text)) },
            onExecuteCommand        = { cmd  -> ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand(cmd)) },
            onPasteFromClipboard    = ideViewModel::pasteFromKotlinClipboard,
            showKeyboardToolbar     = s.showKeyboardToolbar,
            showSymbolBar           = s.showSymbolBar,
            customSymbols           = s.customSymbols,
            modifier                = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

// ── Sidebar nav panel — compact icon row ──────────────────────────────────────

@Composable
private fun SidebarNavPanel(
    currentScreen: AppScreen,
    onNavigateProjects: () -> Unit,
    onNavigateEditor: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    val colors = LocalIdeColors.current
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surface),
    ) {
        NavIconButton(
            icon     = Icons.Default.FolderOpen,
            label    = "Projects",
            selected = currentScreen == AppScreen.PROJECTS,
            onClick  = onNavigateProjects,
        )
        NavIconButton(
            icon     = Icons.Default.Code,
            label    = "Editor",
            selected = currentScreen == AppScreen.EDITOR,
            onClick  = onNavigateEditor,
        )
        NavIconButton(
            icon    = Icons.Default.MergeType,
            label   = "Git",
            selected = false,
            enabled  = false,
            onClick  = {},
        )
        NavIconButton(
            icon    = Icons.Default.Terminal,
            label   = "Terminal",
            selected = false,
            enabled  = false,
            onClick  = {},
        )
        NavIconButton(
            icon     = Icons.Default.Settings,
            label    = "Settings",
            selected = currentScreen == AppScreen.SETTINGS,
            onClick  = onNavigateSettings,
        )
    }
}

@Composable
private fun NavIconButton(
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
    IconButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = tint,
            modifier           = Modifier.size(24.dp),
        )
    }
}

// ── Sidebar no-project hint ───────────────────────────────────────────────────
//
// Shown in the Editor-screen sidebar when no project is open.
// Gives a quick path to open a project without navigating away.
@Composable
private fun SidebarNoProjectHint(onOpenProject: () -> Unit) {
    val colors = LocalIdeColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text  = "No project open",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDisabled,
        )
        TextButton(onClick = onOpenProject) {
            Text(
                text  = "Open Project",
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
            )
        }
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
        IconButton(onClick = onRevealActiveFile, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector        = Icons.Default.MyLocation,
                contentDescription = "Reveal active file",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
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
//
// Layout:
//   Left:  Sidebar toggle (hamburger) | breadcrumb file path
//   Right: Save (when autoSave off) | Search | Run/Preview | overflow (Find & Replace, Save As)

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
    onRevealInTree: (String) -> Unit,
    onMenuClick: (() -> Unit)?,
) {
    val colors          = LocalIdeColors.current
    var overflowOpen    by remember { mutableStateOf(false) }
    var pathDropdownOpen by remember { mutableStateOf(false) }

    // Build breadcrumb path from file tree.
    // pathTo returns a leading-slash path like "/src/pages/home.html".
    val filePath = when {
        activeTab != null -> fileTree.pathTo(activeTab.documentUri)
            ?: if (projectName.isNotEmpty()) "/$projectName/${activeTab.displayName}" else activeTab.displayName
        projectName.isNotEmpty() -> projectName
        else -> ""   // C006: no application title when nothing is open
    }

    // Ancestors of the active file (for breadcrumb tap → reveal in tree).
    val ancestors = remember(activeTab?.documentUri, fileTree) {
        activeTab?.documentUri?.let { fileTree.ancestorsOf(it) } ?: emptyList()
    }

    // Siblings at the same level (for file-switch dropdown).
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
            // Show ONLY the file path — no separate file name headline.
            // The path doubles as a tappable quick-switcher for siblings.
            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Text(
                    text     = filePath,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // C007: path is always tappable when a file is active — independent of
                    // whether siblings/ancestors are visible in the currently expanded tree.
                    modifier = if (activeTab != null) Modifier.clickable { pathDropdownOpen = true }
                               else Modifier,
                )
                if (pathDropdownOpen) {
                    DropdownMenu(
                        expanded         = pathDropdownOpen,
                        onDismissRequest = { pathDropdownOpen = false },
                    ) {
                        // Parent directories — C007: ancestor taps close the navigator only.
                        // Sidebar is NOT opened (path navigator is independent of sidebar).
                        // Full in-navigator folder browsing is a Phase 2 enhancement.
                        if (ancestors.isNotEmpty()) {
                            ancestors.forEach { ancestor ->
                                DropdownMenuItem(
                                    text    = { Text("\u25B8 ${ancestor.displayName}/", color = colors.textSecondary) },
                                    onClick = { pathDropdownOpen = false },
                                )
                            }
                            HorizontalDivider()
                        }
                        // Siblings — switch to a file at the same level
                        if (siblings.isNotEmpty()) {
                            siblings.forEach { sibling ->
                                DropdownMenuItem(
                                    text    = {
                                        val isActive = sibling.documentUri == activeTab?.documentUri
                                        Text(
                                            sibling.displayName,
                                            color = if (isActive) colors.accent else colors.textPrimary,
                                        )
                                    },
                                    onClick = {
                                        pathDropdownOpen = false
                                        if (!sibling.isDirectory) onOpenFile(sibling.documentUri)
                                    },
                                )
                            }
                        } else if (ancestors.isEmpty()) {
                            // C007: siblings unavailable — parent folder not expanded in tree yet
                            DropdownMenuItem(
                                text    = { Text("Expand parent folder in sidebar to navigate", color = colors.textDisabled) },
                                onClick = { pathDropdownOpen = false },
                                enabled = false,
                            )
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
            // Save — hidden when autoSave is on
            if (!autoSave) {
                IconButton(onClick = onSave, enabled = activeTab != null) {
                    Icon(
                        imageVector        = Icons.Default.Save,
                        contentDescription = "Save",
                        tint               = if (activeTab != null) colors.textSecondary else colors.textDisabled,
                    )
                }
            }
            // Search — promotes Find to a first-class action (C006)
            IconButton(onClick = onFind) {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = "Search",
                    tint               = colors.textSecondary,
                )
            }
            // Run / Preview — always visible
            IconButton(onClick = onTogglePreview) {
                Icon(
                    imageVector        = Icons.Default.PlayArrow,
                    contentDescription = if (isPreviewVisible) "Hide preview" else "Preview / Run",
                    tint               = if (isPreviewVisible) colors.accent else colors.textSecondary,
                )
            }
            // Overflow: less-frequent actions (Find & Replace, Save As)
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
                        text        = { Text("Find & Replace") },
                        leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick     = { overflowOpen = false; onReplace() },
                    )
                    DropdownMenuItem(
                        text        = { Text("Save As\u2026") },
                        leadingIcon = { Icon(Icons.Default.SaveAs, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick     = { overflowOpen = false; onSaveAs() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor         = colors.surface,
            titleContentColor      = colors.textPrimary,
            actionIconContentColor = colors.textSecondary,
        ),
        modifier = Modifier.height(48.dp),
    )
}

// ── File operation dialogs ─────────────────────────────────────────────────────

@Composable
private fun FileOpDialogHost(
    dialog: FileOpDialog?,
    ideViewModel: IdeViewModel,
) {
    when (dialog) {
        is FileOpDialog.Rename -> RenameDialog(
            node      = dialog.node,
            onConfirm = { ideViewModel.renameNode(dialog.node, it) },
            onDismiss = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.Delete -> DeleteDialog(
            node      = dialog.node,
            onConfirm = { ideViewModel.deleteNode(dialog.node) },
            onDismiss = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.CreateFile -> CreateFileDialog(
            parent    = dialog.parentNode,
            onConfirm = { ideViewModel.createFileInDirectory(dialog.parentNode, it) },
            onDismiss = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.CreateFolder -> CreateFolderDialog(
            parent    = dialog.parentNode,
            onConfirm = { ideViewModel.createFolderInDirectory(dialog.parentNode, it) },
            onDismiss = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.Duplicate -> DuplicateDialog(
            node      = dialog.node,
            onConfirm = { ideViewModel.duplicateFile(dialog.node, it) },
            onDismiss = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.UnsavedClose -> UnsavedCloseDialog(
            fileName  = dialog.displayName,
            onSave    = { ideViewModel.saveAndCloseTab(dialog.tabId) },
            onDiscard = { ideViewModel.confirmCloseTab(dialog.tabId) },
            onCancel  = ideViewModel::dismissFileOpDialog,
        )
        null -> {}
    }
}

// ── Dialog composables ─────────────────────────────────────────────────────────

@Composable
private fun RenameDialog(node: FileNode, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(node.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Rename") },
        text    = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("New name") },
                singleLine    = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(node: FileNode, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Delete") },
        text    = { Text("Permanently delete \"${node.displayName}\"? This cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreateFileDialog(parent: FileNode, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("New File") },
        text    = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("File name") },
                singleLine    = true,
                placeholder   = { Text("main.kt") },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreateFolderDialog(parent: FileNode, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("New Folder") },
        text    = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Folder name") },
                singleLine    = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DuplicateDialog(node: FileNode, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("copy_${node.displayName}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Duplicate") },
        text    = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("New name") },
                singleLine    = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Duplicate")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UnsavedCloseDialog(
    fileName: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title   = { Text("Unsaved Changes") },
        text    = { Text("\"$fileName\" has unsaved changes. Save before closing?") },
        confirmButton = { TextButton(onClick = onSave) { Text("Save & Close") } },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDiscard,
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Discard") }
                TextButton(onClick = onCancel) { Text("Cancel") }
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
        confirmButton = { TextButton(onClick = onSaveAll) { Text("Save All") } },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDiscard,
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Discard All") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun CrashRecoveryDialog(count: Int, onRestore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Recover Unsaved Files") },
        text    = { Text("$count file(s) were not saved before the previous session ended. Restore them?") },
        confirmButton = { TextButton(onClick = onRestore) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
    )
}
