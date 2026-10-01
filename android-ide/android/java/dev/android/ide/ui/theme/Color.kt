// android-ide/android/java/dev/android/ide/ui/theme/Color.kt
//
// Semantic application color tokens. Keep Compose chrome neutral; reserve accent,
// warning, success, and error colors for the states and actions they represent.

package dev.android.ide.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class IdeColors(
    /** Root screen canvas; aligned with the Monaco editor canvas. */
    val background: Color,
    /** Default component surface, such as cards and app bars. */
    val surface: Color,
    /** Lower-emphasis containers and controls. */
    val surfaceVariant: Color,
    /** Quiet selected-row fill. */
    val activeHighlight: Color,
    /** Low-emphasis borders and dividers. */
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    /** Brand role for primary actions and active navigation. */
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** Lower-emphasis supporting accent. */
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    /** Reserve error roles for actual failures and destructive actions. */
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    /** IDE-specific attention state, distinct from failure. */
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** Positive operation state. */
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** IDE-specific document state and stable terminal canvas. */
    val modified: Color,
    val terminalBackground: Color,
    /** Inverse roles used by transient surfaces and inverse components. */
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
)

val darkIdeColors = IdeColors(
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF252526),
    surfaceVariant = Color(0xFF2D2D30),
    activeHighlight = Color(0xFF353B44),
    separator = Color(0xFF414141),
    textPrimary = Color(0xFFE6E6E6),
    textSecondary = Color(0xFFB1B1B1),
    textDisabled = Color(0xFF858585),
    primary = Color(0xFF55A9E2),
    onPrimary = Color(0xFF061522),
    primaryContainer = Color(0xFF173B5A),
    onPrimaryContainer = Color(0xFFD8EDFF),
    secondary = Color(0xFF67C4B5),
    onSecondary = Color(0xFF082521),
    secondaryContainer = Color(0xFF20433E),
    onSecondaryContainer = Color(0xFFC9EEE7),
    tertiary = Color(0xFFE0B45E),
    onTertiary = Color(0xFF2A1C00),
    tertiaryContainer = Color(0xFF493710),
    onTertiaryContainer = Color(0xFFFFE8B2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF5F1010),
    errorContainer = Color(0xFF5C1D0D),
    onErrorContainer = Color(0xFFFFDAD6),
    warning = Color(0xFFF2C66D),
    warningContainer = Color(0xFF3A3020),
    onWarningContainer = Color(0xFFFFE6A6),
    success = Color(0xFF7BD9A9),
    successContainer = Color(0xFF18392B),
    onSuccessContainer = Color(0xFFC2F0D1),
    modified = Color(0xFFE2C08D),
    terminalBackground = Color(0xFF101216),
    inverseSurface = Color(0xFFF1F2F4),
    inverseOnSurface = Color(0xFF25272B),
    inversePrimary = Color(0xFF145B93),
)

val lightIdeColors = IdeColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F7F9),
    surfaceVariant = Color(0xFFECEFF3),
    activeHighlight = Color(0xFFE4EEFA),
    separator = Color(0xFFD6DCE5),
    textPrimary = Color(0xFF1F2328),
    textSecondary = Color(0xFF59636E),
    textDisabled = Color(0xFF7D8793),
    primary = Color(0xFF0066B3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E8F8),
    onPrimaryContainer = Color(0xFF102F4B),
    secondary = Color(0xFF166B63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5EEE8),
    onSecondaryContainer = Color(0xFF103E38),
    tertiary = Color(0xFF8B5E0A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E7C6),
    onTertiaryContainer = Color(0xFF4E3800),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    warning = Color(0xFF795900),
    warningContainer = Color(0xFFFFF1CC),
    onWarningContainer = Color(0xFF4D3800),
    success = Color(0xFF216E45),
    successContainer = Color(0xFFDCEFE3),
    onSuccessContainer = Color(0xFF173B29),
    modified = Color(0xFF895503),
    terminalBackground = Color(0xFF101216),
    inverseSurface = Color(0xFF313840),
    inverseOnSurface = Color(0xFFEEF2F6),
    inversePrimary = Color(0xFF99C5EE),
)

/** Provides theme-aware IDE-specific tokens to descendants. */
val LocalIdeColors = staticCompositionLocalOf { darkIdeColors }
