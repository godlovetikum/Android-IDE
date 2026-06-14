# DEBUG_LOG.md — Android IDE

Historical debugging record. Every bug fix, architectural correction, and failed design decision is recorded here.

Future contributors must be able to understand previous mistakes without rediscovering them.

---

## Quick Index

| ID | Subsystem | One-line summary |
|----|-----------|-----------------|
| BUG-001 | android | Launcher icon missing from APK |
| BUG-002 | editor | Monaco required internet — offline use impossible |
| BUG-003 | filesystem | SafRepository.listChildren returned root children for subdirectories |
| BUG-004 | editor | `@SuppressLint("JavascriptInterface")` false-positive via generic remember<T> |
| BUG-005 | viewmodel | `application` property invisible in AndroidViewModel subclass (lifecycle 2.6+) |
| BUG-006 | editor | Preview WebView crash terminated app (missing onRenderProcessGone) |
| BUG-007 | editor | `loadUrl("data:...")` OOM/URL-length issues for large HTML |
| BUG-008 | ui | Monaco horizontal swipe accidentally opened the sidebar drawer |
| BUG-009 | ui | Sidebar nav panel consumed 220dp, leaving too little for file tree |
| BUG-010 | ui | File open in sidebar did not close the drawer |
| BUG-011 | ui | Top app bar showed file name headline + path subtitle, wasted height |
| BUG-012 | ui | Keyboard toolbar used horizontalScroll, hid most actions off-screen |
| BUG-013 | editor | Monaco WebView did not request focus on tap — keyboard didn't appear |
| BUG-014 | editor | Paste used WebView clipboard API (slow, permission-gated on Android) |
| BUG-015 | data | `clipboard: FileNode?` — multi-item cut/copy not supported |
| BUG-016 | ui | `Unresolved reference: dp` in AppRoot.kt |
| BUG-017 | ui | `Unresolved reference: parent` in FileOpDialogHost (4 sites) |
| BUG-018 | editor | Monaco editor WebView crash terminated app (missing onRenderProcessGone) |
| BUG-019 | viewmodel | `togglePreview()` / Run crashed app: main-thread + no error handling |
| BUG-020 | ui | Sidebar showed file tree on Projects and Settings screens |
| BUG-021 | ui | Top bar: app title fallback + Search buried in overflow |
| BUG-022 | ui | Path navigator opened sidebar; broke for single-child or collapsed folders |
| BUG-023 | editor | No temporary tab behavior — all files opened as permanent tabs |
| BUG-024 | editor | Keyboard toolbar: indent broken without selection; two redundant keyboard toggle buttons |
| BUG-025 | editor | Editor single-tap did not reliably show the soft keyboard |
| BUG-026 | editor | Monaco renderWhitespace hardcoded; no Phase-4 placeholders in Settings |
| AC-001 | architecture | Tech stack migration: Slint/Rust → Kotlin/Jetpack Compose |
| AC-002 | architecture | Navigation refactor: bottom NavigationBar removed, sidebar-only |

---

## Entries

