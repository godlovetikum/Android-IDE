package dev.android.ide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.android.ide.contracts.OperationOutcome

/** Maps a workflow outcome to the matching semantic feedback role. */
@Composable
fun operationStatusColor(outcome: OperationOutcome): Color = when (outcome) {
    OperationOutcome.COMPLETE -> LocalIdeColors.current.success
    OperationOutcome.BLOCKED,
    OperationOutcome.PARTIAL,
    OperationOutcome.INTERRUPTED -> LocalIdeColors.current.warning
    OperationOutcome.FAILED -> MaterialTheme.colorScheme.error
    OperationOutcome.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun operationStatusContainerColor(outcome: OperationOutcome): Color = when (outcome) {
    OperationOutcome.COMPLETE -> LocalIdeColors.current.successContainer
    OperationOutcome.BLOCKED,
    OperationOutcome.PARTIAL,
    OperationOutcome.INTERRUPTED -> LocalIdeColors.current.warningContainer
    OperationOutcome.FAILED -> LocalIdeColors.current.errorContainer
    OperationOutcome.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun operationStatusContentColor(outcome: OperationOutcome): Color = when (outcome) {
    OperationOutcome.COMPLETE -> LocalIdeColors.current.onSuccessContainer
    OperationOutcome.BLOCKED,
    OperationOutcome.PARTIAL,
    OperationOutcome.INTERRUPTED -> LocalIdeColors.current.onWarningContainer
    OperationOutcome.FAILED -> LocalIdeColors.current.onErrorContainer
    OperationOutcome.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
}
