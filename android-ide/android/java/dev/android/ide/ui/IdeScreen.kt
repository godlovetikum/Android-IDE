// android-ide/android/java/dev/android/ide/ui/IdeScreen.kt
//
// Root screen — always active. Handles three content areas (PROJECTS / EDITOR / SETTINGS)
// with a persistent sidebar for navigation and file management.
//
// Layout modes:
//   Wide (≥ 600dp) — permanent 240dp sidebar column + content area
//   Narrow (< 600dp) — integrated sidebar column + content area
//
// The sidebar contains:
//   1. Compact nav icon row (48dp tall) — Projects, Editor, Git, Terminal, Settings
//   2. Files header (shown when a project is open) — search, reveal, new file, new folder, more
//   3. FileTreePanel (dominates remaining height)
//
// Opening a file may hide the integrated sidebar surface to give the editor
// focus, but it does not close or destroy the sidebar context.
//
// imePadding() is applied to the editor content area so it shrinks when the
// soft keyboard appears.

package dev.android.ide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.android.ide.editor.EditorOutbound
import dev.android.ide.ui.components.EditorPane
import dev.android.ide.ui.components.EditorTabBar
import dev.android.ide.ui.components.FileTreePanel
import dev.android.ide.ui.components.IdeStatusBar
import dev.android.ide.ui.screen.UnavailableDomainScreen
import dev.android.ide.ui.screen.HomeScreen
import dev.android.ide.ui.screen.ProjectsScreen
import dev.android.ide.ui.screen.ProjectDetailsScreen
import dev.android.ide.ui.screen.SettingsScreen
import dev.android.ide.ui.theme.LocalIdeColors
import dev.android.ide.viewmodel.IdeViewModel
import dev.android.ide.data.model.Project
import dev.android.ide.viewmodel.model.AppScreen
import dev.android.ide.viewmodel.model.FileNode
import dev.android.ide.viewmodel.model.FileOpDialog
import dev.android.ide.viewmodel.model.IdeUiState
import dev.android.ide.viewmodel.model.findNode
import dev.android.ide.viewmodel.model.pathTo
import kotlinx.coroutines.launch

private fun formatRelativeDate(timestampMs: Long): String {
    val elapsedMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minuteMs = 60_000L
    val hourMs = 60 * minuteMs
    val dayMs = 24 * hourMs
    return when {
        elapsedMs < minuteMs -> "just now"
        elapsedMs < hourMs -> "${elapsedMs / minuteMs} min ago"
        elapsedMs < dayMs -> "${elapsedMs / hourMs} hr ago"
        else -> "${elapsedMs / dayMs} days ago"
    }
}

// ── IdeScreen ─────────────────────────────────────────────────────────────────

