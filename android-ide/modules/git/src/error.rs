use thiserror::Error;

#[derive(Debug, Error)]
pub enum GitError {
    #[error("Not a git repository: {path}")]
    NotARepository { path: String },
    #[error("Git operation failed: {0}")]
    Git(#[from] git2::Error),
    #[error("Authentication failed")]
    AuthFailed,
    #[error("Merge conflict in: {files}")]
    MergeConflict { files: String },
}
