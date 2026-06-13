// android-ide/android/java/dev/androidide/data/model/EditorSettings.kt
//
// Editor display and behaviour settings, persisted by EditorSettingsRepository.

package dev.androidide.data.model

data class EditorSettings(
    val fontSize: Int          = 14,
    val tabSize: Int           = 4,
    val wordWrap: Boolean      = false,
    val lineNumbers: Boolean   = true,
    val autoSave: Boolean      = false,
    /** Monaco theme: "dark" | "light" | "system" (follows app theme). */
    val editorTheme: String    = "system",
    /** Portrait-mode stacking order for the preview panel. */
    val previewLayout: PreviewLayout = PreviewLayout.PREVIEW_ABOVE,
    /** Show the keyboard accessory toolbar above the system keyboard. */
    val showKeyboardToolbar: Boolean = true,
    /** Show the symbol shortcut bar above the keyboard toolbar. */
    val showSymbolBar: Boolean       = true,
    /** Hide the root .git folder in the file tree sidebar. */
    val hideGitFolder: Boolean       = true,
    /**
     * Code symbol shortcuts shown in the symbol bar.
     * Each entry is a short string (typically 1–2 chars) inserted on tap.
     */
    val customSymbols: List<String>  = DEFAULT_SYMBOLS,
) {
    companion object {
        val DEFAULT_SYMBOLS = listOf("<", ">", "/", "=", "(", ")", "{", "}", "[", "]", "\"", "`")
    }
}
