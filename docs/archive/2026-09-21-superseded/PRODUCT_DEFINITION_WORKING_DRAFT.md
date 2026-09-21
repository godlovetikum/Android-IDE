# Android IDE — Product Definition Working Draft

**Status:** Working product definition. This document defines the product we intend to build; it is not a description of the current implementation and does not authorize source-code changes.

## 1. Product purpose and scope

Android IDE is a mobile development workspace that brings project management, code editing, terminal work, runtimes, dependencies, browser use, web previews, developer tools, Git, extensions, credentials, security, and customization into one Android application.

The product exists because Android developers currently experience friction when they switch among separate applications, lose visible context, depend on editors with limited capabilities, or lose background work when Android recreates or terminates an application process. Android IDE must preserve durable state where possible, distinguish hidden work from stopped work, report Android limitations honestly, and avoid presenting unavailable work as if it still exists.

The product scope is **mobile-first**. One primary screen or panel is visible at a time. Desktop-style multi-pane presentation is deferred and is not an active product requirement.

## 2. Product principles

### 2.1 Clear ownership

Every capability has an owner. Project files and project metadata are project-owned. Terminal sessions, browser tabs, and global Git configuration are global. Repository state is owned by Git. The application shell provides navigation but does not take ownership of the data it exposes.

A global tool may be opened while a project is selected without becoming project-owned. A terminal started at a project folder is still a global terminal session with an initial working directory.

### 2.2 One source of truth

The user interface must report the state held by the underlying file provider, runtime, browser, or Git repository. It must not maintain a conflicting shadow state that remains different from terminal commands or external changes.

### 2.3 Predictable operations

Creation, rename, move, copy, cut, paste, delete, import, export, save, Git operations, and other mutations must have explicit targets, visible scope, predictable results, and structured outcomes.

### 2.4 Progressive disclosure

The application should expose capabilities where users naturally expect them. It should use context menus, grouped settings, domain-specific controls, and layered navigation instead of displaying every operation on every screen.

### 2.5 Feedback is part of the operation

An operation is not complete from the user’s perspective until the application reports whether it is processing, complete, partially complete, blocked, interrupted, or failed. Feedback must remain visible and understandable in the current interaction context.

### 2.6 Android conventions

Normal tap opens or selects. Long press exposes context or enters selection mode. Back follows the navigation stack. Press-and-hold controls repeat operations when repetition is meaningful, such as cursor movement or selection expansion.

## 3. Product domains

1. **Project and workspace management** — acquisition, registration, storage, project details, organization, and workspace context.
2. **Code editing** — file navigation, editor tabs, document editing, search, replacement, templates, formatting, and recovery.
3. **Terminal, runtimes, dependencies, and background processes** — global command execution, package management, processes, development servers, and runtime backends.
4. **Browser, previews, and developer tools** — ordinary browsing, tabs, downloads, previews, console, inspection, and viewport testing.
5. **Git integration and remote repository synchronization** — global Git configuration, credentials, repository state, operations, and remote synchronization.
6. **Extensions, credentials, security, settings, and customization** — extensions, application preferences, secure secrets, permissions, and display behavior.

## 4. Application shell and navigation

### 4.1 Home

A fresh launch always opens the Home screen, regardless of the domain that was active when the previous session ended. Home is an entry point and orientation surface, not a dashboard. It presents navigation controls for Projects, Editor, Terminal, Browser, Git, Extensions, Settings, and other approved global areas.

Home may provide controls that lead to process management or other areas. It must not summarize running processes, project data, browser tabs, Git activity, or other workspace state.

### 4.2 Sidebar structure

The sidebar is a contextual navigation panel with two functional areas.

The **upper area** provides compact quick navigation for high-priority destinations such as Home, Editor, Terminal, Browser, Git, and a Navigation or More control. More substitutes or expands the compact destinations with remaining screens and grouped operations such as Projects, Extensions, Settings, Credentials and Security, and Display. It should not simply repeat buttons already visible.

The **lower area** shows context for the current screen when that screen naturally owns a sidebar context:

- Projects shows recent or registered projects and project-management navigation.
- Editor shows the project file tree and editor-related controls.
- Terminal shows terminal sessions.
- Extensions shows extension navigation or installed extensions.
- Settings shows grouped settings categories.

Screens without a natural sidebar context, such as Home, Project Details, or Git, may retain the previous meaningful context. Home has no Home-owned data context. Browser tabs and browser controls remain inside the browser’s own conventional layout rather than being copied into the application sidebar.

### 4.3 Layered navigation

The sidebar and domain screens should provide clear routes between related levels. For example, selecting Git inside a project opens repository Git for that project, while a visible route leads to global Git settings. Selecting Settings opens grouped settings, while each group leads to its own focused settings screen.

## 5. Project and workspace management

### 5.1 Project acquisition

Project acquisition is initiated from the Projects or Project Management surface through one clear action. The available routes are:

1. Create a blank project.
2. Import an existing folder.
3. Import a ZIP archive.
4. Clone a remote Git repository.

These are user-facing workflows, not buttons that must appear everywhere. Each route returns through common verification, registration, and workspace-opening behavior.

### 5.2 Blank project

The workflow collects a project name and description, allows the user to choose a destination, and presents a review containing the name, description, and full target storage path. The user can correct any value before creating the project.

Creation verifies the selected location and must never silently fall back to app-internal storage. It reports progress and distinguishes complete, partial, blocked, interrupted, and failed results. A newly created project initializes the required project metadata and agreed project templates. Ordinary user files, including a README that the user later deletes, must not be recreated automatically on every reopen.

