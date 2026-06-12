// android-ide/android/java/dev/androidide/ui/screen/SettingsScreen.kt
//
// Settings screen — theme selection and coming-soon feature previews.

package dev.androidide.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.androidide.data.model.AppTheme
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.IdeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: IdeUiState,
    ideViewModel: IdeViewModel,
) {
    val colors = LocalIdeColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Settings", color = colors.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Theme ──────────────────────────────────────────────────────
            SectionHeader("Appearance")

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(vertical = 8.dp),
                ) {
                    ThemeOption("Dark",   AppTheme.DARK,   uiState.appTheme, ideViewModel)
                    ThemeOption("Light",  AppTheme.LIGHT,  uiState.appTheme, ideViewModel)
                    ThemeOption("System", AppTheme.SYSTEM, uiState.appTheme, ideViewModel)
                }
            }

            // ── Coming soon ────────────────────────────────────────────────
            SectionHeader("Coming in Future Phases")

            Text(
                text  = "These features are planned for Phase 2 and beyond.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            ComingSoonItem(
                icon        = Icons.Default.Terminal,
                title       = "Terminal",
                description = "Run shell commands inside the IDE.",
            )
            ComingSoonItem(
                icon        = Icons.Default.MergeType,
                title       = "Git",
                description = "Commit, push, pull, and branch directly in the editor.",
            )
            ComingSoonItem(
                icon        = Icons.Default.Extension,
                title       = "Extensions",
                description = "Install language servers and plugins.",
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = LocalIdeColors.current
    Text(
        text  = title,
        style = MaterialTheme.typography.labelMedium,
        color = colors.accent,
    )
}

@Composable
private fun ThemeOption(
    label: String,
    theme: AppTheme,
    current: AppTheme,
    ideViewModel: IdeViewModel,
) {
    val colors   = LocalIdeColors.current
    val selected = theme == current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role     = Role.RadioButton,
                onClick  = { ideViewModel.setTheme(theme) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick  = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = colors.textPrimary)
    }
}

@Composable
private fun ComingSoonItem(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val colors = LocalIdeColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = colors.textDisabled,
                modifier           = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textDisabled,
                )
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDisabled,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text  = "Phase 2",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textDisabled,
            )
        }
    }
}
