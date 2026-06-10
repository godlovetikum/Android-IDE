/// android-ide/src/main.rs
///
/// Application entry point for the Android IDE.
/// On Android this is invoked via the JNI bridge (see android/ directory).
/// On desktop (for development) this launches the Slint window directly.

use anyhow::Result;
use tracing::{info, Level};
use tracing_subscriber::FmtSubscriber;

fn main() -> Result<()> {
    // Initialize logging
    let subscriber = FmtSubscriber::builder()
        .with_max_level(Level::DEBUG)
        .finish();
    tracing::subscriber::set_global_default(subscriber)
        .expect("Failed to set global tracing subscriber");

    info!("Android IDE starting");

    // Initialize settings (must be first — other modules depend on it)
    let _settings = android_ide_lib::init_settings()?;

    info!("Settings initialized");

    // Initialize filesystem module
    let _fs = android_ide_lib::init_filesystem()?;

    info!("Filesystem module initialized");

    // Launch UI
    android_ide_lib::run_ui()?;

    Ok(())
}
