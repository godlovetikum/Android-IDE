/// android-ide/modules/settings/src/schema.rs
///
/// Typed settings schemas. All settings are serialized/deserialized as TOML.

use serde::{Deserialize, Serialize};

/// Root application settings. Serialized to $SETTINGS_DIR/android-ide/settings.toml
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppSettings {
    pub editor: EditorSettings,
    pub ui: UiSettings,
    /// Path (or SAF tree URI on Android) of the most recently opened project.
    /// Restored at startup to re-open the last session.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub last_project_path: Option<String>,
}

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            editor: EditorSettings::default(),
            ui: UiSettings::default(),
            last_project_path: None,
        }
    }
}

/// Editor-specific settings (Monaco configuration)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EditorSettings {
    pub font_size: u32,
    pub tab_size: u32,
    pub insert_spaces: bool,
    pub word_wrap: bool,
    pub auto_save: bool,
    pub auto_save_delay_ms: u32,
    pub theme: String,
}

impl Default for EditorSettings {
    fn default() -> Self {
        Self {
            font_size: 14,
            tab_size: 4,
            insert_spaces: true,
            word_wrap: false,
            auto_save: true,
            auto_save_delay_ms: 2000,
            theme: "vs-dark".to_string(),
        }
    }
}

/// UI-specific settings
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UiSettings {
    pub sidebar_visible: bool,
    pub terminal_panel_height: u32,
    pub color_theme: String,
}

impl Default for UiSettings {
    fn default() -> Self {
        Self {
            sidebar_visible: true,
            terminal_panel_height: 200,
            color_theme: "dark".to_string(),
        }
    }
}

/// Per-project settings. Serialized to $PROJECT_ROOT/.android-ide/settings.toml
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ProjectSettings {
    /// Override global editor settings for this project
    pub editor: Option<EditorSettings>,
    /// Git remote name to use for push/pull (default: "origin")
    pub git_remote: Option<String>,
    /// Language server overrides for this project
    pub lsp: Option<LspProjectSettings>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LspProjectSettings {
    /// Path to language server binary (overrides global LSP config)
    pub server_path: Option<String>,
    /// Extra arguments to pass to the language server
    pub extra_args: Vec<String>,
}
