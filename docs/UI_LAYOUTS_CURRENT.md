# Android IDE — Current UI Layout Atlas

> **Purpose:** Text-only illustration of the UI currently implemented on the `dev` branch. This is a structural wireframe, not a visual screenshot. It describes the layout hierarchy, controls, states, and user flows currently represented by the Compose surfaces.

## 1. Global application frame

The application uses a full-screen Compose shell with a modal navigation drawer. The shell itself does not add a generic top bar; each major surface owns its own header.

```text
┌────────────────────────────────────────────────────────────┐
│                    CURRENT SURFACE                         │
│                                                            │
│                Surface-owned content                       │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### Global behavior

- The drawer is opened by the surface-owned menu icon.
- Opening the drawer dismisses the editor keyboard and clears editor focus.
- Drawer gestures are disabled while the drawer is closed; navigation is controlled by the explicit menu affordance.
- Navigation is blocked or confirmed when a file operation is active.
- The shell preserves surface state when moving between surfaces.
- Back behavior is layered:
  1. Close an open drawer.
  2. Dismiss an operation or exit prompt.
  3. Dismiss an editor search surface.
  4. Pop nested navigation.
  5. Request application exit.

## 2. Sidebar / navigation drawer

The drawer is a modal sheet. Its top row contains the permanent domain controls. The lower content changes according to the selected sidebar context.

```text
┌──────────────────────────────────────────┐
│              Android IDE drawer           │
├──────────────────────────────────────────┤
│  Navigation   Editor   Terminal   More…   │  ← permanent domain shelf
├──────────────────────────────────────────┤
│                                          │
│  Contextual lower section                 │
│  (depends on selected shelf item)         │
│                                          │
└──────────────────────────────────────────┘
```

### Navigation section — default section

```text
┌──────────────────────────────────────────┐
│ Navigation                               │
├──────────────────────────────────────────┤
│ Home                                     │
│ Projects                                 │
│ Editor                                   │
│ Terminal                                 │
│ Browser                    Coming soon   │
│ Git                       Coming soon    │
│ Settings                                 │
├──────────────────────────────────────────┤
│ Recent projects                          │
│   • Project Alpha                        │
│   • Project Beta                         │
├──────────────────────────────────────────┤
│ + Create project                         │
│   Import folder                          │
│   Import ZIP                             │
└──────────────────────────────────────────┘
```

### Editor section

When a project is open, the Editor drawer section owns the project file tree rather than the editor content area.

```text
┌──────────────────────────────────────────┐
│ Editor                                   │
├──────────────────────────────────────────┤
│ Files   [search] [locate] [+file]        │
│         [+folder] [more]                  │
├──────────────────────────────────────────┤
│ ▼ Project Alpha                          │
│   📄 index.html                          │
│   🎨 styles.css                          │
│   JS app.js                              │
│   TS types.ts                            │
│   ▼ src                                  │
│     📄 main.kt                           │
│                                          │
└──────────────────────────────────────────┘
```

The file-tree `more` menu contains:

```text
Refresh files
Import files
Export project
Project details
```

File and folder context menus expose operations such as open, rename, duplicate, copy, cut, paste, delete, export, copy path, and terminal-at-folder where supported.

### Terminal section

```text
┌──────────────────────────────────────────┐
│ Terminal                                 │
├──────────────────────────────────────────┤
│ New session                              │
│ Existing sessions                        │
│   Untitled session                       │
│   Build session                          │
└──────────────────────────────────────────┘
```

### More / secondary navigation

Secondary domains remain visually separated from the primary navigation and show plain explanatory text when not implemented. They do not masquerade as active feature cards or operational controls.

## 3. Home screen

```text
┌────────────────────────────────────────────────────────────┐
│ [⋯]  Home                                                   │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ Welcome back techie! Choose a domain to continue            │
│                                                            │
│ ┌──────────────────────┐  ┌──────────────────────┐          │
│ │ 📁  Projects          │  │ </>  Editor          │          │
│ │ Registered projects   │  │ Files and documents  │          │
│ └──────────────────────┘  └──────────────────────┘          │
│                                                            │
│ ┌──────────────────────┐  ┌──────────────────────┐          │
│ │ >_  Terminal          │  │ ◉   Browser          │          │
│ │ Runtime sessions      │  │ Preview and browsing │          │
│ │                       │  │ coming soon          │          │
│ └──────────────────────┘  └──────────────────────┘          │
│                                                            │
│ ┌──────────────────────┐  ┌──────────────────────┐          │
│ │ Git                  │  │ Extensions           │          │
│ │ Repository operations │  │ Provider extensions  │          │
│ │ coming soon           │  │ coming soon          │          │
│ └──────────────────────┘  └──────────────────────┘          │
│                                                            │
│ ┌────────────────────────────────────────────────────────┐   │
│ │ ⚙  Settings                                            │   │
│ │    Application preferences                              │   │
│ └────────────────────────────────────────────────────────┘   │
│                                                            │
│ [ Exit Android IDE ]                                       │
└────────────────────────────────────────────────────────────┘
```

- Projects, Editor, Terminal, and Settings are enabled.
- Browser, Git, and Extensions are visibly muted and communicate their future status.
- The exit action is a full-width destructive-colored button at the bottom.

## 4. Projects screen

### Default populated state

```text
┌────────────────────────────────────────────────────────────┐
│ [☰]  Projects                         [↻] [sort]            │
├────────────────────────────────────────────────────────────┤
│ [ Search projects                              ] [filter]   │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ ┌────────────────────────────────────────────────────────┐   │
│ │ Project Alpha                              [⋮]          │   │
│ │ A small project description                             │   │
│ │ Git   24 files  • 1.4 MB                    2h ago      │   │
│ └────────────────────────────────────────────────────────┘   │
│                                                            │
│ ┌────────────────────────────────────────────────────────┐   │
│ │ Project Beta                               [⋮]          │   │
│ │ 8 files  • 240 KB  • Attention needed                   │   │
│ │ Storage access needs attention                           │   │
│ └────────────────────────────────────────────────────────┘   │
│                                                            │
│                                             [ + ]            │
└────────────────────────────────────────────────────────────┘
```

### Toolbar controls

- Menu: opens the global drawer.
- Refresh: re-inspects registered project records.
- Sort: Name, Last opened, Registered, File count, Size.
- Search: searches registered project names only.
- Clear search: appears inside the search field when text exists.
- Filter: All, Git, Available, Attention.
- Floating action button: expands project-acquisition actions.

### Add-project action menu

```text
                 ┌─────────────────────────┐
                 │ Create blank project    │
                 │ Import folder           │
                 │ Import ZIP              │
                 └─────────────────────────┘
                                      [ + ]
