package dev.android.ide.ui.screen

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.android.ide.app.AppShellState
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.contracts.RuntimeAvailability
import dev.android.ide.contracts.SessionAvailability

@Composable
fun TerminalSurface(state: AppShellState, viewModel: AppShellViewModel, onOpenNavigation: () -> Unit, modifier: Modifier = Modifier) {
    var packageInput by remember { mutableStateOf("") }
    var packageMenuOpen by remember { mutableStateOf(false) }
    var packageDialogOpen by remember { mutableStateOf(false) }
    var ctrlLatched by remember { mutableStateOf(false) }
    var altLatched by remember { mutableStateOf(false) }
    var escapeLatched by remember { mutableStateOf(false) }
    var initialSessionRequested by remember { mutableStateOf(false) }
    val sessions = state.terminalSessions
    val selected = sessions.firstOrNull { it.id == state.selectedTerminalSessionId && it.availability == SessionAvailability.AVAILABLE }
    val runtimeAvailable = state.runtimeCapabilities?.availability == RuntimeAvailability.AVAILABLE
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(runtimeAvailable) {
        if (runtimeAvailable && !initialSessionRequested && sessions.none { it.availability == SessionAvailability.AVAILABLE }) {
            initialSessionRequested = true
            viewModel.createTerminalSession(workingDirectory = null)
        }
    }
    LaunchedEffect(runtimeAvailable) {
        if (runtimeAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onOpenNavigation) { Icon(Icons.Default.Menu, "Open sidebar") }
            Column(Modifier.weight(1f)) {
                Text("Terminal", style = MaterialTheme.typography.headlineSmall)
                Text(
                    when (state.runtimeCapabilities?.availability) {
                        RuntimeAvailability.AVAILABLE -> "Terminal available"
                        RuntimeAvailability.INITIALIZING -> "Preparing terminal…"
                        else -> state.runtimeCapabilities?.explanation ?: "Terminal unavailable"
                    },
                    color = if (runtimeAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = viewModel::refreshTerminalSessions) { Icon(Icons.Default.Refresh, "Refresh terminal sessions") }
            Box {
                IconButton(onClick = { packageMenuOpen = true }) { Icon(Icons.Default.MoreVert, "Terminal tools") }
                DropdownMenu(expanded = packageMenuOpen, onDismissRequest = { packageMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Manage terminal packages") }, onClick = { packageMenuOpen = false; packageDialogOpen = true; viewModel.refreshRuntimePackages() })
                }
            }
        }
        if (packageDialogOpen) {
            AlertDialog(
                onDismissRequest = { if (!state.terminalOperationInProgress) packageDialogOpen = false },
                title = { Text("Terminal packages") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (state.installedRuntimePackages.isEmpty()) "No additional packages are installed." else state.installedRuntimePackages.joinToString { it.name + (it.version?.let { version -> " $version" } ?: "") }, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(packageInput, { packageInput = it }, label = { Text("Package names") }, placeholder = { Text("git curl python") }, singleLine = true, enabled = !state.terminalOperationInProgress)
                        state.terminalFeedback?.let { report -> Text(report.message, color = if (report.outcome == dev.android.ide.contracts.OperationOutcome.COMPLETE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = { Button(onClick = { viewModel.installRuntimePackages(packageInput.split(Regex("[\\s,]+"))); packageInput = "" }, enabled = state.runtimeCapabilities?.packageManagerAvailable == true && packageInput.isNotBlank() && !state.terminalOperationInProgress) { Text(if (state.terminalOperationInProgress) "Installing…" else "Install") } },
                dismissButton = { TextButton(onClick = { packageDialogOpen = false }, enabled = !state.terminalOperationInProgress) { Text("Close") } },
            )
        }
        if (selected == null || selected.availability != dev.android.ide.contracts.SessionAvailability.AVAILABLE) {
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No active terminal session", style = MaterialTheme.typography.titleMedium)
                    Text(if (runtimeAvailable) "Starting a fresh command-line session in the terminal home…" else "The terminal runtime is not available yet.")
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
            TerminalShortcutToolbar(
                ctrlLatched = ctrlLatched,
                altLatched = altLatched,
                escapeLatched = escapeLatched,
                onCtrlToggle = { ctrlLatched = !ctrlLatched },
                onAltToggle = { altLatched = !altLatched },
                onEscapeToggle = { escapeLatched = !escapeLatched },
                onSend = { key ->
                    viewModel.sendTerminalInput(encodeTerminalShortcut(key, ctrlLatched, altLatched, escapeLatched))
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

private data class TerminalShortcut(val label: String, val sequence: String, val forceCtrl: Boolean = false)

private val TERMINAL_NAVIGATION_SHORTCUTS = listOf(
    TerminalShortcut("Esc key", "\u001B"),
    TerminalShortcut("Tab", "\t"),
    TerminalShortcut("Enter", "\r"),
    TerminalShortcut("Backspace", "\u007F"),
    TerminalShortcut("↑", "\u001B[A"),
    TerminalShortcut("↓", "\u001B[B"),
    TerminalShortcut("←", "\u001B[D"),
    TerminalShortcut("→", "\u001B[C"),
    TerminalShortcut("Home", "\u001B[H"),
    TerminalShortcut("End", "\u001B[F"),
    TerminalShortcut("PgUp", "\u001B[5~"),
    TerminalShortcut("PgDn", "\u001B[6~"),
)

private val TERMINAL_CONTROL_SHORTCUTS = listOf(
    TerminalShortcut("Ctrl+C", "c", forceCtrl = true),
    TerminalShortcut("Ctrl+D", "d", forceCtrl = true),
    TerminalShortcut("Ctrl+Z", "z", forceCtrl = true),
    TerminalShortcut("Ctrl+L", "l", forceCtrl = true),
)

@Composable
private fun TerminalShortcutToolbar(
    ctrlLatched: Boolean,
    altLatched: Boolean,
    escapeLatched: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onEscapeToggle: () -> Unit,
    onSend: (TerminalShortcut) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalShortcutButton("Ctrl", ctrlLatched, onCtrlToggle)
            TerminalShortcutButton("Alt", altLatched, onAltToggle)
            TerminalShortcutButton("Esc mod", escapeLatched, onEscapeToggle)
            TERMINAL_CONTROL_SHORTCUTS.forEach { shortcut ->
                TerminalShortcutButton(shortcut.label, false) { onSend(shortcut) }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TERMINAL_NAVIGATION_SHORTCUTS.forEach { shortcut ->
                TerminalShortcutButton(shortcut.label, false) { onSend(shortcut) }
            }
        }
    }
}

@Composable
private fun TerminalShortcutButton(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier
        .height(38.dp)
        .semantics { this.selected = selected }
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
    } else {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
    }
}

private fun encodeTerminalShortcut(
    shortcut: TerminalShortcut,
    ctrlLatched: Boolean,
    altLatched: Boolean,
    escapeLatched: Boolean,
): ByteArray {
    val ctrl = ctrlLatched || shortcut.forceCtrl
    val raw = shortcut.sequence
    val payload = when {
        ctrl && raw.length == 1 && raw[0].code in 0x20..0x7E -> byteArrayOf((raw[0].code and 0x1F).toByte())
        ctrl && raw.startsWith("\u001B[") -> ctrlModifiedCsi(raw).toByteArray(Charsets.UTF_8)
        else -> raw.toByteArray(Charsets.UTF_8)
    }
    val prefix = if (altLatched || escapeLatched) byteArrayOf(0x1B) else byteArrayOf()
    return prefix + payload
}

private fun ctrlModifiedCsi(sequence: String): String {
    val final = sequence.lastOrNull() ?: return sequence
    val parameter = when (final) {
        'A', 'B', 'C', 'D', 'H', 'F' -> "1;5"
        '~' -> sequence.substringAfter('[').substringBefore('~').ifBlank { "1" } + ";5"
        else -> return sequence
    }
    return "\u001B[${parameter}${final}"
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
