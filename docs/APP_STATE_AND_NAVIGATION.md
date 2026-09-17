# Android IDE App State and Navigation Contract

## 1. Purpose

This document defines the application-wide state model that all feature domains must use. It exists to prevent editor, project management, terminal, Git, browser, and future extension work from introducing separate navigation assumptions.

This is a design contract and handoff document. It does not claim that every surface described here is already implemented.

## 2. State scopes

### Application scope

Application scope contains settings and navigation information that belongs to the IDE as a whole:

- Theme and system-bar appearance.
- Editor defaults.
- File-tree visibility settings.
- Registered projects and recent-project ordering.
- Current application surface.
- Last restorable project reference.
- Durable descriptors for terminal sessions and browser tabs once those domains exist.

Application scope must not contain the contents of project files or project-specific editor tabs.

### Project scope

Project scope belongs to one selected project:

- Project URI and display information.
- Project-local `.dev-android-ide` metadata.
- File-tree expansion and visible navigation context.
- Open editor tabs for that project.
- Active editor tab.
- Cursor and scroll positions.
- Project-local search context when deliberately persisted.
- Terminal sessions associated with the project.
- Browser tabs associated with the project.
- Project task and preview descriptors.

Switching projects must save the outgoing project scope before replacing it with the incoming project scope. A project switch must not leak tabs, cursor positions, terminal sessions, browser tabs, or pending drafts across projects.

### Surface scope

A surface is a top-level work area inside the selected project. The initial and planned surfaces are:

- **Projects:** project registry and project creation/opening.
- **Project Details:** detailed project information and project-level actions.
- **Editor:** file tree, editor tabs, Monaco editor, editor search, and optional editor-local preview while that preview remains part of the editor domain.
- **Terminal:** terminal sessions and command output.
- **Browser:** future in-app browser tabs and project preview.

A surface can be visible, hidden, or closed according to its domain rules. Hiding changes visibility and focus. It must not silently destroy durable state or terminate owned work.

## 3. Navigation model

The root navigation state must contain the selected project and one current top-level surface. The project registry is the entry surface when no project is open. Project Details is reachable from the project list, recent-project surfaces, editor-side project actions, and other project-owned entry points approved by the project domain.

Within Editor, the file tree, editor tab bar, breadcrumb/sibling navigator, search panels, and Monaco editor are editor-owned navigation components. They must not create a second application-level project or surface state.

Within Terminal, the session list selects a terminal session but does not change the selected project. Within Browser, the tab strip selects a browser tab but does not change the selected project. The selected terminal or browser tab is child state of its surface.

Back behavior must be state-dependent:

1. Dismiss the highest-priority transient control, such as a dialog, search widget, context menu, or keyboard-owned overlay.
2. Close or hide the current child panel according to that domain’s contract.
3. Return from Project Details to the previous project-owned surface.
4. Leave the current project only through the project-exit confirmation flow when unsaved work or active tasks require it.
5. Return to Projects only after project state has been saved or the user has explicitly discarded it.

A feature must not intercept Back merely to navigate to its own preferred screen.

## 4. Tabs and sessions

### Editor tabs

Editor tabs represent open documents within the selected project. Each tab has a stable identity, document URI, display name, language, dirty state, temporary/pinned state, cursor position, and scroll position. A temporary tab may be replaced by another temporary tab; a pinned tab must remain until explicitly closed.

Closing an editor tab must not close a terminal session or browser tab. Project switching must save the editor workspace and restore only tabs whose document URIs remain accessible.

### Terminal sessions

Terminal sessions are independent child sessions owned by the Terminal surface and associated with a project. Hiding the Terminal surface must not terminate a session. Closing a session must explicitly terminate its process or mark it unavailable and release its resources. A long-running command must not depend on the Terminal composable remaining in composition.

### Browser tabs

Browser tabs are future child sessions owned by the Browser surface and associated with a project or preview task. Hiding the Browser surface must not close a tab. Closing a browser tab must explicitly release its WebView/session state. If Android reclaims a WebView or process, the durable browser descriptor must be used to recreate the tab as far as supported.

## 5. Hide, close, and exit

**Hide** means the surface is no longer visible. Its durable state and owned work remain available.

**Close** means the user intentionally terminates a child tab/session and releases its resources. Closing must not be hidden behind a misleading navigation action.

**Leave project** means the user is leaving the current project context. The app must save project-local workspace state, handle dirty files, preserve or explicitly stop active tasks, and then navigate to another project or Projects.

**Exit** means an explicit in-app command to stop IDE-owned runtime sessions and terminate the current IDE workflow. Android may still kill the process independently, so the state contract must guarantee restoration rather than promise process immortality.

## 6. Durable restoration

The durable application envelope must record the last selected project and surface. Project-local metadata must record editor workspace state and, when those domains exist, terminal and browser descriptors. Restoration must be staged:

1. Start the app and load global settings.
2. Validate the last project URI and available permissions.
3. Open the project and initialize or repair required metadata.
4. Load project workspace descriptors.
5. Validate each editor document, terminal session, and browser tab descriptor.
6. Restore accessible children and mark unavailable children with actionable status.
7. Restore the selected surface and child selection.

A stale URI must not prevent the project itself from opening. A failed runtime session must not erase editor tabs or project metadata. A process recreation must not be treated as a clean exit.

## 7. Project identity and metadata naming

The requested identity direction is:

| Purpose | Proposed value |
|---|---|
| Android application ID | `dev.android.ide` |
| Kotlin namespace/package direction | `dev.android.ide` after a deliberate package migration decision |
| Project-local metadata directory | `.dev-android-ide` |

The leading dot keeps metadata hidden by default. The hyphenated directory name is the filesystem form of the same product identity. The current code uses the Kotlin package family `dev.androidide` and the metadata directory `.androidide`; these are existing values, not the proposed final values.

The migration must be handled as its own identity task. It must define whether existing `.androidide` folders are migrated, read as legacy aliases, or left in place with a one-time conversion. It must also update Gradle namespace/application ID, manifest references, package declarations, generated metadata, visibility settings, tests, and documentation consistently. No feature-domain implementation should silently change the identity.

## 8. File-tree visibility rule

The visibility configuration concerns only project control folders:

- The project-local metadata folder (`.dev-android-ide` after migration).
- The Git folder (`.git`).

README files are ordinary project documentation and are not controlled by this visibility configuration. Root README creation and preservation belong to project initialization; README visibility does not belong in the file-tree control-folder setting.

## 9. Implementation gate

Before further source implementation, the project owner must approve:

- The top-level surfaces and their ownership.
- The application/project/surface state boundaries.
- The Back, Hide, Close, Leave Project, and Exit semantics.
- The proposed application ID and metadata-directory migration strategy.
- The file-tree visibility rule.
- The domain order in `PROJECT_PLAN.md`.

Until this gate is approved, the next work should be documentation review and targeted architecture clarification rather than additional feature coding.