```

### Empty states

```text
No registered projects

Create or import a project to get started.
```

When a search has no results:

```text
No matching projects

Try a different project name or clear the search.
```

### Project card context menu

```text
Project details
Refresh
Rename
Change Location
Copy & Duplicate
Export or Share
Copy Storage Path
Copy Remote URLs
Open in Editor
Open Git                 (future domain)
Open Terminal
Open Browser or Preview  (future domain)
────────────────────────
Remove from Registry
Permanently Delete
```

### Multi-selection state

```text
┌────────────────────────────────────────────────────────────┐
│ 3 selected                                                 │
│ [Remove from Registry] [Permanently Delete]                │
│ [Export or Share] [Copy Storage Path] [Cancel]              │
└────────────────────────────────────────────────────────────┘
```

## 5. Project acquisition workflows

All acquisition workflows use blocking dialogs. User inputs, inspection output, verdicts, progress, and final results are separate visual/structural regions.

### Create blank project — input step

```text
┌──────────────────────────────────────────┐
│ Create blank project                     │
├──────────────────────────────────────────┤
│ [ Project name                         ] │
│   Use one folder name                    │
│                                          │
│ [ Description (optional)               ] │
│                                          │
│ Storage location                         │
│ [ Not selected                         ] │
│ [ Choose storage location ]              │
│                                          │
│ ● Enter a project name to continue       │  ← contextual feedback
├──────────────────────────────────────────┤
│                         [Cancel] [Review] │
└──────────────────────────────────────────┘
```

### Create blank project — review step

```text
┌──────────────────────────────────────────┐
│ Review project                           │
├──────────────────────────────────────────┤
│ Project name: Project Alpha              │
│ Description: Optional description        │
│ Target storage location:                 │
│   /storage/projects                      │
│                                          │
│ ● Ready to create after the destination  │
│   conflict check                         │
├──────────────────────────────────────────┤
│                 [Back] [Create project]  │
└──────────────────────────────────────────┘
```

During execution:

```text
● Creating the project…   [spinner]
[Create project] disabled
[Back] disabled
[Cancel/dismiss] disabled
```

A conflict keeps the review surface open and replaces the ready message with one decision such as:

```text
This folder already exists in this directory.
Choose a different location or project name.
```

### Existing-folder import

```text
┌──────────────────────────────────────────┐
│ Review folder import                     │
├──────────────────────────────────────────┤
│ Selected folder: /storage/projects/App   │
│                                          │
│ [ Project name                         ] │
│ [ Description (optional)               ] │
│                                          │
│ ● Checking whether this folder can be    │
│   registered…                            │
├──────────────────────────────────────────┤
│                         [Cancel] [Import] │
└──────────────────────────────────────────┘
```

Possible verdicts:

- `Couldn't verify the target location`
- `This folder is already registered`
- `This folder overlaps another project`
- `Enter a valid project name to continue`
- `Ready to register this folder in place`

