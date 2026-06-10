/// android-ide/modules/filesystem/src/manager.rs
///
/// FilesystemManager — the primary interface for all filesystem operations.
///
/// Routing:
///   Android (cfg target_os = "android"):  delegates to saf::* functions
///   Desktop (all other targets):          uses std::fs directly
///
/// The SAF path on Android must be initialized before use:
///   1. JNI_OnLoad calls saf::init_vm(vm)
///   2. Activity.onCreate calls SafBridge.init(this) on the Java side
///   3. FilesystemManager operations can then proceed normally

use tracing::{debug, info};

use crate::error::FilesystemError;
use crate::tree::{FileKind, FileNode, FileTree};

pub struct FilesystemManager {
    /// Currently open project root (filesystem path on desktop; SAF tree URI on Android)
    project_root: Option<String>,
}

impl FilesystemManager {
    pub fn new() -> Result<Self, FilesystemError> {
        info!("FilesystemManager initialized");
        Ok(Self { project_root: None })
    }

    /// Open a project at the given path or SAF tree URI and return its file tree.
    pub fn open_project(&mut self, root_path: &str) -> Result<FileTree, FilesystemError> {
        info!(path = root_path, "Opening project");
        self.project_root = Some(root_path.to_string());
        self.build_tree(root_path)
    }

    /// Return the currently open project root, if any.
    pub fn project_root(&self) -> Option<&str> {
        self.project_root.as_deref()
    }

    /// Read the contents of a file as a UTF-8 string.
    pub fn read_file(&self, path: &str) -> Result<String, FilesystemError> {
        debug!(path, "read_file");

        #[cfg(not(target_os = "android"))]
        {
            std::fs::read_to_string(path).map_err(|e| match e.kind() {
                std::io::ErrorKind::NotFound => {
                    FilesystemError::NotFound { path: path.to_string() }
                }
                std::io::ErrorKind::PermissionDenied => {
                    FilesystemError::PermissionDenied { path: path.to_string() }
                }
                _ => FilesystemError::Io(e),
            })
        }

        #[cfg(target_os = "android")]
        {
            let bytes = crate::saf::read_file(path)?;
            String::from_utf8(bytes).map_err(|_| FilesystemError::NotAFile { path: path.to_string() })
        }
    }

    /// Read the raw bytes of a file.
    pub fn read_file_bytes(&self, path: &str) -> Result<Vec<u8>, FilesystemError> {
        debug!(path, "read_file_bytes");

        #[cfg(not(target_os = "android"))]
        {
            std::fs::read(path).map_err(|e| match e.kind() {
                std::io::ErrorKind::NotFound => {
                    FilesystemError::NotFound { path: path.to_string() }
                }
                std::io::ErrorKind::PermissionDenied => {
                    FilesystemError::PermissionDenied { path: path.to_string() }
                }
                _ => FilesystemError::Io(e),
            })
        }

        #[cfg(target_os = "android")]
        {
            crate::saf::read_file(path)
        }
    }

    /// Write a UTF-8 string to a file, replacing existing content.
    pub fn write_file(&self, path: &str, content: &str) -> Result<(), FilesystemError> {
        debug!(path, "write_file");

        #[cfg(not(target_os = "android"))]
        {
            std::fs::write(path, content).map_err(|e| match e.kind() {
                std::io::ErrorKind::PermissionDenied => {
                    FilesystemError::PermissionDenied { path: path.to_string() }
                }
                _ => FilesystemError::Io(e),
            })
        }

        #[cfg(target_os = "android")]
        {
            crate::saf::write_file(path, content.as_bytes())
        }
    }

    /// Write raw bytes to a file, replacing existing content.
    pub fn write_file_bytes(&self, path: &str, data: &[u8]) -> Result<(), FilesystemError> {
        debug!(path, bytes = data.len(), "write_file_bytes");

        #[cfg(not(target_os = "android"))]
        {
            std::fs::write(path, data).map_err(|e| match e.kind() {
                std::io::ErrorKind::PermissionDenied => {
                    FilesystemError::PermissionDenied { path: path.to_string() }
                }
                _ => FilesystemError::Io(e),
            })
        }

        #[cfg(target_os = "android")]
        {
            crate::saf::write_file(path, data)
        }
    }

    /// Create a new file at the given path.
    ///
    /// On Android, `parent_uri` is the SAF tree URI of the containing directory
    /// and `filename` is the display name. The returned path is the new document URI.
    ///
    /// On desktop, `parent_uri` is the directory path and `filename` is the name.
    pub fn create_file(
        &self,
        parent_path: &str,
        filename: &str,
        mime_type: Option<&str>,
    ) -> Result<String, FilesystemError> {
        debug!(parent = parent_path, name = filename, "create_file");

        #[cfg(not(target_os = "android"))]
        {
            let full_path = format!("{}/{}", parent_path.trim_end_matches('/'), filename);
            std::fs::File::create(&full_path)
                .map(|_| full_path)
                .map_err(FilesystemError::Io)
        }

        #[cfg(target_os = "android")]
        {
            let mime = mime_type.unwrap_or_else(|| {
                // Infer a reasonable MIME type from the extension, default to plain text
                match filename.rsplit('.').next() {
                    Some("rs") => "text/x-rust",
                    Some("kt") => "text/x-kotlin",
                    Some("java") => "text/x-java",
                    Some("py") => "text/x-python",
                    Some("js") | Some("mjs") => "text/javascript",
                    Some("ts") => "text/typescript",
                    Some("json") => "application/json",
                    Some("toml") => "application/toml",
                    Some("md") => "text/markdown",
                    Some("c") | Some("h") => "text/x-c",
                    Some("cpp") | Some("hpp") => "text/x-c++",
                    _ => "text/plain",
                }
            });
            crate::saf::create_document(parent_path, filename, mime)
        }
    }

