# DEBUG_LOG.md — Android IDE

Historical debugging record. Every bug fix, architectural correction, and failed design decision must be recorded here.

Future contributors must be able to understand previous mistakes without rediscovering them.

---

## Log Format

| Field | Description |
|-------|-------------|
| Date | ISO 8601 date (YYYY-MM-DD) |
| Subsystem | Module where the issue occurred |
| Issue | Brief description of the problem observed |
| Root Cause | Why it happened |
| Files Modified | Comma-separated list of modified files |
| Solution | What was changed to fix it |
| Prevention | How to avoid this class of bug in the future |
| Notes | Optional additional context |

---

## Entries

### BUG-001 — Slint 1.8 incompatible with Android targets

| Field | Value |
|-------|-------|
| **Date** | 2026-06-10 |
| **Subsystem** | build-chain |
| **Issue** | `cargo ndk -t arm64-v8a build` failed with `feature "backend-winit" does not exist for Android target` |
| **Root Cause** | `Cargo.toml` declared `slint = { version = "1.8", features = ["backend-winit"] }` in `[dependencies]`, which applies to ALL targets including Android. `backend-winit` requires X11/Wayland/Metal — absent on Android. Additionally, Slint 1.8 has no Android backend at all; Android support was added in Slint 1.10 and stabilised in 1.16. |
| **Files Modified** | `Cargo.toml` |
| **Solution** | Upgraded Slint to 1.16.1. Moved `slint` from `[dependencies]` to per-target sections: desktop gets `features = ["backend-winit"]`, Android gets `features = ["backend-android-activity-06"]`. `slint-build` upgraded to match: `1.16.1`. |
| **Prevention** | Never put Slint (or any UI framework with platform-specific backend features) in the shared `[dependencies]`. Always use `[target.'cfg(...)'.dependencies]` for Android vs desktop splits. |
| **Notes** | The comment in the original Cargo.toml (`"backend-android-activity" feature in the APK build profile`) was incorrect — this feature did not exist in 1.8. The correct feature for android-activity 0.6 in Slint 1.16+ is `backend-android-activity-06`. |

---

### BUG-002 — Missing `.cargo/config.toml` — no Android NDK linker config

| Field | Value |
|-------|-------|
| **Date** | 2026-06-10 |
| **Subsystem** | build-chain |
| **Issue** | Direct `cargo build --target aarch64-linux-android` failed with linker-not-found errors. |
| **Root Cause** | `.cargo/config.toml` was never created. Without it, Cargo has no way to know which NDK compiler to use when cross-compiling directly (without cargo-ndk). |
| **Files Modified** | `.cargo/config.toml` (created) |
| **Solution** | Created `.cargo/config.toml` with `rustflags = ["-C", "relocation-model=pic"]` for all Android targets. `cargo-ndk` still handles the linker via `CARGO_TARGET_<TRIPLE>_LINKER` env var; the config.toml covers direct `cargo build --target` invocations with manually set `CARGO_TARGET_*_LINKER`. |
| **Prevention** | Every Rust project targeting Android must have a `.cargo/config.toml` in the project root. Add it at project creation time. |

---

### BUG-003 — Android UI never initialized — blank screen on device

| Field | Value |
|-------|-------|
| **Date** | 2026-06-10 |
| **Subsystem** | android-entry |
| **Issue** | App loaded on device, initialized subsystems, then exited immediately with blank screen. No Slint UI was shown. |
| **Root Cause** | The Android JNI entry point `nativeStart()` initialized settings and filesystem but never created or ran the Slint window. `run_ui_loop()` was never called on Android. Additionally, `nativeStart()` is a custom JNI function called from a custom Java Activity — but the correct pattern for Slint 1.16+ with android-activity 0.6 is the `android_main()` function called by NativeActivity, not a custom Activity + JNI method. |
| **Files Modified** | `src/lib.rs` |
| **Solution** | Added `android_main(app: slint::android::AndroidApp)` as the true Android entry point. It: (1) initializes logcat logging, (2) sets the settings data dir from `app.internal_data_path()`, (3) calls `slint::android::init(app)`, (4) calls `crate::run_ui()`. Kept `JNI_OnLoad` for SAF JavaVM initialization. |
| **Prevention** | With Slint 1.10+ on Android, always use `android_main` (not a custom JNI entry point) and always call `slint::android::init(app)` before any Slint API. `run_ui()` must be called from `android_main()`, never from a JNI function. |
| **Notes** | `android_main` is found by the android-activity crate's NativeActivity glue code. The function is `#[no_mangle] fn android_main(app: slint::android::AndroidApp)` — not `extern "system"`, not `pub`. |

