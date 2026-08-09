# Matt Pocock engineering workflow

This is the repository's engineering-skill source of truth.

## Entry

For a non-trivial local task, call `prepare_task` once. Automatic skill selection is Matt-Pocock-only. If no skill is automatically selected, use `skill_search` + `skill_read` explicitly rather than falling back to another workflow family.

Use `codex/ask-matt` when the route is unclear. It is the router for the installed Matt Pocock skill set.

## Main routes

- New or changed behavior: `codex/implement`; use `codex/tdd` at a real behavioral seam.
- Hard bug, regression, flaky test, or performance failure: `codex/diagnosing-bugs`.
- Agent-facing instructions, `AGENTS.md`, or skills: `codex/writing-for-agents`.
- Review before integration: `codex/code-review`.
- External/primary-source reading that should leave durable evidence: `codex/research` when applicable.
- Larger ambiguous work: route through `codex/ask-matt` to the appropriate grill/spec/tickets flow.

## Parallel workers

Native Codex multi-agent fan-out may use up to the configured worker limit. Each implementation worker owns one isolated writable Git worktree/branch. Workers do not share writable worktrees. The orchestrator owns integration and publication.

Each worker commits and pushes its candidate branch before any Gradle verification. Gradle verification then runs in Codex Cloud against the exact pushed SHA according to `docs/agents/codex-cloud-gradle.md`.

## Legacy plans

The directory name `docs/superpowers/` is historical and may remain in paths. Any line in a legacy plan that says a `superpowers:*` skill is required is superseded by this document for current work. Preserve historical evidence; do not mass-rewrite old completed plans solely to rename the workflow.
