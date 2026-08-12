# Workspace-47 context

- **Autonomous branch**: the integration branch `agent/workspace-47-autonomous-20260803`; the canonical source branch is not mutated by autonomous work.
- **Orchestrator**: ChatGPT Watchdog coordinates evidence, isolated workers, integration, and publication.
- **Portal worker**: one native Codex implementation worker owning one isolated worktree/branch for one bounded portal candidate.
- **Cloud Gradle**: every agent-initiated Gradle/Android verification runs in the saved `workspace-47-android` Codex Cloud environment against an exact pushed SHA.
- **Matt workflow**: the Matt Pocock engineering skills routed by `codex/ask-matt`; repository policy is in `docs/agents/matt-pocock-workflow.md`.
