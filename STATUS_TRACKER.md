# STATUS_TRACKER.md — Android IDE

**Current Date:** 2026-06-13
**Active Phase:** Phase 1 UI Correction Pass (complete) → Phase 2 — Linux Runtime and Terminal (not started)
**Stack:** Kotlin 1.9.22 + Jetpack Compose + Material3 + Monaco WebView

---

## Phase Progress

| Phase | Status | Completed / Total Tasks |
|-------|--------|------------------------|
| Phase 1 — Foundation | **COMPLETE** ✅ | All deliverables verified |
| Phase 1 — UI Correction Pass | **COMPLETE** ✅ | 2026-06-13 |
| Phase 1 — Compile Error / Crash Fix Pass | **COMPLETE** ✅ | 2026-06-13 |
| Tech Stack Migration (Slint/Rust → Kotlin/Compose) | **COMPLETE** ✅ | 2026-06-12 |
| Phase 2 — Linux Runtime | Not Started | 0 / 5 |
| Phase 3 — Git | Not Started | 0 / 8 |
| Phase 4 — Language Intelligence | Not Started | 0 / 6 |
| Phase 5 — Extensions | Not Started | 0 / 5 |

---

## Completed Tasks

| # | Task | Subsystem | Date |
|---|------|-----------|------|
| 001 | Project documentation created (README, DEBUG_LOG, QA_WORKFLOW, STATUS_TRACKER) | documentation | 2026-06-10 |
| 002 | Compose application shell (MainActivity, IdeScreen, adaptive layout) | ui | 2026-06-12 |
| 003 | SAF bridge — SafRepository.kt (list, read, write, create, delete, rename) | filesystem | 2026-06-12 |
| 004 | Monaco editor WebView integration (EditorPane, EditorBridge, EditorMessage) | editor | 2026-06-12 |
| 005 | File tree explorer (FileTreePanel — expand/collapse, file open) | ui | 2026-06-12 |
| 006 | Tab management (EditorTabBar — open, select, close, dirty indicator) | editor/ui | 2026-06-12 |
| 007 | File save (Ctrl+S + toolbar button via IdeViewModel.saveActiveFile) | filesystem/ui | 2026-06-12 |
| 008 | Cursor position + language display (IdeStatusBar) | ui | 2026-06-12 |
| 009 | IDE color palette + Material3 dark theme (Color.kt, Theme.kt, Type.kt) | ui/theme | 2026-06-12 |
| 010 | Launcher icon resources (adaptive icon, mipmap-anydpi-v26) | android | 2026-06-11 |
| 011 | GitHub Actions CI — lint + debug APK + release APK | ci | 2026-06-12 |
| 012 | Monaco offline bundle (fetch-monaco.sh, git-ignored vs/) | editor/ci | 2026-06-11 |
| 013 | Tech stack migration: Slint/Rust → Kotlin/Jetpack Compose | migration | 2026-06-12 |
| 014 | Fix subdirectory expansion bug in SafRepository.listChildren | filesystem | 2026-06-12 |
| 015 | Fix compileDebugKotlin failure — `application` property visibility (BUG-005) | viewmodel | 2026-06-13 |
| 016 | UI Correction: data models — EditorSettings (3 new flags + customSymbols), FileSearchResult, IdeUiState (search/selection/previewHtmlContent fields) | data/viewmodel | 2026-06-13 |
| 017 | UI Correction: repository layer — EditorSettingsRepository persists new booleans + symbols, SafRepository file:// URI support | filesystem | 2026-06-13 |
| 018 | UI Correction: ViewModel — IdeViewModel createBlankProject auto-path, togglePreview uses loadDataWithBaseURL, search/reveal/select/copyPath/removeProject methods | viewmodel | 2026-06-13 |
| 019 | UI Correction: AppRoot — remove bottom NavigationBar, IdeScreen always root, create project direct to name dialog, confirmRemoveProject dialog | ui | 2026-06-13 |
| 020 | UI Correction: IdeScreen — sidebar restructured (large nav buttons + Files header), gesturesEnabled=false, Run always visible, file path dropdown, integrated Projects/Settings screens | ui | 2026-06-13 |
| 021 | UI Correction: EditorPane — fix preview crash (onRenderProcessGone), loadDataWithBaseURL, split toolbar into KeyboardToolbar (icons) + SymbolBar (custom symbols), showKeyboardToolbar/showSymbolBar params | editor/ui | 2026-06-13 |
| 022 | UI Correction: FileTreePanel — root project node, corrected file/folder menus (Rename/CopyPath/Copy/Cut/Delete/Select for files; +NewFile/NewFolder/Import/Export/Paste for folders), active file highlight, .git filter, multi-select mode, file search results panel | ui | 2026-06-13 |
| 023 | UI Correction: SettingsScreen — Keyboard Toolbar toggle, Symbol Bar toggle, File Tree section with Hide .git Folder toggle, sidebar navigation icon | ui | 2026-06-13 |
| 024 | UI Correction: ProjectsScreen — sidebar navigation icon callback | ui | 2026-06-13 |
| 025 | Data: EditorSettings — add `uiFontScale: Float` (0.75–1.50) and `defaultProjectDir: String` | data | 2026-06-13 |
| 026 | Data: EditorSettingsRepository — persist `uiFontScale` and `defaultProjectDir` via SharedPreferences | data | 2026-06-13 |
| 027 | Data: SessionRepository — project-scoped tab persistence (KEY_PREFIX_TABS + projectHash, KEY_PREFIX_ACTIVE + projectHash) | data | 2026-06-13 |
| 028 | Data: IdeUiState — rename `clipboard: FileNode?` → `clipboardItems: List<FileNode>` for multi-item clipboard | data | 2026-06-13 |
| 029 | Data: FileNode.kt — `pathTo` returns leading slash (`/displayName/…`); add `ancestorsOf` extension | data | 2026-06-13 |
| 030 | ViewModel: IdeViewModel — project-scoped session save/restore; `saveCurrentProjectSession` before project switch | viewmodel | 2026-06-13 |
| 031 | ViewModel: IdeViewModel — `copyFileNode`/`cutFileNode` collect multi-select items, exit selection mode; `pasteFileNode` iterates all items | viewmodel | 2026-06-13 |
| 032 | ViewModel: IdeViewModel — `createBlankProject` writes `package.json` template; respects `defaultProjectDir` | viewmodel | 2026-06-13 |
| 033 | ViewModel: IdeViewModel — `pasteFromKotlinClipboard` reads Android ClipboardManager, sends `InsertText` to Monaco | viewmodel | 2026-06-13 |
| 034 | UI: IdeScreen — SidebarNavPanel replaced with compact 48dp icon-only Row of 5 `IconButton`s | ui | 2026-06-13 |
| 035 | UI: IdeScreen — `onCloseDrawer` lambda threaded into sidebar; drawer auto-closes on file open (narrow screen) | ui | 2026-06-13 |
| 036 | UI: IdeScreen — `IdeTopBar` shows path only (no headline title); path tappable for ancestor reveal + sibling quick-switch dropdown | ui | 2026-06-13 |
| 037 | UI: EditorPane — `KeyboardToolbar` replaced horizontal scroll with 2-page `HorizontalPager` (page 1: nav/undo, page 2: clipboard/keyboard); icons 24dp, targets 44dp | editor/ui | 2026-06-13 |
| 038 | UI: EditorPane — WebView `isFocusableInTouchMode = true`; `onTouchListener` calls `requestFocus()`; `onPasteFromClipboard` callback bypasses WebView clipboard API | editor/ui | 2026-06-13 |
| 039 | UI: FileTreePanel — param renamed to `clipboardItems: List<FileNode>`; paste label shows item count; cut items visually dimmed | ui | 2026-06-13 |
| 040 | UI: SettingsScreen — add UI Font Scale slider (0.75–1.50, 5% steps, reset button); add Default Project Directory inline editor | ui | 2026-06-13 |
| 041 | UI: AppRoot — `CompositionLocalProvider(LocalDensity)` applies `uiFontScale` to all app text | ui | 2026-06-13 |
| 042 | Editor: monaco-init.js — tap-to-focus (`click`+`touchend` → `editor.focus()`); `insertText` uses `editor.executeEdits` (atomic paste); content-change debounce 300ms→150ms; performance options (no context menu, no autocomplete, no folding, no link detection); `applyLayout` uses `window.innerWidth/innerHeight` | editor | 2026-06-13 |
| 043 | CI: build.yml — release APK job has `if: github.event_name == 'workflow_dispatch'` guard; release builds never run automatically | ci | 2026-06-13 |
| 044 | Fix BUG-016: `Unresolved reference: dp` in AppRoot.kt — added `import androidx.compose.ui.unit.dp` | ui | 2026-06-13 |
| 045 | Fix BUG-017: `Unresolved reference: parent` in FileOpDialogHost — `dialog.parent` → `dialog.parentNode` at all 4 call sites | ui | 2026-06-13 |
| 046 | Fix BUG-018: Monaco editor WebView crash terminates app — override `onRenderProcessGone` on editor WebView to return `true`; add `EditorCrashedBox` with Reload button; `editorView` lambda shared across all 4 layout branches | editor | 2026-06-13 |
| 047 | CI: split `build.yml` → `build-debug.yml` (push/PR/dispatch, debug APK) + `build-release.yml` (dispatch-only, release APK with keystore signing); `build.yml` retired to redirect comment | ci | 2026-06-13 |

