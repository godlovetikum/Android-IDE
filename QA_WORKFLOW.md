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
2. Read `PROJECT_PLAN.md` — confirm the active phase and next task
3. Identify **exactly one** target subsystem to work on

**Allowed subsystem targets (examples):**
- `filesystem` — file tree refresh
- `editor` — Monaco theme switching
- `terminal` — scrollback buffer
- `git` — clone dialog
- `linux-runtime` — proot bootstrap
- `lsp` — diagnostic rendering
- `extensions` — extension loader
- `settings` — TOML serialization

**Do NOT:**
- Select multiple subsystems simultaneously
- Begin with a full repository review
- Load all source files

---

### Step 2 — Targeted File Reading

Read **only** the files directly related to the active subsystem:

- The subsystem's `src/modules/<subsystem>/mod.rs`
- The subsystem's `README.md`
- Any files in `DEBUG_LOG.md` pertaining to this subsystem
- The current `STATUS_TRACKER.md` task entry

**Never:**
- Scan the entire repository
- Load all source files
- Perform full-project reviews (unless explicitly requested by the project owner)
- Read files from unrelated subsystems

If a dependency on another subsystem is encountered, read only that subsystem's **public API surface** (its `mod.rs` exports), not its internal implementation.

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
2. If an architectural decision was made: add it to `PROJECT_PLAN.md` under Architecture Decisions
3. If a new file was created: document its purpose in the relevant module's `README.md`
4. If a new dependency was added: record the justification in the module's `README.md`

---

### Step 6 — Update Tracking Records

1. Update `STATUS_TRACKER.md`:
   - Move the completed task from "In Progress" to "Completed"
   - Move the next task to "In Progress"
   - Update phase completion percentage
2. Update `PROJECT_PLAN.md` progress table if a phase milestone was reached

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
Refactors must be isolated, scoped to a single module, and documented.

### Rule 4 — Document Every Decision
Every architectural decision must be recorded before implementation begins.

### Rule 5 — Justify Every Dependency
Every new `Cargo.toml` dependency requires a written justification in the relevant module's `README.md`.

### Rule 6 — Document Every File
Every new file must have a header comment stating its purpose.

### Rule 7 — Module READMEs
Every module must maintain a `README.md` explaining:
- Purpose
- Responsibilities
- Public API surface
- Dependencies (and why)
- Known limitations

### Rule 8 — No Placeholder Code
Do not create stub implementations intended to be rewritten. If a production implementation is not yet feasible, document the architectural interface and leave the body unimplemented with a clear `todo!()` macro and a comment linking to the relevant task in `STATUS_TRACKER.md`.

### Rule 9 — No Mock Implementations
Avoid mock implementations except when explicitly requested by the project owner.

### Rule 10 — Assume Continuation
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
| Read PROJECT_PLAN.md | Always |
| Read active subsystem mod.rs | Always |
| Read active subsystem README.md | Always |
| Read dependency subsystem public API | When needed |
| Read DEBUG_LOG.md for active subsystem | When debugging |
| Read all files in repository | Prohibited without explicit approval |

Last updated: 2026-06-10
