# Android IDE Status Tracker

## Current project state

The product-definition and provider-research discovery gate is complete. The approved documents now define what Android IDE is, the supported feature domains, the storage and runtime relationship, the selected provider direction, and the staged implementation order.

The active canonical documents are:

- `docs/ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md`
- `docs/ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md`
- `docs/ANDROID_IDE_IMPLEMENTATION_ROADMAP_APPROVED.md`
- `docs/ARCHITECTURE_CONTRACTS.md`
- `docs/TERMUX_PROJECT_FILESYSTEM_CONCEPT_REPORT.md`

Superseded planning notes, earlier research reports, and working versions are intentionally absent from this experiment branch. The retained documents above are the only product and architecture references for this source tree.

## Completed discovery gate

The first implementation-planning objective—defining and clarifying the product—has been satisfied. The approved definition covers the six product domains, mobile-first navigation, project acquisition, storage ownership, the SAF-accessible Android IDE filesystem provider, protected Termux runtime behavior, global terminal behavior, browser and preview behavior, Git levels, editor behavior, lifecycle expectations, security boundaries, and the extensions placeholder.

The approved provider research covers obtainable provider choices, versions, licenses, acquisition routes, customization boundaries, Termux runtime initialization, the SAF-accessible Android IDE filesystem provider, Monaco integration, GeckoView, browser tooling, Git, LSP, Keystore, and Android lifecycle constraints.

The approved roadmap establishes the implementation order and acceptance gates. No feature-domain implementation is authorized merely by the existence of these documents.

## Architecture contract status

Phase 0 contract/bootstrap work is complete on the `dev` branch. The accepted contract set records state ownership, storage authority, project-location capability rules, navigation and restoration behavior, lifecycle and session semantics, provider adapter boundaries, shared events, operation outcomes, error categories, and the identity decision. This is not a claim that later runtime, editor, browser, Git, or project-management gates are complete.

The Kotlin contract types are in `android-ide/android/java/dev/android/ide/contracts/ApplicationContracts.kt`. They are provider-neutral and do not claim that any runtime, editor, browser, Git, language-intelligence, credentials, or extension feature is implemented.

The `test` branch experiment uses the upstream `com.termux` application identity and private `files/usr` / `files/home` layout. Runtime installation intentionally has no legacy directory migration or fallback. Any remaining project-metadata compatibility code must be removed before this branch is treated as an exact no-migration experiment.

## Application foundation entry status

The architecture contracts and identity prerequisite are complete. The application-foundation implementation is complete at source level; its behavioral gate remains unverified until the repository workflow runs on Android tooling. On this experiment branch, the identity prerequisite means the upstream `com.termux` application identity and private `files/usr` / `files/home` layout, with no legacy migration or fallback.

Phase 0 must finalize or record the following contracts:

- state ownership across project, portable metadata, runtime, and global layers;
- user-visible versus private-development project locations;
- storage capability validation and no-silent-substitution behavior;
- Home, navigation, contextual sidebar, Back, Hide, Close, Leave Project, and Exit semantics;
- durable restoration and unavailable-state handling;
- terminal, editor, Git, browser, storage, and lifecycle adapter boundaries;
- shared event, error, conflict, and operation-result vocabulary;
- application identity and metadata-directory migration strategy;
- the narrow Phase 1 foundation scope.

## Application foundation implementation status

The application-foundation implementation is complete through `AppShell`, `AppShellViewModel`, `ApplicationStateStore`, `RuntimeStateStore`, `ProjectStateService`, `ProjectProviderAdapters`, `LifecycleStateStore`, and `KeystoreCredentialVault`. The active shell owns Home, top-level navigation, integrated mobile navigation, project restoration, last-project identity, capability state, Back handling, explicit Exit handling, and lifecycle coordination through the application contracts. The former monolithic `IdeViewModel`/`AppRoot` editor shell is inactive reference material for later domain work; it is not the foundation authority. Behavioral acceptance and remote build verification remain deferred to the repository’s GitHub Actions workflow.