### BUG-001 — Launcher icon missing from APK

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | android |
| **Issue** | APK installed and launched but showed no icon on the home screen launcher. |
| **Root Cause** | `AndroidManifest.xml` had no `android:icon` or `android:roundIcon` attributes and no `res/` directory existed. |
| **Files Modified** | `AndroidManifest.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/mipmap-anydpi-v26/ic_launcher_round.xml`, `res/drawable/ic_launcher_foreground.xml`, `res/values/colors.xml` |
| **Solution** | Added adaptive icon: vector "A" monogram (VS-Code blue #007ACC) on dark IDE background (#1e1e1e). Since `minSdk = 26`, only `mipmap-anydpi-v26/` is needed. |
| **Prevention** | Always add `android:icon` to `<application>` and create `mipmap-anydpi-v26/` when creating a new Android project. |

---

### BUG-002 — Monaco required internet — offline use impossible

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | editor |
| **Issue** | On a device without internet, the editor WebView showed "Loading editor…" indefinitely. |
| **Root Cause** | `index.html` and `monaco-init.js` loaded Monaco from the unpkg CDN at runtime. |
| **Files Modified** | `assets/editor/index.html`, `assets/editor/monaco-init.js`, `scripts/fetch-monaco.sh` (created), `.gitignore` |
| **Solution** | Changed to relative local paths (`vs/loader.js`, `vs`). Created `scripts/fetch-monaco.sh` to download Monaco 0.52.0 from npm into `assets/editor/vs/`. Added `assets/editor/vs/` to `.gitignore` (~20 MB). CI runs the script before `./gradlew`. |
| **Prevention** | Never load runtime assets from a CDN in a mobile app. Bundle all required JS/CSS. The script is idempotent — exits early if `vs/loader.js` exists. Monaco version is pinned in `MONACO_VERSION` in the script. |

---

### BUG-003 — SafRepository.listChildren returned root children for subdirectories

| Field | Value |
|-------|-------|
| **Date** | 2026-06-12 |
| **Subsystem** | filesystem |
| **Issue** | Expanding a subdirectory showed the project root's children instead of the subdirectory's children. |
| **Root Cause** | `listChildren` called `DocumentsContract.getTreeDocumentId(parentUri)` for all URIs where `isTreeUri()` is true. `getTreeDocumentId` always returns the root document ID. Child directory URIs built by `buildDocumentUriUsingTree` have the shape `/tree/<treeDocId>/document/<docId>` — `isTreeUri()` returns `true` for them, but the correct document ID is in the "document" segment, not "tree". |
| **Solution** | Detect whether the URI has a "document" path segment: if yes, use `DocumentsContract.getDocumentId(parentUri)`; if no, use `getTreeDocumentId(parentUri)`. |
| **Prevention** | SAF has two URI shapes: tree URIs (`/tree/<docId>`) from `ACTION_OPEN_DOCUMENT_TREE` and document-within-tree URIs (`/tree/<treeDocId>/document/<docId>`). Always distinguish them. Never assume `getTreeDocumentId` gives the correct ID for child document URIs. |

---

### BUG-004 — `@SuppressLint("JavascriptInterface")` false-positive via generic remember

| Field | Value |
|-------|-------|
| **Date** | 2026-06-12 |
| **Subsystem** | editor |
| **Issue** | Lint reported `[JavascriptInterface]` as a build-blocking error even with `@JavascriptInterface` correctly applied. |
| **Root Cause** | Android lint resolves the type of the object passed to `addJavascriptInterface` statically. When the object is produced by `remember<T> { EditorBridge() }`, lint sees type `T` (the generic), not the concrete `EditorBridge`, and cannot find the annotation. This is a lint static-analysis limitation — the annotation is present at runtime. |
| **Solution** | Added `"JavascriptInterface"` to `@SuppressLint(...)` on `EditorPane`. Comment documents that the annotation IS present on `EditorBridge.onMessage`. |
| **Prevention** | Any `addJavascriptInterface(obj, name)` call where `obj` is produced by a generic function must suppress `[JavascriptInterface]` and document why. |

---

### BUG-005 — `application` property invisible in AndroidViewModel

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | viewmodel |
| **Issue** | `compileDebugKotlin` failed: `Cannot access 'application': it is invisible (private in a supertype) in 'IdeViewModel'`. |
| **Root Cause** | `AndroidViewModel.application` was made `private` in `lifecycle-viewmodel:2.6.0`. The project uses `2.7.0`. |
| **Solution** | Replaced `application.resources.configuration.uiMode` with `getApplication<Application>().resources.configuration.uiMode`. |
| **Prevention** | Never access `AndroidViewModel.application` as a property. Always use the public `getApplication<Application>()` method. |

---

### BUG-006 — Preview WebView crash terminated app

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Issue** | App terminated without a Java stack trace when the preview WebView rendered malformed or resource-heavy HTML. |
| **Root Cause** | `WebViewClient.onRenderProcessGone` was not overridden. The default returns `false`, which terminates the entire application process when the renderer crashes. |
| **Solution** | Override `onRenderProcessGone` on the preview WebView's `WebViewClient` to return `true` and set `previewCrashed` state. When `previewCrashed` is true, show an error placeholder composable. |
| **Prevention** | Every production `WebView` that loads arbitrary content must override `onRenderProcessGone` and return `true`. Apply to all WebViews simultaneously — BUG-018 shows what happens when only one of two WebViews is fixed. |

---

### BUG-007 — `loadUrl("data:...")` OOM and URL-length issues for large HTML

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Issue** | Preview WebView loaded via `data:text/html;base64,...` URL. For large HTML (~50 kB), this produced ~70 kB URL strings causing GC pressure and occasional OOM on low-RAM devices. |
| **Root Cause** | Base64 encoding creates a ~1.33× larger string held in memory during encoding and URL construction. |
| **Solution** | Pass raw HTML as `previewHtmlContent` in `IdeUiState`. Load via `loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)`. No base64, no URL length limit. |
| **Prevention** | Never use `data:` URL scheme for loading arbitrary HTML. Always use `loadData()` or `loadDataWithBaseURL()`. |

---

### BUG-008 — Monaco horizontal swipe accidentally opened the sidebar drawer

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | Horizontal swipe in the Monaco editor triggered the `ModalNavigationDrawer` to open. |
| **Root Cause** | `ModalNavigationDrawer` defaults to `gesturesEnabled = true`, intercepting horizontal swipe events that start near the left edge. Monaco's horizontal scroll is indistinguishable from a drawer-open gesture. |
| **Solution** | Set `gesturesEnabled = false`. Sidebar opens only via the hamburger icon. |
| **Prevention** | Always set `gesturesEnabled = false` on `ModalNavigationDrawer` when content contains a horizontally-scrollable surface. Note: this also prevents the scrim tap-to-close (tracked as F011 for a fix). |

---

### BUG-009 — Sidebar nav panel consumed 220dp, leaving too little for file tree

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | Vertical `Column` of 5 labelled `NavigationDrawerItem` rows (~44dp each = ~220dp total) left only ~40% of sidebar height for the file tree. |
| **Root Cause** | `NavigationDrawerItem` is designed for drawers with labelled primary destinations. Using 5 such items for a utility nav strip wastes vertical space. |
| **Solution** | Replaced with a single `Row` of 5 `IconButton`s (48×48dp). Nav strip is exactly 48dp tall. Labels available via `contentDescription` for accessibility. |
| **Note** | The 48dp single-row layout is a partial fix. F008 tracks the remaining work to move to a proper 3-column grid with visible labels per the spec. |

---

### BUG-010 — File open in sidebar did not close the drawer

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | After tapping a file on a narrow screen, the drawer stayed open and covered the editor. |
| **Root Cause** | `onFileClick` called only `openFile()` + `navigateTo(EDITOR)`. The drawer's coroutine scope was not reachable from inside `FileTreePanel`. |
| **Solution** | Threaded `onCloseDrawer: (() -> Unit)?` through the sidebar content lambda. On file open, `onCloseDrawer?.invoke()` closes the drawer. Note: Monaco focus + keyboard after close is tracked as F010. |
| **Prevention** | Any action inside a modal drawer that should dismiss it must close the drawer as part of the same event handler. |

---

### BUG-011 — Top app bar: file name headline + path subtitle wasted toolbar height

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | Two text items (large file-name headline + smaller path subtitle) pushed the bar to 64dp+. |
| **Solution** | Single `Text` showing only the full path, e.g. `/src/pages/home.html`. Path is tappable for quick-switch dropdown. Top bar height reduced to 48dp. |
| **Prevention** | In a code editor, the file path is the title. One single-line path item is sufficient. |

---

### BUG-012 — Keyboard toolbar used horizontalScroll, hiding most actions off-screen

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | 13 icon buttons in a single `Row` wrapped in `horizontalScroll`. Only ~5 icons visible at once. Users had to scroll to find Cut/Copy/Paste. |
| **Solution** | 2-page `HorizontalPager`. Page 1: navigation + undo/redo (8 actions). Page 2: clipboard + select + keyboard control (5 actions). All actions visible without horizontal scrolling. |
| **Prevention** | Never use horizontal scroll for a toolbar. Use paging or fewer items. |

---

### BUG-013 — Monaco WebView did not request focus on tap — keyboard didn't appear

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Issue** | Tapping the Monaco editor sometimes required two taps to show the soft keyboard. |
| **Root Cause** | `isFocusableInTouchMode` not set. Without it, the view can receive focus from code but not from a touch event, which is what the IME observes. |
| **Solution** | Set `isFocusable = true` and `isFocusableInTouchMode = true`. Added `setOnTouchListener` calling `requestFocus()` on `ACTION_UP`. JS-side: `click` + `touchend` listeners call `editor.focus()` with 50ms delay. |
| **Note** | The 50ms delay and missing selection guard on the `click` handler are tracked as F024. |

---

### BUG-014 — Paste used WebView clipboard API (slow, permission-gated on Android)

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Issue** | Toolbar "Paste" called `editor.action.clipboardPasteAction`. On Android API 29+, this invokes `navigator.clipboard.readText` which requires user permission and adds 100–300ms latency. |
| **Solution** | Added `onPasteFromClipboard` callback. Paste button reads directly from Android `ClipboardManager` (no permission needed) and sends `insertText` command. JS uses `editor.executeEdits` (atomic, preserves undo history). |
| **Prevention** | Always read the Android clipboard from Kotlin. Never rely on `navigator.clipboard` API in a WebView on Android. |

---

### BUG-015 — `clipboard: FileNode?` — multi-item cut/copy not supported

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | data |
| **Issue** | Multi-select mode allowed selecting several items but Cut/Copy operated only on the single tapped node. |
| **Root Cause** | `IdeUiState.clipboard: FileNode?` stored only one item. |
| **Solution** | Renamed to `clipboardItems: List<FileNode>`. Copy/Cut in multi-select mode collects all `selectedUris`. `pasteFileNode` iterates all items. FileTreePanel shows item count in paste menu label. |
| **Prevention** | Design clipboard state as a list from the beginning. |

---

### BUG-016 — `Unresolved reference: dp` in AppRoot.kt

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Root Cause** | `AppRoot.kt` imported `androidx.compose.ui.unit.Density` but not `.dp`. One import from a package does not auto-import sibling extensions. |
| **Solution** | Added `import androidx.compose.ui.unit.dp`. |
| **Prevention** | Each extension property (`.dp`, `.sp`, etc.) must be imported explicitly even when another member of the same package is already imported. |

---

### BUG-017 — `Unresolved reference: parent` in FileOpDialogHost

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Root Cause** | `FileOpDialog.CreateFile` and `CreateFolder` declare `parentNode: FileNode`, but `FileOpDialogHost` accessed `dialog.parent` (missing the `Node` suffix) at 4 call sites. |
| **Solution** | Replaced all 4 `dialog.parent` references with `dialog.parentNode`. |
| **Prevention** | When calling into sealed-class variants, verify property names against the variant declaration — a name mismatch is a compile error, not a runtime one. |

---

### BUG-018 — Monaco editor WebView crash terminated app

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Issue** | App terminated without a stack trace when the Monaco WebView renderer crashed (memory pressure, large file, complex syntax highlighting). |
| **Root Cause** | `EditorPane.kt` created the Monaco `WebView` with a default `WebViewClient()` whose `onRenderProcessGone` returns `false` by default — Android kills the app process. BUG-006 fixed the preview WebView but missed the editor WebView. |
| **Solution** | Added custom `WebViewClient` that overrides `onRenderProcessGone` to set `editorCrashed = true` and return `true`. When `editorCrashed` is true, an `EditorCrashedBox` (error + "Reload Editor" button) is shown. Reload calls `loadUrl(...)` on the same WebView object — valid after a renderer crash on API 26+. `editorView` lambda shared across all 4 layout branches. |
| **Prevention** | When fixing a crash in one WebView, immediately apply the same fix to all WebViews in the same composable. |

---

### BUG-019 — `togglePreview()` / Run crashed app: main-thread execution + no error handling

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | viewmodel |
| **Issue** | Pressing Run terminates the application. |
| **Root Cause** | Three compounding problems: (1) `togglePreview()` was a plain `fun` called directly from a Compose click handler on the main thread. (2) `markdownToPreviewHtml()` ran synchronously on the main thread — ANR risk and any exception killed the app. (3) No `try-catch` anywhere in the function — uncaught exception from a main-thread ViewModel fun terminates the process. |
| **Solution** | Added `requestRun()` as the project-scoped entry point. Converted `togglePreview()` to `viewModelScope.launch { … }`. Moved `markdownToPreviewHtml()` into `withContext(Dispatchers.Default)`. Wrapped the content-generation block in `runCatching { }` — exceptions surface as `statusMessage`. Wired Run button to `::requestRun`. |
| **Note** | Three additional crash paths (Binder IPC overflow, unattached WebView, no global handler) are tracked as F001 for a follow-up fix. |
| **Prevention** | ViewModel functions called from Compose click handlers must either be safe funs with no failure paths, or use `viewModelScope.launch { runCatching { … } }`. Heavy computation must always run on `Dispatchers.Default`. |

---

### BUG-020 — Sidebar showed file tree on Projects and Settings screens

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Root Cause** | `sidebarContent` lambda used `if (projectRootUri != null)` — checked only whether a project was open, not which screen was active. |
| **Solution** | Replaced with `when (uiState.currentScreen)` — `EDITOR` shows file tree, `PROJECTS`/`SETTINGS` show only nav strip. |
| **Prevention** | Sidebar content decisions must consider both `currentScreen` and project state. |

---

### BUG-021 — Top bar: app title fallback + Search hidden in overflow

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Root Cause** | Breadcrumb fell back to hard-coded `"Android IDE"`. Find was buried in overflow. Action order didn't match spec (Save → Search → Run). |
| **Solution** | Empty string fallback. Dedicated `Search` `IconButton` (Find). Removed Find from overflow. Action order: Save (if !autoSave) → Search → Run → overflow. |
| **Prevention** | Hardcoded app name strings in UI composables must be empty strings when nothing is open. Frequently used actions must be first-class icon buttons. |

---

### BUG-022 — Path navigator opened sidebar; broken for collapsed or single-child folders

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Root Cause** | Path was only tappable when `siblings.isNotEmpty() || ancestors.isNotEmpty()`. Clicking an ancestor called `onRevealInTree` which opened the sidebar. |
| **Solution** | Path always tappable when `activeTab != null`. Ancestor `onClick` only closes the navigator dropdown — sidebar not touched. Disabled hint item shown when parent not expanded. |
| **Note** | A full independent SAF-backed navigator (not dependent on tree expand state) is tracked as F003. |
| **Prevention** | Path navigation composables must not gate their clickability on the content they would show. Path navigation and sidebar state must be orthogonal. |

---

### BUG-023 — No temporary tab behavior — all files opened as permanent tabs

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | viewmodel + ui |
| **Root Cause** | `EditorTab` had no `isTemporary` flag. `openFileInternal` always created permanent tabs. |
| **Solution** | Added `isTemporary: Boolean = false` to `EditorTab`. Single-tap → temporary tab (replaces previous preview). Double-tap → permanent. First edit → pin (sets `isTemporary = false`). "Keep Open" in tab overflow. Temporary tabs render with italic title. |
| **Prevention** | File-open actions must always distinguish preview (single-tap) from pin (double-tap or first edit). This is standard IDE behavior (VS Code, IntelliJ). |

---

### BUG-024 — Keyboard toolbar: indent broken without selection; two redundant keyboard toggle buttons

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Root Cause** | `editor.action.indentLines` is a no-op without a selection. Two separate Show/Hide Keyboard buttons consumed 2 of 6 toolbar slots. |
| **Solution** | Added `smartIndent`/`smartOutdent` JS handlers: check `editor.getSelection().isEmpty()` — if no selection, insert spaces at cursor; if selection, run `indentLines`/`outdentLines`. Single stateful keyboard toggle button. |
| **Prevention** | Monaco action IDs must be tested with and without a selection. State-ful interactions must track their own state — not duplicate as two buttons. |

---

### BUG-025 — Editor single-tap did not reliably show the soft keyboard

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Root Cause** | `setOnTouchListener` called `v.requestFocus()` but not `InputMethodManager.showSoftInput`. The JS `editor.focus()` fires 50ms after touchend — after the Android IME decision window closes — so the IME did not show. |
| **Solution** | On `ACTION_UP`, call both `v.requestFocus()` AND `imm.showSoftInput(v, SHOW_IMPLICIT)`. |
| **Prevention** | Any WebView inside Compose that needs the IME must call `showSoftInput` explicitly after `requestFocus`. `requestFocus` alone is insufficient in Compose. |

---

### BUG-026 — Monaco renderWhitespace hardcoded; no Phase-4 disabled placeholders in Settings

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor/ui |
| **Root Cause** | `renderWhitespace: 'selection'` baked into Monaco creation call. `EditorSettings` and `SetEditorOptions` did not expose it. No Phase-4 placeholders in Settings so users couldn't distinguish missing vs. unimplemented features. |
| **Solution** | Added `renderWhitespace: String = "selection"` to `EditorSettings` and `renderWhitespace: String?` to `SetEditorOptions`. Both `setEditorSettings` and `onEditorReady` pass it through the pipeline. Settings screen gained a "Render Whitespace" row (None/Selection/All `FilterChip`). Two disabled Switch rows for "Code Completion" and "Code Folding" (labeled "Phase 4"). |
| **Prevention** | Any Monaco option affecting the user's writing experience must be in the `EditorSettings → SetEditorOptions → JS` pipeline from day one. |

---

## Architectural Corrections

### AC-001 — Tech Stack Migration: Slint/Rust → Kotlin/Jetpack Compose

**Date:** 2026-06-12

The original design used Rust + Slint UI with a JNI bridge and `NativeActivity` entry point. Fully replaced with Kotlin + Jetpack Compose (`ComponentActivity`), Monaco WebView as a Compose `AndroidView`, and SAF accessed directly via `ContentResolver`. No JNI, no NDK, no native code. See `TECH_STACK_MIGRATION.md` for the full migration record.

---

### AC-002 — Navigation refactor: bottom NavigationBar removed, sidebar-only navigation

**Date:** 2026-06-13

**Original:** Bottom `NavigationBar` with Projects / Editor / Settings tabs. Sidebar only existed within `IdeScreen`.

**New:** `IdeScreen` is always the root screen. A persistent sidebar (drawer on narrow, permanent column on wide) handles all navigation. Navigation buttons: Projects, Editor, Git (Phase 3), Terminal (Phase 2), Settings. No bottom `NavigationBar`.

**Rationale:** Bottom tabs consume vertical space needed for code. A sidebar keeps navigation reachable via one tap from any screen while maximising editor real estate.

---

Last updated: 2026-06-13
