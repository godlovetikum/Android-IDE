// android-ide/android/java/dev/androidide/ui/components/EditorTabBar.kt
//
// Horizontal scrollable tab bar showing open editor files.
// Each tab has a ••• overflow button with Save / Close / Close Others / Close All.
// A "+" button at the far right opens a new blank tab.

package dev.androidide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.model.EditorTab

@Composable
fun EditorTabBar(
    tabs: List<EditorTab>,
    onTabSelected: (String) -> Unit,
    onTabCloseSafe: (String) -> Unit,
    onTabSave: (String) -> Unit,
    onTabPin: (String) -> Unit,
    onCloseOthers: (String) -> Unit,
    onCloseAll: () -> Unit,
    onNewBlankTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current
    Row(
        modifier = modifier
            .height(36.dp)
            .fillMaxWidth()
            .background(colors.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Scrollable tab list
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                EditorTabItem(
                    tab          = tab,
                    onSelected   = { onTabSelected(tab.id) },
                    onCloseSafe  = { onTabCloseSafe(tab.id) },
                    onSave       = { onTabSave(tab.id) },
                    onPin        = { onTabPin(tab.id) },
                    onCloseOthers = { onCloseOthers(tab.id) },
                    onCloseAll   = onCloseAll,
                )
            }
        }

        // "+" new blank tab button
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(colors.separator),
        )
        IconButton(
            onClick  = onNewBlankTab,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = "New tab",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}

// ── Tab item ───────────────────────────────────────────────────────────────────

@Composable
private fun EditorTabItem(
    tab: EditorTab,
    onSelected: () -> Unit,
    onCloseSafe: () -> Unit,
    onSave: () -> Unit,
    onPin: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit,
) {
    val colors  = LocalIdeColors.current
    val bgColor = if (tab.isActive) colors.background else colors.surface
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(bgColor)
            .clickable(onClick = onSelected),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 10.dp, end = 2.dp),
        ) {
            // Dirty / saving indicator
            when {
                tab.isSaving -> CircularProgressIndicator(
                    modifier  = Modifier.size(8.dp).padding(end = 2.dp),
                    strokeWidth = 1.5.dp,
                    color     = colors.accent,
                )
                tab.isDirty -> Text(
                    text  = "\u25cf ",  // ●
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.modified,
                )
                else -> Spacer(Modifier.width(0.dp))
            }

            Text(
                text     = tab.displayName,
                // C011: temporary (preview) tabs shown in italic
                style    = MaterialTheme.typography.labelMedium.copy(
                    fontStyle = if (tab.isTemporary) FontStyle.Italic else FontStyle.Normal,
                ),
                color    = if (tab.isActive) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 100.dp),
            )

            Spacer(Modifier.width(2.dp))

            // ••• overflow button
            Box {
                IconButton(
                    onClick  = { menuOpen = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.MoreVert,
                        contentDescription = "Tab options",
                        tint               = if (tab.isActive) colors.textSecondary else colors.textDisabled,
                        modifier           = Modifier.size(14.dp),
                    )
                }

                DropdownMenu(
                    expanded         = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    // C011: pin the preview tab to make it permanent
                    if (tab.isTemporary) {
                        DropdownMenuItem(
                            text    = { Text("Keep Open") },
                            onClick = { menuOpen = false; onPin() },
                        )
                    }
                    if (!tab.isBlank) {
                        DropdownMenuItem(
                            text    = { Text("Save") },
                            onClick = { menuOpen = false; onSave() },
                        )
                    }
                    DropdownMenuItem(
                        text    = { Text("Close") },
                        onClick = { menuOpen = false; onCloseSafe() },
                    )
                    DropdownMenuItem(
                        text    = { Text("Close Others") },
                        onClick = { menuOpen = false; onCloseOthers() },
                    )
                    DropdownMenuItem(
                        text    = { Text("Close All") },
                        onClick = { menuOpen = false; onCloseAll() },
                    )
                }
            }
        }

        // Active indicator bar at bottom
        if (tab.isActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.accent)
                    .align(Alignment.BottomCenter),
            )
        }

        // Right separator
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(colors.separator)
                .align(Alignment.CenterEnd),
        )
    }
}