### ZIP import

```text
┌──────────────────────────────────────────┐
│ Review ZIP import                        │
├──────────────────────────────────────────┤
│ ZIP archive: /Downloads/app.zip          │
│ [ Project name                         ] │
│ [ Description (optional)               ] │
│ Storage location                         │
│ [ Not selected                         ] │
│ [ Choose storage location ]              │
│                                          │
│ ● Ready to validate the archive and      │
│   destination                            │
├──────────────────────────────────────────┤
│                         [Cancel] [Import] │
└──────────────────────────────────────────┘
```

### Copy & Duplicate / Change Location

```text
┌──────────────────────────────────────────┐
│ Copy & Duplicate project                 │
├──────────────────────────────────────────┤
│                                          │
│ [ Copy of Project Alpha                ] │
│ [ Description                         ] │  ← duplicate only
│                                          │
│ Destination parent                       │
│ [ Not selected                         ] │
│ [ Choose destination parent ]             │
│                                          │
│ The original remains unchanged.          │
│ The destination will be checked for       │
│ conflicts and project containment.       │
├──────────────────────────────────────────┤
│                         [Cancel] [Copy &  │
│                                  Duplicate]
└──────────────────────────────────────────┘
```

For Change Location, the name defaults to the existing project name and the explanatory text describes a move rather than a copy.

### Export project as ZIP

```text
┌──────────────────────────────────────────┐
│ Export project as ZIP                    │
├──────────────────────────────────────────┤
│ Archive name: Project Alpha.zip          │
│ The project is copied without changing   │
│ the source.                              │
│                                          │
│ [progress / result feedback]             │
├──────────────────────────────────────────┤
│              [Cancel] [Choose export     │
│                       location]          │
└──────────────────────────────────────────┘
```

## 6. Project Details

```text
┌────────────────────────────────────────────────────────────┐
│ [☰]  Project Alpha                                  [⋮]    │
├────────────────────────────────────────────────────────────┤
│ Project description                                        │
│                                                            │
│ Files                       24                             │
│ Folders                     6                              │
│ Size                        1.4 MB                         │
│ Provider                    Android storage                │
│ Project storage             Available                      │
│ Read / update               Available                      │
│ Create                      Available                      │
│ Rename                      Available                      │
│ Delete                      Available                      │
│ Change observation          Available                      │
│ Git branch                  main                           │
└────────────────────────────────────────────────────────────┘
```

