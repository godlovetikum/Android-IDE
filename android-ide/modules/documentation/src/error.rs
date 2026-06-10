use thiserror::Error;

#[derive(Debug, Error)]
pub enum DocumentationError {
    #[error("Document not found: {path}")]
    NotFound { path: String },
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}
