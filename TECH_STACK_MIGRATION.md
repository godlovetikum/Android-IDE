# TECH_STACK_MIGRATION.md — Android IDE

Migration from Slint/Rust to Kotlin/Jetpack Compose.

**Migration start:** 2026-06-12  
**Architect:** migration brief v1

---

## Phase Status

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 1 | Assessment — inventory, risk analysis, roadmap | ✅ Complete |
| Phase 2 | Build System Migration — Gradle Kotlin DSL | ✅ Complete |
| Phase 3 | UI Migration — Jetpack Compose application shell | ✅ Complete |
| Phase 4 | Editor Migration — Monaco WebView + SAF bridge | ✅ Complete |
| Phase 5 | Verification — remove obsolete deps, update docs | ✅ Complete |

---

## Phase 1: Assessment

### Migration target

| From | To |
|------|----|
| Rust (`src/`, `modules/`) | Kotlin |
| Slint UI (`ui/*.slint`) | Jetpack Compose |
| JNI bridge (saf.rs, webview.rs) | Direct Kotlin/Android APIs |
| `android_main` NativeActivity entry point | `ComponentActivity` + `setContent {}` |
| `IDEActivity extends NativeActivity` | `MainActivity extends ComponentActivity` |
| `SafBridge.java` (Rust-init pattern) | `SafRepository.kt` (direct ContentResolver) |
| `EditorBridge.java` (native JNI call) | `EditorBridge.kt` (Kotlin callback) |
| cargo-ndk cross-compile pipeline | Standard `./gradlew assembleRelease` |

### Files inventoried

**Build configuration read:**
- `android-ide/Cargo.toml` — Rust workspace + package + Slint/JNI deps
- `android-ide/build.rs` — slint_build compilation step
- `android-ide/.cargo/config.toml` — NDK rustflags
- `android-ide/android/app/build.gradle.kts` — Android Gradle (had jniLibs, NDK ABI filters, no Kotlin/Compose)
- `android-ide/android/build.gradle.kts` — root Gradle (AGP only, no Kotlin plugin)
- `android-ide/android/settings.gradle.kts` — single `:app` module
- `android-ide/android/gradle.properties` — Gradle heap settings
- `.github/workflows/build.yml` — 3 jobs, all using Rust toolchain + cargo-ndk

**Android entry points read:**
- `android/app/src/main/AndroidManifest.xml` — `IDEActivity` (NativeActivity subclass)
- `android/java/dev/androidide/IDEActivity.java` — Slint NativeActivity + WebView overlay
- `android/java/dev/androidide/EditorBridge.java` — `native void nativeOnEditorMessage()` JNI call
- `android/java/dev/androidide/SafBridge.java` — Rust-initialized ContentResolver bridge

**Slint/Rust files inventoried (not read in full):**
- `ui/main.slint`, `ui/components/dialogs.slint`, `ui/components/file_tree.slint`
- `src/lib.rs`, `src/main.rs`, `src/ui.rs`
- `modules/editor/`, `modules/filesystem/`, `modules/settings/`, and 5 other modules

**Architecture documentation read:**
- `README.md` (PROJECT_PLAN.md) — architecture decisions AD-001 through AD-004
- `DEBUG_LOG.md` — BUG-001 through BUG-I migration history
- `STATUS_TRACKER.md` — phase progress

### Dependency inventory

#### Removed Rust/Slint dependencies

| Dependency | Where | Reason removed |
|------------|-------|----------------|
| `slint = "1.16.1"` (desktop + Android) | `Cargo.toml` | Entire UI framework replaced by Compose |
| `slint-build = "1.16.1"` | `Cargo.toml` `[build-dependencies]` | build.rs compilation no longer needed |
| `android-activity = "0.6"` | `Cargo.toml` | NativeActivity entry point no longer used |
| `jni = "0.21"` | `Cargo.toml` | No JNI bridge — replaced by direct Kotlin APIs |
| `android_logger = "0.14"` | `Cargo.toml` | Rust logging; replaced by Android `Log.*` |
| `tracing`, `tracing-subscriber` | `Cargo.toml` | Rust-specific; replaced by `android.util.Log` |
| `cargo-ndk` | CI `build.yml` | Cross-compile tool; no Rust to compile |
| NDK `26.3.11579264` | CI `build.yml` | NDK not needed for pure Kotlin build |

#### Preserved / migrated dependencies

| Old | New | Note |
|-----|-----|------|
| SAF `DocumentsContract` via `SafBridge.java` | `SafRepository.kt` | Same Android API, Kotlin coroutines |
| Monaco WebView `EditorBridge.java` | `EditorBridge.kt` | Same `@JavascriptInterface`; removes `native` JNI call |
| `android/assets/editor/` (Monaco HTML/JS) | Unchanged | JS protocol preserved exactly |
| `scripts/fetch-monaco.sh` | Unchanged | Still required; CI step preserved |
| Gradle 8.7 + AGP 8.3.2 | Same | No Gradle version change needed |

