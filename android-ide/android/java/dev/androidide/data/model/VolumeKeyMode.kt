// android-ide/android/java/dev/androidide/data/model/VolumeKeyMode.kt
//
// Controls how hardware volume keys behave when the editor has focus.

package dev.androidide.data.model

enum class VolumeKeyMode {
    /** Volume Up → cursor left, Volume Down → cursor right. */
    HORIZONTAL,
    /** Volume Up → cursor up, Volume Down → cursor down. */
    VERTICAL,
    /** Volume keys are not intercepted; system audio volume changes normally. */
    DISABLED,
}
