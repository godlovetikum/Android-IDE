# Android IDE Phase 0 Implementation Guidance

**Status:** Approved implementation guidance for Phase 0  
**Depends on:** [Approved Product Definition](./ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md), [Approved Provider Research](./ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md), and [Approved Implementation Roadmap](./ANDROID_IDE_IMPLEMENTATION_ROADMAP_APPROVED.md)

## 1. Purpose and boundary

Phase 0 is the contract and architecture-freeze phase. It prepares the project for implementation without expanding product scope and without building domain features. Its purpose is to remove ambiguity from ownership, state, storage, lifecycle, and provider boundaries before Phase 1 changes the application foundation.

Phase 0 is complete when the implementation team can begin Phase 1 without making new product decisions about where data lives, which scope owns a session, how navigation restores, or how the Termux runtime relates to project storage. It is not complete merely because documents exist. The contracts must be internally consistent, specific enough to implement, and small enough to verify.

Phase 0 must not implement the editor, terminal, browser, Git, language intelligence, credentials, or extensions. It may create small type/interface prototypes or fixtures when they clarify a contract, but those prototypes must not become an unreviewed second architecture.

## 2. Authoritative documents

The following three documents are the active baseline for all implementation planning:

1. [Approved Product Definition](./ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md) — defines what Android IDE is, what users can do, and how each capability behaves.
2. [Approved Provider Research](./ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md) — defines the selected provider direction, obtainable versions, integration facts, and technical constraints.
3. [Approved Implementation Roadmap](./ANDROID_IDE_IMPLEMENTATION_ROADMAP_APPROVED.md) — defines phase order, deliverables, dependencies, and acceptance gates.

If an older document conflicts with one of these three, the approved document takes precedence. Historical documents are retained in `docs/archive/` for traceability and are not active implementation guidance.

## 3. Required Phase 0 outputs

Phase 0 produces the following outputs:

- a state-ownership matrix;
- a storage and authority matrix;
- a navigation and restoration contract;
- a lifecycle and session contract;
- provider adapter contracts;
- project-location capability rules;
- an identity and metadata migration decision;
- an error, conflict, and operation-result vocabulary;
- a minimal cross-domain event vocabulary;
- a Phase 1 entry checklist.

These outputs may be recorded in one architecture decision record or several focused records. They must use the terminology in the approved product definition and must not revive superseded names or behaviors.

## 4. State ownership contract

Every durable or observable value must have one declared owner. The UI may display a value owned elsewhere, but it must not create a competing copy that becomes authoritative.

| State category | Owner | Examples | Must not contain |
|---|---|---|---|
| User project files | Selected project location | Source files, generated files, `.git`, package manifests | Global preferences, live PTYs, browser profiles |
| Portable project metadata | Project metadata directory | Project identity, portable editor descriptors, recovery descriptors where approved | Secrets, live process handles, caches, browser authentication |
| Project-associated runtime state | App-private runtime storage | PTYs, logs, process handles, runtime caches, browser runtime data associated with a project | Authoritative source files, global credentials |
| Global application data | App-private global storage | Registry, recent-project order, global preferences, encrypted credentials, application services | Project file contents, project-specific tabs |
| Surface state | Owning domain/application state | Current surface, selected tab/session, transient controls | Independent copies of project identity or process state |

The project metadata directory is portable project state, not a general-purpose app database. A terminal session descriptor may be portable only when the product explicitly defines it as a descriptor; the PTY, process, log, and runtime handle remain app-private. Credentials never belong in project metadata.

The first implementation should define read and write ownership for each state value. A screen that needs a value receives it through a state contract or query interface. It does not write directly into another domain’s storage.

## 5. Project-location and filesystem contract

Android IDE supports two explicit project-location classes.

### 5.1 User-visible local location

A user-visible local location keeps project files accessible outside Android IDE. It may be device storage, removable storage, or another local provider that satisfies the required operations.

The location is eligible only when the editor and integrated runtime can operate on the same location for reading, writing, creating, renaming, deleting, moving, executing supported files, and observing relevant changes. A provider that can be selected but cannot provide the required development behavior is rejected with an actionable explanation.

### 5.2 Private development workspace

The private development workspace is an explicitly selected project location inside Android IDE’s Termux-based runtime filesystem. It is intended for projects requiring stronger Unix/POSIX behavior, package installation, executable scripts, native binaries, symlinks, language servers, file watching, or local development servers.

It is not a hidden temporary copy. When selected, it is the authoritative project location. The location must appear in project details, project size calculations, and relocation workflows. Android IDE must provide explicit export, sharing, backup, copy, move, and relocation actions because unrelated applications cannot ordinarily browse the private directory by path.

