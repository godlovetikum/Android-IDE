use thiserror::Error;

#[derive(Debug, Error)]
pub enum LinuxRuntimeError {
    #[error("Runtime not initialized")]
    NotInitialized,
    #[error("proot not found at path: {path}")]
    ProotNotFound { path: String },
    #[error("Environment bootstrap failed: {reason}")]
    BootstrapFailed { reason: String },
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}
