/// android-ide/src/ui.rs
///
/// Binding layer between the Slint-generated MainWindow and the IDE subsystems.
///
/// Responsibilities:
/// - Initialise FilesystemManager, EditorManager, and SettingsManager
/// - Register the file-save handler (task 019)
/// - Wire all Slint callbacks to filesystem / editor / settings operations
/// - Manage the open-tabs model in the Slint window
/// - Drive the auto-save timer (Slint Timer, main-thread)
/// - Translate FilesystemManager results into Slint model types
///
/// Threading model:
///   FilesystemManager and EditorManager are wrapped in Arc<Mutex<>> so the
///   save handler (registered via webview::register_save_handler) can be
///   Send+Sync, enabling it to be called from the Android JNI background thread.
///   All Slint callbacks run on the main event-loop thread.
///
/// Android initialization contract:
///   `run_ui_loop()` MUST be called after `slint::android::init(app)` on Android.
///   This is guaranteed by `android_main()` in src/lib.rs, which calls
///   `slint::android::init()` before `crate::run_ui()`. Never call run_ui_loop()
///   from a JNI function — only from android_main() after Slint is initialized.
///
///   Platform-specific behaviour at runtime:
///   - `MainWindow::new()`: works on both platforms after Slint init
///   - `window.run()`: blocks on desktop; integrates with NativeActivity looper on Android
///   - WebView (wry): desktop only — gated with #[cfg(not(target_os = "android"))]
///   - `send_to_editor()`: Android WebView path — gated with #[cfg(target_os = "android")]

slint::include_modules!();

use std::rc::Rc;
use std::cell::RefCell;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use slint::{ModelRc, SharedString, VecModel};
use tracing::{error, info, warn};

use android_ide_filesystem::{FilesystemManager, FileKind, FileNode};
use android_ide_editor::{EditorManager, TabId};
use android_ide_editor::bridge::EditorOutbound;
use android_ide_editor::webview;
use android_ide_settings::SettingsManager;

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

/// Create the MainWindow, wire all callbacks, and run the Slint event loop.
/// Blocks until the window is closed.
pub fn run_ui_loop() -> anyhow::Result<()> {
    // Shared subsystem instances — Arc<Mutex<>> so the save handler can be Send.
    let fs     = Arc::new(Mutex::new(FilesystemManager::new()?));
    let editor = Arc::new(Mutex::new(EditorManager::new()));

    // --- Register save handler (task 019) ------------------------------------
    // Runs on possibly a background thread (Android JNI) or main thread (desktop).
    // On success: mark_clean is already called by handle_inbound_message before
    // this handler is invoked. On failure: re-dirty the tab and re-buffer content.
    {
        let fs_save     = Arc::clone(&fs);
        let editor_save = Arc::clone(&editor);

        webview::register_save_handler(move |path, content| {
            match fs_save.lock() {
                Ok(fs_guard) => match fs_guard.write_file(&path, &content) {
                    Ok(_) => info!(path, "File saved"),
                    Err(e) => {
                        error!(path, "Save failed: {e}");
                        // Put the content back so the next save cycle can retry
                        if let Ok(mut mgr) = editor_save.lock() {
                            if let Some(tab) = mgr.tab_by_path(&path).cloned() {
                                mgr.mark_dirty(&tab.id);
                                mgr.set_pending_content(&tab.id, content);
                            }
                        }
                    }
                },
                Err(e) => error!("FilesystemManager lock poisoned in save handler: {e}"),
            }
        });
    }

    let window = MainWindow::new()?;
    let settings = SettingsManager::new()?;
    let app = Rc::new(RefCell::new(AppState::new(
        window.as_weak(),
        Arc::clone(&fs),
        Arc::clone(&editor),
        settings,
    )));

    wire_callbacks(&window, Rc::clone(&app));

    // --- Cursor position callback (task 021) ---------------------------------
    // On Android this fires on the WebView background thread; invoke_from_event_loop
    // schedules the Slint property update onto the main event-loop thread.
    // On desktop (wry) the IPC callback is already main-thread, but
    // invoke_from_event_loop is safe to call from any thread.
    {
        let window_weak = window.as_weak();
        webview::register_cursor_handler(move |line, col| {
            let w = window_weak.clone();
            let _ = slint::invoke_from_event_loop(move || {
                if let Some(win) = w.upgrade() {
                    win.set_cursor_line(line as i32);
                    win.set_cursor_col(col as i32);
                }
            });
        });
    }

    // --- Auto-save timer (task 019) ------------------------------------------
    // Runs every auto_save_delay_ms on the main thread.
    // Checks each dirty tab for pending content and triggers a save via the
    // registered save handler through the Monaco bridge.
    {
        let delay_ms = app.borrow().settings.get().editor.auto_save_delay_ms;
        let fs_auto  = Arc::clone(&fs);
        let ed_auto  = Arc::clone(&editor);

        let timer = slint::Timer::default();
        timer.start(
            slint::TimerMode::Repeated,
            Duration::from_millis(delay_ms as u64),
            move || {
                auto_save_tick(&fs_auto, &ed_auto);
            },
        );
        // Keep timer alive for the session — leak intentionally (single process).
        std::mem::forget(timer);
    }

    // Restore settings into the window
    app.borrow_mut().restore_ui_state(&window);

    window.run()?;
    Ok(())
}

