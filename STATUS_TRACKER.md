# STATUS_TRACKER.md — Android IDE

**Current Date:** 2026-06-13
**Active Phase:** Phase 1 — Correction Pass #2 (in planning)
**Stack:** Kotlin 1.9.22 + Jetpack Compose + Material3 + Monaco WebView

---

## Phase Progress

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 1 — Foundation | **COMPLETE** ✅ | All deliverables verified |
| Phase 1 — Correction Pass #1 (C-series) | **COMPLETE** ✅ | 18 items, completed 2026-06-13 |
| Phase 1 — Correction Pass #2 (F-series) | **COMPLETE** ✅ | All 26 F-series defects implemented across 2 sessions |
| Phase 2 — Linux Runtime | Not Started | 0 / 5 |
| Phase 3 — Git | Not Started | 0 / 8 |
| Phase 4 — Language Intelligence | Not Started | 0 / 6 |
| Phase 5 — Extensions | Not Started | 0 / 5 |

---

## Completed Tasks — Phase 1 Foundation (001–047)

These tasks represent the initial build and tech-stack migration. Full implementation notes
are in `DEBUG_LOG.md` for bug entries and `TECH_STACK_MIGRATION.md` for architectural decisions.

| # | Task | Subsystem | Date |
|---|------|-----------|------|
| 001 | Project documentation created | documentation | 2026-06-10 |
| 002 | Compose application shell (MainActivity, IdeScreen, adaptive layout) | ui | 2026-06-12 |
| 003 | SAF bridge — SafRepository.kt (list, read, write, create, delete, rename) | filesystem | 2026-06-12 |
| 004 | Monaco editor WebView integration (EditorPane, EditorBridge, EditorMessage) | editor | 2026-06-12 |
| 005 | File tree explorer (FileTreePanel — expand/collapse, file open) | ui | 2026-06-12 |
| 006 | Tab management (EditorTabBar — open, select, close, dirty indicator) | editor/ui | 2026-06-12 |
| 007 | File save (Ctrl+S + toolbar button via IdeViewModel.saveActiveFile) | filesystem/ui | 2026-06-12 |
| 008 | Cursor position + language display (IdeStatusBar) | ui | 2026-06-12 |
| 009 | IDE color palette + Material3 dark theme | ui/theme | 2026-06-12 |
| 010 | Launcher icon resources (adaptive icon, mipmap-anydpi-v26) | android | 2026-06-11 |
| 011 | GitHub Actions CI — lint + debug APK + release APK | ci | 2026-06-12 |
| 012 | Monaco offline bundle (fetch-monaco.sh, git-ignored vs/) | editor/ci | 2026-06-11 |
| 013 | Tech stack migration: Slint/Rust → Kotlin/Jetpack Compose | migration | 2026-06-12 |
| 014 | Fix SAF subdirectory expansion bug (BUG-003) | filesystem | 2026-06-12 |
| 015 | Fix compileDebugKotlin: `application` property visibility (BUG-005) | viewmodel | 2026-06-13 |
| 016–043 | UI Correction Pass #1: data models, repositories, ViewModel, AppRoot, IdeScreen, EditorPane, FileTreePanel, SettingsScreen, ProjectsScreen, CI pipeline | all | 2026-06-13 |
| 044 | Fix BUG-016: `Unresolved reference: dp` in AppRoot.kt | ui | 2026-06-13 |
| 045 | Fix BUG-017: `Unresolved reference: parent` in FileOpDialogHost (4 sites) | ui | 2026-06-13 |
| 046 | Fix BUG-018: Monaco editor WebView crash terminates app | editor | 2026-06-13 |
| 047 | CI: split build.yml → build-debug.yml + build-release.yml | ci | 2026-06-13 |

---

## Completed Tasks — Phase 1 Correction Pass #1 (C-series)

