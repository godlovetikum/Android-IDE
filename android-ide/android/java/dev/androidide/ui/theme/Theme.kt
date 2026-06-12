// android-ide/android/java/dev/androidide/ui/theme/Theme.kt
//
// Material3 dark color scheme for the Android IDE.
// Always dark — an IDE defaults to a dark theme.

package dev.androidide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IdeColorScheme = darkColorScheme(
    primary              = Md3Primary,
    onPrimary            = Md3OnPrimary,
    primaryContainer     = Md3PrimaryContainer,
    onPrimaryContainer   = Md3OnPrimaryContainer,

    secondary            = Md3Secondary,
    onSecondary          = Md3OnSecondary,
    secondaryContainer   = Md3SecondaryContainer,
    onSecondaryContainer = Md3OnSecondaryContainer,

    background           = Md3Background,
    onBackground         = Md3OnBackground,

    surface              = Md3Surface,
    onSurface            = Md3OnSurface,
    surfaceVariant       = Md3SurfaceVariant,
    onSurfaceVariant     = Md3OnSurfaceVariant,

    outline              = Md3Outline,
    outlineVariant       = Md3OutlineVariant,

    error                = Md3Error,
    onError              = Md3OnError,
    errorContainer       = Md3ErrorContainer,
    onErrorContainer     = Md3OnErrorContainer,
)

@Composable
fun AndroidIDETheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IdeColorScheme,
        typography  = IdeTypography,
        content     = content,
    )
}
