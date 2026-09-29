package dev.android.ide.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import dev.android.ide.data.model.AppTheme
import dev.android.ide.data.model.EditorSettings
import dev.android.ide.data.model.VolumeKeyMode
import dev.android.ide.ui.theme.LocalIdeColors
import dev.android.ide.viewmodel.IdeViewModel
import dev.android.ide.viewmodel.model.IdeUiState

enum class SettingsCategory(val title: String, val description: String, val icon: ImageVector) {
    GENERAL("General", "App theme and interface-wide preferences", Icons.Default.Palette),
    EDITOR("Editor", "Editor appearance, code behavior, keyboard, and file tree", Icons.Default.Code),
    PROJECTS("Projects", "Project creation and storage preferences", Icons.Default.FolderOpen),
    GIT("Git", "Repository identity, history, and source control", Icons.Default.MergeType),
    CREDENTIALS("Credentials", "Git accounts, tokens, and secure sign-in data", Icons.Default.Lock),
    SECURITY("Security", "Storage access, Android permissions, and protected data", Icons.Default.Lock),
    TERMINAL("Terminal", "Terminal runtime and session preferences", Icons.Default.Terminal),
    BROWSER("Browser", "Browser preview and web-project preferences", Icons.Default.Language),
    EXTENSIONS("Extensions", "Language tools and editor extensions", Icons.Default.Extension),
}

