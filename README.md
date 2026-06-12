# PROJECT_PLAN.md — Android IDE

## Project Overview

A production-grade Android IDE designed for mobile-first developers who work entirely from an Android phone, with optional assistance from remote AI coding tools and GitHub Actions CI/CD.

The IDE combines VS Code-style project management, Monaco Editor, an integrated terminal with embedded Linux runtime, Git integration, Language Server Protocol support, and an extension platform — all in a single native Android application.

**Primary constraint:** Every workflow must remain operable from an Android phone. No desktop environment is assumed.

---

## Current Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose + Material3 |
| Activity | `ComponentActivity` (`setContent {}`) |
| Editor | Monaco 0.52.0 in `WebView` (Compose `AndroidView`) |
| File I/O | Android Storage Access Framework (SAF) via `SafRepository.kt` |
| State | `AndroidViewModel` + `StateFlow` |
| Build | Gradle 8.7 + AGP 8.3.2 + Kotlin DSL |
| CI | GitHub Actions — lint + debug APK, release APK |

---

## Architecture Overview

```
android-ide/
├── android/
│   ├── app/
│   │   ├── build.gradle.kts          — App module build config
│   │   └── src/main/
│   │       ├── AndroidManifest.xml   — Permissions, MainActivity declaration
│   │       └── res/                  — Launcher icon resources
│   ├── build.gradle.kts              — Root build file (plugin declarations)
│   ├── settings.gradle.kts           — Project structure
│   └── gradle.properties             — Gradle daemon settings
├── java/dev/androidide/              — All Kotlin source files
│   ├── MainActivity.kt               — ComponentActivity entry point
│   ├── ui/
│   │   ├── IdeScreen.kt              — Root adaptive layout composable
│   │   ├── theme/                    — Color, typography, Material3 theme
│   │   └── components/
│   │       ├── EditorPane.kt         — Monaco WebView + optional preview WebView
│   │       ├── EditorTabBar.kt       — Open-file tab row
│   │       ├── FileTreePanel.kt      — SAF file tree sidebar
│   │       └── IdeStatusBar.kt       — Cursor position + language status bar
│   ├── editor/
│   │   ├── EditorBridge.kt           — @JavascriptInterface bridge (Monaco ↔ Kotlin)
│   │   └── EditorMessage.kt          — EditorInbound / EditorOutbound sealed classes
│   ├── saf/
│   │   └── SafRepository.kt          — SAF ContentResolver operations (coroutines)
│   └── viewmodel/
│       ├── IdeViewModel.kt            — AndroidViewModel — IDE state + coordination
│       └── model/
│           ├── IdeUiState.kt         — Complete observable UI state
│           ├── EditorTab.kt          — Open file tab state
│           └── FileNode.kt           — File tree node + tree manipulation helpers
├── assets/editor/
│   ├── index.html                    — Monaco shell page (loaded by WebView)
│   ├── monaco-init.js                — Monaco init + Kotlin bridge protocol
│   └── vs/                           — Monaco AMD bundle (git-ignored; fetched by script)
├── scripts/
│   └── fetch-monaco.sh               — Downloads Monaco 0.52.0 into assets/editor/vs/
└── .github/workflows/build.yml       — CI: lint + debug APK, release APK
```

### Communication Contract

All inter-module communication is via **explicit typed interfaces** only. No module may access another module's internal types or state directly. Each package exposes a public API that is the sole communication surface.

### Monaco ↔ Kotlin Bridge Protocol

```
Kotlin → JS (via WebView.evaluateJavascript):
  window.androidIDE.receiveMessage({ type, ...payload })

JS → Kotlin (via @JavascriptInterface):
  window.AndroidBridge.onMessage(jsonString)
```

Message types:

