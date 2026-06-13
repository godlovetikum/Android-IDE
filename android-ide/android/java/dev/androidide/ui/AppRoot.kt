// android-ide/android/java/dev/androidide/ui/AppRoot.kt
//
// Top-level navigation shell.
// Wraps the app in AndroidIDETheme (so dark/light switching applies everywhere).
// Owns all ActivityResultLaunchers so they are registered at the root
// composable level and shared with child screens via callbacks.
//
// Bottom NavigationBar is only shown on PROJECTS and SETTINGS screens — the
// EDITOR screen uses its sidebar for navigation.

package dev.androidide.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.androidide.ui.screen.ProjectsScreen
import dev.androidide.ui.screen.SettingsScreen
import dev.androidide.ui.theme.AndroidIDETheme
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.AppScreen
import dev.androidide.viewmodel.model.FileNode

@Composable
fun AppRoot(ideViewModel: IdeViewModel = viewModel()) {
    val uiState by ideViewModel.uiState.collectAsState()
    val context  = LocalContext.current

    // ── State for multi-step flows ──────────────────────────────────────────
    var importTargetDirUri       by remember { mutableStateOf("") }
    var createProjectParentUri   by remember { mutableStateOf("") }
    var showCreateProjectDialog  by remember { mutableStateOf(false) }
    var createProjectName        by remember { mutableStateOf("") }

    // ── Open existing project folder ────────────────────────────────────────
    val openProjectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            ideViewModel.openProject(uri.toString())
        }
    }

    // ── Save As — creates a new document in SAF ────────────────────────────
    val activeTabName  = uiState.openTabs.firstOrNull { it.isActive }?.displayName ?: "untitled"
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            ideViewModel.saveActiveFileAs(uri.toString())
        }
    }

    // ── Import multiple files into the tree ─────────────────────────────────
    val importFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty() && importTargetDirUri.isNotEmpty()) {
            uris.forEach { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            ideViewModel.importFiles(importTargetDirUri, uris.map { it.toString() })
        }
        importTargetDirUri = ""
    }

    // ── Create blank project — pick parent folder then enter a name ─────────
    val createProjectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            createProjectParentUri  = uri.toString()
            createProjectName       = "MyProject"
            showCreateProjectDialog = true
        }
    }

    AndroidIDETheme(appTheme = uiState.appTheme) {
        val colors = LocalIdeColors.current

        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            // ── Screen content ──────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.currentScreen) {
                    AppScreen.PROJECTS -> ProjectsScreen(
                        uiState             = uiState,
                        ideViewModel        = ideViewModel,
                        onOpenProjectFolder = { openProjectLauncher.launch(null) },
                        onCreateBlankProject = { createProjectLauncher.launch(null) },
                    )
                    AppScreen.EDITOR -> IdeScreen(
                        ideViewModel        = ideViewModel,
                        uiState             = uiState,
                        onOpenProjectFolder = { openProjectLauncher.launch(null) },
                        onSaveAs            = { saveAsLauncher.launch(activeTabName) },
                        onImportFilesAt     = { node ->
                            importTargetDirUri = node.documentUri
                            importFilesLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    AppScreen.SETTINGS -> SettingsScreen(
                        uiState      = uiState,
                        ideViewModel = ideViewModel,
                    )
                }
            }

            // ── Bottom navigation bar (only on non-editor screens) ──────────
            if (uiState.currentScreen != AppScreen.EDITOR) {
                NavigationBar(containerColor = colors.surface) {
                    NavigationBarItem(
                        icon     = { Icon(Icons.Default.FolderOpen, contentDescription = "Projects") },
                        label    = { Text("Projects") },
                        selected = uiState.currentScreen == AppScreen.PROJECTS,
                        onClick  = { ideViewModel.navigateTo(AppScreen.PROJECTS) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = colors.accent,
                            selectedTextColor   = colors.accent,
                            unselectedIconColor = colors.textSecondary,
                            unselectedTextColor = colors.textSecondary,
                            indicatorColor      = colors.activeHighlight,
                        ),
                    )
                    NavigationBarItem(
                        icon     = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label    = { Text("Settings") },
                        selected = uiState.currentScreen == AppScreen.SETTINGS,
                        onClick  = { ideViewModel.navigateTo(AppScreen.SETTINGS) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = colors.accent,
                            selectedTextColor   = colors.accent,
                            unselectedIconColor = colors.textSecondary,
                            unselectedTextColor = colors.textSecondary,
                            indicatorColor      = colors.activeHighlight,
                        ),
                    )
                }
            }
        }
    }

    // ── Create Blank Project dialog ─────────────────────────────────────────
    if (showCreateProjectDialog) {
        AlertDialog(
            onDismissRequest = { showCreateProjectDialog = false },
            title   = { Text("New Project") },
            text    = {
                OutlinedTextField(
                    value         = createProjectName,
                    onValueChange = { createProjectName = it },
                    label         = { Text("Project name") },
                    singleLine    = true,
                    placeholder   = { Text("MyProject") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createProjectName.isNotBlank()) {
                            ideViewModel.createBlankProject(
                                createProjectParentUri,
                                createProjectName.trim(),
                            )
                            showCreateProjectDialog = false
                        }
                    },
                    enabled = createProjectName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateProjectDialog = false }) { Text("Cancel") }
            },
        )
    }
}