| ID | Task | Subsystem | Date |
|----|------|-----------|------|
| C001 | Run button crash: moved togglePreview into viewModelScope coroutine with runCatching | viewmodel + ui | 2026-06-13 |
| C002 | Sidebar nav icons 22dp → 24dp | ui | 2026-06-13 |
| C003 | Sidebar content screen-aware: file tree only on Editor screen; Projects/Settings show nav strip only | ui | 2026-06-13 |
| C004 | File-open → close-drawer confirmed wired; Monaco focus/keyboard tracked as F010 | ui | 2026-06-13 |
| C005 | uiFontScale in EditorSettings, applied via LocalDensity, slider + reset in SettingsScreen | ui + theme | 2026-06-13 |
| C006 | Top bar: empty fallback (no app title); Search promoted to icon; Save→Search→Run order | ui | 2026-06-13 |
| C007 | Path navigation: path always tappable; sibling-count gate removed; hint shown when parent not expanded | ui | 2026-06-13 |
| C008 | Editor focus: ACTION_UP calls requestFocus() + showSoftInput(SHOW_IMPLICIT) | ui/editor | 2026-06-13 |
| C009 | Session save/restore per project URI confirmed complete | viewmodel + data | 2026-06-13 |
| C010 | Tab dedup: openFileInternal checks existing tabs before creating new one | viewmodel | 2026-06-13 |
| C011 | Temporary tab: single-tap = preview; first edit = pin; double-tap = permanent; italic title | viewmodel + ui | 2026-06-13 |
| C012 | Multi-copy/cut: copyFileNode/cutFileNode collect selectedUris; pasteFileNode accepts root | viewmodel + ui | 2026-06-13 |
| C013 | Copy path: pathTo always returns leading slash; "/$raw" fallback | viewmodel | 2026-06-13 |
| C014 | renderWhitespace exposed in Settings; Phase-4 disabled placeholders for Code Completion and Folding | ui/editor | 2026-06-13 |
| C015 | Monaco performance: formatOnPaste=false; autoIndent=brackets; glyphMargin=false; wordWrap re-layout | editor | 2026-06-13 |
| C016 | Text selection guard: touchend focus() skipped when Monaco has non-empty selection | editor | 2026-06-13 |
| C017 | Keyboard toolbar: smartIndent/smartOutdent JS; single keyboard toggle; Outdent button added | ui/editor | 2026-06-13 |
| C018 | Find/Replace: ShowFind/ShowReplace messages → actions.find / startFindReplaceAction in JS | editor | 2026-06-13 |

---

## Pending Tasks — Phase 1 Correction Pass #2 (F-series)

Full root-cause analysis for each task is in the **Phase 1 Correction Pass #2 Re-Audit** section of this file (see below).

### Completed — Priority 0 — Crash / Data Loss

| ID | Task | Subsystem | Date |
|----|------|-----------|------|
| F001 ✅ | Run/Preview crash: webView.post + try-catch + 2 MB guard + DefaultUncaughtExceptionHandler | editor | 2026-06-14 |
| F002 ✅ | Session restore race: sequential coroutine replaces parallel launches | viewmodel | 2026-06-14 |

### Completed — Priority 1 — Broken Core Workflows

| ID | Task | Subsystem | Date |
|----|------|-----------|------|
| F003 ✅ | Path navigator: SAF-backed state machine (navStack, loadNavChildren, folder tap navigates, Up button) | ui + saf | 2026-06-14 |
| F004 ✅ | Save As: inline project-relative path dialog (FileOpDialog.SaveAs + saveAsAtPath) | ui + viewmodel + saf | 2026-06-14 |
| F005 ✅ | Binary validation: 5 MB guard, null-byte scan, image base64 preview in Monaco | viewmodel + saf | 2026-06-14 |
| F006 ✅ | requestRun project-scoped: searches full fileTree for first .html if active tab not previewable | viewmodel | 2026-06-14 |
| F007 ✅ | Duplicate name rejection: pre-check in all 6 create/rename/duplicate paths | viewmodel + ui | 2026-06-14 |
| F025 ✅ | Leading "/" absolute-path routing + SAF-queried duplicate check in createFile/Folder; move-by-path deferred to Phase 2 for rename | viewmodel + ui + saf | 2026-06-14 |

### Completed — Priority 2 — Sidebar and UI Defects

