# settings module

## Purpose

Provides typed, TOML-backed persistence for all user-configurable IDE settings. Foundation layer — no dependencies on other IDE modules.

## Responsibilities

- Load/save `AppSettings` from `$CONFIG_DIR/android-ide/settings.toml` (desktop) or `getFilesDir()` (Android, task 020)
- Load/save `ProjectSettings` from `$PROJECT_ROOT/.android-ide/settings.toml`
- Remember the last opened project path for session restore
- Provide typed default values when no settings file exists
- Migrate gracefully from invalid/old files (fall back to defaults, log a warning)

## Public API

```rust
use android_ide_settings::SettingsManager;

let mut settings = SettingsManager::new()?;

// Read
let app = settings.get();
println!("Font size: {}", app.editor.font_size);

// Session restore
if let Some(path) = settings.last_project_path() {
    // re-open last project
}

// Mutate + persist
settings.set_last_project_path("/path/to/project");
settings.save()?;

// Full settings update
let mut app = settings.get().clone();
app.editor.font_size = 16;
settings.save_app_settings(app)?;

// Per-project settings
let proj = settings.load_project_settings("/path/to/project");
```

## Schema

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `editor.font_size` | `u32` | `14` | Monaco editor font size |
| `editor.tab_size` | `u32` | `4` | Spaces per tab |
| `editor.insert_spaces` | `bool` | `true` | Indent with spaces |
| `editor.word_wrap` | `bool` | `false` | Soft word wrap |
| `editor.auto_save` | `bool` | `true` | Auto-save on change |
| `editor.auto_save_delay_ms` | `u32` | `2000` | Auto-save debounce |
| `editor.theme` | `string` | `"vs-dark"` | Monaco theme name |
| `ui.sidebar_visible` | `bool` | `true` | Show file tree sidebar |
| `ui.terminal_panel_height` | `u32` | `200` | Terminal panel height px |
| `ui.color_theme` | `string` | `"dark"` | App color theme |
| `last_project_path` | `Option<string>` | `None` | Last opened project path (session restore) |

## Dependencies

| Crate | Target | Justification |
|-------|--------|---------------|
| `serde` / `toml` | all | TOML serialization |
| `thiserror` | all | Error type derivation |
| `tracing` | all | Structured debug logging |
| `dirs = "5"` | desktop only | XDG-correct config dir. Not needed on Android where `getFilesDir()` is used. |

## Known Limitations

- Android settings path (`getFilesDir()`) not yet implemented — falls back to in-memory defaults on Android until task 020.
- No automatic schema migration between versions; missing fields fall back to `#[serde(default)]`.
- No file watcher for hot-reload of settings changes.
