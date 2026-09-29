# Android IDE: Implementation Requests Since Project Handoff

**Project:** Android IDE  
**Working branch:** `dev`  
**Repository:** `godlovetikum/Android-IDE`  
**Document date:** 2026-09-29  
**Purpose:** Record the implementation requests, corrections, product decisions, UI direction, and execution constraints provided since the project was handed over.

## 1. Working boundaries and execution rules

The project must be worked on exclusively from the `dev` branch. Work must remain local until explicit instructions to commit or push are given. No commit or push should be made implicitly.

The user explicitly limited builds and compilation because of credit and environment constraints. Unless specifically requested later, implementation work should use source inspection, static audits, targeted reasoning, and diff checks instead of builds, compilation, lint, or tests. Do not create build artifacts or run unnecessary validation workflows.

When a correction is requested, it should be implemented as a permanent, complete fix rather than a superficial change that leaves the same issue to be flagged again. The current source and its documentation should be reviewed together, with the `dev` branch treated as authoritative for the active work.

Each substantial correction domain should have a written session plan containing scope and acceptance criteria, followed by a source-only re-audit against that plan.

## 2. Initial lint correction

The initial reported lint issue was an unresolved `crashReportCount` reference in the application shell. The correction was to pass the crash-report count explicitly into `SurfaceHost` instead of relying on an out-of-scope reference.

The source was aligned with the reported `origin/dev` state before patching, while preserving the pre-existing `.gitignore` working-tree edit.

## 3. Global sidebar and editor file-tree access

The global sidebar must remain accessible from every screen, including home, editor, terminal, settings, Git, browser, grouped screens, and placeholder domains, while its lower content must be contextual to the active domain.

The sidebar shell must contain a labeled top row with these controls in this order:

1. Navigation
2. Editor
3. Terminal
4. Git
5. Browser

The top-row labels must remain visible. The icons and structure specified by the user must not be casually replaced by the reference screenshots. The navigation icon should be plane-like, while the top-row structure itself must remain intact.

The navigation top-row control is a sidebar-content control only. It must not switch the underlying application screen or route to Home. Its purpose is to expose navigation controls for areas that are not represented by the other top-row controls. It should have accessibility language such as “Navigate to more sections.” It is the default lower-section tab from Home.

The navigation lower section must show navigation controls only, such as Home, Projects, Settings, Extensions, and other approved destinations. It must not show recent projects or unrelated project-list content.

The lower sections must have intentional visual boundaries, using bordered or container-like grouping rather than loose icons and text.

Top-level sidebar controls should switch the underlying screen and change the lower sidebar context, but should not close the sidebar. Precise actions inside the lower content may close it when continuing in the new surface is the natural result.

The sidebar must provide accessibility support, including clear labels, selected state semantics, and touch-friendly controls. Swipe or gesture access must remain available on all eligible screens.

## 4. Contextual sidebar closing behavior

Sidebar navigation callbacks must carry an explicit close-sidebar policy rather than applying one global behavior to every action.

Top-row screen switching keeps the sidebar open. Contextual actions should close it when the user is moving into a focused workflow, for example:

- opening a file from the editor file tree;
- starting a new terminal session;
- opening a project or a focused project workflow;
- importing or exporting when continuing in the target workflow makes the sidebar unnecessary.

Actions that are exploratory, contextual, or expected to be repeated should normally keep the sidebar open, for example:

- searching file names;
- opening a context menu;
- expanding or collapsing tree nodes;
- using locate or project-content search;
- inspecting an item without leaving the current surface.

The close decision must be supplied by the sidebar action itself and must not alter navigation-stack behavior.

## 5. Navigation-tab visual styling

The navigation tab should follow the styling direction of the provided SPDK Editor screenshot without copying its component structure.

The intended direction is:

- premium, intentional tile or box controls;
- visible container boundaries;
- clear active-state treatment;
- spacing and alignment appropriate for a small mobile screen;
- blue used selectively for active or prominent states;
- no unfinished collection of circular icon buttons;
- labels retained in the Android IDE top row;
- icons and overall component structure preserved as previously specified.

The navigation tab must remain strictly navigation-oriented. It should not contain recent projects or unrelated acquisition content.

## 6. Editor sidebar and file tree

The editor sidebar must expose the file tree whenever the editor tab is active. A user must be able to load a project and then access its files immediately from the editor sidebar.

The editor sidebar action row should use a compact, larger, accessible tile row rather than tiny unlabeled icons. It should include:

- Files;
- filename/project search;
- locate active file or project item;
- create file;
- create folder;
- project contents search labeled or described as “Contents”;
- context menu.

Labels may be visible or supplied through tooltips and accessibility descriptions, but the controls must be understandable and usable on a phone.

The file tree visual direction should include:

- vertical hierarchy guide lines for folder and file descendants;
- consistent indentation and alignment;
- distinct file-type icons rather than generic badges or one generic file icon;
- folder expansion controls without redundant extra folder icons;
- active file highlighting;
- active ancestor highlighting from the project root through the open file, including ancestors that remain visible while collapsed;
- clear styling for the project root and active path;
- touch-friendly row controls and context menus.

The file tree must preserve context-menu semantics for file operations instead of placing every mutation action inline.

The editor top bar must not expose the Preview/Run button at this stage. Browser is a separate global surface. Browser preview/run behavior is reserved for the later browser implementation, where the default launch file for web projects can be opened there.

## 7. Editor top bar, paths, Save As, and file navigation

The editor top bar should support a readable breadcrumb/path experience and a context menu for file navigation actions such as:

- History;
- Go Back;
- Go Forward;
- folder navigation;
- opening the current folder or project root for root-level files.

Breadcrumb navigation should behave as a real current-folder cursor with reliable upward and downward traversal. Root-level files must resolve to the project root.

Save As must:

- remain open when validation fails;
- show actionable validation errors in the active dialog;
- show submission/progress state while the file operation runs;
- remain open when path resolution, target creation, or writing fails;
- clear submission state on success;
- preserve the existing pending-navigation confirmation flow;
- distinguish a deleted or provider-invalidated source URI from a genuine failed cleanup operation.

The editor must remove obsolete contextual-sidebar toggle parameters and controls that no longer match the global sidebar model.

## 8. Keyboard shortcut toolbar customization

The editor keyboard shortcut toolbar must support persistent customization of button order.

The requested behavior includes:

- persisted keyboard toolbar action ordering;
- sanitization of invalid or missing action identifiers;
- settings UI for reordering actions;
- reset-to-default ordering;
- accessible labels for reorder controls;
- live editor use of the persisted order;
- correct pagination after the action order changes;
- safe reset when the selected page becomes invalid.

Every shortcut-toolbar button must work, especially the keyboard-toggle button. The keyboard-toggle action must control native soft-keyboard visibility rather than remaining dead logic.

The legacy editor host and current editor surface must both receive the persisted toolbar ordering.

## 9. Project management review and correction principles

Project management must be evaluated against the approved product definition and real developer workflows, not by treating every product requirement as user-facing diagnostic copy.

The project list is an entry point, not a dashboard. It should remain concise and space-efficient for mobile users. It should show the project and only the most important available status signal. Project Details is the place for deeper information.

Controls that are placeholders may remain interactive where appropriate. If a service or underlying feature is not implemented, the UI should present an appropriate coming-soon or unavailable feedback state rather than falsely implying completion. Placeholders should not be removed merely because their backend is not yet implemented.

Copy must not expose implementation details such as:

- “the project will be inspected” explanations;
- provider verification mechanics;
- internal containment checks;
- encoded storage identifiers;
- excessive diagnostic lists;
- redundant labels explaining obvious UI behavior.

User-facing feedback should be concise, contextual, and plain-language.

## 10. Registry removal and permanent deletion