### 5.3 Existing folder

The workflow opens Android’s folder picker. The user selects an existing directory rather than entering a new project destination. After selection, Android IDE verifies the selected folder’s full storage path, existence, access, structure, permissions, and whether it is already registered.

The import review presents the selected project path and the result of those checks. It may derive a display name from the folder name and allow the user to provide or change a description, but it must not ask the user to review a new destination that the workflow will create. The workflow does not create or replace the selected folder. It initializes only required IDE metadata, registers the verified project, and offers to open it.

If the selected folder is unavailable, inaccessible, already registered, or otherwise unsuitable, the workflow remains open and reports the condition so the user can choose another folder or resolve the issue. It does not silently select another path or fall back to internal storage.

Android IDE supports editing projects in user-accessible local storage, including local device storage, accessible external storage, and accessible SD-card storage. When a selected folder is backed by a cloud or remote document provider, Android IDE does not register it as an in-place editable project. Instead, the user must import or download its contents into a user-selected local destination, review the resulting project identity and destination, and edit the local copy. The original cloud content remains a source or export target; it is not silently treated as the live project working copy.

This is also the normal route for registering a folder created through the global terminal, including a folder produced by a project initializer or `git clone`. The terminal does not need an Import as Project command.

### 5.4 ZIP archive

The workflow verifies that the selected item is a readable supported ZIP archive, validates its contents, prevents unsafe archive paths, defines the extraction destination and collision behavior, reports extraction progress and partial failures, verifies the extracted location, and registers it only after successful inspection.

### 5.5 Git clone

The workflow accepts repository information, allows selection of a provider and credential, lets the user select a destination, shows a review, reports clone progress and errors, and completes through common project inspection and registration.

Credentials must not appear in project metadata, source files, logs, visible output, or unsafe remote URLs.

### 5.6 Reviewed project identity and destination for ZIP and Git acquisition

ZIP import and Git clone use the same reviewed project-setup pattern as blank-project creation. The workflow begins by selecting a ZIP archive or entering a Git repository link. Android IDE derives a proposed project name from the archive name or repository information and presents it to the user for review.

The user can edit the proposed project name, enter an optional description, choose a storage location, and review the complete target path before extraction or cloning begins. The project name is a user-controlled project identity; it is not required to remain identical to the archive filename or repository name.

Before creating the destination folder, Android IDE inspects the selected parent location for conflicts, including an existing folder, file, registered project, inaccessible item, or other condition that would make the target ambiguous or unsafe. The workflow must report the conflict and provide a deliberate resolution path, such as choosing another name or location. It must not silently overwrite, merge, rename, or fall back to another storage location.

Only after the user confirms the reviewed name, optional description, source, and destination, and the conflict check succeeds, may Android IDE create the project directory and begin extraction or cloning. Progress, partial completion, failure, and cleanup results are reported through the common acquisition feedback model.

### 5.7 Universal destination-conflict behavior

All acquisition workflows stop before creating content when the requested destination conflicts with an existing item. The workflow remains open so the user can review and change the project name, description, storage location, credential, or other input. It reports the exact conflict inline within the active creation or acquisition surface.

A conflict message identifies the relevant condition, such as: “This folder already exists in this directory” or “This file already exists in this directory.” It explains the practical resolution, such as choosing a different directory or entering a different project, folder, or file name. The workflow must not silently replace, merge, overwrite, rename, or redirect the requested destination.

Before creating a destination folder for a blank project, ZIP extraction, or Git clone, Android IDE inspects the selected parent location and its children. The same preflight rule applies to file and folder creation inside a project.

### 5.8 Project destination containment

A project destination must never be located inside another registered project. Before Android IDE creates, imports, extracts, clones, copies, duplicates, or moves a project, it compares the canonical target path with every registered project location.

The target is invalid if it is the same as another registered project, a child of another registered project, or a parent that would contain another registered project in a way that breaks project ownership and registry safety. The workflow stops before creating or copying content and reports the conflicting registered project and paths. The user can choose another destination or change the project name where that resolves the conflict.

This containment rule applies to blank-project creation, existing-folder import, ZIP extraction, Git clone, Copy & Duplicate, and Change Location. Android IDE must not allow a project to be created inside another project merely because the nested folder is currently accessible. This prevents deleting or moving a parent project from invalidating a child project record.

### 5.9 Project registry and project list

The Projects screen maintains registered project records. A project item normally shows its name, description when available, file count, project size when available, and relevant availability, permission, Git, synchronization, or warning indicators.

The list searches registered project records, primarily by project name. It does not recursively search device storage, storage-provider roots, or unregistered folders. It provides explicit sorting such as name, last opened, registration time, file count, or project size when those values are available.

When the Projects screen opens, Android IDE loads registered project records immediately and does not block the list on complete recursive inspection. It verifies visible or relevant information progressively. File count, project size, permissions, and availability show an appropriate loading, unavailable, or stale state when current values are not yet known.

The Projects screen provides an explicit Refresh action and supports pull-to-refresh. Refresh re-inspects registered project locations and updates the values that can be verified. Opening Project Details or starting an operation triggers the verification required for that action. The list does not continuously and recursively verify every project, and it does not present stale values as guaranteed current.

A registered project remains in the registry when verification fails, but it is marked unavailable or requiring attention. The user can inspect the reason, retry verification, choose another location where supported, or remove the project record.