| ID | Task | Subsystem | Date |
|----|------|-----------|------|
| F008 ✅ | Sidebar nav panel: 2-Row grid with NavCell (56dp, icon + label) | ui | 2026-06-14 |
| F009 ✅ | Sidebar PROJECTS: SidebarRecentProjectsList; SETTINGS: SidebarSettingsShortcuts | ui | 2026-06-14 |
| F010 ✅ | Sidebar file-select: focusEditor + showSoftInput 150ms after drawer close | ui + editor | 2026-06-14 |
| F011 ✅ | Sidebar tap-outside: transparent scrim Box overlay when drawer open | ui | 2026-06-14 |
| F012 ✅ | Root-folder paste: Paste DropdownMenuItem in RootProjectNode when clipboard non-empty | ui | 2026-06-14 |
| F013 ✅ | File icons: fileIconFor() extension-based mapping (Code, Article, Image, etc.) | ui | 2026-06-14 |
| F014 ✅ | Files header touch targets: 36dp IconButton / 20dp icons | ui | 2026-06-14 |
| F022 ✅ | Rename Project dead button: replaced no-op with statusMessage "available in Phase 2" | ui | 2026-06-14 |
| F023 ✅ | Duplicate added to file context menu (DropdownMenuItem → showDuplicateDialog) | ui | 2026-06-14 |
| F024 ✅ | Text selection: touchend focus() skipped when Monaco selection non-empty | editor | 2026-06-14 |

### Completed — Priority 2 — Editor Behavior

| ID | Task | Subsystem | Date |
|----|------|-----------|------|
| F015 ✅ | Keyboard toggle: WindowInsets.ime replaces local mutableStateOf | editor/ui | 2026-06-14 |
| F016 ✅ | Cut/Copy: RequestCopy/RequestCut → JS reads selection → Kotlin ClipboardManager | editor + viewmodel | 2026-06-14 |
| F026 ✅ | Folder Select: DropdownMenuItem added to folder context menu | ui | 2026-06-14 |

### Completed — Priority 3 — Monaco Settings Surface

| ID | Task | Subsystem | Date |
|----|------|-----------|------|
| F017 ✅ | minimap, scrollBeyondLastLine, cursorStyle, bracketPairColorization, autoClosingBrackets in EditorSettings, Repository, JS setEditorOptions, and SettingsScreen | settings + editor | 2026-06-14 |

### Completed — Priority 4 — CI Pipeline

| ID | Task | Subsystem | Date |
|----|------|-----------|------|
| F018 ✅ | CI keystore: printf '%s' + post-decode size guard (>1000 bytes) | ci | 2026-06-14 |

### Completed — Priority 5 — Data Integrity

| ID | Task | Subsystem | Date |
|----|------|-----------|------|
| F019 ✅ | Project switch: CloseAllModels + WebView reload for clean Monaco state | viewmodel + editor | 2026-06-14 |
| F020 ✅ | SAF permission guard: validates persistedUriPermissions before openProjectInternal | viewmodel | 2026-06-14 |

---

## Phase 1 Correction Pass #2 — Root Cause Reference

### F001 — Run/Preview Crash (Three Independent Paths)

**Path A:** `LaunchedEffect(previewHtmlContent, isPreviewVisible)` fires in the same Compose frame that sets `isPreviewVisible = true`. The `AndroidView(previewWebView)` has not been attached to the view hierarchy yet when `previewWebView.post { loadDataWithBaseURL(...) }` is enqueued. On some Android OEM/version combinations, calling `loadDataWithBaseURL` on an unattached WebView throws `RuntimeException` on the main thread, killing the process.

**Path B:** `loadDataWithBaseURL` routes content through the Android Binder IPC layer. Payloads exceeding ~800 KB trigger `TransactionTooLargeException` (an uncaught `RuntimeException`), terminating the process with no dialog. No size guard exists.

**Path C:** The `onRenderProcessGone` overrides protect against the WebView's renderer *subprocess* dying. They do NOT catch main-thread `RuntimeException` from Paths A and B. There is no global uncaught exception handler.

**Required fix:** (1) Add 2 MB content size cap before loading. (2) Wrap `previewWebView.post {}` in try-catch catching `RuntimeException`, setting `previewCrashed = true` on catch. (3) Gate the `LaunchedEffect` with a 1-frame delay using `LaunchedEffect(isPreviewVisible)` + `withFrameMillis {}` so composition completes before load. (4) Add `Thread.setDefaultUncaughtExceptionHandler` in Application class to catch and display non-fatal errors instead of terminating.