Remove from Registry and Permanently Delete are separate operations.

Remove from Registry must:

- remove the project record from Android IDE’s registry;
- preserve user project files, Git data, ordinary README files, and selected storage location;
- remove only IDE-owned registry references or metadata explicitly defined as registry-owned;
- remove the IDE portable metadata directory when that directory is explicitly owned by Android IDE and its removal is part of the agreed registry-removal semantics;
- not delete the user’s project folder or project contents.

Permanent deletion must:

- require explicit confirmation identifying the project and deletion scope;
- use the verified project identity and location, not a display name alone;
- delete the project folder and contents through the controlled storage route;
- never delete a parent directory, unrelated item, or another registered project;
- distinguish successful deletion from failed inspection afterward;
- treat a provider-invalidated URI or absent deleted item as evidence that deletion succeeded, not as a permission-loss failure;
- report partial deletion, rejected deletion, permission loss, or uncertainty accurately;
- remove the registry record only after the deletion result is accepted as successful.

The same provider-aware deletion semantics must apply to file deletion, project deletion, acquisition cleanup, relocation cleanup, rollback, export cleanup, and Save As failure recovery.

## 11. Acquisition workflows and modal copy

Existing-folder import should be called **Load an existing project**.

The workflow should:

- ask the user to select the location where the project already exists;
- inspect permissions and suitability;
- show concise errors when the location is inaccessible or unsuitable;
- derive the project display name from the selected folder rather than asking the user to rename the project during import;
- allow the user to add a description;
- offer to continue and load the project;
- avoid asking for a new destination because the selected folder is already the project location.

ZIP import and Git acquisition remain separate acquisition flows from Create New.

Acquisition modals should contain only the material elements needed for the current workflow:

- a clear title;
- the required input or location control;
- concise contextual feedback;
- action buttons.

The location presentation must:

- hide the location display until a location has actually been selected;
- avoid “Selected,” “Chosen,” or “Target storage location” labels;
- change “Choose a location” to “Choose another location” after selection;
- show a readable path rather than an encoded `content://` identifier;
- avoid prefixes such as “Selected storage /”;
- avoid double-labeling source and destination paths;
- avoid exposing provider-specific encoded storage paths.

Acquisition feedback should use concise states such as:

- “Inspecting project location…”;
- “Loading project…”;
- “Ready”;
- a direct contextual error;
- “Working…” or an equivalent progress state.

The implementation detail that a location will be inspected or verified should not be written as explanatory modal copy.

Acquisition completion should remain within the active review modal rather than stacking a second “Project ready” dialog.

## 12. Create New project templates

Create New must be a distinct entry point from existing-folder loading, ZIP import, and Git clone. It must not list all acquisition flows as if they were equal templates.

The approved Create New template choices are:

1. Start from scratch
2. Node.js application
3. npm package
4. pnpm package
5. Node.js HTTP server
6. Static web project

Each template must generate files that match its README and scripts. Start from scratch should create only the root README, `.gitignore`, and IDE metadata, without adding a package-manager setup.

The generated root README must sell and explain Android IDE in the style illustrated by the connected Quiet Mails README. It should explain:

- what Android IDE is;
- that it is a mobile development workspace;
- who it helps and what problem it solves;
- how the user continues the project with Android IDE;
- how the file tree, editor, terminal, Projects screen, Git tools, and browser fit into the workflow;
- that the project remains in the selected user storage location;
- what the selected starter template provides;
- the next commands and development steps.

The generated README must not be a generic package-manager dump or merely an attribution block. Android IDE attribution should be product positioning and onboarding.

The current approved template copy direction is:

> Android IDE is a mobile development environment for creating, editing, organizing, and running software projects directly from an Android device. It gives you a project workspace with a file tree, code editor, terminal, Git tools, and project management features so you can continue working without needing a desktop computer.

And:

> Open this project in Android IDE to browse and edit its files, use the terminal to install dependencies and run development commands, manage the project through the Projects screen, and use Git when repository tooling is available.