| Direction | Type | Fields |
|-----------|------|--------|
| Kotlin → JS | `loadFile` | path, content, language |
| Kotlin → JS | `setTheme` | theme |
| Kotlin → JS | `setFontSize` | size |
| Kotlin → JS | `requestSave` | path |
| Kotlin → JS | `closeTab` | path |
| JS → Kotlin | `ready` | — |
| JS → Kotlin | `contentChanged` | path, content |
| JS → Kotlin | `cursorMoved` | line, column |
| JS → Kotlin | `fileSaved` | path |

---

## Subsystem Descriptions

### `editor/` — Monaco Editor Bridge
**Purpose:** Bidirectional bridge between the Monaco editor WebView and Kotlin. Manages editor state, file open/close lifecycle, and communicates edits to the ViewModel.

**Responsibilities:**
- `@JavascriptInterface` reception of Monaco messages
- Outbound JS evaluation via `WebView.evaluateJavascript()`
- Typed message protocol (`EditorInbound` / `EditorOutbound`)
- Tab management (multiple open files)

**Dependencies:** `viewmodel/`

---

### `saf/` — Storage Access Framework
**Purpose:** All project file access uses SAF (Storage Access Framework) via `SafRepository`. Manages project directory trees, file read/write, and document URIs.

**Responsibilities:**
- SAF URI resolution and content provider access
- Directory tree listing (children of tree/document URIs)
- File CRUD operations (create, read, write, delete, rename)
- Coroutine-based I/O on `Dispatchers.IO`

**Dependencies:** None (foundation layer)

---

### `viewmodel/` — IDE State Management
**Purpose:** Single source of truth for IDE state. Bridges SAF file operations, editor bridge events, and Compose UI updates.

**Responsibilities:**
- Project open / file tree population
- Directory expand/collapse with lazy SAF loading
- Editor tab lifecycle (open, select, close)
- File save (explicit and Ctrl+S triggered)
- Cursor position and language state

**Dependencies:** `editor/`, `saf/`

---

### `ui/` — Jetpack Compose UI
**Purpose:** All UI composables — adaptive layout (wide/narrow), file tree sidebar, tab bar, Monaco WebView host, status bar.

**Responsibilities:**
- Adaptive layout (wide ≥ 600dp, narrow < 600dp)
- SAF picker integration (`rememberLauncherForActivityResult`)
- Monaco `WebView` lifecycle via `remember {}` in `EditorPane`
- Live preview WebView (conditionally shown alongside editor)

**Dependencies:** `viewmodel/`, `editor/`

---

## Milestone Map

| Milestone | Description | Target Phase |
|-----------|-------------|-------------|
| M1 | App shell launches, Compose UI renders | Phase 1 |
| M2 | User can open a directory and browse files | Phase 1 |
| M3 | User can open a file in Monaco and edit it | Phase 1 |
| M4 | User can save file changes | Phase 1 |
| M5 | Terminal UI renders and connects to PTY | Phase 2 |
| M6 | Linux runtime boots and accepts commands | Phase 2 |
| M7 | Package installation works | Phase 2 |
| M8 | Git clone, commit, push, pull work | Phase 3 |
| M9 | LSP diagnostics visible in editor | Phase 4 |
| M10 | First extension loads successfully | Phase 5 |

---

## Phase Breakdown

### Phase 1 — Foundation
**Goal:** User can create projects, open projects, browse files, and edit files.

**Status: COMPLETE ✅**

Deliverables:
- [x] Compose application shell (MainActivity, IdeScreen, adaptive layout)
- [x] SAF bridge — directory tree, file read/write (`SafRepository.kt`)
- [x] Monaco Editor WebView integration (`EditorPane.kt`, `EditorBridge.kt`)
- [x] File tree explorer (expand/collapse, file open on tap)
- [x] Tab management (open, select, close, dirty indicator)
- [x] File save (Ctrl+S + toolbar save button)
- [x] Cursor position + language in status bar
- [x] Launcher icon (adaptive icon, API 26+)
- [x] GitHub Actions CI (lint + debug APK, release APK)
- [x] Monaco offline bundle (`fetch-monaco.sh`, git-ignored `vs/`)

