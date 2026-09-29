package dev.android.ide.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.android.ide.CrashReportSummary
import java.text.DateFormat
import java.util.Date

/** First-class diagnostics workspace. Confirmation and operational dialogs belong to this domain. */
@Composable
fun CrashConsoleSurface(
    reports: List<CrashReportSummary>,
    recoveryCount: Int = 0,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: (CrashReportSummary) -> Unit,
    onShare: (CrashReportSummary) -> Unit,
    onExport: (CrashReportSummary) -> Unit,
) {
    var selectedId by remember(reports) { mutableStateOf(reports.firstOrNull()?.id) }
    var showMessage by remember(selectedId) { mutableStateOf(false) }
    var showStackTrace by remember(selectedId) { mutableStateOf(false) }
    var showJson by remember(selectedId) { mutableStateOf(false) }
    var operationFeedback by remember { mutableStateOf<String?>(null) }
    var exportCandidate by remember { mutableStateOf<CrashReportSummary?>(null) }
    val selected = reports.firstOrNull { it.id == selectedId } ?: reports.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics console") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh diagnostics") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Inspect application failures progressively. Select a report for full diagnostics, then copy, share, or export its JSON log.",
                style = MaterialTheme.typography.bodySmall,
            )
            operationFeedback?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Report catalogue", style = MaterialTheme.typography.titleSmall)
                if (reports.isEmpty()) {
                    Text("No persisted crash reports are available.")
                    Text(
                        if (recoveryCount == 0) "No recovery entries are currently recorded."
                        else "$recoveryCount recovery entr${if (recoveryCount == 1) "y is" else "ies are"} available to inspect from the editor.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    reports.forEach { report ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedId = report.id },
                            colors = CardDefaults.cardColors(
                                containerColor = if (report.id == selected?.id) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(report.exception, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    report.message.ifBlank { "No exception message" },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(formatCrashTime(report.timestampMs), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    selected?.let { report ->
                        HorizontalDivider()
                        Text("Selected diagnostics", style = MaterialTheme.typography.titleSmall)
                        Text("Exception: ${report.exception}")
                        Text("App ${report.appVersion} • Android ${report.androidVersion} (API ${report.apiLevel})")
                        Text("ABI: ${report.abi} • Thread: ${report.thread}")
                        if (recoveryCount > 0) Text("Recovery entries available: $recoveryCount", style = MaterialTheme.typography.bodySmall)
                        DiagnosticSection("Message", showMessage, { showMessage = !showMessage }) {
                            SelectionContainer { Text(report.message.ifBlank { "No exception message" }, style = MaterialTheme.typography.bodySmall) }
                        }
                        DiagnosticSection("Stack trace", showStackTrace, { showStackTrace = !showStackTrace }) {
                            SelectionContainer {
                                Text(report.stackTrace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                            }
                        }
                        DiagnosticSection("Raw JSON", showJson, { showJson = !showJson }) {
                            SelectionContainer {
                                Text(report.rawJson, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onCopy(report); operationFeedback = "Full report copied to the clipboard." }) { Text("Copy") }
                            OutlinedButton(onClick = { onShare(report); operationFeedback = "Share sheet opened for the full report." }) { Text("Share") }
                            OutlinedButton(onClick = { exportCandidate = report }) { Text("Export JSON") }
                        }
                    }
                }
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to previous surface") }
        }
    }
    exportCandidate?.let { report ->
        AlertDialog(
            onDismissRequest = { exportCandidate = null },
            title = { Text("Export diagnostics JSON?") },
            text = { Text("Export the complete selected report, including its stack trace and raw diagnostic fields, as a JSON log file.") },
            confirmButton = {
                Button(onClick = {
                    exportCandidate = null
                    onExport(report)
                    operationFeedback = "Choose a destination for the JSON log."
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { exportCandidate = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DiagnosticSection(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title)
                Text(if (expanded) "Hide" else "Show")
            }
        }
        if (expanded) content()
    }
}

private fun formatCrashTime(timestampMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampMs))