/// Called by the auto-save timer every `auto_save_delay_ms`.
fn auto_save_tick(fs: &Arc<Mutex<FilesystemManager>>, editor: &Arc<Mutex<EditorManager>>) {
    let mut to_save: Vec<(TabId, String /*path*/, String /*content*/)> = Vec::new();

    {
        let mut mgr = match editor.lock() {
            Ok(g) => g,
            Err(e) => { error!("auto_save_tick: EditorManager lock poisoned: {e}"); return; }
        };

        // Collect dirty tabs into owned values BEFORE taking mutable borrows.
        // `all_tabs()` returns `Vec<&EditorTab>`; `.into_iter().cloned()` produces
        // owned `EditorTab` values, releasing the immutable borrow of `mgr`.
        let dirty: Vec<android_ide_editor::EditorTab> = mgr.all_tabs()
            .into_iter()
            .filter(|t| t.is_dirty)
            .cloned()
            .collect();

        for tab in dirty {
            if let Some(content) = mgr.take_pending_content(&tab.id) {
                mgr.mark_clean(&tab.id);
                to_save.push((tab.id.clone(), tab.path.clone(), content));
            }
        }
    }

    if to_save.is_empty() { return; }

    match fs.lock() {
        Ok(fs_guard) => {
            for (tab_id, path, content) in &to_save {
                match fs_guard.write_file(path, content) {
                    Ok(_) => info!(path, "Auto-saved"),
                    Err(e) => {
                        error!(path, "Auto-save failed: {e}");
                        if let Ok(mut mgr) = editor.lock() {
                            mgr.mark_dirty(tab_id);
                            mgr.set_pending_content(tab_id, content.clone());
                        }
                    }
                }
            }
        }
        Err(e) => error!("auto_save_tick: FilesystemManager lock poisoned: {e}"),
    }
}

// ---------------------------------------------------------------------------
// Application state
// ---------------------------------------------------------------------------

struct AppState {
    fs:       Arc<Mutex<FilesystemManager>>,
    editor:   Arc<Mutex<EditorManager>>,
    settings: SettingsManager,
    window:   slint::Weak<MainWindow>,
}

impl AppState {
    fn new(
        window:   slint::Weak<MainWindow>,
        fs:       Arc<Mutex<FilesystemManager>>,
        editor:   Arc<Mutex<EditorManager>>,
        settings: SettingsManager,
    ) -> Self {
        Self { fs, editor, settings, window }
    }

    fn restore_ui_state(&mut self, window: &MainWindow) {
        let app_settings = self.settings.get();
        window.set_sidebar_visible(app_settings.ui.sidebar_visible);

        if let Some(last_project) = self.settings.last_project_path() {
            info!(path = last_project, "Restoring last project");
            self.open_project(window, &last_project);
        }
    }

    // -----------------------------------------------------------------------
    // Project
    // -----------------------------------------------------------------------

