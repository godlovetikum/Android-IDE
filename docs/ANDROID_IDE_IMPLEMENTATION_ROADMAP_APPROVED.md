# Android IDE Implementation Roadmap

**Status:** Approved implementation roadmap.

## 1. Purpose

This roadmap converts the agreed product definition and provider research into an ordered implementation sequence. The product definition specifies user-visible behavior. The provider research specifies selected technologies and integration constraints. This document specifies **phase order, dependencies, deliverables, and acceptance gates**.

Implementation proceeds one domain at a time. A phase is not accepted because code exists; it is accepted only when its focused behavioral gate passes. Development must remain lightweight: no broad rewrites, heavy builds, background processes, commits, or pushes unless explicitly authorized.

## 2. Decisions this roadmap must preserve

- The app opens at a Home entry point and uses layered mobile navigation with a contextual sidebar. The sidebar and related surfaces follow the agreed mobile interaction and persistence behavior.
- Projects are acquired through blank creation, existing-folder import, ZIP import, and Git clone.
- A project may use either a **user-visible local location** or an explicitly selected **private development workspace** backed by the integrated Termux runtime.
- The selected location is authoritative. The private workspace is not an automatic hidden duplicate.
- User-visible locations are accepted only when the editor and runtime can operate on that same location. Unsupported locations are rejected.
- Cloud-backed projects are imported or downloaded locally before editing. Automatic two-way cloud synchronization is excluded.
- User project files, portable project metadata, project-associated runtime state, and global application data remain separate.
- The terminal is global. Starting it from a project sets an initial directory but does not permanently restrict the session to that project.
- Switching screens or applications does not explicitly close terminal sessions. Explicit close actions terminate sessions and their child processes.
- Monaco is the editor surface, not the filesystem. A file adapter connects it to the project’s authoritative location.
- Git UI actions and terminal Git commands operate on the same repository and configuration.
- Extensions remain a placeholder until their security and lifecycle model is defined.

## 3. Phase overview

| Phase | Domain | Outcome | Acceptance gate |
|---|---|---|---|
| 0 | Contracts and architecture freeze | State ownership and adapter boundaries are fixed | No unresolved ownership or lifecycle contradiction remains |
| 1 | App foundation and storage | Shell, navigation, persistence, metadata, and storage layers exist | State restores correctly and storage layers remain separated |
| 2 | Project and workspace | Registry and non-Git acquisition/management are reliable | Projects can be acquired, inspected, copied, moved, exported, and removed safely |
| 3 | Termux runtime and terminal | Private development workspace, packages, sessions, and durable processes work | Sessions and child processes behave correctly across navigation and lifecycle events |
| 4 | Code editor | Mobile-first Monaco editor and file operations are usable | Editing, tabs, search, mutation, saving, and recovery are reliable |
| 5 | Browser and previews | Normal browser, local previews, console, and viewport testing work | Browser state is usable without corrupting project or runtime state |
| 6 | Git | Repository and global Git workflows are complete | UI and terminal show the same repository state and credentials are secure |
| 7 | Language intelligence | LSP client and runtime-managed language servers work | Language services use the same files, dependencies, and working directory |
| 8 | Settings and security | Global preferences, credentials, permissions, and storage controls are complete | Global and project data remain separated and secrets are protected |
| 9 | Extensions boundary and hardening | Extension rules and cross-domain release hardening are complete | Independent and integration acceptance suites pass |

## 4. Phase 0 — Contracts and architecture freeze

### Objective

Turn the product definition into implementation contracts before feature code is expanded.

### Deliverables

Define application state, project identity, storage ownership, navigation state, surface state, terminal session identity, editor tab identity, browser tab identity, and lifecycle event vocabulary.

Define adapters for project storage, project registry, metadata, runtime workspace, terminal runtime, editor documents, Git, browser/preview, credentials, and lifecycle coordination. Higher-level domains must not depend directly on provider-specific storage or process details.

