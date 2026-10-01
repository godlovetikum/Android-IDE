package dev.android.ide.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp

/** Small, purpose-drawn line glyphs; independent of Material's icon catalog. */
@Composable
fun EditorToolbarGlyph(
    actionId: String,
    tint: Color,
    modifier: Modifier = Modifier,
    keyboardShowing: Boolean = false,
) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val offsetX = (size.width - 24f * unit) / 2f
        val offsetY = (size.height - 24f * unit) / 2f
        val stroke = 1.8f * unit
        fun point(x: Float, y: Float) = Offset(offsetX + x * unit, offsetY + y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, point(x1, y1), point(x2, y2), stroke, cap = StrokeCap.Round)
        }
        fun path(vararg coordinates: Float) {
            if (coordinates.size < 4) return
            val shape = Path().apply {
                moveTo(point(coordinates[0], coordinates[1]).x, point(coordinates[0], coordinates[1]).y)
                var index = 2
                while (index + 1 < coordinates.size) {
                    val p = point(coordinates[index], coordinates[index + 1])
                    lineTo(p.x, p.y)
                    index += 2
                }
            }
            drawPath(shape, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        fun rect(left: Float, top: Float, right: Float, bottom: Float, radius: Float = 2f) {
            drawRoundRect(
                color = tint,
                topLeft = point(left, top),
                size = Size((right - left) * unit, (bottom - top) * unit),
                cornerRadius = CornerRadius(radius * unit),
                style = Stroke(stroke),
            )
        }
        fun circle(x: Float, y: Float, radius: Float, filled: Boolean = false) {
            drawCircle(
                color = tint,
                radius = radius * unit,
                center = point(x, y),
                style = if (filled) Fill else Stroke(stroke),
            )
        }
        fun arrow(direction: String, start: Float = 6f, end: Float = 18f, cross: Float = 12f) {
            when (direction) {
                "left" -> { line(end, cross, start, cross); line(start, cross, start + 4f, cross - 4f); line(start, cross, start + 4f, cross + 4f) }
                "right" -> { line(start, cross, end, cross); line(end, cross, end - 4f, cross - 4f); line(end, cross, end - 4f, cross + 4f) }
                "up" -> { line(cross, end, cross, start); line(cross, start, cross - 4f, start + 4f); line(cross, start, cross + 4f, start + 4f) }
                else -> { line(cross, start, cross, end); line(cross, end, cross - 4f, end - 4f); line(cross, end, cross + 4f, end - 4f) }
            }
        }
        fun textRows() {
            line(5f, 7f, 19f, 7f)
            line(5f, 12f, 17f, 12f)
            line(5f, 17f, 19f, 17f)
        }
        fun plus(x: Float, y: Float, half: Float = 3f) {
            line(x - half, y, x + half, y)
            line(x, y - half, x, y + half)
        }
        fun minus(x: Float, y: Float, half: Float = 3f) = line(x - half, y, x + half, y)
        fun cross(x: Float, y: Float, half: Float = 3f) {
            line(x - half, y - half, x + half, y + half)
            line(x - half, y + half, x + half, y - half)
        }
        fun bentArrow(right: Boolean) {
            val shape = Path().apply {
                val a = point(if (right) 6f else 18f, 8f)
                val b = point(12f, 8f)
                val c = point(12f, 16f)
                val d = point(if (right) 18f else 6f, 16f)
                moveTo(a.x, a.y)
                cubicTo(point(8f, 8f).x, point(8f, 8f).y, b.x, b.y, b.x, b.y)
                lineTo(c.x, c.y)
                lineTo(d.x, d.y)
            }
            drawPath(shape, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val tipX = if (right) 18f else 6f
            line(tipX, 16f, if (right) tipX - 3f else tipX + 3f, 13f)
            line(tipX, 16f, if (right) tipX - 3f else tipX + 3f, 19f)
        }

        when (actionId) {
            "cursorLeft", "selectLeft" -> { arrow("left"); if (actionId == "selectLeft") line(9f, 7f, 9f, 17f) }
            "cursorRight", "selectRight" -> { arrow("right"); if (actionId == "selectRight") line(15f, 7f, 15f, 17f) }
            "cursorUp", "selectUp" -> { arrow("up"); if (actionId == "selectUp") line(7f, 9f, 17f, 9f) }
            "cursorDown", "selectDown" -> { arrow("down"); if (actionId == "selectDown") line(7f, 15f, 17f, 15f) }
            "cursorHome", "cursorWordLeft", "selectWordLeft", "selectToStart" -> {
                line(6f, 5f, 6f, 19f); arrow("left", 8f, 18f)
                if (actionId == "cursorWordLeft" || actionId == "selectWordLeft") { circle(16f, 6f, 1f, true); circle(19f, 6f, 1f, true) }
                if (actionId == "selectToStart") line(9f, 17f, 9f, 7f)
            }
            "cursorEnd", "cursorWordRight", "selectWordRight", "selectToEnd" -> {
                line(18f, 5f, 18f, 19f); arrow("right", 6f, 16f)
                if (actionId == "cursorWordRight" || actionId == "selectWordRight") { circle(5f, 6f, 1f, true); circle(8f, 6f, 1f, true) }
                if (actionId == "selectToEnd") line(15f, 17f, 15f, 7f)
            }
            "cursorPageUp", "cursorPageDown", "cursorDocumentStart", "cursorDocumentEnd" -> {
                rect(5f, 4f, 19f, 20f, 1.5f)
                if (actionId == "cursorPageUp" || actionId == "cursorDocumentStart") arrow("up", 8f, 16f)
                else arrow("down", 8f, 16f)
                if (actionId == "cursorDocumentStart" || actionId == "cursorDocumentEnd") line(8f, 5f, 16f, 5f)
            }
            "indent", "outdent" -> {
                line(11f, 6f, 19f, 6f); line(11f, 12f, 19f, 12f); line(11f, 18f, 19f, 18f)
                if (actionId == "indent") { line(5f, 7f, 5f, 17f); line(5f, 12f, 9f, 8f); line(5f, 12f, 9f, 16f) }
                else { line(9f, 7f, 9f, 17f); line(9f, 12f, 5f, 8f); line(9f, 12f, 5f, 16f) }
            }
            "undo", "redo" -> {
                val right = actionId == "redo"
                val curve = Path().apply {
                    val start = point(if (right) 18f else 6f, 8f)
                    val end = point(if (right) 6f else 18f, 16f)
                    moveTo(start.x, start.y)
                    cubicTo(point(if (right) 11f else 13f, 3f).x, point(if (right) 11f else 13f, 3f).y,
                        point(if (right) 11f else 13f, 21f).x, point(if (right) 11f else 13f, 21f).y, end.x, end.y)
                }
                drawPath(curve, tint, style = Stroke(stroke, cap = StrokeCap.Round))
                val x = if (right) 18f else 6f
                line(x, 8f, if (right) x - 4f else x + 4f, 5f)
                line(x, 8f, if (right) x - 4f else x + 4f, 11f)
            }
            "deleteLeft", "deleteRight", "deleteWordLeft", "deleteWordRight" -> {
                val left = actionId.endsWith("Left")
                if (left) path(18f, 5f, 11f, 5f, 5f, 12f, 11f, 19f, 18f, 19f, 18f, 5f)
                else path(6f, 5f, 13f, 5f, 19f, 12f, 13f, 19f, 6f, 19f, 6f, 5f)
                cross(12f, 12f, if (actionId.startsWith("deleteWord")) 2.5f else 3.5f)
                if (actionId.startsWith("deleteWord")) { circle(7f, 7f, 1f, true); circle(7f, 17f, 1f, true) }
            }
            "insertLineAfter", "insertLineBefore" -> {
                line(5f, 7f, 19f, 7f); line(5f, 12f, 16f, 12f); line(5f, 17f, 19f, 17f)
                plus(18f, if (actionId == "insertLineAfter") 17f else 7f, 2.5f)
            }
            "cut" -> { circle(7f, 7f, 2f); circle(7f, 17f, 2f); line(9f, 8f, 19f, 18f); line(9f, 16f, 19f, 6f) }
            "copy", "duplicateSelection" -> { rect(8f, 5f, 19f, 17f); rect(5f, 8f, 16f, 20f); if (actionId == "duplicateSelection") plus(17f, 18f, 2f) }
            "paste" -> { rect(6f, 6f, 18f, 20f); rect(9f, 4f, 15f, 8f); line(9f, 12f, 15f, 12f); line(9f, 16f, 15f, 16f) }
            "selectAll" -> { rect(5f, 5f, 19f, 19f, 1f); line(8f, 8f, 16f, 8f); line(8f, 12f, 16f, 12f); line(8f, 16f, 16f, 16f) }
            "selectLine" -> { line(5f, 7f, 19f, 7f); line(5f, 12f, 19f, 12f); line(5f, 17f, 19f, 17f); line(7f, 10f, 7f, 14f) }
            "selectWord" -> { rect(4f, 7f, 20f, 17f, 1f); line(7f, 12f, 10f, 12f); line(12f, 12f, 17f, 12f) }
            "expandSelection", "shrinkSelection" -> {
                rect(7f, 7f, 17f, 17f, 1f)
                if (actionId == "expandSelection") { line(5f, 5f, 8f, 8f); line(19f, 5f, 16f, 8f); line(5f, 19f, 8f, 16f); line(19f, 19f, 16f, 16f) }
                else { line(5f, 8f, 8f, 8f); line(19f, 8f, 16f, 8f); line(5f, 16f, 8f, 16f); line(19f, 16f, 16f, 16f) }
            }
            "addCursorAbove", "addCursorBelow", "addCursorAtLineEnds" -> {
                line(5f, 7f, 19f, 7f); line(5f, 12f, 19f, 12f); line(5f, 17f, 19f, 17f)
                val y = if (actionId == "addCursorAbove") 5f else if (actionId == "addCursorBelow") 19f else 12f
                plus(if (actionId == "addCursorAtLineEnds") 18f else 12f, y, 2.5f)
            }
            "addNextOccurrence", "addPreviousOccurrence", "selectAllOccurrences" -> {
                circle(8f, 8f, 2f); circle(16f, 8f, 2f); circle(8f, 16f, 2f); circle(16f, 16f, 2f)
                if (actionId == "addNextOccurrence") plus(16f, 16f, 2f)
                if (actionId == "addPreviousOccurrence") minus(16f, 16f, 2f)
            }
            "formatDocument", "formatSelection" -> { rect(5f, 5f, 19f, 19f); line(8f, 9f, 16f, 9f); line(8f, 13f, 13f, 13f); line(8f, 17f, 11f, 17f); line(16f, 15f, 19f, 18f) }
            "commentLine", "commentSelection" -> { rect(4f, 5f, 20f, 17f, 3f); line(8f, 9f, 16f, 9f); line(8f, 13f, 14f, 13f); line(8f, 17f, 6f, 20f) }
            "moveLineUp" -> { line(6f, 15f, 18f, 15f); line(6f, 19f, 18f, 19f); arrow("up", 7f, 17f) }
            "moveLineDown" -> { line(6f, 5f, 18f, 5f); line(6f, 9f, 18f, 9f); arrow("down", 7f, 17f) }
            "joinLines" -> { line(5f, 6f, 10f, 6f); line(19f, 6f, 14f, 6f); path(10f, 6f, 12f, 9f, 14f, 12f); line(5f, 18f, 19f, 18f) }
            "sortLinesAscending", "sortLinesDescending", "organizeImports" -> {
                textRows()
                if (actionId == "sortLinesAscending") arrow("up", 9f, 17f)
                if (actionId == "sortLinesDescending") arrow("down", 9f, 17f)
                if (actionId == "organizeImports") { line(18f, 7f, 18f, 17f); line(18f, 17f, 15f, 14f); line(18f, 17f, 21f, 14f) }
            }
            "fold", "foldAll", "foldLevel1", "foldLevel2" -> { textRows(); line(7f, 8f, 11f, 12f); line(11f, 12f, 7f, 16f) }
            "unfold", "unfoldAll" -> { textRows(); line(9f, 8f, 5f, 12f); line(5f, 12f, 9f, 16f) }
            "goToDefinition", "goToDeclaration", "goToTypeDefinition", "goToImplementation", "findReferences" -> {
                circle(8f, 8f, 3f); circle(16f, 16f, 3f); line(10f, 10f, 14f, 14f)
                if (actionId == "goToTypeDefinition") { circle(16f, 8f, 2f); line(10f, 8f, 14f, 8f) }
                if (actionId == "findReferences") { circle(8f, 16f, 2f); line(8f, 11f, 8f, 14f) }
            }
            "goBack" -> arrow("left", 7f, 18f)
            "goForward" -> arrow("right", 7f, 18f)
            "triggerSuggest", "quickFix", "codeAction" -> {
                circle(12f, 11f, 5f); line(9f, 16f, 9f, 19f); line(15f, 16f, 15f, 19f); line(10f, 20f, 14f, 20f)
                if (actionId != "triggerSuggest") { line(12f, 2f, 12f, 4f); line(3f, 11f, 5f, 11f); line(19f, 11f, 21f, 11f) }
            }
            "triggerParameterHints" -> { path(5f, 6f, 9f, 6f, 9f, 10f, 6f, 12f, 9f, 14f, 9f, 18f, 5f, 18f); circle(16f, 17f, 1f, true); line(16f, 13f, 16f, 14f) }
            "renameSymbol" -> { line(6f, 18f, 16f, 8f); line(5f, 19f, 9f, 18f); path(14f, 6f, 16f, 4f, 20f, 8f, 18f, 10f) }
            "toggleKeyboard" -> {
                rect(3f, 6f, 21f, 18f, 2f)
                for (x in listOf(7f, 11f, 15f, 19f)) { circle(x, 9f, 0.8f, true); circle(x, 12f, 0.8f, true) }
                line(7f, 15f, 17f, 15f)
                if (keyboardShowing) arrow("down", 8f, 16f, 12f)
            }
            "toggleWordWrap" -> { line(4f, 7f, 20f, 7f); line(4f, 12f, 16f, 12f); bentArrow(true); line(4f, 18f, 11f, 18f) }
            "zoomIn", "zoomOut" -> { circle(10f, 10f, 6f); line(14f, 14f, 20f, 20f); if (actionId == "zoomIn") plus(10f, 10f, 2.5f) else minus(10f, 10f, 2.5f) }
            "toolbarPagePrevious" -> arrow("left")
            "toolbarPageNext" -> arrow("right")
            "dragHandle" -> { circle(8f, 7f, 1f, true); circle(16f, 7f, 1f, true); circle(8f, 12f, 1f, true); circle(16f, 12f, 1f, true); circle(8f, 17f, 1f, true); circle(16f, 17f, 1f, true) }
            else -> { circle(12f, 12f, 7f); plus(12f, 12f, 3f) }
        }
    }
}