@Composable
fun SettingsScreen(
    uiState: IdeUiState,
    ideViewModel: IdeViewModel,
    onNavigationIconClick: (() -> Unit)? = null,
    scrollToSection: String? = null,
    onScrollConsumed: () -> Unit = {},
) {
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = selectedCategoryName?.let { name ->
        SettingsCategory.values().firstOrNull { it.name == name }
    }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategoryName = null
    }

    LaunchedEffect(scrollToSection) {
        val requested = scrollToSection ?: return@LaunchedEffect
        val category = when (requested) {
            "App Theme", "UI Font Size" -> SettingsCategory.GENERAL
            "Editor Theme", "Editor", "Controls", "File Tree" -> SettingsCategory.EDITOR
            "Project Storage" -> SettingsCategory.PROJECTS
            "Permissions", "Folder Access", "Android App Permissions" -> SettingsCategory.SECURITY
            "Credentials", "Manage Credentials" -> SettingsCategory.CREDENTIALS
            "Terminal" -> SettingsCategory.TERMINAL
            "Browser" -> SettingsCategory.BROWSER
            "Git" -> SettingsCategory.GIT
            else -> null
        }
        if (category != null) selectedCategoryName = category.name
        onScrollConsumed()
    }

    val colors = LocalIdeColors.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedCategory?.title ?: "Settings", color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedCategory != null) selectedCategoryName = null
                        else onNavigationIconClick?.invoke()
                    }) {
                        Icon(
                            imageVector = if (selectedCategory != null) Icons.Default.ArrowBack else Icons.Default.Menu,
                            contentDescription = if (selectedCategory != null) "Back to settings" else "Open sidebar",
                            tint = colors.accent,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        if (selectedCategory == null) {
            SettingsCategoryList(
                modifier = Modifier.padding(innerPadding),
                onCategorySelected = { selectedCategoryName = it.name },
            )
        } else {
            SettingsCategoryContent(
                category = selectedCategory,
                uiState = uiState,
                ideViewModel = ideViewModel,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun SettingsCategoryList(
    modifier: Modifier,
    onCategorySelected: (SettingsCategory) -> Unit,
) {
    val colors = LocalIdeColors.current
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Choose a settings category",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        items(SettingsCategory.values().toList()) { category ->
            Card(
                onClick = { onCategorySelected(category) },
                colors = CardDefaults.cardColors(containerColor = colors.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(category.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(category.title, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(category.description, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    uiState: IdeUiState,
    ideViewModel: IdeViewModel,
    modifier: Modifier,
) {
    val s = uiState.editorSettings
    val colors = LocalIdeColors.current
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(category.description, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        when (category) {
            SettingsCategory.GENERAL -> item { GeneralSettings(uiState, s, ideViewModel) }
            SettingsCategory.EDITOR -> item { EditorSettingsContent(uiState, s, ideViewModel) }
            SettingsCategory.PROJECTS -> item { ProjectsSettingsContent() }
            SettingsCategory.GIT,
            SettingsCategory.TERMINAL,
            SettingsCategory.BROWSER,
            SettingsCategory.EXTENSIONS -> item { DomainPlaceholder(category) }
            SettingsCategory.CREDENTIALS -> item { DomainPlaceholder(category) }
            SettingsCategory.SECURITY -> item { StorageAccessSettings() }
        }
    }
}

@Composable
private fun StorageAccessSettings() {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var grantedCount by rememberSaveable {
        mutableStateOf(resolver.persistedUriPermissions.count { it.isReadPermission || it.isWritePermission })
    }
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val result = runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            result.onSuccess {
                grantedCount = resolver.persistedUriPermissions.count { it.isReadPermission || it.isWritePermission }
                permissionMessage = "Folder access saved"
            }.onFailure {
                permissionMessage = "Android did not grant access to that folder"
            }
        } else {
            permissionMessage = "Folder access was not changed"
        }
    }
    SettingsCard {
        Text("Project folder access", style = MaterialTheme.typography.titleSmall)
        Text(
            "Android IDE uses Android’s folder access permission for project files. Grant access to a folder when Android asks, and the permission remains available after the app is reopened.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalIdeColors.current.textSecondary,
        )
        Text("$grantedCount saved folder permission(s)", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("Grant folder access") }
        permissionMessage?.let { Text(it, color = LocalIdeColors.current.textSecondary, style = MaterialTheme.typography.bodySmall) }
    }
    SettingsCard {
        Text("Android app permissions", style = MaterialTheme.typography.titleSmall)
        Text(
            "Notifications and other Android-managed permissions are controlled by the system settings for this app.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalIdeColors.current.textSecondary,
        )
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            })
        }) { Text("Open Android app settings") }
    }
}

@Composable
private fun GeneralSettings(uiState: IdeUiState, s: EditorSettings, ideViewModel: IdeViewModel) {
    SettingsCard {
        Text("App theme", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            AppThemeOption("Dark", AppTheme.DARK, uiState.appTheme, ideViewModel)
            AppThemeOption("Light", AppTheme.LIGHT, uiState.appTheme, ideViewModel)
            AppThemeOption("System", AppTheme.SYSTEM, uiState.appTheme, ideViewModel)
        }
    }
    SettingsCard {
        Text("Interface text size", style = MaterialTheme.typography.titleSmall)
        Text("Affects menus, file lists, dialogs, and other interface text.", style = MaterialTheme.typography.bodySmall, color = LocalIdeColors.current.textSecondary)
        val pct = (s.uiFontScale * 100).toInt()
        Text("$pct%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = s.uiFontScale,
            onValueChange = { value ->
                val snapped = (value / EditorSettings.UI_FONT_SCALE_STEP).toInt() * EditorSettings.UI_FONT_SCALE_STEP
                ideViewModel.setEditorSettings(s.copy(uiFontScale = snapped.coerceIn(EditorSettings.UI_FONT_SCALE_MIN, EditorSettings.UI_FONT_SCALE_MAX)))
            },
            valueRange = EditorSettings.UI_FONT_SCALE_MIN..EditorSettings.UI_FONT_SCALE_MAX,
            steps = ((EditorSettings.UI_FONT_SCALE_MAX - EditorSettings.UI_FONT_SCALE_MIN) / EditorSettings.UI_FONT_SCALE_STEP).toInt() - 1,
        )
        TextButton(onClick = { ideViewModel.setEditorSettings(s.copy(uiFontScale = 1f)) }, enabled = s.uiFontScale != 1f) { Text("Reset") }
    }
}

@Composable
private fun EditorSettingsContent(uiState: IdeUiState, s: EditorSettings, ideViewModel: IdeViewModel) {
    val colors = LocalIdeColors.current
    SettingsCard {
        Text("Editor appearance", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            EditorThemeOption("Dark", "dark", s.editorTheme, s, ideViewModel)
            EditorThemeOption("Light", "light", s.editorTheme, s, ideViewModel)
            EditorThemeOption("Follow app theme", "system", s.editorTheme, s, ideViewModel)
        }
        HorizontalDivider(color = colors.separator)
        SettingStepper("Code font size", "${s.fontSize} sp", {
            if (s.fontSize > 10) ideViewModel.setEditorSettings(s.copy(fontSize = s.fontSize - 1))
        }, {
            if (s.fontSize < 28) ideViewModel.setEditorSettings(s.copy(fontSize = s.fontSize + 1))
        })
        HorizontalDivider(color = colors.separator)
        VisibilitySettingRow("Document information row", "Show line, column, spacing, language, and encoding below the editor.", s.showStatusBar, { ideViewModel.setEditorSettings(s.copy(showStatusBar = it)) })
    }
    SettingsCard {
        Text("Editing behavior", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(color = colors.separator)
        Text("Tab size", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(2, 4, 8).forEach { size -> FilterChip(s.tabSize == size, { ideViewModel.setEditorSettings(s.copy(tabSize = size)) }, label = { Text("$size") }) } }
        HorizontalDivider(color = colors.separator)
        VisibilitySettingRow("Word wrap", "Wrap long lines inside the editor.", s.wordWrap, { ideViewModel.setEditorSettings(s.copy(wordWrap = it)) })
        HorizontalDivider(color = colors.separator)
        VisibilitySettingRow("Line numbers", "Show line numbers beside the code.", s.lineNumbers, { ideViewModel.setEditorSettings(s.copy(lineNumbers = it)) })
        HorizontalDivider(color = colors.separator)
        Text("Whitespace", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("None" to "none", "Selection" to "selection", "All" to "all").forEach { (label, value) -> FilterChip(s.renderWhitespace == value, { ideViewModel.setEditorSettings(s.copy(renderWhitespace = value)) }, label = { Text(label) }) } }
        HorizontalDivider(color = colors.separator)
        VisibilitySettingRow("Minimap", "Show a code overview at the edge of the editor.", s.minimapEnabled, { ideViewModel.setEditorSettings(s.copy(minimapEnabled = it)) })
        HorizontalDivider(color = colors.separator)
        VisibilitySettingRow("Scroll past the last line", "Allow the last line to sit at the centre of the view.", s.scrollBeyondLastLine, { ideViewModel.setEditorSettings(s.copy(scrollBeyondLastLine = it)) })
        HorizontalDivider(color = colors.separator)
        VisibilitySettingRow("Bracket pair colors", "Color nested bracket levels differently.", s.bracketPairColorization, { ideViewModel.setEditorSettings(s.copy(bracketPairColorization = it)) })
        HorizontalDivider(color = colors.separator)
        ChoiceSetting("Cursor style", listOf("Line" to "line", "Block" to "block", "Underline" to "underline"), s.cursorStyle) { ideViewModel.setEditorSettings(s.copy(cursorStyle = it)) }
        HorizontalDivider(color = colors.separator)
        ChoiceSetting("Auto-close brackets", listOf("Always" to "always", "Smart" to "languageDefined", "Never" to "never"), s.autoClosingBrackets) { ideViewModel.setEditorSettings(s.copy(autoClosingBrackets = it)) }
        HorizontalDivider(color = colors.separator)
        VisibilitySettingRow("Auto save", "Save changes shortly after editing.", s.autoSave, { ideViewModel.setEditorSettings(s.copy(autoSave = it)) })
    }
    KeyboardSettingsContent(uiState, s, ideViewModel)
    FileTreeSettingsContent(s, ideViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardSettingsContent(uiState: IdeUiState, s: EditorSettings, ideViewModel: IdeViewModel) {
    var toolbarOrderSheetOpen by rememberSaveable { mutableStateOf(false) }
    var draftOrder by rememberSaveable { mutableStateOf(s.keyboardToolbarOrder) }
    SettingsCard {
        Text("Keyboard and input", style = MaterialTheme.typography.titleSmall)
        VisibilitySettingRow("Keyboard toolbar", "Show cursor, selection, and editing controls above the keyboard.", s.showKeyboardToolbar, { ideViewModel.setEditorSettings(s.copy(showKeyboardToolbar = it)) })
        HorizontalDivider()
        VisibilitySettingRow("Symbol bar", "Show one-tap common character shortcuts above the keyboard.", s.showSymbolBar, { ideViewModel.setEditorSettings(s.copy(showSymbolBar = it)) })
        HorizontalDivider()
        Text("Volume keys in editor", style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.selectableGroup()) {
            VolumeKeyOption("Cursor horizontal (← / →)", VolumeKeyMode.HORIZONTAL, uiState.volumeKeyMode, ideViewModel)
            VolumeKeyOption("Cursor vertical (↑ / ↓)", VolumeKeyMode.VERTICAL, uiState.volumeKeyMode, ideViewModel)
            VolumeKeyOption("Disabled (system volume)", VolumeKeyMode.DISABLED, uiState.volumeKeyMode, ideViewModel)
        }
        HorizontalDivider()
        Text("Keyboard toolbar order", style = MaterialTheme.typography.bodyMedium)
        Text("The toolbar order is managed in a separate drag-and-drop editor.", style = MaterialTheme.typography.bodySmall, color = LocalIdeColors.current.textSecondary)
        TextButton(onClick = { draftOrder = s.keyboardToolbarOrder; toolbarOrderSheetOpen = true }) {
            Text("Customize toolbar order")
        }
    }
    if (toolbarOrderSheetOpen) {
        ModalBottomSheet(onDismissRequest = { toolbarOrderSheetOpen = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Customize keyboard toolbar", style = MaterialTheme.typography.titleMedium)
                Text("Long-press an action, then drag it to a new position.", style = MaterialTheme.typography.bodySmall, color = LocalIdeColors.current.textSecondary)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    itemsIndexed(draftOrder, key = { _, id -> id }) { index, actionId ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(draftOrder) {
                                    var currentIndex = index
                                    detectDragGesturesAfterLongPress(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val target = when {
                                                dragAmount.y > 18f -> currentIndex + 1
                                                dragAmount.y < -18f -> currentIndex - 1
                                                else -> currentIndex
                                            }.coerceIn(0, draftOrder.lastIndex)
                                            if (target != currentIndex) {
                                                val reordered = draftOrder.toMutableList()
                                                reordered[currentIndex] = reordered[target].also { reordered[target] = reordered[currentIndex] }
                                                draftOrder = reordered
                                                currentIndex = target
                                            }
                                        },
                                        onDragEnd = {},
                                        onDragCancel = {},
                                    )
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.DragHandle, contentDescription = "Drag ${keyboardToolbarLabel(actionId)}")
                            Text("${index + 1}. ${keyboardToolbarLabel(actionId)}", Modifier.padding(start = 12.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { draftOrder = EditorSettings.DEFAULT_KEYBOARD_TOOLBAR_ORDER }) { Text("Reset") }
                    TextButton(onClick = { toolbarOrderSheetOpen = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        ideViewModel.setEditorSettings(s.copy(keyboardToolbarOrder = draftOrder))
                        toolbarOrderSheetOpen = false
                    }) { Text("Done") }
                }
            }
        }
    }
}

private fun keyboardToolbarLabel(id: String): String = when (id) {
    "indent" -> "Indent"
    "outdent" -> "Outdent"
    "cursorUp" -> "Cursor up"
    "cursorDown" -> "Cursor down"
    "cursorLeft" -> "Cursor left"
    "cursorRight" -> "Cursor right"
    "undo" -> "Undo"
    "redo" -> "Redo"
    "cut" -> "Cut"
    "copy" -> "Copy"
    "paste" -> "Paste"
    "selectAll" -> "Select all"
    "keyboardToggle" -> "Toggle keyboard"
    "selectLeft" -> "Select left"
    "selectRight" -> "Select right"
    "selectUp" -> "Select up"
    "selectDown" -> "Select down"
    "selectWordLeft" -> "Select word left"
    "selectWordRight" -> "Select word right"
    "selectToStart" -> "Select to start"
    "selectToEnd" -> "Select to end"
    "formatDocument" -> "Format document"
    "commentLine" -> "Comment or uncomment"
    "duplicateLine" -> "Duplicate line"
    "moveLineUp" -> "Move line up"
    "moveLineDown" -> "Move line down"
    "fold" -> "Fold"
    "unfold" -> "Unfold"
    "previousMatch" -> "Previous match"
    "nextMatch" -> "Next match"
    "closeSearch" -> "Close find"
    else -> id
}

@Composable
private fun FileTreeSettingsContent(s: EditorSettings, ideViewModel: IdeViewModel) {
    SettingsCard {
        Text("File tree", style = MaterialTheme.typography.titleSmall)
        VisibilitySettingRow("Hide .git folder", "Keep repository internals out of the file tree.", s.hideGitFolder, { ideViewModel.setEditorSettings(s.copy(hideGitFolder = it)) })
        HorizontalDivider()
        VisibilitySettingRow("Hide workspace metadata", "Keep Android IDE workspace metadata out of the file tree.", s.hideProjectMetadataFolder, { ideViewModel.setEditorSettings(s.copy(hideProjectMetadataFolder = it)) })
    }
}

@Composable
private fun ProjectsSettingsContent() {
    SettingsCard {
        Text("Storage locations are chosen during project creation.", style = MaterialTheme.typography.bodyMedium)
        Text("The editor does not keep a hidden default path. This avoids creating projects in an unexpected folder.", style = MaterialTheme.typography.bodySmall, color = LocalIdeColors.current.textSecondary)
    }
}

@Composable
private fun DomainPlaceholder(category: SettingsCategory) {
    Text(
        text = "${category.title} settings are not available yet. This domain will be added in its own workflow.",
        color = LocalIdeColors.current.textSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = LocalIdeColors.current.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SettingStepper(title: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(value, color = LocalIdeColors.current.textSecondary, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDecrease, modifier = Modifier.size(36.dp), contentPadding = PaddingValues(0.dp)) { Text("−") }
            OutlinedButton(onClick = onIncrease, modifier = Modifier.size(36.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
        }
    }
}

@Composable
private fun VisibilitySettingRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().selectable(selected = checked, role = Role.Switch, onClick = { onCheckedChange(!checked) })) {
        Column(Modifier.weight(1f)) { Text(title, color = LocalIdeColors.current.textPrimary); Text(description, color = LocalIdeColors.current.textSecondary, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceSetting(title: String, choices: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { choices.forEach { (label, value) -> FilterChip(selected == value, { onSelected(value) }, label = { Text(label) }) } }
    }
}

@Composable
private fun AppThemeOption(label: String, theme: AppTheme, current: AppTheme, ideViewModel: IdeViewModel) {
    val selected = theme == current
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { ideViewModel.setTheme(theme) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null); Spacer(Modifier.width(8.dp)); Text(label)
    }
}

@Composable
private fun EditorThemeOption(label: String, themeKey: String, currentKey: String, settings: EditorSettings, ideViewModel: IdeViewModel) {
    val selected = themeKey == currentKey
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { ideViewModel.setEditorSettings(settings.copy(editorTheme = themeKey)) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null); Spacer(Modifier.width(8.dp)); Text(label)
    }
}

@Composable
private fun VolumeKeyOption(label: String, mode: VolumeKeyMode, current: VolumeKeyMode, ideViewModel: IdeViewModel) {
    val selected = mode == current
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { ideViewModel.setVolumeKeyMode(mode) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null); Spacer(Modifier.width(8.dp)); Text(label)
    }
}
