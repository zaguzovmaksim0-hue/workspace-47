# Workspace-47 context

- **Stable main**: `origin/main` is the stable public default branch. Start each bounded change on a fresh branch from the current `origin/main`; integrate only through a pull request after the applicable checks pass on the exact candidate commit.
- **Autonomous lifecycle**: ChatGPT Watchdog coordinates one bounded candidate at a time, records exact-SHA evidence, merges only through the pull request, then fetches the new `origin/main` before starting the next independent branch.
- **Historical work**: prior autonomous integration branches and their plans remain historical evidence, not continuation bases for current development.
- **Cloud Gradle**: every agent-initiated Gradle/Android verification runs in the saved `workspace-47-android` Codex Cloud environment against an exact pushed SHA.
- **Matt workflow**: the Matt Pocock engineering skills routed by `codex/ask-matt`; repository policy is in `docs/agents/matt-pocock-workflow.md`.
