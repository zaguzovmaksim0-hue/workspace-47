#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cat >&2 <<'MESSAGE'
ERROR: This legacy source-publication runner is retired.

It encoded the 2026-08-12 pre-publication branch/cutoff and ran Android/Gradle
inside native Termux. That execution model is no longer valid for current
stable-main development.

Use the current policies instead:
  - branch/PR lifecycle: CONTEXT.md and CONTRIBUTING.md
  - verification: docs/agents/github-actions-verification.md
  - broad Android/Python/Go/security gate: repository GitHub Actions on the
    exact pull-request head SHA
  - physical certificate/authentication E2E: separate operator-controlled
    device acceptance when required

Do not automatically fall back to a full phone-local Gradle gate or Codex Cloud
when GitHub Actions is unavailable. Diagnose/report the CI blocker unless the
operator explicitly authorizes a different incident fallback.
MESSAGE
exit 2
