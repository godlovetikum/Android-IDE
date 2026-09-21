// android-ide/android/java/dev/android/ide/data/model/PreviewLayout.kt
//
// Portrait-mode stacking order for the preview panel.
// In landscape orientation the preview is always shown beside the editor.

package dev.android.ide.data.model

enum class PreviewLayout {
    PREVIEW_ABOVE,  // Preview panel above the editor (default)
    EDITOR_ABOVE,   // Editor above the preview panel
}
