# Android IDE Status Tracker

## Current project state

The product-definition and provider-research discovery gate is complete. The approved documents now define what Android IDE is, the supported feature domains, the storage and runtime relationship, the selected provider direction, and the staged implementation order.

The active canonical documents are:

- `docs/ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md`
- `docs/ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md`
- `docs/ANDROID_IDE_IMPLEMENTATION_ROADMAP_APPROVED.md`
- `docs/PHASE_0_IMPLEMENTATION_GUIDANCE.md`
- `docs/ARCHITECTURE_CONTRACTS.md`

Superseded planning notes, earlier research reports, and working versions are retained under `docs/archive/` for traceability. They are not active implementation guidance.

## Completed discovery gate

The first implementation-planning objective—defining and clarifying the product—has been satisfied. The approved definition covers the six product domains, mobile-first navigation, project acquisition, storage ownership, the private Termux development workspace, global terminal behavior, browser and preview behavior, Git levels, editor behavior, lifecycle expectations, security boundaries, and the extensions placeholder.

The approved provider research covers obtainable provider choices, versions, licenses, acquisition routes, customization boundaries, Termux runtime initialization, the private development filesystem, Monaco integration, GeckoView, browser tooling, Git, LSP, Keystore, and Android lifecycle constraints.

The approved roadmap establishes the implementation order and acceptance gates. No feature-domain implementation is authorized merely by the existence of these documents.

## Architecture contract status

Phase 0 contract/bootstrap work is complete on the `dev` branch. The accepted contract set records state ownership, storage authority, project-location capability rules, navigation and restoration behavior, lifecycle and session semantics, provider adapter boundaries, shared events, operation outcomes, error categories, and the identity migration decision. This is not a claim that later runtime, editor, browser, Git, or project-management gates are complete.

The Kotlin contract types are in `android-ide/android/java/dev/android/ide/contracts/ApplicationContracts.kt`. They are provider-neutral and do not claim that any runtime, editor, browser, Git, language-intelligence, credentials, or extension feature is implemented.

The identity migration from `dev.androidide` / `.androidide` to `dev.android.ide` / `.dev-android-ide` is implemented as a one-way acquisition/opening migration. The target directory is checked first; if absent, legacy files are copied into it and the legacy directory is deleted only after successful migration. All later reads and writes use only `.dev-android-ide`.

## Application foundation entry status

The architecture contracts and identity prerequisite are complete. The application-foundation implementation is complete at source level; its behavioral gate remains unverified until the repository workflow runs on Android tooling.

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
The Phase 2 implementation is now present on `dev`: blank project creation, existing-folder import, validated ZIP import, provider-backed project details, exact-name file/folder mutations, export, duplication, copy-verify-delete relocation, registry removal, permanent deletion, batch actions, and explicit private-workspace copy/move boundaries. All destructive paths inspect destinations, reject nested registered-project locations, verify copies before deleting sources, and return complete, partial, blocked, or failed operation reports. ZIP import rejects traversal, absolute paths, duplicate paths, unsafe entries, oversized archives, and extraction conflicts before or during exact extraction, then cleans up failed imports. Project details compute current file count, folder count, total size, timestamps, location, capability state, language totals, and Git branch, failing closed when the provider cannot be fully inspected. GitHub Actions and phone acceptance remain required before declaring the Phase 2 gate passed.

The project list now supports long-press multi-selection, selected-row feedback, reversible batch removal from the registry, verified batch permanent deletion, cancellation, and selection cleanup when navigating away. Permanent deletion is explicitly confirmed and refuses to remove a registered parent while child projects remain.

The remaining project-management actions are now wired: project rename, export/share as ZIP, copy storage path, copy Git remote URLs, batch ZIP export, batch path copy, verified batch permanent deletion, and precise unavailable-versus-permission-lost registry state. Acquisition and transfer reject both directions of project containment. Provider mutations preserve tri-state deletion uncertainty, normalize document URIs, use bounded streaming copies, verify copied contents, report unrecoverable move cleanup as partial, and preserve metadata-migration conflicts. ZIP export stages archives before destination write. Private-workspace transfer is explicitly unavailable until its runtime adapter is initialized rather than reporting false success. Archive-name collisions and provider failures produce partial or blocked reports instead of overwriting or silently succeeding. GitHub Actions and device/provider acceptance remain the gate.

### Code editing

The product behavior and Monaco provider direction are defined. Implementation remains future work and must follow Phase 4. Required acceptance coverage includes mobile controls, press-and-hold repetition, file tree behavior, search and replace, tab states, saving, recovery, external changes, and file mutation feedback.

### Terminal, runtime, dependencies, and background processes

The Termux runtime direction and private development workspace model are defined. Implementation remains future work and must follow Phase 3. Required acceptance coverage includes bootstrap initialization, package capability reporting, PTYs, global sessions, project working directories, child-process ownership, foreground lifecycle, process-loss reporting, and explicit close behavior.

### Browser, previews, and developer tools

The unified browser behavior and GeckoView/console direction are defined. Implementation remains future work and must follow Phase 5. Required acceptance coverage includes normal browsing, browser tabs, downloads, local previews, development-server access, console behavior, viewport testing, and failure isolation.

### Git integration

Repository-level and global Git behavior, credential boundaries, and terminal interoperability are defined. Implementation remains future work and must follow Phase 6.

### Language intelligence

The LSP client and runtime-managed language-server direction are defined. Implementation remains future work and must follow Phase 7.

### Settings, security, credentials, and customization

The domain boundary and Keystore-backed security direction are defined. Implementation remains future work and must follow Phase 8.

### Extensions

Extensions remain an explicit placeholder. Packaging, permissions, sandboxing, lifecycle, installation, execution, and update behavior are not yet product-defined and must not be implemented as an assumed marketplace or unrestricted dynamic-code system.

## Repository discipline

The working tree contains pre-existing implementation changes and untracked documentation from earlier work. No existing change is accepted solely because it is present in source. Do not commit or push without explicit instruction. Keep validation targeted and avoid heavy Android builds or background processes unless explicitly authorized.

## Phase 0/1 hardening completed before Phase 2

The `dev` branch received the following pre-Phase-2 corrections on 2026-09-24. SAF mutation URI normalization now preserves child document IDs instead of converting them to the selected tree root. Copy-then-delete move fallbacks verify the copied destination before source deletion and clean up the copy when source removal fails. The foundation shell no longer renders redundant application branding above navigation, and selected navigation items use layout-stable styling rather than a variable-width bullet prefix.

These corrections are source-level hardening only. Android build, lint, device, and provider-matrix acceptance remain assigned to GitHub Actions and device testing. No Phase 2 feature implementation has been started in this session.
