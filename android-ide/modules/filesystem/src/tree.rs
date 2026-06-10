/// android-ide/modules/filesystem/src/tree.rs
///
/// File tree data structures.
/// Represents a project directory as a tree of FileNode entries.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum FileKind {
    File,
    Directory,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileNode {
    /// Display name of the file or directory
    pub name: String,
    /// Full path (platform-specific: SAF URI on Android, filesystem path on desktop)
    pub path: String,
    /// Whether this is a file or directory
    pub kind: FileKind,
    /// File size in bytes (None for directories)
    pub size: Option<u64>,
    /// Children (populated lazily for directories)
    pub children: Option<Vec<FileNode>>,
}

#[derive(Debug, Clone)]
pub struct FileTree {
    /// Root node of the project directory
    pub root: FileNode,
}

impl FileTree {
    pub fn new(root: FileNode) -> Self {
        Self { root }
    }

    /// Find a node by path
    pub fn find(&self, path: &str) -> Option<&FileNode> {
        Self::find_in(&self.root, path)
    }

    fn find_in<'a>(node: &'a FileNode, path: &str) -> Option<&'a FileNode> {
        if node.path == path {
            return Some(node);
        }
        if let Some(children) = &node.children {
            for child in children {
                if let Some(found) = Self::find_in(child, path) {
                    return Some(found);
                }
            }
        }
        None
    }
}
