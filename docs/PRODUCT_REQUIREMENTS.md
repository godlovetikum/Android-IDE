# Product Requirements

## Phone-first development workspace

Android IDE is intended to be one developer workspace on Android, not a collection of unrelated screens that must be replaced by other applications. Project files, editing, previews, terminal sessions, Git operations, browser sessions, imports, exports, and sharing should be available from one application while respecting Android storage, lifecycle, memory, and background-execution rules.

## Editor initialization and access

The Monaco editor WebView should initialize independently of project and file selection. This allows the editor renderer and bridge to warm up before the first project is opened and avoids imposing WebView startup latency on the first editing action.

Initialization must not grant user access to an editor workspace without a project. When no project is selected:

- The Editor navigation control is disabled.
- The user cannot reach the Monaco surface through normal navigation.
- No project file, tab, preview, save action, or project-scoped editor operation is available.
- The initialized editor host remains outside the visible content layout.

When a project is opened, the editor surface becomes available. It may initially show an empty state until the user selects or creates a file.

## Application text scaling

The application font-scale preference applies to all Compose-rendered user-interface text, including:

- Project-list rows and search feedback.
- Project-details screens and metadata fields.
- Dialog titles, descriptions, warnings, confirmation codes, and validation messages.
- Text fields, labels, placeholders, menus, buttons, and tooltips.
- File-tree names, loading indicators, operation feedback, and status messages.
- Settings controls and accessibility descriptions where visible text is rendered.

Code font size inside Monaco is a separate preference because source-code typography has independent readability and density requirements. Both controls must remain clear in Settings.

## Independent sessions and background work

Changing the visible tab must not implicitly terminate work owned by another session. A future terminal, preview server, Git task, or browser session must have an explicit lifecycle independent of its viewport:

- A session can be visible, hidden, minimized, or restored.
- Hiding a session does not stop it.
- Stopping a session requires an explicit user action or an unavoidable Android lifecycle event that is explained to the user.
- Long-running work must expose state, output or logs, errors, cancellation, restart, and resource usage where applicable.
- Android foreground-service, battery, notification, process-death, and permission rules must be handled explicitly.

This session model applies to live previews, terminal tabs, browser tabs, Git operations, and project-management jobs. The editor viewport is only one presentation surface; it must not be treated as the owner of unrelated background work.

## Public documentation standard

Repository documentation describes user-visible behavior, supported workflows, data ownership, limitations, and product requirements. Internal task notes, temporary implementation commentary, and claims about unpushed local work do not belong in public product documentation. Engineering records may retain technical detail, but they must use stable terminology and identify whether a behavior is implemented, experimental, or planned.
