# DEBUG_LOG.md — Android IDE

Historical debugging record. Every bug fix, architectural correction, and failed design decision must be recorded here.

Future contributors must be able to understand previous mistakes without rediscovering them.

---

## Log Format

| Field | Description |
|-------|-------------|
| Date | ISO 8601 date (YYYY-MM-DD) |
| Subsystem | Module where the issue occurred |
| Issue | Brief description of the problem observed |
| Root Cause | Why it happened |
| Files Modified | Comma-separated list of modified files |
| Solution | What was changed to fix it |
| Prevention | How to avoid this class of bug in the future |

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
| **Solution** | Added `android:icon="@mipmap/ic_launcher"` and `android:roundIcon` to `<application>`. Created adaptive icon: vector "A" monogram (VS-Code blue #007ACC) on dark IDE background (#1e1e1e). Since `minSdk = 26`, only the `mipmap-anydpi-v26/` density bucket is needed. |
| **Prevention** | Always add `android:icon` to `<application>` and create `mipmap-anydpi-v26/` icon resources when creating a new Android project. |

---

### BUG-002 — Monaco editor required internet — offline use impossible

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | editor |
| **Issue** | On a device without internet, the editor WebView showed "Loading editor…" indefinitely. |
| **Root Cause** | `index.html` and `monaco-init.js` loaded Monaco from the unpkg CDN at runtime. |
| **Files Modified** | `assets/editor/index.html`, `assets/editor/monaco-init.js`, `scripts/fetch-monaco.sh` (created), `.gitignore` |
| **Solution** | Changed both files to use relative local paths (`vs/loader.js`, `vs`). Created `scripts/fetch-monaco.sh` to download Monaco 0.52.0 from npm into `assets/editor/vs/`. Added `assets/editor/vs/` to `.gitignore` (~20 MB). Added CI step to run the script before `./gradlew` in both jobs. |
| **Prevention** | Never load runtime assets from a CDN in a mobile app. Bundle all required JS/CSS in the APK. Add `vs/` (or equivalent) to `.gitignore` and document the download step. |
| **Notes** | The script is idempotent — exits early if `vs/loader.js` already exists. Monaco version is pinned in `fetch-monaco.sh`; change `MONACO_VERSION` there to upgrade. |

---

### BUG-003 — SafRepository.listChildren returns root children when expanding subdirectory

| Field | Value |
|-------|-------|
| **Date** | 2026-06-12 |
| **Subsystem** | filesystem |
| **Issue** | Expanding a subdirectory in the file tree showed the project root's children again instead of the subdirectory's children. |
| **Root Cause** | `listChildren` called `DocumentsContract.getTreeDocumentId(parentUri)` for all URIs where `isTreeUri()` is true. `getTreeDocumentId` always returns the **root** document ID. Child directory URIs built by `buildDocumentUriUsingTree` have the path `/tree/<treeDocId>/document/<docId>` — `isTreeUri()` returns true for these, but the correct document ID is in the "document" segment, not the "tree" segment. |
| **Files Modified** | `saf/SafRepository.kt` |
| **Solution** | In `listChildren`, detect whether the URI has a "document" path segment. If yes, use `DocumentsContract.getDocumentId(parentUri)` to get the subdirectory's document ID. If no, use `getTreeDocumentId(parentUri)` for the root tree URI. |
| **Prevention** | SAF has two URI shapes: plain tree URIs (`/tree/<docId>`) from `ACTION_OPEN_DOCUMENT_TREE` and document-within-tree URIs (`/tree/<treeDocId>/document/<docId>`) from `buildDocumentUriUsingTree`. Always distinguish between them when extracting the document ID. Never assume `getTreeDocumentId` gives the correct ID for child document URIs. |

---

### BUG-004 — `@SuppressLint("JavascriptInterface")` missing from EditorPane

| Field | Value |
|-------|-------|
| **Date** | 2026-06-12 |
| **Subsystem** | editor |
| **Issue** | Lint reported `[JavascriptInterface]` as a build-blocking error even after `@JavascriptInterface` was correctly applied to `EditorBridge.onMessage` and an explicit `: EditorBridge` type annotation was added to `editorBridge`. |
| **Root Cause** | Android lint resolves the type of the object passed to `addJavascriptInterface(obj, name)` **statically**. `editorBridge` is initialised via `remember<T> { EditorBridge() }`. Even with an explicit variable type annotation, lint sees the type as the generic `T` (the return of `remember<T>`) at the call site inside the `apply {}` lambda and cannot find `@JavascriptInterface` on `T`. The annotation is correctly present at runtime — this is a lint static analysis limitation, not a functional bug. |
| **Files Modified** | `ui/components/EditorPane.kt` |
| **Solution** | Added `"JavascriptInterface"` to the existing `@SuppressLint(...)` on the `EditorPane` composable function: `@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "WebViewClientOnReceivedSslError")`. A comment above the annotation documents that the `@JavascriptInterface` IS present on `EditorBridge.onMessage` — the suppression silences a false-positive. |
| **Prevention** | Any time `addJavascriptInterface(obj, name)` is called where `obj` is produced by a generic function (e.g. `remember<T>{}`, `lazy<T>{}`, or any function returning a type parameter), lint cannot see through the generic and will report `[JavascriptInterface]` regardless of annotations on the concrete class. Always add `@SuppressLint("JavascriptInterface")` at the enclosing declaration level and document why. |

---

### BUG-005 — `application` property inaccessible in `IdeViewModel.isSystemDark()`

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | viewmodel |
| **Issue** | `compileDebugKotlin` failed with `Cannot access 'application': it is invisible (private in a supertype) in 'IdeViewModel'` at `IdeViewModel.kt:95`. |
| **Root Cause** | `AndroidViewModel.application` was made `private` in the supertype as of `androidx.lifecycle:lifecycle-viewmodel:2.6.0`. The project uses `lifecycle-viewmodel-compose:2.7.0`, which carries this visibility change. `isSystemDark()` accessed `application` as though it were a `protected` property, which it no longer is. |
| **Files Modified** | `viewmodel/IdeViewModel.kt` |
| **Solution** | Replaced `application.resources.configuration.uiMode` with `getApplication<Application>().resources.configuration.uiMode`. `AndroidViewModel.getApplication<T>()` is the stable public accessor that has always been available and is not affected by the visibility change. |
| **Prevention** | Never access `AndroidViewModel.application` as a property directly. Always use `getApplication<Application>()`. The `application` property was historically `protected` but this is not guaranteed across lifecycle versions. The public method is the only stable API. |

---

### BUG-006 — Preview WebView crash terminates app

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Issue** | App terminates without a Java stack trace when the live-preview WebView renders a malformed or resource-heavy HTML page. |
| **Root Cause** | `WebViewClient.onRenderProcessGone` was not overridden. Android's default implementation returns `false`, which terminates the entire application process when the WebView renderer crashes or is killed by the OS. |
| **Files Modified** | `ui/components/EditorPane.kt` |
| **Solution** | Override `onRenderProcessGone` on the preview WebView's `WebViewClient` to return `true` (crash handled) and set a `previewCrashed` state flag. When `previewCrashed` is true, the WebView is replaced by an error placeholder composable instead of rendering invisibly or crashing. |
| **Prevention** | Any production `WebView` that loads arbitrary content must override `onRenderProcessGone` and return `true`. The default `false` terminates the app. This applies especially to preview/sandbox WebViews. |

---

### BUG-007 — `loadUrl("data:...")` with large HTML caused OOM and URL-length issues

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Issue** | The preview WebView was loaded via a `data:text/html;base64,...` URL. For large HTML files (~50 kB), this produced ~70 kB URL strings, causing GC pressure and occasional `OutOfMemoryError` on low-RAM devices. |
| **Root Cause** | Base64 encoding a large HTML document creates a ~1.33× larger string, held in memory during both encoding and URL construction. URL-based loading also limits content to what can fit in a URI. |
| **Files Modified** | `viewmodel/IdeViewModel.kt` (removed base64 encoding), `viewmodel/model/IdeUiState.kt` (renamed `previewUrl` → `previewHtmlContent`), `ui/components/EditorPane.kt` (use `loadDataWithBaseURL`) |
| **Solution** | Pass raw HTML string directly as `previewHtmlContent` in `IdeUiState`. Load in the WebView via `loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)`. No base64 encoding, no URL length limit, no extra memory for the encoded string. |
| **Prevention** | Never use `data:` URL scheme for loading arbitrary HTML into a WebView. Always use `loadData()` or `loadDataWithBaseURL()` which accept the content directly. |

---

### BUG-008 — Horizontal swipe in Monaco editor accidentally opens the sidebar drawer

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | Horizontal swipe in the Monaco editor (scrolling a long code line) triggered the `ModalNavigationDrawer` to open, interrupting editing. |
| **Root Cause** | `ModalNavigationDrawer` defaults to `gesturesEnabled = true`, intercepts horizontal swipe events that start near the left edge of the screen. Monaco's horizontal scroll (which starts from anywhere in the editor) is indistinguishable from a drawer-open gesture. |
| **Files Modified** | `ui/IdeScreen.kt` |
| **Solution** | Set `gesturesEnabled = false` on the `ModalNavigationDrawer`. The sidebar is opened exclusively via the hamburger icon in the top app bar. |
| **Prevention** | Always set `gesturesEnabled = false` on `ModalNavigationDrawer` when the content area contains a horizontally-scrollable surface (e.g. WebView, HorizontalPager, Map). |

---

## Architectural Corrections

### AC-001 — Tech Stack Migration: Slint/Rust → Kotlin/Jetpack Compose

**Date:** 2026-06-12

**Original design:**
- Rust application with Slint UI framework
- JNI bridge for SAF and WebView
- NativeActivity entry point via `android_main()`
- `IDEActivity extends NativeActivity` to layer Monaco WebView above native Surface

**Migrated design:**
- Kotlin + Jetpack Compose
- `ComponentActivity.setContent {}` entry point
- Monaco WebView as a Compose `AndroidView` inside `EditorPane.kt`
- SAF accessed directly via `ContentResolver` in `SafRepository.kt`
- No JNI, no NDK, no native code

**Consequence:** All Rust/Slint source files removed. Build pipeline simplified to standard `./gradlew assembleRelease`. See TECH_STACK_MIGRATION.md for the full migration record.

---

### AC-002 — Navigation refactor: bottom NavigationBar removed, sidebar-only navigation

**Date:** 2026-06-13

**Original design:**
- Bottom `NavigationBar` with Projects / Editor / Settings tabs
- `AppRoot` switched between `ProjectsScreen`, `IdeScreen`, `SettingsScreen` at the top level
- Sidebar existed only within `IdeScreen` (editor screen only)

**New design:**
- `IdeScreen` is always the root screen — `AppRoot` renders nothing else
- A persistent sidebar (always accessible via drawer or permanent column on wide screens) handles all navigation
- Navigation buttons in the sidebar: Projects, Editor, Git (Phase 2), Terminal (Phase 2), Settings
- No bottom `NavigationBar`

**Rationale:** On mobile developer tools, bottom tabs consume vertical space needed for code. A sidebar keeps navigation reachable via one tap from any screen while maximising editor real estate. The drawer gesture (`gesturesEnabled = false`) prevents accidental opens from editor swipes.

Last updated: 2026-06-13

---

### BUG-009 — Sidebar nav panel consumed 220dp vertical space, leaving too little for the file tree

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | The sidebar navigation panel was a vertical Column of 5 labelled `NavigationDrawerItem` rows (~44dp each = ~220dp total), leaving only ~40% of the sidebar height for the file tree on a typical 800dp-tall phone. |
| **Root Cause** | `NavigationDrawerItem` includes a label + optional badge — it's designed for drawers where each item is a primary destination with its own row. Using 5 such items for a utility nav strip wastes space. |
| **Files Modified** | `ui/IdeScreen.kt` |
| **Solution** | Replace the vertical item list with a single `Row` of 5 `IconButton`s, each 48×48dp (standard touch target). The entire nav strip is now exactly 48dp tall. Labels are provided only as `contentDescription` for accessibility; a long-press tooltip is available via `TooltipBox`. |
| **Prevention** | Use a compact icon-only row for utility navigation when vertical space is scarce. `NavigationDrawerItem` with text labels is appropriate for a bottom NavBar but wastes height in a sidebar. |

---

### BUG-010 — File open in sidebar did not close the drawer, leaving editor hidden

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | After tapping a file in the sidebar drawer on a narrow screen, the drawer remained open. The editor was technically visible behind it, but the drawer covered the full screen. The user had to swipe or tap the backdrop to dismiss it. |
| **Root Cause** | `onFileClick` in `fileTreePanelContent` called only `ideViewModel.openFile()` + `navigateTo(EDITOR)`. The drawer's coroutine scope was not reachable from inside `FileTreePanel`. |
| **Files Modified** | `ui/IdeScreen.kt` |
| **Solution** | Threaded a `onCloseDrawer: (() -> Unit)?` lambda through the sidebar content lambda. On narrow screens, `closeDrawer` is wired to `scope.launch { drawerState.close() }`. On file open and file search select, `onCloseDrawer?.invoke()` is called immediately after `openFile`. On wide screens the lambda is null (no drawer to close). |
| **Prevention** | Any action inside a modal drawer that should "complete and dismiss" must close the drawer as part of the same event handler. |

---

### BUG-011 — Top app bar showed file name as a headline title + path as a separate subtitle, wasting toolbar height

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | `IdeTopBar` showed two text items: a large file-name headline and a smaller path sub-headline. Combined with action icons they pushed the bar to 64dp+, leaving less space for the editor. |
| **Root Cause** | Copied the default `CenterAlignedTopAppBar` template that places a large title. |
| **Files Modified** | `ui/IdeScreen.kt` |
| **Solution** | Single `Text` item in the `title` slot showing only the full path (e.g. `/src/pages/home.html`). The path is tappable: a dropdown lists ancestor directories (to reveal in tree) and sibling files (quick switch). Top bar height reduced to 48dp. |
| **Prevention** | In a code editor, the file path is the title. Don't show a separate headline and subtitle — one single-line path item is sufficient. |

---

### BUG-012 — Keyboard toolbar used horizontalScroll, hiding most actions off-screen

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | ui |
| **Issue** | The keyboard toolbar placed 13 icon buttons in a single `Row` wrapped in `horizontalScroll`. On a 360dp-wide phone, only ~5 icons were visible without scrolling. Users had to scroll right to find Cut, Copy, Paste. The scroll position was not persisted across keyboard shows/hides. |
| **Root Cause** | `horizontalScroll` was used as a quick way to fit all actions without designing a second page. |
| **Files Modified** | `ui/components/EditorPane.kt` |
| **Solution** | Replace `horizontalScroll` + single `Row` with a 2-page `HorizontalPager`. Page 1: navigation + undo/redo (7 actions). Page 2: clipboard + select + keyboard control (6 actions). Page indicator dots below. Icons 24dp, touch targets 44dp. All actions visible without horizontal scrolling. |
| **Prevention** | Never use horizontal scroll for a toolbar. Users won't discover hidden items. Use paging or fewer items. |

---

### BUG-013 — Monaco WebView did not request focus on tap, so keyboard did not appear

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Issue** | Tapping the Monaco editor area did not reliably summon the soft keyboard. The WebView had focus but had not been configured as focusable in touch mode, so Android's IME did not activate. |
| **Root Cause** | `isFocusableInTouchMode` was not set on the WebView. Without it, the view can receive focus from code but not from a touch event, which is what the IME observes. |
| **Files Modified** | `ui/components/EditorPane.kt`, `assets/editor/monaco-init.js` |
| **Solution** | Set `isFocusable = true` and `isFocusableInTouchMode = true` on the WebView. Added `setOnTouchListener` that calls `requestFocus()`. On the JS side, added `click` + `touchend` listeners on the editor DOM node that call `editor.focus()` (with a 50ms delay on touchend to let tap-selection settle). |
| **Prevention** | Any editable WebView that should accept keyboard input on Android must have `isFocusableInTouchMode = true`. |

---

### BUG-014 — Paste from keyboard toolbar used WebView clipboard API (slow, permission-gated)

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | editor |
| **Issue** | The toolbar "Paste" button called `editor.action.clipboardPasteAction` in Monaco. On Android, this invokes the WebView's clipboard API, which requires user permission on API 29+ and is noticeably slow (100–300ms latency before text appears). |
| **Root Cause** | Monaco's built-in paste action uses the browser clipboard API (`navigator.clipboard.readText`), which Android WebView implements through a permission system. |
| **Files Modified** | `ui/components/EditorPane.kt`, `viewmodel/IdeViewModel.kt` |
| **Solution** | Added `onPasteFromClipboard` callback to `KeyboardToolbar`. When the Paste button is tapped, Kotlin reads directly from `android.content.ClipboardManager` (no permission needed) and sends the text as an `insertText` command. JS uses `editor.executeEdits` (atomic, preserves undo history) instead of `editor.trigger('keyboard', 'type', ...)`. |
| **Prevention** | Always read the Android clipboard from Kotlin when in a Compose/WebView hybrid. Never rely on the WebView's `navigator.clipboard` API on Android — it's slow and requires runtime permissions. |

---

### BUG-015 — `clipboardItems` renamed from `clipboard: FileNode?` — multi-item cut/copy not supported

| Field | Value |
|-------|-------|
| **Date** | 2026-06-13 |
| **Subsystem** | data |
| **Issue** | `IdeUiState.clipboard: FileNode?` stored only one file/folder at a time. Multi-select mode allowed selecting several items, but Cut/Copy from multi-select silently operated only on the single tapped node. |
| **Root Cause** | Original clipboard field was designed for single-item operations. |
| **Files Modified** | `viewmodel/model/IdeUiState.kt`, `viewmodel/IdeViewModel.kt`, `ui/components/FileTreePanel.kt`, `ui/IdeScreen.kt` |
| **Solution** | Renamed `clipboard: FileNode?` → `clipboardItems: List<FileNode>`. `copyFileNode` / `cutFileNode` now check `isMultiSelectMode` and, if active, collect all `selectedUris` into `clipboardItems`. Multi-select mode exits automatically after copy/cut so the user can navigate to a destination. `pasteFileNode` iterates over all items. `FileTreePanel` shows a count in the paste menu item ("Move 3 items here"). |
| **Prevention** | Design clipboard state as a list from the beginning, even when only one item is initially supported. |

---

## Architectural Corrections

### AC-001 — Tech Stack Migration: Slint/Rust → Kotlin/Jetpack Compose

**Date:** 2026-06-12

**Original design:**
- Rust application with Slint UI framework
- JNI bridge for SAF and WebView
- NativeActivity entry point via `android_main()`
- `IDEActivity extends NativeActivity` to layer Monaco WebView above native Surface

**Migrated design:**
- Kotlin + Jetpack Compose
- `ComponentActivity.setContent {}` entry point
- Monaco WebView as a Compose `AndroidView` inside `EditorPane.kt`
- SAF accessed directly via `ContentResolver` in `SafRepository.kt`
- No JNI, no NDK, no native code

**Consequence:** All Rust/Slint source files removed. Build pipeline simplified to standard `./gradlew assembleRelease`. See TECH_STACK_MIGRATION.md for the full migration record.

---

### AC-002 — Navigation refactor: bottom NavigationBar removed, sidebar-only navigation

**Date:** 2026-06-13

**Original design:**
- Bottom `NavigationBar` with Projects / Editor / Settings tabs
- `AppRoot` switched between `ProjectsScreen`, `IdeScreen`, `SettingsScreen` at the top level
- Sidebar existed only within `IdeScreen` (editor screen only)

**New design:**
- `IdeScreen` is always the root screen — `AppRoot` renders nothing else
- A persistent sidebar (always accessible via drawer or permanent column on wide screens) handles all navigation
- Navigation buttons in the sidebar: Projects, Editor, Git (Phase 2), Terminal (Phase 2), Settings
- No bottom `NavigationBar`

**Rationale:** On mobile developer tools, bottom tabs consume vertical space needed for code. A sidebar keeps navigation reachable via one tap from any screen while maximising editor real estate. The drawer gesture (`gesturesEnabled = false`) prevents accidental opens from editor swipes.

Last updated: 2026-06-13