### 5.10 Registry removal and permanent deletion

**Remove from Registry** and **Permanently Delete** are separate operations with different ownership and consequences.

Remove from Registry removes the project record from Android IDE’s global project registry and removes IDE-owned registry references. It does not delete, modify, move, or rename the user’s project files, Git data, ordinary README files, or selected storage location. Project-associated metadata is removed only when the product explicitly defines that metadata as registry-owned and the user is informed of the scope. The workflow verifies that the registry record is gone while preserving the project location.

Delete Permanently removes the selected project data from its user-authorized storage location. It is a destructive operation that requires an explicit confirmation identifying the full project path, project name, and scope of deletion. It must not be presented as equivalent to registry removal.

Before permanent deletion, Android IDE verifies the project location and reports provider permissions or limitations. During deletion it reports progress and handles nested files and folders through the controlled storage route. Afterward it verifies that the intended project location and contents are gone, or reports exactly what remains and why. A provider failure, permission loss, partial deletion, or uncertain result remains visible in the deletion workflow and is not reported as success.

Permanent deletion must never delete a parent directory outside the selected project, another registered project, or an unrelated item that shares a display name. The storage route uses the verified project identity and location rather than a display name alone.

### 5.11 Change Location and Copy & Duplicate

**Change Location** moves an existing project to a new valid destination. The workflow shows the current full project path, lets the user choose a destination parent and confirm the project name, checks conflicts and project containment, previews source and destination paths, and waits for explicit confirmation. It then recursively copies the project through the controlled storage route, verifies the destination, removes the original only after successful verification, updates the registry to the new path, and reports the complete result.

**Copy & Duplicate** is one project operation. It works like blank-project acquisition, but it starts with the selected project’s existing information and content. The workflow presents the source project, current project name, description, storage-related details, and relevant project information as initial values. The user can change every value that is meaningful and editable, including the new project name, description, destination, and other supported project settings. The original project remains unchanged.

Before copying, Android IDE derives the proposed target path from the reviewed name and destination, checks conflicts, verifies that the target is not inside another registered project, and presents the source and target for confirmation. After confirmation, it copies the project through the controlled storage route, verifies the copied result, initializes or updates project-specific identity as required, and registers the new project only after verification. The operation reports progress, partial completion, failure, cleanup, and the final registered result.

A destination conflict stops Change Location or Copy & Duplicate before content is created or copied and leaves the review surface open for correction.

### 5.12 Project interactions and batch operations

A normal tap opens a project. A context-menu control and long press expose project operations. Long press may open a context menu or enter multi-selection, but the behavior must be clear and consistent.

The individual-project context menu in the Projects list and the context menu in Project Details expose the same project-management actions and corresponding workflows. These shared actions include Project Details, Refresh, Rename, Change Location, Copy & Duplicate, Export or Share, Copy Storage Path, Copy Remote URLs, Remove from Registry, and Permanently Delete. The project-list menu may additionally provide contextual entry actions such as Open in Editor, Open Git, Open Terminal, and Open Browser or Preview when the project contains applicable content. Project Details may provide the same entry actions when they are relevant to the inspected project.

Multi-selection shows the selected count and exposes exactly these batch operations:

- Remove from Registry;
- Permanently Delete;
- Export or Share;
- Copy Storage Path.

Batch operations identify the selected projects before execution and report per-project results. Remove from Registry never deletes user files. Permanent deletion requires explicit destructive confirmation and verified scope. Export or Share creates one ZIP archive per selected project using the project name as the archive name, subject to collision handling in the destination. Copy Storage Path copies the full storage paths for the selected projects in a useful text format.

### 5.13 Export and Share as ZIP

Export or Share, whether initiated from the Projects screen, Project Details, an open project, or a selected folder inside a project, creates a ZIP archive containing the selected project or folder and its contents. The archive uses the source project or folder name as its default archive name.

The default export destination is Android’s standard Downloads folder, the same default destination used for browser downloads. The workflow shows the archive name, source path, destination path, archive contents or scope where practical, and any conflict with an existing archive before creation. The user may save the archive to another permitted location or share the resulting ZIP through Android’s sharing flow.

Archive creation reports progress, completion, partial results, failure, and cleanup. It must not alter the source project or folder. An archive is not registered as a project automatically unless the user later imports it through the ZIP acquisition workflow.

### 5.14 Project Details

Project Details is a concise read-only information surface with contextual management actions. It may show identity, description, full storage location, file count, project size, metadata status, permissions, availability, and project-level diagnostics.

It may show Git remote URLs and branches as repository associations. It must not show commits, diffs, staging state, or other repository working-state details; those belong to Git.

Project Details exposes its management operations through a visible context control and a context menu rather than a row of permanent action buttons. Its context menu provides Refresh, Rename, Change Location, Copy & Duplicate, Export or Share, Copy Storage Path, Copy Remote URLs, Remove from Registry, and Permanently Delete. It also provides Open in Editor, Open Git, Open Terminal, and Open Browser or Preview when those entry actions are applicable to the project. Destructive actions remain clearly separated and require their own confirmation workflow.

### 5.15 Project lifecycle

Project existence and permissions must be checked when required. If a project cannot be inspected and confirmed to exist, Android IDE must not open it as healthy. It should identify whether the project is recoverable, unavailable, or irrecoverable and explain the next action.

## 6. Editor and file-tree

### 6.1 Editor structure

The Editor consists of a contextual project file-tree sidebar, an editor tab area, and one active document editing area. The file tree provides project navigation and file/folder management. The tab area provides document switching. The active editor provides document editing and document-level commands.

