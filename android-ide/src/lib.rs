/// android-ide/src/lib.rs
///
/// Library root. Exposes the public initialization API used by:
///   - src/main.rs            (desktop entry point)
///   - android_entry module   (Android JNI entry point via JNI_OnLoad)
///
/// This module does NOT contain business logic. It delegates to subsystem modules.

pub mod error;
pub mod ui;

use anyhow::Result;

/// Initialize the settings module.
/// Must be called before any other module initialization.
pub fn init_settings() -> Result<android_ide_settings::SettingsManager> {
    android_ide_settings::SettingsManager::new()
}

/// Initialize the filesystem module.
pub fn init_filesystem() -> Result<android_ide_filesystem::FilesystemManager> {
    android_ide_filesystem::FilesystemManager::new()
}

/// Launch the Slint UI event loop.
/// On desktop this blocks until the window is closed.
/// On Android the Activity calls create_window() directly.
pub fn run_ui() -> Result<()> {
    ui::run_ui_loop()
}

// ---------------------------------------------------------------------------
// Android JNI entry points — compiled only for Android targets.
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
pub mod android_entry {
    use jni::objects::JClass;
    use jni::sys::{jint, JavaVM as RawJavaVM, JNI_VERSION_1_6};
    use jni::{JavaVM, JNIEnv};
    use tracing::info;

    /// Called automatically by the Android runtime when the native library is loaded.
    ///
    /// We use JNI_OnLoad — the FIRST native call — to store the JavaVM globally.
    /// All subsequent SAF bridge calls attach to this VM on whichever thread they run.
    ///
    /// Must return JNI_VERSION_1_6 or higher; returning 0 causes library load failure.
    #[no_mangle]
    pub unsafe extern "system" fn JNI_OnLoad(
        vm: *mut RawJavaVM,
        _reserved: *mut std::ffi::c_void,
    ) -> jint {
        // SAFETY: The Android runtime guarantees this pointer is valid and non-null
        // for the entire lifetime of the process.
        let vm = unsafe { JavaVM::from_raw(vm).expect("JNI_OnLoad: null JavaVM pointer") };
        android_ide_filesystem::saf::init_vm(vm);
        JNI_VERSION_1_6
    }

    /// Called by MainActivity.nativeStart() after SafBridge.init(this) has run.
    ///
    /// Initialization order (enforced by the Activity):
    ///   1. SafBridge.init(this)   — Java side, stores ContentResolver context
    ///   2. nativeStart()          — calls this function
    #[no_mangle]
    pub extern "system" fn Java_dev_androidide_MainActivity_nativeStart(
        _env: JNIEnv,
        _class: JClass,
    ) {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Debug)
                .with_tag("AndroidIDE"),
        );
        info!("Android IDE native layer started");

        if let Err(e) = crate::init_settings() {
            tracing::error!("Settings init failed: {e}");
        }
        if let Err(e) = crate::init_filesystem() {
            tracing::error!("Filesystem init failed: {e}");
        }
        // UI is driven by the Slint Android backend via the Activity event loop.
        // TODO(task-014 follow-up): integrate android-activity crate for Slint Android backend.
    }
}
