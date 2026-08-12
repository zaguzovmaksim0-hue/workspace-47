#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cat >&2 <<'MESSAGE'
ERROR: This legacy source-publication runner is retired.

It encoded the 2026-08-12 pre-publication branch/cutoff and ran Android/Gradle
inside native Termux. That execution model is no longer valid for current
stable-main development.

Use the current policies instead:
  - branch/PR lifecycle: CONTEXT.md and CONTRIBUTING.md
  - Android/Gradle execution: docs/agents/codex-cloud-gradle.md
  - exact pushed Android candidate gate:
      $HOME/bin/w47-cloud full --branch BRANCH --sha SHA

For a future release/publication milestone, run only individually reviewed
non-Gradle release/security checks plus the approved Cloud Android gate until a
new current release orchestrator is reviewed and merged.
MESSAGE
exit 2
