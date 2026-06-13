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
)
