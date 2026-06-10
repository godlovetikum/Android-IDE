/// android-ide/modules/settings/src/lib.rs
///
/// Settings module — public API surface.
///
/// Provides typed TOML-based settings persistence for user preferences
/// and per-project configuration overrides.
///
/// Dependencies: None (foundation layer)

pub mod android;
pub mod error;
pub mod manager;
pub mod schema;

pub use error::SettingsError;
pub use manager::SettingsManager;
pub use schema::{AppSettings, EditorSettings, ProjectSettings};

pub type Result<T> = std::result::Result<T, SettingsError>;
