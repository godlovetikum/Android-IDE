// android-ide/android/java/dev/android/ide/ui/components/IdeStatusBar.kt
//
// Low-emphasis status information for editor context.

package dev.android.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.android.ide.ui.theme.LocalIdeColors

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
            .height(26.dp)
            .background(colors.surface)
            .padding(horizontal = 12.dp),
    ) {
        StatusChip(text = "Ln $cursorLine, Col $cursorColumn", color = colors.textSecondary)
        if (fileName.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            StatusChip(text = fileName, color = colors.textPrimary)
        }
        Spacer(Modifier.weight(1f))
        if (statusMessage.isNotEmpty()) {
            StatusChip(text = statusMessage, color = colors.textSecondary)
            Spacer(Modifier.width(12.dp))
        }
        if (language.isNotEmpty()) {
            StatusChip(
                text = language.replaceFirstChar { it.uppercase() },
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}
