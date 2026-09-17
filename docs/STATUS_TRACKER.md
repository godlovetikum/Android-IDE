# Android IDE Status Tracker

## Immediate planning gate

Review and approve `docs/NEXT_WORK_PRIORITY.md` and `docs/APP_STATE_AND_NAVIGATION.md` before further source implementation. The review must clarify the complete product direction, domain boundaries, application/project/surface state ownership, navigation behavior, editor/terminal/browser tab lifecycles, durable restoration, identity migration, and independent domain testing.

The proposed application ID is `dev.android.ide`, and the proposed project metadata directory is `.dev-android-ide`. The current source still uses `dev.androidide` and `.androidide`; no identity migration has been authorized or implemented. Visibility settings are intended for the metadata folder and `.git` only. README files are ordinary project documentation and are not part of that toggle.

## Reported unresolved domains

### Project and storage

Delete, move, cut-paste, provider inconsistency, permission-loss, project-creation, and metadata-initialization problems remain unresolved and require independent acceptance testing.

### Editor

The Android 15 editing crash, false unsaved-state recovery, document/global search replacement workflow, keyboard navigation, repeat-press controls, templates, and language-intelligence requirements remain unresolved or only partially implemented.

### Terminal and runtime

Terminal UI, PTY management, Linux runtime, package installation, durable sessions, long-running task ownership, and explicit Hide versus Close behavior remain future work.

### Git

Git status, staging, commits, branches, synchronization, credentials, and diff review remain future work and are separate from terminal implementation.

### Browser

The in-app browser, multiple browser tabs, preview integration, browser restoration, and WebView failure isolation are future work and were not part of the original project plan.

### Language intelligence

LSP lifecycle, diagnostics, completion, symbol navigation, references, code actions, and language-aware HTML behavior remain future work after the editor direction is stabilized.

### Extensions

Extension packaging, loading, sandboxing, permissions, management, and examples remain future work after the core product domains are clarified.

## Validation rule

Each domain must be tested independently before cross-domain integration. The working tree contains uncommitted implementation changes from earlier passes; no change should be treated as accepted solely because it is present in source.
