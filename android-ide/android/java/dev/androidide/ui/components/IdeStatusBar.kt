// android-ide/android/java/dev/androidide/ui/components/IdeStatusBar.kt
//
// Status bar shown at the bottom of the editor screen.

package dev.androidide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.androidide.ui.theme.LocalIdeColors

@Composable
fun IdeStatusBar(
    cursorLine: Int,
    cursorColumn: Int,
    fileName: String,
    language: String,
    statusMessage: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIdeColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(colors.accent)
            .padding(horizontal = 8.dp),
    ) {
        StatusChip(text = "Ln $cursorLine, Col $cursorColumn", color = colors.onAccent)

        if (fileName.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            StatusChip(text = fileName, color = colors.onAccent)
        }

        Spacer(Modifier.weight(1f))

        if (statusMessage.isNotEmpty()) {
            StatusChip(text = statusMessage, color = colors.onAccent)
            Spacer(Modifier.weight(1f))
        }

        if (language.isNotEmpty()) {
            StatusChip(
                text  = language.replaceFirstChar { it.uppercase() },
                color = colors.onAccent,
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}
