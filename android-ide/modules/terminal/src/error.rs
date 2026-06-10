use thiserror::Error;

#[derive(Debug, Error)]
pub enum TerminalError {
    #[error("PTY creation failed: {0}")]
    PtyFailed(String),
    #[error("Session not found: {id}")]
    SessionNotFound { id: String },
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}