The Editor is available only when a project is selected. The renderer may initialize independently for responsiveness, but the user cannot access project files, editor tabs, previews, or save operations without a project context.

### 6.2 Mobile editing behavior

Editor controls must support touch selection, cursor movement, indentation, undo, redo, save, dirty-state indication, and keyboard interaction. Controls that mutate cursor position, selection, indentation, or another repeating operation support press-and-hold repetition when the operation logically repeats.

Editor actions should use supported Monaco shortcuts and capabilities for formatting, cursor control, selection, find, replace, and related operations. Code font size is separate from application UI font scaling.

### 6.3 File-tree presentation

Files and folders use familiar, type-appropriate icons and visual states. Distinctive icons should be available for HTML, CSS, JavaScript, TypeScript, JSX/TSX, XML, SVG, SQL, images, video, PDF, PHP, Python, Gradle, Kotlin, Java, Markdown, and other supported types. Icons supplement clear labels and accessibility descriptions; they do not replace them.

### 6.4 File and folder operations

The file tree provides contextual operations for creating, opening, editing, renaming, deleting, duplicating, copying, cutting, pasting, moving, exporting, sharing, importing, copying a path, opening a terminal at a folder, and opening previewable content in the browser.

Create and rename workflows accept a complete relative path, not only a leaf name. They support intermediate or nested folders where appropriate and show the resolved full path, destination, collision behavior, and affected project.

Deleting a file must not delete its parent folder or project. Deleting a folder affects only that folder and its contents. Cross-project copy and cut/paste identify both source and destination projects and paths.

### 6.5 One controlled file-mutation route

Every file and folder mutation passes through one controlled storage/repository route. This includes create, rename, delete, move, copy, cut, paste, duplicate, import, export, sharing preparation, and metadata-related project operations.

The route validates source and destination, handles collisions, reports provider capabilities, verifies the result, and returns structured outcomes. Move and rename may use verified recursive copy-and-delete when provider-native operations are unreliable, but the source is not removed until the destination is sufficiently confirmed.

### 6.6 Mutation preflight and post-operation verification

Every file, folder, and project creation operation performs a preflight inspection of the intended parent location before it attempts to create anything. It compares the user’s requested name and target path with the existing children at that location. If a conflict exists, the operation stops before creation and reports the conflict inside the active modal or workflow surface. The user may change the name or location and retry without losing the rest of the reviewed input.

The storage route must not rely on a provider to resolve a conflict by silently renaming the requested item. The requested destination is either created with the requested identity or the operation is reported as unsuccessful. Cancellation remains available through the active workflow; it is not treated as an error or as an implicit exit from the project surface.

After a create, copy, move, rename, extraction, or clone operation completes, the storage route re-inspects the result and compares its returned identity, name, parent, and path with the user’s requested target. If a provider created an item with a different identity or location, the operation is not accepted as successful. When safe cleanup is required, the route may remove only the specific item identified by the operation’s verified return value and must never delete an unrelated existing item based only on a matching name.

The route reports complete, blocked, failed, partial, or mismatched outcomes. All conflict and verification feedback remains visible in the relevant modal or workflow surface when that surface is still active. A hidden toast is not sufficient for a blocked or failed mutation.

### 6.7 Mutation feedback

Mutations expose processing, progress where meaningful, success, partial completion, blockage, interruption, failure, and recovery options. Modal workflows keep important feedback inside the modal when it remains relevant. A successful lightweight operation may use a visible transient notification. A failed, blocked, or partial operation that requires attention uses inline feedback or a noticeable modal rather than a hidden toast.

Feedback must not be hidden behind the keyboard, a modal surface, or the bottom edge of the screen. The full path should be shown, preferably from the device storage route when appropriate and from the project-relative route when working inside a project. An operation that cannot be verified must not be reported as successful.

### 6.8 File path navigation

The file path displayed above the active editor is the navigation entry point. It is clickable and expands a directory-oriented dropdown showing the current file’s siblings and its surrounding folders. A root README shows its root-level siblings. A file such as `web/src/index.html` opens the relevant directory context and allows navigation upward or downward.

A separate ambiguous context-menu button must not replace this file-path entry point.

### 6.9 Preview and file-size rules

A Preview action is placed in the editor’s top-right action area. It is enabled when the active file or project contains content that can be previewed safely in the Browser and is intentionally disabled when no applicable preview exists.

Previewable content may include Markdown, HTML, text, SVG, web images, video, PDF, and other browser-displayable formats. Binary files that the browser can display should open in the Browser rather than producing an unsupported-editor format error.

The editor supports editable code and text files up to a defined limit of approximately five megabytes. Files larger than that limit show a clear file-too-large result and an appropriate alternative, such as opening in the Browser when supported. The exact limit may be refined through performance testing, but the user-facing rule must remain explicit.

### 6.10 Syntax highlighting and templates

The editor must provide syntax highlighting for a broad set of languages and formats, including XML, SVG, Python, JavaScript, TypeScript, JSX/TSX, HTML, CSS, SQL, PHP, Kotlin, Java, Gradle, Markdown, and other supported types. Unsupported highlighting must fall back clearly to plain text rather than implying language support.

The editor supports file templates and template initialization shortcuts where the editor provider and file type support them. Templates must be explicit and must not recreate ordinary user files without user action.

### 6.11 Search and replace

Search capabilities are separate features with separate entry points and responsibilities. They must not be merged into one ambiguous search control.

