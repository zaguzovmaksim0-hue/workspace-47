# Workspace-47 agent instructions

## Agent skills

Use the Matt Pocock engineering workflow for planning, implementation, debugging, research, and review. See `docs/agents/matt-pocock-workflow.md`.

Legacy `superpowers:*` requirements in historical plans are superseded by that workflow. They are not automatic or mandatory for this repository unless the operator explicitly requests them.

## Android / Gradle execution

Every Gradle command initiated by ChatGPT Watchdog, Codex, or a subagent runs in the saved Codex Cloud environment. See `docs/agents/codex-cloud-gradle.md`.

The Android phone is an orchestrator, not a Gradle build host. Push an isolated worker branch, capture its exact SHA, and use `$HOME/bin/w47-cloud`. A local Gradle incident fallback requires explicit operator authorization for that incident.

## Agent metadata

GitHub is the issue tracker. Domain documentation is single-context. See `docs/agents/issue-tracker.md`, `docs/agents/triage-labels.md`, and `docs/agents/domain.md`.
