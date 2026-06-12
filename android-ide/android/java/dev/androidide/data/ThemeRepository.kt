// android-ide/android/java/dev/androidide/data/ThemeRepository.kt
//
// Persists the user's chosen application theme (Dark / Light / System).

package dev.androidide.data

import android.content.Context
import dev.androidide.data.model.AppTheme

class ThemeRepository(context: Context) {

    companion object {
        private const val PREFS = "ide_theme"
        private const val KEY   = "app_theme"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(): AppTheme = runCatching {
        AppTheme.valueOf(prefs.getString(KEY, AppTheme.DARK.name) ?: AppTheme.DARK.name)
    }.getOrElse { AppTheme.DARK }

    fun set(theme: AppTheme) {
        prefs.edit().putString(KEY, theme.name).apply()
    }
}
