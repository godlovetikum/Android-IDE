// android-ide/android/java/dev/androidide/ui/screen/SettingsScreen.kt
//
// Settings screen — app theme, editor theme, preview layout, editor display,
// volume key controls, UI font scaling, default project directory, and Phase 2
// feature placeholders.

package dev.androidide.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.androidide.data.model.AppTheme
import dev.androidide.data.model.EditorSettings
import dev.androidide.data.model.PreviewLayout
import dev.androidide.data.model.VolumeKeyMode
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.IdeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: IdeUiState,
    ideViewModel: IdeViewModel,
    onNavigationIconClick: (() -> Unit)? = null,
) {
    val colors = LocalIdeColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Settings", color = colors.textPrimary) },
                navigationIcon = {
                    if (onNavigationIconClick != null) {
                        IconButton(onClick = onNavigationIconClick) {
                            Icon(
                                imageVector        = Icons.Default.Menu,
                                contentDescription = "Open sidebar",
                                tint               = colors.accent,
                            )
                        }
                    }
                },
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
            val s = uiState.editorSettings

            // ── App Appearance ─────────────────────────────────────────────
            SectionHeader("App Theme")
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(
                    modifier = Modifier.fillMaxWidth().selectableGroup().padding(vertical = 8.dp),
                ) {
                    AppThemeOption("Dark",   AppTheme.DARK,   uiState.appTheme, ideViewModel)
                    AppThemeOption("Light",  AppTheme.LIGHT,  uiState.appTheme, ideViewModel)
                    AppThemeOption("System", AppTheme.SYSTEM, uiState.appTheme, ideViewModel)
                }
            }

            // ── UI Font Scale ───────────────────────────────────────────────
            SectionHeader("UI Font Size")
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text  = "Text Scale",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            val pct = (s.uiFontScale * 100).toInt()
                            Text(
                                text  = "$pct% — affects all UI text (file tree, menus, dialogs)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value         = s.uiFontScale,
                        onValueChange = { v ->
                            val snapped = (v / EditorSettings.UI_FONT_SCALE_STEP).toInt() *
                                EditorSettings.UI_FONT_SCALE_STEP
                            ideViewModel.setEditorSettings(
                                s.copy(uiFontScale = snapped.coerceIn(
                                    EditorSettings.UI_FONT_SCALE_MIN,
                                    EditorSettings.UI_FONT_SCALE_MAX,
                                ))
                            )
                        },
                        valueRange    = EditorSettings.UI_FONT_SCALE_MIN..EditorSettings.UI_FONT_SCALE_MAX,
                        steps         = ((EditorSettings.UI_FONT_SCALE_MAX - EditorSettings.UI_FONT_SCALE_MIN) /
                            EditorSettings.UI_FONT_SCALE_STEP).toInt() - 1,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text  = "${(EditorSettings.UI_FONT_SCALE_MIN * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textDisabled,
                        )
                        TextButton(
                            onClick  = { ideViewModel.setEditorSettings(s.copy(uiFontScale = 1.0f)) },
                            enabled  = s.uiFontScale != 1.0f,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            text  = "${(EditorSettings.UI_FONT_SCALE_MAX * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textDisabled,
                        )
                    }
                }
            }

            // ── Editor Theme ───────────────────────────────────────────────
            SectionHeader("Editor Theme")
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(
                    modifier = Modifier.fillMaxWidth().selectableGroup().padding(vertical = 8.dp),
                ) {
                    EditorThemeOption("Dark",   "dark",   s.editorTheme, s, ideViewModel)
                    EditorThemeOption("Light",  "light",  s.editorTheme, s, ideViewModel)
                    EditorThemeOption("System (follow app theme)", "system", s.editorTheme, s, ideViewModel)
                }
            }

            // ── Preview Layout ─────────────────────────────────────────────
            SectionHeader("Preview Layout (Portrait)")
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(
                    modifier = Modifier.fillMaxWidth().selectableGroup().padding(vertical = 8.dp),
                ) {
                    PreviewLayoutOption(
                        "Preview Above Editor",
                        PreviewLayout.PREVIEW_ABOVE,
                        s.previewLayout, s, ideViewModel,
                    )
                    PreviewLayoutOption(
                        "Editor Above Preview",
                        PreviewLayout.EDITOR_ABOVE,
                        s.previewLayout, s, ideViewModel,
                    )
                }
            }

            // ── Editor ─────────────────────────────────────────────────────
            SectionHeader("Editor")
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // Font size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Font Size", color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("${s.fontSize} sp (Monaco editor only)", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { if (s.fontSize > 10) ideViewModel.setEditorSettings(s.copy(fontSize = s.fontSize - 1)) },
                                modifier = Modifier.size(32.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) { Text("\u2212", style = MaterialTheme.typography.labelLarge) }
                            OutlinedButton(
                                onClick = { if (s.fontSize < 28) ideViewModel.setEditorSettings(s.copy(fontSize = s.fontSize + 1)) },
                                modifier = Modifier.size(32.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) { Text("+", style = MaterialTheme.typography.labelLarge) }
                        }
                    }

                    HorizontalDivider(color = colors.separator)

                    // Tab size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tab Size", color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(2, 4, 8).forEach { size ->
                                FilterChip(
                                    selected = s.tabSize == size,
                                    onClick  = { ideViewModel.setEditorSettings(s.copy(tabSize = size)) },
                                    label    = { Text("$size") },
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.separator)

                    // Word wrap
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = s.wordWrap, role = Role.Switch, onClick = {
                                ideViewModel.setEditorSettings(s.copy(wordWrap = !s.wordWrap))
                            }),
                    ) {
                        Text("Word Wrap", color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = s.wordWrap, onCheckedChange = { ideViewModel.setEditorSettings(s.copy(wordWrap = it)) })
                    }

                    HorizontalDivider(color = colors.separator)

                    // Line numbers
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = s.lineNumbers, role = Role.Switch, onClick = {
                                ideViewModel.setEditorSettings(s.copy(lineNumbers = !s.lineNumbers))
                            }),
                    ) {
                        Text("Line Numbers", color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = s.lineNumbers, onCheckedChange = { ideViewModel.setEditorSettings(s.copy(lineNumbers = it)) })
                    }

                    HorizontalDivider(color = colors.separator)

                    // Auto save
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = s.autoSave, role = Role.Switch, onClick = {
                                ideViewModel.setEditorSettings(s.copy(autoSave = !s.autoSave))
                            }),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Save", color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("Save on every edit (150 ms debounce)", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = s.autoSave, onCheckedChange = { ideViewModel.setEditorSettings(s.copy(autoSave = it)) })
                    }

                    HorizontalDivider(color = colors.separator)

                    // Show keyboard toolbar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = s.showKeyboardToolbar, role = Role.Switch, onClick = {
                                ideViewModel.setEditorSettings(s.copy(showKeyboardToolbar = !s.showKeyboardToolbar))
                            }),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keyboard Toolbar", color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("Show cursor navigation & edit buttons above the keyboard", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = s.showKeyboardToolbar, onCheckedChange = { ideViewModel.setEditorSettings(s.copy(showKeyboardToolbar = it)) })
                    }

                    HorizontalDivider(color = colors.separator)

                    // Show symbol bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = s.showSymbolBar, role = Role.Switch, onClick = {
                                ideViewModel.setEditorSettings(s.copy(showSymbolBar = !s.showSymbolBar))
                            }),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Symbol Bar", color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("Show one-tap common character shortcuts above the keyboard", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = s.showSymbolBar, onCheckedChange = { ideViewModel.setEditorSettings(s.copy(showSymbolBar = it)) })
                    }
                }
            }

            // ── File Tree ──────────────────────────────────────────────────
            SectionHeader("File Tree")
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = s.hideGitFolder, role = Role.Switch, onClick = {
                                ideViewModel.setEditorSettings(s.copy(hideGitFolder = !s.hideGitFolder))
                            }),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide .git Folder", color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("Remove the .git directory from the file tree", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = s.hideGitFolder, onCheckedChange = { ideViewModel.setEditorSettings(s.copy(hideGitFolder = it)) })
                    }
                }
            }

            // ── Project Storage ────────────────────────────────────────────
            SectionHeader("Project Storage")
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text  = "Default Project Directory",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text  = "New blank projects are created here. Leave empty to use app-private storage (no special permissions required).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))

                    val dir = s.defaultProjectDir
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text     = if (dir.isEmpty()) "(app-private storage)" else dir,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = if (dir.isEmpty()) colors.textDisabled else colors.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                        )
                        if (dir.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick        = { ideViewModel.setEditorSettings(s.copy(defaultProjectDir = "")) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier       = Modifier.height(32.dp),
                            ) {
                                Text("Reset", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(
                        text  = "To change this directory, edit it here directly or use the \"New Project\" dialog in the Projects screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDisabled,
                    )
                    var editPath by remember { mutableStateOf(dir) }
                    LaunchedEffect(dir) { editPath = dir }
                    OutlinedTextField(
                        value         = editPath,
                        onValueChange = { editPath = it },
                        modifier      = Modifier.fillMaxWidth(),
                        label         = { Text("Path", style = MaterialTheme.typography.bodySmall) },
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodySmall,
                        placeholder   = { Text("/storage/emulated/0/Projects", style = MaterialTheme.typography.bodySmall) },
                        trailingIcon  = {
                            if (editPath != dir) {
                                TextButton(
                                    onClick        = { ideViewModel.setEditorSettings(s.copy(defaultProjectDir = editPath.trim())) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text("Apply", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                    )
                }
            }

            // ── Controls ───────────────────────────────────────────────────
            SectionHeader("Controls")
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text  = "Volume Keys in Editor",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Column(modifier = Modifier.selectableGroup()) {
                        VolumeKeyOption("Cursor horizontal (\u2190 / \u2192)", VolumeKeyMode.HORIZONTAL, uiState.volumeKeyMode, ideViewModel)
                        VolumeKeyOption("Cursor vertical (\u2191 / \u2193)",   VolumeKeyMode.VERTICAL,   uiState.volumeKeyMode, ideViewModel)
                        VolumeKeyOption("Disabled (system volume)",             VolumeKeyMode.DISABLED,   uiState.volumeKeyMode, ideViewModel)
                    }
                }
            }

            // ── Coming soon ────────────────────────────────────────────────
            SectionHeader("Coming in Future Phases")
            Text(
                text  = "These features are planned for Phase 2 and beyond.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            ComingSoonItem(icon = Icons.Default.Terminal,  title = "Terminal",   description = "Run shell commands inside the IDE.")
            ComingSoonItem(icon = Icons.Default.MergeType, title = "Git",        description = "Commit, push, pull, and branch directly in the editor.")
            ComingSoonItem(icon = Icons.Default.Extension, title = "Extensions", description = "Install language servers and plugins.")
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    val colors = LocalIdeColors.current
    Text(text = title, style = MaterialTheme.typography.labelMedium, color = colors.accent)
}

@Composable
private fun AppThemeOption(label: String, theme: AppTheme, current: AppTheme, ideViewModel: IdeViewModel) {
    val colors   = LocalIdeColors.current
    val selected = theme == current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = { ideViewModel.setTheme(theme) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = LocalIdeColors.current.textPrimary)
    }
}

@Composable
private fun EditorThemeOption(
    label: String,
    themeKey: String,
    currentKey: String,
    settings: EditorSettings,
    ideViewModel: IdeViewModel,
) {
    val colors   = LocalIdeColors.current
    val selected = themeKey == currentKey
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = {
                ideViewModel.setEditorSettings(settings.copy(editorTheme = themeKey))
            })
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = colors.textPrimary)
    }
}

