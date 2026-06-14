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
import androidx.compose.material.icons.filled.Folder
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
import dev.androidide.data.model.Project
import dev.androidide.viewmodel.model.AppScreen
import dev.androidide.viewmodel.model.FileNode
import dev.androidide.viewmodel.model.FileOpDialog
import dev.androidide.viewmodel.model.IdeUiState
import dev.androidide.viewmodel.model.ancestorsOf
import dev.androidide.viewmodel.model.findNode
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
            // F010: coroutine scope used to post a delayed focusEditor command after
            // the drawer close animation completes.  150 ms gives the slide-out
            // animation time to finish before Monaco consumes the focus request.
            val scope = rememberCoroutineScope()
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
                        // F010: focus Monaco after the drawer closes so the soft keyboard
                        // appears immediately without requiring a second tap on the editor.
                        scope.launch {
                            kotlinx.coroutines.delay(150)
                            ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand("focusEditor"))
                        }
                    }
                },
                onFileDoubleClick        = { uri ->
                    // C011: double-tap opens a permanent (non-preview) tab
                    ideViewModel.openFilePermanent(uri)
                    ideViewModel.navigateTo(AppScreen.EDITOR)
                    onCloseDrawer?.invoke()
                    // F010: same focus fix for double-tap.
                    scope.launch {
                        kotlinx.coroutines.delay(150)
                        ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand("focusEditor"))
                    }
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
                onPasteAtRoot            = { ideViewModel.pasteFileNode(rootNode) },
                onRefresh                = ideViewModel::refreshProject,
                onRenameProject          = { ideViewModel.noteStatusMessage("Rename Project available in Phase 2") },
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
                    // F010: focus Monaco after search-select too.
                    scope.launch {
                        kotlinx.coroutines.delay(150)
                        ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand("focusEditor"))
                    }
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
                                onRenameProject    = { ideViewModel.noteStatusMessage("Rename Project available in Phase 2") },
                                onRemoveProject    = {
                                    uiState.projectRootUri?.let { ideViewModel.requestRemoveProject(it) }
                                },
                            )
                            fileTreePanelContent(Modifier.fillMaxSize(), onCloseDrawer)
                        } else {
                            SidebarNoProjectHint(onOpenProject = onOpenProjectFolder)
                        }
                    }
                    // F009: sidebar content for Projects and Settings screens.
                    AppScreen.PROJECTS -> {
                        SidebarRecentProjectsList(
                            projects       = uiState.recentProjects,
                            onOpenProject  = { uri ->
                                ideViewModel.openProject(uri)
                                onCloseDrawer?.invoke()
                            },
                        )
                    }
                    AppScreen.SETTINGS -> {
                        SidebarSettingsShortcuts()
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
                            // F003: SAF-backed navigator bypasses in-memory expand state.
                            loadNavChildren  = { uri -> ideViewModel.loadNavChildren(uri) },
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
            // F011: explicit scrim overlay behind the drawer content area.
            // gesturesEnabled=false prevents the built-in swipe but some OEM
            // firmware also disables the built-in scrim tap-to-close; this
            // explicit overlay is always reliable regardless of firmware.
            Box(modifier = Modifier.fillMaxSize()) {
                mainContent(toggleDrawer, Modifier.fillMaxSize())
                if (drawerState.currentValue == DrawerValue.Open) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                            .clickable { scope.launch { drawerState.close() } },
                    )
                }
            }
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

