// ApplicationStateStore owns the small global envelope needed to reconnect durable
// application context without moving project files or project-owned child state into
// global preferences.
package dev.android.ide.app

import android.content.Context
import dev.android.ide.contracts.Surface

class ApplicationStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun lastProjectId(): String? = preferences.getString(KEY_LAST_PROJECT, null)

    fun recordProject(projectId: String) {
        preferences.edit().putString(KEY_LAST_PROJECT, projectId).apply()
    }

    fun recordSurface(surface: Surface) {
        preferences.edit().putString(KEY_LAST_SURFACE, surface.name).apply()
    }

    fun lastSurface(): Surface = runCatching {
        Surface.valueOf(preferences.getString(KEY_LAST_SURFACE, Surface.HOME.name) ?: Surface.HOME.name)
    }.getOrDefault(Surface.HOME)

    private companion object {
        const val PREFERENCES = "application_state"
        const val KEY_LAST_PROJECT = "last_project_id"
        const val KEY_LAST_SURFACE = "last_surface"
    }
}
