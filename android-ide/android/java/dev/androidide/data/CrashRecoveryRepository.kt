// android-ide/android/java/dev/androidide/data/CrashRecoveryRepository.kt
//
// Detects crashed sessions and persists unsaved editor content so it can be
// offered for restoration on the next launch.
//
// Crash detection:
//   A boolean flag tracks whether the previous session ended cleanly.
//   IdeViewModel sets it to false on startup (markSessionStart) and true in
//   onCleared (markCleanExit). If the flag is false on startup, the previous
//   session did not exit cleanly and any stored unsaved entries are presented
//   for recovery.
//
// Content storage:
//   Each unsaved tab's content is stored as a JSON blob in SharedPreferences,
//   keyed by project identity and tab ID. The content is written on every
//   contentChanged event and cleared when the tab is saved or closed normally.

package dev.androidide.data

import android.content.Context
import android.util.Base64
import dev.androidide.data.model.RecoveryEntry
import org.json.JSONObject

class CrashRecoveryRepository(context: Context) {

    companion object {
        private const val PREFS          = "crash_recovery"
        private const val KEY_CLEAN_EXIT = "clean_exit"
        private const val UNSAVED_PREFIX = "unsaved_"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Call at session startup — marks the session as in-progress. */
    fun markSessionStart() = prefs.edit().putBoolean(KEY_CLEAN_EXIT, false).apply()

    /** Call in ViewModel.onCleared — marks the session as cleanly ended. */
    fun markCleanExit() = prefs.edit().putBoolean(KEY_CLEAN_EXIT, true).apply()

    /**
     * Returns true if the previous session did not exit cleanly and there are
     * unsaved content entries worth offering for recovery.
     */
    fun isPreviousSessionDirty(): Boolean {
        if (!prefs.contains(KEY_CLEAN_EXIT)) return false   // first ever run
        return !prefs.getBoolean(KEY_CLEAN_EXIT, true)
    }

    /** Persist the current unsaved content for a tab. */
    fun saveUnsavedContent(
        projectRootUri: String,
        tabId: String,
        documentUri: String,
        displayName: String,
        content: String,
    ) {
        val json = JSONObject().apply {
            put("projectRootUri", projectRootUri)
            put("tabId",       tabId)
            put("documentUri", documentUri)
            put("displayName", displayName)
            put("content",     content)
        }.toString()
        prefs.edit().putString(storageKey(projectRootUri, tabId), json).apply()
    }

    /** Return persisted unsaved entries for one project. */
    fun getUnsavedEntries(projectRootUri: String): List<RecoveryEntry> =
        prefs.all
            .filterKeys { it.startsWith(UNSAVED_PREFIX) }
            .values
            .mapNotNull { value ->
                runCatching {
                    val obj = JSONObject(value as String)
                    val storedProjectUri = obj.optString("projectRootUri")
                    if (storedProjectUri != projectRootUri) return@runCatching null
                    RecoveryEntry(
                        projectRootUri = storedProjectUri,
                        tabId       = obj.getString("tabId"),
                        documentUri = obj.getString("documentUri"),
                        displayName = obj.getString("displayName"),
                        content     = obj.getString("content"),
                    )
                }.getOrNull()
            }

    /** Remove the unsaved entry for [tabId] (tab saved or closed normally). */
    fun clearUnsavedContent(projectRootUri: String, tabId: String) =
        prefs.edit().remove(storageKey(projectRootUri, tabId)).apply()

    /** Remove every persisted draft belonging to one project. */
    fun clearProject(projectRootUri: String) {
        prefs.edit().apply {
            prefs.all
                .filterKeys { it.startsWith(UNSAVED_PREFIX) }
                .forEach { key ->
                    val value = prefs.getString(key, null) ?: return@forEach
                    val storedProject = runCatching {
                        JSONObject(value).optString("projectRootUri")
                    }.getOrNull()
                    if (storedProject == projectRootUri) remove(key)
                }
            apply()
        }
    }

    /** Remove all unsaved entries (after recovery accepted or discarded). */
    fun clearAll() {
        prefs.edit().apply {
            prefs.all.keys
                .filter { it.startsWith(UNSAVED_PREFIX) }
                .forEach { remove(it) }
            apply()
        }
    }

    private fun storageKey(projectRootUri: String, tabId: String): String =
        "$UNSAVED_PREFIX${Base64.encodeToString(
            projectRootUri.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )}_$tabId"
}
