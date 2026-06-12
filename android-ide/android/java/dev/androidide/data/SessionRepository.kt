// android-ide/android/java/dev/androidide/data/SessionRepository.kt
//
// Persists the IDE working session across process deaths so the user can
// resume immediately without re-navigating to their project.
//
// Stored in SharedPreferences — lightweight enough for the session envelope.
// Tab content is NOT stored (re-read from SAF on restore); only the URIs
// and the active-tab marker are persisted.

package dev.androidide.data

import android.content.Context

class SessionRepository(context: Context) {

    companion object {
        private const val PREFS              = "ide_session"
        private const val KEY_PROJECT_URI    = "project_uri"
        private const val KEY_OPEN_TABS      = "open_tab_uris"   // newline-separated
        private const val KEY_ACTIVE_TAB_URI = "active_tab_uri"
        private const val KEY_SCREEN         = "last_screen"     // AppScreen name
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(
        projectUri: String?,
        openTabUris: List<String>,
        activeTabUri: String?,
        screenName: String,
    ) {
        prefs.edit()
            .putString(KEY_PROJECT_URI,    projectUri)
            .putString(KEY_OPEN_TABS,      openTabUris.filter { it.isNotBlank() }.joinToString("\n"))
            .putString(KEY_ACTIVE_TAB_URI, activeTabUri)
            .putString(KEY_SCREEN,         screenName)
            .apply()
    }

    fun getProjectUri():    String?       = prefs.getString(KEY_PROJECT_URI, null)
    fun getOpenTabUris():   List<String>  = prefs.getString(KEY_OPEN_TABS, "")
        ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    fun getActiveTabUri():  String?       = prefs.getString(KEY_ACTIVE_TAB_URI, null)
    fun getScreenName():    String?       = prefs.getString(KEY_SCREEN, null)

    fun clear() = prefs.edit().clear().apply()
}
