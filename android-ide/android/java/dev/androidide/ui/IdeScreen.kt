// android-ide/android/java/dev/androidide/ui/IdeScreen.kt
//
// Root IDE screen composable — adaptive layout.
//
// Migration note (2026-06-12):
//   Replaces the root Slint layout in ui/main.slint.
//   Same layout structure (app bar + sidebar + editor column + status bar);
//   same adaptive threshold (600dp) from the Slint NARROW_THRESHOLD_DP constant.
//
// Layout:
//   Wide (≥ 600dp):  [ TopBar                              ]  48dp
//                    [ FileTree (240dp) │ Editor column     ]  flex
//                    [ StatusBar                            ]  22dp
//
//   Narrow (< 600dp): TopBar with menu icon → opens ModalNavigationDrawer
//                     Editor column fills the width
//                     StatusBar at the bottom

package dev.androidide.ui

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.androidide.ui.components.EditorPane
import dev.androidide.ui.components.EditorTabBar
import dev.androidide.ui.components.FileTreePanel
import dev.androidide.ui.components.IdeStatusBar
import dev.androidide.ui.theme.*
import dev.androidide.viewmodel.IdeViewModel
import kotlinx.coroutines.launch

@Composable
fun IdeScreen(ideViewModel: IdeViewModel = viewModel()) {
    val uiState by ideViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWide = screenWidthDp >= 600  // mirrors Slint NARROW_THRESHOLD_DP = 600

    val openProjectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist read + write access across device reboots.
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            ideViewModel.openProject(uri.toString())
        }
    }

    val activeTab = uiState.openTabs.firstOrNull { it.isActive }

    if (isWide) {
        // ── Wide layout: permanent sidebar ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(IdeBackground)
                .systemBarsPadding(),
        ) {
            IdeTopBar(
                projectName  = uiState.projectName,
                onOpenProject = { openProjectLauncher.launch(null) },
                onSave       = { ideViewModel.saveActiveFile() },
                onMenuClick  = null,
            )

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Sidebar: 240dp — matches Slint sidebar width
                FileTreePanel(
                    nodes      = uiState.fileTree,
                    onFileClick = ideViewModel::openFile,
                    onDirToggle = ideViewModel::toggleDirectory,
                    modifier   = Modifier.width(240.dp).fillMaxHeight().background(IdeSurface),
                )
                VerticalDivider(thickness = 1.dp, color = IdeSeparator)

                // Editor column
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (uiState.openTabs.isNotEmpty()) {
                        EditorTabBar(
                            tabs         = uiState.openTabs,
                            onTabSelected = ideViewModel::selectTab,
                            onTabClosed  = ideViewModel::closeTab,
                        )
                        HorizontalDivider(thickness = 1.dp, color = IdeSeparator)
                    }
                    EditorPane(
                        activeTab          = activeTab,
                        isEditorReady      = uiState.isEditorReady,
                        isPreviewVisible   = uiState.isPreviewVisible,
                        previewUrl         = uiState.previewUrl,
                        onEditorReady      = ideViewModel::onEditorReady,
                        onEditorMessage    = ideViewModel::onEditorMessage,
                        modifier           = Modifier.weight(1f).fillMaxWidth(),
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
        // ── Narrow layout: sidebar in modal drawer ──────────────────────────
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier            = Modifier.width(280.dp),
                    drawerContainerColor = IdeSurface,
                ) {
                    FileTreePanel(
                        nodes      = uiState.fileTree,
                        onFileClick = { uri ->
                            ideViewModel.openFile(uri)
                            scope.launch { drawerState.close() }
                        },
                        onDirToggle = ideViewModel::toggleDirectory,
                        modifier   = Modifier.fillMaxSize(),
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IdeBackground)
                    .systemBarsPadding(),
            ) {
                IdeTopBar(
                    projectName  = uiState.projectName,
                    onOpenProject = { openProjectLauncher.launch(null) },
                    onSave       = { ideViewModel.saveActiveFile() },
                    onMenuClick  = { scope.launch { drawerState.open() } },
                )
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (uiState.openTabs.isNotEmpty()) {
                        EditorTabBar(
                            tabs         = uiState.openTabs,
                            onTabSelected = ideViewModel::selectTab,
                            onTabClosed  = ideViewModel::closeTab,
                        )
                        HorizontalDivider(thickness = 1.dp, color = IdeSeparator)
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
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text  = "Android IDE",
                    style = MaterialTheme.typography.titleMedium,
                    color = IdeTextPrimary,
                )
                if (projectName.isNotEmpty()) {
                    Text(
                        text     = projectName,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = IdeTextSecondary,
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
                        tint               = IdeTextSecondary,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSave) {
                Icon(
                    imageVector        = Icons.Default.Save,
                    contentDescription = "Save file",
                    tint               = IdeTextSecondary,
                )
            }
            IconButton(onClick = onOpenProject) {
                Icon(
                    imageVector        = Icons.Default.FolderOpen,
                    contentDescription = "Open project folder",
                    tint               = IdeTextSecondary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor         = IdeSurface,
            titleContentColor      = IdeTextPrimary,
            actionIconContentColor = IdeTextSecondary,
        ),
        modifier = Modifier.height(48.dp),
    )
}
