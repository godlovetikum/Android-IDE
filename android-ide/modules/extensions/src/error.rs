use thiserror::Error;

#[derive(Debug, Error)]
pub enum ExtensionError {
    #[error("Extension not found: {name}")]
    NotFound { name: String },
    #[error("Permission denied for extension: {name}")]
    PermissionDenied { name: String },
    #[error("Load failed: {reason}")]
    LoadFailed { reason: String },
}
