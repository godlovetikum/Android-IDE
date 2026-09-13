# Android IDE

Android IDE is a mobile-first development environment for developers who build and maintain websites, web applications, mobile applications, and other software directly from an Android phone.

The project brings project management, SAF-backed file access, a Monaco-based editor, file tabs, previews, and Android-aware workflows into one focused application. The long-term goal is to provide a practical phone-based alternative to a desktop editor workflow without requiring users to switch between separate apps for project files, code editing, previews, terminal sessions, and Git operations.

## Current status

The **Phase 1 editor and project workspace foundation is implemented**. The project can create and open projects, browse files, edit and save documents, manage tabs, preview web content, and perform project-level operations. Production hardening remains in progress, particularly for Android storage providers, interrupted mutations, permission recovery, workspace restoration, and release-device validation.

Terminal sessions, persistent background processes, integrated Git mutations, language intelligence, and extensions are planned follow-on capabilities. They are documented as separate phases rather than being represented as completed features.

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

- [Project plan](docs/PROJECT_PLAN.md) — product scope, architecture, milestones, and phase definitions.
- [Status tracker](docs/STATUS_TRACKER.md) — current implementation status and task history.
- [Phase 1 production readiness](docs/PHASE1_PRODUCTION_READINESS.md) — open hardening gates for the editor and project workspace.
- [QA workflow](docs/QA_WORKFLOW.md) — validation and change-review process.
- [Debug log](docs/DEBUG_LOG.md) — historical defect analysis and fixes.
- [Tech-stack migration](docs/TECH_STACK_MIGRATION.md) — archival record of the migration from Rust/Slint to Kotlin/Compose.

## Product direction

Android IDE is being developed around a phone-first workflow: one application for project files, code editing, previews, terminal work, Git operations, imports, exports, and sharing. Android’s storage, process, and security constraints are treated as product requirements rather than desktop assumptions.

The next major product area after Phase 1 hardening is the integrated runtime: terminal tabs, project-scoped processes, persistent local servers, and preview routing that continue operating when the user changes visible tabs.

## Contributing

Before changing code, review the [QA workflow](docs/QA_WORKFLOW.md), [status tracker](docs/STATUS_TRACKER.md), and the relevant architecture or readiness document. Keep user-facing behavior, storage semantics, and Android lifecycle behavior explicit in the documentation.

## License

The repository does not currently declare a license. Treat the code as all-rights-reserved unless a license is added by the project owner.
