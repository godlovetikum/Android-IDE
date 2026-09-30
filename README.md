# Android IDE

Android IDE is a mobile-first development environment for developers who build and maintain websites, web applications, mobile applications, and other software directly from an Android phone.

The project brings project management, SAF-backed file access, a Monaco-based editor, file tabs, previews, and Android-aware workflows into one focused application. The long-term goal is to provide a practical phone-based alternative to a desktop editor workflow without requiring users to switch between separate apps for project files, code editing, previews, terminal sessions, and Git operations.

## Current status

The active application shell provides Home, project registration state, location capability reporting, portable metadata handling, lifecycle state, and navigation. The source now also includes an Android IDE `DocumentsProvider` user-files root, provider-backed terminal working directories, a Termux-managed baseline package plan, and initial stdio language-server runtime wiring.

Terminal sessions and extensions still require their remaining service gates. Git UI/acquisition is intentionally deferred; Git remains available through the terminal package. Language-server process startup is present, while Monaco request/notification binding and diagnostics/completion presentation remain follow-up work.

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

- [Approved product definition](docs/ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md) — product scope, supported domains, and user-facing behavior.
- [Approved provider research](docs/ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md) — provider direction, integration facts, and technical constraints.
- [Approved implementation roadmap](docs/ANDROID_IDE_IMPLEMENTATION_ROADMAP_APPROVED.md) — phase order, deliverables, and acceptance gates.
- [Phase 0 implementation guidance](docs/PHASE_0_IMPLEMENTATION_GUIDANCE.md) — contract/bootstrap outputs and entry checklist.
- [Application architecture contracts](docs/ARCHITECTURE_CONTRACTS.md) — accepted ownership, lifecycle, adapter, event, error, and identity contracts.
- [Termux-backed project filesystem concept report](docs/TERMUX_PROJECT_FILESYSTEM_CONCEPT_REPORT.md) — provider-first storage model, runtime/package separation, package baseline, and LSP direction.
- [Status tracker](docs/STATUS_TRACKER.md) — current implementation status and task history.
- [Debug log](docs/DEBUG_LOG.md) — historical defect analysis and fixes.
- Superseded planning documents are retained in [`docs/archive/`](docs/archive/).

## Product direction

Android IDE is being developed around a phone-first workflow: one application for project files, code editing, previews, terminal work, Git operations, imports, exports, and sharing. Android’s storage, process, and security constraints are treated as product requirements rather than desktop assumptions.

The next major product area after Phase 1 hardening is the integrated runtime: terminal tabs, project-scoped processes, persistent local servers, and preview routing that continue operating when the user changes visible tabs.

## Contributing

Before changing code, review the [Phase 0 implementation guidance](docs/PHASE_0_IMPLEMENTATION_GUIDANCE.md), [status tracker](docs/STATUS_TRACKER.md), and the relevant approved architecture or product document. Keep user-facing behavior, storage semantics, and Android lifecycle behavior explicit in the documentation.

## License

The repository does not currently declare a license. Treat the code as all-rights-reserved unless a license is added by the project owner.