The starter files must agree with the documented commands. For example:

- Node.js applications create `src/index.js`;
- HTTP server templates create `src/server.js`;
- static web templates create `index.html`, `src/main.js`, and `styles/main.css`;
- npm and pnpm package templates create their package entry points;
- package descriptions are safely escaped when written to `package.json`.

## 13. Project list status indicators and loading states

The project list must immediately show registered project records without blocking on complete recursive inspection.

The project name should be visible from registry data. Values loaded progressively from storage inspection should show loading state while inspection is pending. They must not show “unavailable” and then later change to real data during normal loading.

The project list should show concise card information such as:

- project name;
- description when available;
- file count when available;
- project size when available;
- last-opened or similar useful recency information;
- Git indicator when Git is detected;
- one most important attention indicator when a problem or required action is available.

During loading, metric presentation should communicate loading independently, such as:

- `Files loading…`;
- `Size loading…`.

After inspection completes, unavailable values are appropriate only when the details service has actually determined that the value cannot be obtained.

Status signals must not become a dashboard or a list of every condition. At most one priority attention badge should be shown, with priority generally given to:

1. permission needed;
2. unsupported provider;
3. unavailable location;
4. inspection required;
5. another concise attention state.

Git should remain a separate badge because it identifies project capability or repository context rather than an error.

The project card must be intentionally styled rather than flat or unfinished. It should use the persisted application theme consistently, including:

- borders;
- appropriate elevation or shadows;
- deliberate text hierarchy;
- primary blue for active or prominent states;
- gold or yellow for warnings where appropriate;
- red for errors and destructive actions;
- theme-aware surfaces and content colors.

Blue must not be used indiscriminately for every control, background, border, or text element.

Project Details should carry the deeper information that does not belong in the project list, including grouped identity, location, contents, capabilities, and Git information.

## 14. Project Details

Project Details must be useful rather than a dump of unrelated or incorrect information. It should use concise grouped sections and show the fields that help a developer understand and manage the project, including where available:

- project identity;
- description;
- readable storage path;
- storage provider;
- creation and last-modified information;
- file and folder counts;
- total size;
- language totals;
- storage capabilities and explanations;
- Git information and sanitized remote URLs;
- relevant availability or attention state.

Encoded provider paths must not be shown as user-facing storage paths.

## 15. Context menus throughout the application

All context menus, dropdown menus, and action lists should use grouped, intentional semantics.

This applies to:

- project cards;
- project details;
- sidebar file tree;
- terminal sessions;
- dropdown menus;
- acquisition and project-operation menus;
- any other contextual action surface.

Related actions should be grouped and visually separated. Icons should be attached where they improve recognition.

Typical grouping includes:

- details, refresh, and information;
- copy path and repository information;
- open in Editor, Terminal, Git, or Browser;
- duplication, relocation, export, or sharing;
- registry removal and permanent deletion.

Destructive actions must use red/error styling. Remove from Registry should use a blue or other accent treatment distinct from permanent deletion. Ordinary actions should follow the current light/dark application theme rather than using arbitrary colors.

Browser preview actions may remain in project lists and context menus. The only preview/run control removed by request is the editor top-bar Preview button.

## 16. Terminal sessions and terminal sidebar

The terminal is a global feature, not project-owned. Terminal sessions must remain available when switching screens, returning Home, changing domains, or leaving the application surface, subject to Android/backend limitations.

Every session should not expose Rename and Exit as inline controls. These actions should be available through a per-session context menu or equivalent contextual action surface.

Terminal sidebar session rows should therefore provide:

- session selection;
- a context menu for rename;
- a context menu for close/exit;
- no cluttered inline lifecycle controls;
- accessible labels and touch targets.

The terminal sidebar should expose terminal-specific contextual content, while Home, Settings, Git, Browser, and other non-contextual screens should use the global Navigation lower section rather than an editor or terminal sidebar context.