### 5.3 Common authority rule

Monaco, the file tree, the Termux runtime, Git, language servers, and local servers must operate on the selected authoritative project location. The editor’s in-memory model is not a second source of truth. A runtime cache is not a project copy.

Cloud-backed locations are not live editable project locations. They must be downloaded or imported into a selected local location before registration as an Android IDE project. Automatic two-way synchronization is excluded from the current product.

### 5.4 Phase 0 contract work

Define a `ProjectLocation` identity that can represent both location classes without pretending every location is a normal path. It should retain the location kind, stable identifier, display label, capability state, and user-visible location details. Do not expose provider-specific URI details throughout the application.

Define a capability result that distinguishes supported, unsupported, unavailable, permission-lost, and not-yet-checked states. Capability checks must be explicit and must not silently switch location class.

Define copy, move, export, import, and relocation semantics as operations over locations. Every operation must have preflight, execution, verification, partial-failure, interruption, and cleanup outcomes.

## 6. Navigation and surface contract

The app opens at Home. Home is an entry point for navigation and project/workspace access; it is not a data dashboard that silently changes the meaning of active sessions.

Navigation must distinguish top-level application surfaces from child state. The initial surfaces are Home, Projects, Project Details, Editor, Terminal, Browser, Git, and Settings or other approved domain entries. The contextual sidebar changes according to the current or retained context. It is an integrated mobile surface, not a desktop-mode panel and not an overlay that disappears merely because the user taps another area.

A surface may be hidden without being closed. Hiding changes visibility and focus. It does not destroy durable state or terminate owned work. Closing is an explicit domain action that releases the relevant child resource. Leaving a project is distinct from closing a terminal session or browser tab.

Define the back-stack rules in this order:

1. dismiss the highest-priority transient control;
2. close or hide a child control according to its domain contract;
3. return from details to the previous domain surface;
4. leave the selected project only after dirty work and active tasks are handled;
5. return to Home or Projects without destroying global sessions.

No domain may intercept Back merely to navigate to a preferred internal screen.

## 7. Restoration contract

Restoration is staged and must tolerate partial failure. The app must not treat process death as a clean exit.

The restoration sequence is:

1. initialize global settings and the global application envelope;
2. validate the last project identity and storage permission;
3. open or mark the project unavailable;
4. initialize or repair required portable metadata;
5. load project workspace descriptors;
6. validate editor documents, terminal descriptors, browser descriptors, and task descriptors independently;
7. restore accessible children;
8. mark inaccessible children unavailable with actionable status;
9. restore the selected surface and child selection where possible;
10. fall back to Home when the previous surface cannot be restored.

A stale file URI must not prevent a project from opening. An unavailable terminal session must not erase editor tabs. A failed browser restoration must not delete project metadata. Restoration must never silently replace a missing project with a different project.

## 8. Lifecycle and session contract

The application must use Android-supported lifecycle mechanisms for durable user-visible work. The goal is survivability across app switching, navigation, and ordinary extended idleness where Android permits it. The product must not claim an unkillable process.

Long-running terminal and server work must be owned by a runtime/service boundary rather than by a composable screen or screen-scoped coroutine. The UI observes session state; it does not own the lifetime of the process.

Each session or task needs a stable identity, owner scope, backend identity where available, working directory, creation time, availability state, and termination reason. Runtime process identifiers and PTYs belong to app-private runtime state. Durable records belong to the appropriate global or project-associated state layer.

The contract must distinguish:

- **Available:** the backend can reconnect or report the session as usable.
- **Unavailable:** the durable record exists, but the backend cannot currently reconnect or use it.
- **Explicitly closed:** the user intentionally terminated it.
- **Invalidated:** an unavoidable Android, device, permission, or backend event ended it.

Switching domains, returning Home, hiding Terminal, or changing sessions does not close a session. Explicit Close Session or Close All Sessions terminates the backend session and its owned child processes.

## 9. Adapter contracts

### 9.1 Project storage adapter

The adapter exposes project-relative operations without leaking SAF or private-runtime details into the editor, Git, or terminal UI. It must support preflight checks, read/write streams, child listing, mutation, metadata, capability inspection, and change observation where supported.

### 9.2 Runtime workspace adapter

The adapter creates and validates the private Termux development workspace, reports its root identity, and exposes project working directories to the runtime. It must not be used to silently materialize a user-visible project.

### 9.3 Terminal runtime adapter

The adapter creates sessions, assigns an initial working directory, sends input, receives output, resizes PTYs, reports capabilities, tracks child processes, terminates sessions, and reports availability. The UI must not depend on Termux-specific classes.

### 9.4 Editor document adapter

