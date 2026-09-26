package dev.android.ide.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.android.ide.contracts.Surface

/** Home is an orientation surface only: it owns no project, process, tab, or Git summary. */
@Composable
fun HomeSurface(onNavigate: (Surface) -> Unit, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Android IDE", style = MaterialTheme.typography.headlineMedium)
        Text("Choose an area to continue.")
        Button(onClick = { onNavigate(Surface.PROJECTS) }, Modifier.fillMaxWidth()) { Text("Projects") }
        Button(onClick = { onNavigate(Surface.EDITOR) }, Modifier.fillMaxWidth()) { Text("Editor") }
        Button(onClick = { onNavigate(Surface.TERMINAL) }, Modifier.fillMaxWidth()) { Text("Terminal") }
        Button(onClick = { onNavigate(Surface.BROWSER) }, Modifier.fillMaxWidth()) { Text("Browser") }
        Button(onClick = { onNavigate(Surface.GIT) }, Modifier.fillMaxWidth()) { Text("Git") }
        Button(onClick = { onNavigate(Surface.EXTENSIONS) }, Modifier.fillMaxWidth()) { Text("Extensions") }
        Button(onClick = { onNavigate(Surface.SETTINGS) }, Modifier.fillMaxWidth()) { Text("Settings") }
        OutlinedButton(onClick = onExit, Modifier.fillMaxWidth()) { Text("Exit") }
    }
}
