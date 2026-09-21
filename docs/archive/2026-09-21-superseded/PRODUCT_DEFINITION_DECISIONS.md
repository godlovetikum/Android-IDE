# Android IDE Product Definition Decisions

This document records decisions made during product-definition discovery. It is a planning record, not an implementation claim. No source implementation is authorized by this document alone.

## Product definition currently agreed

Android IDE is a mobile development workspace that unifies project creation and management, code editing, terminal commands and dependency management, synchronization with remote repositories, in-app web browsing and previews, browser developer tools, background tasks, and related developer workflows in one Android application.

The current repository state describes an implementation stage, not the complete product definition.

## Decision 1 — Project/workspace unit

A project is a synchronized development environment. It includes user project files, project-specific portable IDE state, app-managed runtime associations, and remote repository associations. The project model must support later integration of editor, terminal, Git, browser, previews, and background tasks without allowing each domain to invent separate project state.

## Decision 2 — Four storage layers

The product adopts four storage layers:

1. **User project files:** source files, project documentation, generated project files, and Git-owned repository data such as `.git/`. These remain user/project data and must not be silently recreated or replaced.
2. **Portable project metadata:** a project-local metadata directory, currently represented by `.androidide` pending any separately approved identity migration. It stores portable project-specific IDE state such as project identity, workspace descriptors, editor state, terminal/browser/task descriptors, and recovery information. It must remain versioned and migratable.
3. **App-private project-associated runtime state:** live or reconstructable process/session state, PTYs, logs, browser cache and storage, temporary files, runtime handles, and other device-specific data associated with a project. This does not belong in the portable project metadata folder.
4. **Global application data and preferences:** global settings, project registry, recent-project ordering, encrypted credentials, global diagnostics, and application-wide services. This data does not belong in a project folder.

Credentials, browser authentication storage, live processes, large logs, caches, and device-specific process handles must not be stored in the portable project metadata directory.

## Decision 3 — Unified browser and developer-tools feature

The browser is one unified product feature, not two separate browser products. It should behave as a normal internet browser inside Android IDE while also supporting project preview and web-development inspection workflows.

The unified browser should support:

- ordinary internet browsing from within the app;
- switching from the editor to browser tabs without leaving Android IDE;
- searching the internet while coding;
- downloading files through the browser;
- opening local or served HTML/CSS/JavaScript previews;
- displaying project development-server output;
- access to browser console and developer tools for web debugging;
- multiple browser tabs or equivalent tab/session navigation;
- association of relevant tabs with a project or preview task where useful;
- durable restoration of tab descriptors when supported by Android and the WebView implementation.

The distinction is not between “normal browser” and “preview browser.” They are the same browser capability with different tab content and optional project association. A project preview is a browser tab whose content comes from a project or development server; an internet tab is a browser tab whose content comes from the web. They should share the browser surface, tab model, navigation, downloads, and developer-tools access.

Browser cookies, cache, authentication sessions, downloads, and other browser runtime data belong to app-private browser/runtime storage rather than portable project metadata. Project metadata may store only durable descriptors such as project association, preview-task association, URL, route, and restoration hints.

## Current product-definition principle

Features must be defined and scoped before their data models, storage details, implementation phases, or technical limits are finalized. Technical architecture should be derived from the agreed feature behavior rather than from accidental limitations of the current repository.

## Next discovery step

Continue product discovery one question at a time. The next question should define the complete feature domains of Android IDE and the capabilities that belong inside each domain, before revisiting detailed storage or implementation models.

## Decision 4 — Six top-level feature domains

Android IDE is organized into six top-level domains:

1. **Project and workspace management** — the primary foundation for projects, workspace state, storage, restoration, and cross-domain coordination.
2. **Code editing** — files, editor tabs, editing behavior, saving, recovery, search, replacement, navigation, and editor workflows.
3. **Terminal, runtime, dependencies, and background processes** — terminal sessions, command execution, dependency management, runtime environments, long-running processes, servers, and their independent lifecycle.
4. **Browser, previews, and developer tools** — unified internet browsing, project previews, development-server output, browser tabs, downloads, browser console, and web developer tools.
5. **Git integration and remote repository synchronization** — local Git workflows, repository synchronization, remote repositories, branches, history, diffs, credentials needed for Git, and related collaboration workflows.
6. **Extensions, credentials, security, settings, and customization** — extensibility, application settings, user customization, security controls, and credentials that are not exclusively part of Git. Git-specific credential workflows remain integrated with the Git domain while shared credential/security infrastructure may be provided here.