**Document Find** and **Document Find and Replace** operate only on the active document. They are available from the Editor top bar. Document Find does not mutate content. The user explicitly enables replacement before Replace or Replace All becomes available. The search field, match count, current-match controls, replacement field, and replacement actions remain clearly distinguishable.

**Project Filename Search** searches registered files and folders by name or path. It is available from the Editor file-tree sidebar and does not inspect file contents.

**Project Content Search** recursively searches the contents of eligible files across the selected project. It is available from the Editor file-tree sidebar and is not the same feature as Project Filename Search.

Project Content Search provides every match with its file path, line number, relevant line or context, and match position where available. Selecting a result opens or activates the corresponding file, scrolls the editor to the matching line and position, and highlights the match. The result remains associated with the search query while the user reviews matches.

Project Content Search also provides an explicit content replacement mode. It follows the same conceptual workflow as Document Find and Replace but applies across the selected project: the user enters a search query, reviews all matches, optionally enables replacement, enters replacement content, previews or confirms the affected files and matches, and then executes Replace or Replace All. Replacement must be deliberate, report per-file results, preserve a recoverable operation state where possible, and never be activated merely because the user typed into the search field.

The file-tree sidebar exposes Project Filename Search, Project Content Search, file creation, folder creation, file import, Locate Current File, and related project actions. The Editor top bar exposes Document Find and Document Find and Replace. These locations follow the ownership and scope of each operation.

### 6.12 Editor tab states

A **temporary tab** is opened without content changes. It may originate from the file tree, a search result, file creation, import, or another path. Opening the same unchanged file through another route reuses the same document tab. A temporary tab becomes permanent when the user edits it or pins it. Temporary tabs do not survive a new application session.

A **permanent tab** is a tab whose file content was changed after opening, whether the changes are later saved or remain unsaved. A permanent tab survives application sessions and may be closed by Close All Tabs unless it is pinned.

A **pinned tab** is explicitly pinned by the user. It survives Close All Tabs and other bulk-close actions. It can be closed only through an explicit close action directed at that tab, with dirty-state confirmation where required.

A **dirty tab** contains unsaved changes. Dirty state is separate from permanent and pinned state. Close All Tabs may close dirty tabs only after offering Save and Close, Discard and Close, or Cancel. A recovered tab containing unsaved content is reopened as a permanent dirty tab, not as a separate restored-tab category.

### 6.13 Tab actions

An individual tab may provide Pin Tab, Unpin Tab, Save, Save As, Close, Close Other Tabs, Close All Tabs, Refresh, Copy File Path, Reveal File in Project Tree, Duplicate File, and Duplicate Tab where applicable.

Refresh re-inspects the file path and storage state. If the file was deleted externally, the editor reports that state and offers to recreate it through Save As while preserving current contents, or discard and close the tab.

Duplicate File creates a separate file copy through the controlled file-mutation route. Duplicate Tab means opening another view of the same document identity; it does not create a file copy. If the product cannot support independent views safely, Duplicate Tab is omitted rather than presented as a misleading operation.

### 6.14 Visual tab state

Temporary, permanent, pinned, and dirty states must be visually distinguishable without relying only on color. Dirty tabs use an additional strong visual treatment, such as an accent, indicator, or background distinction, together with an accessible label. The visual treatment must remain legible under application font scaling and color-accessibility settings.


### 6.15 File-tree expansion and Locate behavior

The file tree may preserve expanded and collapsed folder state while the current application session remains active. This state does not need to survive across application sessions. The user controls expansion by expanding or collapsing folders; opening a file from the tree, a search result, an import workflow, or another route does not change the tree’s expansion state.

Switching editor tabs changes the active document only. It must not automatically expand the active tab’s parent folders, collapse other folders, or scroll the file tree. This prevents tab switching from causing unexpected navigation changes.

The file tree provides a dedicated **Locate Current File** action. When selected, it identifies the active editor file, expands the required parent folders, and scrolls the tree to reveal that file. Locate is the intentional action for changing the tree’s expansion and scroll position to match the active document.

The file-tree sidebar also provides mobile-friendly controls for filename search, recursive project-content search, document Find and Replace access, creating files, creating folders, importing files, and other approved file-management operations. These controls remain clearly distinguished by purpose and do not merge filename search with content search.

### 6.16 Sidebar scrolling and bottom space

All sidebar contexts use a consistent scrolling model. The sidebar content can scroll vertically, including the contextual lower section, while preserving the upper navigation controls according to the final mobile layout.

The scrollable lower content should retain additional bottom spacing of approximately twenty-five percent of the device viewport height. This space prevents the last item from being crowded against the bottom edge, keyboard, gesture area, or other system UI and gives the user room to position content comfortably. The exact spacing adapts to the device and insets, but the design principle applies to every sidebar context, including Projects, Editor, Terminal, Extensions, and Settings.

### 6.17 Editor top-bar layout

The mobile Editor top bar follows a fixed, readable order:

```text
[Sidebar] [Clickable active-file path] [Find] [Replace] [Save / Save As] [Preview] [More]
[                         Editor tabs row                              ]
[                         Active document                              ]
[                 Symbol shortcut row, if enabled                      ]
[              Keyboard control row, if enabled                        ]
```

The sidebar toggle is at the top-left. Directly beside it is the clickable path of the active document. The path is the file-navigation entry point described above and must not be replaced by an unrelated context-menu button.