## 17. Terminal mobile shortcut toolbar

The terminal must provide a mobile-oriented shortcut-input toolbar because a phone keyboard does not expose the controls commonly needed by command-line tools.

The toolbar must support latched modifiers and practical terminal navigation keys, including:

- Ctrl;
- Alt;
- Escape;
- cursor and arrow navigation;
- common control-button inputs;
- other useful terminal shortcuts supported by the backend.

Ctrl, Alt, and Escape modifiers must be able to latch so the user can press a modifier and then a navigation or command key. Input should be encoded as the correct byte or ANSI sequence and sent through the terminal runtime adapter.

The toolbar must have accessible labels, selected/latched state styling, and touch-friendly controls. It should be designed specifically for mobile terminal use rather than copying the editor toolbar blindly.

## 18. Crash reporting and terminal-start failures

Starting a terminal session previously appeared to crash the application without making the crash report visible on the next launch. Crash reporting must therefore be durable and recoverable.

The crash reporter must:

- persist crash information before process termination when possible;
- use an emergency marker when a full report cannot be written;
- include emergency markers in next-launch detection;
- count emergency markers in the report count;
- expose the latest persisted report for review;
- clear stale emergency markers after a full report is successfully persisted;
- survive storage/write failures as far as possible.

The next launch should surface the persisted crash information on Home in an accessible, reviewable dialog or error card. The crash report should be available even when the failure happened during terminal session startup.

The terminal runtime and application-shell exception boundaries should preserve the report-writing path rather than allowing startup failures to disappear silently.

## 19. Styling and accessibility principles across all domains

The application should feel intentionally designed and premium rather than assembled from random icons and unfinished controls.

The visual system must use the persisted application theme consistently. Primary blue is reserved for active states, prominent information, selected elements, and important positive or capability indicators. Gold/yellow may be used for warnings and secondary emphasis. Red/error colors are reserved for failures and destructive actions.

Controls must have:

- clear labels or accessibility descriptions;
- visible selected and active states;
- touch-friendly sizing;
- meaningful grouping;
- predictable contextual behavior;
- usable semantics for users with disabilities.

Mobile space is limited. Copy should be concise, avoid data dumps, preserve whitespace, and communicate only what the user needs to act.

## 20. Documentation and audit expectations

Implementation plans and audits created during the work include:

- `docs/SESSION_PLAN_PROJECT_MANAGEMENT_FIXES_2026-09-28.md`;
- `docs/SESSION_PLAN_PROJECT_LIST_DETAILS_FIXES_2026-09-28.md`;
- `docs/SESSION_PLAN_SIDEBAR_TERMINAL_CRASH_FIXES_2026-09-28.md`;
- `docs/SESSION_PLAN_CREATE_NEW_TEMPLATES_2026-09-29.md`.

The active documentation should continue to record the scope, acceptance criteria, implementation result, and source-only audit result for each major domain.

## 21. Explicit non-goals and deferred work

The following boundaries were explicitly established:

- Do not build or compile unless separately requested.
- Do not commit or push unless separately requested.
- Do not add a top-editor Preview/Run control before the Browser surface supports that workflow.
- Do not make Navigation switch the underlying application screen.
- Do not remove placeholders merely because their backend is not implemented; make them honest and interactive where appropriate.
- Do not add hidden project copies or silently redirect projects to another provider/location.
- Do not treat implementation verification steps as user-facing product copy.
- Do not turn the project list into a status dashboard.
- Do not expose all available project statuses at once when one prominent attention state is sufficient.
- Do not rename a project folder when the requested action is changing the project display name; project display name and storage-folder location are separate concepts.

## 22. Current execution status

The source tree contains the cumulative uncommitted implementation changes made during the handoff work. The working branch remains `dev`. No commit or push has been performed. Source-only audits and whitespace checks have been used where requested, and builds, compilation, lint, and tests have intentionally not been run unless explicitly authorized.
