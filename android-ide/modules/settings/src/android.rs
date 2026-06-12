/// android-ide/modules/settings/src/android.rs
///
/// Android-specific helpers for the settings module.
///
/// On Android, the canonical persistent storage path is `getFilesDir()` — the
/// app's private internal storage. This path is available via AndroidApp during
/// `android_main()` as `app.internal_data_path()`.
///
/// Initialization (enforced by android_main in src/lib.rs):
///
///   1. android_main() receives the AndroidApp handle from NativeActivity.
///   2. app.internal_data_path() returns the files dir as a PathBuf.
///   3. android::init_files_dir(&path_str) is called directly — no JNI required.
///   4. slint::android::init(app) consumes the app handle.
///   5. run_ui() → SettingsManager::new() → android::files_dir() returns the path.
///
/// The JNI export that was previously here (nativeSetFilesDir) required a
/// custom Java MainActivity to call it. That Activity was removed when the
/// entry point changed to NativeActivity / android_main(). The files dir is
/// now sourced directly from AndroidApp::internal_data_path(), requiring no JNI.

use std::sync::OnceLock;
use tracing::{info, warn};

static ANDROID_FILES_DIR: OnceLock<String> = OnceLock::new();

/// Store the Android `getFilesDir()` path for use by SettingsManager.
///
/// Call this exactly once from `android_main()` before constructing SettingsManager.
/// Subsequent calls are silently ignored (OnceLock guarantees write-once semantics).
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