Define the two project-location classes. The private development workspace requires Termux runtime initialization and package paths. A user-visible location requires capability validation. Both expose the same logical project operations.

### Gate

Every later phase has a clear owner for files, metadata, sessions, preferences, credentials, and lifecycle events. No phase needs to decide whether a value is project-specific or global.

## 5. Phase 1 — Application foundation and storage

### Objective

Build the smallest stable shell and state foundation used by every domain.

### Deliverables

Implement Home, top-level navigation, contextual sidebar contracts, back-stack behavior, and the agreed mobile-first layout behavior. The sidebar is an integrated contextual surface rather than a dismissible overlay when opened.

Implement the four storage layers: user project files, portable project metadata, app-private project-associated runtime state, and global application data/preferences. Establish the project metadata directory convention, project registry, global preferences store, encrypted-vault boundary, and app-private runtime-state area.

Implement persisted storage permissions, location identity, capability inspection, and lifecycle coordination interfaces for foreground work, restoration records, runtime availability, and explicit termination.

### Gate

The app launches to Home, navigates between placeholder domains, restores global preferences, reopens project records, distinguishes unavailable storage from deleted projects, and never creates a hidden project copy during foundation operations.

## 6. Phase 2 — Project and workspace management

### Objective

Deliver reliable project registration, acquisition, inspection, and lifecycle management before editor or Git integration.

### Deliverables

Implement the project list, recent ordering, project details, file count, project size, location display, long-press multi-selection, context menus, and common operation feedback.

Implement blank creation with name, description, destination, review, conflict preflight, metadata initialization, and post-operation verification. Implement existing-folder import without replacing the selected folder.

Implement ZIP import with archive validation, safe extraction, reviewed name and destination, conflict detection, progress, partial-failure reporting, and post-extraction verification. Cloud-backed folders and archives are imported into a selected local destination.

Implement remove from registry, permanent deletion, export/share as ZIP, copy storage path, copy and duplicate, change location through copy-verify-delete, and batch operations. Reject project destinations nested inside another registered project.

Implement explicit copy/move workflows between user-visible local storage and the private development workspace. The destination must be inspected before copying and verified afterward.

### Gate

Blank creation, folder import, ZIP import, conflict handling, nested-project rejection, batch actions, export, duplication, relocation, permission loss, unavailable locations, and removal versus deletion all pass. No operation silently renames, overwrites, merges, relocates, or replaces a user-selected project.

## 7. Phase 3 — Termux runtime and terminal

### Objective

Integrate the complete Termux runtime foundation—not only terminal rendering libraries—and provide a useful global terminal.

### Deliverables

Integrate the selected Termux source/libraries, pinned bootstrap, architecture-specific assets, package paths, and initialization. Establish the private development workspace, configure `HOME`, `PREFIX`, `PATH`, library paths, package database, and shell startup.

Provide runtime capability reporting and an approved baseline package set including shell tools, Git, OpenSSH, certificates, archive tools, process tools, and supported Node.js or Python tooling. Package operations expose progress, result, version, and failure details.

Implement global session creation, naming, switching, explicit close, close-all, availability, and open-from-folder/project. Implement PTY input/output, resizing, zoom, interruption where supported, child-process tracking, local-server tracking, and session records.

Use the lifecycle coordinator and foreground-service mechanisms for visible long-running work. Preserve session records after unavoidable process loss and report availability accurately. A project selected for the private workspace must be directly usable by the shell, Git, language servers, and local servers.

### Gate

Verify package installation, shell/Git commands, file mutation, executable scripts, symlinks where supported, project working directories, local servers, session switching, explicit close, app switching, Home navigation, extended idle behavior, process-loss reporting, and relaunch. Closing a session must terminate its owned child processes; switching sessions must not.

## 8. Phase 4 — Code editor

### Objective

Deliver the mobile-first editor using Monaco as the editing surface and the shared project adapter as file authority.

