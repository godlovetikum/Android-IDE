// android-ide/android/java/dev/android/ide/data/ProjectRepository.kt
//
// Persists the project registry (name, URI, last-opened timestamp) in
// SharedPreferences as a JSON array.  SharedPreferences is sufficient for the current registry volume and keeps registry ownership app-private.

package dev.android.ide.data

import android.content.Context
import dev.android.ide.data.model.Project
import dev.android.ide.contracts.CapabilityState
import org.json.JSONArray
import org.json.JSONObject

class ProjectRepository(context: Context) {

    companion object {
        private const val PREFS = "project_registry"
        private const val KEY   = "projects"
        private const val CORRUPT_KEY = "projects_corrupt_backup"
        private const val MAX   = 20      // max entries retained
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class ReadResult(val projects: List<Project>, val warning: String? = null)

    fun readAll(): ReadResult = parse(prefs.getString(KEY, "[]") ?: "[]")

    fun getAll(): List<Project> = readAll().projects

    /**
     * Insert or update a project entry; moves it to the front of the list
     * (most-recently-opened first).
     */
    fun upsert(project: Project) {
        val list = getAll().filterNot { it.stableLocationId == project.stableLocationId } + project
        save(list.sortedWith(compareByDescending<Project> { it.lastOpenedMs }.thenByDescending { it.createdMs }).take(MAX))
    }

    fun remove(stableLocationId: String): Boolean {
        val existing = getAll()
        if (existing.none { it.stableLocationId == stableLocationId }) return false
        save(existing.filterNot { it.stableLocationId == stableLocationId })
        return getAll().none { it.stableLocationId == stableLocationId }
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
                put("stableLocationId", p.stableLocationId)
                put("locationLabel", p.locationLabel)
                put("capabilityState", p.capabilityState.name)
                p.capabilityMessage?.let { put("capabilityMessage", it) }
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun parse(json: String): ReadResult {
        val array = try {
            JSONArray(json)
        } catch (_: Exception) {
            prefs.edit().putString(CORRUPT_KEY, json).apply()
            return ReadResult(emptyList(), "The project registry is malformed; a recovery copy was preserved")
        }
        val projects = mutableListOf<Project>()
        val invalid = JSONArray()
        var containsLegacyLocationKind = false
        for (index in 0 until array.length()) {
            val raw = array.opt(index)
            if (raw is JSONObject && raw.has("locationKind")) containsLegacyLocationKind = true
            val project = runCatching {
                val obj = raw as? JSONObject ?: error("Registry entry is not an object")
                val uri = obj.getString("uri").takeIf(String::isNotBlank)
                    ?: error("Registry entry has no location")
                val lastOpenedMs = obj.optLong("lastOpenedMs", obj.optLong("createdMs", 0L))
                Project(
                    name = obj.getString("name").takeIf(String::isNotBlank) ?: error("Registry entry has no name"),
                    description = obj.optString("description", ""),
                    uri = uri,
                    lastOpenedMs = lastOpenedMs,
                    createdMs = obj.optLong("createdMs", lastOpenedMs),
                    stableLocationId = obj.optString("stableLocationId", uri).takeIf(String::isNotBlank)
                        ?: error("Registry entry has no stable identity"),
                    locationLabel = obj.optString("locationLabel", uri),
                    capabilityState = runCatching {
                        CapabilityState.valueOf(obj.optString("capabilityState"))
                    }.getOrDefault(CapabilityState.NOT_YET_CHECKED),
                    capabilityMessage = obj.optString("capabilityMessage").takeIf { it.isNotBlank() },
                )
            }.getOrNull()
            if (project == null) invalid.put(raw) else projects += project
        }
        if (invalid.length() > 0) {
            prefs.edit().putString(CORRUPT_KEY, invalid.toString()).apply()
            return ReadResult(
                projects.sortedWith(compareByDescending<Project> { it.lastOpenedMs }.thenByDescending { it.createdMs }),
                "Some malformed project registry entries were preserved for recovery; valid projects remain available",
            )
        }
        val sorted = projects.sortedWith(compareByDescending<Project> { it.lastOpenedMs }.thenByDescending { it.createdMs })
        if (containsLegacyLocationKind) save(sorted)
        return ReadResult(sorted)
    }
}
