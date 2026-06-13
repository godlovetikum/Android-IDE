// android-ide/android/java/dev/androidide/viewmodel/model/EditorTab.kt
//
// State for a single open editor tab.

package dev.androidide.viewmodel.model

import java.util.UUID

/**
 * [id]             Stable tab identifier — UUID v4.
 * [documentUri]    SAF document URI. For blank (unsaved) tabs: "blank://new/<uuid>".
 * [displayName]    Filename shown in the tab label.
 * [language]       Monaco language ID (e.g. "kotlin", "java", "plaintext").
 * [content]        File content; null while the file is being read from SAF.
 * [isDirty]        True if the editor has unsaved changes.
 * [isActive]       True if this tab is currently visible in the editor.
 * [isSaving]       True while an async write is in flight; used to show a saving indicator.
 * [isBlank]        True for a "new blank" tab that has never been saved to a real file.
 *                  Blank tabs have a synthetic documentUri and do not correspond to a
 *                  SAF document until the user performs "Save As".
 */
data class EditorTab(
    val id: String = UUID.randomUUID().toString(),
    val documentUri: String,
    val displayName: String,
    val language: String,
    val content: String? = null,
    val isDirty: Boolean = false,
    val isActive: Boolean = false,
    val isSaving: Boolean = false,
    val isBlank: Boolean = false,
)
