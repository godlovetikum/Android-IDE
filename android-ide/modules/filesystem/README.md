# filesystem module

## Purpose

Bridges Android Storage Access Framework (SAF) to the rest of the IDE. Provides all file system access — directory trees, file CRUD, and change detection — on both Android and desktop development builds.

## Responsibilities

- SAF URI resolution and ContentResolver access (Android)
- Direct `std::fs` access for desktop development builds
- Directory tree construction and lazy loading
- File read/write/create/rename/delete
- Project root management

## Public API

```rust
use android_ide_filesystem::{FilesystemManager, FileTree, FileNode, FileKind};

let mut fs = FilesystemManager::new()?;

// Open a project (path on desktop; SAF tree URI on Android)
let tree = fs.open_project("/path/to/project")?;

// File CRUD
let text   = fs.read_file("/path/to/file.rs")?;
let bytes  = fs.read_file_bytes("/path/to/image.png")?;
fs.write_file("/path/to/file.rs", "new content")?;
fs.write_file_bytes("/path/to/image.png", &data)?;

let new_path = fs.create_file("/path/to/dir", "Main.kt", Some("text/x-kotlin"))?;
let new_dir  = fs.create_directory("/path/to/dir", "subpackage")?;
let new_path = fs.rename("/path/to/old.kt", "new.kt")?;
fs.delete("/path/to/file.kt")?;

// Lazy directory expansion (used by the file tree widget)
let children = fs.expand_directory("/path/to/dir")?;
```

## Android SAF — Initialization Sequence

The SAF bridge requires a two-step initialization before any operations:

1. **JNI_OnLoad** (automatic, called when the `.so` is loaded):
   Calls `android_ide_filesystem::saf::init_vm(vm)` to store the `JavaVM` globally.

2. **Activity.onCreate()** (Java side):
   Calls `SafBridge.init(this)` to store the `ContentResolver` context.

Both steps must complete before any `FilesystemManager` operation is called on Android.

```java
// MainActivity.java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    SafBridge.init(this);      // step 2 — must come before nativeStart
    nativeStart();             // calls into Rust
}
```

## Android SAF — URI Shapes

| Type | Shape |
|------|-------|
| Tree URI (directory) | `content://com.android.externalstorage.documents/tree/primary%3AMyProject` |
| Document URI (file) | `content://com.android.externalstorage.documents/document/primary%3AMyProject%2FMain.kt` |

`FilesystemManager` accepts and returns both URI types. After a `rename()` on Android the returned URI may differ from the input — always use the returned value for subsequent operations.

## Module Structure

| File | Purpose |
|------|---------|
| `src/lib.rs` | Public API surface and module exports |
| `src/manager.rs` | `FilesystemManager` — main entry point; routes to std::fs or SAF |
| `src/saf.rs` | Android SAF JNI bridge — wraps `dev.androidide.SafBridge` Java class |
| `src/tree.rs` | `FileTree`, `FileNode`, `FileKind` data structures |
| `src/error.rs` | `FilesystemError` enum |
| `src/path.rs` | Platform-aware path utilities (SAF URI vs filesystem path detection) |

Java companion:

| File | Purpose |
|------|---------|
| `android/java/dev/androidide/SafBridge.java` | ContentResolver wrapper; all SAF I/O goes through here |

## Dependencies

| Crate | Target | Justification |
|-------|--------|---------------|
| `serde` / `serde_json` | all | JSON deserialization of `listChildren` response from SafBridge |
| `thiserror` | all | Ergonomic error type derivation |
| `tracing` | all | Structured debug logging |
| `jni = "0.21"` | Android only | Required for all Java interop on Android; no alternative exists |

## Known Limitations

- File watching (inotify / FileObserver) is not yet implemented.
- `expand_directory` on Android does one ContentResolver round-trip per call. Bulk tree loading for large projects is a future optimization.
- SAF does not support atomic renames across different document providers.