### Project Details menu

```text
Refresh
Rename
Change Location
Copy & Duplicate
Export or Share
Copy Storage Path
Copy Remote URLs
Open in Editor
Open Git
Open Terminal
Open Browser or Preview
────────────────────────
Remove from Registry
Permanently Delete
```

### Destructive confirmation layouts

Remove from Registry:

```text
Remove project from registry?

Project Alpha will be removed from Android IDE, but its files,
Git data, and location will remain unchanged.


[Cancel]                         [Remove from Registry]
```

Permanently Delete:

```text
Permanently delete Project Alpha?

This permanently removes the project data from its selected
storage location. This action cannot be undone.

Project: Project Alpha
Type 427 to confirm
[ Confirmation code                         ]

[Cancel]                         [Delete permanently]
```

The destructive action remains disabled until the confirmation code is exact.

## 7. Editor

The Editor is available only with a project context.

### No-project state

```text
┌────────────────────────────────────────────────────────────┐
│ [☰]  Editor                                                │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ No project is open                                         │
│ Choose a project before opening files in the editor.        │
│                                                            │
│ [Open Projects]                                            │
└────────────────────────────────────────────────────────────┘
```

### Project editor layout

```text
┌────────────────────────────────────────────────────────────┐
│ [☰]  Project Alpha     [tabs…]                [save] [⋮]   │
├───────────────────────┬────────────────────────────────────┤
│ FILES                 │ index.html                         │
│ [search][locate]     │ styles.css                        │
│ [+file][+folder][⋮]  ├────────────────────────────────────┤
│                       │                                    │
│ ▼ Project Alpha       │  1  <!doctype html>                 │
│   📄 index.html       │  2  <html>                         │
│   🎨 styles.css       │  3    <head>                       │
│   JS app.js           │  4      <title>App</title>           │
│   TS types.ts         │  5    </head>                      │
│   ▼ src               │                                    │
│     main.kt           │                                    │
│                       │                                    │
│                       ├────────────────────────────────────┤
│                       │ line 4, column 12 • HTML • UTF-8   │
└───────────────────────┴────────────────────────────────────┘
```

On narrow screens, the file tree is in the contextual drawer and the editor receives the full content width after a file is selected.

### Editor top actions

- Open global navigation.
- File tabs and dirty-state indicators.
- Save.
- Additional editor actions.
- Settings entry where available.
- Run/Preview is intentionally not wired to a local WebView preview; browser preview belongs to the later Browser domain.

### Editor contextual file-tree header

```text
Files   [Find files] [Locate current file] [New file]
        [New folder] [More]
```

### Filename search

```text
┌──────────────────────────────┐
│ [ Search files              ] │
├──────────────────────────────┤
│ index.html       HTML        │
│ app.js           JavaScript  │
│ main.kt          Kotlin      │
└──────────────────────────────┘
```

The search toggle returns to the normal file tree. Content search provides match-line metadata and a replace-all route.

### File operation dialogs

```text
Rename index.html
[ New name or project-relative path       ]
[inline validation or result feedback]
[Cancel]                              [Rename]
```

```text
Duplicate app.js
[ Copy of app.js                         ]
[inline validation or result feedback]
[Cancel]                              [Duplicate]
```

```text
Delete folder src?
This removes only the selected folder and its contents.
It does not delete the parent project.
[Cancel]                              [Delete]
```

While a mutation is active, a blocking overlay appears:

```text
┌──────────────────────────────────────────────┐
│              [spinner] Working…              │
└──────────────────────────────────────────────┘
```

## 8. Terminal

The Terminal surface owns its own header and does not require a project location to start a session.

