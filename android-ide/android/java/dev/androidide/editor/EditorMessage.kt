// android-ide/android/java/dev/androidide/editor/EditorMessage.kt
//
// Message protocol between Monaco (JS) and Kotlin.
//
// Migration note (2026-06-12):
//   Replaces the Rust EditorInbound / EditorOutbound enums in
//   modules/editor/src/bridge.rs.
//
// Protocol (unchanged from the Rust bridge — monaco-init.js is NOT modified):
//
//   Inbound  (Monaco → Kotlin, via AndroidBridge.onMessage):
//     { type: "ready" }
//     { type: "contentChanged", path, content }
//     { type: "cursorMoved",    line, column }
//     { type: "fileSaved",      path }
//
//   Outbound (Kotlin → Monaco, via window.androidIDE.receiveMessage):
//     { type: "loadFile",    path, content, language }
//     { type: "setTheme",    theme }
//     { type: "setFontSize", size }
//     { type: "requestSave", path }
//     { type: "closeTab",    path }

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
    /** Load a file into the editor. Creates or reuses a Monaco model for [path]. */
    data class LoadFile(val path: String, val content: String, val language: String) : EditorOutbound()

    /** Switch Monaco theme. Use "vs-dark" for the androidide-dark theme. */
    data class SetTheme(val theme: String) : EditorOutbound()

    /** Update editor font size (points). */
    data class SetFontSize(val size: Int) : EditorOutbound()

    /** Ask Monaco to emit a fileSaved message for [path]. */
    data class RequestSave(val path: String) : EditorOutbound()

    /** Unload the model for [path] from Monaco. */
    data class CloseTab(val path: String) : EditorOutbound()

    /**
     * Serialise this message to a JavaScript expression that calls
     * [window.androidIDE.receiveMessage] with the payload object.
     *
     * The caller should pass the result string to
     * [android.webkit.WebView.evaluateJavascript].
     */
    fun toJs(): String {
        val payload = toJsonObject().toString()
        // JSON.parse ensures Monaco receives an object, not a string, even though
        // receiveMessage also handles string input (see monaco-init.js line 154).
        return "window.androidIDE && window.androidIDE.receiveMessage(JSON.parse(${escapeJs(payload)}))"
    }

    private fun toJsonObject(): JSONObject = JSONObject().apply {
        when (val msg = this@EditorOutbound) {
            is LoadFile  -> { put("type", "loadFile");    put("path", msg.path); put("content", msg.content); put("language", msg.language) }
            is SetTheme  -> { put("type", "setTheme");    put("theme", msg.theme) }
            is SetFontSize -> { put("type", "setFontSize"); put("size", msg.size) }
            is RequestSave -> { put("type", "requestSave"); put("path", msg.path) }
            is CloseTab  -> { put("type", "closeTab");    put("path", msg.path) }
        }
    }

    private fun escapeJs(json: String): String {
        // Wrap in a JS string literal, escaping characters that would break
        // the evaluateJavascript call.
        return "'${json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")}'"
    }
}
