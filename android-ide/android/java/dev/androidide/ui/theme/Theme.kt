// android-ide/android/java/dev/androidide/ui/theme/Theme.kt
//
// Material3 theme wrapper that supports Dark, Light, and System themes.
// Theme changes apply without restarting the app — they are driven by
// the AppTheme value in IdeUiState, collected in AppRoot and passed here.

package dev.androidide.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.androidide.data.model.AppTheme

private val IdeDarkColorScheme = darkColorScheme(
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

private val IdeLightColorScheme = lightColorScheme(
    primary              = Md3LightPrimary,
    onPrimary            = Md3LightOnPrimary,
    primaryContainer     = Md3LightPrimaryContainer,
    onPrimaryContainer   = Md3LightOnPrimaryContainer,
    secondary            = Md3LightSecondary,
    onSecondary          = Md3LightOnSecondary,
    secondaryContainer   = Md3LightSecondaryContainer,
    onSecondaryContainer = Md3LightOnSecondaryContainer,
    background           = Md3LightBackground,
    onBackground         = Md3LightOnBackground,
    surface              = Md3LightSurface,
    onSurface            = Md3LightOnSurface,
    surfaceVariant       = Md3LightSurfaceVariant,
    onSurfaceVariant     = Md3LightOnSurfaceVariant,
    outline              = Md3LightOutline,
    outlineVariant       = Md3LightOutlineVariant,
    error                = Md3LightError,
    onError              = Md3LightOnError,
    errorContainer       = Md3LightErrorContainer,
    onErrorContainer     = Md3LightOnErrorContainer,
)

/**
 * Root theme composable.
 *
 * @param appTheme The user's chosen theme. Defaults to DARK.
 */
@Composable
fun AndroidIDETheme(
    appTheme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit,
) {
    val isDark = when (appTheme) {
        AppTheme.DARK   -> true
        AppTheme.LIGHT  -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) IdeDarkColorScheme else IdeLightColorScheme
    val ideColors   = if (isDark) darkIdeColors      else lightIdeColors

    CompositionLocalProvider(LocalIdeColors provides ideColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = IdeTypography,
            content     = content,
        )
    }
}