```text
┌────────────────────────────────────────────────────────────┐
│ [☰]  Terminal                         [↻] [close all] [⋮]  │
│      Runtime available                                     │
├────────────────────────────────────────────────────────────┤
│ [Untitled session] [edit] [close]                         │
│ [Build session]    [edit] [close]                         │
│ [ Session name                         ] [ + New ]          │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ $ pwd                                                      │
│ /data/user/0/dev.android.ide/files/termux/home             │
│ $                                                        │
│                                                            │
├────────────────────────────────────────────────────────────┤
│ Tap the terminal to type. Long-press for selection/copy.  │
│                                                     [Stop] │
└────────────────────────────────────────────────────────────┘
```

### Terminal first-open behavior

- If no project is selected, a new session uses the runtime home workspace.
- The initial label is `Untitled session`.
- The session ID is internal and is not presented as the session name.
- A notification permission request is made contextually when the runtime becomes available.

### No-session state

```text
┌──────────────────────────────────────────┐
│ No terminal session                      │
│ A session will open in the terminal home │
│ workspace. You can rename it later.     │
│                                          │
│ [ + Create session ]                     │
└──────────────────────────────────────────┘
```

### Rename session dialog

```text
┌──────────────────────────────────────────┐
│ Rename session                           │
├──────────────────────────────────────────┤
│ [ Session name                         ] │
├──────────────────────────────────────────┤
│                         [Cancel] [Rename] │
└──────────────────────────────────────────┘
```

### Terminal tools menu and package dialog

```text
[⋮]
┌─────────────────────────┐
│ Manage runtime packages │
└─────────────────────────┘
```

```text
┌──────────────────────────────────────────┐
│ Runtime packages                         │
├──────────────────────────────────────────┤
│ No additional packages are installed.    │
│                                          │
│ [ Package names                       ]  │
│   git curl python                        │
│                                          │
│ [inline install result]                  │
├──────────────────────────────────────────┤
│                           [Close] [Install]│
└──────────────────────────────────────────┘
```

During package installation, the operation overlay blocks the terminal surface:

```text
┌──────────────────────────────────────────┐
│ [spinner] Installing selected packages… │
└──────────────────────────────────────────┘
```

## 9. Settings

Settings uses a two-level layout: category list first, then a focused category screen.

### Settings category list

```text
┌────────────────────────────────────────────────────────────┐
│ [☰]  Settings                                              │
├────────────────────────────────────────────────────────────┤
│ Choose a settings category                                 │
│                                                            │
│ 🎨  General                                                │
│     App theme and interface-wide preferences               │
│                                                            │
│ </> Editor                                                 │
│     Editor appearance, code behavior, keyboard, file tree  │
│                                                            │
│ 📁  Projects                                               │
│     Project creation and storage preferences               │
│                                                            │
│ Git                                                        │
│ Credentials                                                │
│ Security                                                   │
│ >_  Terminal                                                │
│ Browser                                                    │
│ Extensions                                                 │
└────────────────────────────────────────────────────────────┘
```

Selecting a category changes the header to:

```text
[←]  Editor
```

The back icon returns to the category list. From the category list it opens the global drawer.

### General

```text
┌──────────────────────────────────────────┐
│ App theme                                │
│ ( ) Dark                                 │
│ ( ) Light                                │
│ ( ) System                               │
├──────────────────────────────────────────┤
│ Interface text size                     │
│ Affects menus, lists, dialogs, and UI.  │
│ 100%                                     │
│ [──────────────●──────────────]          │
│ [Reset]                                  │
└──────────────────────────────────────────┘
```

### Editor

Editor settings are separated into focused groups:

