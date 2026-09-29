
## Re-audit result

The source-only audit confirms that project summaries remain absent from state until inspection completes, so cards render `Loading project details…`; only completed failures use unavailable wording. Git is rendered as a themed badge, and the first available attention condition is rendered as a single additional badge rather than a status dump. Refresh progress is represented separately from blocking operations so the list can expose progressive loading. Project Details now uses grouped Identity, Location, Contents, Capabilities, and Git sections, including metadata state, readable storage path, creation/last-modified times, language totals, capability explanations, branch count, and sanitized remote URLs without commits or diffs. `git diff --check` passed, no build/test artifacts were created, and no build, compilation, lint, or tests were run.

## Follow-up audit and correction — 2026-09-29

The project-list audit found that `refreshProjects()` launched summary inspection separately and returned immediately, allowing the refresh indicator to clear while metric cards were still loading. Summary inspection is now awaited within the refresh operation, with generation checks preventing stale concurrent results from replacing newer state and a `finally` block clearing the refresh indicator after success or failure.

Cards now show independent `Files loading…` and `Size loading…` states while summaries are absent. Once inspection completes, unavailable values are shown only when the details service actually returns an unavailable result. The unavailable reason is preserved, but the list presents only one concise priority badge: permission, provider support, location, inspection, or a generic attention badge. Git remains a separate themed badge. No build, compilation, lint, or tests were run; no commit or push was performed.