---

## In Progress Tasks

None. Phase 1 complete (including compile error and crash fix pass). Ready to begin Phase 2.

---

## Pending Tasks — Phase 2

| # | Task | Subsystem | Priority |
|---|------|-----------|---------|
| 101 | Terminal UI composable | terminal | High |
| 102 | PTY creation and management | terminal | High |
| 103 | proot environment bootstrap | linux-runtime | High |
| 104 | Package manager bridge (apt/pkg) | linux-runtime | Medium |
| 105 | Terminal session persistence | terminal | Medium |

---

## Future Tasks — Phase 3

| # | Task | Subsystem |
|---|------|-----------|
| 201 | Git status panel | git |
| 202 | Stage/unstage workflow | git |
| 203 | Commit dialog | git |
| 204 | Branch management UI | git |
| 205 | Push/pull operations | git |
| 206 | Clone dialog | git |
| 207 | Diff viewer | git |
| 208 | SSH key and HTTPS credential manager | git |

---

## Future Tasks — Phase 4

| # | Task | Subsystem |
|---|------|-----------|
| 301 | LSP server lifecycle | lsp |
| 302 | Diagnostics rendering | lsp |
| 303 | Autocomplete | lsp |
| 304 | Go-to-definition | lsp |
| 305 | Hover documentation | lsp |
| 306 | Code actions | lsp |