The right side provides direct actions for Search, Replace, and saving. Save is available when the current document requires manual saving, including when autosave is disabled. Save As is available for blank or newly created documents and remains accessible through the tab menu or More actions when appropriate.

The Preview or Run action sits immediately to the left of the top-right More control when preview or run is applicable. It is disabled or omitted when no supported action exists. More contains lower-frequency document and editor actions without hiding the primary actions users need during ordinary editing.

The editor tabs occupy the row directly below the top bar. Each tab has its own context menu. The active document occupies the main area below the tabs. Optional document information, such as cursor line and column or file type, may appear at the bottom when enabled, but it must be configurable because it consumes valuable mobile screen space.

### 6.18 Shortcut rows above the Android keyboard

The Editor provides two independently configurable horizontal rows above the Android keyboard when enabled.

The **symbol shortcut row** provides frequently used code symbols and short insertions that improve mobile typing. It may include brackets, braces, parentheses, quotes, separators, operators, arrows, and user-configured symbols. Pressing a symbol inserts it at the editor cursor without requiring the user to switch keyboard layouts.

The **keyboard control row** provides mobile-accessible controls for indentation, outdentation, cursor movement, line navigation, selection, range selection, undo, redo, copy, paste, select all, keyboard visibility, and other supported editing commands. These commands should use Monaco’s command/action bridge rather than duplicating editor logic in the Compose layer.

Both rows remain visible above the Android keyboard while enabled. Their visibility, order, page arrangement, and symbol set are configurable. They must not consume space when disabled. The controls must support press-and-hold repetition for operations such as moving the cursor, extending selection, and repeated indentation where that behavior is meaningful.

Desktop-oriented editor shortcuts should be available through these controls and through physical-keyboard input when a hardware keyboard is connected. A visible button may represent a shortcut without preventing the user from entering the equivalent keyboard command.


### 6.19 Persistent editor display preferences

The symbol shortcut row, keyboard control row, and document-information/status row are configurable editor surfaces. Each is enabled by default so the complete mobile editing aid is available immediately.

The user can disable or re-enable each row through editor preferences. These choices are global application preferences, not project files or project metadata. They persist across application sessions and apply according to the user’s editor settings.

Android IDE must not silently change these preferences because of screen navigation, project changes, editor-tab changes, configuration refreshes, or lifecycle events. A layout adaptation may preserve the preference while temporarily explaining that a control is unavailable in the current input state, but it must not silently convert an enabled preference into a disabled one.

### 6.20 Editor tab scrolling

The editor tab row is a horizontally scrollable row. When tabs exceed the available width, the user swipes horizontally through the row to reach additional tabs. Android IDE does not require a separate tab overflow button or a second tab-list surface for ordinary navigation.

Each tab remains individually identifiable through its file name and visual state. Dirty, pinned, active, permanent, and temporary states must remain distinguishable within the scrollable row.

### 6.21 Editor Preferences

Editor Preferences provide a structured surface for the settings supported by the editor provider. Preferences are global application data, persist across sessions, and change only through explicit user action.

The preference surface should expose supported options including line-number visibility, word wrapping, editor theme, minimap visibility, autocomplete, syntax highlighting behavior, code font size, indentation, tab width, spaces versus tabs, bracket and quote behavior, formatting behavior, cursor style, scroll behavior, and other provider-supported editor capabilities.

A preference is shown only when the selected editor provider supports it, and unsupported options are not presented as if they work. Provider-supported capabilities are organized into predictable groups rather than exposed as an unstructured collection of switches. Each displayed preference must have defined behavior, visible state, and a clear unsupported or unavailable state when the provider cannot supply it.

Editor Preferences do not change project files unless the user explicitly chooses a project-specific setting and the product later defines that scope. The default scope is global application preference, separate from project data and project metadata.


### 6.22 Editor file-opening behavior

When the user opens an editable file from the file tree, a search result, file creation, import, or another valid route, the file becomes the active temporary tab unless it is already open. If the document is already open, Android IDE activates its existing tab rather than creating a duplicate document tab.

The horizontally scrollable tab row scrolls only as needed to reveal the newly active tab. Opening or activating a file does not expand, collapse, or scroll the file tree. The user uses Locate Current File when they intentionally want the active document revealed in the tree.

Opening a file does not change its temporary, permanent, pinned, or dirty state except when the user edits it or explicitly pins it according to the tab rules.

### 6.23 Code-editing commands and shortcuts

The Editor exposes code-editing commands through the top bar, tab menus, keyboard-control row, symbol row, document search controls, and physical-keyboard input when available. A command is included only when its behavior is meaningful for the current document and editor provider.

#### Essential document commands

The initial editor command set includes New File, Open, Save, Save As, Refresh, Close, Close Other Tabs, Close All Tabs, Pin Tab, Unpin Tab, Undo, Redo, Cut, Copy, Paste, Select All, Delete Selection, and Copy File Path. Commands that may lose content require dirty-state protection and explicit confirmation.

#### Cursor and selection commands

The mobile keyboard-control row includes cursor left, right, up, down, word left, word right, line start, line end, document start, document end, page up, page down, and matching selection-expansion variants. It also includes select word, select line, select to start, select to end, select all, expand selection, shrink selection where supported, and cancel selection.

Movement and selection controls support press-and-hold repetition when the operation is repeatable. Repetition must stop when the user releases the control and must not repeat after the control loses focus.

#### Indentation and formatting commands

The Editor includes indent, outdent, increase or decrease indentation where supported, format document, format selection, normalize line endings, and convert indentation style when supported. Formatting commands show an unavailable state when no formatter is available for the document language.

