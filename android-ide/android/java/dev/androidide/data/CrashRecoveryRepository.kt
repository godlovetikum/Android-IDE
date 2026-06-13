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
//   keyed by tab ID. The content is written on every contentChanged event
//   (Monaco debounces these to 300 ms) and cleared when the tab is saved or
//   closed normally.

package dev.androidide.data

import android.content.Context
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
        tabId: String,
        documentUri: String,
        displayName: String,
        content: String,
    ) {
        val json = JSONObject().apply {
            put("tabId",       tabId)
            put("documentUri", documentUri)
            put("displayName", displayName)
            put("content",     content)
        }.toString()
        prefs.edit().putString("$UNSAVED_PREFIX$tabId", json).apply()
    }

    /** Return all persisted unsaved entries. */
    fun getUnsavedEntries(): List<RecoveryEntry> =
        prefs.all
            .filterKeys { it.startsWith(UNSAVED_PREFIX) }
            .values
            .mapNotNull { value ->
                runCatching {
                    val obj = JSONObject(value as String)
                    RecoveryEntry(
                        tabId       = obj.getString("tabId"),
                        documentUri = obj.getString("documentUri"),
                        displayName = obj.getString("displayName"),
                        content     = obj.getString("content"),
                    )
                }.getOrNull()
            }

    /** Remove the unsaved entry for [tabId] (tab saved or closed normally). */
    fun clearUnsavedContent(tabId: String) =
        prefs.edit().remove("$UNSAVED_PREFIX$tabId").apply()

    /** Remove all unsaved entries (after recovery accepted or discarded). */
    fun clearAll() {
        prefs.edit().apply {
            prefs.all.keys
                .filter { it.startsWith(UNSAVED_PREFIX) }
                .forEach { remove(it) }
            apply()
        }
    }
}