    fn open_project(&mut self, window: &MainWindow, path: &str) {
        let tree = match self.fs.lock() {
            Ok(mut g) => match g.open_project(path) {
                Ok(t) => t,
                Err(e) => { error!("Failed to open project at {path}: {e}"); return; }
            },
            Err(e) => { error!("FilesystemManager poisoned: {e}"); return; }
        };

        info!(path, "Project opened");

        let project_name = std::path::Path::new(path)
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or(path)
            .to_string();

        window.set_project_name(project_name.into());

        let entries = flatten_tree(&tree.root, 0);
        let model: Rc<VecModel<FileTreeEntry>> = Rc::new(VecModel::from(entries));
        window.set_file_tree_entries(ModelRc::from(model));

        self.settings.set_last_project_path(path);
        let _ = self.settings.save();
    }

    fn create_project(&mut self, window: &MainWindow, name: &str, parent_path: &str, project_type: &str) {
        info!(name, parent_path, project_type, "Creating new project");

        let project_path = match self.fs.lock() {
            Ok(g) => match g.create_directory(parent_path, name) {
                Ok(p) => p,
                Err(e) => { error!("Failed to create project directory: {e}"); return; }
            },
            Err(e) => { error!("FilesystemManager poisoned: {e}"); return; }
        };

        if let Err(e) = self.scaffold_project(&project_path, name, project_type) {
            error!("Scaffold failed: {e}");
        }

        self.open_project(window, &project_path);
    }

    fn scaffold_project(&mut self, root: &str, name: &str, project_type: &str) -> anyhow::Result<()> {
        let fs = self.fs.lock().map_err(|e| anyhow::anyhow!("FS lock: {e}"))?;

        match project_type {
            "Rust" => {
                let cargo_toml = format!(
                    "[package]\nname = \"{}\"\nversion = \"0.1.0\"\nedition = \"2021\"\n\n[dependencies]\n",
                    name.to_lowercase().replace(' ', "-")
                );
                let src_dir = fs.create_directory(root, "src")?;
                let main_rs = "fn main() {\n    println!(\"Hello, world!\");\n}\n";
                fs.create_file(root, "Cargo.toml", Some("application/toml"))?;
                fs.write_file(&format!("{root}/Cargo.toml"), &cargo_toml)?;
                fs.create_file(&src_dir, "main.rs", Some("text/x-rust"))?;
                fs.write_file(&format!("{src_dir}/main.rs"), main_rs)?;
            }
            "Kotlin (Android)" => {
                let src = format!("fun main() {{\n    println(\"Hello from {name}!\")\n}}\n");
                fs.create_file(root, "Main.kt", Some("text/x-kotlin"))?;
                fs.write_file(&format!("{root}/Main.kt"), &src)?;
            }
            "Python" => {
                let src = format!("# {name}\n\ndef main():\n    print(\"Hello, world!\")\n\nif __name__ == \"__main__\":\n    main()\n");
                fs.create_file(root, "main.py", Some("text/x-python"))?;
                fs.write_file(&format!("{root}/main.py"), &src)?;
            }
            "C/C++" => {
                let src = "#include <stdio.h>\n\nint main(void) {\n    printf(\"Hello, world!\\n\");\n    return 0;\n}\n";
                fs.create_file(root, "main.c", Some("text/x-c"))?;
                fs.write_file(&format!("{root}/main.c"), src)?;
            }
            _ => {
                let readme = format!("# {name}\n\nA new project.\n");
                fs.create_file(root, "README.md", Some("text/markdown"))?;
                fs.write_file(&format!("{root}/README.md"), &readme)?;
            }
        }
        Ok(())
    }

    // -----------------------------------------------------------------------
    // Tab management (task 018)
    // -----------------------------------------------------------------------

