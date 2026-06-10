use thiserror::Error;

#[derive(Debug, Error)]
pub enum LspError {
    #[error("Language server not running for language: {language}")]
    ServerNotRunning { language: String },
    #[error("JSON-RPC error: {0}")]
    JsonRpc(String),
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),
}
