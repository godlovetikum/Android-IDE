/// android-ide/modules/editor/src/tab.rs
///
/// EditorTab — represents a single open file in the editor.
///
/// Tab IDs use UUID v4 (random, 128-bit). The `uuid` crate is used instead of a
/// SystemTime-based stub because SystemTime is not monotonic and can produce
/// duplicate IDs when two files are opened within the same nanosecond.

use serde::{Deserialize, Serialize};

pub type TabId = String;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EditorTab {
    /// Unique identifier for this tab (UUID v4)
    pub id: TabId,
    /// Full path of the open file
    pub path: String,
    /// Display name (filename only)
    pub name: String,
    /// Whether the file has unsaved changes
    pub is_dirty: bool,
    /// Whether this tab is currently active (focused)
    pub is_active: bool,
    /// Language identifier for Monaco (e.g. "rust", "typescript", "python")
    pub language: String,
}

impl EditorTab {
    pub fn new(path: &str, language: &str) -> Self {
        let name = std::path::Path::new(path)
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or(path)
            .to_string();

        Self {
            id: uuid::Uuid::new_v4().to_string(),
            path: path.to_string(),
            name,
            is_dirty: false,
            is_active: false,
            language: language.to_string(),
        }
    }
}

/// Infer Monaco language identifier from file extension.
pub fn language_for_extension(ext: &str) -> &'static str {
    match ext {
        "rs" => "rust",
        "ts" | "tsx" => "typescript",
        "js" | "jsx" | "mjs" | "cjs" => "javascript",
        "py" => "python",
        "go" => "go",
        "c" | "h" => "c",
        "cpp" | "cc" | "cxx" | "hpp" => "cpp",
        "java" => "java",
        "kt" | "kts" => "kotlin",
        "swift" => "swift",
        "json" => "json",
        "yaml" | "yml" => "yaml",
        "toml" => "toml",
        "md" => "markdown",
        "html" | "htm" => "html",
        "css" => "css",
        "scss" => "scss",
        "sh" | "bash" | "zsh" => "shell",
        "sql" => "sql",
        "xml" => "xml",
        _ => "plaintext",
    }
}
