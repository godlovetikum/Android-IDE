// android-ide/android/java/dev/androidide/data/EditorSettingsRepository.kt
//
// Persists editor display, behaviour, and control settings in SharedPreferences.

package dev.androidide.data

import android.content.Context
import dev.androidide.data.model.EditorSettings
import dev.androidide.data.model.PreviewLayout
import dev.androidide.data.model.VolumeKeyMode

class EditorSettingsRepository(context: Context) {

    companion object {
        private const val PREFS               = "editor_settings"
        private const val KEY_FONT_SIZE       = "font_size"
        private const val KEY_TAB_SIZE        = "tab_size"
        private const val KEY_WORD_WRAP       = "word_wrap"
        private const val KEY_LINE_NUMBERS    = "line_numbers"
        private const val KEY_AUTO_SAVE       = "auto_save"
        private const val KEY_EDITOR_THEME    = "editor_theme"
        private const val KEY_PREVIEW_LAYOUT  = "preview_layout"
        private const val KEY_VOLUME_MODE     = "volume_key_mode"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getEditorSettings(): EditorSettings = EditorSettings(
        fontSize      = prefs.getInt(KEY_FONT_SIZE, 14),
        tabSize       = prefs.getInt(KEY_TAB_SIZE, 4),
        wordWrap      = prefs.getBoolean(KEY_WORD_WRAP, false),
        lineNumbers   = prefs.getBoolean(KEY_LINE_NUMBERS, true),
        autoSave      = prefs.getBoolean(KEY_AUTO_SAVE, false),
        editorTheme   = prefs.getString(KEY_EDITOR_THEME, "system") ?: "system",
        previewLayout = runCatching {
            PreviewLayout.valueOf(
                prefs.getString(KEY_PREVIEW_LAYOUT, PreviewLayout.PREVIEW_ABOVE.name) ?: "PREVIEW_ABOVE"
            )
        }.getOrElse { PreviewLayout.PREVIEW_ABOVE },
    )

    fun setEditorSettings(settings: EditorSettings) {
        prefs.edit()
            .putInt(KEY_FONT_SIZE,      settings.fontSize)
            .putInt(KEY_TAB_SIZE,       settings.tabSize)
            .putBoolean(KEY_WORD_WRAP,     settings.wordWrap)
            .putBoolean(KEY_LINE_NUMBERS,  settings.lineNumbers)
            .putBoolean(KEY_AUTO_SAVE,     settings.autoSave)
            .putString(KEY_EDITOR_THEME,   settings.editorTheme)
            .putString(KEY_PREVIEW_LAYOUT, settings.previewLayout.name)
            .apply()
    }

    fun getVolumeKeyMode(): VolumeKeyMode = runCatching {
        VolumeKeyMode.valueOf(prefs.getString(KEY_VOLUME_MODE, VolumeKeyMode.HORIZONTAL.name) ?: "HORIZONTAL")
    }.getOrElse { VolumeKeyMode.HORIZONTAL }

    fun setVolumeKeyMode(mode: VolumeKeyMode) {
        prefs.edit().putString(KEY_VOLUME_MODE, mode.name).apply()
    }
}
