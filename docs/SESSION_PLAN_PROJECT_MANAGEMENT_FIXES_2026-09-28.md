# Session Plan: Project-Management Corrections

**Branch:** `dev`  
**Date:** 2026-09-28  
**Constraints:** No commit, no push, no build, no compilation, no lint/test execution.

## Purpose

Apply the corrections identified during the project-management review while preserving the product's mobile-first, concise UI direction. Service-layer verification remains detailed; user-facing workflows expose short, actionable feedback.

## Scope

### 1. Remove from Registry

`Remove from Registry` must remove both:

- the global project-registry record; and
- the Android IDE portable metadata directory created for that registration.

It must not delete or modify ordinary project files, Git data, or the project directory. Metadata cleanup must be exact-name and verified, and must never delete a similarly named user directory.

If metadata cleanup cannot be completed, keep the registry record and return a partial/failed result so the user can retry safely. If metadata is already absent, treat cleanup as complete.

### 2. Provider-aware deletion

For files, folders, and projects:

- a failed delete request is failure;
- positive post-delete existence is failure;
- absence is success;
- a provider-invalidated/unresolvable old URI after an accepted delete is not converted into a false permission failure.

Successful project deletion must remove the registry record. The same interpretation must be used in cleanup paths and move/copy rollback where an accepted delete invalidates the old URI.

### 3. Acquisition workflow presentation

Keep acquisition feedback in the active acquisition workflow while it is open. Keep project-list mutation feedback on the project list. Do not expose provider/preflight internals as a data dump.

Simplify acquisition dialogs to:

- essential inputs and selected source/destination;
- one concise status message or progress state;
- primary action and correction/cancel action.

Preserve service-level conflict, containment, archive, metadata, and registration checks. Do not remove valid deferred placeholder controls.

## Acceptance criteria

- `removeFromRegistry` invokes verified portable-metadata cleanup before registry removal.
- Batch registry removal inherits the same behavior.
- Permanent project deletion treats accepted deletion plus `INACCESSIBLE` old URI as deletion success/registry-cleanup eligible, while `EXISTS` remains failure.
- File/folder deletion and cleanup paths do not require the deleted URI to remain queryable as `ABSENT` when the delete operation was accepted.
- Acquisition dialogs do not render duplicated diagnostic cards or technical verification language by default.
- Operation reports remain visible in the active acquisition modal and project-list feedback remains available for project mutations.
- No unrelated source files are changed.
- Final audit checks each criterion with source inspection only.

## Re-audit result

Source-only audit completed after implementation:

- Remove from Registry now removes only the exact Android IDE target/legacy metadata directories, then removes the registry record; ordinary project content is not targeted.
- Metadata cleanup failure leaves the project registered and returns a retryable partial outcome.
- Permanent project deletion now rejects only a positive `EXISTS` result after an accepted delete; `ABSENT` and provider-invalidated `INACCESSIBLE` results continue to registry cleanup.
- File/folder deletion, relocation rollback, duplicate cleanup, ZIP cleanup, path rollback, and Save As cleanup no longer require the stale deleted URI to report `ABSENT`; they require an accepted delete and no positive existence.
- Acquisition feedback remains in the active acquisition modal as a compact status line; the stacked completion dialog was removed, and successful acquisition offers `Open project` in the same modal.
- Project-list mutation feedback remains owned by the project-list/application state.
- `git diff --check` passed; all intended plan/source files exist and are non-empty.
- No build, compilation, lint, or tests were run. No commit or push was performed.
