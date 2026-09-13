# Android IDE Phase 1 Production-Readiness Review

**Review date:** 2026-09-12

## Executive conclusion

The repository has a credible Phase 1 foundation: users can create and open projects, browse SAF-backed storage, edit files in Monaco, save files, manage tabs, preview web content, and perform project-level operations such as search, sorting, duplication, export, and storage migration. However, it should not yet be described as **production-grade Phase 1 complete**. The core workflows exist, but the production gate remains open because Android storage providers, process death, permission revocation, interrupted mutations, workspace restoration, and release-device validation are not yet covered to a production standard.

The correct boundary is:

> **Phase 1 is the editor and project workspace foundation. The foundation is functionally implemented, but production hardening is incomplete.**

Terminal execution, persistent local servers, full Git mutation workflows, and LSP are not Phase 1 omissions; they belong to the subsequent developer-runtime and intelligence phases. They are essential to Godlove’s complete phone-based developer tool, but should be planned as explicit phases rather than quietly implied by a “complete” Phase 1 label.

## Visibility and metadata policy

`README.md` is a normal project document and is now visible by default. It can be hidden through the File Tree settings when a user prefers a lower-noise tree. The project-local `.androidide` directory is treated like `.git`: it is hidden by default, but it can be revealed through settings for inspection, backup, and advanced maintenance.

The project metadata directory should contain only project-scoped IDE information. The current direction is appropriate:

| Location | Appropriate contents |
|---|---|
| `.androidide/project.json` | Schema version, project display name, creation metadata, and migration markers |
| `.androidide/workspace.json` | Open document URIs, active document, workspace layout/version, and project-local view state |
| `.androidide/recovery/` or equivalent future location | Project-specific recovery drafts when they are migrated out of app preferences |
| App-private storage | Global theme, editor defaults, keyboard preferences, credentials, recent-project registry, and crash telemetry |
| `.git/` | Git’s own repository data; never rewrite or treat it as IDE metadata |

The next hardening step is migration/version handling: projects without `.androidide` should open normally, metadata creation should be retryable, malformed metadata should be quarantined rather than blocking project open, and metadata writes should use temporary files or a safe replacement strategy where the provider supports it.

## Phase 1 production gate

### P0 — Data safety and storage-provider hardening

1. **Complete mutation transactions.** Every create, rename, delete, copy, cut, paste, duplicate, export, and project move must expose a processing state, verify the postcondition, and report whether the final state is complete, partial, or rolled back.
2. **Provider matrix validation.** Validate against Android internal storage, shared external storage, a document-provider tree, cloud-backed providers where available, and Termux-accessible locations. Native `moveDocument` support and provider-specific URI behavior differ materially.
3. **Rollback guarantees.** If a copy succeeds but source deletion fails, retain the source and remove the destination only when safe. If rollback itself fails, show both locations and an actionable recovery instruction instead of claiming failure without context.
4. **Permission-loss recovery.** A revoked SAF grant must produce a clear reauthorization flow, preserve the registry entry, and avoid silently opening an empty or invalid project.
5. **Concurrent-operation guard.** Disable conflicting mutations while one operation is active, prevent double-submit, and serialize operations affecting the same URI.
6. **Large-tree and large-file limits.** Add cancellation, bounded traversal, progress reporting, and clear limits for huge projects. Recursive verification must not freeze the UI or exhaust memory.

### P1 — Workspace and editor reliability

1. **Workspace restoration contract.** Restore tabs only after the project tree and permissions are confirmed. Missing files should become recoverable “missing tab” entries rather than silently disappearing.
2. **Project URI migration.** When a provider changes a URI after rename or move, update open tabs, workspace metadata, crash recovery entries, project registry records, and cached details atomically.
3. **Dirty-state correctness.** Saving, autosave, closing, switching projects, moving projects, and process death must preserve or clearly resolve dirty buffers. A failed save must never clear the dirty marker.
4. **Editor lifecycle hardening.** Cover WebView recreation, renderer death, process death, project switching, orientation/configuration changes, keyboard appearance, and editor readiness races.
5. **Undo/redo and save conflict policy.** Define behavior when the underlying file changes outside the IDE, including reload, overwrite, diff, and conflict prompts.
6. **Accessibility and touch quality.** Verify minimum touch targets, content descriptions, keyboard navigation where applicable, focus order, contrast, text scaling, and narrow-screen behavior.