// ── Sidebar nav panel — 3-column icon grid ────────────────────────────────────
//
// F008: replaced the single 48dp-tall Row of 5 unlabelled buttons with a
// 3-column compact grid that shows visible text labels.  Each cell is 56dp tall.
// Row 1: Projects | Editor | Settings
// Row 2: Git | Terminal  (disabled, centred; Git/Terminal land in Phase 2/3)

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
            .background(colors.surface),
    ) {
        // ── Row 1: Projects, Editor, Settings ─────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            NavCell(
                icon     = Icons.Default.FolderOpen,
                label    = "Projects",
                selected = currentScreen == AppScreen.PROJECTS,
                onClick  = onNavigateProjects,
                modifier = Modifier.weight(1f),
            )
            NavCell(
                icon     = Icons.Default.Code,
                label    = "Editor",
                selected = currentScreen == AppScreen.EDITOR,
                onClick  = onNavigateEditor,
                modifier = Modifier.weight(1f),
            )
            NavCell(
                icon     = Icons.Default.Settings,
                label    = "Settings",
                selected = currentScreen == AppScreen.SETTINGS,
                onClick  = onNavigateSettings,
                modifier = Modifier.weight(1f),
            )
        }
        // ── Row 2: Git, Terminal (disabled — Phase 2 / Phase 3) ───────────
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(0.5f))
            NavCell(
                icon     = Icons.Default.MergeType,
                label    = "Git",
                selected = false,
                enabled  = false,
                onClick  = {},
                modifier = Modifier.weight(1f),
            )
            NavCell(
                icon     = Icons.Default.Terminal,
                label    = "Terminal",
                selected = false,
                enabled  = false,
                onClick  = {},
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun NavCell(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current
    val tint = when {
        !enabled -> colors.textDisabled
        selected -> colors.accent
        else     -> colors.textSecondary
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = tint,
            modifier           = Modifier.size(24.dp),
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
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

// ── F009: Sidebar — Recent projects list (PROJECTS screen) ───────────────────
//
// Shown in the sidebar when the PROJECTS screen is active.
// Tapping a project opens it and closes the drawer on narrow layouts.

@Composable
private fun SidebarRecentProjectsList(
    projects: List<Project>,
    onOpenProject: (String) -> Unit,
) {
    val colors = LocalIdeColors.current
    if (projects.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = "No recent projects",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDisabled,
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text     = "Recent",
                style    = MaterialTheme.typography.labelSmall,
                color    = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            projects.forEach { project ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(project.uri) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint               = colors.textSecondary,
                        modifier           = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text     = project.name,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = colors.textPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── F009: Sidebar — Settings section shortcuts (SETTINGS screen) ──────────────
//
// Shown in the sidebar when the SETTINGS screen is active.
// Displays the available settings sections as a visual index.

@Composable
private fun SidebarSettingsShortcuts() {
    val colors = LocalIdeColors.current
    val sections = listOf(
        "Appearance",
        "Editor Display",
        "Keyboard",
        "File Tree",
        "Project Storage",
        "UI Scale",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text     = "Sections",
            style    = MaterialTheme.typography.labelSmall,
            color    = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        sections.forEach { section ->
            Text(
                text     = section,
                style    = MaterialTheme.typography.bodySmall,
                color    = colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
        // F014: increased from 28dp/16dp to 36dp/20dp to meet 44dp effective touch target
        //       requirement on Android (36dp IconButton + 4dp system touch delegation = ~40dp,
        //       approaching the 44dp minimum; 28dp was reliably misfire-prone in practice).
        IconButton(
            onClick  = if (isSearchVisible) onHideFileSearch else onShowFileSearch,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector        = Icons.Default.Search,
                contentDescription = if (isSearchVisible) "Close search" else "Search files",
                tint               = if (isSearchVisible) colors.accent else colors.textSecondary,
                modifier           = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onRevealActiveFile, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector        = Icons.Default.MyLocation,
                contentDescription = "Reveal active file",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onNewFile, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = "New file",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onNewFolder, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector        = Icons.Default.CreateNewFolder,
                contentDescription = "New folder",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(20.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector        = Icons.Default.MoreVert,
                    contentDescription = "More file actions",
                    tint               = colors.textSecondary,
                    modifier           = Modifier.size(20.dp),
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
    // F003: SAF-backed navigator — loads children without expand-state dependency.
    loadNavChildren: suspend (String) -> List<FileNode>,
) {
    val colors           = LocalIdeColors.current
    val scope            = rememberCoroutineScope()
    var overflowOpen     by remember { mutableStateOf(false) }
    var pathDropdownOpen by remember { mutableStateOf(false) }

    // F003: navigator state machine — independent of in-memory tree expand state.
    // navStack: history of visited folder URIs (most recent last).
    // navItems: current directory's children as returned from SAF.
    var navStack   by remember { mutableStateOf(emptyList<String>()) }
    var navItems   by remember { mutableStateOf(emptyList<FileNode>()) }
    var navLoading by remember { mutableStateOf(false) }

    // Build breadcrumb path from file tree.
    // pathTo returns a leading-slash path like "/src/pages/home.html".
    val filePath = when {
        activeTab != null -> fileTree.pathTo(activeTab.documentUri)
            ?: if (projectName.isNotEmpty()) "/$projectName/${activeTab.displayName}" else activeTab.displayName
        projectName.isNotEmpty() -> projectName
        else -> ""   // C006: no application title when nothing is open
    }

    // Ancestors of the active file (for breadcrumb display inside the dropdown).
    val ancestors = remember(activeTab?.documentUri, fileTree) {
        activeTab?.documentUri?.let { fileTree.ancestorsOf(it) } ?: emptyList()
    }

    // F003: parent folder URI of the active file — obtained from the tree without
    // requiring expand state (findNode traverses all cached children unconditionally).
    val activeParentUri = remember(activeTab?.documentUri, fileTree) {
        activeTab?.documentUri?.let { uri ->
            fileTree.findNode(uri)?.parentDocumentUri
        }
    }

    // F003: load children whenever the dropdown opens or the current nav folder changes.
    LaunchedEffect(pathDropdownOpen, navStack) {
        if (!pathDropdownOpen) {
            navStack   = emptyList()
            navItems   = emptyList()
            navLoading = false
            return@LaunchedEffect
        }
        val targetUri = navStack.lastOrNull() ?: activeParentUri ?: return@LaunchedEffect
        navLoading = true
        navItems   = loadNavChildren(targetUri)
        navLoading = false
    }

    TopAppBar(
        title = {
            // Show ONLY the file path — no separate file name headline.
            // The path doubles as a tappable quick-switcher via the SAF-backed navigator.
            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Text(
                    text     = filePath,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (activeTab != null) Modifier.clickable { pathDropdownOpen = true }
                               else Modifier,
                )
                if (pathDropdownOpen) {
                    DropdownMenu(
                        expanded         = pathDropdownOpen,
                        onDismissRequest = { pathDropdownOpen = false },
                    ) {
                        // ── Breadcrumb header ─────────────────────────────────────────
                        if (ancestors.isNotEmpty()) {
                            ancestors.forEach { ancestor ->
                                DropdownMenuItem(
                                    text    = { Text("\u25B8 ${ancestor.displayName}/", color = colors.textSecondary) },
                                    onClick = {
                                        // Navigate into this ancestor directory via SAF.
                                        scope.launch { navStack = navStack + ancestor.documentUri }
                                    },
                                )
                            }
                            HorizontalDivider()
                        }
                        // ── Up button ────────────────────────────────────────────────
                        if (navStack.size > 1) {
                            DropdownMenuItem(
                                text    = { Text("\u2191 Up", color = colors.accent) },
                                onClick = { scope.launch { navStack = navStack.dropLast(1) } },
                            )
                            HorizontalDivider()
                        }
                        // ── Directory contents from SAF ───────────────────────────────
                        when {
                            navLoading -> {
                                DropdownMenuItem(
                                    text    = { Text("Loading\u2026", color = colors.textDisabled) },
                                    onClick = {},
                                    enabled = false,
                                )
                            }
                            navItems.isEmpty() && !navLoading -> {
                                DropdownMenuItem(
                                    text    = { Text("Empty folder", color = colors.textDisabled) },
                                    onClick = {},
                                    enabled = false,
                                )
                            }
                            else -> {
                                navItems.forEach { item ->
                                    val isActive = item.documentUri == activeTab?.documentUri
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (item.isDirectory) {
                                                    Icon(
                                                        imageVector        = Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint               = colors.textSecondary,
                                                        modifier           = Modifier.size(16.dp),
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("${item.displayName}/", color = colors.textSecondary)
                                                } else {
                                                    Text(
                                                        item.displayName,
                                                        color = if (isActive) colors.accent else colors.textPrimary,
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            if (item.isDirectory) {
                                                // Navigate into sub-folder via SAF.
                                                scope.launch { navStack = navStack + item.documentUri }
                                            } else {
                                                pathDropdownOpen = false
                                                onOpenFile(item.documentUri)
                                            }
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
        // F004: project-relative Save As dialog.
        is FileOpDialog.SaveAs -> SaveAsDialog(
            suggestedName = dialog.suggestedName,
            onConfirm     = { ideViewModel.saveAsAtPath(it) },
            onDismiss     = ideViewModel::dismissFileOpDialog,
        )
        null -> {}
    }
}

// ── Dialog composables ─────────────────────────────────────────────────────────

// F004: Project-relative Save As dialog.
// The user types a path relative to the project root, e.g. "src/utils/Foo.kt".
// Intermediate directories are created automatically by IdeViewModel.saveAsAtPath.
@Composable
private fun SaveAsDialog(
    suggestedName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Save As") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter a path relative to the project root. Use \"/\" to navigate into sub-folders (e.g. src/utils/Foo.kt). Intermediate directories are created automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value         = path,
                    onValueChange = { path = it },
                    label         = { Text("Path") },
                    placeholder   = { Text("src/NewFile.kt") },
                    singleLine    = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (path.isNotBlank()) onConfirm(path.trim()) },
                enabled = path.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

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
