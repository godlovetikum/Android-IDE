/// android-ide/modules/filesystem/src/saf.rs
///
/// Android Storage Access Framework JNI bridge.
///
/// Bridges all Rust filesystem operations to Java's ContentResolver through
/// the `dev.androidide.SafBridge` Java class. Only compiled on Android targets.
///
/// Initialization sequence (must happen in this order):
///   1. JVM calls JNI_OnLoad → `saf::init_vm(vm)` stores the JavaVM globally
///   2. `android_main()` captures `activity_ptr` from `AndroidApp` (before slint init consumes it)
///   3. `android_main()` → `saf::init_safe_bridge(activity_ptr)` → `SafBridge.init(activity)` via JNI
///   4. FilesystemManager is now usable; SAF operations can proceed
///
/// Dependencies:
///   jni = "0.21" — JNI bindings, Android target only.
///     Justification: All Java interop on Android requires JNI. There is no
///     alternative for calling ContentResolver from Rust on Android.

use jni::objects::{JByteArray, JString, JValue};
use jni::JavaVM;
use serde::Deserialize;
use std::sync::OnceLock;
use tracing::{debug, info, warn};

use crate::error::FilesystemError;
use crate::tree::{FileKind, FileNode};

// ---------------------------------------------------------------------------
// Global JavaVM
// ---------------------------------------------------------------------------

static JAVA_VM: OnceLock<JavaVM> = OnceLock::new();

/// Store the JavaVM for use by all future SAF calls.
/// Called once from JNI_OnLoad in `src/lib.rs`.
pub fn init_vm(vm: JavaVM) {
    if JAVA_VM.set(vm).is_err() {
        warn!("JavaVM already initialized — duplicate init_vm call ignored");
    }
}

/// Return a reference to the stored JavaVM.
///
/// Used by other Android modules (e.g. `editor::webview::android`) that need
/// to make JNI calls but share the VM stored during `JNI_OnLoad` rather than
/// managing their own reference.
///
/// Returns `None` if `init_vm()` has not yet been called (should not happen
/// after `JNI_OnLoad` fires; treat as a fatal precondition failure).
pub fn get_vm() -> Option<&'static JavaVM> {
    JAVA_VM.get()
}

fn vm() -> Result<&'static JavaVM, FilesystemError> {
    JAVA_VM.get().ok_or(FilesystemError::SafNotInitialized)
}

// ---------------------------------------------------------------------------
// SafBridge initialization
// ---------------------------------------------------------------------------

/// Minimal repr(C) mirror of the NDK's ANativeActivity struct — first four
/// fields only, sufficient to reach the Activity jobject (`clazz`).
///
/// The layout is specified by the NDK ABI and has been stable since API 9:
///   callbacks  — *mut ANativeActivityCallbacks  (pointer-sized)
///   vm         — *mut JavaVM                    (pointer-sized)
///   env        — *mut JNIEnv  (main thread)     (pointer-sized)
///   clazz      — jobject  (the Activity object) (pointer-sized)
///
/// References: <android/native_activity.h>  NDK r26 / API 9+
#[repr(C)]
struct NativeActivityPartial {
    _callbacks: *mut std::ffi::c_void,
    _vm:        *mut std::ffi::c_void,
    _env:       *mut std::ffi::c_void,
    clazz:      jni::sys::jobject,
}

