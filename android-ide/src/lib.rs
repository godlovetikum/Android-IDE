/// android-ide/src/lib.rs
///
/// Library root. Exposes the public initialization API used by:
///   - src/main.rs         (desktop binary entry point)
///   - android_main()      (Android NativeActivity entry point via android-activity crate)
///
/// Android entry-point model (Slint 1.16+ / android-activity 0.6):
///   The Android runtime calls `android_main(app: AndroidApp)` in the loaded .so.
///   This function initialises Slint's Android backend and runs the UI event loop.
///   There is NO custom Java Activity — `android.app.NativeActivity` in
///   AndroidManifest.xml finds the .so via `android.app.lib_name = "android_ide_lib"`.
///
/// Android initialization order (enforced by android_main):
///   1. Library loaded     → JNI_OnLoad fires       → SAF JavaVM stored
///   2. android_main runs  → activity_ptr captured  (before slint init consumes app)
///   3.                    → internal_data_path()    → settings dir set
///   4.                    → saf::init_safe_bridge() → SafBridge.init(activity) called
///   5.                    → slint::android::init()  → Slint Android backend ready
///   6.                    → run_ui()                → subsystems created, event loop starts
///
/// Steps 4 and 5 MUST be in this order: init_safe_bridge uses the JavaVM from step 1
/// (already set) and must happen before any filesystem operations in run_ui().
/// slint::android::init() consumes the AndroidApp, so activity_ptr must be saved first.

pub mod error;
pub mod ui;

use anyhow::Result;

/// Initialize the settings module and return the manager.
/// On desktop, called from main() before run_ui().
/// On Android, android_main() calls run_ui() which creates its own SettingsManager.
pub fn init_settings() -> Result<android_ide_settings::SettingsManager> {
    android_ide_settings::SettingsManager::new()
}

/// Initialize the filesystem module.
/// On desktop, called from main() before run_ui().
/// On Android, android_main() calls run_ui() which creates its own FilesystemManager.
pub fn init_filesystem() -> Result<android_ide_filesystem::FilesystemManager> {
    android_ide_filesystem::FilesystemManager::new()
}

/// Launch the Slint UI event loop.
///
/// Desktop: call after init_settings() + init_filesystem(). Blocks until closed.
///
/// Android: call from android_main() AFTER slint::android::init(app).
///   The Slint Android event loop integrates with NativeActivity's looper and
///   returns when the Activity is destroyed.
pub fn run_ui() -> Result<()> {
    ui::run_ui_loop()
}

// ---------------------------------------------------------------------------
// Android entry point — compiled only when targeting Android.
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
mod android_entry {
    use jni::sys::{jint, JavaVM as RawJavaVM, JNI_VERSION_1_6};
    use jni::JavaVM;
    use tracing::info;

    /// Called automatically by the Android runtime the moment the .so is loaded,
    /// before android_main and before any JNI method calls from Java.
    ///
    /// Stores the JavaVM so the SAF bridge can attach from any thread.
    ///
    /// SAFETY: Android guarantees `vm` is valid and non-null for the process lifetime.
    #[no_mangle]
    pub unsafe extern "system" fn JNI_OnLoad(
        vm: *mut RawJavaVM,
        _reserved: *mut std::ffi::c_void,
    ) -> jint {
        let vm = unsafe { JavaVM::from_raw(vm).expect("JNI_OnLoad: null JavaVM") };
        android_ide_filesystem::saf::init_vm(vm);
        JNI_VERSION_1_6
    }

    /// Primary Android entry point called by android-activity / NativeActivity.
    ///
    /// Initialization order (see module-level doc for rationale):
    ///   1. Logging
    ///   2. Capture activity_ptr BEFORE slint::android::init() consumes `app`
    ///   3. Settings data dir from internal_data_path()
    ///   4. SafBridge.init() via JNI — must precede any filesystem operations
    ///   5. Slint Android backend — must precede MainWindow::new()
    ///   6. UI event loop
    #[no_mangle]
    fn android_main(app: slint::android::AndroidApp) {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Debug)
                .with_tag("AndroidIDE"),
        );

        info!("Android IDE starting (android_main)");

        // ── Step 2: capture pointers before `app` is consumed ────────────────
        // slint::android::init(app) takes ownership of `app`. All values we need
        // from it must be read beforehand.
        let activity_ptr = app.activity_as_ptr();

        // ── Step 3: settings data dir ─────────────────────────────────────────
        if let Some(data_path) = app.internal_data_path() {
            let path_str = data_path
                .to_str()
                .unwrap_or("/data/data/dev.androidide/files");
            android_ide_settings::android::init_files_dir(path_str);
            info!(path = path_str, "Settings data dir registered");
        } else {
            tracing::warn!("internal_data_path() returned None — settings will not persist");
        }

        // ── Step 4: SafBridge initialization ─────────────────────────────────
        // Calls SafBridge.init(activity) via JNI using ANativeActivity.clazz.
        // Without this, every SAF file operation returns null and the filesystem
        // module fails entirely — no files can be opened, read, or saved.
        //
        // SAFETY: activity_ptr is the valid ANativeActivity* from AndroidApp,
        // guaranteed non-null and alive for the NativeActivity lifetime.
        unsafe {
            if let Err(e) = android_ide_filesystem::saf::init_safe_bridge(activity_ptr) {
                tracing::error!(
                    "SafBridge.init() failed: {e} — \
                     all file operations will return errors on this session"
                );
                // Continue anyway: settings and Slint UI still work; the user
                // will see errors when opening files rather than a silent crash.
            } else {
                info!("SafBridge initialized");
            }
        }

        // ── Step 5: Slint Android backend ─────────────────────────────────────
        // Consumes `app`. Must happen after all reads from app above.
        // Must happen before MainWindow::new() or any Slint API call.
        if let Err(e) = slint::android::init(app) {
            tracing::error!("Slint Android init failed: {e}");
            return;
        }

        // ── Step 6: UI event loop ──────────────────────────────────────────────
        // Creates FilesystemManager, EditorManager, SettingsManager, wires all
        // Slint callbacks, registers save/cursor handlers, starts auto-save timer,
        // and blocks until the NativeActivity is destroyed.
        if let Err(e) = super::run_ui() {
            tracing::error!("Android IDE UI loop exited with error: {e}");
        }
    }
}
