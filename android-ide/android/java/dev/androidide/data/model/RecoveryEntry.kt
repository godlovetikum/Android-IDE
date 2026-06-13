// android-ide/android/java/dev/androidide/data/model/RecoveryEntry.kt
//
// A single unsaved-content entry from a previous crashed session.
// Populated by CrashRecoveryRepository and surfaced in IdeUiState so the
// UI can offer restoration without exposing the repository type.

package dev.androidide.data.model

data class RecoveryEntry(
    val tabId: String,
    val documentUri: String,
    val displayName: String,
    val content: String,
)