/// Call `SafBridge.init(activity)` via JNI from the ANativeActivity pointer
/// provided by `android_main`.
///
/// This replaces the old `Activity.onCreate() → SafBridge.init(this)` call
/// that was lost when we switched from a custom Java Activity to NativeActivity.
///
/// Must be called:
///   - After `init_vm()` (i.e. after JNI_OnLoad — always true in android_main)
///   - Before any filesystem operations that go through SafBridge
///   - Before `slint::android::init(app)` consumes the AndroidApp
///
/// # Safety
/// `activity_ptr` must be the `ANativeActivity*` from `AndroidApp::activity_as_ptr()`.
/// The NDK guarantees this pointer is valid for the lifetime of the NativeActivity.
pub unsafe fn init_safe_bridge(
    activity_ptr: *mut std::ffi::c_void,
) -> Result<(), FilesystemError> {
    if activity_ptr.is_null() {
        return Err(FilesystemError::Jni(
            "init_safe_bridge: null ANativeActivity pointer".into(),
        ));
    }

    // Read the Activity jobject from ANativeActivity.clazz.
    // SAFETY: activity_ptr is a valid ANativeActivity* guaranteed by the caller.
    let native_activity = activity_ptr as *const NativeActivityPartial;
    let clazz = unsafe { (*native_activity).clazz };

    let vm = vm()?;
    let env = attach(vm)?;
    // SAFETY: we own the attach guard; env is valid for this scope.
    let mut env = unsafe { env.unsafe_clone() };

    // SAFETY: clazz is the Activity jobject, valid for the NativeActivity lifetime.
    let activity_obj = unsafe { jni::objects::JObject::from_raw(clazz) };

    env.call_static_method(
        BRIDGE_CLASS,
        "init",
        "(Landroid/content/Context;)V",
        &[JValue::Object(&activity_obj)],
    )
    .map_err(|e| FilesystemError::Jni(format!("SafBridge.init JNI call failed: {e}")))?;

    info!("SafBridge initialized via ANativeActivity.clazz");
    Ok(())
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const BRIDGE_CLASS: &str = "dev/androidide/SafBridge";

/// SAF MIME type that represents a directory.
pub const MIME_DIRECTORY: &str = "vnd.android.document/directory";

// ---------------------------------------------------------------------------
// Wire type — JSON shape returned by SafBridge.listChildren()
// ---------------------------------------------------------------------------

#[derive(Debug, Deserialize)]
struct SafEntry {
    /// Full document URI: content://...
    id: String,
    /// Display name (filename)
    name: String,
    /// MIME type; MIME_DIRECTORY means it is a subdirectory
    #[serde(rename = "mimeType")]
    mime_type: String,
    /// File size in bytes; 0 for directories
    size: u64,
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

fn attach<'a>(vm: &'a JavaVM) -> Result<jni::AttachGuard<'a>, FilesystemError> {
    vm.attach_current_thread()
        .map_err(|e| FilesystemError::Jni(e.to_string()))
}

// ---------------------------------------------------------------------------
// Public SAF operations
// ---------------------------------------------------------------------------

/// List the immediate children of a SAF tree URI.
///
/// Children that are directories have `children: None`. The caller is
/// responsible for lazy-loading subdirectories when the user expands them.
pub fn list_children(tree_uri: &str) -> Result<Vec<FileNode>, FilesystemError> {
    debug!(uri = tree_uri, "SAF list_children");

    let vm = vm()?;
    let env = attach(vm)?;
    // Safety: env borrow scope is within this function.
    let mut env = unsafe { env.unsafe_clone() };

    let j_uri = env
        .new_string(tree_uri)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let result = env
        .call_static_method(
            BRIDGE_CLASS,
            "listChildren",
            "(Ljava/lang/String;)Ljava/lang/String;",
            &[JValue::Object(&j_uri)],
        )
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let j_obj = result
        .l()
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    if j_obj.is_null() {
        return Err(FilesystemError::SafQueryFailed {
            uri: tree_uri.to_string(),
            reason: "listChildren returned null — check SafBridge.init() was called".to_string(),
        });
    }

    let json: String = env
        .get_string(&JString::from(j_obj))
        .map_err(|e| FilesystemError::Jni(e.to_string()))?
        .into();

    let entries: Vec<SafEntry> = serde_json::from_str(&json).map_err(|e| {
        FilesystemError::SafQueryFailed {
            uri: tree_uri.to_string(),
            reason: format!("JSON parse error: {e}"),
        }
    })?;

    let nodes = entries
        .into_iter()
        .map(|e| {
            let kind = if e.mime_type == MIME_DIRECTORY {
                FileKind::Directory
            } else {
                FileKind::File
            };
            let size = if kind == FileKind::File {
                Some(e.size)
            } else {
                None
            };
            FileNode {
                name: e.name,
                path: e.id,
                kind,
                size,
                children: None,
            }
        })
        .collect();

    Ok(nodes)
}

