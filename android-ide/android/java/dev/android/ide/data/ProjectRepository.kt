// android-ide/android/java/dev/android/ide/data/ProjectRepository.kt
//
// Persists the project registry (name, URI, last-opened timestamp) in
// SharedPreferences as a JSON array.  SharedPreferences is sufficient for the current registry volume and keeps registry ownership app-private.

package dev.android.ide.data

import android.content.Context
import dev.android.ide.data.model.Project
import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.ProjectLocationKind
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
                put("description",  p.description)
                put("uri",          p.uri)
                put("lastOpenedMs", p.lastOpenedMs)
                put("createdMs",    p.createdMs)
                put("locationKind", p.locationKind.name)
                put("stableLocationId", p.stableLocationId)
                put("locationLabel", p.locationLabel)
                put("capabilityState", p.capabilityState.name)
                p.capabilityMessage?.let { put("capabilityMessage", it) }
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun parse(json: String): List<Project> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val lastOpenedMs = obj.optLong("lastOpenedMs", System.currentTimeMillis())
            Project(
                name         = obj.getString("name"),
                description  = obj.optString("description", ""),
                uri          = obj.getString("uri"),
                lastOpenedMs = lastOpenedMs,
                createdMs    = obj.optLong("createdMs", lastOpenedMs),
                locationKind = runCatching {
                    ProjectLocationKind.valueOf(obj.optString("locationKind"))
                }.getOrDefault(ProjectLocationKind.USER_VISIBLE_LOCAL),
                stableLocationId = obj.optString("stableLocationId", obj.getString("uri")),
                locationLabel = obj.optString("locationLabel", obj.getString("uri")),
                capabilityState = runCatching {
                    CapabilityState.valueOf(obj.optString("capabilityState"))
                }.getOrDefault(CapabilityState.NOT_YET_CHECKED),
                capabilityMessage = obj.optString("capabilityMessage").takeIf { it.isNotBlank() },
            )
        }
    }.getOrElse { emptyList() }
}