### Deliverables

Implement the editor layout: sidebar toggle, clickable active path, search and replace, save/save-as, preview/run, context menu, horizontal tab row, active document, configurable keyboard-control row, configurable symbol row, and optional status line.

Implement the file tree with session-only expanded state, locate-current-file, file search, project content search, project find-and-replace, result lines, highlighted matches, and navigation to matching files and positions.

Implement file and folder creation, templates, nested paths, rename, delete, move, copy, duplicate, import, export/share, terminal-at-folder, and previewable-file actions. All mutations use common preflight, execution, verification, feedback, and conflict rules.

Implement Monaco models, project-relative URIs, save, dirty tracking, recovery content, and Temporary, Permanent, Pinned, and Dirty tab states. Implement touch-sized controls and press-and-hold repetition for repeatable editor commands. Persist editor preferences globally.

### Gate

Editing, saving, recovery, close-all behavior, tab persistence, mobile keyboard controls, repeated press behavior, file-tree mutation, project search/replace, external-change reporting, and an agreed large-file baseline pass.

## 9. Phase 5 — Browser, previews, and developer tools

### Objective

Add a global normal browser and project-preview workflow without making browser state project-owned.

### Deliverables

Integrate GeckoView Stable and Android Components. Implement URL/search input, tabs, navigation, downloads to Android Downloads, permissions, browser data, browser actions, and browser settings.

Implement local-file preview and development-server preview. A preview may originate from the editor, file tree, project, or terminal, but the browser remains a global domain.

Implement the agreed console scope: JavaScript commands, output, runtime errors, clear-console, history, reload, responsive viewport presets, and custom widths. Keep browser profiles and runtime data in the browser-private layer.

### Gate

Internet browsing without a project, browser tabs, downloads, local HTML/CSS/JavaScript preview, development-server access, console output, runtime-error reporting, viewport changes, browser restoration, and page/preview failure isolation pass.

## 10. Phase 6 — Git integration

### Objective

Provide a user-friendly layer over the same Git package and configuration used by the terminal.

### Deliverables

Implement repository status, changed files, diffs, staging, commits, history, branches, checkout, tags where supported, remotes, fetch, pull, push, synchronization, merge, rebase where supported, restore, reset, revert, clean/discard safeguards, conflict visibility, and repository configuration.

Implement Git clone as acquisition with repository input, derived editable project name, description, credential selection, destination review, conflict preflight, progress, verification, and registration.

Implement global Git identity, providers/hosts, credentials, credential-store settings, SSH and known-host settings where supported, ignore rules, and defaults. Use the encrypted credential vault and redact secrets.

Refresh repository views after terminal Git commands. UI operations and terminal commands must mutate the same repository and configuration.

### Gate

Clone, credentials, status, staging, commits, branches, synchronization, conflict handling, destructive-operation safeguards, terminal/UI consistency, and secret redaction pass.

## 11. Phase 7 — Language intelligence

### Objective

Add LSP after the editor, terminal, and project filesystem are stable.

### Deliverables

Integrate the Monaco language client and JSON-RPC transport. Start language servers through Termux with the same project working directory, files, dependencies, environment, and package paths as terminal operations.

Implement initialization, shutdown, restart, unavailable states, diagnostics, completion, hover, definition/declaration, references, symbols, rename, formatting, code actions, and supported code lenses. Refresh or restart servers after dependency or configuration changes.

### Gate

Language servers see the same files and dependencies as the terminal. Failed or unavailable services are reported rather than represented as stale current intelligence.

## 12. Phase 8 — Settings, security, credentials, and customization

### Objective

Complete global preferences and secure cross-domain configuration.

### Deliverables

Implement grouped settings for application/display, editor, terminal/runtime, browser, storage/permissions, credentials/security, and approved customization. Make preference changes explicit and persistent.

