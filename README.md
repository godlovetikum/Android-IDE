# PROJECT_PLAN.md — Android IDE

## Project Overview

A production-grade Android IDE designed for mobile-first developers who work entirely from an Android phone, with optional assistance from remote AI coding tools and GitHub Actions CI/CD.

The IDE combines VS Code-style project management, Monaco Editor, an integrated terminal with embedded Linux runtime, Git integration, Language Server Protocol support, and an extension platform — all in a single native Android application.

**Primary constraint:** Every workflow must remain operable from an Android phone. No desktop environment is assumed.

---

## Architecture Overview

```
android-ide/
├── src/
│   ├── main.rs              — Application entry point
│   └── lib.rs               — Library root / public API surface
├── modules/
│   ├── editor/              — Monaco editor integration via WebView
│   ├── filesystem/          — Android Storage Access Framework bridge
│   ├── terminal/            — PTY management and terminal UI
│   ├── linux-runtime/       — Embedded Linux environment (proot/chroot)
│   ├── git/                 — Git operations via git2-rs
│   ├── lsp/                 — Language Server Protocol client
│   ├── extensions/          — Extension loader and lifecycle manager
│   ├── settings/            — Persistent user/project settings
│   └── documentation/       — In-app documentation viewer
├── ui/
│   └── *.slint              — Slint UI component definitions
├── android/
│   ├── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/
│   └── workflows/           — GitHub Actions build pipelines
└── docs/                    — Developer documentation
```

### Communication Contract

All inter-module communication is via **explicit typed interfaces** only. No module may access another module's internal types, functions, or state directly. Each module exposes a public API in its `mod.rs` that is the sole communication surface.

---

## Subsystem Descriptions

### `/editor`
**Purpose:** Embeds Monaco Editor inside an Android WebView, manages editor state, themes, file open/close lifecycle, and communicates edits to the filesystem module.

**Responsibilities:**
- Monaco WebView lifecycle management
- File load/save orchestration with filesystem
- Theme application and synchronization
- Editor configuration (indentation, font size, word wrap)
- Tab management (multiple open files)

**Dependencies:** `filesystem`, `settings`

---

### `/filesystem`
**Purpose:** Bridges Android Storage Access Framework (SAF) to the rest of the IDE. Manages project directory trees, file read/write, and file watching.

**Responsibilities:**
- SAF URI resolution and content provider access
- Directory tree construction and caching
- File CRUD operations (create, read, update, delete, rename, move)
- File watching / change detection
- Project root management

**Dependencies:** None (foundation layer)

---

### `/terminal`
**Purpose:** Provides a terminal emulator UI backed by a real PTY, connected to the embedded Linux runtime.

**Responsibilities:**
- Slint terminal UI widget
- PTY creation and management (via `nix` or `libc` bindings)
- Terminal emulation (VT100/ANSI)
- Session management (multiple terminal tabs)
- Process lifecycle (spawn, kill, resize)

**Dependencies:** `linux-runtime`

---

### `/linux-runtime`
**Purpose:** Manages the embedded Linux environment on Android (proot-based). Provides shell access, package management, and process isolation.

**Responsibilities:**
- proot/chroot environment setup and teardown
- Filesystem overlay management
- Package manager integration (apt/pkg for Termux-style environment)
- Environment variable management
- Binary/library path configuration

**Dependencies:** `filesystem`

---

### `/git`
**Purpose:** Full Git workflow integration using `git2-rs`.

**Responsibilities:**
- Clone, init repositories
- Stage, commit, amend
- Branch create/switch/merge/delete
- Push, pull, fetch
- Status, diff, log
- Credential management (SSH key, HTTPS token)

**Dependencies:** `filesystem`, `settings`

---

### `/lsp`
**Purpose:** Language Server Protocol client that spawns and manages language servers inside the Linux runtime, communicating via JSON-RPC.

**Responsibilities:**
- LSP server process lifecycle (start, stop, restart)
- JSON-RPC transport (stdin/stdout)
- Capability negotiation
- Diagnostics (errors/warnings)
- Completion requests
- Go-to-definition, hover, references
- Code actions

