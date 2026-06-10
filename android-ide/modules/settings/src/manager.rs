/// android-ide/modules/settings/src/manager.rs
///
/// SettingsManager — load, save, and access typed settings.

use tracing::{debug, info, warn};

use crate::error::SettingsError;
use crate::schema::{AppSettings, ProjectSettings};

pub struct SettingsManager {
    app_settings: AppSettings,
    settings_path: Option<std::path::PathBuf>,
}

impl SettingsManager {
    /// Create a new SettingsManager. Attempts to load settings from disk.
    /// Falls back to defaults if no settings file exists.
    pub fn new() -> Result<Self, SettingsError> {
        let settings_path = Self::resolve_settings_path();

        let app_settings = if let Some(path) = &settings_path {
            if path.exists() {
                let content = std::fs::read_to_string(path)?;
                match toml::from_str::<AppSettings>(&content) {
                    Ok(s) => {
                        info!(path = %path.display(), "Loaded settings from disk");
                        s
                    }
                    Err(e) => {
                        warn!("Failed to parse settings file, using defaults: {e}");
                        AppSettings::default()
                    }
                }
            } else {
                debug!("No settings file found, using defaults");
                AppSettings::default()
            }
        } else {
            warn!("Could not determine settings path, using defaults");
            AppSettings::default()
        };

        Ok(Self { app_settings, settings_path })
    }

    // -----------------------------------------------------------------------
    // Read access
    // -----------------------------------------------------------------------

    /// Get a reference to the current application settings.
    pub fn get(&self) -> &AppSettings {
        &self.app_settings
    }

    /// Alias for `get()` — kept for call sites that prefer the explicit name.
    pub fn app_settings(&self) -> &AppSettings {
        &self.app_settings
    }

    /// Return the last opened project path (or SAF URI on Android), if any.
    pub fn last_project_path(&self) -> Option<&str> {
        self.app_settings.last_project_path.as_deref()
    }

    // -----------------------------------------------------------------------
    // Mutation
    // -----------------------------------------------------------------------

    /// Store the path of the most recently opened project and persist to disk.
    pub fn set_last_project_path(&mut self, path: &str) {
        self.app_settings.last_project_path = Some(path.to_string());
    }

    /// Persist current in-memory settings to disk.
    pub fn save(&self) -> Result<(), SettingsError> {
        self.persist()
    }

    /// Update and persist application settings.
    pub fn save_app_settings(&mut self, settings: AppSettings) -> Result<(), SettingsError> {
        self.app_settings = settings;
        self.persist()
    }

    /// Load per-project settings from the project root.
    pub fn load_project_settings(&self, project_root: &str) -> ProjectSettings {
        let project_settings_path = std::path::Path::new(project_root)
            .join(".android-ide")
            .join("settings.toml");

        if project_settings_path.exists() {
            match std::fs::read_to_string(&project_settings_path)
                .ok()
                .and_then(|s| toml::from_str::<ProjectSettings>(&s).ok())
            {
                Some(s) => {
                    info!(path = %project_settings_path.display(), "Loaded project settings");
                    s
                }
                None => {
                    warn!("Failed to parse project settings, using defaults");
                    ProjectSettings::default()
                }
            }
        } else {
            ProjectSettings::default()
        }
    }

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------

    fn persist(&self) -> Result<(), SettingsError> {
        if let Some(path) = &self.settings_path {
            if let Some(parent) = path.parent() {
                std::fs::create_dir_all(parent)?;
            }
            let content = toml::to_string_pretty(&self.app_settings)?;
            std::fs::write(path, content)?;
            debug!(path = %path.display(), "Saved settings to disk");
            Ok(())
        } else {
            Err(SettingsError::StorageUnavailable)
        }
    }

    fn resolve_settings_path() -> Option<std::path::PathBuf> {
        #[cfg(target_os = "android")]
        {
            // Populated by init_android_files_dir() called from the Activity
            // via Java_dev_androidide_MainActivity_nativeSetFilesDir JNI export.
            crate::android::files_dir()
                .map(|d| std::path::PathBuf::from(d).join("settings.toml"))
        }

        #[cfg(not(target_os = "android"))]
        {
            dirs::config_dir()
                .map(|d| d.join("android-ide").join("settings.toml"))
        }
    }
}