### F002 — Session Restore Race

`restoreSession()` launches `openProjectInternal(projectUri)` and each `openFileInternal(uri)` as independent, unsynchronized coroutines on `viewModelScope`. `openProjectInternal` resets `openTabs = emptyList()` mid-flight while file coroutines may have already added tabs. **Fix:** Replace parallel launches with a single sequential coroutine.

### F003 — Path Navigator

`findSiblings` traverses only `node.isExpanded == true` nodes. A parent folder that is not expanded in the sidebar returns null, making the navigator completely empty. Clicking a folder in the dropdown does nothing (`if (!sibling.isDirectory) onOpenFile(...)`). **Fix:** Add `suspend fun listChildrenDirect(parentUri)` to SafRepository. Replace `findSiblings` with a self-contained navigator state machine: `navCurrentUri`, `navItems`, `navIsLoading`. Tap file → open + close nav. Tap folder → load its children, update `navCurrentUri`. "↑ Up" button loads parent. Disabled at project root.

### F004 — Save As

`saveAsLauncher` uses `ActivityResultContracts.CreateDocument` — the Android system picker. The saved file gets an opaque SAF URI outside the project tree, invisible in the file tree and unusable by subsequent tree operations. **Fix:** Remove `saveAsLauncher`. Add `FileOpDialog.SaveAs`. Show inline path dialog, resolve relative path against project root via SAF.

### F005 — Binary File Validation

`readFile` returns raw bytes unconditionally decoded as UTF-8 and loaded into Monaco. Binary content (`.apk`, `.class`, compiled assets) corrupts Monaco rendering. Large files exceed Monaco's comfortable edit range. **Fix:** Add 5 MB size guard. Add null-byte binary detection (first 8 KB scan). Add MIME-type detection: images show inline HTML `<img>` preview. All checks run before Monaco model creation.

### F006 — requestRun File-Scoped

`requestRun()` only checks `activeTab?.language`. If a `.css` or `.js` file is active in a project containing `index.html`, it shows "No run provider." **Fix:** If active tab is not HTML/Markdown, search the full in-memory `fileTree` (all nodes, not just expanded) for the first `.html` file. If found, load and preview it. If nothing found, show status "No previewable content in this project."

### F007 — Duplicate Name Rejection

All four create paths (`createFileInDirectory`, `createFolderInDirectory`, `createFileAtRoot`, `createFolderAtRoot`) and `renameNode`/`duplicateFile` call SAF directly with no pre-check. SAF silently renames the collision (e.g. `index.html` → `index (1).html`) — no error, no warning. **Fix:** Add `error: String?` to dialog state. Pre-check parent's children against the target name. Show inline error, block SAF call on collision.

### F025 — Full-Path Verification and Leading-Slash Semantics

Current verification (F007) only compares `leafName` against `parentNode.children`. It would incorrectly reject `src/index.html` when `index.html` exists at project root. Additionally, names beginning with `/` should be treated as absolute paths from the project root — useful for both rename (move semantics) and create (cross-folder targeting from any context menu). **Fix:** If `name.startsWith("/")`: strip slash, split by `/`, call `resolveOrCreatePath(rootUri, segments)` in SafRepository which walks/creates intermediate directories and returns `(parentDirUri, leafName)`. Then check SAF-queried children of `parentDirUri` for `leafName`. If no leading slash: check only `parentNode.children`.

### F008 — Sidebar Nav Panel Layout

Five 48dp `IconButton`s in a horizontal `Row`. The spec requires "large icon buttons in a compact grid layout." **Fix:** 3-column grid (`LazyVerticalGrid` or manual `Row`+`Row`). Row 1: Projects, Editor, Settings. Row 2: Git (disabled), Terminal (disabled), spare. Each cell 56dp×56dp, icon 24dp, text label below icon (`labelSmall`). Selected state: accent background. Height: ~120dp total.

### F009 — Sidebar Content: Projects and Settings

The `when (PROJECTS, SETTINGS)` branch is completely empty — sidebar shows only the nav strip with nothing below. **Fix:** Projects sidebar: compact list of `uiState.recentProjects` (project name, tap to open). Settings sidebar: list of section labels (App, Editor, Keyboard, File Tree, Storage) with `LazyListState` scroll-to-item wired from `SettingsScreen`.

