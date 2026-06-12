// android-ide/android/java/dev/androidide/ui/theme/Color.kt
//
// IDE color constants and theming support.
//
// The app supports DARK (VS Code dark+), LIGHT (VS Code light+), and SYSTEM themes.
// All composables obtain colours through LocalIdeColors.current rather than
// referencing the top-level constants directly.

package dev.androidide.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Dark palette ─────────────────────────────────────────────────────────────
// Matches Monaco's androidide-dark theme so the Compose chrome is visually
// unified with the editor surface.

private val Dark_Background     = Color(0xFF1E1E1E)
private val Dark_Surface        = Color(0xFF252526)
private val Dark_SurfaceVariant = Color(0xFF2D2D2D)
private val Dark_ActiveHighlight= Color(0xFF37373D)
private val Dark_Separator      = Color(0xFF3C3C3C)
private val Dark_TextPrimary    = Color(0xFFD4D4D4)
private val Dark_TextSecondary  = Color(0xFF858585)
private val Dark_TextDisabled   = Color(0xFF5A5A5A)
private val Dark_Accent         = Color(0xFF007ACC)
private val Dark_AccentLight    = Color(0xFF569CD6)
private val Dark_Error          = Color(0xFFF48771)
private val Dark_Warning        = Color(0xFFCCA700)
private val Dark_Modified       = Color(0xFFE2C08D)

// ── Light palette ─────────────────────────────────────────────────────────────
// Matches Monaco's androidide-light theme (VS Code Light+ inspired).

private val Light_Background     = Color(0xFFFFFFFF)
private val Light_Surface        = Color(0xFFF3F3F3)
private val Light_SurfaceVariant = Color(0xFFEBEBEB)
private val Light_ActiveHighlight= Color(0xFFE8E8E8)
private val Light_Separator      = Color(0xFFE1E1E1)
private val Light_TextPrimary    = Color(0xFF1F1F1F)
private val Light_TextSecondary  = Color(0xFF717171)
private val Light_TextDisabled   = Color(0xFFA0A0A0)
private val Light_Accent         = Color(0xFF0078D4)
private val Light_AccentLight    = Color(0xFF005FB8)
private val Light_Error          = Color(0xFFD73A49)
private val Light_Warning        = Color(0xFFDCA11D)
private val Light_Modified       = Color(0xFF895503)

// ── IdeColors data class ──────────────────────────────────────────────────────
// All composables must use LocalIdeColors.current.<field> rather than
// the file-level constants below, so theme switching works without restart.

data class IdeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val activeHighlight: Color,
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val accent: Color,
    val accentLight: Color,
    val error: Color,
    val warning: Color,
    val modified: Color,
    /** Always white — used for text on top of accent-coloured backgrounds. */
    val onAccent: Color = Color.White,
)

val darkIdeColors = IdeColors(
    background      = Dark_Background,
    surface         = Dark_Surface,
    surfaceVariant  = Dark_SurfaceVariant,
    activeHighlight = Dark_ActiveHighlight,
    separator       = Dark_Separator,
    textPrimary     = Dark_TextPrimary,
    textSecondary   = Dark_TextSecondary,
    textDisabled    = Dark_TextDisabled,
    accent          = Dark_Accent,
    accentLight     = Dark_AccentLight,
    error           = Dark_Error,
    warning         = Dark_Warning,
    modified        = Dark_Modified,
)

val lightIdeColors = IdeColors(
    background      = Light_Background,
    surface         = Light_Surface,
    surfaceVariant  = Light_SurfaceVariant,
    activeHighlight = Light_ActiveHighlight,
    separator       = Light_Separator,
    textPrimary     = Light_TextPrimary,
    textSecondary   = Light_TextSecondary,
    textDisabled    = Light_TextDisabled,
    accent          = Light_Accent,
    accentLight     = Light_AccentLight,
    error           = Light_Error,
    warning         = Light_Warning,
    modified        = Light_Modified,
)

/** Provides [IdeColors] to all descendant composables. */
val LocalIdeColors = staticCompositionLocalOf { darkIdeColors }

// ── Legacy top-level constants ────────────────────────────────────────────────
// Kept for the Material3 dark color scheme in Theme.kt.
// Do NOT use these in composables — use LocalIdeColors.current instead.

internal val IdeBackground     = Dark_Background
internal val IdeSurface        = Dark_Surface
internal val IdeSurfaceVariant = Dark_SurfaceVariant
internal val IdeSeparator      = Dark_Separator
internal val IdeTextPrimary    = Dark_TextPrimary
internal val IdeTextSecondary  = Dark_TextSecondary
internal val IdeAccent         = Dark_Accent
internal val IdeAccentLight    = Dark_AccentLight
internal val IdeError          = Dark_Error

// Material3 role seeds (dark scheme)
internal val Md3Primary              = IdeAccent
internal val Md3OnPrimary            = Color(0xFFFFFFFF)
internal val Md3PrimaryContainer     = Color(0xFF004A7A)
internal val Md3OnPrimaryContainer   = Color(0xFFCCE5FF)
internal val Md3Secondary            = IdeAccentLight
internal val Md3OnSecondary          = Color(0xFF1A1A1A)
internal val Md3SecondaryContainer   = Color(0xFF263850)
internal val Md3OnSecondaryContainer = Color(0xFFBDD6EF)
internal val Md3Background           = IdeBackground
internal val Md3OnBackground         = IdeTextPrimary
internal val Md3Surface              = IdeSurface
internal val Md3OnSurface            = IdeTextPrimary
internal val Md3SurfaceVariant       = IdeSurfaceVariant
internal val Md3OnSurfaceVariant     = IdeTextSecondary
internal val Md3Outline              = IdeSeparator
internal val Md3OutlineVariant       = Color(0xFF2A2A2A)
internal val Md3Error                = IdeError
internal val Md3OnError              = Color(0xFF1A0A00)
internal val Md3ErrorContainer       = Color(0xFF5C1D0D)
internal val Md3OnErrorContainer     = Color(0xFFFFDAD6)

// Material3 role seeds (light scheme)
internal val Md3LightPrimary              = Light_Accent
internal val Md3LightOnPrimary            = Color(0xFFFFFFFF)
internal val Md3LightPrimaryContainer     = Color(0xFFCFE4FF)
internal val Md3LightOnPrimaryContainer   = Color(0xFF001E33)
internal val Md3LightSecondary            = Light_AccentLight
internal val Md3LightOnSecondary          = Color(0xFFFFFFFF)
internal val Md3LightSecondaryContainer   = Color(0xFFCBE5FF)
internal val Md3LightOnSecondaryContainer = Color(0xFF001D33)
internal val Md3LightBackground           = Light_Background
internal val Md3LightOnBackground         = Light_TextPrimary
internal val Md3LightSurface              = Light_Surface
internal val Md3LightOnSurface            = Light_TextPrimary
internal val Md3LightSurfaceVariant       = Light_SurfaceVariant
internal val Md3LightOnSurfaceVariant     = Light_TextSecondary
internal val Md3LightOutline              = Light_Separator
internal val Md3LightOutlineVariant       = Color(0xFFCCCCCC)
internal val Md3LightError                = Light_Error
internal val Md3LightOnError              = Color(0xFFFFFFFF)
internal val Md3LightErrorContainer       = Color(0xFFFFDAD6)
internal val Md3LightOnErrorContainer     = Color(0xFF410002)
