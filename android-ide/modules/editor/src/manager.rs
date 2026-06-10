/// android-ide/modules/editor/src/manager.rs
///
/// EditorManager — coordinates open tabs, file loads, cursor state, and
/// pending content for save operations.

use std::collections::HashMap;
use tracing::{debug, info};

use crate::error::EditorError;
use crate::tab::{EditorTab, TabId, language_for_extension};

pub struct EditorManager {
    tabs: HashMap<TabId, EditorTab>,
    active_tab_id: Option<TabId>,
    /// Content written by the editor but not yet persisted to disk.
    /// Key: TabId, Value: latest content string.
    /// Consumed by the filesystem module during save (task 019).
    pending_content: HashMap<TabId, String>,
    /// Most recent cursor position (line, column) — 1-based.
    cursor_line: u32,
    cursor_column: u32,
}

impl EditorManager {
    pub fn new() -> Self {
        info!("EditorManager initialized");
        Self {
            tabs: HashMap::new(),
            active_tab_id: None,
            pending_content: HashMap::new(),
            cursor_line: 1,
            cursor_column: 1,
        }
    }

    // -----------------------------------------------------------------------
    // Tab lifecycle
    // -----------------------------------------------------------------------

    /// Open a file in a new tab (or switch to existing tab if already open).
    /// Returns the TabId for the opened/existing tab.
    pub fn open_file(&mut self, path: &str) -> TabId {
        // Return existing tab if already open
        if let Some(existing) = self.tabs.values().find(|t| t.path == path) {
            let id = existing.id.clone();
            self.set_active(&id);
            return id;
        }

        let ext = std::path::Path::new(path)
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or("");
        let language = language_for_extension(ext);

        let mut tab = EditorTab::new(path, language);
        tab.is_active = true;

        // Deactivate previous tab
        if let Some(prev_id) = &self.active_tab_id {
            if let Some(prev) = self.tabs.get_mut(prev_id) {
                prev.is_active = false;
            }
        }

        let id = tab.id.clone();
        self.tabs.insert(id.clone(), tab);
        self.active_tab_id = Some(id.clone());

        debug!(path, "Opened file in new tab");
        id
    }

    /// Close a tab by ID. Pending content is discarded.
    pub fn close_tab(&mut self, tab_id: &TabId) -> Result<(), EditorError> {
        if !self.tabs.contains_key(tab_id) {
            return Err(EditorError::TabNotFound { id: tab_id.clone() });
        }
        self.tabs.remove(tab_id);
        self.pending_content.remove(tab_id);

        if self.active_tab_id.as_deref() == Some(tab_id.as_str()) {
            self.active_tab_id = self.tabs.keys().last().cloned();
        }

        Ok(())
    }

    // -----------------------------------------------------------------------
    // Dirty state
    // -----------------------------------------------------------------------

    /// Mark a tab as having unsaved changes.
    pub fn mark_dirty(&mut self, tab_id: &TabId) {
        if let Some(tab) = self.tabs.get_mut(tab_id) {
            tab.is_dirty = true;
        }
    }

    /// Mark a tab as clean (changes have been saved to disk).
    pub fn mark_clean(&mut self, tab_id: &TabId) {
        if let Some(tab) = self.tabs.get_mut(tab_id) {
            tab.is_dirty = false;
        }
        self.pending_content.remove(tab_id);
    }

    // -----------------------------------------------------------------------
    // Pending content (task 019 — save)
    // -----------------------------------------------------------------------

    /// Store the latest editor content for a tab, overwriting any previous pending content.
    /// Called by webview::handle_inbound_message when a contentChanged message arrives.
    pub fn set_pending_content(&mut self, tab_id: &TabId, content: String) {
        self.pending_content.insert(tab_id.clone(), content);
    }

    /// Take the pending content for a tab, removing it from the map.
    /// Returns None if there is no unsaved content.
    /// Called by the save operation (task 019).
    pub fn take_pending_content(&mut self, tab_id: &TabId) -> Option<String> {
        self.pending_content.remove(tab_id)
    }

    /// Returns true if the given tab has content pending save.
    pub fn has_pending_content(&self, tab_id: &TabId) -> bool {
        self.pending_content.contains_key(tab_id)
    }

    // -----------------------------------------------------------------------
    // Cursor state
    // -----------------------------------------------------------------------

    /// Update the cursor position. Called from contentChanged/cursorMoved messages.
    pub fn update_cursor(&mut self, line: u32, column: u32) {
        self.cursor_line = line;
        self.cursor_column = column;
    }

    /// Return the current cursor position (line, column), 1-based.
    pub fn cursor_position(&self) -> (u32, u32) {
        (self.cursor_line, self.cursor_column)
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    pub fn all_tabs(&self) -> Vec<&EditorTab> {
        let mut tabs: Vec<&EditorTab> = self.tabs.values().collect();
        tabs.sort_by_key(|t| &t.name);
        tabs
    }

    pub fn active_tab(&self) -> Option<&EditorTab> {
        self.active_tab_id.as_ref().and_then(|id| self.tabs.get(id))
    }

    pub fn tab_by_path(&self, path: &str) -> Option<&EditorTab> {
        self.tabs.values().find(|t| t.path == path)
    }

    pub fn any_dirty(&self) -> bool {
        self.tabs.values().any(|t| t.is_dirty)
    }

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------

    fn set_active(&mut self, tab_id: &TabId) {
        if let Some(prev_id) = &self.active_tab_id {
            if let Some(prev) = self.tabs.get_mut(prev_id) {
                prev.is_active = false;
            }
        }
        if let Some(tab) = self.tabs.get_mut(tab_id) {
            tab.is_active = true;
            self.active_tab_id = Some(tab_id.clone());
        }
    }
}