    /// Open a file: register it in EditorManager, read content, send LoadFile
    /// to Monaco, update the Slint tab bar model.
    fn open_file(&mut self, window: &MainWindow, path: &str) {
        // Read file content
        let content = match self.fs.lock() {
            Ok(g) => match g.read_file(path) {
                Ok(c) => c,
                Err(e) => { error!("Failed to read file {path}: {e}"); return; }
            },
            Err(e) => { error!("FS lock poisoned: {e}"); return; }
        };

        // Register in EditorManager
        let tab_id = match self.editor.lock() {
            Ok(mut mgr) => mgr.open_file(path),
            Err(e) => { error!("Editor lock poisoned: {e}"); return; }
        };

        // Detect language
        let ext = std::path::Path::new(path)
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or("");
        let language = android_ide_editor::tab::language_for_extension(ext);

        // Update active file / language in status bar
        let filename = std::path::Path::new(path)
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or(path)
            .to_string();

        window.set_active_file_path(filename.clone().into());
        window.set_active_language(language_display(ext).into());

        // Send LoadFile to Monaco
        let msg = EditorOutbound::LoadFile {
            path: path.to_string(),
            content,
            language: language.to_string(),
        };

        // Desktop: wry WebView handle is stored in AppState in a real implementation;
        // for now call the platform-agnostic send_to_editor (Android path).
        // Task 017 follow-up: integrate wry WebView into AppState for desktop.
        #[cfg(target_os = "android")]
        if let Err(e) = webview::send_to_editor(&msg) {
            error!("send_to_editor failed: {e}");
        }

        // Rebuild tab bar model
        self.rebuild_tab_model(window);
        _ = tab_id;
    }

    /// Switch the active tab.
    fn switch_tab(&mut self, window: &MainWindow, tab_id: &str) {
        let (path, language_ext) = {
            match self.editor.lock() {
                Ok(mgr) => {
                    if let Some(tab) = mgr.all_tabs().iter().find(|t| t.id == tab_id).cloned() {
                        (tab.path.clone(), tab.language.clone())
                    } else {
                        warn!("switch_tab: tab {tab_id} not found");
                        return;
                    }
                }
                Err(e) => { error!("Editor lock: {e}"); return; }
            }
        };

        // Re-open the file path (open_file handles the already-open case)
        self.open_file(window, &path);
        _ = language_ext;
    }

    /// Close a tab. If dirty, save first (best-effort auto-save on close).
    fn close_tab(&mut self, window: &MainWindow, tab_id: &str) {
        // Collect owned tab info before any mutable borrow.
        let (path, content) = {
            match self.editor.lock() {
                Ok(mut mgr) => {
                    // `into_iter().cloned()` releases the immutable borrow before
                    // `take_pending_content` takes a mutable one.
                    let tab_opt: Option<android_ide_editor::EditorTab> = mgr.all_tabs()
                        .into_iter()
                        .find(|t| t.id == tab_id)
                        .cloned();
                    match tab_opt {
                        Some(t) => {
                            let content = mgr.take_pending_content(&t.id);
                            (t.path.clone(), content)
                        }
                        None => return,
                    }
                }
                Err(e) => { error!("Editor lock: {e}"); return; }
            }
        };

        // Write any unsaved content to disk before closing
        if let Some(content) = content {
            if let Ok(fs) = self.fs.lock() {
                if let Err(e) = fs.write_file(&path, &content) {
                    error!("Save-on-close failed for {path}: {e}");
                }
            }
        }

        // Remove from EditorManager
        if let Ok(mut mgr) = self.editor.lock() {
            if let Err(e) = mgr.close_tab(&tab_id.to_string()) {
                error!("close_tab: {e}");
            }

            // Clone the active tab info before any mutable operations.
            let active_info: Option<(String /*path*/, String /*language*/)> = mgr
                .active_tab()
                .map(|t| (t.path.clone(), t.language.clone()));
            drop(mgr); // release lock before calling close_tab

            if let Some((active_path, active_lang)) = active_info {
                {
                let ext = std::path::Path::new(&active_path)
                    .extension()
                    .and_then(|e| e.to_str())
                    .unwrap_or("");
                let filename = std::path::Path::new(&active_path)
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or(&active_path)
                    .to_string();
                window.set_active_file_path(filename.into());
                window.set_active_language(language_display(ext).into());
                }

                let content = self.fs.lock().ok().and_then(|fs| fs.read_file(&active_path).ok());
                if let Some(c) = content {
                    let msg = EditorOutbound::LoadFile {
                        path: active_path,
                        content: c,
                        language: active_lang,
                    };
                    #[cfg(target_os = "android")]
                    if let Err(e) = webview::send_to_editor(&msg) {
                        error!("send_to_editor on close: {e}");
                    }
                }
            } else {
                window.set_active_file_path("".into());
                window.set_active_language("".into());
            }
        }

        // Tell Monaco to close the tab
        let close_msg = EditorOutbound::CloseTab { path: path.clone() };
        #[cfg(target_os = "android")]
        if let Err(e) = webview::send_to_editor(&close_msg) {
            error!("send CloseTab: {e}");
        }

        self.rebuild_tab_model(window);
    }

