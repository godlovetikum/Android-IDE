# DEBUG_LOG.md — Android IDE

Historical debugging record. Every bug fix, architectural correction, and failed design decision must be recorded here.

Future contributors must be able to understand previous mistakes without rediscovering them.

---

## Log Format

| Field | Description |
|-------|-------------|
| Date | ISO 8601 date (YYYY-MM-DD) |
| Subsystem | Module where the issue occurred |
| Issue | Brief description of the problem observed |
| Root Cause | Why it happened |
| Files Modified | Comma-separated list of modified files |
| Solution | What was changed to fix it |
| Prevention | How to avoid this class of bug in the future |

---

## Entries

### BUG-001 — Launcher icon missing from APK

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | android |
| **Issue** | APK installed and launched but showed no icon on the home screen launcher. |
| **Root Cause** | `AndroidManifest.xml` had no `android:icon` or `android:roundIcon` attributes and no `res/` directory existed. |
| **Files Modified** | `AndroidManifest.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/mipmap-anydpi-v26/ic_launcher_round.xml`, `res/drawable/ic_launcher_foreground.xml`, `res/values/colors.xml` |
| **Solution** | Added `android:icon="@mipmap/ic_launcher"` and `android:roundIcon` to `<application>`. Created adaptive icon: vector "A" monogram (VS-Code blue #007ACC) on dark IDE background (#1e1e1e). Since `minSdk = 26`, only the `mipmap-anydpi-v26/` density bucket is needed. |
| **Prevention** | Always add `android:icon` to `<application>` and create `mipmap-anydpi-v26/` icon resources when creating a new Android project. |

---

### BUG-002 — Monaco editor required internet — offline use impossible

| Field | Value |
|-------|-------|
| **Date** | 2026-06-11 |
| **Subsystem** | editor |
| **Issue** | On a device without internet, the editor WebView showed "Loading editor…" indefinitely. |
| **Root Cause** | `index.html` and `monaco-init.js` loaded Monaco from the unpkg CDN at runtime. |
| **Files Modified** | `assets/editor/index.html`, `assets/editor/monaco-init.js`, `scripts/fetch-monaco.sh` (created), `.gitignore` |
| **Solution** | Changed both files to use relative local paths (`vs/loader.js`, `vs`). Created `scripts/fetch-monaco.sh` to download Monaco 0.52.0 from npm into `assets/editor/vs/`. Added `assets/editor/vs/` to `.gitignore` (~20 MB). Added CI step to run the script before `./gradlew` in both jobs. |
| **Prevention** | Never load runtime assets from a CDN in a mobile app. Bundle all required JS/CSS in the APK. Add `vs/` (or equivalent) to `.gitignore` and document the download step. |
| **Notes** | The script is idempotent — exits early if `vs/loader.js` already exists. Monaco version is pinned in `fetch-monaco.sh`; change `MONACO_VERSION` there to upgrade. |

---

### BUG-003 — SafRepository.listChildren returns root children when expanding subdirectory

| Field | Value |
|-------|-------|
| **Date** | 2026-06-12 |
| **Subsystem** | filesystem |
| **Issue** | Expanding a subdirectory in the file tree showed the project root's children again instead of the subdirectory's children. |
| **Root Cause** | `listChildren` called `DocumentsContract.getTreeDocumentId(parentUri)` for all URIs where `isTreeUri()` is true. `getTreeDocumentId` always returns the **root** document ID. Child directory URIs built by `buildDocumentUriUsingTree` have the path `/tree/<treeDocId>/document/<docId>` — `isTreeUri()` returns true for these, but the correct document ID is in the "document" segment, not the "tree" segment. |
| **Files Modified** | `saf/SafRepository.kt` |
| **Solution** | In `listChildren`, detect whether the URI has a "document" path segment. If yes, use `DocumentsContract.getDocumentId(parentUri)` to get the subdirectory's document ID. If no, use `getTreeDocumentId(parentUri)` for the root tree URI. |
| **Prevention** | SAF has two URI shapes: plain tree URIs (`/tree/<docId>`) from `ACTION_OPEN_DOCUMENT_TREE` and document-within-tree URIs (`/tree/<treeDocId>/document/<docId>`) from `buildDocumentUriUsingTree`. Always distinguish between them when extracting the document ID. Never assume `getTreeDocumentId` gives the correct ID for child document URIs. |

---

## Architectural Corrections

### AC-001 — Tech Stack Migration: Slint/Rust → Kotlin/Jetpack Compose

**Date:** 2026-06-12

**Original design:**
- Rust application with Slint UI framework
- JNI bridge for SAF and WebView
- NativeActivity entry point via `android_main()`
- `IDEActivity extends NativeActivity` to layer Monaco WebView above native Surface

**Migrated design:**
- Kotlin + Jetpack Compose
- `ComponentActivity.setContent {}` entry point
- Monaco WebView as a Compose `AndroidView` inside `EditorPane.kt`
- SAF accessed directly via `ContentResolver` in `SafRepository.kt`
- No JNI, no NDK, no native code

**Consequence:** All Rust/Slint source files removed. Build pipeline simplified to standard `./gradlew assembleRelease`. See TECH_STACK_MIGRATION.md for the full migration record.

Last updated: 2026-06-12
