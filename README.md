# Android IDE — Termux identity experiment

This `test` branch is a **non-distribution experiment** of Android IDE. It changes the application identity and private runtime layout to match the upstream Termux package identity so that the resulting debug APK can be tested on a phone.

## Experiment question

Other Android IDE branches have encountered permission-denied failures when terminal packages such as `pkg`, `npm`, and `apt` execute. The working hypothesis is that those packages contain hard-coded Termux identity and path assumptions. This branch tests whether using the same Android application identity and private filesystem layout removes that incompatibility.

The experiment deliberately targets the upstream Termux expectations:

```text
applicationId: com.termux
namespace:     com.termux

/data/data/com.termux/files
/data/data/com.termux/files/usr
/data/data/com.termux/files/home
```

This branch does **not** migrate files from the former Android IDE directories, retain compatibility fallbacks, or consume a custom Termux package repository. A fresh installation and device test are required to answer the experiment question.

## Scope and restrictions

- This is experimental source code, not a product release.
- Do not distribute, publish, sign, or treat this branch as production software.
- The branch retains only the source and documentation needed to implement and evaluate the experiment.
- The debug GitHub Actions workflow is retained because it is the approved way to produce a test APK for the phone-based validation step.
- Release workflows, release signing configuration, production APK configuration, archives, and superseded/unapproved planning documents are intentionally absent.
- Changes remain uncommitted and unpushed until the project owner explicitly instructs otherwise.

## Current source changes

The experiment branch currently includes:

- Upstream Termux application ID and generated-resource namespace: `com.termux`.
- Termux-compatible private runtime paths under `context.filesDir`, resolving to `files/usr` and `files/home`.
- No migration or fallback from legacy Android IDE or Termux-home paths.
- Termux resource imports updated to the generated `com.termux.R` namespace.
- Shared branded file-icon rendering across the file tree, file-name search, and project-content search results.

## Build and test workflow

Compilation is intentionally performed by GitHub Actions rather than locally. The retained workflow is `.github/workflows/build-debug.yml`; it runs lint, assembles a debug APK, and uploads the debug artifact for installation on the test phone.

No release APK or production-signing path is supported by this branch.

## Retained documentation

- [Approved product definition](docs/ANDROID_IDE_PRODUCT_DEFINITION_APPROVED.md) — product scope and user-facing behavior.
- [Approved implementation roadmap](docs/ANDROID_IDE_IMPLEMENTATION_ROADMAP_APPROVED.md) — implementation phases and acceptance gates.
- [Approved provider research](docs/ANDROID_IDE_PROVIDER_RESEARCH_APPROVED.md) — provider facts and technical constraints.
- [Architecture contracts](docs/ARCHITECTURE_CONTRACTS.md) — ownership, storage, lifecycle, provider, and identity boundaries.
- [Termux project filesystem concept report](docs/TERMUX_PROJECT_FILESYSTEM_CONCEPT_REPORT.md) — filesystem and runtime integration context.
- [Status tracker](docs/STATUS_TRACKER.md) — source-level implementation status and remaining validation work.

These documents describe product and architecture context; they do not authorize distribution or replace the experiment acceptance test.

## Repository layout

| Directory | Purpose |
|---|---|
| `android-ide/android/` | Android application and Gradle project |
| `android-ide/android/java/` | Kotlin and vendored Termux source |
| `android-ide/assets/` | Offline editor and developer-tool assets |
| `scripts/` | Build-support scripts used by GitHub Actions |
| `.github/workflows/build-debug.yml` | Debug-only CI build for device testing |
| `docs/` | Retained product definition and architectural context |

## License

The repository does not currently declare a license. Treat the code as all-rights-reserved unless a license is added by the project owner.