/// Read the full byte contents of a SAF document URI.
pub fn read_file(document_uri: &str) -> Result<Vec<u8>, FilesystemError> {
    debug!(uri = document_uri, "SAF read_file");

    let vm = vm()?;
    let env = attach(vm)?;
    let mut env = unsafe { env.unsafe_clone() };

    let j_uri = env
        .new_string(document_uri)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let result = env
        .call_static_method(
            BRIDGE_CLASS,
            "readFile",
            "(Ljava/lang/String;)[B",
            &[JValue::Object(&j_uri)],
        )
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let j_obj = result
        .l()
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    if j_obj.is_null() {
        return Err(FilesystemError::NotFound {
            path: document_uri.to_string(),
        });
    }

    let bytes = env
        .convert_byte_array(&JByteArray::from(j_obj))
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    Ok(bytes)
}

/// Write data to a SAF document URI, replacing existing content entirely.
///
/// Uses open mode "wt" (write + truncate) via ContentResolver.openOutputStream.
pub fn write_file(document_uri: &str, data: &[u8]) -> Result<(), FilesystemError> {
    debug!(uri = document_uri, bytes = data.len(), "SAF write_file");

    let vm = vm()?;
    let env = attach(vm)?;
    let mut env = unsafe { env.unsafe_clone() };

    let j_uri = env
        .new_string(document_uri)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let j_data = env
        .byte_array_from_slice(data)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let result = env
        .call_static_method(
            BRIDGE_CLASS,
            "writeFile",
            "(Ljava/lang/String;[B)Z",
            &[JValue::Object(&j_uri), JValue::Object(&j_data)],
        )
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let ok = result
        .z()
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    if !ok {
        return Err(FilesystemError::WriteFailed {
            path: document_uri.to_string(),
        });
    }

    Ok(())
}

/// Create a new document (file or directory) inside a SAF tree.
///
/// Pass `mime_type = MIME_DIRECTORY` to create a subdirectory.
/// Returns the document URI of the newly created document.
pub fn create_document(
    parent_tree_uri: &str,
    display_name: &str,
    mime_type: &str,
) -> Result<String, FilesystemError> {
    debug!(
        parent = parent_tree_uri,
        name = display_name,
        mime = mime_type,
        "SAF create_document"
    );

    let vm = vm()?;
    let env = attach(vm)?;
    let mut env = unsafe { env.unsafe_clone() };

    let j_parent = env
        .new_string(parent_tree_uri)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;
    let j_name = env
        .new_string(display_name)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;
    let j_mime = env
        .new_string(mime_type)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let result = env
        .call_static_method(
            BRIDGE_CLASS,
            "createFile",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            &[
                JValue::Object(&j_parent),
                JValue::Object(&j_name),
                JValue::Object(&j_mime),
            ],
        )
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let j_obj = result
        .l()
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    if j_obj.is_null() {
        return Err(FilesystemError::SafCreateFailed {
            parent: parent_tree_uri.to_string(),
            name: display_name.to_string(),
        });
    }

    let uri: String = env
        .get_string(&JString::from(j_obj))
        .map_err(|e| FilesystemError::Jni(e.to_string()))?
        .into();

    Ok(uri)
}

/// Create a new subdirectory inside a SAF tree.
pub fn create_directory(parent_tree_uri: &str, display_name: &str) -> Result<String, FilesystemError> {
    create_document(parent_tree_uri, display_name, MIME_DIRECTORY)
}

