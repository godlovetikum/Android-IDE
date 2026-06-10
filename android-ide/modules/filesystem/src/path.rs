/// android-ide/modules/filesystem/src/path.rs
///
/// Platform-aware path utilities.
///
/// On Android, "paths" are SAF content URIs (content://...).
/// On desktop, they are regular filesystem paths.
///
/// This module provides utilities for working with both.

/// Returns true if the given string looks like an Android SAF content URI.
pub fn is_saf_uri(path: &str) -> bool {
    path.starts_with("content://")
}

/// Extract a display name from a path or SAF URI.
pub fn display_name(path: &str) -> &str {
    if is_saf_uri(path) {
        // For SAF URIs the display name must come from the ContentResolver
        // This is a fallback for when the JNI query is not available
        path.rsplit('/').next().unwrap_or(path)
    } else {
        std::path::Path::new(path)
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or(path)
    }
}

/// Returns the file extension, if any.
pub fn extension(path: &str) -> Option<&str> {
    if is_saf_uri(path) {
        // Extension is not reliable from URI alone — rely on MIME type from ContentResolver
        None
    } else {
        std::path::Path::new(path)
            .extension()
            .and_then(|e| e.to_str())
    }
}
