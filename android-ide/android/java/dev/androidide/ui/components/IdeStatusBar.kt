// android-ide/android/java/dev/androidide/ui/components/IdeStatusBar.kt
//
// Status bar shown at the bottom of the IDE screen.
//
// Migration note (2026-06-12):
//   Replaces the StatusBar {} component in ui/main.slint.
//   Height is 22dp — matches the Slint STATUS_BAR_HEIGHT constant.

package dev.androidide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.androidide.ui.theme.*

@Composable
fun IdeStatusBar(
    cursorLine: Int,
    cursorColumn: Int,
    fileName: String,
    language: String,
    statusMessage: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)  // STATUS_BAR_HEIGHT matches Slint constant
            .background(IdeAccent)
            .padding(horizontal = 8.dp),
    ) {
        // Left: cursor position
        StatusChip(text = "Ln $cursorLine, Col $cursorColumn")

        if (fileName.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            StatusChip(text = fileName)
        }

        Spacer(Modifier.weight(1f))

        // Centre: transient message (e.g. "Saved", "Save failed")
        if (statusMessage.isNotEmpty()) {
            StatusChip(text = statusMessage, color = IdeTextPrimary)
            Spacer(Modifier.weight(1f))
        }

        // Right: language
        if (language.isNotEmpty()) {
            StatusChip(text = language.replaceFirstChar { it.uppercase() })
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: androidx.compose.ui.graphics.Color = Md3OnPrimary,
) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}