/// Delete a SAF document (file or empty directory).
pub fn delete_document(document_uri: &str) -> Result<(), FilesystemError> {
    debug!(uri = document_uri, "SAF delete_document");

    let vm = vm()?;
    let env = attach(vm)?;
    let mut env = unsafe { env.unsafe_clone() };

    let j_uri = env
        .new_string(document_uri)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let result = env
        .call_static_method(
            BRIDGE_CLASS,
            "deleteDocument",
            "(Ljava/lang/String;)Z",
            &[JValue::Object(&j_uri)],
        )
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let ok = result
        .z()
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    if !ok {
        return Err(FilesystemError::DeleteFailed {
            path: document_uri.to_string(),
        });
    }

    Ok(())
}

/// Rename a SAF document.
///
/// Returns the new document URI — Android may assign a different URI after rename.
pub fn rename_document(document_uri: &str, new_name: &str) -> Result<String, FilesystemError> {
    debug!(uri = document_uri, new_name, "SAF rename_document");

    let vm = vm()?;
    let env = attach(vm)?;
    let mut env = unsafe { env.unsafe_clone() };

    let j_uri = env
        .new_string(document_uri)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;
    let j_name = env
        .new_string(new_name)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let result = env
        .call_static_method(
            BRIDGE_CLASS,
            "renameDocument",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            &[JValue::Object(&j_uri), JValue::Object(&j_name)],
        )
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let j_obj = result
        .l()
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    if j_obj.is_null() {
        return Err(FilesystemError::SafQueryFailed {
            uri: document_uri.to_string(),
            reason: "renameDocument returned null".to_string(),
        });
    }

    let uri: String = env
        .get_string(&JString::from(j_obj))
        .map_err(|e| FilesystemError::Jni(e.to_string()))?
        .into();

    Ok(uri)
}

/// Query the display name of a SAF document URI from the ContentResolver.
///
/// Falls back to the URI's last path segment if the ContentResolver query fails.
pub fn get_display_name(document_uri: &str) -> Result<String, FilesystemError> {
    debug!(uri = document_uri, "SAF get_display_name");

    let vm = vm()?;
    let env = attach(vm)?;
    let mut env = unsafe { env.unsafe_clone() };

    let j_uri = env
        .new_string(document_uri)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let result = env
        .call_static_method(
            BRIDGE_CLASS,
            "getDisplayName",
            "(Ljava/lang/String;)Ljava/lang/String;",
            &[JValue::Object(&j_uri)],
        )
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let j_obj = result
        .l()
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    if j_obj.is_null() {
        // Graceful fallback: use URI basename
        return Ok(crate::path::display_name(document_uri).to_string());
    }

    let name: String = env
        .get_string(&JString::from(j_obj))
        .map_err(|e| FilesystemError::Jni(e.to_string()))?
        .into();

    Ok(name)
}

/// Query the MIME type of a SAF document URI from the ContentResolver.
pub fn get_mime_type(document_uri: &str) -> Result<String, FilesystemError> {
    debug!(uri = document_uri, "SAF get_mime_type");

    let vm = vm()?;
    let env = attach(vm)?;
    let mut env = unsafe { env.unsafe_clone() };

    let j_uri = env
        .new_string(document_uri)
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let result = env
        .call_static_method(
            BRIDGE_CLASS,
            "getMimeType",
            "(Ljava/lang/String;)Ljava/lang/String;",
            &[JValue::Object(&j_uri)],
        )
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    let j_obj = result
        .l()
        .map_err(|e| FilesystemError::Jni(e.to_string()))?;

    if j_obj.is_null() {
        return Ok("application/octet-stream".to_string());
    }

    let mime: String = env
        .get_string(&JString::from(j_obj))
        .map_err(|e| FilesystemError::Jni(e.to_string()))?
        .into();

    Ok(mime)
}
