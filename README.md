# Android IDE

Android IDE is a mobile-first development environment for developers who build and maintain websites, web applications, mobile applications, and other software directly from an Android phone.

The project brings project management, SAF-backed file access, a Monaco-based editor, file tabs, previews, and Android-aware workflows into one focused application. The long-term goal is to provide a practical phone-based alternative to a desktop editor workflow without requiring users to switch between separate apps for project files, code editing, previews, terminal sessions, and Git operations.

## Current status

The active application shell provides Home, project registration state, location capability reporting, portable metadata handling, lifecycle state, project acquisition, live project details, export, duplication, relocation, registry removal, permanent deletion, and common operation feedback. The project operations service also exposes exact-name file and folder mutations, batch actions, and explicit private-workspace transfer boundaries. GitHub Actions and phone acceptance remain required before the Phase 2 gate is declared complete. The editor implementation inherited from the earlier application remains reference code until it is rebuilt behind those contracts.

Terminal sessions, persistent background processes, integrated Git mutations, language intelligence, and extensions remain unavailable until their owning services and provider boundaries are implemented.

## Technology

| Area | Current implementation |
|---|---|
| Platform | Native Android |
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose and Material 3 |
| Editor | Monaco Editor 0.52.0 hosted in WebView |
| Storage | Android Storage Access Framework (SAF) |
| State | AndroidViewModel and StateFlow |
| Build | Gradle 8.7, Android Gradle Plugin 8.3.2 |
| Automation | GitHub Actions for wrapper generation and APK builds |

## Repository layout

| Directory | Purpose |
|---|---|
| `android-ide/android/` | Android application and Gradle project |
| `android-ide/android/java/` | Kotlin application source |
| `android-ide/assets/` | Offline editor assets and Monaco integration |
| `scripts/` | Build-support scripts such as Monaco asset retrieval |
| `.github/workflows/` | Manual wrapper generation and APK build workflows |
| `docs/` | Project plan, architecture history, QA process, status, and readiness records |

## Building

The Android Gradle project is located in `android-ide/android/`. The repository includes the Gradle wrapper used by CI and GitHub Actions workflows. A local Android SDK is required for compilation and linting.

```bash
cd android-ide/android
./gradlew assembleDebug
```

APK builds are also available through the repository’s GitHub Actions workflows. The wrapper-generation workflow is manual-only and is separate from APK compilation.

## Documentation

- [Approved implementation roadmap](docs/ANDROID_IDE_IMPLEMENTATION_ROADMAP_APPROVED.md) — product scope, milestones, dependencies, and phase definitions.
- [Status tracker](docs/STATUS_TRACKER.md) — current implementation status and task history.
- [Application architecture contracts](docs/ARCHITECTURE_CONTRACTS.md) — accepted ownership, lifecycle, adapter, event, error, and identity contracts.
- [Approved product definition](docs/ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md) — user-visible behavior and product boundaries.
- [Approved provider research](docs/ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md) — selected providers and integration constraints.
- [Phase 0 implementation guidance](docs/PHASE_0_IMPLEMENTATION_GUIDANCE.md) — contract and foundation entry criteria.
- [QA workflow](docs/archive/2026-09-21-superseded/QA_WORKFLOW.md) — validation and change-review process retained from the project workflow.
- [Debug log](docs/DEBUG_LOG.md) — historical defect analysis and fixes.
- [Archived technology-stack migration](docs/archive/2026-09-21-superseded/TECH_STACK_MIGRATION.md) — archival record of the migration from Rust/Slint to Kotlin/Compose.

## Product direction

Android IDE is being developed around a phone-first workflow: one application for project files, code editing, previews, terminal work, Git operations, imports, exports, and sharing. Android’s storage, process, and security constraints are treated as product requirements rather than desktop assumptions.

The next major product area after Phase 1 hardening is project and workspace management: reliable project acquisition, inspection, copying, moving, exporting, and safe removal. The integrated runtime follows that phase in the approved roadmap.

## Contributing

Before changing code, review the [QA workflow](docs/QA_WORKFLOW.md), [status tracker](docs/STATUS_TRACKER.md), and the relevant architecture or readiness document. Keep user-facing behavior, storage semantics, and Android lifecycle behavior explicit in the documentation.

## License

The repository does not currently declare a license. Treat the code as all-rights-reserved unless a license is added by the project owner.