---

## Future Tasks — Phase 5

| # | Task | Subsystem |
|---|------|-----------|
| 401 | Extension package format | extensions |
| 402 | Extension loader | extensions |
| 403 | Extension permission model | extensions |
| 404 | Extension manager UI | extensions |
| 405 | Example extension | extensions |

---

## Session Notes

**2026-06-10:** Initial project setup — Slint/Rust prototype. Project documentation created. SAF bridge, Slint UI shell, Monaco WebView integration implemented.

**2026-06-11:** Build chain fixes (launcher icon, CI platform package, Monaco offline bundle). IDEActivity NativeActivity subclass implemented to layer Monaco WebView above Slint surface.

**2026-06-12:** Full tech stack migration — Slint/Rust replaced by Kotlin/Jetpack Compose. All Phase 1 deliverables ported and verified. See TECH_STACK_MIGRATION.md for the full migration record.

**2026-06-12:** Post-migration audit — fixed subdirectory expansion bug in SafRepository.listChildren. Removed unused imports in IdeViewModel.

**2026-06-13:** Phase 1 UI Correction Pass complete. All 9 file groups updated: data models, repository layer, ViewModel, AppRoot, IdeScreen, EditorPane, FileTreePanel, SettingsScreen, ProjectsScreen. Key fixes: preview crash (BUG-006), base64 URL issue (BUG-007), drawer gesture conflict (BUG-008), sidebar restructure, multi-select mode, .git filtering, file search panel, corrected context menus.

**2026-06-13:** Compile error and crash fix pass. Fixed BUG-016 (`Unresolved reference: dp` in AppRoot.kt — missing `import androidx.compose.ui.unit.dp`), BUG-017 (`Unresolved reference: parent` in FileOpDialogHost — `dialog.parent` → `dialog.parentNode` at 4 call sites), and BUG-018 (Monaco editor WebView crash terminates app — override `onRenderProcessGone` on editor WebView, add `EditorCrashedBox` with Reload). Replaced monolithic `build.yml` with `build-debug.yml` (auto, debug) + `build-release.yml` (dispatch-only, release + keystore signing).

Last updated: 2026-06-13