The six domains are product groupings, not necessarily six isolated technical modules. They may share foundation services, but each domain must have a clear behavioral contract and completion criteria.

Project and workspace management and code editing are the first critical usability foundation. The product roadmap should define and complete domains deliberately rather than adding isolated features across several domains at once.

## Product-definition workflow

Feature definition proceeds domain by domain. For each domain, first define the user-visible capabilities and boundaries; then define behavior, relationships to other domains, limitations, state ownership, storage implications, and acceptance criteria. Technical implementation and coding follow only after the relevant domain contract is agreed.

## Decision 5 — Project/workspace scope includes global and project-centered work

Project and workspace management is not limited to opening one project folder. Android IDE must support a unified workspace with multiple entry modes:

- **Project-centered mode:** the user works inside a selected project and its associated editor, terminal, browser/preview, Git, and task state.
- **Global workspace mode:** the user opens Android IDE without selecting a project and can use capabilities that do not require a project, such as a global terminal, ordinary internet browsing, global Git configuration and repository inspection, credential management, and device-accessible file operations where Android permissions allow them.
- **Cross-project mode:** the user can move or copy files and folders between projects, inspect multiple registered projects, and coordinate operations across project boundaries without incorrectly assigning one project's state to another.

The selected project remains important, but it is not a prerequisite for every capability. The application must distinguish global state from project state and must not force unrelated global activities into a project merely because the app was opened from a project screen.

### Expanded project creation and import

Project/workspace management must support multiple ways to obtain a project:

1. Create a new project in a user-reviewed, user-selected storage location.
2. Open an existing project from Android-accessible storage.
3. Import a project from a ZIP archive, with archive-root validation, path-traversal protection, extraction progress, collision policy, and post-import inspection.
4. Clone a remote repository through the Git domain, then register and open the resulting project through the workspace domain.
5. Obtain a project through a global terminal command, such as `git clone`, and then import or register the resulting project through an explicit workspace flow.

The Git clone operation belongs to Git integration, and terminal-based cloning belongs to terminal/runtime management. Project/workspace management owns the resulting project registration, inspection, metadata initialization, workspace association, and user-visible transition into the project. No domain should silently register incomplete or unverified output from another domain.

### Global versus project-scoped sessions

Terminal, browser, Git, and background-task capabilities may be either global or project-associated:

- A terminal may be global and operate wherever Android permissions allow, or project-scoped with a project root and project-relative working directory.
- A browser tab may be a general internet tab or associated with a project preview/task; both belong to the unified browser feature.
- Git configuration and credential inspection may be global, while repository operations and synchronization are associated with a specific repository/project.
- Background tasks may be global or project-associated, with explicit ownership and lifecycle descriptors.

The workspace model must represent these scopes clearly. Switching into a project must not hide or destroy global sessions, and leaving a project must not terminate project-associated work unless the user explicitly chooses to stop or close it.

### Revised seven feature groups for Project and Workspace Management

1. **Project creation and acquisition** — new projects, existing folders, ZIP imports, and handoff from Git clone or terminal-created project output.
2. **Project import, opening, inspection, and registration** — existence checks, provider permissions, metadata initialization/repair, project registry, and safe registration of output from other domains.
3. **Project registry and multi-project organization** — recent projects, search, sorting, display names, unavailable entries, and recovery-oriented project records.
4. **Project storage and file-tree management** — files, folders, safe mutations, cross-project copy/move, project duplication, export, import, name collisions, paths, and deletion boundaries.
5. **Workspace context and scope management** — global workspace, selected-project workspace, cross-project operations, global sessions, project-associated sessions, and context transitions.
6. **Workspace state, restoration, and lifecycle** — project state, global state, surface state, child-session descriptors, Activity recreation, process death, Android memory reclamation, Hide/Close/Stop/Leave/Exit semantics, and unavailable-state recovery.
7. **Storage permissions, provider reliability, and operation feedback** — permissions, provider differences, verification, interruption, partial results, progress, inline feedback, and recoverability.

This expansion does not move terminal, browser, Git, or credential implementation into Project and Workspace Management. It defines the workspace contracts and handoffs that allow those domains to operate globally or in association with a project.