---

### BUG-004 — Gradle project files missing — APK build impossible

| Field | Value |
|-------|-------|
| **Date** | 2026-06-10 |
| **Subsystem** | build-chain |
| **Issue** | CI step `./gradlew assembleRelease` failed — no Gradle project existed. The `android/` directory contained only `java/` and `assets/` subdirectories. |
| **Root Cause** | The Gradle Android project was not created during initial setup. The GitHub Actions workflow referenced `./gradlew assembleRelease` but the wrapper, build scripts, and manifest were all absent. |
| **Files Modified** | `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/wrapper/gradle-wrapper.properties`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `.github/workflows/build.yml` |
| **Solution** | Created a complete Android Gradle project. Added `gradle/actions/setup-gradle@v3` + `gradle wrapper` generation step to CI so the wrapper jar does not need to be committed. AndroidManifest.xml uses `android.app.NativeActivity` with `android.app.lib_name = "android_ide_lib"` to match the Cargo `[lib] name`. |
| **Prevention** | Android project build files must exist before any CI run. Create them at project setup time alongside the initial Rust code. |
| **Notes** | The `gradle-wrapper.jar` binary is not committed — it is generated on-the-fly in CI by `gradle wrapper --gradle-version=8.7`. |

---

### BUG-005 — SafBridge.init() never called via NativeActivity — all file ops fail

| Field | Value |
|-------|-------|
| **Date** | 2026-06-10 |
| **Subsystem** | filesystem / android-entry |
| **Issue** | All SAF file operations (`listChildren`, `readFile`, `writeFile`, etc.) return null/fail on Android. File tree empty. Files cannot be opened, read, or saved. Phase 1 success criteria not met on device. |
| **Root Cause** | The SAF bridge requires `SafBridge.init(context)` to be called before any ContentResolver operations. The old design called it from `Activity.onCreate()` on a custom Java Activity. When the entry point was changed to `android_main` + `NativeActivity`, the custom Java Activity was eliminated — and with it, the `SafBridge.init()` call. The `android_main` function did not replace it. |
| **Files Modified** | `modules/filesystem/src/saf.rs`, `src/lib.rs` |
| **Solution** | Added `saf::init_safe_bridge(activity_ptr: *mut c_void)` to `saf.rs`. This function reads `ANativeActivity.clazz` (the Activity jobject) from the NDK struct using a stable `repr(C)` partial mirror, then calls `SafBridge.init(clazz)` via JNI. Called from `android_main()` BEFORE `slint::android::init(app)` consumes the `AndroidApp`. |
| **Prevention** | When switching Activity models (custom → NativeActivity), audit every Java method that was previously called from `Activity.onCreate()` and find a replacement call site in `android_main`. |
| **Notes** | `ANativeActivity.clazz` is at byte offset 24 on 64-bit / 12 on 32-bit — stable since NDK API 9 per `<android/native_activity.h>`. The `activity_ptr` must be captured before `slint::android::init(app)` since that call takes ownership of `app`. |

---

### BUG-006 — WRITE_EXTERNAL_STORAGE missing from AndroidManifest.xml

| Field | Value |
|-------|-------|
| **Date** | 2026-06-10 |
| **Subsystem** | build-chain / android-entry |
| **Issue** | App writes project files but manifest only declared `READ_EXTERNAL_STORAGE`. On Android 8–9 (API 26–28), write operations to external storage would be denied at runtime with `SecurityException`. |
| **Root Cause** | Initial manifest declaration only considered read operations. The IDE is a full IDE — it writes files too. |
| **Files Modified** | `android/app/src/main/AndroidManifest.xml` |
| **Solution** | Added `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`. This covers API 26–28 where scoped storage does not apply. API 29+ uses SAF exclusively and does not require this permission for SAF-granted URIs. |
| **Prevention** | For any app that writes files: declare `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` alongside `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"`. |

---

