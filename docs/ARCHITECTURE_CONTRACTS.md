# Android IDE Application Architecture Contracts

**Status:** Accepted architecture contract set for the `dev` branch
**Primary responsibility:** Define the ownership and provider boundaries that application services must preserve.
**Scope:** Contracts and decisions only; this document does not claim that terminal, browser, Git, language intelligence, credentials, or extensions are implemented.

## 1. Goal and active references

The goal of this contract set is to remove ownership, storage, navigation, lifecycle, provider, and identity ambiguity before expanding feature code. The active references are the approved product definition, approved provider research, approved implementation roadmap, and implementation guidance. Documents under `docs/archive/` are historical and do not override these references.

## 2. State ownership matrix

| State | Authoritative owner | Readers | Explicit exclusions |
|---|---|---|---|
| User project files, generated files, `.git`, manifests | Selected `ProjectLocation` | Project storage adapter, editor, terminal, Git, language servers, previews | No app-private shadow project copy |
| Portable project metadata | Project metadata directory at the project root | Project registry, workspace restoration, project details | No secrets, PTYs, process handles, browser authentication, or caches |
| Project-associated runtime state | App-private runtime storage | Runtime and lifecycle services | No authoritative project files or global credentials |
| Global application data and preferences | App-private global storage | Application shell and domain services | No project file contents or project-owned tabs |
| Surface and transient UI state | Owning domain/application state | Compose surfaces | Not an independent source of project, session, or repository truth |
| Credentials and secure material | Keystore-backed encrypted vault | Credential and provider adapters | Never project metadata, source, logs, URLs, or events |

A screen may query another owner through an adapter or state contract, but it must not write directly into another owner’s storage or establish a competing authority.

## 3. Project-location and storage authority

Android IDE has exactly two supported project-location classes:

1. **User-visible local location:** a device, removable, or other local provider location that passes capability checks for the required editor and runtime operations on the same files.
2. **Private development workspace:** an explicitly selected location inside the integrated Termux-based runtime. It is authoritative when selected; it is not a hidden duplicate or cache.

Cloud-backed or remote document providers are import/export sources, not live editable project locations. A location that can be selected but cannot support the required read, write, mutation, execution, or change-observation behavior is rejected with an actionable result. The application must never silently change location class, overwrite, merge, rename, or redirect a requested destination.

Every acquisition or relocation operation follows: validate input; inspect destination and capabilities; review the exact operation; execute; verify returned identity and state; report complete, partial, blocked, interrupted, failed, or cancelled outcome; offer supported recovery or cleanup.

## 4. Navigation and restoration contract

The application opens at **Home**. Home is an entry point, not a dashboard and not an owner of project, terminal, browser, or Git state. Initial surfaces are Home, Projects, Project Details, Editor, Terminal, Browser, Git, and Settings.

The contextual sidebar is an integrated mobile surface. Hiding a surface changes visibility and focus but does not close its children or terminate owned work. Leaving a project is distinct from closing a terminal session, browser tab, editor tab, or other child resource.

Back handling is ordered as follows:

1. dismiss the highest-priority transient control;
2. close or hide a child control according to its domain contract;
3. return from details to the previous domain surface;
4. leave the selected project only after dirty work and active tasks are handled;
5. return to Home or Projects without destroying global sessions.

Restoration is staged and tolerant of partial failure: initialize global state; validate the last project and permission; open or mark it unavailable; initialize portable metadata; load workspace descriptors; validate editor, terminal, browser, and task descriptors independently; restore accessible children; mark inaccessible children unavailable; restore surface and selection where possible; otherwise fall back to Home. Missing or stale child state must not delete unrelated state or substitute a different project.

## 5. Lifecycle and session contract

Long-running work belongs to a runtime/service boundary, never to a composable screen or screen-scoped coroutine. The UI observes state. Android IDE reports the limits of process survivability and never promises an unkillable process.

Every session or task has a stable identity, owner scope, backend identity where available, working directory, creation time, availability state, and termination reason. PTYs, process identifiers, and logs are app-private runtime state; durable descriptors are stored in the appropriate global or project-associated layer.

Availability states are:

- **Available:** the backend can reconnect or report the session as usable.
- **Unavailable:** the durable record exists but the backend cannot currently reconnect or use it.
- **Explicitly closed:** the user intentionally terminated it.
- **Invalidated:** an unavoidable Android, device, permission, or backend event ended it.

Changing screens, returning Home, hiding Terminal, or switching sessions does not close a session. Explicit close terminates the session and its owned child processes.

## 6. Provider adapter boundaries

Higher-level domains depend on these logical contracts, not on SAF, Termux, WebView, Monaco, Git implementation classes, or provider-specific process details:

| Adapter | Responsibility |
|---|---|
| `ProjectStorageAdapter` | Project-relative listing, reads, writes, mutations, metadata, capability checks, and change observation |
| `ProjectRegistryAdapter` | Registered project records, recent ordering, availability, and removal without deleting user files |
| `ProjectMetadataAdapter` | Portable identity and workspace descriptors, with explicit migration and no secret storage |
| `RuntimeWorkspaceAdapter` | Private workspace initialization, root identity, and project working directories |
| `TerminalRuntimeAdapter` | Sessions, PTY I/O, resize, child tracking, termination, and availability |
| `EditorDocumentAdapter` | Stable project-relative document identity, load/save, external-change reporting, and recovery separation |
| `GitAdapter` | Canonical runtime Git status, mutations, configuration, output, and errors |
| `BrowserPreviewAdapter` | Global browser/preview state and failure isolation from project and runtime ownership |
| `CredentialVaultAdapter` | Secure credential lifecycle, redaction, revocation, and provider access without exposing secrets |
| `LifecycleCoordinator` | Restoration, foreground work, runtime availability, invalidation, and explicit exit semantics |

The initial Kotlin contract types live in `dev.android.ide.contracts`. Implementations may be added by later phases without changing the ownership vocabulary.

## 7. Identity and metadata decision

The approved product direction names application identity `dev.android.ide` and metadata directory `.dev-android-ide`; the pre-Phase-0 source used `dev.androidide` and `.androidide`.

For this bootstrap, the decision is explicit:

- `dev.android.ide` and `.dev-android-ide` are the **authoritative identity** for the application architecture.
- Existing `dev.androidide` source/package names and `.androidide` metadata are **legacy inputs**, not new authoritative names.
- The identity migration is implemented at the application boundary. The application ID, Kotlin namespace/package references, manifest references, generated metadata, visibility rules, and documentation use the authoritative identity.
- Metadata initialization always checks `.dev-android-ide` first. If it does not exist, it is created, legacy files are migrated into it, and `.androidide` is deleted only after migration succeeds. Reads and writes after acquisition use only `.dev-android-ide`; the legacy directory is never preserved alongside it.
- Credentials and runtime handles remain excluded from both metadata names.

This decision makes migration a one-way acquisition/opening step and prevents feature work from creating an accidental mixed identity.

## 8. Shared event vocabulary

Events describe facts and identify entities; they are not screen-specific commands and never carry secrets.

| Event family | Examples |
|---|---|
| Project | registered, opened, unavailable, relocated, exported, removed, deleted |
| Storage | permission granted, lost, restored, rejected |
| Project file | created, changed, moved, renamed, deleted, externally changed |
| Runtime/session | session created, available, unavailable, explicitly closed, invalidated |
| Child process | started, exited, failed, terminated with owner |
| Browser | tab created, restored, unavailable, closed |
| Git | repository changed externally, refreshed |
| Lifecycle | hidden, foregrounded, process recreated, service stopped, app exited |

## 9. Error and operation-result vocabulary

All user-data mutations use the same operation lifecycle: validation, conflict/capability preflight, reviewed operation, execution, verification, and an explicit result with recovery or cleanup where supported.

Stable error categories are: permission loss; unsupported provider capability; destination conflict; invalid archive; unavailable runtime; package failure; process loss; malformed metadata; external file change; credential failure; and user cancellation.

Stable operation outcomes are: **complete**, **partial**, **blocked**, **interrupted**, **failed**, and **cancelled**. A conflict stops before creation, replacement, merge, or deletion. Providers must not silently rename or substitute a target.

## 10. Application foundation entry checklist and exact scope

Application foundation may begin. The smallest Phase 1 scope is:

1. establish Home, top-level navigation, contextual sidebar, and Back/Hide/Close/Leave/Exit contracts;
2. establish the four storage layers and project registry boundary;
3. persist storage permission and location identity;
4. provide capability inspection and unavailable-state reporting;
5. add lifecycle coordination and restoration records;
6. verify that no hidden project copy is created.

Application foundation must not prematurely implement terminal runtime, editor feature expansion, browser, Git, language intelligence, credentials, or extensions.

## 11. Acceptance and limitations

Architecture contracts are accepted when the contract record and Kotlin contract types agree, active documents point to the approved references, legacy documents are clearly archived, identity migration is isolated and visible, and targeted checks find no active claim that cloud storage is live-editable, sessions are project-owned by default, credentials belong in project metadata, desktop mode is required, or processes are unkillable.

This contract set does not claim that any provider integration or runtime feature is implemented. Android builds and device validation are separate acceptance activities and are intentionally not run as part of this lightweight contract verification.
