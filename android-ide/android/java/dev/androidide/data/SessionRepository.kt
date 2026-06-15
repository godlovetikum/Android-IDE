// android-ide/android/java/dev/androidide/data/SessionRepository.kt
//
// Persists the IDE working session across process deaths so the user can
// resume immediately without re-navigating to their project.
//
// Stored in SharedPreferences — lightweight enough for the session envelope.
// Tab content is NOT stored (re-read from SAF on restore); only the URIs
// and the active-tab marker are persisted.
//
// Workspace state (open tabs, active tab, cursor and scroll positions) is
// SCOPED PER PROJECT so that:
//   - Project A always restores Project A's tabs and positions.
//   - Project B always restores Project B's tabs and positions.
//   - No tab leakage between projects occurs.
//
// Project-scoped keys use a compact hex representation of the project URI's
// hashCode. Hash collisions are astronomically unlikely for the ≤ 20 projects
// a typical user keeps open.
//
// Cursor positions  : newline-separated "uri|line|column" entries
// Scroll positions  : newline-separated "uri|scrollTop" entries

package dev.androidide.data

import android.content.Context

class SessionRepository(context: Context) {

    companion object {
        private const val PREFS              = "ide_session"
        private const val KEY_PROJECT_URI    = "project_uri"
        private const val KEY_SCREEN         = "last_screen"     // AppScreen name

        // Per-project tab keys — suffixed with a hex hash of the project URI.
        private const val KEY_PREFIX_TABS    = "tabs_"           // + projectHash → newline-separated URIs
        private const val KEY_PREFIX_ACTIVE  = "active_"         // + projectHash → active URI
        private const val KEY_PREFIX_CURSORS = "cursors_"        // + projectHash → newline-separated "uri|line|col"
        private const val KEY_PREFIX_SCROLLS = "scrolls_"        // + projectHash → newline-separated "uri|scrollTop"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Global (non-project-scoped) ────────────────────────────────────────

    fun save(
        projectUri: String?,
        openTabUris: List<String>,
        activeTabUri: String?,
        screenName: String,
        cursorPositions: Map<String, Pair<Int, Int>> = emptyMap(),
        scrollPositions: Map<String, Int> = emptyMap(),
    ) {
        val editor = prefs.edit()
            .putString(KEY_PROJECT_URI, projectUri)
            .putString(KEY_SCREEN, screenName)
        if (projectUri != null) {
            val hash = projectHash(projectUri)
            editor
                .putString(KEY_PREFIX_TABS    + hash, openTabUris.filter { it.isNotBlank() }.joinToString("\n"))
                .putString(KEY_PREFIX_ACTIVE  + hash, activeTabUri)
                .putString(KEY_PREFIX_CURSORS + hash, serializeCursors(cursorPositions))
                .putString(KEY_PREFIX_SCROLLS + hash, serializeScrolls(scrollPositions))
        }
        editor.apply()
    }

    fun getProjectUri():  String? = prefs.getString(KEY_PROJECT_URI, null)
    fun getScreenName():  String? = prefs.getString(KEY_SCREEN, null)

    // ── Per-project tab state ──────────────────────────────────────────────

    fun getOpenTabUrisForProject(projectUri: String): List<String> {
        val hash = projectHash(projectUri)
        return prefs.getString(KEY_PREFIX_TABS + hash, "")
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun getActiveTabUriForProject(projectUri: String): String? {
        val hash = projectHash(projectUri)
        return prefs.getString(KEY_PREFIX_ACTIVE + hash, null)
    }

    fun getCursorPositionsForProject(projectUri: String): Map<String, Pair<Int, Int>> {
        val hash = projectHash(projectUri)
        val raw  = prefs.getString(KEY_PREFIX_CURSORS + hash, "") ?: return emptyMap()
        return raw.split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size == 3) {
                    val uri = parts[0]
                    val lineNum = parts[1].toIntOrNull() ?: return@mapNotNull null
                    val col     = parts[2].toIntOrNull() ?: return@mapNotNull null
                    uri to Pair(lineNum, col)
                } else null
            }
            .toMap()
    }

    fun getScrollPositionsForProject(projectUri: String): Map<String, Int> {
        val hash = projectHash(projectUri)
        val raw  = prefs.getString(KEY_PREFIX_SCROLLS + hash, "") ?: return emptyMap()
        return raw.split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val idx = line.lastIndexOf('|')
                if (idx < 0) return@mapNotNull null
                val uri    = line.substring(0, idx)
                val scroll = line.substring(idx + 1).toIntOrNull() ?: return@mapNotNull null
                uri to scroll
            }
            .toMap()
    }

    /** Save tabs for a specific project without changing the current project pointer. */
    fun saveTabsForProject(
        projectUri: String,
        openTabUris: List<String>,
        activeTabUri: String?,
        cursorPositions: Map<String, Pair<Int, Int>> = emptyMap(),
        scrollPositions: Map<String, Int> = emptyMap(),
    ) {
        val hash = projectHash(projectUri)
        prefs.edit()
            .putString(KEY_PREFIX_TABS    + hash, openTabUris.filter { it.isNotBlank() }.joinToString("\n"))
            .putString(KEY_PREFIX_ACTIVE  + hash, activeTabUri)
            .putString(KEY_PREFIX_CURSORS + hash, serializeCursors(cursorPositions))
            .putString(KEY_PREFIX_SCROLLS + hash, serializeScrolls(scrollPositions))
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    // ── Serialization helpers ───────────────────────────────────────────────

    private fun serializeCursors(positions: Map<String, Pair<Int, Int>>): String =
        positions.entries.joinToString("\n") { (uri, pos) -> "$uri|${pos.first}|${pos.second}" }

    private fun serializeScrolls(positions: Map<String, Int>): String =
        positions.entries.joinToString("\n") { (uri, scroll) -> "$uri|$scroll" }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun projectHash(projectUri: String): String =
        projectUri.hashCode().toUInt().toString(16)
}
