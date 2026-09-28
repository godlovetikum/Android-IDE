package dev.android.ide.ui.screen

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.android.ide.app.AppShellState
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.contracts.RuntimeAvailability

@Composable
fun TerminalSurface(state: AppShellState, viewModel: AppShellViewModel, modifier: Modifier = Modifier) {
    var sessionName by remember { mutableStateOf("") }
    var packageInput by remember { mutableStateOf("") }
    val sessions = state.terminalSessions
    val selected = sessions.firstOrNull { it.id == state.selectedTerminalSessionId }
    val runtimeAvailable = state.runtimeCapabilities?.availability == RuntimeAvailability.AVAILABLE

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Terminal", style = MaterialTheme.typography.headlineSmall)
                Text(
                    when (state.runtimeCapabilities?.availability) {
                        RuntimeAvailability.AVAILABLE -> "Runtime available"
                        RuntimeAvailability.INITIALIZING -> "Runtime initializing…"
                        else -> state.runtimeCapabilities?.explanation ?: "Runtime unavailable"
                    },
                    color = if (runtimeAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = viewModel::refreshTerminalSessions) { Icon(Icons.Default.Refresh, "Refresh terminal sessions") }
            IconButton(onClick = viewModel::refreshRuntimePackages) { Icon(Icons.Default.Refresh, "Refresh installed packages") }
            IconButton(onClick = viewModel::closeAllTerminalSessions, enabled = sessions.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, "Close all terminal sessions") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Runtime packages", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (state.installedRuntimePackages.isEmpty()) "No additional packages are installed."
                    else state.installedRuntimePackages.joinToString { it.name + (it.version?.let { version -> " $version" } ?: "") },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(packageInput, { packageInput = it }, Modifier.weight(1f), label = { Text("Package names") }, placeholder = { Text("git curl python") }, singleLine = true)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Button(onClick = { viewModel.installRuntimePackages(packageInput.split(Regex("[\\s,]+"))); packageInput = "" }, enabled = state.runtimeCapabilities?.packageManagerAvailable == true && packageInput.isNotBlank() && !state.terminalOperationInProgress) {
                        if (state.terminalOperationInProgress) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 6.dp))
                        Text(if (state.terminalOperationInProgress) "Installing…" else "Install")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sessions.forEach { session ->
                TextButton(onClick = { viewModel.selectTerminalSession(session.id) }) {
                    Text(session.name, color = if (session.id == selected?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.closeTerminalSession(session.id) }) { Icon(Icons.Default.Close, "Close session") }
            }
            OutlinedTextField(sessionName, { sessionName = it }, label = { Text("Session name") }, singleLine = true, modifier = Modifier.width(180.dp))
            Button(onClick = { viewModel.createTerminalSession(name = sessionName); sessionName = "" }, enabled = runtimeAvailable) { Icon(Icons.Default.Add, null); Text("New") }
        }
        if (selected == null) {
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No terminal session", style = MaterialTheme.typography.titleMedium)
                    Text("Create a session to use the integrated terminal.")
                    Button(onClick = { viewModel.createTerminalSession(name = sessionName); sessionName = "" }, enabled = runtimeAvailable) { Icon(Icons.Default.Add, null); Text("Create session") }
                }
            }
        } else {
            DisposableEffect(selected.id) {
                onDispose { viewModel.unbindTerminalView(selected.id) }
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    TerminalView(context, null).also { terminalView ->
                        terminalView.setBackgroundColor(android.graphics.Color.rgb(16, 18, 22))
                        terminalView.setTerminalViewClient(IdeTerminalViewClient())
                        viewModel.bindTerminalView(selected.id) { terminalView.postInvalidateOnAnimation() }
                        viewModel.terminalSession(selected.id)?.let(terminalView::attachSession)
                    }
                },
                update = { view ->
                    val live = viewModel.terminalSession(selected.id)
                    if (live != null && view.mTermSession !== live) view.attachSession(live)
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Tap the terminal to type. Long-press for selection and copy.", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = viewModel::interruptTerminalSession, enabled = selected.availability == dev.android.ide.contracts.SessionAvailability.AVAILABLE) { Icon(Icons.Default.Stop, "Interrupt session") }
            }
        }
        state.terminalFeedback?.let { report ->
            Text(report.message, color = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
    if (state.terminalOperationInProgress) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Card {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 10.dp), strokeWidth = 2.dp)
                    Text("Installing selected packages…")
                }
            }
        }
    }
    }
}

private class IdeTerminalViewClient : TerminalViewClient {
    override fun onScale(scale: Float) = scale
    override fun onSingleTapUp(e: MotionEvent) = Unit
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: com.termux.terminal.TerminalSession) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
    override fun onLongPress(event: MotionEvent) = false
    override fun readControlKey() = false
    override fun readAltKey() = false
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: com.termux.terminal.TerminalSession) = false
    override fun onEmulatorSet() = Unit
    override fun logError(tag: String, message: String) = Unit
    override fun logWarn(tag: String, message: String) = Unit
    override fun logInfo(tag: String, message: String) = Unit
    override fun logDebug(tag: String, message: String) = Unit
    override fun logVerbose(tag: String, message: String) = Unit
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
    override fun logStackTrace(tag: String, e: Exception) = Unit
}
