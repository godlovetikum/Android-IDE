/// android-ide/modules/filesystem/src/error.rs
///
/// Error types for the filesystem module.

use thiserror::Error;

#[derive(Debug, Error)]
pub enum FilesystemError {
    // ------------------------------------------------------------------
    // Generic filesystem errors (both platforms)
    // ------------------------------------------------------------------

    #[error("File not found: {path}")]
    NotFound { path: String },

    #[error("Permission denied: {path}")]
    PermissionDenied { path: String },

    #[error("Path is not a directory: {path}")]
    NotADirectory { path: String },

    #[error("Path is not a file: {path}")]
    NotAFile { path: String },

    #[error("Write failed: {path}")]
    WriteFailed { path: String },

    #[error("Delete failed: {path}")]
    DeleteFailed { path: String },

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    // ------------------------------------------------------------------
    // Android SAF errors
    // ------------------------------------------------------------------

    /// Returned when any SAF operation is attempted before `saf::init_vm()`
    /// has been called. Indicates that JNI_OnLoad did not run, which means
    /// the native library was not loaded by the Android runtime.
    #[error("SAF bridge not initialized — ensure JNI_OnLoad has run and SafBridge.init(context) has been called")]
    SafNotInitialized,

    /// The supplied content:// URI is syntactically malformed.
    #[error("SAF URI invalid: {uri}")]
    InvalidSafUri { uri: String },

    /// A ContentResolver query (e.g. listChildren, getDisplayName) returned
    /// null or an unexpected result.
    #[error("SAF query failed for {uri}: {reason}")]
    SafQueryFailed { uri: String, reason: String },

    /// DocumentsContract.createDocument returned null.
    #[error("SAF create failed: could not create '{name}' inside {parent}")]
    SafCreateFailed { parent: String, name: String },

    /// A JNI call itself failed — the Java method threw an exception or the
    /// type conversion failed. The inner string is the JNI error description.
    #[error("JNI error: {0}")]
    Jni(String),
}