```text
┌──────────────────────────────────────────┐
│ Editor appearance                        │
│ ( ) Dark                                 │
│ ( ) Light                                │
│ ( ) Follow app theme                     │
│ Code font size                  [−] 14 [＋]│
│ Document information row            [on] │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│ Editing behavior                         │
│ Tab size             [2] [4] [8]          │
│ Word wrap                              [ ]│
│ Line numbers                            [✓]│
│ Whitespace        [None] [Selection] [All]│
│ Minimap                               [✓]│
│ Scroll past last line                   [ ]│
│ Bracket pair colors                     [✓]│
│ Cursor style       [Line] [Block] [Underline]
│ Auto-close brackets [Always] [Smart] [Never]
│ Auto save                             [ ]│
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│ Keyboard and input                       │
│ Keyboard toolbar                        [✓]│
│ Symbol bar                              [✓]│
│ Volume keys:                             │
│ ( ) Cursor horizontal                    │
│ ( ) Cursor vertical                      │
│ ( ) Disabled                             │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│ File tree                                │
│ Hide .git folder                        [✓]│
│ Hide workspace metadata                 [✓]│
└──────────────────────────────────────────┘
```

### Projects

```text
┌──────────────────────────────────────────┐
│ Storage locations are chosen during      │
│ project creation.                        │
│                                          │
│ Android IDE does not use a hidden default │
│ project path.                            │
└──────────────────────────────────────────┘
```

### Security

```text
┌──────────────────────────────────────────┐
│ Project folder access                    │
│ Android IDE uses Android’s folder access │
│ permission for project files.            │
│                                          │
│ 2 saved folder permission(s)             │
│ [Grant folder access]                    │
│ Folder access saved                      │
├──────────────────────────────────────────┤
│ Android app permissions                  │
│ Notifications and other Android-managed  │
│ permissions are controlled by Android.  │
│                                          │
│ [Open Android app settings]              │
└──────────────────────────────────────────┘
```

### Git, Credentials, Terminal, Browser, Extensions

These categories currently render a plain domain message instead of non-functional controls:

```text
Git settings are not available yet. This domain will be added in its own workflow.
```

The same structure is used for Credentials, Terminal, Browser, and Extensions settings where controls are not yet implemented. The Terminal runtime itself is implemented on the Terminal surface; this settings category remains reserved for future terminal preferences.

## 10. Shared operation states

### Loading / blocking

```text
┌──────────────────────────────────────────┐
│                                          │
│                 [spinner]                │
│                 Working…                 │
│                                          │
└──────────────────────────────────────────┘
```

Controls that could mutate the same workflow are disabled or muted while the operation runs.

### Success

```text
✓ Project registered
✓ Terminal session renamed
✓ Folder access saved
```

### Blocked / validation

```text
! Enter a valid project name to continue
! Choose a storage location to continue
! This folder is already registered
```

### Failure / partial completion

```text
! The destination could not be inspected
! The project was renamed but the new registry entry could not be written
! Project data was deleted, but the storage provider did not allow the result to be rechecked

Recovery: Restore storage access and retry the operation.
```

## 11. Navigation while an operation is active

When navigation is requested during a file or project operation:

```text
┌──────────────────────────────────────────┐
│ Operation in progress                    │
├──────────────────────────────────────────┤
│ An operation is still running. Stay here │
│ to keep its progress visible, or leave   │
│ it running in the background and         │
│ continue.                                │
├──────────────────────────────────────────┤
│ [Keep waiting]                 [Leave running]
└──────────────────────────────────────────┘
```

For editor-specific file mutations, the title is `File operation in progress`, with the same keep-waiting/background choice.

## 12. Future-domain placeholders

Unsupported domains use a deliberately plain structure:

```text
┌──────────────────────────────────────────┐
│ [☰]  Browser                             │
├──────────────────────────────────────────┤
│ Browser not available.                   │
│ Coming soon (phase 5).                   │
└──────────────────────────────────────────┘
```

They do not expose fake search bars, cards, settings controls, or action buttons that imply functionality that is not wired yet.

## 13. Current implementation boundaries

- Browser preview/run is not wired to a local WebView preview from the Editor.
- Git operations remain a future domain.
- Browser and Extensions remain future domains.
- Terminal runtime, PTY-backed terminal view, session persistence, package controls, notification permission request, default runtime-home launch, and session renaming are implemented.
- Android project folder permissions are managed through the Security settings category and Android’s folder picker.
