# Python Dependabot Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the pinned Python dependency manifest under the same automated version-update monitoring coverage already used for Gradle, Go modules and GitHub Actions.

**Architecture:** Keep `tools/requirements.txt` and all dependency versions unchanged. Add one narrowly scoped Dependabot `pip` entry for `/tools`, and pin that invariant with the existing Python CI policy test.

**Tech Stack:** GitHub Dependabot v2 configuration, Python `unittest`, existing Gradle/Go/artifact/release policy gates.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- Preserve canonical branch `feature/ws024-secure-tunnel-20260728` at `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- Do not upgrade PyYAML or any dependency/tool/action version.
- Do not change workflow permissions, build/runtime code, portal/profile policy, signing/TLS behavior or release gates.
- No APK installation/launch, device control, portal interaction, credentials, certificates, signatures, upload, payment or submission.
- Evidence documents change only after fresh verification.

---

### Task 1: Pin Python version-update monitoring coverage

**Files:**
- Modify: `tools/tests/test_ci_policy.py`
- Modify: `.github/dependabot.yml`

**Interfaces:**
- Consumes: existing `tools/requirements.txt` and Dependabot v2 configuration.
- Produces: one weekly `pip` update-monitoring entry scoped to `/tools`.

- [ ] **Step 1: Write the failing policy regression**

Strengthen `test_dependabot_covers_all_package_managers` to require exactly one
`package-ecosystem: "pip"` entry and the `/tools` directory while preserving the
existing exact-count assertions for Gradle, Go modules and GitHub Actions.

- [ ] **Step 2: Run RED**

```bash
python3 -m unittest \
  tools.tests.test_ci_policy.CiPolicyTest.test_dependabot_covers_all_package_managers -v
```

Expected: FAIL because the current Dependabot config contains zero pip entries.

- [ ] **Step 3: Add the minimum Dependabot block**

Add exactly one block:

```yaml
  - package-ecosystem: "pip"
    directory: "/tools"
    schedule:
      interval: "weekly"
      day: "monday"
    open-pull-requests-limit: 5
```

Do not alter the existing three ecosystem blocks or `tools/requirements.txt`.

- [ ] **Step 4: Run focused GREEN**

Rerun the exact policy test, then the complete `tools.tests.test_ci_policy` module.

### Task 2: Full verification, evidence and remote integration

**Files:**
- Update after verification: `docs/autonomous/2026-08-04-audit-ledger.md`
- Update after verification: `docs/security-roadmap.md`
- Update after verification: `docs/test-report.md`
- Update after verification: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

**Interfaces:**
- Consumes: final Dependabot/test/spec/plan diff.
- Produces: fresh verification evidence and one pushed atomic milestone commit.

- [ ] **Step 1: Run full gates**

Run resolved-core/AAPT2/runtime-lock checks, complete Debug/QA JVM suites,
Debug/QA/QA-AndroidTest assemblies, lint, Python discovery, Go test/vet/build,
Android APK artifact checks and release-without-private-signing fail-closed. Remove the
generated relay binary and require zero release APKs.

- [ ] **Step 2: Review exact scope**

Require `tools/requirements.txt` unchanged. Inspect the complete diff, run
`git diff --check`, and scan for unpinned workflow actions, widened permissions,
dynamic dependency versions, sensitive data and unrelated runtime changes.

- [ ] **Step 3: Record observed evidence**

Update only the four evidence documents with exact RED/GREEN/full-gate jobs, counts,
artifact hashes, limitations and prohibited-action confirmation. Do not claim a hosted
Dependabot run.

- [ ] **Step 4: Commit and push**

Stage only milestone files, rerun staged whitespace/supply-chain/sensitive scans,
create one atomic commit, push to
`origin/agent/workspace-47-autonomous-20260803`, fetch, and verify exact remote SHA,
divergence `0/0`, clean worktree and immutable canonical SHA.