#### New Kotlin/Compose dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| `org.jetbrains.kotlin.android` plugin | 1.9.22 | Kotlin Android support |
| `androidx.compose:compose-bom` | 2024.02.00 | Compose BOM — pins all Compose library versions |
| `androidx.compose.ui:ui` | BOM | Compose core UI |
| `androidx.compose.material3:material3` | BOM | Material Design 3 components |
| `androidx.compose.foundation:foundation` | BOM | Layout + gesture primitives |
| `androidx.activity:activity-compose` | 1.8.2 | `setContent {}` + `rememberLauncherForActivityResult` |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.7.0 | `viewModel()` Compose integration |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.7.0 | `collectAsState()` |
| `kotlinx-coroutines-android` | 1.7.3 | `viewModelScope`, suspend SAF calls |

### Risk analysis

| Risk | Severity | Mitigation |
|------|----------|------------|
| Monaco JS protocol must not change | High | Bridge protocol read from `monaco-init.js` and replicated exactly in `EditorBridge.kt`. The JS files are NOT modified. |
| SAF URI semantics must be preserved | High | `SafRepository.kt` is a direct port of `SafBridge.java` — same API surface, same DocumentsContract calls |
| Compose WebView `remember` must survive recomposition | Medium | `editorWebView` wrapped in `remember {}` in `EditorPane`; `AndroidView` update lambda is no-op |
| Gradle `sourceSets` path `../../java` must resolve Kotlin | Low | Kotlin compiler picks up `.kt` in `java.srcDirs()` by convention |
| Wide/narrow adaptive layout must match Slint 600dp threshold | Low | `LocalConfiguration.current.screenWidthDp < 600` mirrors `NARROW_THRESHOLD_DP = 600` |

### Architecture decisions preserved

From the existing docs (AD-001 through AD-004):

- **AD-001**: SAF-based file access — preserved. `SafRepository.kt` uses identical `DocumentsContract` calls.
- **AD-002**: Monaco Editor as editor component — preserved. Same `file:///android_asset/editor/index.html`.
- **AD-003**: The Slint/android-activity version pin — **removed** (Slint no longer used).
- **AD-004**: IDEActivity NativeActivity overlay — **removed** (NativeActivity replaced by ComponentActivity; WebView is now a native Compose `AndroidView`).

New architecture decision recorded below.

---

## Phase 2: Build System Migration

### Files modified

**`android/build.gradle.kts`** — Added `org.jetbrains.kotlin.android` plugin declaration.

**`android/app/build.gradle.kts`** — Added Kotlin plugin, Compose build feature, `kotlinOptions.jvmTarget`, removed NDK ABI filters and `jniLibs.srcDirs`, added Compose + ViewModel + Coroutines dependencies.

**`android/gradle.properties`** — Added `kotlin.code.style=official`, removed Slint-specific comment.

**`.github/workflows/build.yml`** — Removed 3 Rust-dependent jobs (check, build-android, build-android-x86). New 2-job workflow: `check` (lint + debug APK) and `build-release` (release APK). Removed: Rust toolchain install, cargo cache, NDK install, cargo-ndk install, `cargo ndk build` step.

---

## Phase 3: UI Migration

### Slint → Compose component mapping

| Slint component | Compose replacement |
|-----------------|---------------------|
| `main.slint` root layout | `IdeScreen.kt` |
| `AppBar {}` | `IdeTopBar` composable using `TopAppBar` |
| `SideBar {}` (permanent/narrow) | `FileTreePanel.kt` in `PermanentDrawer` / `ModalNavigationDrawer` |
| `TabBar {}` | `EditorTabBar.kt` |
| `EditorArea {}` (WebView placeholder) | `EditorPane.kt` (Monaco `WebView` via `AndroidView`) |
| Preview panel (IDEActivity `mPreviewWebView`) | Second `AndroidView` WebView in `EditorPane.kt` |
| `StatusBar {}` | `IdeStatusBar.kt` |
| Slint dark theme (`#1e1e1e`, `#007ACC`) | `ui/theme/Color.kt` + Material3 `ColorScheme` |

### New source files created

