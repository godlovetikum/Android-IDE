# TECH_STACK_MIGRATION.md — Android IDE

**Migration from Slint/Rust to Kotlin/Jetpack Compose. Complete as of 2026-06-12.**

This document is an archival record. The migration is finished — it will not be updated further.

---

## Summary

| From | To |
|------|----|
| Rust application (`src/`, `modules/`) | Kotlin |
| Slint UI (`ui/*.slint`) | Jetpack Compose + Material3 |
| JNI bridge (`SafBridge.java`, `EditorBridge.java`) | Direct Kotlin/Android APIs (`ContentResolver`, `WebView`) |
| `IDEActivity extends NativeActivity` | `MainActivity extends ComponentActivity` |
| `android_main()` NativeActivity entry point | `setContent {}` Compose entry point |
| cargo-ndk cross-compile pipeline | Standard `./gradlew assembleRelease` |

---

## What Was Removed

| File / Directory | Reason |
|-----------------|--------|
| `Cargo.toml`, `Cargo.lock`, `build.rs`, `.cargo/config.toml` | Entire Rust workspace |
| `src/` (`lib.rs`, `main.rs`, `ui.rs`) | Rust application source |
| `ui/` (`main.slint`, `components/*.slint`) | Slint UI files |
| `modules/` (editor, filesystem, settings, git, terminal, lsp, extensions, documentation) | All 8 Rust modules |
| `android/java/dev/androidide/IDEActivity.java` | Slint NativeActivity subclass |
| `android/java/dev/androidide/EditorBridge.java` | Rust JNI bridge |
| `android/java/dev/androidide/SafBridge.java` | Rust-initialized SAF bridge |

## What Was Preserved

| File / Directory | Why |
|-----------------|-----|
| `android/assets/editor/` | Monaco HTML/JS — JS protocol unchanged (see MD-004) |
| `scripts/fetch-monaco.sh` | Still required to download Monaco bundle at build time |
| `android/gradle/` | Gradle wrapper config |
| `android/app/src/main/res/` | Launcher icon resources |

## New Kotlin Layer

All Slint/Rust source replaced by Kotlin. Key packages:

- `ui/` — Compose screens and components (`IdeScreen`, `EditorPane`, `FileTreePanel`, `EditorTabBar`, `IdeStatusBar`, `ProjectsScreen`, `SettingsScreen`, `AppRoot`)
- `ui/theme/` — Material3 dark theme, IDE color palette, typography
- `editor/` — `EditorBridge`, `EditorMessage` (JS bridge protocol)
- `saf/` — `SafRepository` (SAF operations via `ContentResolver`)
- `viewmodel/` — `IdeViewModel`, `IdeUiState`, `EditorTab`, `FileNode`, `EditorSettings`, `SessionRepository`, `EditorSettingsRepository`

---

## Migration Decisions

### MD-001 — ComponentActivity over AppCompatActivity
`ComponentActivity` is the minimal Activity for Compose. `AppCompatActivity` is redundant when using Material3 Compose directly.

### MD-002 — AndroidViewModel for SAF Context
`IdeViewModel extends AndroidViewModel(application)` avoids leaking an Activity reference while providing the `Application` context needed for `ContentResolver`. Always use `getApplication<Application>()`, never the `application` property directly (see BUG-005).

### MD-003 — Monaco WebView remembered across recompositions
The Monaco `WebView` is wrapped in `remember {}` in `EditorPane.kt` to prevent recreation on recomposition. The `AndroidView` update lambda is a no-op; all editor state changes go through `evaluateJavascript()`.

### MD-004 — JS bridge protocol unchanged
`monaco-init.js` and `index.html` structure was preserved from the Rust implementation. `EditorBridge.kt` produces the identical JSON call format, ensuring zero regression in Monaco behaviour. The inbound/outbound protocol (sealed classes in `EditorMessage.kt`) maps directly to the JSON message types in `monaco-init.js`.

### MD-005 — Build pipeline simplification
Removed: Rust toolchain, cargo cache, NDK install, cargo-ndk, GTK/WebKit2GTK system dependencies. Retained: standard Android Gradle build. CI went from a complex multi-job Rust+Android pipeline to two standard jobs: `build-debug.yml` (push/PR, debug APK) and `build-release.yml` (manual dispatch only, signed release APK).

---

Last updated: 2026-06-12 (archival — migration complete)
