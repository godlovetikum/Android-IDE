/// android-ide/modules/documentation/src/lib.rs
///
/// Documentation module — markdown rendering, in-app docs, README viewer.

pub mod error;

pub use error::DocumentationError;

pub type Result<T> = std::result::Result<T, DocumentationError>;

pub struct DocumentationManager;

impl DocumentationManager {
    pub fn new() -> Self { Self }
}