### F010 — Sidebar File-Select: Monaco Focus and Keyboard

`onFileClick` calls `openFile + navigateTo(EDITOR) + onCloseDrawer?.invoke()`. Drawer closes, editor is shown, but Monaco has no focus — keyboard doesn't appear. User must tap the editor again. **Fix:** After `onCloseDrawer?.invoke()`, post a 150ms-delayed `sendEditorCommand(EditorOutbound.ExecuteCommand("focusEditor"))` + `imm.showSoftInput(editorWebView, SHOW_IMPLICIT)`.

### F011 — Sidebar Tap-Outside Close

`gesturesEnabled=false` correctly prevents Monaco horizontal scroll from opening the drawer, but it also prevents `ModalNavigationDrawer`'s built-in backdrop tap-to-close. **Fix:** Keep `gesturesEnabled=false`. Wrap the main content area in a `Box`. When `!drawerState.isClosed`, add a transparent `Modifier.clickable { closeDrawer() }` overlay as an additional `Box` layer on top of the content.

### F012 — Root-Folder Paste Missing

`RootProjectNode` has no Paste item. The spec says "restrictions preventing root-folder paste must be removed." `pasteFileNode` already accepts root-level nodes — only the UI trigger is missing. **Fix:** Add `clipboardItems: List<FileNode>` and `onPasteAtRoot: () -> Unit` params to `RootProjectNode`. Add conditional Paste `DropdownMenuItem` when clipboard non-empty.

### F013 — File Icons

All non-directory files use `Icons.Default.InsertDriveFile` regardless of extension. **Fix:** Add `fileIconFor(name: String): ImageVector` mapping: `.kt/.kts` → Code, `.html/.htm` → Language, `.css` → Style/Palette, `.js/.ts` → Code, `.json/.yaml/.toml` → DataObject, `.md` → Article, `.png/.jpg/.svg/etc.` → Image, `.pdf` → PictureAsPdf, `.zip/.gz` → FolderZip, `.sh` → Terminal, `.gradle` → Build, default → InsertDriveFile.

### F014 — Files Header Button Touch Targets

All five `FilesHeader` icon buttons use `Modifier.size(28.dp)` with `Modifier.size(16.dp)` icons. **Fix:** Increase `IconButton` to `Modifier.size(36.dp)`, icon to `Modifier.size(20.dp)`.

### F015 — Keyboard Toggle Uses Wrong State

`var keyboardShowing by remember { mutableStateOf(true) }` is never updated when the keyboard is dismissed by Back or system gesture. Icon shows wrong state. **Fix:** Replace with `val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0`. Drive icon and IME action from `imeVisible`.

### F016 — Keyboard Toolbar Cut/Copy

`editor.action.clipboardCutAction` and `editor.action.clipboardCopyAction` use the browser clipboard API, which is permission-gated on Android WebView. Operations silently fail. **Fix:** Add `EditorOutbound.RequestCopy` and `EditorOutbound.RequestCut` messages. JS reads selection text via `editor.getModel().getValueInRange(editor.getSelection())`, posts back as new `EditorInbound.ClipboardText(text)`. Kotlin writes to `ClipboardManager` directly. Cut additionally deletes the selection via `editor.executeEdits`.

### F017 — Monaco Settings Surface

Options hardcoded in `monaco-init.js` that should be in `EditorSettings`: `minimap.enabled` (OFF), `scrollBeyondLastLine` (OFF), `cursorStyle` (default), `bracketPairColorization.enabled` (OFF), `autoClosingBrackets` (default). Phase-4 disabled placeholders needed for `folding`, `quickSuggestions`. **Fix:** Add each to `EditorSettings`, `SetEditorOptions`, JS `setEditorOptions` handler, and `SettingsScreen`.

### F018 — CI Keystore Decode

`echo "$KEYSTORE_BASE64" | base64 -d` appends a trailing newline. On strict base64 decoders, this produces a truncated keystore file, causing `keytool: java.io.EOFException`. **Fix:** Replace with `printf '%s' "$KEYSTORE_BASE64" | base64 -d > release.keystore`. Add post-decode size check: if file is under 1000 bytes, fail with clear diagnostic.

### F019 — Monaco Stale Models on Project Switch

