# QA_WORKFLOW.md — Android IDE

## Purpose

This document is a **mandatory operating procedure** for all future development sessions. Every modification to the Android IDE codebase must follow this workflow without exception.

The purpose of this workflow is to:
- Reduce AI context consumption
- Reduce token usage
- Improve recovery after interruptions
- Allow continuation from partial work
- Maintain long-term codebase health

---

## Mandatory Development Workflow

### Step 1 — Session Initialization

At the beginning of every development session:

1. Read `STATUS_TRACKER.md` — understand current project state
2. Read `README.md` (PROJECT_PLAN.md) — confirm the active phase and next task
3. Identify **exactly one** target subsystem to work on

**Allowed subsystem targets:**
- `saf` — SAF file operations (`saf/SafRepository.kt`)
- `editor` — Monaco bridge (`editor/EditorBridge.kt`, `editor/EditorMessage.kt`)
- `viewmodel` — IDE state management (`viewmodel/IdeViewModel.kt`, `viewmodel/model/`)
- `ui` — Compose screens and components (`ui/IdeScreen.kt`, `ui/components/`)
- `ui/theme` — Colors, typography, Material3 theme (`ui/theme/`)
- `terminal` — Terminal UI + PTY (Phase 2, not yet started)
- `linux-runtime` — proot environment (Phase 2, not yet started)
- `git` — Git operations (Phase 3, not yet started)
- `lsp` — Language server client (Phase 4, not yet started)
- `extensions` — Extension loader (Phase 5, not yet started)

**Do NOT:**
- Select multiple subsystems simultaneously
- Begin with a full repository review
- Load all source files

---

### Step 2 — Targeted File Reading

Read **only** the files directly related to the active subsystem:

- The subsystem's main Kotlin file(s)
- Any entries in `DEBUG_LOG.md` pertaining to this subsystem
- The current `STATUS_TRACKER.md` task entry

**Never:**
- Scan the entire repository
- Load all source files
- Perform full-project reviews (unless explicitly requested by the project owner)
- Read files from unrelated subsystems

If a dependency on another subsystem is encountered, read only that subsystem's **public API surface** (its exported types and functions), not its internal implementation.

---

### Step 3 — Produce a Change Plan

Before writing any code, document the intended change:

1. State the subsystem being modified
2. List files that will be read and why
3. Describe the specific change in plain language
4. Identify risks and unknowns
5. Confirm the change is within scope of a single subsystem

Changes that span more than two subsystems must be split into separate tasks.

---

### Step 4 — Execute the Change

Implement the change according to the plan.

Rules during execution:
- Make only the changes documented in Step 3
- Do not perform opportunistic refactors
- Do not add features beyond scope
- If a new issue is discovered, **stop**, document it in `DEBUG_LOG.md`, and add it to `STATUS_TRACKER.md` as a future task
- Never rewrite large sections of code without explicit justification documented in the plan

---

### Step 5 — Document the Change

After implementation:

1. If a bug was fixed: add an entry to `DEBUG_LOG.md`
2. If an architectural decision was made: add it to `README.md` under Architecture Decisions
3. If a new file was created: add a header comment to the file explaining its purpose
4. If a new dependency was added: add it to `app/build.gradle.kts` with a comment explaining the reason

---

### Step 6 — Update Tracking Records

1. Update `STATUS_TRACKER.md`:
   - Move the completed task from "In Progress" to "Completed"
   - Move the next task to "In Progress"
2. Update `README.md` progress table if a phase milestone was reached

---

### Step 7 — Stop

Do not:
- Begin the next task in the same session without reading `STATUS_TRACKER.md` again
- Attempt to fix unrelated issues discovered during the session
- Perform any unplanned work

---

## Incremental Development Rules

### Rule 1 — One Subsystem at a Time
Never implement multiple major subsystems simultaneously. Only one subsystem may be actively developed in a single session.

### Rule 2 — No Broad Rewrites
Never rewrite large sections of the codebase without explicit justification documented in the change plan.

### Rule 3 — No Broad Refactors
Refactors must be isolated, scoped to a single package, and documented.

### Rule 4 — Document Every Decision
Every architectural decision must be recorded in `README.md` before implementation begins.

### Rule 5 — Justify Every Dependency
Every new `implementation()` dependency in `app/build.gradle.kts` requires a comment explaining the reason.

### Rule 6 — Header Comment on Every File
Every new Kotlin file must have a header comment stating its purpose, what it replaces (if anything), and key design decisions.

### Rule 7 — No Placeholder Code
Do not create stub implementations intended to be rewritten. If a production implementation is not yet feasible, document the architectural interface and leave the body with a clear `TODO("link to STATUS_TRACKER task #N")` comment.

### Rule 8 — No Mock Implementations
Avoid mock implementations except when explicitly requested by the project owner.

### Rule 9 — Assume Continuation
All work must be self-documenting. Assume another engineer (or AI session) may continue development at any point with no context from previous sessions other than the documentation in this repository.

---

## Repository Scanning Policy

**Large-scale repository scanning is prohibited during normal development sessions.**

Exceptions (require explicit project owner approval):
- Architectural audit requested by the project owner
- Security review
- Dependency upgrade affecting all modules

---

## Context Budget Guidelines

| Operation | Allowed |
|-----------|---------|
| Read STATUS_TRACKER.md | Always |
| Read README.md | Always |
| Read active subsystem Kotlin file(s) | Always |
| Read dependency subsystem public API | When needed |
| Read DEBUG_LOG.md for active subsystem | When debugging |
| Read all files in repository | Prohibited without explicit approval |

Last updated: 2026-06-12