#### Search and navigation commands

The Editor includes Find, Find Next, Find Previous, Find and Replace, Go to Line, Go to Column, Go to Matching Bracket, Go to Definition where language intelligence supports it, and Locate Current File. Search and replacement remain separate from Project Filename Search and Project Content Search.

#### Multi-cursor and editing productivity

Where supported by the editor provider, the Editor may include add cursor above, add cursor below, add cursor at next match, select all matching occurrences, remove the last cursor, and collapse to one cursor. These commands require clear visual indicators because multiple insertion points can be difficult to understand on a small display.

#### Code-structure commands

Where supported by the language provider, the Editor may include fold, unfold, fold all, unfold all, fold level, comment line, uncomment line, toggle block comment, rename symbol, code action, autocomplete, parameter hints, signature help, and quick documentation. These capabilities depend on language intelligence and must not be presented as universally available for every file type.

#### View and navigation commands

The Editor may include toggle line numbers, toggle word wrap, toggle minimap, toggle whitespace markers, toggle indentation guides, toggle bracket-pair guides, increase or decrease code font size, and focus or hide the keyboard. These are preferably controlled through Editor Preferences, with only frequently used view controls exposed in the More menu.

#### Command presentation rules

The top bar contains only high-frequency actions: Search, Replace, Save or Save As when applicable, Preview or Run when applicable, and More. The keyboard-control row contains repeatable cursor, selection, indentation, clipboard, and editing actions. The symbol row contains insertable symbols and short snippets. Tab menus contain tab and document-lifecycle actions. Editor Preferences contain persistent display and behavior settings. Unsupported commands are disabled with an explanation or omitted; they are never shown as if they work.

## 7. Terminal, runtimes, dependencies, and background work

### 7.1 Global terminal sessions

The terminal is a global standalone feature. It is not owned by a project and is not restricted to the current project root. The user can create any terminal session, edit its session name, switch between session tabs, and close a session explicitly. The Terminal screen presents the user’s global session list and the selected session’s terminal surface.

Switching screens, tabs, or terminal sessions does not close or stop the session. Closing one session shuts it down in the terminal backend and consequently closes the processes and background work owned by that session, including dependency installation, local servers, and other commands running within it. The Terminal surface also provides Close All Sessions with the same explicit lifecycle meaning for every selected session.

A terminal session can be created from the global Terminal surface or from a folder or project context. Opening a terminal from a folder creates a global session whose initial working directory is that folder. Opening a terminal from a project uses the project location as the initial directory but does not make the session project-owned.

The session model follows the capabilities and lifecycle semantics of the selected third-party terminal backend. Android IDE should expose only states the backend can reliably provide. The general session availability indicator is **Available** or **Unavailable**. The product does not require artificial Paused, Idle, or Running states merely for presentation. Backend output and command results remain visible within the selected available session.

### 7.2 Terminal runtime backend

The terminal uses an embedded third-party backend that provides shell and runtime capabilities. Android IDE controls access, session selection, naming, explicit close actions, capability reporting, and security boundaries. The product must not assume that `apt`, `pkg`, `sudo`, Docker, or another command exists; it exposes the commands and packages that the selected backend actually provides.

The terminal supports practical shell commands, package installation, Git, dependency management, file operations, Node and other supported runtimes, development servers, and long-running processes within Android and backend limitations. Switching away from a session does not terminate it. Closing the session is the explicit action that shuts it down.

The terminal surface supports practical zooming or display scaling so the user can enlarge or reduce terminal content without changing command or process behavior.

The application lifecycle is designed for durable, user-visible work. Switching applications, hiding the Android IDE behind another application, navigating to Home, changing domains, changing terminal sessions, or leaving the device idle for an extended period must not by itself terminate the application runtime, terminal sessions, development servers, or other active background work. The application should use the strongest Android-supported lifecycle mechanism appropriate for this user-visible work and should keep the runtime available until the user explicitly terminates it or Android, the device, or the selected backend imposes an unavoidable termination.

This is a persistence and survivability requirement, not a claim that Android can be prevented from terminating a process in every circumstance. If Android or the backend terminates work, the application must preserve the session record, report the resulting availability, and provide supported recovery or cleanup actions rather than silently treating the work as completed.

### 7.3 Terminal session persistence

Terminal session records are global application state and persist across application launches. The underlying runtime should remain alive while the user moves between applications and while the device is idle for an extended period, subject to Android and backend limits. A fresh launch still opens Home rather than automatically placing the user inside the last terminal session. The user opens Terminal through normal navigation and selects the session to view.

When a stored session can be reconnected through the terminal backend, selecting it reopens the session and its available terminal output or working context. When the backend cannot reconnect to a stored session, the session record remains visible as **Unavailable** with an explanation and any supported recovery or cleanup action. Android IDE must not present an unavailable session as active or silently create a replacement session under the same identity.

Switching away from Terminal, returning to Home, navigating to another domain, or closing the application surface does not mean that the user explicitly closed a terminal session. Only an explicit Close Session or Close All Sessions operation, or an explained unavoidable backend or Android lifecycle event, closes or invalidates the session. The application reports the resulting availability and does not silently discard the session record.

## 8. Browser, previews, and developer tools

The Browser is global and follows normal browser conventions: URL/search field, tabs, navigation, downloads, browser actions, settings, and browser-specific menus. It can browse the internet without a project and can display local files, development-server output, and project previews.

