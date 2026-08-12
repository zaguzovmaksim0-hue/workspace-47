# Autonomous branch CI coverage design

## Finding

The autonomous audit contract requires every completed milestone to be pushed to
`agent/workspace-47-autonomous-20260803`. Both repository GitHub Actions workflows currently
run on pushes to `main` and `feature/**`, but not `agent/**`. Therefore a verified autonomous
commit can be present on the remote branch without triggering either the full CI workflow or the
security workflow on that push. Pull-request execution is not an adequate substitute for this
autonomous branch because the task explicitly prohibits automatically creating a PR.

The existing Python CI policy verifies immutable action SHAs, read-only permissions, gate
contents and supply-chain controls, but does not assert branch-trigger coverage.

## Scope

Workflow behavior:
- `.github/workflows/ci.yml`
- `.github/workflows/security.yml`

Policy regression:
- `tools/tests/test_ci_policy.py`

Evidence after verification:
- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No Android runtime, network, WebView, certificate, signing, portal profile, dependency version,
workflow permission, action pin, schedule, job command or release policy changes.

## Required behavior

1. Pushes to `main`, `feature/**` and `agent/**` must trigger the ordinary CI workflow.
2. Pushes to `main`, `feature/**` and `agent/**` must trigger the security workflow.
3. Existing `pull_request` behavior remains unchanged.
4. The security workflow's weekly schedule remains unchanged.
5. Both workflows retain `permissions: contents: read`, SHA-pinned allowed actions and
   `persist-credentials: false`.
6. No new secrets, write permissions, artifact uploads or external actions are introduced.

## Selected approach

Add one branch glob, `agent/**`, next to the existing `feature/**` entry in each workflow. Add a
single policy test that reads both workflow files and requires the three expected push branch
entries. This is narrower than broadening pushes to every branch and keeps the current branch
allowlist explicit.

Rejected alternatives:
- Trigger on every branch: unnecessarily broad and increases CI load for arbitrary personal or
  temporary branches.
- Depend only on `pull_request`: does not cover the task's mandatory autonomous remote pushes and
  a PR is explicitly not created automatically.
- Add a separate autonomous-only workflow: duplicates existing gates and creates drift risk.

## Verification strategy

- First add only the CI policy test and run that exact test against unchanged workflows; require
  RED because `agent/**` is absent.
- Add `agent/**` to both workflow push branch allowlists and rerun the exact test GREEN.
- Run the complete Python policy/discovery suite.
- Parse/check workflow text through the existing policy invariants: read-only permissions,
  immutable action SHA pins, checkout credential suppression, expected jobs and schedule.
- Run all relevant current repository gates before commit/push. The workflow change itself is not
  claimed executed server-side until GitHub receives the resulting `agent/**` push; local evidence
  establishes syntax/policy and the commands referenced by the workflows.
- Review exact diff, whitespace, sensitive-data and unsafe security patterns, then commit and push
  atomically and verify the exact remote SHA.
