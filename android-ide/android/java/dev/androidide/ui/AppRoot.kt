// android-ide/android/java/dev/androidide/ui/AppRoot.kt
//
// Top-level navigation shell.
// Wraps the app in AndroidIDETheme (so dark/light switching applies everywhere).
// Owns all ActivityResultLaunchers so they are registered at the root
// composable level and shared with child screens via callbacks.
//
// Global font scaling:
//   uiFontScale from EditorSettings is applied here via LocalDensity so it
//   affects ALL text in the application — file tree, menus, settings, dialogs,
//   toolbar labels, and any other UI chrome — not just the Monaco editor.
//
// IdeScreen is always the root screen — no bottom NavigationBar.
// Navigation between Projects / Editor / Settings is handled by the sidebar.

package dev.androidide.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.androidide.ui.theme.AndroidIDETheme
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.FileNode

@Composable
fun AppRoot(ideViewModel: IdeViewModel = viewModel()) {
    val uiState by ideViewModel.uiState.collectAsState()
    val context  = LocalContext.current

    // ── State for multi-step flows ──────────────────────────────────────────
    var importTargetDirUri      by remember { mutableStateOf("") }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var createProjectName       by remember { mutableStateOf("") }

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

    // ── Global font scale — applied to ALL Compose text ────────────────────
    // uiFontScale multiplies LocalDensity.fontScale so that every Text in the
    // entire composition is scaled: file tree rows, menu items, settings labels,
    // dialogs, toolbar labels, and status bar text.
    val baseDensity  = LocalDensity.current
    val uiFontScale  = uiState.editorSettings.uiFontScale

    AndroidIDETheme(appTheme = uiState.appTheme) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density   = baseDensity.density,
                fontScale = uiFontScale,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                // IdeScreen is always the root — sidebar handles all navigation.
                IdeScreen(
                    ideViewModel        = ideViewModel,
                    uiState             = uiState,
                    onOpenProjectFolder = { openProjectLauncher.launch(null) },
                    onCreateBlankProject = {
                        createProjectName       = "MyProject"
                        showCreateProjectDialog = true
                    },
                    onSaveAs            = { saveAsLauncher.launch(activeTabName) },
                    onImportFilesAt     = { node ->
                        importTargetDirUri = node.documentUri
                        importFilesLauncher.launch(arrayOf("*/*"))
                    },
                    onImportFilesAtRoot = {
                        importTargetDirUri = uiState.projectRootUri ?: ""
                        if (importTargetDirUri.isNotEmpty()) importFilesLauncher.launch(arrayOf("*/*"))
                    },
                )
            }
        }
    }

    // ── Create Blank Project dialog ─────────────────────────────────────────
    if (showCreateProjectDialog) {
        AlertDialog(
            onDismissRequest = { showCreateProjectDialog = false },
            title   = { Text("New Project") },
            text    = {
                Column {
                    OutlinedTextField(
                        value         = createProjectName,
                        onValueChange = { createProjectName = it },
                        label         = { Text("Project name") },
                        singleLine    = true,
                        placeholder   = { Text("MyProject") },
                    )
                    val dir = uiState.editorSettings.defaultProjectDir
                    if (dir.isNotEmpty()) {
                        Text(
                            text  = "Location: $dir",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = androidx.compose.ui.Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createProjectName.isNotBlank()) {
                            ideViewModel.createBlankProject(createProjectName.trim())
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

    // ── Remove Project confirmation dialog ──────────────────────────────────
    val removeUri = uiState.confirmRemoveProjectUri
    if (removeUri != null) {
        AlertDialog(
            onDismissRequest = { ideViewModel.cancelRemoveProject() },
            title   = { Text("Remove Project") },
            text    = {
                Text("Remove this project from the list? The files on disk will NOT be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick  = { ideViewModel.confirmRemoveProject() },
                    colors   = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { ideViewModel.cancelRemoveProject() }) { Text("Cancel") }
            },
        )
    }
}
