// android-ide/android/java/dev/androidide/ui/AppRoot.kt
//
// Top-level navigation shell.
// Wraps the entire app in AndroidIDETheme (so theme changes apply everywhere),
// and hosts the bottom NavigationBar.

package dev.androidide.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
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

@Composable
fun AppRoot(ideViewModel: IdeViewModel = viewModel()) {
    val uiState by ideViewModel.uiState.collectAsState()
    val context  = LocalContext.current

    // ── Folder picker launcher ──────────────────────────────────────────────
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
                    )
                    AppScreen.EDITOR -> IdeScreen(
                        ideViewModel        = ideViewModel,
                        uiState             = uiState,
                        onOpenProjectFolder = { openProjectLauncher.launch(null) },
                    )
                    AppScreen.SETTINGS -> SettingsScreen(
                        uiState      = uiState,
                        ideViewModel = ideViewModel,
                    )
                }
            }

            // ── Bottom navigation bar ───────────────────────────────────────
            NavigationBar(containerColor = colors.surface) {
                NavigationBarItem(
                    icon     = {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Projects")
                    },
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
                    icon     = {
                        Icon(Icons.Default.Code, contentDescription = "Editor")
                    },
                    label    = { Text("Editor") },
                    selected = uiState.currentScreen == AppScreen.EDITOR,
                    onClick  = { ideViewModel.navigateTo(AppScreen.EDITOR) },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = colors.accent,
                        selectedTextColor   = colors.accent,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary,
                        indicatorColor      = colors.activeHighlight,
                    ),
                )
                NavigationBarItem(
                    icon     = {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    },
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