The adapter maps authoritative project files to stable project-relative Monaco model URIs, loads content, writes saves, observes external changes, and separates unsaved recovery content from saved project files.

### 9.5 Git adapter

The Git adapter uses the canonical Git executable in the selected runtime as the repository source of truth. It exposes status, mutations, output, errors, and configuration without silently introducing a second Git engine for the same workflow.

### 9.6 Lifecycle coordinator

The coordinator connects application state, foreground work, runtime availability, restoration records, and explicit Exit behavior. It must report unavoidable termination rather than pretending that work continued.

## 10. Event vocabulary

Phase 0 should define a small event vocabulary shared across domains. Events should describe facts, not UI commands that only one screen understands.

Recommended event families include:

- project registered, opened, unavailable, relocated, exported, removed, or deleted;
- storage permission granted, lost, restored, or rejected;
- project file created, changed, moved, renamed, deleted, or externally changed;
- terminal session created, available, unavailable, explicitly closed, or invalidated;
- child process started, exited, failed, or terminated with its owner;
- browser tab created, restored, unavailable, or closed;
- Git repository changed externally or refreshed;
- lifecycle hidden, foregrounded, process recreated, service stopped, or app exited.

Events must not carry secrets. They should identify entities and result status, while sensitive output remains in the appropriate secure or runtime channel.

## 11. Error and operation-result contract

Every mutation that can affect user data uses the same lifecycle:

1. validate input;
2. inspect the destination and conflicts;
3. present the reviewed operation;
4. execute without silent substitution;
5. verify the returned identity and resulting state;
6. report complete, partial, blocked, interrupted, failed, or cancelled outcome;
7. offer supported recovery or cleanup.

A conflict means the operation stops before creation, replacement, merge, or deletion. The user can change the name or location and retry. Android IDE must not rely on a provider to silently rename a conflicting file or folder.

Define stable error categories for permission loss, unsupported provider capability, destination conflict, invalid archive, unavailable runtime, package failure, process loss, malformed metadata, external file change, credential failure, and user cancellation.

## 12. Identity and metadata decision

Phase 0 must explicitly decide the application identity and metadata migration before feature-domain implementation. The current direction is application identity `dev.android.ide` and project metadata directory `.dev-android-ide`, while older source and documents may use `dev.androidide` and `.androidide`.

The decision must state whether legacy metadata is migrated, read as a compatibility alias, or converted once. It must cover application ID, Kotlin namespace, manifest references, generated metadata, visibility rules, tests, and documentation. No feature phase may silently perform this migration.

## 13. Phase 1 entry checklist

Phase 1 may begin only when:

- the three approved documents are the active references;
- old planning documents are archived and clearly marked as superseded;
- storage-layer ownership is recorded;
- project-location classes and capability rules are recorded;
- navigation, Back, Hide, Close, Leave Project, and Exit semantics are recorded;
- restoration and unavailable-state behavior are recorded;
- adapter boundaries are recorded;
- event and error vocabularies are recorded;
- identity and metadata migration is decided or explicitly isolated as a prerequisite task;
- the implementation team can identify the smallest Phase 1 change set without modifying terminal, editor, browser, or Git behavior prematurely.

## 14. Lightweight verification

Phase 0 verification should be document and contract verification, not a broad Android build. Use targeted searches, consistency checks, small data-model tests where useful, and manual review of the contracts.

At minimum, verify that no active document describes desktop mode, treats cloud storage as a live project location, makes terminal sessions project-owned by default, places credentials in project metadata, or promises an unkillable process. Verify that every approved roadmap phase points back to the approved product definition and provider research.

## 15. Phase 0 completion record

When Phase 0 is complete, record the accepted contract set, any explicitly deferred decisions, known provider limitations, and the exact Phase 1 scope. The completion record must not claim that a runtime, editor, browser, or Git feature is implemented. It should state only that the implementation boundaries are ready for the foundation phase.

## References

[1]: ./ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md "Approved Android IDE Product Definition"
[2]: ./ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md "Approved Android IDE Provider and Dependency Research"
[3]: ./ANDROID_IDE_IMPLEMENTATION_ROADMAP_APPROVED.md "Approved Android IDE Implementation Roadmap"
[4]: https://developer.android.com/guide/components/activities/process-lifecycle "Android Application Process Lifecycle"
[5]: https://developer.android.com/develop/background-work/services/foreground-services "Android Foreground Services"
[6]: https://github.com/termux/termux-app/wiki/Termux-Libraries "Termux Libraries"
[7]: https://github.com/termux/termux-packages/wiki/Termux-file-system-layout "Termux Filesystem Layout"

Manus AI  
September 2026
