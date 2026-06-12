// android-ide/android/java/dev/androidide/viewmodel/model/AppScreen.kt
//
// Top-level navigation destinations for the IDE application.

package dev.androidide.viewmodel.model

enum class AppScreen {
    /** Recent projects list + open-project action. */
    PROJECTS,

    /** Monaco editor with file tree sidebar. */
    EDITOR,

    /** Theme, preferences, and coming-soon feature previews. */
    SETTINGS,
}
