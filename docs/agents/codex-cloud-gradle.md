# Codex Cloud Gradle policy — historical

> **Deprecated for current development.** This file preserves the former Codex Cloud execution boundary for historical references. The current canonical verification policy is `docs/agents/github-actions-verification.md`.

Current agents must use GitHub Actions as the broad pull-request gate. Narrow local development tests are allowed when useful. Do not invoke `w47-cloud` or `codex cloud` as the default or automatic fallback; use Codex Cloud only when the operator explicitly requests it for a specific task.

Historical Cloud task IDs, the saved `workspace-47-android` environment, and `tools/w47-cloud` remain valid historical evidence/tooling but do not define current acceptance.