Complete Keystore-backed encryption, credential lifecycle, access controls, redaction, revocation, and secure deletion. Connect Git and terminal workflows to shared secure infrastructure while keeping controls in their expected domains.

Implement storage-management views distinguishing project files, portable metadata, runtime state, browser state, caches, package data, and global application data.

### Gate

Preference persistence, credential encryption, redaction, revocation, permission loss, storage accounting, cleanup scopes, and project/global separation pass.

## 13. Phase 9 — Extensions boundary and release hardening

### Objective

Define the extension boundary only after core domains are stable, then harden the complete product.

### Deliverables

Define extension packaging, permissions, sandboxing, installation, updates, execution, removal, and trust rules. Do not implement a broad extension ecosystem without a separate product and security decision.

Complete restoration, navigation, process cleanup, accessibility, touch-target review, performance baselines, storage cleanup, third-party notices, license manifests, and migration paths.

Run independent acceptance suites for every domain, then focused integration suites for project-editor, project-terminal, editor-browser, terminal-Git, Git-credentials, and terminal-language-server boundaries. Include process loss, permission loss, storage unavailability, malformed archives, failed clones, package failures, and partial operations.

### Gate

Every implemented domain passes its independent gate, cross-domain state ownership is verified, sensitive data is protected, and unsupported Android/provider behavior is reported rather than silently ignored.

## 14. Dependency rules

The critical dependency chain is:

```text
Contracts
  -> application state and storage
  -> project registry and location handling
  -> Termux runtime and terminal
  -> Monaco editor
  -> browser and previews
  -> Git integration
  -> language intelligence
  -> security and customization hardening
```

The private development workspace is established in Phase 3, but its storage ownership is defined in Phase 1 and its project selection/copy/move behavior is exposed in Phase 2. This prevents the runtime from becoming an unplanned replacement for project storage.

Small preparation tasks may proceed in parallel when they do not change these contracts. Full domain implementation must not create competing storage or lifecycle models.

## 15. Explicitly out of scope unless approved later

This roadmap excludes automatic cloud synchronization, Docker support, root-only operations, an unkillable-process guarantee, unrestricted Android system-directory access, a general extension marketplace, and a native desktop-class compiler toolchain. Each would require a product-definition change and provider/security review.

## References

[1]: ./ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md "Approved Android IDE Product Definition"
[2]: ./ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md "Approved Android IDE Provider and Dependency Research"
[3]: ./STATUS_TRACKER.md "Android IDE Status Tracker"
[4]: ./PHASE_0_IMPLEMENTATION_GUIDANCE.md "Android IDE Phase 0 Implementation Guidance"
[5]: https://github.com/termux/termux-app/wiki/Termux-Libraries "Termux Libraries"
[6]: https://github.com/termux/termux-packages/wiki/Termux-file-system-layout "Termux Filesystem Layout"
[7]: https://developer.android.com/develop/background-work/services/foreground-services "Android Foreground Services"
[8]: https://microsoft.github.io/monaco-editor/ "Monaco Editor Documentation"
[9]: https://microsoft.github.io/language-server-protocol/ "Language Server Protocol"
[10]: https://developer.android.com/guide/topics/providers/document-provider "Android Storage Access Framework"
[11]: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/ "GeckoView Documentation"
[12]: https://github.com/liriliri/eruda "Eruda Developer Console"
[13]: https://git-scm.com/docs "Git Documentation"
[14]: https://developer.android.com/privacy-and-security/keystore "Android Keystore System"

Manus AI  
September 2026

*This roadmap defines sequence and gates. It does not authorize source changes, commits, pushes, or heavy builds.*
- The app opens at a Home entry point and uses layered mobile navigation with a contextual sidebar. The sidebar and related surfaces follow the agreed mobile interaction and persistence behavior.
Implement Home, top-level navigation, contextual sidebar contracts, back-stack behavior, and the agreed mobile-first layout behavior. The sidebar is an integrated contextual surface rather than a dismissible overlay when opened.