@Composable
fun IdeScreen(
    ideViewModel: IdeViewModel,
    uiState: IdeUiState,
    onOpenProjectFolder: () -> Unit,
    onCreateBlankProject: (String?) -> Unit,
    onExportProject: (String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onMoveProject: (String) -> Unit,
    onExportDirectory: (FileNode) -> Unit,
    onSaveAs: () -> Unit,
    onImportFilesAt: (FileNode) -> Unit,
    onImportFilesAtRoot: () -> Unit,
) {
    val colors        = LocalIdeColors.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWide        = screenWidthDp >= 600
    val activeTab     = uiState.openTabs.firstOrNull { it.isActive }
    var settingsSectionTarget by remember { mutableStateOf<String?>(null) }
    var narrowSidebarVisible by rememberSaveable { mutableStateOf(true) }
    BackHandler(enabled = uiState.currentScreen == AppScreen.EDITOR && uiState.isEditorSearchVisible) {
        ideViewModel.dismissEditorSearch()
    }
    // Back handler: when on EDITOR, confirm exit or return to Home.
    BackHandler(enabled = uiState.currentScreen == AppScreen.EDITOR && !uiState.isEditorSearchVisible) {
        if (!ideViewModel.requestExit()) {
            ideViewModel.navigateTo(AppScreen.HOME)
        }
    }
    BackHandler(enabled = uiState.currentScreen == AppScreen.PROJECT_DETAILS) {
        ideViewModel.dismissProjectDetails()
    }

    // Monaco is initialized independently of project/file state so the first
    // project does not pay the WebView startup cost. It is deliberately kept
    // outside the user-facing layout until a project is open.
    if (uiState.projectRootUri == null) {
        Box(
            modifier = Modifier
                .size(1.dp)
                .graphicsLayer { alpha = 0f },
        ) {
            EditorContent(
                uiState = uiState,
                ideViewModel = ideViewModel,
                modifier = Modifier.fillMaxSize(),
            )
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
    // [onCloseDrawer] hides the integrated sidebar surface after opening a file
    // on narrow screens so the editor receives the primary panel.
    val fileTreePanelContent: @Composable (Modifier, onCloseDrawer: (() -> Unit)?) -> Unit =
        { mod, onCloseDrawer ->
            val scope = rememberCoroutineScope()
            FileTreePanel(
                nodes                    = uiState.fileTree,
                clipboardItems           = uiState.clipboardItems,
                clipboardIsCut           = uiState.clipboardIsCut,
                projectName              = uiState.projectName,
                activeTabDocumentUri     = activeTab?.documentUri,
                locateTargetUri          = uiState.locateTargetUri,
                locateRequestToken       = uiState.locateRequestToken,
                onLocateConsumed         = ideViewModel::clearLocateRequest,
                hideGitFolder            = uiState.editorSettings.hideGitFolder,
                isMultiSelectMode        = uiState.isMultiSelectMode,
                selectedUris             = uiState.selectedUris,
                isSearchVisible          = uiState.isSearchVisible,
                isContentSearchVisible   = uiState.isContentSearchVisible,
                fileSearchQuery          = uiState.fileSearchQuery,
                fileSearchResults        = uiState.fileSearchResults,
                contentSearchQuery       = uiState.contentSearchQuery,
                contentSearchResults     = uiState.contentSearchResults,
                onFileClick              = { uri ->
                    if (uiState.isMultiSelectMode) ideViewModel.toggleNodeSelection(uri)
                    else {
                        ideViewModel.openFile(uri)
                        ideViewModel.navigateTo(AppScreen.EDITOR)
                        onCloseDrawer?.invoke()
                        // appears immediately without requiring a second tap on the editor.
                        scope.launch {
                            kotlinx.coroutines.delay(300)
                            ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand("focusEditor"))
                        }
                    }
                },
                onFileDoubleClick        = { uri ->
                    ideViewModel.openFilePermanent(uri)
                    ideViewModel.navigateTo(AppScreen.EDITOR)
                    onCloseDrawer?.invoke()
                    scope.launch {
                        kotlinx.coroutines.delay(300)
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
                onExportDirectory        = onExportDirectory,
                onNewFileAtRoot          = { ideViewModel.showCreateFileDialog(rootNode) },
                onNewFolderAtRoot        = { ideViewModel.showCreateFolderDialog(rootNode) },
                onImportFilesAtRoot      = onImportFilesAtRoot,
                onExportProject          = {
                    uiState.projectRootUri?.let(onExportProject)
                },
                onPasteAtRoot            = { ideViewModel.pasteFileNode(rootNode) },
                onRefresh                = ideViewModel::refreshProject,
                onShowProjectDetails     = {
                    uiState.projectRootUri?.let { ideViewModel.showProjectDetails(it) }
                },
                onDeleteProject          = {
                    uiState.projectRootUri?.let { ideViewModel.requestDeleteProject(it) }
                },
                onRemoveProject          = {
                    uiState.projectRootUri?.let { ideViewModel.requestRemoveProject(it) }
                },
                onCopyPath               = ideViewModel::copyPathToClipboard,
                onToggleNodeSelection    = ideViewModel::toggleNodeSelection,
                onExitSelectionMode      = ideViewModel::exitSelectionMode,
                onSearchQueryChange      = ideViewModel::searchFiles,
                onContentSearchQueryChange = ideViewModel::searchProjectContents,
                onHideFileSearch         = ideViewModel::hideFileSearch,
                onHideContentSearch      = ideViewModel::hideContentSearch,
                onSearchFileSelect       = { uri ->
                    if (uiState.isContentSearchVisible) ideViewModel.hideContentSearch()
                    else ideViewModel.hideFileSearch()
                    ideViewModel.openFile(uri)
                    ideViewModel.navigateTo(AppScreen.EDITOR)
                    onCloseDrawer?.invoke()
                    scope.launch {
                        kotlinx.coroutines.delay(300)
                        ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand("focusEditor"))
                    }
                },
                modifier                 = mod,
            )
        }

    // ── Sidebar composable (shared between wide/narrow) ────────────────────
    val sidebarContent: @Composable (Modifier, onCloseDrawer: (() -> Unit)?) -> Unit =
        { mod, onCloseDrawer ->
            fun closeAfter(action: () -> Unit) {
                onCloseDrawer?.invoke()
                action()
            }
            Column(
                modifier = mod
                    .background(colors.surface)
                    .fillMaxHeight(),
            ) {
                SidebarNavPanel(
                    currentScreen      = uiState.currentScreen,
                    hasProject         = uiState.projectRootUri != null,
                    onNavigateHome     = { closeAfter { ideViewModel.navigateTo(AppScreen.HOME) } },
                    onNavigateProjects = { closeAfter { ideViewModel.navigateTo(AppScreen.PROJECTS) } },
                    onNavigateEditor   = { closeAfter { ideViewModel.navigateTo(AppScreen.EDITOR) } },
                    onNavigateTerminal = { closeAfter { ideViewModel.navigateTo(AppScreen.TERMINAL) } },
                    onNavigateBrowser  = { closeAfter { ideViewModel.navigateTo(AppScreen.BROWSER) } },
                    onNavigateGit      = { closeAfter { ideViewModel.navigateTo(AppScreen.GIT) } },
                    onNavigateDetails  = {
                        uiState.projectRootUri?.let { closeAfter { ideViewModel.showProjectDetails(it) } }
                    },
                    onNavigateSettings = { closeAfter { ideViewModel.navigateTo(AppScreen.SETTINGS) } },
                )
                HorizontalDivider(thickness = 1.dp, color = colors.separator)
                // The file tree is only relevant when the Editor is the active screen.
                // Projects and Settings manage their own content in the main area.
                when (uiState.currentScreen) {
                    AppScreen.HOME, AppScreen.TERMINAL, AppScreen.BROWSER, AppScreen.GIT ->
                        Spacer(Modifier.weight(1f))
                    AppScreen.EDITOR -> {
                        if (uiState.projectRootUri != null) {
                            FilesHeader(
                                isSearchVisible    = uiState.isSearchVisible,
                                onShowFileSearch   = ideViewModel::showFileSearch,
                                onHideFileSearch   = ideViewModel::hideFileSearch,
                                onShowContentSearch = ideViewModel::showContentSearch,
                                onRevealActiveFile = ideViewModel::revealActiveFile,
                                onNewFile          = { ideViewModel.showCreateFileDialog(rootNode) },
                                onNewFolder        = { ideViewModel.showCreateFolderDialog(rootNode) },
                                onImportFiles      = onImportFilesAtRoot,
                                onRefresh          = ideViewModel::refreshProject,
                                onExportProject    = {
                                    uiState.projectRootUri?.let(onExportProject)
                                },
                                onMoveProject      = {
                                    uiState.projectRootUri?.let(onMoveProject)
                                },
                                onShowProjectDetails = {
                                    uiState.projectRootUri?.let { ideViewModel.showProjectDetails(it) }
                                },
                                onRemoveProject    = {
                                    uiState.projectRootUri?.let { ideViewModel.requestRemoveProject(it) }
                                },
                                onDeleteProject    = {
                                    uiState.projectRootUri?.let { ideViewModel.requestDeleteProject(it) }
                                },
                            )
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                fileTreePanelContent(Modifier.fillMaxSize(), onCloseDrawer)
                                if (uiState.fileTreeLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                                        color = colors.accent,
                                    )
                                }
                                if (uiState.fileMutationLoading) {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        CircularProgressIndicator(color = colors.accent)
                                        Text(
                                            text = "Processing…",
                                            color = colors.textSecondary,
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            SidebarNoProjectHint(
                                onOpenProject = {
                                    onCloseDrawer?.invoke()
                                    onOpenProjectFolder()
                                },
                            )
                        }
                    }
                    AppScreen.PROJECTS -> {
                        SidebarRecentProjectsList(
                            projects       = uiState.recentProjects,
                            modifier       = Modifier.weight(1f).fillMaxWidth(),
                            onOpenProject  = { uri ->
                                closeAfter { ideViewModel.openProject(uri) }
                            },
                            onShowDetails  = { uri ->
                                closeAfter { ideViewModel.showProjectDetails(uri) }
                            },
                            onDuplicate    = { uri -> closeAfter { onDuplicateProject(uri) } },
                            onExport       = { uri -> closeAfter { onExportProject(uri) } },
                            onMove         = { uri -> closeAfter { onMoveProject(uri) } },
                            onRemove       = { uri -> closeAfter { ideViewModel.requestRemoveProject(uri) } },
                            onDelete       = { uri -> closeAfter { ideViewModel.requestDeleteProject(uri) } },
                        )
                    }
                    AppScreen.SETTINGS -> {
                        SidebarSettingsShortcuts(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onSectionClick = {
                                settingsSectionTarget = it
                                onCloseDrawer?.invoke()
                            },
                        )
                    }
                    AppScreen.PROJECT_DETAILS -> {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

    // ── Content composable ────────────────────────────────────────────────
    val mainContent: @Composable (onToggleSidebar: (() -> Unit)?, Modifier) -> Unit =
        { onToggleSidebar, mod ->
            when (uiState.currentScreen) {
                AppScreen.HOME -> {
                    HomeScreen(
                        hasProject = uiState.projectRootUri != null,
                        onNavigate = ideViewModel::navigateTo,
                    )
                }
                AppScreen.EDITOR -> {
                    Column(modifier = mod.background(colors.background)) {
                        IdeTopBar(
                            projectName      = uiState.projectName,
                            activeTab        = activeTab,
                            fileTree         = uiState.fileTree,
                            projectRootUri   = uiState.projectRootUri,
                            isPreviewVisible = uiState.isPreviewVisible,
                            autoSave         = uiState.editorSettings.autoSave,
                            onSave           = ideViewModel::saveActiveFile,
                            onSaveAs         = onSaveAs,
                            onFind           = ideViewModel::showEditorFind,
                            onReplace        = ideViewModel::showEditorReplace,
                            onTogglePreview  = ideViewModel::requestRun,
                            onOpenFile       = { uri -> ideViewModel.openFile(uri) },
                            onRevealInTree   = { uri ->
                                ideViewModel.revealActiveFile()
                            },
                            onMenuClick      = onToggleSidebar,
                            loadNavChildren  = { uri -> ideViewModel.loadNavChildren(uri) },
                        )
                        if (uiState.projectRootUri != null) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                EditorContent(
                                    uiState      = uiState,
                                    ideViewModel = ideViewModel,
                                    modifier     = Modifier.fillMaxSize(),
                                )
                                if (!uiState.isEditorReady || uiState.editorFileLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center),
                                        color = colors.accent,
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (uiState.projectRootUri == null) {
                                        "Open a project to start editing"
                                    } else {
                                        "Open a file from the sidebar to start editing"
                                    },
                                    color = colors.textSecondary,
                                )
                            }
                        }
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
                        onExportProject      = onExportProject,
                        onDuplicateProject   = onDuplicateProject,
                        onMoveProject        = onMoveProject,
                        onNavigationIconClick = onToggleSidebar,
                    )
                }
                AppScreen.PROJECT_DETAILS -> {
                    ProjectDetailsScreen(
                        uiState = uiState,
                        onBack = ideViewModel::dismissProjectDetails,
                        onDuplicate = onDuplicateProject,
                        onExport = onExportProject,
                        onMove = onMoveProject,
                        onRename = ideViewModel::renameProjectInRegistry,
                        onRemove = ideViewModel::requestRemoveProject,
                        onDelete = ideViewModel::requestDeleteProject,
                    )
                }
                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        uiState               = uiState,
                        ideViewModel          = ideViewModel,
                        onNavigationIconClick = onToggleSidebar,
                        scrollToSection       = settingsSectionTarget,
                        onScrollConsumed      = { settingsSectionTarget = null },
                    )
                }
                AppScreen.TERMINAL -> UnavailableDomainScreen(
                    title = "Terminal",
                    description = "The global terminal surface is unavailable until the runtime service is implemented.",
                    onBackToHome = { ideViewModel.navigateTo(AppScreen.HOME) },
                )
                AppScreen.BROWSER -> UnavailableDomainScreen(
                    title = "Browser",
                    description = "Browser tabs and previews are reserved for the browser phase.",
                    onBackToHome = { ideViewModel.navigateTo(AppScreen.HOME) },
                )
                AppScreen.GIT -> UnavailableDomainScreen(
                    title = "Git",
                    description = "Repository and global Git workflows are reserved for the Git phase.",
                    onBackToHome = { ideViewModel.navigateTo(AppScreen.HOME) },
                )
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
    } else if (narrowSidebarVisible) {
        // The sidebar is an integrated mobile surface, not a modal overlay.
        // It occupies the primary panel while open, then yields the panel to
        // the selected domain when navigation occurs.
        sidebarContent(Modifier.fillMaxSize()) { narrowSidebarVisible = false }
    } else {
        mainContent({ narrowSidebarVisible = true }, Modifier.fillMaxSize())
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

    uiState.projectSwitchRequest?.let { request ->
        ProjectSwitchConfirmDialog(
            targetProjectName = request.projectName,
            onSave          = ideViewModel::saveAndSwitchProject,
            onDiscard       = ideViewModel::discardAndSwitchProject,
            onCancel        = ideViewModel::cancelProjectSwitch,
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
            activeTabContent       = uiState.openTabs.firstOrNull { it.isActive }?.let { tab ->
                ideViewModel.editorContentForTab(tab.id, tab.content)
            },
            isEditorReady           = uiState.isEditorReady,
            editorBindRevision     = uiState.editorBindRevision,
            isPreviewVisible        = uiState.isPreviewVisible,
            previewHtmlContent      = uiState.previewHtmlContent,
            previewLayout           = s.previewLayout,
            editorCommands          = ideViewModel.editorCommand,
            onEditorReady           = ideViewModel::onEditorReady,
            onEditorRendererGone   = ideViewModel::onEditorRendererGone,
            onEditorMessage         = ideViewModel::onEditorMessage,
            onInsertText            = { text -> ideViewModel.sendEditorCommand(EditorOutbound.InsertText(text)) },
            onExecuteCommand        = { cmd  -> ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand(cmd)) },
            onPasteFromClipboard    = ideViewModel::pasteFromKotlinClipboard,
            hasEditorSelection      = uiState.hasEditorSelection,
            showKeyboardToolbar     = s.showKeyboardToolbar,
            showSymbolBar           = s.showSymbolBar,
            customSymbols           = s.customSymbols,
            tabCursorPositions      = uiState.tabCursorPositions,
            tabScrollPositions      = uiState.tabScrollPositions,
            modifier                = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

// ── Sidebar nav panel — 3-column icon grid ────────────────────────────────────
//
// 3-column compact grid that shows visible text labels.  Each cell is 56dp tall.
// Row 1: Projects | Editor | Settings

@Composable
private fun SidebarNavPanel(
    currentScreen: AppScreen,
    hasProject: Boolean,
    onNavigateHome: () -> Unit,
    onNavigateProjects: () -> Unit,
    onNavigateEditor: () -> Unit,
    onNavigateTerminal: () -> Unit,
    onNavigateBrowser: () -> Unit,
    onNavigateGit: () -> Unit,
    onNavigateDetails: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    val colors = LocalIdeColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        // ── Row 1: Home, Projects, Editor ─────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            NavCell(
                icon     = Icons.Default.Home,
                label    = "Home",
                selected = currentScreen == AppScreen.HOME,
                onClick  = onNavigateHome,
                modifier = Modifier.weight(1f),
            )
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
                enabled  = hasProject,
                onClick  = onNavigateEditor,
                modifier = Modifier.weight(1f),
            )
        }
        // ── Row 2: Terminal, Browser, Git ───────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            NavCell(
                icon     = Icons.Default.Terminal,
                label    = "Terminal",
                selected = currentScreen == AppScreen.TERMINAL,
                onClick  = onNavigateTerminal,
                modifier = Modifier.weight(1f),
            )
            NavCell(
                icon     = Icons.Default.Public,
                label    = "Browser",
                selected = currentScreen == AppScreen.BROWSER,
                onClick  = onNavigateBrowser,
                modifier = Modifier.weight(1f),
            )
            NavCell(
                icon     = Icons.Default.MergeType,
                label    = "Git",
                selected = currentScreen == AppScreen.GIT,
                onClick  = onNavigateGit,
                modifier = Modifier.weight(1f),
            )
        }
        // ── Row 3: Project details and Settings ─────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            NavCell(
                icon     = Icons.Default.Info,
                label    = "Details",
                selected = currentScreen == AppScreen.PROJECT_DETAILS,
                enabled  = hasProject,
                onClick  = onNavigateDetails,
                modifier = Modifier.weight(1f),
            )
            NavCell(
                icon     = Icons.Default.Settings,
                label    = "Settings",
                selected = currentScreen == AppScreen.SETTINGS,
                onClick  = onNavigateSettings,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
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

//
// Shown in the sidebar when the PROJECTS screen is active.
// Tapping a project opens it and closes the drawer on narrow layouts.

@Composable
private fun SidebarRecentProjectsList(
    projects: List<Project>,
    onOpenProject: (String) -> Unit,
    onShowDetails: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onExport: (String) -> Unit,
    onMove: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current
    if (projects.isEmpty()) {
        Column(
            modifier = modifier
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
        LazyColumn(modifier = modifier.fillMaxSize()) {
            item {
                Text(
                    text     = "Recent",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(projects.sortedByDescending { it.lastOpenedMs }) { project ->
                var menuOpen by remember(project.uri) { mutableStateOf(false) }
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
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "Project options", tint = colors.textSecondary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Details") }, onClick = { menuOpen = false; onShowDetails(project.uri) })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuOpen = false; onDuplicate(project.uri) })
                        DropdownMenuItem(text = { Text("Export") }, onClick = { menuOpen = false; onExport(project.uri) })
                        DropdownMenuItem(text = { Text("Move") }, onClick = { menuOpen = false; onMove(project.uri) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Delete permanently") }, onClick = { menuOpen = false; onDelete(project.uri) })
                        DropdownMenuItem(text = { Text("Remove from registry") }, onClick = { menuOpen = false; onRemove(project.uri) })
                    }
                }
                Text(
                    text = "Last opened ${formatRelativeDate(project.lastOpenedMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 44.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

//
// Shown in the sidebar when the SETTINGS screen is active.
// Displays the available settings sections as a visual index.

@Composable
private fun SidebarSettingsShortcuts(
    modifier: Modifier = Modifier,
    onSectionClick: (String) -> Unit = {},
) {
    val colors = LocalIdeColors.current
    // Each pair: (sectionKey matching SectionHeader title, sidebar display label).
    val sections = listOf(
        "App Theme"       to "Appearance",
        "UI Font Size"    to "UI Scale",
        "Editor"          to "Editor Display",
        "File Tree"       to "File Tree",
        "Project Storage" to "Project Storage",
        "Controls"        to "Keyboard",
    )
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text(
                text     = "Sections",
                style    = MaterialTheme.typography.labelSmall,
                color    = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(sections) { (sectionKey, label) ->
            Text(
                text     = label,
                style    = MaterialTheme.typography.bodySmall,
                color    = colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSectionClick(sectionKey) }
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
    onShowContentSearch: () -> Unit,
    onRevealActiveFile: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onRefresh: () -> Unit,
    onExportProject: () -> Unit,
    onMoveProject: () -> Unit,
    onShowProjectDetails: () -> Unit,
    onRemoveProject: () -> Unit,
    onDeleteProject: () -> Unit,
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
        //       requirement on Android (36dp IconButton + 4dp system touch delegation = ~40dp,
        //       approaching the 44dp minimum; 28dp was reliably misfire-prone in practice).
        IconButton(
            onClick  = {
                if (isSearchVisible) {
                    onHideFileSearch()
                } else {
                    onShowFileSearch()
                }
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector        = Icons.Default.Search,
                contentDescription = if (isSearchVisible) "Close filename search" else "Search filenames",
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
        IconButton(onClick = onShowProjectDetails, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector        = Icons.Default.Info,
                contentDescription = "Project details",
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
                DropdownMenuItem(
                    text    = { Text("Search project contents") },
                    onClick = { menuOpen = false; onShowContentSearch() },
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
                DropdownMenuItem(
                    text    = { Text("Move Storage\u2026") },
                    onClick = { menuOpen = false; onMoveProject() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text    = { Text("Project details") },
                    onClick = { menuOpen = false; onShowProjectDetails() },
                )
                DropdownMenuItem(
                    text    = { Text("Delete permanently") },
                    onClick = { menuOpen = false; onDeleteProject() },
                )
                DropdownMenuItem(
                    text    = { Text("Remove from registry", color = LocalIdeColors.current.error) },
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
    activeTab: dev.android.ide.viewmodel.model.EditorTab?,
    fileTree: List<FileNode>,
    projectRootUri: String?,
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
    loadNavChildren: suspend (String) -> List<FileNode>,
) {
    val colors           = LocalIdeColors.current
    var overflowOpen     by remember { mutableStateOf(false) }
    var pathDropdownOpen by remember { mutableStateOf(false) }

    // SPCK-style sibling navigator: the menu lists the current folder's siblings.
    // Tapping a folder descends; tapping a file opens it; ".." ascends.
    var navStack   by remember { mutableStateOf(emptyList<String>()) }
    var navItems   by remember { mutableStateOf(emptyList<FileNode>()) }
    var navLoading by remember { mutableStateOf(false) }

    // Build breadcrumb path from file tree.
    // pathTo returns a leading-slash path like "/src/pages/home.html".
    val filePath = when {
        activeTab != null -> fileTree.pathTo(activeTab.documentUri)
            ?: if (projectName.isNotEmpty()) "/$projectName/${activeTab.displayName}" else activeTab.displayName
        projectName.isNotEmpty() -> projectName
        else -> ""
    }

    // requiring expand state (findNode traverses all cached children unconditionally).
    val activeParentUri = remember(activeTab?.documentUri, fileTree) {
        activeTab?.documentUri?.let { uri ->
            fileTree.findNode(uri)?.parentDocumentUri
        }
    }

    // Load the current sibling list whenever the menu opens or navigation changes.
    LaunchedEffect(pathDropdownOpen, navStack) {
        if (!pathDropdownOpen) {
            navStack   = emptyList()
            navItems   = emptyList()
            navLoading = false
            return@LaunchedEffect
        }
        val targetUri = navStack.lastOrNull() ?: activeParentUri ?: projectRootUri ?: return@LaunchedEffect
        navLoading = true
        navItems   = loadNavChildren(targetUri)
        navLoading = false
    }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight()) {
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (activeTab != null) {
                    Box {
                        IconButton(onClick = { pathDropdownOpen = true }) {
                            Icon(Icons.Default.MoreVert, "Navigate path", tint = colors.textSecondary)
                        }
                    }
                }
                if (pathDropdownOpen) {
                    DropdownMenu(
                        expanded         = pathDropdownOpen,
                        onDismissRequest = { pathDropdownOpen = false },
                    ) {
                        val currentUri = navStack.lastOrNull() ?: activeParentUri ?: projectRootUri
                        val parentUri = currentUri?.let { uri -> fileTree.findNode(uri)?.parentDocumentUri }
                        DropdownMenuItem(
                            text = { Text("..", color = colors.accent) },
                            enabled = navStack.isNotEmpty() || parentUri != null,
                            onClick = {
                                if (navStack.isNotEmpty()) navStack = navStack.dropLast(1)
                                else parentUri?.let { navStack = listOf(it) }
                            },
                        )
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
                                                navStack = navStack + item.documentUri
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
        is FileOpDialog.BinaryOpenError -> BinaryOpenErrorDialog(
            fileName  = dialog.fileName,
            onDismiss = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.Rename -> RenameDialog(
            node         = dialog.node,
            onConfirm    = { ideViewModel.renameNode(dialog.node, it) },
            onDismiss    = ideViewModel::dismissFileOpDialog,
            errorMessage = dialog.errorMessage,
        )
        is FileOpDialog.Delete -> DeleteDialog(
            node      = dialog.node,
            onConfirm = { ideViewModel.deleteNode(dialog.node, dialog.selectedNodes) },
            onDismiss = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.CreateFile -> CreateFileDialog(
            parent       = dialog.parentNode,
            onConfirm    = { ideViewModel.createFileInDirectory(dialog.parentNode, it) },
            onDismiss    = ideViewModel::dismissFileOpDialog,
            errorMessage = dialog.errorMessage,
            isSubmitting  = dialog.isSubmitting,
        )
        is FileOpDialog.CreateFolder -> CreateFolderDialog(
            parent       = dialog.parentNode,
            onConfirm    = { ideViewModel.createFolderInDirectory(dialog.parentNode, it) },
            onDismiss    = ideViewModel::dismissFileOpDialog,
            errorMessage = dialog.errorMessage,
            isSubmitting  = dialog.isSubmitting,
        )
        is FileOpDialog.Duplicate -> DuplicateDialog(
            node         = dialog.node,
            onConfirm    = { ideViewModel.duplicateFile(dialog.node, it) },
            onDismiss    = ideViewModel::dismissFileOpDialog,
            errorMessage = dialog.errorMessage,
        )
        is FileOpDialog.UnsavedClose -> UnsavedCloseDialog(
            fileName  = dialog.displayName,
            onSave    = { ideViewModel.saveAndCloseTab(dialog.tabId) },
            onDiscard = { ideViewModel.confirmCloseTab(dialog.tabId) },
            onCancel  = ideViewModel::dismissFileOpDialog,
        )
        is FileOpDialog.SaveAs -> SaveAsDialog(
            suggestedName = dialog.suggestedName,
            onConfirm     = { ideViewModel.saveAsAtPath(it) },
            onDismiss     = ideViewModel::dismissFileOpDialog,
        )
        null -> {}
    }
}

@Composable
private fun BinaryOpenErrorDialog(fileName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Binary file") },
        text = {
            Text(
                "\"$fileName\" cannot be opened in the text editor. " +
                    "Opening binary files is not supported yet and is planned for a later phase."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

// ── Dialog composables ─────────────────────────────────────────────────────────

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
private fun RenameDialog(
    node: FileNode,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null,
) {
    var name by remember { mutableStateOf(node.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Rename") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("New name") },
                    singleLine    = true,
                )
                if (errorMessage != null) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
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
private fun CreateFileDialog(
    parent: FileNode,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null,
    isSubmitting: Boolean = false,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("New File") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("File name") },
                    singleLine    = true,
                    placeholder   = { Text("main.kt") },
                    textStyle     = MaterialTheme.typography.bodyMedium,
                )
                if (errorMessage != null) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank() && !isSubmitting) {
                Text(if (isSubmitting) "Creating…" else "Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") } },
    )
}

@Composable
private fun CreateFolderDialog(
    parent: FileNode,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null,
    isSubmitting: Boolean = false,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("New Folder") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Folder name") },
                    singleLine    = true,
                    textStyle     = MaterialTheme.typography.bodyMedium,
                )
                if (errorMessage != null) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank() && !isSubmitting) {
                Text(if (isSubmitting) "Creating…" else "Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") } },
    )
}

@Composable
private fun DuplicateDialog(
    node: FileNode,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null,
) {
    var name by remember { mutableStateOf("copy_${node.displayName}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Duplicate") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("New name") },
                    singleLine    = true,
                    textStyle     = MaterialTheme.typography.bodyMedium,
                )
                if (errorMessage != null) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
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
private fun ProjectSwitchConfirmDialog(
    targetProjectName: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title   = { Text("Unsaved Changes") },
        text    = {
            Text("Save changes before switching to $targetProjectName?")
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save and Switch") }
        },
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
private fun CrashRecoveryDialog(count: Int, onRestore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Recover Unsaved Files") },
        text    = { Text("$count file(s) were not saved before the previous session ended. Restore them?") },
        confirmButton = { TextButton(onClick = onRestore) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
    )
}
