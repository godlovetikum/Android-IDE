# Session Plan: Individual Acquisition Components

**Branch:** `dev`  
**Scope:** Replace the shared acquisition-modal markup with separate, intentionally minimal components for each project-acquisition workflow.

## Accepted behavior

- Create New, Load an existing project, Import ZIP project, and Clone Git repository each have an independent composable component.
- Components contain only the workflow title, necessary inputs, concise feedback, and action buttons.
- No copy describes the UI as intentionally empty or explains that it is space-free.
- Folder, archive, and destination locations remain hidden until the user has selected them.
- Once a location exists, it is rendered through the readable storage-location formatter and the picker action changes to “Choose another location.”
- Create New remains a two-step flow: inputs/template selection, then review.
- Existing-folder loading derives the project name from the selected folder and only asks for a description.
- ZIP import keeps archive selection, project details, and destination selection focused in its own component.
- Git clone has its own repository URL input and preserves the honest coming-soon feedback because the clone service is not wired.
- Existing acquisition ViewModel callbacks and completion/open-project behavior are preserved.

## Implementation result and re-audit

`AcquisitionDialogs.kt` now contains the four individual acquisition components and shared minimal picker/status primitives. `AppRoot.kt` owns only workflow state, launchers, and callback wiring; the former shared acquisition-dialog blocks and obsolete verdict/feedback helpers were removed. A source-only audit confirmed that no forbidden filler copy or redundant selected-location labels remain, picker values are conditionally rendered, and every component is wired from its corresponding project-list action. No build, compilation, lint, or tests were run. No commit or push was performed.