Terminal runtime, browser, Git, language intelligence, credentials, extensions, and advanced project acquisition remain intentionally outside this Phase 1 foundation and are represented only by explicit navigation placeholders or adapter boundaries.

## Domain status

### Project and workspace management

The acquisition and project-operation source slice is implemented: destination and containment preflight, exact-name creation, bounded ZIP validation/extraction, portable metadata initialization, identity read-back verification, registry registration, cleanup on failure, and result-only operation feedback. Existing-folder import preserves the project’s existing creation timestamp and unknown portable metadata fields instead of silently replacing them. Android/device acceptance remains deferred because no build or compilation was run.

### Code editing

The product behavior and Monaco provider direction are defined. Implementation remains future work and must follow Phase 4. Required acceptance coverage includes mobile controls, press-and-hold repetition, file tree behavior, search and replace, tab states, saving, recovery, external changes, and file mutation feedback.

### Terminal, runtime, dependencies, and background processes

The Termux runtime and SAF-accessible Android IDE filesystem provider now share one filesystem adapter. It exposes the Android IDE provider root and persisted SAF grants as mounted roots, resolves child nodes, preserves provider identity, and returns an optional native path only when the runtime can actually access it. Runtime initialization declares and installs the baseline toolchain through Termux commands. PTY descriptors survive process recreation as explicitly unavailable records rather than being falsely reported as live or silently discarded; project terminal launch refuses inaccessible working directories.

### Browser, previews, and developer tools

The unified browser behavior and GeckoView/console direction are defined. Implementation remains future work and must follow Phase 5. Required acceptance coverage includes normal browsing, browser tabs, downloads, local previews, development-server access, console behavior, viewport testing, and failure isolation.

### Git integration

Repository-level and global Git behavior, credential boundaries, and terminal interoperability are defined. A separate Git UI remains intentionally deferred; project Git actions now explain that Git is managed through the project Terminal rather than navigating to a misleading unfinished surface. Git operations remain terminal operations.

### Language intelligence

The source-level editor-intelligence slice is implemented through the shared filesystem resolver: language-server definitions, lifecycle-aware Termux stdio JSON-RPC sessions, initialize/initialized/shutdown handling, Monaco request routing, document open/change/save/close synchronization, completion, hover, definition, references, formatting, code actions, diagnostics, and snippets. Server responses now translate native file URIs back to provider-backed Monaco document identities, including nested diagnostics and workspace edits. Projects without a runtime-accessible directory remain explicitly unavailable. Android/device behavior and remaining language-specific server coverage remain acceptance work; no build or compilation was run.

### Editor toolbar

The toolbar uses a flat real-command catalog with a fixed five-action page size. The confirmed 23-action default is persisted in user order, stale IDs are normalized in settings, invalid saved orders fall back safely at render time, and language-server-dependent actions are labelled as such. Search and close remain in the editor top bar.

## Clarified implementation audit

The current cross-domain defects and the agreed filesystem-first correction are recorded in section 12 of `docs/ARCHITECTURE_CONTRACTS.md`. That section is the authoritative record of the audit findings and the permanent-fix direction; this tracker records only implementation status and does not duplicate the full decision text.

### Settings, security, credentials, and customization

The domain boundary and Keystore-backed security direction are defined. Implementation remains future work and must follow Phase 8.

### Extensions

Extensions remain an explicit placeholder. Packaging, permissions, sandboxing, lifecycle, installation, execution, and update behavior are not yet product-defined and must not be implemented as an assumed marketplace or unrestricted dynamic-code system.

## Repository discipline

The working tree contains pre-existing implementation changes and untracked documentation from earlier work. No existing change is accepted solely because it is present in source. Do not commit or push without explicit instruction. Keep validation targeted and avoid heavy Android builds or background processes unless explicitly authorized.
