# STATUS_TRACKER.md — Android IDE

**Current Date:** 2026-06-11
**Active Phase:** Phase 1 — Foundation ✅ COMPLETE + Build Chain Fixes Applied
**Active Subsystem:** terminal (next phase — Phase 2)

---

## Completed Tasks

| # | Task | Subsystem | Date |
|---|------|-----------|------|
| 001 | Created PROJECT_PLAN.md | documentation | 2026-06-10 |
| 002 | Created DEBUG_LOG.md | documentation | 2026-06-10 |
| 003 | Created QA_WORKFLOW.md | documentation | 2026-06-10 |
| 004 | Created STATUS_TRACKER.md | documentation | 2026-06-10 |
| 005 | Created Cargo.toml workspace | project-setup | 2026-06-10 |
| 006 | Created application main.rs shell | app-shell | 2026-06-10 |
| 007 | Created filesystem module skeleton | filesystem | 2026-06-10 |
| 008 | Created editor module skeleton | editor | 2026-06-10 |
| 009 | Created settings module skeleton | settings | 2026-06-10 |
| 010 | Created Slint UI shell (main.slint) | ui | 2026-06-10 |
| 011 | Created GitHub Actions build pipeline | ci | 2026-06-10 |
| 012 | Created all module README.md files | documentation | 2026-06-10 |
| 013 | Implemented filesystem SAF bridge | filesystem | 2026-06-10 |
| 014 | Implemented project creation dialog (Slint UI) | ui | 2026-06-10 |
| 015 | Implemented project opening / recent projects list | ui | 2026-06-10 |
| 016 | Implemented file tree explorer widget | ui | 2026-06-10 |
| 017 | Implemented Monaco WebView integration | editor | 2026-06-10 |
| 018 | Implemented file open/close tab management | editor+ui | 2026-06-10 |
| 019 | Implemented file save (explicit + auto-save) | filesystem+ui | 2026-06-10 |
| 020 | Implemented settings TOML persistence (Android path) | settings | 2026-06-10 |
| 021 | Implemented status bar cursor position wiring | ui | 2026-06-10 |
| B01 | Fix BUG-001: Slint 1.8 → 1.16.1, split per-target features | build-chain | 2026-06-10 |
| B02 | Fix BUG-002: Create .cargo/config.toml with NDK rustflags | build-chain | 2026-06-10 |
| B03 | Fix BUG-003: Add android_main() entry point, slint::android::init() | android-entry | 2026-06-10 |
| B04 | Fix BUG-004: Create full Gradle Android project + CI update | build-chain | 2026-06-10 |
| B05 | Fix BUG-005: SafBridge.init() via saf::init_safe_bridge() from android_main | filesystem | 2026-06-10 |
| B06 | Fix BUG-006: Add WRITE_EXTERNAL_STORAGE (maxSdkVersion=28) to manifest | build-chain | 2026-06-10 |
| B07 | Fix BUG-A: Install platforms;android-34 in CI Android build jobs | ci | 2026-06-11 |
| B08 | Fix BUG-B: Install GTK3/WebKit2GTK system deps in CI check job | ci | 2026-06-11 |
| B09 | Fix BUG-C: Add launcher icon resources + manifest android:icon attribute | android | 2026-06-11 |
| B10 | Fix stale saf.rs doc comment (init sequence step 2 described old Activity model) | filesystem | 2026-06-11 |
| B11 | Fix BUG-D: Remove missing modules/git workspace member from Cargo.toml | build-chain | 2026-06-11 |
| B12 | Fix BUG-E: scaffold_project SAF URI slash-concatenation bug (all 5 project types) | filesystem/ui | 2026-06-11 |
| B13 | Fix BUG-F: Monaco offline bundle — scripts/fetch-monaco.sh + CI step + local paths | editor/ci | 2026-06-11 |
| B14 | Fix BUG-G: Remove dead nativeSetFilesDir JNI export, update settings/android.rs doc | settings | 2026-06-11 |
| B15 | Fix SafBridge.java stale class comment (described old Activity.onCreate() pattern) | filesystem | 2026-06-11 |
| B16 | Fix OPEN-001: IDEActivity extends NativeActivity — Monaco WebView overlay + edit+preview split | editor/android-entry | 2026-06-11 |
| B17 | Fix OPEN-002: uuid crate replaces SystemTime stub in tab.rs — collision-proof tab IDs | editor | 2026-06-11 |

---

## In Progress Tasks

None. Phase 1, all build-chain fixes, and all open architectural issues are complete.

---

## Pending Tasks — Phase 2