`openProjectInternal` sets `isEditorReady = false` but does not reload or reset the Monaco WebView. Monaco keeps all open model URIs from the previous project. Files from the new project that share a path with old models get the wrong content. **Fix:** On project switch, send `EditorOutbound.CloseAllModels` to dispose all Monaco models, then reload the WebView to guarantee a clean state.

### F020 — SAF Permission Guard

Restoring a session URI that has lost its SAF permission grant (revoked after reboot or permission reset) causes a silent failure — the project root is set but file tree operations fail with no user-visible error. **Fix:** Before `openProjectInternal`, check `contentResolver.persistedUriPermissions.any { it.uri.toString() == projectUri }`. If not found, show status message and clear stale session.

### F022 — Rename Project Dead Button

`onClick = { /* TODO Phase 2: rename project via dialog */ }` — the menu item is enabled and clickable, menu closes, nothing happens. Users have no indication the action exists but is deferred. **Fix:** Change `onClick` to `ideViewModel.setStatusMessage("Rename Project — available in Phase 2")` OR disable the item with `enabled = false` and append `" (Phase 2)"` to the label text.

### F023 — Duplicate Missing from File Menu

`FileTreeRow` receives `onShowDuplicateDialog: (FileNode) -> Unit` and it is wired all the way from `IdeScreen` → `FileTreePanel` → `FileTreeRow`. But the file context menu (lines 541–570) contains no "Duplicate" `DropdownMenuItem`. The backend (`FileOpDialog.Duplicate`, `DuplicateDialog`, `ideViewModel.duplicateFile`) is fully implemented. **Fix:** Add `DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuOpen = false; onShowDuplicateDialog(node) })` to the file context menu, before the Delete divider.

### F024 — Text Selection (Manual Select) Broken

**Cause A:** The `click` event listener (monaco-init.js line 258) calls `editor.focus()` unconditionally. On Android, a long-press to initiate selection fires: `touchstart → touchend → [handles appear] → click`. The `click` fires AFTER the selection handles are visible. `editor.focus()` repositions Monaco's cursor, collapsing the selection and dismissing the handles.

**Cause B:** The `touchend` timeout of 50ms is too short on slow devices; `editor.getSelection()` may not yet reflect the Android DOM selection change, so the guard passes and `editor.focus()` is incorrectly called.

**Fix:** (1) Add the same selection guard to the `click` handler, also checking `document.getSelection().toString() !== ''`. (2) Extend `touchend` timeout from 50ms to 150ms. (3) Add `document.addEventListener('selectionchange', ...)` to cache `isSelectingText` flag; use it in both handlers as a third guard.

### F026 — Folder Select Missing from Folder Context Menu

The file context menu has "Select" which enters multi-select mode for files. The folder context menu does not. Users cannot multi-select folders unless they long-press (which opens the same menu). **Fix:** Add `DropdownMenuItem(text = { Text("Select") }, onClick = { menuOpen = false; onSelect(node.documentUri) })` to the folder context menu section.

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

**2026-06-10:** Initial project setup — Slint/Rust prototype. Documentation created.

**2026-06-11–12:** Monaco offline bundle, CI pipeline, launcher icon. Full tech stack migration from Slint/Rust to Kotlin/Jetpack Compose. All Phase 1 Foundation deliverables complete. See `TECH_STACK_MIGRATION.md`.

**2026-06-13 (Pass 1):** Phase 1 UI Correction Pass #1. 18 C-series items completed. Key items: preview crash (BUG-006/018/019), base64→loadDataWithBaseURL (BUG-007), drawer gesture (BUG-008), sidebar restructure, keyboard toolbar redesign (BUG-012/024), editor focus/IME (BUG-013/025), multi-select clipboard, session restore, temporary tabs, text selection guard (C016).

**2026-06-13 (Pass 2 planning):** Full re-audit of all source files. 26 defects catalogued across crash-level, core workflow, sidebar/UI, Monaco settings, CI, and data integrity categories. Three new defects identified this session: F022 (Rename Project dead button), F023 (Duplicate missing from file menu), F024 (text selection — click handler missing guard), F025 (full-path duplicate verification). Root cause analysis complete for all. No code edits yet.

Last updated: 2026-06-13
