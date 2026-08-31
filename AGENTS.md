# Workspace-47 agent instructions

## Agent skills

Use the Matt Pocock engineering workflow for planning, implementation, debugging, research, and review. See `docs/agents/matt-pocock-workflow.md`.

Legacy `superpowers:*` requirements in historical plans are superseded by that workflow. They are not automatic or mandatory for this repository unless the operator explicitly requests them.

## Verification / Android / Gradle

GitHub Actions is the canonical broad verification environment. See `docs/agents/github-actions-verification.md`.

Agents may use narrowly scoped local tests during implementation when they shorten the RED/GREEN loop, but must not routinely run the full Android unit/lint/assembly gate on the phone. Commit and push the candidate, open/update the pull request, and use the GitHub Actions checks on the exact PR head SHA as the required broad integration evidence.

Codex Cloud and `w47-cloud` are not the default path and are not an automatic fallback. Use them only when the operator explicitly requests that environment for a specific task. If GitHub Actions is unavailable, diagnose/report the blocker instead of silently moving the full gate to the phone or Codex Cloud.

## Agent metadata

GitHub is the issue tracker. Domain documentation is single-context. The current branch/PR lifecycle is defined in `CONTEXT.md`; public contribution expectations are in `CONTRIBUTING.md`. See `docs/agents/issue-tracker.md`, `docs/agents/triage-labels.md`, and `docs/agents/domain.md`.