### BUG-A — CI Android build panics: "No Android platforms found"

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | ci / build-chain |
| **Issue** | CI job "Build Android (arm64-v8a)" fails during `cargo ndk ... build --release` with: `thread 'main' panicked at i-slint-backend-android-activity-1.16.1/build.rs:33: No Android platforms found` |
| **Root Cause** | `i-slint-backend-android-activity`'s build script scans `$ANDROID_HOME/platforms/` for `android-<N>/android.jar` to determine the SDK API level at compile time. The CI "Install NDK" step only ran `sdkmanager "ndk;26.3.11579264"` — installing the NDK but NOT the Android platform package (`platforms;android-34`). The `android-actions/setup-android@v3` step installed only `tools platform-tools` by default. So `$ANDROID_HOME/platforms/` was empty and the build script panicked. |
| **Files Modified** | `.github/workflows/build.yml` (both Android build jobs) |
| **Solution** | Changed the "Install NDK" step (renamed "Install NDK and Android platform") to install both: `sdkmanager "ndk;..." "platforms;android-34"`. Also exports `ANDROID_PLATFORM=$ANDROID_SDK_ROOT/platforms/android-34` as an env var so the Slint build script can use it directly without scanning. Applied to both `build-android` and `build-android-x86` jobs. |
| **Prevention** | Whenever a Slint Android build is added to CI, always install both the NDK AND the matching `platforms;android-<targetSdk>` SDK package. The Slint Android backend build script requires `android.jar` from the platform package — it does NOT use the NDK's `android.jar`. |
| **Notes** | Secondary observation: `ANDROID_NDK_HOME` (set to NDK 26) differed from the runner's pre-installed `ANDROID_NDK_ROOT` (NDK 27). This caused a warning but was not the fatal error. The platform package was the sole cause of the panic. |

---

### BUG-B — CI Check & Lint fails: "Package glib-2.0 was not found"

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | ci / build-chain |
| **Issue** | CI job "Check & Lint" fails on the Clippy step (and would also fail on Build/Test steps) with: `The system library 'glib-2.0' required by crate 'glib-sys' was not found.` |
| **Root Cause** | `cargo clippy --all-targets` and `cargo build --all` compile the host (Linux x86_64) target. The `editor` module declares `wry = "0.46"` for non-Android targets. `wry` on Linux requires WebKit2GTK 4.1 (`libwebkit2gtk-4.1-dev`) and GLib (`libglib2.0-dev`) — GTK3 system libraries that are NOT pre-installed on the `ubuntu-latest` GitHub Actions runner. The `glib-sys` build script calls `pkg-config --libs --cflags glib-2.0` and fails because no `.pc` file is present. |
| **Files Modified** | `.github/workflows/build.yml` (check job) |
| **Solution** | Added a "Install Linux system dependencies" step in the `check` job, placed before the first `cargo` compile step. Installs: `libgtk-3-dev libwebkit2gtk-4.1-dev libglib2.0-dev libayatana-appindicator3-dev librsvg2-dev`. These packages satisfy all transitive system library requirements of `wry 0.46` on Ubuntu 22.04. |
| **Prevention** | Any CI job that compiles a crate with `wry` (or any GTK/WebKit-backed crate) on a Linux runner must install the GTK3 + WebKit2GTK development packages first. Add this step whenever `wry`, `webkit2gtk`, or any GTK-dependent crate is added to the desktop dependency tree. |
| **Notes** | The `cargo fmt` steps do not compile and are not affected. All three compile steps (Clippy, Build, Test) in the `check` job fail with the same root cause — the system dependencies step must precede all of them. |

---

