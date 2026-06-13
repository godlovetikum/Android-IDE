// android-ide/android/java/dev/androidide/data/model/PreviewLayout.kt
//
// Portrait-mode stacking order for the preview panel.
// In landscape orientation the preview is always shown beside the editor.

package dev.androidide.data.model

enum class PreviewLayout {
    PREVIEW_ABOVE,  // Preview panel above the editor (default)
    EDITOR_ABOVE,   // Editor above the preview panel
}
