// android-ide/android/java/dev/androidide/data/model/EditorSettings.kt
//
// Editor display and behaviour settings, persisted by EditorSettingsRepository.

package dev.androidide.data.model

data class EditorSettings(
    // ── Monaco editor ───────────────────────────────────────────────────────
    val fontSize: Int          = 14,
    val tabSize: Int           = 4,
    val wordWrap: Boolean      = false,
    val lineNumbers: Boolean   = true,
    val autoSave: Boolean      = false,
    /** Monaco theme: "dark" | "light" | "system" (follows app theme). */
    val editorTheme: String    = "system",
    /**
     * C014: Monaco renderWhitespace option.
     * "none"      — whitespace never highlighted
     * "selection" — whitespace highlighted in selected text (default)
     * "all"       — all whitespace always highlighted
     * "boundary"  — whitespace at block boundaries
     */
    val renderWhitespace: String = "selection",
    // ── F017: additional Monaco settings surface ────────────────────────────
    /** Show/hide the minimap (code overview) on the right side of the editor. */
    val minimapEnabled: Boolean           = true,
    /** Allow scrolling past the last line so it can sit in the centre of the view. */
    val scrollBeyondLastLine: Boolean     = false,
    /**
     * Cursor render shape.
     * "line"       — thin vertical bar (default)
     * "block"      — filled block that sits over the next character
     * "underline"  — horizontal underline below the current character
     */
    val cursorStyle: String               = "line",
    /** Enable bracket-pair colourisation — one colour per nesting level. */
    val bracketPairColorization: Boolean  = true,
    /**
     * Auto-close brackets.
     * "always"           — always insert the matching bracket
     * "languageDefined"  — language grammar decides (default)
     * "never"            — no auto-closing
     */
    val autoClosingBrackets: String       = "languageDefined",

    /** Portrait-mode stacking order for the preview panel. */
    val previewLayout: PreviewLayout = PreviewLayout.PREVIEW_ABOVE,

    // ── Keyboard ────────────────────────────────────────────────────────────
    /** Show the keyboard accessory toolbar above the system keyboard. */
    val showKeyboardToolbar: Boolean = true,
    /** Show the symbol shortcut bar above the keyboard toolbar. */
    val showSymbolBar: Boolean       = true,
    /**
     * Code symbol shortcuts shown in the symbol bar.
     * Each entry is a short string (typically 1–2 chars) inserted on tap.
     */
    val customSymbols: List<String>  = DEFAULT_SYMBOLS,

    // ── File tree ───────────────────────────────────────────────────────────
    /** Hide the root .git folder in the file tree sidebar. */
    val hideGitFolder: Boolean       = true,

    // ── UI font scaling ─────────────────────────────────────────────────────
    /**
     * Multiplier applied to the entire application's text scale via
     * LocalDensity.fontScale. Affects the file tree, menus, settings,
     * dialogs, and all other UI chrome — not just the Monaco editor.
     *
     * Range: 0.75 – 1.50. Default: 1.0 (system default).
     */
    val uiFontScale: Float = 1.0f,

    // ── Project storage ─────────────────────────────────────────────────────
    /**
     * Absolute path to the default directory for new blank projects.
     * Empty string → use app-specific external storage (no special permission needed).
     * When non-empty, the user-chosen directory is used for createBlankProject.
     */
    val defaultProjectDir: String = "",
) {
    companion object {
        val DEFAULT_SYMBOLS = listOf("<", ">", "/", "=", "(", ")", "{", "}", "[", "]", "\"", "`")

        const val UI_FONT_SCALE_MIN = 0.75f
        const val UI_FONT_SCALE_MAX = 1.50f
        const val UI_FONT_SCALE_STEP = 0.05f
    }
}
