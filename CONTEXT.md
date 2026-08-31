# Workspace-47 context

- **Stable main**: `origin/main` is the stable public default branch. Start each bounded change on a fresh branch from the current `origin/main`; integrate only through a pull request after the applicable checks pass on the exact candidate commit.
- **Autonomous lifecycle**: ChatGPT Watchdog coordinates one bounded candidate at a time, records exact-SHA evidence, merges only through the pull request, then fetches the new `origin/main` before starting the next independent branch.
- **Historical work**: prior autonomous integration branches and their plans remain historical evidence, not continuation bases for current development.
- **GitHub Actions verification**: broad Android/Gradle, emulator instrumentation, Python, Go and security acceptance runs through the repository GitHub Actions workflows on the exact pushed PR-head SHA. Focused local development checks are allowed when useful; full phone-local Gradle and Codex Cloud are not automatic fallbacks. See `docs/agents/github-actions-verification.md`.
- **Physical E2E**: emulator CI does not replace operator-controlled physical-device acceptance for real certificate/provider/authenticated flows when such evidence is required.
- **Matt workflow**: the Matt Pocock engineering skills routed by `codex/ask-matt`; repository policy is in `docs/agents/matt-pocock-workflow.md`.