| File | Description |
|------|-------------|
| `MainActivity.kt` | `ComponentActivity` — IDE entry point |
| `ui/theme/Color.kt` | IDE color constants + Material3 `ColorScheme` |
| `ui/theme/Type.kt` | Typography (JetBrains Mono for editor, system for UI) |
| `ui/theme/Theme.kt` | `AndroidIDETheme` composable |
| `ui/IdeScreen.kt` | Root IDE screen — adaptive layout (wide/narrow) |
| `ui/components/FileTreePanel.kt` | SAF file tree sidebar (`LazyColumn`) |
| `ui/components/EditorTabBar.kt` | Open-file tab row |
| `ui/components/EditorPane.kt` | Monaco WebView + optional preview WebView |
| `ui/components/IdeStatusBar.kt` | Cursor position + file info status bar |

---

## Phase 4: Editor Migration

### Kotlin source files replacing Java/Rust

| Removed | Replacement | Change summary |
|---------|-------------|----------------|
| `IDEActivity.java` | `MainActivity.kt` | `ComponentActivity` + Compose; no JNI WebView registration |
| `EditorBridge.java` | `editor/EditorBridge.kt` | Removed `native void nativeOnEditorMessage()` JNI; added Kotlin callback |
| `SafBridge.java` | `saf/SafRepository.kt` | Removed Rust-init pattern; ContentResolver from constructor `Context` |

| New file | Description |
|----------|-------------|
| `editor/EditorMessage.kt` | Sealed class for Monaco ↔ Kotlin message protocol |
| `viewmodel/IdeViewModel.kt` | `AndroidViewModel` — IDE state management + SAF + editor coordination |
| `viewmodel/model/IdeUiState.kt` | `IdeUiState` data class |
| `viewmodel/model/FileNode.kt` | `FileNode` data class (replaces Rust `FileNode` struct) |
| `viewmodel/model/EditorTab.kt` | `EditorTab` data class (replaces Rust `EditorTab` struct) |

---

## Phase 5: Verification

### Removed Slint/Rust files

| File / Directory | Reason |
|-----------------|--------|
| `android-ide/build.rs` | `slint_build::compile()` — no longer needed |
| `android-ide/Cargo.toml` | Entire Rust workspace definition |
| `android-ide/Cargo.lock` | Rust lockfile |
| `android-ide/.cargo/config.toml` | NDK rustflags for cargo cross-compile |
| `android-ide/src/` | Rust application source (`lib.rs`, `main.rs`, `ui.rs`) |
| `android-ide/ui/` | Slint UI files (`main.slint`, `components/`) |
| `android-ide/modules/` | 8 Rust modules (editor, filesystem, settings, etc.) |
| `android/java/dev/androidide/IDEActivity.java` | Slint NativeActivity subclass |
| `android/java/dev/androidide/EditorBridge.java` | Rust JNI bridge |
| `android/java/dev/androidide/SafBridge.java` | Rust-initialized SAF bridge |

### Preserved files

| File / Directory | Why preserved |
|-----------------|---------------|
| `android/assets/editor/` | Monaco HTML/JS — untouched; JS protocol identical |
| `scripts/fetch-monaco.sh` | Still required to download Monaco bundle for CI |
| `android/gradle/` | Gradle wrapper config — unchanged |
| `android/app/src/main/res/` | Launcher icon resources — unchanged |
| All `*.md` documentation | Preserved per migration Rule 3 |

---

## Migration Decisions

### MD-001 — ComponentActivity over AppCompatActivity

**Date:** 2026-06-12  
`MainActivity` extends `ComponentActivity` (from `androidx.activity:activity-compose`) rather than `AppCompatActivity`. `ComponentActivity` is the minimal Activity for Compose and is the recommended baseline. `AppCompatActivity` provides backwards-compatible Material components that are redundant when using Material3 Compose directly.

### MD-002 — `ViewModel` receives `Application` for SAF Context

**Date:** 2026-06-12  
`IdeViewModel` extends `AndroidViewModel(application)` so it can hold an `Application` reference for SAF operations (`ContentResolver`) without leaking an `Activity` reference. `SafRepository` is instantiated once in the ViewModel constructor with the Application context.

### MD-003 — Monaco WebView remembered across recompositions

**Date:** 2026-06-12  
The Monaco `WebView` in `EditorPane.kt` is wrapped in `remember {}`. This prevents the WebView from being destroyed and re-created on every recomposition (which would reset Monaco's editor state). The `AndroidView` update lambda is intentionally a no-op — all editor updates are driven via `evaluateJavascript()` from `EditorBridge.kt`.

### MD-004 — JS protocol unchanged

**Date:** 2026-06-12  
`monaco-init.js` and `index.html` are NOT modified. The Kotlin `EditorBridge.kt` produces the exact same `window.androidIDE.receiveMessage({type, ...})` JS call format that the Rust bridge.rs `outbound_to_js()` would have produced. This was verified by reading `monaco-init.js` before writing `EditorBridge.kt`.

---

## Blockers

None. Migration complete as of 2026-06-12.

---

Last updated: 2026-06-12
