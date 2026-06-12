# TECH_STACK_MIGRATION.md — Android IDE

Migration from Slint/Rust to Kotlin/Jetpack Compose. **Complete as of 2026-06-12.**

---

## Summary

| From | To |
|------|----|
| Rust application (`src/`, `modules/`) | Kotlin |
| Slint UI (`ui/*.slint`) | Jetpack Compose + Material3 |
| JNI bridge (SafBridge.java, EditorBridge.java) | Direct Kotlin/Android APIs |
| `IDEActivity extends NativeActivity` | `MainActivity extends ComponentActivity` |
| `android_main()` NativeActivity entry point | `setContent {}` Compose entry point |
| cargo-ndk cross-compile pipeline | Standard `./gradlew assembleRelease` |

---

## What Was Removed

| File / Directory | Reason |
|-----------------|--------|
| `Cargo.toml`, `Cargo.lock`, `build.rs`, `.cargo/config.toml` | Entire Rust workspace |
| `src/` (lib.rs, main.rs, ui.rs) | Rust application source |
| `ui/` (main.slint, components/*.slint) | Slint UI files |
| `modules/` (8 Rust modules) | editor, filesystem, settings, git, terminal, lsp, extensions, documentation |
| `android/java/dev/androidide/IDEActivity.java` | Slint NativeActivity subclass |
| `android/java/dev/androidide/EditorBridge.java` | Rust JNI bridge |
| `android/java/dev/androidide/SafBridge.java` | Rust-initialized SAF bridge |

---

## What Was Preserved

| File / Directory | Why |
|-----------------|-----|
| `android/assets/editor/` | Monaco HTML/JS — JS protocol unchanged |
| `scripts/fetch-monaco.sh` | Still required to download Monaco bundle |
| `android/gradle/` | Gradle wrapper config |
| `android/app/src/main/res/` | Launcher icon resources |

---

## New Files Created

| File | Replaces |
|------|----------|
| `MainActivity.kt` | IDEActivity.java (NativeActivity) |
| `ui/theme/Color.kt` | Slint dark theme constants |
| `ui/theme/Type.kt` | Slint typography |
| `ui/theme/Theme.kt` | Slint theme composable |
| `ui/IdeScreen.kt` | main.slint root layout |
| `ui/components/FileTreePanel.kt` | ui/components/file_tree.slint |
| `ui/components/EditorTabBar.kt` | Slint tab bar |
| `ui/components/EditorPane.kt` | IDEActivity WebView overlay |
| `ui/components/IdeStatusBar.kt` | Slint status bar |
| `editor/EditorBridge.kt` | EditorBridge.java (removed native JNI call) |
| `editor/EditorMessage.kt` | modules/editor/src/bridge.rs |
| `saf/SafRepository.kt` | SafBridge.java (removed Rust init) |
| `viewmodel/IdeViewModel.kt` | src/ui.rs state management |
| `viewmodel/model/IdeUiState.kt` | Rust UI state structs |
| `viewmodel/model/EditorTab.kt` | modules/editor/src/tab.rs |
| `viewmodel/model/FileNode.kt` | modules/filesystem/src/tree.rs |

---

## Build System Changes

**`android/build.gradle.kts`** — Added `org.jetbrains.kotlin.android` plugin (1.9.22).

**`android/app/build.gradle.kts`** — Added Kotlin plugin, Compose build feature, `kotlinOptions.jvmTarget = "17"`. Removed NDK ABI filters and `jniLibs.srcDirs`. Added Compose BOM + ViewModel + Coroutines dependencies.

**`android/gradle.properties`** — Added `kotlin.code.style=official`.

**`.github/workflows/build.yml`** — Removed Rust toolchain, cargo cache, NDK install, cargo-ndk, GTK/WebKit2GTK system deps, x86 Rust job. New 2-job workflow: `check` (lint + debug APK) and `build-release` (release APK).

---

## Migration Decisions (preserved from migration brief)

### MD-001 — ComponentActivity over AppCompatActivity
`ComponentActivity` is the minimal Activity for Compose. `AppCompatActivity` is redundant when using Material3 Compose directly.

### MD-002 — AndroidViewModel for SAF Context
`IdeViewModel extends AndroidViewModel(application)` avoids leaking an Activity reference while providing the `Application` context needed for `ContentResolver`.

### MD-003 — Monaco WebView remembered across recompositions
The Monaco `WebView` is wrapped in `remember {}` in `EditorPane.kt` to prevent recreation on recomposition. The `AndroidView` update lambda is a no-op; all editor state changes go through `evaluateJavascript()`.

### MD-004 — JS bridge protocol unchanged
`monaco-init.js` and `index.html` were not modified. `EditorBridge.kt` produces the identical JS call format that the Rust bridge produced, ensuring zero regression in Monaco behaviour.

Last updated: 2026-06-12
