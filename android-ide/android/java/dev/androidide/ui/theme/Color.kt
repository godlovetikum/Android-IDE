// android-ide/android/java/dev/androidide/ui/theme/Color.kt
//
// IDE color constants — VS Code-inspired dark palette.
//
// These colors match the Monaco editor's androidide-dark theme in monaco-init.js
// so the Compose chrome (app bar, sidebar, tabs, status bar) is visually unified
// with the Monaco editor surface.

package dev.androidide.ui.theme

import androidx.compose.ui.graphics.Color

// ── Background layers ────────────────────────────────────────────────────────

/** Main editor background — matches monaco editor.background */
val IdeBackground = Color(0xFF1E1E1E)

/** Panel/surface background (sidebar, app bar, tab bar) */
val IdeSurface = Color(0xFF252526)

/** Elevated surface — dropdown backgrounds, tooltip containers */
val IdeSurfaceVariant = Color(0xFF2D2D2D)

/** Active tab / selected row highlight */
val IdeActiveHighlight = Color(0xFF37373D)

// ── Borders and separators ────────────────────────────────────────────────────

/** 1dp dividers between panels */
val IdeSeparator = Color(0xFF3C3C3C)

// ── Text ──────────────────────────────────────────────────────────────────────

/** Primary text — matches monaco editor.foreground */
val IdeTextPrimary = Color(0xFFD4D4D4)

/** Secondary text — line numbers, inactive labels */
val IdeTextSecondary = Color(0xFF858585)

/** Disabled text */
val IdeTextDisabled = Color(0xFF5A5A5A)

// ── Accent / brand ─────────────────────────────────────────────────────────

/** Primary accent — VS Code blue (focus border, active tab indicator) */
val IdeAccent = Color(0xFF007ACC)

/** Light accent — keyword color in Monaco, used for secondary actions */
val IdeAccentLight = Color(0xFF569CD6)

// ── Semantic ──────────────────────────────────────────────────────────────────

/** Error — VS Code error red */
val IdeError = Color(0xFFF48771)

/** Warning */
val IdeWarning = Color(0xFFCCA700)

/** Success / modified file indicator */
val IdeModified = Color(0xFFE2C08D)

// ── Material3 ColorScheme seeds ───────────────────────────────────────────────
// Used by Theme.kt to build the darkColorScheme().
// Named to match Material3 role vocabulary.

val Md3Primary = IdeAccent
val Md3OnPrimary = Color(0xFFFFFFFF)
val Md3PrimaryContainer = Color(0xFF004A7A)
val Md3OnPrimaryContainer = Color(0xFFCCE5FF)

val Md3Secondary = IdeAccentLight
val Md3OnSecondary = Color(0xFF1A1A1A)
val Md3SecondaryContainer = Color(0xFF263850)
val Md3OnSecondaryContainer = Color(0xFFBDD6EF)

val Md3Background = IdeBackground
val Md3OnBackground = IdeTextPrimary

val Md3Surface = IdeSurface
val Md3OnSurface = IdeTextPrimary
val Md3SurfaceVariant = IdeSurfaceVariant
val Md3OnSurfaceVariant = IdeTextSecondary

val Md3Outline = IdeSeparator
val Md3OutlineVariant = Color(0xFF2A2A2A)

val Md3Error = IdeError
val Md3OnError = Color(0xFF1A0A00)
val Md3ErrorContainer = Color(0xFF5C1D0D)
val Md3OnErrorContainer = Color(0xFFFFDAD6)
