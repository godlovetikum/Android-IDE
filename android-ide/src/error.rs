/// android-ide/src/error.rs
///
/// Top-level error type for the Android IDE application.
/// Subsystem errors are defined in their respective modules.

use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error("Settings error: {0}")]
    Settings(#[from] android_ide_settings::SettingsError),

    #[error("Filesystem error: {0}")]
    Filesystem(#[from] android_ide_filesystem::FilesystemError),

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}
