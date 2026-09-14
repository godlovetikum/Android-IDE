# Project Metadata and Workspace Storage

This document defines what Android IDE stores inside a project and what remains private to the application. It is written for project owners and users who need to understand, back up, inspect, or remove the IDE’s records without guessing from filenames.

## Storage boundary

Android IDE uses two storage boundaries:

1. **Project-owned storage.** Files placed inside the user-selected project directory are visible to other tools, can be included in backups, and travel with the project when the directory is copied.
2. **Application-private storage.** Records stored in Android `SharedPreferences` belong to the Android IDE installation. They are not project files and are not intended to be edited with the project editor.

The `.git` directory is owned by Git. Android IDE reads selected Git metadata for project details but does not use `.git` as an Android IDE metadata store.

## `.androidide/project.json`

This file is created inside every project’s `.androidide` directory. Android IDE initializes the directory when a project is created, imported, or opened for the first time. The current schema contains the following fields:

| Field | Type | Current value or meaning |
|---|---|---|
| `schemaVersion` | Number | `1`. Identifies the JSON layout so future migrations can distinguish old files from newer layouts. |
| `projectName` | String | The trimmed name entered when the project was created. This is the initial project identity recorded in the project folder; registry display-name edits are currently stored separately in app-private registry data. |
| `createdBy` | String | `Android IDE`. Identifies the tool that initialized the metadata directory. |
| `createdAt` | Number | Unix epoch time in milliseconds recorded when the project metadata is initialized. |
| `purpose` | String | `Project-local workspace and recovery metadata`. Describes the role of the directory to a human or migration tool. |

The file does not contain source code, Git credentials, access tokens, or the complete project tree. The `.androidide` directory is deliberately **not** added to the default `.gitignore`: it is project-owned Android IDE metadata and should remain available when the project is copied or transferred. Users may choose a different Git policy explicitly.

## `.androidide/workspace.json`

This file is written when the current project session is saved during project switching or lifecycle handling. The current schema contains:

| Field | Type | Current value or meaning |
|---|---|---|
| `schemaVersion` | Number | `1`. Identifies the workspace record format. |
| `activeTabUri` | String or null | The provider URI of the active non-blank document tab. It is `null` when no non-blank tab is active. |
| `openTabUris` | Array of strings | Provider URIs for open non-blank document tabs. Blank unsaved tabs are intentionally excluded. |
| `updatedAt` | Number | Unix epoch time in milliseconds for the last write of this workspace file. |

The current project-local workspace file is intentionally small. Cursor and scroll positions are also maintained in the app-private session repository at present; they are not yet serialized into `workspace.json`.

## Default files in a newly created project

Android IDE creates the following project files in addition to `.androidide`.

### `README.md`

The default README is not a placeholder sentence. Its initial contents are:

```markdown
# Project name

> A project created with Android IDE.

## Overview

Describe what this project does, who it is for, and the problem it solves.

## Getting started

1. Install the project dependencies described by `package.json`.
2. Update the scripts in `package.json` for the tools used by this project.
3. Start the project using the appropriate development command.

## Project structure

- `README.md` — project documentation and setup instructions.
- `package.json` — project name, metadata, and development scripts.
- `.gitignore` — generated files and local-only artifacts excluded from Git.
- `.androidide/` — Android IDE project metadata; it is managed by Android IDE.

## Development notes

Record commands, environment requirements, deployment steps, and known limitations here.

## License

Add the project license and attribution information here.
```

The `Project name` heading is replaced with the trimmed project name entered by the user. The rest of the document is intentionally editable guidance, not generated project-specific claims.

### `package.json`

New projects receive a minimal JavaScript-oriented manifest containing:

```json
{
  "name": "normalized-project-name",
  "version": "1.0.0",
  "description": "",
  "main": "index.js",
  "scripts": { "start": "node index.js" },
  "keywords": [],
  "author": "",
  "license": "ISC"
}
```

The package name is lower-cased and non-alphanumeric runs are converted to hyphens. Android IDE does not install dependencies or claim that the `start` script is executable until a future terminal/runtime feature is available.

### `.gitignore`

The default ignore file contains only common generated or machine-local artifacts:

```gitignore
node_modules/
dist/
build/
.DS_Store
```

It does **not** ignore `.androidide/`. The metadata directory is project-owned and must remain available for project transfer and workspace continuity unless the user deliberately changes the project’s Git policy.

## App-private project registry

The `project_registry` preference contains a JSON array of at most 20 recently opened projects. Each entry contains:

| Field | Type | Meaning |
|---|---|---|
| `name` | String | The display name shown in the project list. This can differ from the physical folder name. |
| `uri` | String | The persisted Android SAF or file URI for the project root. |
| `lastOpenedMs` | Number | Unix epoch time in milliseconds used for recency ordering. |
| `createdMs` | Number | The registry’s creation timestamp for the project entry. |

Registry data is not a project file. Moving or deleting the application removes this record unless the application’s Android backup or device migration system preserves it.

## App-private workspace session records

The `ide_sessions` preference stores per-project session information under a hash derived from the project URI. The stored values are:

| Record | Contents |
|---|---|
| Open tabs | Newline-separated document URIs for non-blank tabs. |
| Active tab | The active document URI, when one exists. |
| Cursor positions | Document URI plus line and column values. |
| Scroll positions | Document URI plus the saved vertical scroll value. |

The app also stores the current global screen and session-level values in the general session record. These records are operational state for the installed app, not portable project content.

## Crash-recovery records

The `crash_recovery` preference stores:

| Record | Contents |
|---|---|
| `clean_exit` | Whether the previous IDE session was marked as closed cleanly. |
| Unsaved entries | Project URI, tab identifier, document URI, display name, and unsaved editor content saved for recovery. |

Unsaved recovery content can contain private source code. It must be treated as sensitive application data and must not be included in diagnostics, logs, or public exports without explicit user action.

## Editor settings

Global editor settings remain app-private. They include Monaco preferences such as font size, tab size, wrapping, line numbers, theme, minimap, cursor style, bracket-pair coloring, and auto-closing brackets. They also include UI preferences such as keyboard toolbar visibility, symbol-bar visibility, preview layout, file-tree visibility for `.git` and `.androidide`, and the application font-scale multiplier.

The application font-scale multiplier is applied through Compose density so that labels, dialogs, menus, forms, project details, status messages, loading feedback, and other Compose text use the same user-selected scale. Monaco’s code font size remains a separate editor preference because code typography and application chrome have different usability requirements.

## Removal semantics

### Remove from registry

Removing a project from the registry removes the project entry, its app-private session records, its crash-recovery records, and the project’s `.androidide` directory. It does not delete the project directory, source files, README, package files, or `.git`. Unsaved buffers are discarded as part of removing the project from the IDE.

The metadata directory is initialized for an existing project when it is imported or opened and does not overwrite an existing manifest. This allows projects created outside Android IDE to receive the same project-local workspace contract without changing their source files.

### Permanently delete

Permanent deletion is a separate destructive operation. Android IDE displays a generated numeric code and requires the user to type it back. After validation, the application requests deletion of the complete project root from the selected storage provider and verifies that the root no longer exists before removing the registry records.

## Forward-compatibility rules

Future metadata fields must be added with a schema-version decision, preserve unknown fields when practical, and tolerate a missing or malformed `.androidide` directory. A project must remain openable when metadata is absent. Metadata failure must not be used as a reason to hide or delete user source files.