---

### Phase 2 — Linux Runtime and Terminal
**Goal:** User can run commands and install packages.

Deliverables:
- [ ] Terminal UI (Compose)
- [ ] PTY creation and management
- [ ] Linux runtime setup (proot environment bootstrap)
- [ ] Package manager bridge (apt/pkg)
- [ ] Terminal session persistence

Success Criteria: User can open a terminal, run shell commands, and install packages.

---

### Phase 3 — Git Integration
**Goal:** User can manage repositories entirely within the IDE.

Deliverables:
- [ ] Git status panel
- [ ] Stage/unstage changes
- [ ] Commit dialog
- [ ] Branch list and create/switch
- [ ] Push/pull operations
- [ ] Clone repository dialog
- [ ] Diff viewer
- [ ] Credential storage (SSH key manager, HTTPS token)

---

### Phase 4 — Language Intelligence
**Goal:** IDE provides modern code intelligence.

Deliverables:
- [ ] LSP server lifecycle management
- [ ] Diagnostics display (squiggles + problem panel)
- [ ] Autocomplete
- [ ] Go-to-definition
- [ ] Hover documentation
- [ ] Find references
- [ ] Code actions (quick fixes)

---

### Phase 5 — Extension Platform
**Goal:** Third-party features can be installed safely.

Deliverables:
- [ ] Extension package format spec
- [ ] Extension loader
- [ ] Sandboxed execution model
- [ ] Permission system
- [ ] Extension manager UI
- [ ] Example extension (hello-world)

---

## Progress Tracking

| Phase | Status | Completion |
|-------|--------|-----------|
| Phase 1 — Foundation | **Complete** ✅ | 100% |
| Phase 2 — Linux Runtime | Not Started | 0% |
| Phase 3 — Git | Not Started | 0% |
| Phase 4 — Language Intelligence | Not Started | 0% |
| Phase 5 — Extensions | Not Started | 0% |

---

## Architecture Decisions

### MD-001 — ComponentActivity over AppCompatActivity

**Date:** 2026-06-12

`MainActivity` extends `ComponentActivity` (from `androidx.activity:activity-compose`) rather than `AppCompatActivity`. `ComponentActivity` is the minimal Activity for Compose and is the recommended baseline. `AppCompatActivity` provides backwards-compatible Material components that are redundant when using Material3 Compose directly.

---

### MD-002 — AndroidViewModel receives Application for SAF Context

**Date:** 2026-06-12

`IdeViewModel` extends `AndroidViewModel(application)` so it can hold an `Application` reference for SAF operations (`ContentResolver`) without leaking an `Activity` reference. `SafRepository` is instantiated once in the ViewModel constructor with the Application context.

---

### MD-003 — Monaco WebView remembered across recompositions

**Date:** 2026-06-12

The Monaco `WebView` in `EditorPane.kt` is wrapped in `remember {}`. This prevents the WebView from being destroyed and re-created on every recomposition (which would reset Monaco's editor state). The `AndroidView` update lambda is intentionally a no-op — all editor updates are driven via `evaluateJavascript()` from `EditorBridge.kt`.

---

### MD-004 — JS bridge protocol unchanged from prior design

**Date:** 2026-06-12

`monaco-init.js` and `index.html` define a stable wire protocol. The Kotlin `EditorBridge.kt` produces the exact same `window.androidIDE.receiveMessage({type, ...})` JS call format. This protocol must not be changed without updating both sides simultaneously.

---

### MD-005 — SAF child URI docId extraction

**Date:** 2026-06-12

`DocumentsContract.getTreeDocumentId()` returns the **root** document ID for all tree URIs, including child document URIs (which have the form `/tree/<treeDocId>/document/<docId>`). When listing children of a subdirectory, `getDocumentId()` must be used instead — it returns the actual subdirectory's document ID. `SafRepository.listChildren()` detects which to use by checking for the "document" path segment.

Last updated: 2026-06-12
