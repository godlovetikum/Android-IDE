/// android-ide/modules/settings/src/android.rs
///
/// Android-specific helpers for the settings module.
///
/// On Android, the canonical persistent storage path is `getFilesDir()` — the
/// app's private internal storage. This path is not available until the Activity
/// is created, so it cannot be resolved at module init time.
///
/// Usage in the Activity (Java):
///
///   @Override
///   protected void onCreate(Bundle savedInstanceState) {
///       super.onCreate(savedInstanceState);
///       // Must be called before SettingsManager::new()
///       MainActivity.nativeSetFilesDir(getFilesDir().getAbsolutePath());
///       // ... rest of init
///   }
///
/// The JNI export `Java_dev_androidide_MainActivity_nativeSetFilesDir` stores
/// the path in a global OnceLock so subsequent calls to `files_dir()` can
/// retrieve it without JNI.

use std::sync::OnceLock;
use tracing::{error, info, warn};

static ANDROID_FILES_DIR: OnceLock<String> = OnceLock::new();

/// Store the Android `getFilesDir()` path for use by SettingsManager.
///
/// Call this exactly once from the Activity before constructing SettingsManager.
/// Subsequent calls are silently ignored.
pub fn init_files_dir(path: &str) {
    if ANDROID_FILES_DIR.set(path.to_string()).is_err() {
        warn!("Android files dir already set — ignoring duplicate init");
    } else {
        info!(path, "Android files dir registered for settings");
    }
}

/// Return the stored Android files directory path, if set.
pub fn files_dir() -> Option<&'static str> {
    ANDROID_FILES_DIR.get().map(String::as_str)
}

// ---------------------------------------------------------------------------
// JNI export
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
mod jni_exports {
    use super::*;
    use jni::objects::{JClass, JString};
    use jni::JNIEnv;

    /// Called by the Android Activity during `onCreate` to register `getFilesDir()`.
    ///
    /// Java signature:
    ///   private static native void nativeSetFilesDir(String path);
    #[no_mangle]
    pub extern "system" fn Java_dev_androidide_MainActivity_nativeSetFilesDir(
        mut env: JNIEnv,
        _class: JClass,
        path: JString,
    ) {
        match env.get_string(&path) {
            Ok(s) => init_files_dir(&String::from(s)),
            Err(e) => error!("nativeSetFilesDir: get_string failed: {e}"),
        }
    }
}