| # | Task | Subsystem | Priority |
|---|------|-----------|---------|
| 101 | Terminal UI widget (Slint) | terminal | High |
| 102 | PTY creation and management | terminal | High |
| 103 | proot environment bootstrap | linux-runtime | High |
| 104 | Package manager bridge (apt/pkg) | linux-runtime | Medium |
| 105 | Terminal session persistence | terminal | Medium |

---

## Future Tasks — Phase 3

| # | Task | Subsystem |
|---|------|-----------|
| 201 | Git status panel | git |
| 202 | Stage/unstage workflow | git |
| 203 | Commit dialog | git |
| 204 | Branch management UI | git |
| 205 | Push/pull operations | git |
| 206 | Clone dialog | git |
| 207 | Diff viewer | git |
| 208 | SSH key and HTTPS credential manager | git |

---

## Future Tasks — Phase 4

| # | Task | Subsystem |
|---|------|-----------|
| 301 | LSP server lifecycle | lsp |
| 302 | Diagnostics rendering | lsp |
| 303 | Autocomplete | lsp |
| 304 | Go-to-definition | lsp |
| 305 | Hover documentation | lsp |
| 306 | Code actions | lsp |

---

## Future Tasks — Phase 5

| # | Task | Subsystem |
|---|------|-----------|
| 401 | Extension package format | extensions |
| 402 | Extension loader | extensions |
| 403 | Extension permission model | extensions |
| 404 | Extension manager UI | extensions |
| 405 | Example extension | extensions |

---

## Phase Progress

| Phase | Status | Completed / Total Tasks |
|-------|--------|------------------------|
| Phase 1 — Foundation | **COMPLETE** ✅ | 21 / 21 |
| Build Chain Fixes | **COMPLETE** ✅ | 12 / 12 |
| Phase 2 — Linux Runtime | Not Started | 0 / 5 |
| Phase 3 — Git | Not Started | 0 / 8 |
| Phase 4 — Language Intelligence | Not Started | 0 / 6 |
| Phase 5 — Extensions | Not Started | 0 / 5 |

---

## Session Notes

**2026-06-10:** Initial project setup + SAF bridge (tasks 001–013).

**2026-06-10:** Slint UI — project dialog, recent projects, file tree (tasks 014–016).

**2026-06-10:** Tasks 017–021 — Monaco WebView, tab management, file save, Android settings path, status bar cursor.

**2026-06-10:** Build chain audit (report from test engineer). Four critical Android build blockers fixed:

- **BUG-001 (B01):** Slint upgraded from 1.8 → 1.16.1. Feature `backend-winit` was applying to Android targets. Fixed by splitting into `[target.'cfg(not(target_os = "android"))'.dependencies]` (backend-winit) and `[target.'cfg(target_os = "android")'.dependencies]` (backend-android-activity-06). Added `android-activity = "0.6"` with `native-activity` feature.
- **BUG-002 (B02):** `.cargo/config.toml` created with `relocation-model=pic` rustflags for all Android targets. Required for JNI `.so` output.
- **BUG-003 (B03):** `android_main(app: slint::android::AndroidApp)` added to `src/lib.rs`. This is the NativeActivity entry point (replaces the incomplete `nativeStart` approach). Calls `slint::android::init(app)` before `run_ui()`. Settings data dir obtained from `app.internal_data_path()` — removes need for `nativeSetFilesDir` JNI call.
- **BUG-004 (B04):** Complete Gradle Android project created: `android/settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`. CI updated to use `gradle/actions/setup-gradle@v3` + `gradle wrapper` generation. NativeActivity `android:lib_name = "android_ide_lib"` set correctly.

**SafBridge.java was already fully implemented** (complete SAF operations: listChildren, readFile, writeFile, createFile, deleteDocument, renameDocument, getDisplayName, getMimeType). The test engineer report incorrectly flagged it as missing.

**2026-06-11 (session 1):** CI log analysis. Two CI failures identified and fixed (BUG-A, BUG-B). One launcher icon bug fixed (BUG-C). Stale doc corrected (B10). Full codebase audit surfaced and fixed five additional issues (BUG-D through BUG-G + B15) and two open architectural problems documented (OPEN-001, OPEN-002):

