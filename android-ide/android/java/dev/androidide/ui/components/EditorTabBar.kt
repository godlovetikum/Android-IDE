// android-ide/android/java/dev/androidide/ui/components/EditorTabBar.kt
//
// Horizontal scrollable tab bar showing open editor files.
//
// Migration note (2026-06-12):
//   Replaces the tab row in ui/main.slint.
//   Tabs are 35dp tall (same as the Slint TAB_HEIGHT constant).
//   Active tab has a 2dp bottom accent line in IdeAccent (#007ACC).
//   Each tab has a close (×) button.

package dev.androidide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.androidide.ui.theme.*
import dev.androidide.viewmodel.model.EditorTab

@Composable
fun EditorTabBar(
    tabs: List<EditorTab>,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(35.dp)  // TAB_HEIGHT matches Slint constant
            .fillMaxWidth()
            .background(IdeSurface)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            EditorTab(
                tab          = tab,
                onSelected   = { onTabSelected(tab.id) },
                onClosed     = { onTabClosed(tab.id) },
            )
        }
    }
}

@Composable
private fun EditorTab(
    tab: EditorTab,
    onSelected: () -> Unit,
    onClosed: () -> Unit,
) {
    val bgColor = if (tab.isActive) IdeBackground else IdeSurface

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
            // Dirty indicator dot (●) or file name
            if (tab.isDirty) {
                Text(
                    text  = "● ",
                    style = MaterialTheme.typography.labelMedium,
                    color = IdeModified,
                )
            }
            Text(
                text     = tab.displayName,
                style    = MaterialTheme.typography.labelMedium,
                color    = if (tab.isActive) IdeTextPrimary else IdeTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Spacer(Modifier.width(2.dp))
            // Close button
            IconButton(
                onClick  = onClosed,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint               = if (tab.isActive) IdeTextSecondary else IdeTextDisabled,
                    modifier           = Modifier.size(12.dp),
                )
            }
        }

        // Active tab indicator: 2dp bottom accent line
        if (tab.isActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(IdeAccent)
                    .align(Alignment.BottomCenter),
            )
        }

        // Tab right border
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(IdeSeparator)
                .align(Alignment.CenterEnd),
        )
    }
}