    /// Rebuild the `open-tabs` model in the Slint window from the EditorManager state.
    fn rebuild_tab_model(&self, window: &MainWindow) {
        let tabs: Vec<TabEntry> = match self.editor.lock() {
            Ok(mgr) => {
                let mut sorted = mgr.all_tabs().into_iter().cloned().collect::<Vec<_>>();
                sorted.sort_by_key(|t| t.name.clone());
                sorted.iter().map(|t| TabEntry {
                    id:        t.id.clone().into(),
                    name:      t.name.clone().into(),
                    path:      t.path.clone().into(),
                    is_active: t.is_active,
                    is_dirty:  t.is_dirty,
                    language:  t.language.clone().into(),
                }).collect()
            }
            Err(e) => { error!("Editor lock: {e}"); return; }
        };

        let dirty_any = tabs.iter().any(|t| t.is_dirty);
        window.set_has_unsaved_changes(dirty_any);

        let model: Rc<VecModel<TabEntry>> = Rc::new(VecModel::from(tabs));
        window.set_open_tabs(ModelRc::from(model));
    }

    // -----------------------------------------------------------------------
    // File tree
    // -----------------------------------------------------------------------

    fn toggle_directory(&mut self, window: &MainWindow, path: &str, was_expanded: bool) {
        if was_expanded {
            let current = window.get_file_tree_entries();
            let new_entries: Vec<FileTreeEntry> = current
                .iter()
                .filter(|e| {
                    let ep = e.path.to_string();
                    let is_descendant = ep != path && ep.starts_with(&format!("{path}/"));
                    !is_descendant
                })
                .map(|mut e| {
                    if e.path.as_str() == path { e.is_expanded = false; }
                    e
                })
                .collect();
            let model: Rc<VecModel<FileTreeEntry>> = Rc::new(VecModel::from(new_entries));
            window.set_file_tree_entries(ModelRc::from(model));
        } else {
            let children = match self.fs.lock() {
                Ok(g) => match g.expand_directory(path) {
                    Ok(c) => c,
                    Err(e) => { error!("Expand {path}: {e}"); return; }
                },
                Err(e) => { error!("FS lock: {e}"); return; }
            };

            let current = window.get_file_tree_entries();
            let parent_depth = current.iter()
                .find(|e| e.path.as_str() == path)
                .map(|e| e.depth)
                .unwrap_or(0);

            let mut new_entries: Vec<FileTreeEntry> = Vec::new();
            for mut entry in current.iter() {
                if entry.path.as_str() == path { entry.is_expanded = true; }
                new_entries.push(entry.clone());
                if entry.path.as_str() == path {
                    for child in &children {
                        new_entries.push(file_node_to_entry(child, parent_depth + 1));
                    }
                }
            }

            let model: Rc<VecModel<FileTreeEntry>> = Rc::new(VecModel::from(new_entries));
            window.set_file_tree_entries(ModelRc::from(model));
        }
    }
}

// ---------------------------------------------------------------------------
// Callback wiring
// ---------------------------------------------------------------------------

