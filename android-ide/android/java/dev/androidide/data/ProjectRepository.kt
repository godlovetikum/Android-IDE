// android-ide/android/java/dev/androidide/data/ProjectRepository.kt
//
// Persists the project registry (name, URI, last-opened timestamp) in
// SharedPreferences as a JSON array.  No Room DB required for Phase 1.

package dev.androidide.data

import android.content.Context
import dev.androidide.data.model.Project
import org.json.JSONArray
import org.json.JSONObject

class ProjectRepository(context: Context) {

    companion object {
        private const val PREFS = "project_registry"
        private const val KEY   = "projects"
        private const val MAX   = 20      // max entries retained
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(): List<Project> = parse(prefs.getString(KEY, "[]") ?: "[]")

    /**
     * Insert or update a project entry; moves it to the front of the list
     * (most-recently-opened first).
     */
    fun upsert(project: Project) {
        val list = getAll().filterNot { it.uri == project.uri }.toMutableList()
        list.add(0, project)
        save(list.take(MAX))
    }

    fun remove(uri: String) {
        save(getAll().filterNot { it.uri == uri })
    }

    private fun save(projects: List<Project>) {
        val arr = JSONArray()
        projects.forEach { p ->
            arr.put(JSONObject().apply {
                put("name",         p.name)
                put("uri",          p.uri)
                put("lastOpenedMs", p.lastOpenedMs)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun parse(json: String): List<Project> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Project(
                name         = obj.getString("name"),
                uri          = obj.getString("uri"),
                lastOpenedMs = obj.optLong("lastOpenedMs", System.currentTimeMillis()),
            )
        }
    }.getOrElse { emptyList() }
}