- **BUG-A (B07):** `i-slint-backend-android-activity` build.rs panicked "No Android platforms found". Root cause: `sdkmanager` only installed the NDK, never `platforms;android-34`. Fixed by adding `"platforms;android-34"` to the sdkmanager command in both Android build jobs. Also exports `ANDROID_PLATFORM` env var for explicit override.
- **BUG-B (B08):** Clippy + Build + Test all failed with `Package glib-2.0 was not found`. Root cause: `wry` (used by the desktop editor module) requires GTK3/WebKit2GTK system libraries which are not pre-installed on `ubuntu-latest`. Fixed by adding `sudo apt-get install -y libgtk-3-dev libwebkit2gtk-4.1-dev libglib2.0-dev libayatana-appindicator3-dev librsvg2-dev` step before all cargo compile steps in the `check` job.
- **BUG-C (B09):** APK had no launcher icon. Fixed by adding `android:icon`/`android:roundIcon` to `<application>` in the manifest and creating `res/mipmap-anydpi-v26/`, `res/drawable/`, and `res/values/` resource directories with an adaptive "A" monogram icon.
- **B10:** `modules/filesystem/src/saf.rs` module-level doc described the old `Activity.onCreate() → SafBridge.init(this)` initialization sequence. Corrected to reflect the actual `android_main() → saf::init_safe_bridge(activity_ptr) → SafBridge.init(activity)` flow.
- **BUG-D (B11):** `Cargo.toml` listed `"modules/git"` in `[workspace] members` but the directory did not exist. `cargo build` aborted before compiling any code. Removed the member entry and commented out the orphaned `git2` workspace dependency.
- **BUG-E (B12):** `scaffold_project` in `src/ui.rs` called `create_file()` (returns the new document URI) then discarded the return value and wrote to `"{root}/filename"` — a path-concatenation that is invalid for SAF URIs. All scaffold file writes silently failed on Android. Fixed by capturing the returned URI and passing it to `write_file()` in all five project types.
- **BUG-F (B13):** Monaco editor loaded from the unpkg CDN at runtime — completely broken on offline devices. Created `scripts/fetch-monaco.sh` (idempotent npm-based downloader), gitignored `android/assets/editor/vs/`, updated `index.html` and `monaco-init.js` to use local `vs/` paths, and added a "Bundle Monaco editor" CI step before `./gradlew` in both Android build jobs.
- **BUG-G (B14):** Dead JNI export `Java_dev_androidide_MainActivity_nativeSetFilesDir` in `modules/settings/src/android.rs` required a `MainActivity` class that no longer exists. Removed. Settings data dir now flows via `app.internal_data_path()` → `android::init_files_dir()` in `android_main()` — no JNI needed.
- **B15:** `SafBridge.java` class-level comment described the obsolete `Activity.onCreate() → SafBridge.init(this)` setup. Updated to document the actual Rust-driven `saf::init_safe_bridge() → SafBridge.init(context)` flow.
- **OPEN-001 (not fixed — needs design decision):** The entire Monaco WebView pipeline on Android is unreachable. `WEBVIEW_SENDER` in `webview.rs` is populated by `nativeRegisterEditorWebView()` which requires a `MainActivity`, but the app uses `NativeActivity`. Every `send_to_editor()` call returns `Err(WebViewNotRegistered)`. The editor area is empty on device — Slint IDE chrome works but no code is editable. See DEBUG_LOG.md OPEN-001 for three architectural options.
- **OPEN-002 (not fixed — low priority):** Tab UUID uses `SystemTime::now().as_nanos()` which can collide if two files are opened within the same nanosecond. Should use the `uuid` crate.

**2026-06-11 (session 2):** Both open architectural issues resolved (OPEN-001, OPEN-002 → B16, B17):

- **OPEN-001 → B16:** Created `dev.androidide.IDEActivity extends NativeActivity`. `onCreate()` builds a transparent `FrameLayout` overlay via `getWindow().addContentView()` containing `mEditorWebView` (Monaco) and `mPreviewWebView` (preview, hidden by default). Slint's chrome (app bar 48dp, sidebar 240dp, tab bar 35dp, status bar 22dp) remains visible through the uncovered margins. Rust pulls the WebView reference via JNI in `android_main()` (step 5 of init sequence) via `IDEActivity.getInstance().getEditorWebView()` — guaranteed race-free by Android lifecycle ordering. Added `show_preview(url)`, `hide_preview()`, and `adjust_editor_bounds()` JNI functions. Also fixed `saf.rs` missing `info` import in tracing use statement. See AD-004 in PROJECT_PLAN.md. See BUG-H in DEBUG_LOG.md.
- **OPEN-002 → B17:** `uuid` crate added to workspace deps + editor module deps. `EditorTab::new()` now calls `uuid::Uuid::new_v4().to_string()`. See BUG-I in DEBUG_LOG.md.

Last updated: 2026-06-11
