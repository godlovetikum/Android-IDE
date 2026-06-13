// android-ide/android/java/dev/androidide/editor/EditorMessage.kt
//
// Message protocol between Monaco (JS) and Kotlin.
//
// Inbound  (Monaco → Kotlin, via AndroidBridge.onMessage):
//   { type: "ready" }
//   { type: "contentChanged", path, content }
//   { type: "cursorMoved",    line, column }
//   { type: "fileSaved",      path }
//
// Outbound (Kotlin → Monaco, via window.androidIDE.receiveMessage):
//   { type: "loadFile",         path, content, language }
//   { type: "setTheme",         theme }
//   { type: "setFontSize",      size }
//   { type: "requestSave",      path }
//   { type: "closeTab",         path }
//   { type: "forceLayout" }
//   { type: "executeCommand",   command }
//   { type: "insertText",       text }
//   { type: "setEditorOptions", tabSize?, wordWrap?, lineNumbers?, fontSize? }
//   { type: "showFind" }
//   { type: "showReplace" }

package dev.androidide.editor

import org.json.JSONObject

// ── Inbound (Monaco → Kotlin) ─────────────────────────────────────────────────

sealed class EditorInbound {
    /** Monaco has finished initialising and is ready to receive loadFile messages. */
    object Ready : EditorInbound()

    /** User edited the file; debounced 300 ms by monaco-init.js. */
    data class ContentChanged(val path: String, val content: String) : EditorInbound()

    /** Cursor moved; emitted on every cursor position change. */
    data class CursorMoved(val line: Int, val column: Int) : EditorInbound()

    /** User pressed Ctrl+S / Cmd+S in Monaco. */
    data class FileSaved(val path: String) : EditorInbound()

    companion object {
        /**
         * Parse a JSON string from [AndroidBridge.onMessage] into an [EditorInbound].
         * Returns null for unknown message types or malformed JSON.
         */
        fun fromJson(json: String): EditorInbound? = runCatching {
            val obj = JSONObject(json)
            when (obj.getString("type")) {
                "ready"          -> Ready
                "contentChanged" -> ContentChanged(
                    path    = obj.getString("path"),
                    content = obj.getString("content"),
                )
                "cursorMoved"    -> CursorMoved(
                    line   = obj.getInt("line"),
                    column = obj.getInt("column"),
                )
                "fileSaved"      -> FileSaved(path = obj.getString("path"))
                else             -> null
            }
        }.getOrNull()
    }
}

// ── Outbound (Kotlin → Monaco) ────────────────────────────────────────────────

sealed class EditorOutbound {

    // ── File management ────────────────────────────────────────────────────

    /** Load a file into the editor. Creates or reuses a Monaco model for [path]. */
    data class LoadFile(val path: String, val content: String, val language: String) : EditorOutbound()

    /** Unload the model for [path] from Monaco. */
    data class CloseTab(val path: String) : EditorOutbound()

    /** Ask Monaco to emit a fileSaved message for the current file. */
    data class RequestSave(val path: String) : EditorOutbound()

    // ── Appearance ─────────────────────────────────────────────────────────

    /** Switch Monaco theme. Pass "dark" or "light"; monaco-init.js maps to named themes. */
    data class SetTheme(val theme: String) : EditorOutbound()

    /** Update editor font size (sp). */
    data class SetFontSize(val size: Int) : EditorOutbound()

    /**
     * Update one or more editor options. Null fields are not sent (no change).
     * Sent whenever the user changes editor settings; also on first editor ready
     * to sync the persisted settings into Monaco.
     */
    data class SetEditorOptions(
        val tabSize: Int?      = null,
        val wordWrap: Boolean? = null,
        val lineNumbers: Boolean? = null,
        val fontSize: Int?     = null,
    ) : EditorOutbound()

    // ── Layout ─────────────────────────────────────────────────────────────

    /**
     * Force Monaco to re-layout with its container's current pixel dimensions.
     * Sent once after the editor sends "ready", at which point the WebView is
     * in the Compose tree and has its final measured size.
     */
    object ForceLayout : EditorOutbound()

    // ── Commands ───────────────────────────────────────────────────────────

    /**
     * Trigger a Monaco built-in command or action by ID.
     *
     * Monaco action IDs (use editor.getAction(id).run()):
     *   editor.action.indentLines, editor.action.outdentLines,
     *   editor.action.selectAll, actions.find,
     *   editor.action.startFindReplaceAction
     *
     * Monaco keyboard handler IDs (use editor.trigger('keyboard', id, null)):
     *   cursorLeft, cursorRight, cursorUp, cursorDown,
     *   cursorHome, cursorEnd, cursorPageUp, cursorPageDown,
     *   undo, redo, deleteLeft, deleteRight,
     *   editor.action.clipboardCutAction, editor.action.clipboardCopyAction
     *
     * monaco-init.js tries getAction() first, then trigger() as a fallback.
     */
    data class ExecuteCommand(val command: String) : EditorOutbound()

    /**
     * Insert a literal string at the cursor position.
     * Used by the keyboard toolbar for bracket/punctuation buttons.
     */
    data class InsertText(val text: String) : EditorOutbound()

    /** Show Monaco's built-in find widget. */
    object ShowFind : EditorOutbound()

    /** Show Monaco's built-in find + replace widget. */
    object ShowReplace : EditorOutbound()

    // ── Serialisation ──────────────────────────────────────────────────────

    /**
     * Serialise this message to a JavaScript expression that calls
     * [window.androidIDE.receiveMessage] with the payload object.
     *
     * The caller passes the result to [android.webkit.WebView.evaluateJavascript].
     */
    fun toJs(): String {
        val payload = toJsonObject().toString()
        return "window.androidIDE && window.androidIDE.receiveMessage(JSON.parse(${escapeJs(payload)}))"
    }

    private fun toJsonObject(): JSONObject = JSONObject().apply {
        when (val msg = this@EditorOutbound) {
            is LoadFile        -> { put("type", "loadFile");    put("path", msg.path); put("content", msg.content); put("language", msg.language) }
            is CloseTab        -> { put("type", "closeTab");    put("path", msg.path) }
            is RequestSave     -> { put("type", "requestSave"); put("path", msg.path) }
            is SetTheme        -> { put("type", "setTheme");    put("theme", msg.theme) }
            is SetFontSize     -> { put("type", "setFontSize"); put("size", msg.size) }
            is SetEditorOptions-> {
                put("type", "setEditorOptions")
                msg.tabSize?.let    { put("tabSize", it) }
                msg.wordWrap?.let   { put("wordWrap", if (it) "on" else "off") }
                msg.lineNumbers?.let{ put("lineNumbers", if (it) "on" else "off") }
                msg.fontSize?.let   { put("fontSize", it) }
            }
            is ForceLayout     -> put("type", "forceLayout")
            is ExecuteCommand  -> { put("type", "executeCommand"); put("command", msg.command) }
            is InsertText      -> { put("type", "insertText");     put("text", msg.text) }
            is ShowFind        -> put("type", "showFind")
            is ShowReplace     -> put("type", "showReplace")
        }
    }

    private fun escapeJs(json: String): String {
        // Wrap in a JS single-quoted string literal, escaping characters that
        // would break the evaluateJavascript call.  JSON already escapes " and \
        // so double-backslash must come first to avoid double-escaping.
        return "'${json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")}'"
    }
}