Developer tools include JavaScript console commands, console output, network inspection, local-storage inspection, cache inspection, and other supported browser state. A suitable established console or developer-tools component may be integrated when technically, legally, and securely appropriate.

The Browser supports viewport testing with mobile, tablet, desktop, and custom width presets. The user may enter a custom width in pixels. The product should support a sensible minimum of approximately 280 pixels and a reasonable implementation-dependent maximum. The viewport width changes the page’s content viewport, not the physical Android display.

## 9. Git integration

### 9.1 Connected levels

Git has two connected levels. They are not isolated features.

The **Global Git level** manages providers and hosts, credentials, Git username and email, credential-store configuration, SSH and known-host configuration where supported, global ignore rules, default behavior, and other global Git settings. It is available without a selected project and is reachable from repository Git.

The **Repository Git level** opens when the user selects Git inside a project. It manages substantial repository operations from a user-friendly layer over the same Git capabilities used by the terminal.

From repository Git, the user can navigate to Global Git Settings, Manage Credentials, provider management, and other global configuration. From global Git, the user can discover or select repositories and open repository Git where access is available.

### 9.2 Credentials and providers

Android IDE supports multiple Git providers and hosts. The user can configure and switch credentials rather than binding a project permanently to one provider.

A credential may include provider or host, account username, account email, access token or other supported authentication material, and a user-defined name. Credentials can be added, selected, switched, updated, revoked, and removed through secure workflows. Secrets are encrypted and never written to project metadata, source files, logs, visible output, or unsafe URLs.

### 9.3 Repository operations

Repository Git provides status, changed files, staged and unstaged changes, diffs, staging, unstaging, commits, commit messages, history, branches, branch creation, branch switching and checkout, safe branch deletion, tags where supported, remotes, fetch, pull, push, synchronization, merge, rebase where supported, conflict visibility and resolution, restore, reset, revert, clean or discard with safety controls, and repository-specific configuration.

Checkout, branch switching, restore, reset, revert, commit, and history mutation show scope, affected files or references where practical, risk, and resulting state. Destructive or irreversible operations require clear confirmation.

### 9.4 Terminal interoperability

Git commands run through the terminal and Git actions run through the Git interface operate on the same repository and configuration. The Git interface refreshes or invalidates stale views and reports externally changed state. A terminal command such as staging, checkout, commit, reset, or remote mutation must be reflected in the repository Git view.

## 10. Extensions, settings, security, and credentials

Settings is a grouped entry point rather than one long undifferentiated list. Groups may include application preferences and display, editor preferences, terminal and runtime preferences, browser preferences, credentials, security, storage and permissions, extensions, and other approved areas.

Extensions are an explicit product-definition placeholder. Their installation, permission, lifecycle, update, removal, execution, and security rules are not defined in this working specification and must be decided in a later domain phase. Credentials and secrets use shared secure infrastructure while Git and terminal expose the workflows relevant to their domains.

## 11. Storage and data ownership

Android IDE uses four storage layers:

1. **User project files:** source files, documentation, generated files, and Git repository data such as `.git`.
2. **Portable project metadata:** project-specific IDE state that can be migrated with or alongside the project where appropriate.
3. **App-private project-associated runtime state:** sessions, PTYs, logs, browser runtime data, caches, temporary files, and device-specific handles.
4. **Global application data and preferences:** project registry, recent-project order, encrypted credentials, global settings, diagnostics, and application-wide services.

Project source files remain in a user-accessible local project location and are edited in place. Android IDE must not hide the authoritative project source inside app-private storage merely because the terminal or another provider requires ordinary filesystem access. The embedded terminal/runtime filesystem capability must work with the selected local project location or provide an equivalent user-accessible local filesystem arrangement without creating a hidden second source of truth.

Projects may use either a **user-visible local location** or an explicitly selected **private development workspace** provided by the integrated Termux runtime. The private development workspace is an Android IDE-managed project location with stronger Unix/POSIX behavior for package installation, native binaries, symlinks, executable scripts, language servers, file watching, and local development servers. It is not a hidden second copy: when selected, it is the project’s authoritative working location and must be shown in project details. Android IDE must provide explicit export, sharing, backup, copy, move, and relocation workflows because unrelated applications cannot ordinarily browse that private location.

A user-visible local location is eligible only when the integrated editor and runtime can perform the required project operations on that same location, including reading, writing, creating, renaming, deleting, moving, executing supported files, and observing relevant file changes. If the selected local provider cannot support the required operations, Android IDE rejects the location with an actionable explanation; it must not silently create a private working copy or select another location. The user may deliberately choose the private development workspace instead.

Cloud-backed or remote document locations are not supported as live editable project locations. A cloud-based project is imported or downloaded to a user-selected local location before it becomes an Android IDE project. The local copy is then the authoritative project source for editing, terminal operations, Git, language servers, previews, and project metadata. Any later upload, export, or replacement of the cloud source is an explicit user operation with visible source and destination; automatic two-way synchronization is not part of the product definition.

## 12. Definition boundaries and implementation gates

This document defines the intended product behavior and ownership rules. It does not define final source architecture, data schemas, provider libraries, runtime implementation, or test code.

Before implementation begins for a domain, its behavior must be converted into a focused plan. Development must target one subsystem at a time, avoid broad rewrites, document risks, and test each domain independently before cross-domain integration.

The working draft remains open for clarification. Once the product definition is agreed, a finalized product-definition document should be created and implementation planning should reference that document rather than this working draft.