**Dependencies:** `linux-runtime`, `editor`, `filesystem`

---

### `/extensions`
**Purpose:** Extension loading, sandboxing, permission management, and lifecycle.

**Responsibilities:**
- Extension package format definition
- Extension discovery and loading
- Permission grant/revoke
- Extension enable/disable/uninstall
- Extension-to-IDE API bridge

**Dependencies:** `settings`

---

### `/settings`
**Purpose:** Persistent settings storage for user preferences and per-project configuration.

**Responsibilities:**
- Settings schema definition (typed)
- Serialization/deserialization (TOML)
- Default values
- Settings change notification
- Per-project settings override

**Dependencies:** `filesystem`

---

### `/documentation`
**Purpose:** In-app documentation viewer for built-in docs and project-specific READMEs.

**Responsibilities:**
- Markdown rendering
- Documentation navigation
- Search within documentation
- Link handling

**Dependencies:** `filesystem`

---

## Dependency Map

```
filesystem ─────────────────────────────────────┐
                                                 │
settings ──────────────────────────────────────┐│
                                               ││
editor ───── filesystem, settings              ││
terminal ─── linux-runtime                     ││
linux-runtime ─── filesystem                 ◄─┘│
git ──────── filesystem, settings            ◄──┘
lsp ──────── linux-runtime, editor, filesystem
extensions ─ settings
documentation ─ filesystem
```

---

## Milestone Map

| Milestone | Description | Target Phase |
|-----------|-------------|-------------|
| M1 | App shell launches, Slint UI renders | Phase 1 |
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

Deliverables:
- [ ] Slint application shell (main window, sidebar, editor area, status bar)
- [ ] Filesystem module — SAF bridge, directory tree, file read/write
- [ ] Monaco Editor WebView integration
- [ ] Project creation workflow (new project dialog, directory selection)
- [ ] Project opening workflow (recent projects, open directory)
- [ ] File tree explorer (expand/collapse, file open on tap)
- [ ] File save (explicit save, auto-save option)
- [ ] Settings module (basic schema, TOML persistence)
- [ ] Project tracking documents

Success Criteria: User can create a project, open files, edit them in Monaco, and save changes.

---

### Phase 2 — Linux Runtime and Terminal
**Goal:** User can run commands and install packages.

Deliverables:
- [ ] Terminal UI widget (Slint)
- [ ] PTY management layer
- [ ] Linux runtime setup (proot environment bootstrap)
- [ ] Package manager bridge (apt/pkg)
- [ ] Command execution pipeline
- [ ] Process lifecycle management (spawn, signal, kill)
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
- [ ] Diff viewer (inline or side-by-side)
- [ ] Credential storage (SSH key manager, HTTPS token)

Success Criteria: User can clone, commit, branch, push, and pull entirely within the IDE.

---

### Phase 4 — Language Intelligence
**Goal:** IDE provides modern code intelligence.

Deliverables:
- [ ] LSP server lifecycle management
- [ ] Diagnostics display (squiggles + problem panel)
- [ ] Autocomplete (triggered on keypress)
- [ ] Go-to-definition
- [ ] Hover documentation
- [ ] Find references
- [ ] Code actions (quick fixes)
- [ ] Language server presets (Rust Analyzer, TypeScript, Python LSP)

Success Criteria: Editor provides real-time diagnostics, completion, and navigation for at least two languages.

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

Success Criteria: A third-party extension can be installed and executed without compromising IDE stability.

---

## Progress Tracking

| Phase | Status | Completion |
|-------|--------|-----------|
| Phase 1 — Foundation | In Progress | 0% |
| Phase 2 — Linux Runtime | Not Started | 0% |
| Phase 3 — Git | Not Started | 0% |
| Phase 4 — Language Intelligence | Not Started | 0% |
| Phase 5 — Extensions | Not Started | 0% |

Last updated: 2026-06-10