fn wire_callbacks(window: &MainWindow, app: Rc<RefCell<AppState>>) {
    // Sidebar toggle
    {
        let w = window.as_weak();
        window.on_menu_toggle_sidebar(move || {
            if let Some(w) = w.upgrade() {
                w.set_sidebar_visible(!w.get_sidebar_visible());
            }
        });
    }

    // New project
    {
        let w = window.as_weak();
        let app = Rc::clone(&app);
        window.on_new_project_confirmed(move |req| {
            if let Some(w) = w.upgrade() {
                app.borrow_mut().create_project(&w, &req.name.to_string(), &req.path.to_string(), &req.project_type.to_string());
            }
        });
    }

    // Open project
    {
        let w = window.as_weak();
        let app = Rc::clone(&app);
        window.on_open_project_confirmed(move |path| {
            if let Some(w) = w.upgrade() {
                app.borrow_mut().open_project(&w, &path.to_string());
            }
        });
    }

    // SAF browse
    {
        window.on_saf_browse_requested(move || {
            #[cfg(not(target_os = "android"))]
            warn!("Native directory picker not yet implemented on desktop");
        });
    }

    // File opened from file tree (task 018)
    {
        let w = window.as_weak();
        let app = Rc::clone(&app);
        window.on_file_opened(move |path| {
            if let Some(w) = w.upgrade() {
                app.borrow_mut().open_file(&w, &path.to_string());
            }
        });
    }

    // Tab switch (task 018)
    {
        let w = window.as_weak();
        let app = Rc::clone(&app);
        window.on_tab_switch_requested(move |tab_id| {
            if let Some(w) = w.upgrade() {
                app.borrow_mut().switch_tab(&w, &tab_id.to_string());
            }
        });
    }

    // Tab close (task 018)
    {
        let w = window.as_weak();
        let app = Rc::clone(&app);
        window.on_tab_close_requested(move |tab_id| {
            if let Some(w) = w.upgrade() {
                app.borrow_mut().close_tab(&w, &tab_id.to_string());
            }
        });
    }

    // Directory expand/collapse
    {
        let w = window.as_weak();
        let app = Rc::clone(&app);
        window.on_directory_toggled(move |path, was_expanded| {
            if let Some(w) = w.upgrade() {
                app.borrow_mut().toggle_directory(&w, &path.to_string(), was_expanded);
            }
        });
    }

    // Context menu
    {
        window.on_context_menu_requested(|path, is_dir| {
            info!(path = path.as_str(), is_dir, "Context menu requested");
        });
    }

    // Overflow menu
    {
        window.on_menu_overflow_tapped(|| {
            info!("Overflow menu tapped");
        });
    }
}

// ---------------------------------------------------------------------------
// Tree helpers
// ---------------------------------------------------------------------------

fn flatten_tree(node: &android_ide_filesystem::FileNode, depth: i32) -> Vec<FileTreeEntry> {
    let mut result = Vec::new();
    if depth == 0 {
        if let Some(children) = &node.children {
            let mut sorted = children.clone();
            sorted.sort_by(|a, b| match (&a.kind, &b.kind) {
                (FileKind::Directory, FileKind::File) => std::cmp::Ordering::Less,
                (FileKind::File, FileKind::Directory) => std::cmp::Ordering::Greater,
                _ => a.name.cmp(&b.name),
            });
            for child in &sorted {
                result.push(file_node_to_entry(child, 0));
            }
        }
        return result;
    }
    result.push(file_node_to_entry(node, depth));
    result
}

fn file_node_to_entry(node: &FileNode, depth: i32) -> FileTreeEntry {
    let has_children = node.kind == FileKind::Directory
        && node.children.as_ref().map_or(true, |c| !c.is_empty());
    FileTreeEntry {
        name:         node.name.clone().into(),
        path:         node.path.clone().into(),
        is_directory: node.kind == FileKind::Directory,
        is_expanded:  false,
        is_selected:  false,
        depth,
        has_children,
    }
}

// ---------------------------------------------------------------------------
// Language helpers
// ---------------------------------------------------------------------------

fn language_display(ext: &str) -> &'static str {
    match ext {
        "rs"           => "Rust",
        "kt" | "kts"   => "Kotlin",
        "java"         => "Java",
        "py"           => "Python",
        "js" | "mjs"   => "JavaScript",
        "ts" | "tsx"   => "TypeScript",
        "c"            => "C",
        "cpp" | "cc" | "cxx" => "C++",
        "h" | "hpp"    => "C/C++ Header",
        "json"         => "JSON",
        "toml"         => "TOML",
        "yaml" | "yml" => "YAML",
        "md"           => "Markdown",
        "sh" | "bash"  => "Shell",
        "html"         => "HTML",
        "css"          => "CSS",
        "xml"          => "XML",
        "sql"          => "SQL",
        _              => "Plain Text",
    }
}
