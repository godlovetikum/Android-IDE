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

## Failed Design Decisions

### FD-001 — nativeStart() as primary Android entry point

**Attempted:** Custom JNI function `Java_dev_androidide_MainActivity_nativeStart` in `src/lib.rs` as the primary entry point for Android UI initialization.

**Why it failed:** Slint 1.10+ uses android-activity's `android_main` pattern. The NativeActivity lifecycle is managed by the NDK — you cannot start Slint's UI loop from a JNI function called by a custom Activity. The Activity would call `nativeStart()`, Slint would have no `AndroidApp` handle, and `MainWindow::new()` would panic or do nothing.

**Replacement:** `android_main(app: slint::android::AndroidApp)` — the android-activity standard entry point.

---

Last updated: 2026-06-10
