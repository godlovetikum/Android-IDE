# Android IDE Status Tracker

## Current project state

The product-definition and provider-research discovery gate is complete. The approved documents now define what Android IDE is, the supported feature domains, the storage and runtime relationship, the selected provider direction, and the staged implementation order.

The active canonical documents are:

- `docs/ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md`
- `docs/ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md`
- `docs/ANDROID_IDE_IMPLEMENTATION_ROADMAP_APPROVED.md`
- `docs/PHASE_0_IMPLEMENTATION_GUIDANCE.md`

Superseded planning notes, earlier research reports, and working versions are retained under `docs/archive/` for traceability. They are not active implementation guidance.

## Completed discovery gate

The first implementation-planning objective—defining and clarifying the product—has been satisfied. The approved definition covers the six product domains, mobile-first navigation, project acquisition, storage ownership, the private Termux development workspace, global terminal behavior, browser and preview behavior, Git levels, editor behavior, lifecycle expectations, security boundaries, and the extensions placeholder.

The approved provider research covers obtainable provider choices, versions, licenses, acquisition routes, customization boundaries, Termux runtime initialization, the private development filesystem, Monaco integration, GeckoView, browser tooling, Git, LSP, Keystore, and Android lifecycle constraints.

The approved roadmap establishes the implementation order and acceptance gates. No feature-domain implementation is authorized merely by the existence of these documents.

## Active implementation gate

Complete Phase 0 contract preparation before expanding source implementation. Follow `docs/PHASE_0_IMPLEMENTATION_GUIDANCE.md`.

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

## Domain status

### Project and workspace management

The product behavior is defined. Implementation remains future work and must follow Phase 2 of the approved roadmap. Required acceptance coverage includes acquisition, conflict preflight, metadata initialization, project registry, file and folder mutations, project location classes, export, duplication, relocation, removal, deletion, and permission loss.

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
