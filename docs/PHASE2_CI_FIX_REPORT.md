# Phase 2 CI Compile-Failure Fix Report

**Branch:** `dev`
**Failing run:** [`Debug Build` #36098151883](https://github.com/godlovetikum/Android-IDE/actions/runs/36098151883) — commit `ee5d8b1` "Implement Phase 2 project management and safety fixes"
**Failing task:** `:app:compileDebugKotlin` (`./gradlew lint`), 22 diagnostics
**Status:** Fixed at source level on `dev`; not committed, not pushed
**Constraint honored:** no Android build, no Gradle invocation, no Git write operation

## 1. Summary

The `dev` branch did not compile. The 22 diagnostics reported by the workflow reduce to **four defects plus one missing opt-in**, all introduced by the Phase 2 commit `ee5d8b1`. Each is recorded below and in `docs/DEBUG_LOG.md` as `BUG-029` through `BUG-032`.

| # | Defect | File | Diagnostics | Fix |
|---|---|---|---|---|
| 1 | Rename dialog wrote parent-composable state from a stateless composable | `app/AppShell.kt` | 2 | Added an `onRequestRename` callback parameter and routed the button through it |
| 2 | Missing import for a contract type used in a constructor signature | `project/ProjectDetailsService.kt` | 17 | Added `import dev.android.ide.contracts.ProjectRegistryAdapter` |
| 3 | Experimental Foundation API used without opt-in | `app/AppShell.kt` | 1 | Added `@OptIn(ExperimentalFoundationApi::class)` to `ProjectRow` |
| 4 | `contentEquals` inferred `Any` instead of `Boolean`; equal files could never be reported equal | `saf/SafRepository.kt` | 1 | Restructured so every branch returns an explicit `Boolean` |
| 5 | Positional constructor call invalidated by an inserted defaulted field | `viewmodel/IdeViewModel.kt` | 1 | Converted the call to named arguments |

Diagnostic totals reconcile exactly: `AppShell.kt` 3 (defects 1 and 3), `ProjectDetailsService.kt` 17 (defect 2), `SafRepository.kt` 1, `IdeViewModel.kt` 1 — 22 in total. No diagnostic was left unexplained, and no unrelated code was modified.

## 2. Diagnostic mapping

Every line from the CI log is mapped to its cause below.

| CI diagnostic | Cause |
|---|---|
| `AppShell.kt:719:25 Unresolved reference: renameName` | Defect 1 |
| `AppShell.kt:720:25 Unresolved reference: renameDialogVisible` | Defect 1 |
| `AppShell.kt:817:14 This foundation API is experimental…` | Defect 3 |
| `ProjectDetailsService.kt:12:27 Unresolved reference: ProjectRegistryAdapter` | Defect 2 (the root cause) |
| `ProjectDetailsService.kt:16:64 Unresolved reference: it` | Defect 2 (cascade) |
| `ProjectDetailsService.kt:18:53`, `:24:57`, `:31:28`, `:31:65`, `:34:37`, `:35:41`, `:36:38` — `Unresolved reference: location` | Defect 2 (cascade) |
| `ProjectDetailsService.kt:29:29 Unresolved reference: name` | Defect 2 (cascade) |
| `ProjectDetailsService.kt:30:36`, `:42:71 Unresolved reference: description` | Defect 2 (cascade) |
| `ProjectDetailsService.kt:32:37`, `:32:78`, `:33:34`, `:43:70` — `Unresolved reference: lastOpenedAt` / `registeredAt` | Defect 2 (cascade) |
| `ProjectDetailsService.kt:42:31 Type mismatch: inferred type is Any but String was expected` | Defect 2 (cascade) |
| `SafRepository.kt:686:13 Type mismatch: inferred type is Any but Boolean was expected` | Defect 4 |
| `IdeViewModel.kt:915:49 No value passed for parameter 'uri'` | Defect 5 |

## 3. Defect details

### 3.1 Defect 1 — rename dialog state was resolved in the wrong composable

`AppContent` is a **stateless** composable: it receives `state` and callbacks as parameters and declares no state of its own. The `renameName` and `renameDialogVisible` `rememberSaveable` values belong to `AppShell`, which also renders the rename `AlertDialog`. The Phase 2 commit placed the dialog trigger inside `AppContent`:

```kotlin
Button(
    onClick = {
        renameName = selected?.name.orEmpty()   // not visible here
        renameDialogVisible = true              // not visible here
    },
    …
)
```

Those names exist in neither `AppContent` nor its parameter list, so both references were unresolvable. The correction follows the pattern already used by the sibling actions (`onDuplicateProject`, `onRelocateProject`): a callback parameter carries the request upward and `AppShell`, the actual state owner, performs the assignment.

```kotlin
// AppContent signature
onRequestRename: (String) -> Unit,

// AppContent call site
onClick = { onRequestRename(selected?.name.orEmpty()) },

// AppShell wiring
onRequestRename = { currentName ->
    renameName = currentName
    renameDialogVisible = true
},
```

### 3.2 Defect 2 — one missing import produced seventeen diagnostics

`ProjectDetailsService` lives in `dev.android.ide.project`; `ProjectRegistryAdapter` is declared in `dev.android.ide.contracts`. The import block brought in `CapabilityState` and `ProjectIdentity` from that package but omitted `ProjectRegistryAdapter`. The constructor parameter type was therefore unresolved, so the compiler could not type `registry`, and every subsequent member access on the value it yields failed to resolve — reporting `it`, `location`, `name`, `description`, `lastOpenedAt`, and `registeredAt` as unresolved, plus an `Any`/`String` mismatch at the first property read.

This is the most misleading failure in the log: seventeen diagnostics that read as scattered member errors were caused by one absent import, and all seventeen disappear with the one-line fix.

### 3.3 Defect 3 — experimental Foundation API used without opt-in

`ProjectRow` calls `combinedClickable` for long-press multi-selection, but `combinedClickable` is still annotated `@ExperimentalFoundationApi` in Compose Foundation 1.6.x. The other composables in the project already carry the matching opt-in (for example `FileTreePanel` and `EditorPane`), so this was an omission rather than a deliberate choice. The correction adds the import and the annotation, keeping the project's existing convention of placing `@OptIn` directly above `@Composable`:

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi

// combinedClickable is still experimental in Compose Foundation; long-press project
// selection requires this explicit opt-in.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
```

### 3.4 Defect 4 — `contentEquals` inferred `Any` and could not report equality

The helper ended with this shape:

```kotlin
openInputStream(source)?.use { source ->
    openInputStream(target)?.use { target ->
        while (true) {
            …
            if (sourceCount != targetCount) return@withContext false
            if (sourceCount == -1) return@withContext true
            …
        }
    } ?: return@withContext false
} ?: return@withContext false
```

Neither elvis right-hand side contributes a type, because a non-local return has type `Nothing`. The `try` branch's type therefore came from the outer `use` lambda, whose last expression was the `while (true)` loop. That loop exits only through the non-local `return@withContext true`, so its type is `Unit`. The `try`/`catch` expression thus had branch types `Unit` and `Boolean`, whose common supertype is `Any`, which cannot satisfy the declared `Boolean` return type.

The restructured helper makes every path explicit while preserving the intended semantics:

- both streams are bound to locals, so an already-opened stream is closed if the second cannot be opened;
- `if (sourceCount != targetCount) return@withContext false` keeps a short read a mismatch;
- `if (sourceCount == -1) break` replaces the non-local return with a normal loop exit;
- the `use` block ends with `true`, giving the `try` branch an unambiguous `Boolean`.

This matters functionally, not only for compilation. `contentEquals` backs `verifyDocument`, the content check used by `copyDocumentWithExactName`, the copy-then-delete move fallbacks, and metadata migration before a source is deleted. A helper that can never return `true` would make every verified copy fail.

### 3.5 Defect 5 — positional constructor call broken by an inserted parameter

The same commit added `description: String = ""` as the **second** parameter of `Project`. The pre-existing call site passed two positional arguments:

```kotlin
Project(extractProjectName(uri), uri)
```

With the new signature the URI landed in `description` and `uri` received nothing, producing "No value passed for parameter 'uri'". Named arguments fix the call site independently of parameter order:

```kotlin
Project(name = extractProjectName(uri), uri = uri)
```

## 4. Verification performed

Because Android builds are prohibited in this environment, verification was targeted and source-level:

1. **CI log reconciliation** — all 22 diagnostics were extracted from the workflow log and each was mapped to a specific cause; the per-file counts reconcile exactly to the reported total.
2. **Structural integrity** — delimiter balance for every edited file was compared against `HEAD` and found unchanged, so no edit introduced unbalanced braces, parentheses, or brackets.
3. **Sealed-type exhaustiveness** — the commit added the `Partial` case to `SafeMutationResult`; every `when` over that type (`projectMutationReason`, the rename handler, and the `as?`/`else` fallbacks) was inspected and still covers all cases.
4. **Scope check** — `AppContent` was verified to hold no remaining references to any `AppShell`-scoped state name, and its signature and call site were checked for parameter agreement.
5. **Member-reference audit** — a resolution pass over the new services and their adapters found no unresolved member calls.

## 5. Files changed

| File | Change |
|---|---|
| `android-ide/android/java/dev/android/ide/app/AppShell.kt` | `ExperimentalFoundationApi` import; `onRequestRename` parameter and wiring; rename button delegated to the callback; `@OptIn` on `ProjectRow` |
| `android-ide/android/java/dev/android/ide/project/ProjectDetailsService.kt` | Added the missing `ProjectRegistryAdapter` import |
| `android-ide/android/java/dev/android/ide/saf/SafRepository.kt` | Restructured `contentEquals` to return an explicit `Boolean` and close streams correctly |
| `android-ide/android/java/dev/android/ide/viewmodel/IdeViewModel.kt` | Named arguments in the `Project(…)` fallback construction |
| `docs/DEBUG_LOG.md` | New index rows and entries `BUG-029`–`BUG-032` |
| `docs/STATUS_TRACKER.md` | New "Phase 2 compile-failure remediation" section |

The six working-tree files remain **uncommitted**. `HEAD` is still `ee5d8b1`, and `git reflog` contains only the original clone entry, confirming that no commit, push, reset, or history change was performed.

## 6. Remaining work

The Phase 2 gate is still open. This pass only restored compilability; the acceptance activities recorded in `STATUS_TRACKER.md` remain outstanding and require tooling unavailable here:

- a `Debug Build` run to confirm `:app:compileDebugKotlin` and `lint` pass;
- the Android 15/16 provider matrix for SAF URI behavior, deletion scope, batch ordering, export-failure cleanup, and private-workspace capabilities.
