package dev.android.ide.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import dev.android.ide.contracts.Surface
import dev.android.ide.ui.theme.LocalIdeColors

/** Home is an orientation surface only: it owns no project, process, tab, or Git summary. */
@Composable
fun HomeSurface(onNavigate: (Surface) -> Unit, onExit: () -> Unit, onFeedback: (String) -> Unit) {
    val colors = LocalIdeColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Welcome back techie! Choose a domain to continue", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
        val destinations = listOf(
            HomeDestinationData(Icons.Default.FolderOpen, "Projects", "Registered projects", true) { onNavigate(Surface.PROJECTS) },
            HomeDestinationData(Icons.Default.Code, "Editor", "Files and documents", true) { onNavigate(Surface.EDITOR) },
            HomeDestinationData(Icons.Default.Terminal, "Terminal", "Runtime sessions", false) { onFeedback("Terminal") },
            HomeDestinationData(Icons.Default.Language, "Browser", "Preview and browsing", false) { onFeedback("Browser") },
            HomeDestinationData(Icons.Default.MergeType, "Git", "Repository operations", false) { onFeedback("Git") },
            HomeDestinationData(Icons.Default.Extension, "Extensions", "Provider extensions", false) { onFeedback("Extensions") },
            HomeDestinationData(Icons.Default.Settings, "Settings", "Application preferences", true) { onNavigate(Surface.SETTINGS) },
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
        ) {
            items(destinations) { destination -> HomeDestination(destination) }
        }
        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.error),
        ) { Text("Exit Android IDE") }
    }
}

@Composable
private fun HomeDestination(destination: HomeDestinationData) {
    val colors = LocalIdeColors.current
    Button(
        onClick = destination.onClick,
        enabled = true,
        modifier = Modifier.fillMaxWidth().height(116.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            disabledContainerColor = colors.surface.copy(alpha = 0.55f),
            disabledContentColor = colors.textDisabled,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(destination.icon, contentDescription = destination.title, tint = if (destination.enabled) colors.accent else colors.textSecondary, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(destination.title, style = MaterialTheme.typography.titleLarge)
                Text("> ${if (destination.enabled) destination.description else "${destination.description} coming soon (phase ${homePhase(destination.title)})"}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

private fun homePhase(title: String): Int = when (title) {
    "Terminal" -> 3
    "Browser" -> 5
    "Git" -> 6
    "Extensions" -> 8
    else -> 1
}

private data class HomeDestinationData(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)
