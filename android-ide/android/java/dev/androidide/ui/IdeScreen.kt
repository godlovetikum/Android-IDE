// android-ide/android/java/dev/androidide/ui/IdeScreen.kt
//
// Editor screen — adaptive layout with file tree and Monaco editor.
//
// Layout:
//   Wide (≥ 600dp): permanent sidebar (240dp) + editor column
//   Narrow (< 600dp): ModalNavigationDrawer + editor column
//
// The sidebar shows navigation shortcuts (Projects, Settings) above the file tree.
//
// imePadding() is applied to the editor content area so it shrinks correctly
// when the soft keyboard appears.

package dev.androidide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.data.model.PreviewLayout
import dev.androidide.editor.EditorOutbound
import dev.androidide.ui.components.EditorPane
import dev.androidide.ui.components.EditorTabBar
import dev.androidide.ui.components.FileTreePanel
import dev.androidide.ui.components.IdeStatusBar
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.AppScreen
import dev.androidide.viewmodel.model.FileNode
import dev.androidide.viewmodel.model.FileOpDialog
import dev.androidide.viewmodel.model.IdeUiState
import dev.androidide.viewmodel.model.pathTo
import kotlinx.coroutines.launch

@Composable
fun IdeScreen(
    ideViewModel: IdeViewModel,
    uiState: IdeUiState,
    onOpenProjectFolder: () -> Unit,
    onSaveAs: () -> Unit,
    onImportFilesAt: (FileNode) -> Unit,
) {
    val colors        = LocalIdeColors.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWide        = screenWidthDp >= 600
    val activeTab     = uiState.openTabs.firstOrNull { it.isActive }

    // Exit confirmation back handler
    BackHandler(enabled = uiState.currentScreen == AppScreen.EDITOR) {
        if (!ideViewModel.requestExit()) {
            ideViewModel.navigateTo(AppScreen.PROJECTS)
        }
    }

    // Shared FileTreePanel args builder to avoid duplication
    val fileTreePanelContent: @Composable (Modifier) -> Unit = { modifier ->
        FileTreePanel(
            nodes                    = uiState.fileTree,
            clipboard                = uiState.clipboard,
            clipboardIsCut           = uiState.clipboardIsCut,
            onFileClick              = ideViewModel::openFile,
            onDirToggle              = ideViewModel::toggleDirectory,
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
            onNewFileAtRoot          = {
                ideViewModel.showCreateFileDialog(
                    FileNode(
                        documentUri = uiState.projectRootUri ?: "",
                        displayName = "",
                        mimeType    = "vnd.android.document/directory",
                    )
                )
            },
            onNewFolderAtRoot        = {
                ideViewModel.showCreateFolderDialog(
                    FileNode(
                        documentUri = uiState.projectRootUri ?: "",
                        displayName = "",
                        mimeType    = "vnd.android.document/directory",
                    )
                )
            },
            onImportFilesAtRoot      = {
                val rootNode = FileNode(
                    documentUri = uiState.projectRootUri ?: "",
                    displayName = "",
                    mimeType    = "vnd.android.document/directory",
                )
                onImportFilesAt(rootNode)
            },
            onExportProject          = ideViewModel::exportProject,
            onRefresh                = ideViewModel::refreshProject,
            modifier                 = modifier,
        )
    }

    if (isWide) {
        Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
            IdeTopBar(
                projectName      = uiState.projectName,
                activeTab        = activeTab,
                fileTree         = uiState.fileTree,
                isPreviewVisible = uiState.isPreviewVisible,
                onSave           = ideViewModel::saveActiveFile,
                onSaveAs         = onSaveAs,
                onFind           = { ideViewModel.sendEditorCommand(EditorOutbound.ShowFind) },
                onReplace        = { ideViewModel.sendEditorCommand(EditorOutbound.ShowReplace) },
                onTogglePreview  = ideViewModel::togglePreview,
                onMenuClick      = null,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding(),
            ) {
                // Sidebar: navigation + file tree
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight()
                        .background(colors.surface),
                ) {
                    SidebarNavItems(
                        onNavigateProjects = { ideViewModel.navigateTo(AppScreen.PROJECTS) },
                        onNavigateSettings = { ideViewModel.navigateTo(AppScreen.SETTINGS) },
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.separator)
                    fileTreePanelContent(Modifier.fillMaxSize())
                }
                VerticalDivider(thickness = 1.dp, color = colors.separator)
                EditorContent(
                    uiState      = uiState,
                    ideViewModel = ideViewModel,
                    modifier     = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            IdeStatusBar(
                cursorLine    = uiState.cursorLine,
                cursorColumn  = uiState.cursorColumn,
                fileName      = activeTab?.displayName ?: "",
                language      = activeTab?.language ?: "",
                statusMessage = uiState.statusMessage,
            )
        }
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope       = rememberCoroutineScope()
        ModalNavigationDrawer(
            drawerState   = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier             = Modifier.width(280.dp),
                    drawerContainerColor = colors.surface,
                ) {
                    SidebarNavItems(
                        onNavigateProjects = { ideViewModel.navigateTo(AppScreen.PROJECTS) },
                        onNavigateSettings = { ideViewModel.navigateTo(AppScreen.SETTINGS) },
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.separator)
                    fileTreePanelContent(
                        Modifier.fillMaxSize()
                    )
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
                IdeTopBar(
                    projectName      = uiState.projectName,
                    activeTab        = activeTab,
                    fileTree         = uiState.fileTree,
                    isPreviewVisible = uiState.isPreviewVisible,
                    onSave           = ideViewModel::saveActiveFile,
                    onSaveAs         = onSaveAs,
                    onFind           = { ideViewModel.sendEditorCommand(EditorOutbound.ShowFind) },
                    onReplace        = { ideViewModel.sendEditorCommand(EditorOutbound.ShowReplace) },
                    onTogglePreview  = ideViewModel::togglePreview,
                    onMenuClick      = { scope.launch { drawerState.open() } },
                )
                EditorContent(
                    uiState      = uiState,
                    ideViewModel = ideViewModel,
                    modifier     = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding(),
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
            activeTab        = uiState.openTabs.firstOrNull { it.isActive },
            isEditorReady    = uiState.isEditorReady,
            isPreviewVisible = uiState.isPreviewVisible,
            previewUrl       = uiState.previewUrl,
            previewLayout    = uiState.editorSettings.previewLayout,
            editorCommands   = ideViewModel.editorCommand,
            onEditorReady    = ideViewModel::onEditorReady,
            onEditorMessage  = ideViewModel::onEditorMessage,
            onInsertText     = { text -> ideViewModel.sendEditorCommand(EditorOutbound.InsertText(text)) },
            onExecuteCommand = { cmd  -> ideViewModel.sendEditorCommand(EditorOutbound.ExecuteCommand(cmd)) },
            modifier         = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

// ── Sidebar navigation items ──────────────────────────────────────────────────

@Composable
private fun SidebarNavItems(
    onNavigateProjects: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    val colors = LocalIdeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateProjects, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector        = Icons.Default.FolderOpen,
                contentDescription = "Projects",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onNavigateSettings, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector        = Icons.Default.Settings,
                contentDescription = "Settings",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(18.dp),
            )
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
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onFind: () -> Unit,
    onReplace: () -> Unit,
    onTogglePreview: () -> Unit,
    onMenuClick: (() -> Unit)?,
) {
    val colors       = LocalIdeColors.current
    var overflowOpen by remember { mutableStateOf(false) }

    val title    = activeTab?.displayName ?: "Android IDE"
    val subtitle = when {
        activeTab != null    -> fileTree.pathTo(activeTab.documentUri) ?: projectName
        projectName.isNotEmpty() -> projectName
        else                 -> ""
    }

    val previewSupported = activeTab?.language in setOf("html", "markdown")

    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text     = title,
                    style    = MaterialTheme.typography.titleSmall,
                    color    = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text     = subtitle,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector        = Icons.Default.Code,
                        contentDescription = "Open file tree",
                        tint               = colors.accent,
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }
        },
        actions = {
            // Save
            IconButton(onClick = onSave) {
                Icon(
                    imageVector        = Icons.Default.Save,
                    contentDescription = "Save",
                    tint               = colors.textSecondary,
                )
            }
            // Preview / Run
            IconButton(
                onClick  = onTogglePreview,
                enabled  = previewSupported,
            ) {
                Icon(
                    imageVector        = Icons.Default.PlayArrow,
                    contentDescription = if (isPreviewVisible) "Hide preview" else "Preview / Run",
                    tint               = when {
                        !previewSupported -> colors.textDisabled
                        isPreviewVisible  -> colors.accent
                        else              -> colors.textSecondary
                    },
                )
            }
            // Git placeholder (disabled — Phase 2)
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector        = Icons.Default.MergeType,
                    contentDescription = "Git (coming soon)",
                    tint               = colors.textDisabled,
                )
            }
            // Terminal placeholder (disabled — Phase 2)
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector        = Icons.Default.Terminal,
                    contentDescription = "Terminal (coming soon)",
                    tint               = colors.textDisabled,
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
                        text     = { Text("Save As\u2026") },
                        leadingIcon = { Icon(Icons.Default.SaveAs, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick  = { overflowOpen = false; onSaveAs() },
                    )
                    DropdownMenuItem(
                        text    = { Text("Find") },
                        leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { overflowOpen = false; onFind() },
                    )
                    DropdownMenuItem(
                        text    = { Text("Find & Replace") },
                        leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { overflowOpen = false; onReplace() },
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
