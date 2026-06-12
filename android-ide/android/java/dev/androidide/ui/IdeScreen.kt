// android-ide/android/java/dev/androidide/ui/IdeScreen.kt
//
// Editor screen — adaptive layout with file tree and Monaco editor.
// Theme colours are read from LocalIdeColors so dark/light switching
// applies without restarting the activity.
//
// Layout:
//   Wide (≥ 600dp): permanent sidebar (240dp) + editor column
//   Narrow (< 600dp): ModalNavigationDrawer + editor column

package dev.androidide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.ui.components.EditorPane
import dev.androidide.ui.components.EditorTabBar
import dev.androidide.ui.components.FileTreePanel
import dev.androidide.ui.components.IdeStatusBar
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.FileNode
import dev.androidide.viewmodel.model.FileOpDialog
import dev.androidide.viewmodel.model.IdeUiState
import kotlinx.coroutines.launch

@Composable
fun IdeScreen(
    ideViewModel: IdeViewModel,
    uiState: IdeUiState,
    onOpenProjectFolder: () -> Unit,
) {
    val colors         = LocalIdeColors.current
    val screenWidthDp  = LocalConfiguration.current.screenWidthDp
    val isWide         = screenWidthDp >= 600
    val activeTab      = uiState.openTabs.firstOrNull { it.isActive }

    if (isWide) {
        Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
            IdeTopBar(
                projectName   = uiState.projectName,
                onOpenProject = onOpenProjectFolder,
                onSave        = ideViewModel::saveActiveFile,
                onMenuClick   = null,
            )
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                FileTreePanel(
                    nodes                    = uiState.fileTree,
                    onFileClick              = ideViewModel::openFile,
                    onDirToggle              = ideViewModel::toggleDirectory,
                    onShowRenameDialog       = ideViewModel::showRenameDialog,
                    onShowDeleteDialog       = ideViewModel::showDeleteDialog,
                    onShowCreateFileDialog   = ideViewModel::showCreateFileDialog,
                    onShowCreateFolderDialog = ideViewModel::showCreateFolderDialog,
                    onShowDuplicateDialog    = ideViewModel::showDuplicateDialog,
                    modifier                 = Modifier.width(240.dp).fillMaxHeight().background(colors.surface),
                )
                VerticalDivider(thickness = 1.dp, color = colors.separator)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (uiState.openTabs.isNotEmpty()) {
                        EditorTabBar(
                            tabs          = uiState.openTabs,
                            onTabSelected = ideViewModel::selectTab,
                            onTabClosed   = ideViewModel::closeTab,
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.separator)
                    }
                    EditorPane(
                        activeTab        = activeTab,
                        isEditorReady    = uiState.isEditorReady,
                        isPreviewVisible = uiState.isPreviewVisible,
                        previewUrl       = uiState.previewUrl,
                        onEditorReady    = ideViewModel::onEditorReady,
                        onEditorMessage  = ideViewModel::onEditorMessage,
                        modifier         = Modifier.weight(1f).fillMaxWidth(),
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
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        ModalNavigationDrawer(
            drawerState   = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier             = Modifier.width(280.dp),
                    drawerContainerColor = colors.surface,
                ) {
                    FileTreePanel(
                        nodes                    = uiState.fileTree,
                        onFileClick              = { uri ->
                            ideViewModel.openFile(uri)
                            scope.launch { drawerState.close() }
                        },
                        onDirToggle              = ideViewModel::toggleDirectory,
                        onShowRenameDialog       = ideViewModel::showRenameDialog,
                        onShowDeleteDialog       = ideViewModel::showDeleteDialog,
                        onShowCreateFileDialog   = ideViewModel::showCreateFileDialog,
                        onShowCreateFolderDialog = ideViewModel::showCreateFolderDialog,
                        onShowDuplicateDialog    = ideViewModel::showDuplicateDialog,
                        modifier                 = Modifier.fillMaxSize(),
                    )
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
                IdeTopBar(
                    projectName   = uiState.projectName,
                    onOpenProject = onOpenProjectFolder,
                    onSave        = ideViewModel::saveActiveFile,
                    onMenuClick   = { scope.launch { drawerState.open() } },
                )
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (uiState.openTabs.isNotEmpty()) {
                        EditorTabBar(
                            tabs          = uiState.openTabs,
                            onTabSelected = ideViewModel::selectTab,
                            onTabClosed   = ideViewModel::closeTab,
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.separator)
                    }
                    EditorPane(
                        activeTab        = activeTab,
                        isEditorReady    = uiState.isEditorReady,
                        isPreviewVisible = uiState.isPreviewVisible,
                        previewUrl       = uiState.previewUrl,
                        onEditorReady    = ideViewModel::onEditorReady,
                        onEditorMessage  = ideViewModel::onEditorMessage,
                        modifier         = Modifier.weight(1f).fillMaxWidth(),
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
        }
    }

    // File operation dialogs — driven by IdeUiState.fileOpDialog
    FileOpDialogHost(dialog = uiState.fileOpDialog, ideViewModel = ideViewModel)
}

// ── Top app bar ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeTopBar(
    projectName: String,
    onOpenProject: () -> Unit,
    onSave: () -> Unit,
    onMenuClick: (() -> Unit)?,
) {
    val colors = LocalIdeColors.current
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text  = "Android IDE",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                if (projectName.isNotEmpty()) {
                    Text(
                        text     = projectName,
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
                        imageVector        = Icons.Default.Menu,
                        contentDescription = "Open file tree",
                        tint               = colors.textSecondary,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSave) {
                Icon(
                    imageVector        = Icons.Default.Save,
                    contentDescription = "Save file",
                    tint               = colors.textSecondary,
                )
            }
            IconButton(onClick = onOpenProject) {
                Icon(
                    imageVector        = Icons.Default.FolderOpen,
                    contentDescription = "Open project folder",
                    tint               = colors.textSecondary,
                )
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
        null -> Unit
    }
}

@Composable
private fun RenameDialog(
    node: FileNode,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(node.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Rename") },
        text    = {
            OutlinedTextField(
                value        = text,
                onValueChange = { text = it },
                label        = { Text("New name") },
                singleLine   = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled  = text.isNotBlank(),
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteConfirmDialog(
    node: FileNode,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Delete") },
        text    = {
            Text(
                "Permanently delete \"${node.displayName}\"?" +
                if (node.isDirectory) "\n\nAll files inside will also be deleted." else ""
            )
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
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                label         = { Text(label) },
                singleLine    = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled  = text.isNotBlank(),
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