    /// Create a new directory.
    pub fn create_directory(
        &self,
        parent_path: &str,
        name: &str,
    ) -> Result<String, FilesystemError> {
        debug!(parent = parent_path, name, "create_directory");

        #[cfg(not(target_os = "android"))]
        {
            let full_path = format!("{}/{}", parent_path.trim_end_matches('/'), name);
            std::fs::create_dir(&full_path)
                .map(|_| full_path)
                .map_err(FilesystemError::Io)
        }

        #[cfg(target_os = "android")]
        {
            crate::saf::create_directory(parent_path, name)
        }
    }

    /// Delete a file or empty directory at the given path.
    pub fn delete(&self, path: &str) -> Result<(), FilesystemError> {
        debug!(path, "delete");

        #[cfg(not(target_os = "android"))]
        {
            let metadata = std::fs::metadata(path)
                .map_err(|_| FilesystemError::NotFound { path: path.to_string() })?;
            if metadata.is_dir() {
                std::fs::remove_dir(path).map_err(FilesystemError::Io)
            } else {
                std::fs::remove_file(path).map_err(FilesystemError::Io)
            }
        }

        #[cfg(target_os = "android")]
        {
            crate::saf::delete_document(path)
        }
    }

    /// Rename a file or directory.
    ///
    /// Returns the new path or URI (Android may issue a new URI after rename).
    pub fn rename(&self, path: &str, new_name: &str) -> Result<String, FilesystemError> {
        debug!(path, new_name, "rename");

        #[cfg(not(target_os = "android"))]
        {
            let parent = std::path::Path::new(path)
                .parent()
                .and_then(|p| p.to_str())
                .unwrap_or(".");
            let new_path = format!("{}/{}", parent, new_name);
            std::fs::rename(path, &new_path)
                .map(|_| new_path)
                .map_err(FilesystemError::Io)
        }

        #[cfg(target_os = "android")]
        {
            crate::saf::rename_document(path, new_name)
        }
    }

    // ---------------------------------------------------------------------------
    // Tree building
    // ---------------------------------------------------------------------------

    fn build_tree(&self, root_path: &str) -> Result<FileTree, FilesystemError> {
        let root_node = self.build_node(root_path)?;
        Ok(FileTree::new(root_node))
    }

    fn build_node(&self, path: &str) -> Result<FileNode, FilesystemError> {
        #[cfg(not(target_os = "android"))]
        {
            let metadata = std::fs::metadata(path)
                .map_err(|_| FilesystemError::NotFound { path: path.to_string() })?;

            let name = std::path::Path::new(path)
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or(path)
                .to_string();

            if metadata.is_dir() {
                let mut children = Vec::new();
                if let Ok(entries) = std::fs::read_dir(path) {
                    for entry in entries.flatten() {
                        let entry_path = entry.path().to_string_lossy().to_string();
                        if let Ok(child) = self.build_node(&entry_path) {
                            children.push(child);
                        }
                    }
                    children.sort_by(|a, b| match (&a.kind, &b.kind) {
                        (FileKind::Directory, FileKind::File) => std::cmp::Ordering::Less,
                        (FileKind::File, FileKind::Directory) => std::cmp::Ordering::Greater,
                        _ => a.name.cmp(&b.name),
                    });
                }
                Ok(FileNode {
                    name,
                    path: path.to_string(),
                    kind: FileKind::Directory,
                    size: None,
                    children: Some(children),
                })
            } else {
                Ok(FileNode {
                    name,
                    path: path.to_string(),
                    kind: FileKind::File,
                    size: Some(metadata.len()),
                    children: None,
                })
            }
        }

        #[cfg(target_os = "android")]
        {
            // On Android the root_path is a SAF tree URI.
            // We list immediate children and return the root node with shallow children.
            // Deeper levels are loaded lazily via list_children() when the user expands a dir.
            let name = crate::saf::get_display_name(path).unwrap_or_else(|_| {
                crate::path::display_name(path).to_string()
            });
            let children = crate::saf::list_children(path)?;
            Ok(FileNode {
                name,
                path: path.to_string(),
                kind: FileKind::Directory,
                size: None,
                children: Some(children),
            })
        }
    }

    /// Expand a directory node lazily (Android only — list its SAF children).
    /// On desktop this is a no-op because build_node is already eager.
    pub fn expand_directory(&self, dir_path: &str) -> Result<Vec<FileNode>, FilesystemError> {
        debug!(path = dir_path, "expand_directory");

        #[cfg(not(target_os = "android"))]
        {
            let node = self.build_node(dir_path)?;
            Ok(node.children.unwrap_or_default())
        }

        #[cfg(target_os = "android")]
        {
            crate::saf::list_children(dir_path)
        }
    }
}