### BUG-C — APK has no launcher icon — blank icon on home screen

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | android / build-chain |
| **Issue** | APK installs and launches successfully but shows a blank/default icon on the Android home screen launcher. No `android:icon` attribute was set in the manifest `<application>` element and no icon resources existed. |
| **Root Cause** | The `AndroidManifest.xml` `<application>` element had no `android:icon` or `android:roundIcon` attributes. No `res/` directory existed under `app/src/main/` — no mipmap or drawable resources of any kind were present. |
| **Files Modified** | `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`, `android/app/src/main/res/drawable/ic_launcher_foreground.xml`, `android/app/src/main/res/values/colors.xml` |
| **Solution** | Added `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"` to the `<application>` element. Created adaptive icon resources: mipmap XML files pointing to a vector "A" monogram foreground (VS-Code blue #007ACC) on a dark IDE background (#1e1e1e). Since `minSdk = 26`, only the `mipmap-anydpi-v26/` bucket is needed — adaptive icons are supported on all target devices. |
| **Prevention** | Always add `android:icon` to the `<application>` element and create at least the `mipmap-anydpi-v26/` icon resources when creating a new Android project. Without an icon, the APK cannot be considered production-ready. |
| **Notes** | `fillType="evenOdd"` on the vector path creates the aperture (inner hole) in the "A" letterform. This attribute requires `minSdk >= 24` (Android 7.0), which is satisfied since `minSdk = 26`. |

---

### BUG-D — `modules/git` in workspace members but directory does not exist

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | build-chain |
| **Issue** | `cargo build` (any target) aborts immediately with `error: no such file or directory: modules/git` before any Rust code is compiled. |
| **Root Cause** | `Cargo.toml` listed `"modules/git"` in `[workspace] members`. Cargo resolves all workspace members at startup and panics if a member directory or its `Cargo.toml` does not exist. The `modules/git` package is planned for Phase 3 but was never created. |
| **Files Modified** | `Cargo.toml` |
| **Solution** | Removed `"modules/git"` from `[workspace] members`. Also commented out `git2 = "0.19"` from `[workspace.dependencies]` since nothing in the current workspace depends on it. A note was added explaining that `modules/git` must be created (directory + `Cargo.toml`) before being re-added to members. |
| **Prevention** | Never add a workspace member before its `Cargo.toml` exists. If a module is planned but not yet implemented, keep it out of the workspace `members` list until at least a stub `Cargo.toml` + `src/lib.rs` are committed. |

---

### BUG-E — `scaffold_project` concatenates `/filename` onto SAF tree URIs

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | filesystem / ui |
| **Issue** | Creating a new project on Android succeeds at creating the directory but all scaffold files (Cargo.toml, main.rs, etc.) are silently not written. The new project directory is empty on device. |
| **Root Cause** | `scaffold_project()` in `src/ui.rs` called `fs.create_file(root, "Cargo.toml", ...)` (which returns the new SAF document URI) but discarded the return value, then called `fs.write_file(&format!("{root}/Cargo.toml"), ...)`. On Android, `root` is a SAF tree URI (`content://...`). Appending `/Cargo.toml` to a SAF tree URI produces a string that is not a valid document URI — `SafBridge.writeFile()` receives it, attempts to open it as a `content://` URI, and gets `IllegalArgumentException` from the ContentResolver. The exception is caught and `false` is returned, which `saf::write_file` maps to `FilesystemError::Io`. On desktop `root` is a real filesystem path so slash-concatenation worked correctly there. |
| **Files Modified** | `src/ui.rs` |
| **Solution** | Captured the `Result<String>` returned by `create_file()` in a variable (e.g. `let toml_uri = fs.create_file(...)?`) and passed it to `write_file(&toml_uri, ...)`. Applied to all five project types: Rust, Kotlin, Python, C/C++, Generic. On desktop `create_file()` returns the full filesystem path, so the fix is backward-compatible. |
| **Prevention** | SAF document URIs are opaque. NEVER construct a child document URI by concatenating a parent URI with `/filename`. Always use the URI returned by `createDocument()` / `create_file()` for subsequent operations on that document. |
| **Notes** | `FilesystemManager::create_file()` already correctly returns the document URI on Android (via `saf::create_document`) and the filesystem path on desktop. The bug was purely in the call site discarding that return value. |

---

### BUG-F — Monaco editor required internet connection — offline use impossible

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | editor / build-chain |
| **Issue** | On a device or emulator without internet access, the editor WebView shows "Loading editor…" indefinitely. Monaco fails to load because it is fetched from the unpkg CDN at runtime. |
| **Root Cause** | `index.html` had `<script src="https://unpkg.com/monaco-editor@0.52.0/min/vs/loader.js">` and `monaco-init.js` had `require.config({ paths: { vs: 'https://unpkg.com/monaco-editor@0.52.0/min/vs' } })`. Both URLs point to the unpkg CDN. Any network failure (offline device, slow connection, CDN outage) prevents Monaco from loading at all. |
| **Files Modified** | `android/assets/editor/index.html`, `android/assets/editor/monaco-init.js`, `scripts/fetch-monaco.sh` (created), `.gitignore` (created), `.github/workflows/build.yml` (both Android build jobs) |
| **Solution** | Changed both files to use relative local paths (`vs/loader.js` and `vs` respectively). Created `scripts/fetch-monaco.sh` to download Monaco 0.52.0 from npm (`npm install monaco-editor@0.52.0 --prefix /tmp/monaco`) and copy `min/vs/` to `android/assets/editor/vs/`. Added `android/assets/editor/vs/` to `.gitignore` (~20 MB, not committed). Added "Bundle Monaco editor" step to both Android CI build jobs to run the script before `./gradlew`. |
| **Prevention** | Never load runtime assets from a CDN in a mobile app. Bundle all required JS/CSS assets in the APK. Add download scripts to CI and document the local setup steps. Add `vs/` (or equivalent generated dirs) to `.gitignore` immediately when adopting this pattern. |
| **Notes** | The npm `--no-save` `--ignore-scripts` flags prevent modifying the workspace and skip potentially unsafe post-install hooks. The script is idempotent — it exits early if `vs/loader.js` already exists. Monaco version is pinned in the script; upgrading requires changing `MONACO_VERSION` in `fetch-monaco.sh`. |

---

### BUG-G — Dead JNI export `nativeSetFilesDir` referenced nonexistent `MainActivity`

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | settings / android-entry |
| **Issue** | `modules/settings/src/android.rs` contained a `mod jni_exports` block exporting `Java_dev_androidide_MainActivity_nativeSetFilesDir`. This JNI function requires a `dev.androidide.MainActivity` Java class to call it, but no such class exists — the app uses `android.app.NativeActivity`. The export compiled into the `.so` but was unreachable dead code. |
| **Root Cause** | The JNI export was designed for the original custom-Activity architecture (BUG-003). When the entry point was changed to `android_main()` + `NativeActivity`, the settings data dir moved to `app.internal_data_path()` (direct Rust call, no JNI). The `mod jni_exports` block was not removed at the same time. |
| **Files Modified** | `modules/settings/src/android.rs` |
| **Solution** | Removed the `mod jni_exports` block entirely. Updated the module-level doc to describe the actual initialization flow: `android_main()` → `app.internal_data_path()` → `android::init_files_dir()`. The `init_files_dir()` and `files_dir()` functions are kept — they are still called from `android_main`. |
| **Prevention** | When changing the Android entry-point model, grep for `Java_dev_androidide_MainActivity_native*` JNI exports and remove any that are no longer reachable. |

---

## Architectural Corrections

### AC-001 — Slint Android entry point model changed (1.8 placeholder → 1.16 production)

**Date:** 2026-06-10

**Original design (incorrect):**
- Custom Java `MainActivity` calls `nativeStart()` via JNI
- Slint UI was planned but not implemented ("TODO: integrate android-activity")
- Slint version 1.8 used — has no Android backend

**Corrected design:**
- `android.app.NativeActivity` in AndroidManifest.xml — no custom Java Activity needed
- `android_main(app: AndroidApp)` in Rust is the entry point, called by NativeActivity
- `slint::android::init(app)` initializes the Android backend before any Slint API
- Settings data dir comes from `app.internal_data_path()` — no JNI needed
- Slint 1.16.1 with `backend-android-activity-06` feature

---

### BUG-H — Monaco WebView unreachable on Android — IDEActivity architectural fix

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | editor / android-entry |
| **Issue** | On Android, every call to `send_to_editor()` returned `Err(WebViewNotRegistered)`. The `WEBVIEW_SENDER` in `webview.rs` was populated by `Java_dev_androidide_MainActivity_nativeRegisterEditorWebView` — a JNI export that required a `dev.androidide.MainActivity` class. No such class existed; the app used `android.app.NativeActivity` directly. Files could be opened (Slint tab bar updated) but Monaco received no `LoadFile` messages and the editor area was empty on device. |
| **Root Cause** | OPEN-001: NativeActivity's native Surface is behind all Android Java Views. A `WebView` — a Java View — cannot be overlaid on a NativeActivity surface from Rust alone. The previous design required a custom Java Activity to create and register the WebView, but that Activity (`MainActivity`) was removed when the entry point changed to NativeActivity + `android_main`. The `nativeRegisterEditorWebView` JNI export was never reachable. |
| **Files Modified** | `android/java/dev/androidide/IDEActivity.java` (created), `android/app/src/main/AndroidManifest.xml`, `modules/editor/src/webview.rs`, `src/lib.rs` |
| **Solution** | Implemented Option 2 (custom hybrid Activity): Created `IDEActivity extends NativeActivity` in `android/java/dev/androidide/IDEActivity.java`. Key design points: (1) extends NativeActivity so Slint's android-activity 0.6 backend is unchanged; (2) `onCreate()` calls `super.onCreate()` (loads `.so`) then `setupEditorOverlay()` which creates a `FrameLayout` overlay via `getWindow().addContentView()` — Java Views always composite above the native Surface; (3) the overlay contains a `LinearLayout` with `mEditorWebView` (Monaco, always visible) and `mPreviewWebView` (hidden until `showPreview()` called) for side-by-side edit+preview; (4) overlay is positioned with margins to reveal Slint's chrome (app bar, sidebar, tab bar, status bar) in the uncovered areas. WebView registration uses a PULL design: `android_main()` calls `webview::android::init_webview_from_activity()` (step 5 of init sequence), which calls `IDEActivity.getInstance().getEditorWebView()` via JNI and stores the `GlobalRef` in `WEBVIEW_SENDER`. This is race-free: `android_main()` runs in the native thread started by `onStart()`, which is always called after `onCreate()` completes. Added `saf::get_vm()` to expose the stored `JavaVM` for use by `init_webview_from_activity()`. Added `webview::android::show_preview(url)`, `hide_preview()`, and `adjust_editor_bounds()` Rust functions that call the matching static Java methods via JNI. Manifest changed from `android.app.NativeActivity` to `dev.androidide.IDEActivity`. |
| **Prevention** | When using NativeActivity + Slint, any Java View (WebView, custom UI) must be created in a NativeActivity subclass via `getWindow().addContentView()`. Java Views always layer above the native Surface — this is how Android's compositing works. NEVER try to create a WebView from Rust/JNI and attach it directly to the native Surface. Always extend NativeActivity rather than replacing it, to preserve the android-activity backend contract. |
| **Notes** | AD-004 (IDEActivity design decision) added to PROJECT_PLAN.md. The `saf.rs` tracing import was also corrected in this session: `info!` was used on line 112 but not imported in `use tracing::{debug, warn}` — fixed to `use tracing::{debug, info, warn}`. |

---

### BUG-I — Tab ID collision risk: SystemTime stub replaced with UUID v4

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | editor |
| **Issue** | OPEN-002: `uuid_v4()` in `modules/editor/src/tab.rs` used `SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos()` formatted as hex. `SystemTime` is not monotonic; rapid tab opens (e.g. opening multiple files from the file tree) could yield duplicate IDs. Duplicate tab IDs caused `EditorManager::tab_by_id()` to return the wrong tab and `close_tab()` to close the wrong file. |
| **Root Cause** | The `uuid_v4()` stub was documented as a placeholder (see OPEN-002 note in previous DEBUG_LOG). The `uuid` crate was not yet a dependency. |
| **Files Modified** | `modules/editor/src/tab.rs`, `modules/editor/Cargo.toml`, `Cargo.toml` (workspace) |
| **Solution** | Added `uuid = { version = "1", features = ["v4"] }` to workspace `[workspace.dependencies]` in root `Cargo.toml`. Declared `uuid = { workspace = true }` in `modules/editor/Cargo.toml`. Replaced the `uuid_v4()` stub function entirely: `EditorTab::new()` now calls `uuid::Uuid::new_v4().to_string()` directly. The `uuid` crate uses OS entropy (`/dev/urandom` on Android via `getrandom`) and is collision-proof in practice (2^122 random bits per ID). |
| **Prevention** | Never use `SystemTime` for ID generation. IDs require uniqueness, not time — use a dedicated UUID crate or an atomic counter. |

---

## Known Open Issues

_No open issues after 2026-06-11 session._

---

## Failed Design Decisions

### FD-001 — nativeStart() as primary Android entry point

**Attempted:** Custom JNI function `Java_dev_androidide_MainActivity_nativeStart` in `src/lib.rs` as the primary entry point for Android UI initialization.

**Why it failed:** Slint 1.10+ uses android-activity's `android_main` pattern. The NativeActivity lifecycle is managed by the NDK — you cannot start Slint's UI loop from a JNI function called by a custom Activity. The Activity would call `nativeStart()`, Slint would have no `AndroidApp` handle, and `MainWindow::new()` would panic or do nothing.

**Replacement:** `android_main(app: slint::android::AndroidApp)` — the android-activity standard entry point.

---

Last updated: 2026-06-11
