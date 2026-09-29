# Session Plan: Create New Product-Led Templates

**Branch:** `dev`
**Scope:** Implement the accepted Create New template flow and replace generic starter copy with product-led Android IDE onboarding.

## Product direction

The generated README must sell and explain Android IDE in the same product-led spirit as the connected Quiet Mails README: describe what Android IDE is, what it helps the user do, and how the user continues the project with Android IDE. It must not be a generic package-manager dump or only an attribution block.

## Create New templates

Create New is separate from existing-folder loading, ZIP import, and Git acquisition. It offers:

- Start from scratch
- Node.js application
- npm package
- pnpm package
- Node.js HTTP server
- Static web project

## Acceptance criteria

- The user selects a template before reviewing project creation.
- The selected template controls generated files, package manager, scripts, and README.
- Every generated README explains Android IDE, how to continue in Android IDE, and the template's next steps.
- Generated files match the commands and paths described in the README.
- Start from scratch creates only the root README, `.gitignore`, and IDE metadata.
- No build, compilation, lint, or tests are run for this change.
- No commit or push is performed.

## Implementation and re-audit result

Create New now exposes the six approved starter choices and passes the selected template through the UI, ViewModel, and acquisition service. The generator creates template-specific files and scripts, while the root README explains Android IDE as the mobile development workspace and directs the user to continue the project in Android IDE. Start from scratch creates no package-manager files. The generated source paths match the README instructions, and the server/static starter contents were corrected to contain runnable-looking source rather than escaped placeholder text.

The source-only audit confirmed template wiring, README product copy, generated file paths, and removal of the former generic single-template content. `git diff --check` passed. No build, compilation, lint, or tests were run. No commit or push was performed.