@Composable
private fun PreviewLayoutOption(
    label: String,
    layout: PreviewLayout,
    current: PreviewLayout,
    settings: EditorSettings,
    ideViewModel: IdeViewModel,
) {
    val colors   = LocalIdeColors.current
    val selected = layout == current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = {
                ideViewModel.setEditorSettings(settings.copy(previewLayout = layout))
            })
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = colors.textPrimary)
    }
}

@Composable
private fun VolumeKeyOption(label: String, mode: VolumeKeyMode, current: VolumeKeyMode, ideViewModel: IdeViewModel) {
    val colors   = LocalIdeColors.current
    val selected = mode == current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = { ideViewModel.setVolumeKeyMode(mode) })
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ComingSoonItem(icon: ImageVector, title: String, description: String) {
    val colors = LocalIdeColors.current
    Card(colors = CardDefaults.cardColors(containerColor = colors.surface)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(16.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.textDisabled, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title,       style = MaterialTheme.typography.bodyMedium, color = colors.textDisabled)
                Text(text = description, style = MaterialTheme.typography.bodySmall,  color = colors.textDisabled)
            }
            Spacer(Modifier.width(8.dp))
            Text(text = "Phase 2", style = MaterialTheme.typography.labelSmall, color = colors.textDisabled)
        }
    }
}
