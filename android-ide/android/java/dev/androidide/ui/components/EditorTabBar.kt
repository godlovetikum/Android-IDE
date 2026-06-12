// android-ide/android/java/dev/androidide/ui/components/EditorTabBar.kt
//
// Horizontal scrollable tab bar showing open editor files.
// Colors are read from LocalIdeColors so theme changes apply immediately.

package dev.androidide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.ui.theme.LocalIdeColors
import dev.androidide.viewmodel.model.EditorTab

@Composable
fun EditorTabBar(
    tabs: List<EditorTab>,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current
    Row(
        modifier = modifier
            .height(35.dp)
            .fillMaxWidth()
            .background(colors.surface)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            EditorTabItem(
                tab       = tab,
                onSelected = { onTabSelected(tab.id) },
                onClosed  = { onTabClosed(tab.id) },
            )
        }
    }
}

@Composable
private fun EditorTabItem(
    tab: EditorTab,
    onSelected: () -> Unit,
    onClosed: () -> Unit,
) {
    val colors  = LocalIdeColors.current
    val bgColor = if (tab.isActive) colors.background else colors.surface

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
                .padding(start = 10.dp, end = 4.dp),
        ) {
            if (tab.isDirty) {
                Text(
                    text  = "● ",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.modified,
                )
            }
            Text(
                text     = tab.displayName,
                style    = MaterialTheme.typography.labelMedium,
                color    = if (tab.isActive) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick  = onClosed,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint               = if (tab.isActive) colors.textSecondary else colors.textDisabled,
                    modifier           = Modifier.size(12.dp),
                )
            }
        }

        if (tab.isActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.accent)
                    .align(Alignment.BottomCenter),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(colors.separator)
                .align(Alignment.CenterEnd),
        )
    }
}