### P1 — Project management completeness

1. **Creation destination correctness.** New projects must always request or use an explicit user-approved external destination; no silent app-internal fallback should remain if the product requirement is user-owned storage.
2. **Display-name semantics.** Continue treating registry rename as a display-name operation unless physical folder rename is explicitly requested. The UI must make that distinction unambiguous.
3. **Project details consistency.** Details, search, sorting, registry, and file tree must all use the same current URI and display-name state after moves, renames, and provider URI changes.
4. **Import/open resilience.** Importing a project with no metadata, malformed metadata, a missing README, or a partially initialized `.androidide` directory must remain safe and explain the repair option.
5. **Export/import round trip.** Exported archives need a defined root layout, metadata inclusion policy, path traversal protection, cancellation, progress, and a tested re-import path.

### P2 — Release quality

1. Run Android lint and the smallest relevant compile checks in GitHub Actions on every change.
2. Add unit tests for URI normalization, exact-name collision handling, mutation result classification, metadata migration, and workspace serialization.
3. Add instrumented tests or a documented manual matrix for SAF providers and Android API levels.
4. Add crash-safe diagnostics that do not leak file contents, credentials, or full private paths.
5. Test release APK installation, first launch, offline Monaco loading, project creation, editing, saving, reopening, and upgrade migration.
6. Replace documentation claims of “100% complete” with milestone-specific status and explicit known limitations.

## Godlove’s phone-first developer workflow: remaining phases

### Phase 2 — Integrated runtime

This phase should deliver the one-tap terminal and process model Godlove needs:

- Compose terminal tabs tied to project roots.
- PTY sessions with resize, input, output buffering, interrupt, and exit status.
- A persistent foreground service or equivalent Android-safe host for long-running processes.
- Process/session persistence across tab changes and configuration changes.
- Port discovery and localhost preview routing.
- Start, stop, restart, and logs controls for live servers.
- Background execution policy that clearly handles Android battery and process restrictions.
- Package/bootstrap management appropriate to Android rather than assuming a desktop Linux installation.

Closing a UI tab must not implicitly kill a process unless the user explicitly chooses “stop session.”

### Phase 3 — Integrated Git

The current project details code reads Git metadata, but that is not Git integration. Production Git requires status, staging, commit, branch switching, history, diff review, clone, fetch, pull, push, conflict handling, credential storage, and safe cancellation. Git operations must share the same mutation transaction and progress model as file operations.

### Phase 4 — Language intelligence

LSP process lifecycle, diagnostics, completion, navigation, hover, references, and code actions should be added after the terminal/process foundation exists. Servers must be project-scoped, cancellable, permission-aware, and compatible with Android resource limits.

### Phase 5 — Extensibility and customization

Extensions should be treated as untrusted code. The product needs a package format, permission model, storage quotas, lifecycle management, version compatibility, and a safe update/uninstall path. User customization should cover keybindings, editor profiles, file-tree visibility, themes, preview behavior, terminal profiles, and project defaults without allowing arbitrary extension code to bypass Android security.

## Recommendation

Do not call Phase 1 production-complete yet. Call it **“Phase 1 foundation implemented; production hardening in progress.”** Close the P0 storage/data-safety gate and the P1 editor/workspace gate first. Once those are complete and verified on real Android devices and multiple storage providers, Phase 1 can be considered production-ready. Then proceed to the terminal/process phase, because that is the capability that most directly turns the current editor into Godlove’s single phone-based developer workspace.

The repository’s current architecture can support this direction, but the terminal and persistent-process design should be introduced as explicit services and session models rather than added as another short-lived screen inside the existing ViewModel.

## Current local implementation changes

This review also adds configurable visibility for `.git`, `.androidide`, and `README.md`, with `.git` and `.androidide` hidden by default and `README.md` visible by default. The settings are persisted and the current file tree refreshes when the preferences change.

These changes are intentionally local and have not been pushed.

<!-- End of review -->
