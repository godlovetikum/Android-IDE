// android-ide/android/java/dev/androidide/viewmodel/model/EditorTab.kt
//
// Represents a single open file tab in the editor.
//
// Migration note (2026-06-12):
//   Replaces the Rust EditorTab struct in modules/editor/src/tab.rs.
//   UUID generation uses java.util.UUID (no Rust uuid crate needed).

package dev.androidide.viewmodel.model

import java.util.UUID

/**
 * State for a single open editor tab.
 *
 * [id]           Stable tab identifier — UUID v4, unique per tab instance.
 * [documentUri]  SAF document URI of the open file. Used for all I/O operations.
 * [displayName]  Filename shown in the tab label (e.g. "Main.kt").
 * [language]     Monaco language ID (e.g. "kotlin", "java", "plaintext").
 * [content]      File content; null while the file is being read from SAF.
 * [isDirty]      True if the editor has unsaved changes.
 * [isActive]     True if this tab is currently visible in the editor.
 */
data class EditorTab(
    val id: String = UUID.randomUUID().toString(),
    val documentUri: String,
    val displayName: String,
    val language: String,
    val content: String? = null,
    val isDirty: Boolean = false,
    val isActive: Boolean = false,
)
