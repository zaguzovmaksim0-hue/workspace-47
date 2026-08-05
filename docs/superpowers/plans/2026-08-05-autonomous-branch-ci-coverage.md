# Autonomous Branch CI Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure mandatory autonomous `agent/**` pushes trigger both repository CI and security workflows.

**Architecture:** Preserve the existing explicit GitHub Actions branch allowlist and add only the autonomous branch namespace to both workflows. Guard that contract in the existing Python CI policy suite so future workflow edits cannot silently remove autonomous push coverage.

**Tech Stack:** GitHub Actions YAML, Python 3 `unittest`, existing Gradle/Android, Go and shell verification gates.

## Global Constraints

- Keep workflow permissions exactly `contents: read`.
- Keep all action references pinned to existing 40-character commit SHAs.
- Keep `persist-credentials: false` on checkout.
- Do not change job commands, schedules, dependency/tool versions or release-signing policy.
- Do not add secrets, write permissions, artifact uploads or `pull_request_target`.
- Canonical branch remains immutable at `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

---

### Task 1: Reproduce missing autonomous push coverage

**Files:**
- Modify: `tools/tests/test_ci_policy.py`

**Interfaces:**
- Consumes: `CI`, `SECURITY`, and `CiPolicyTest.read()` already defined in the policy suite.
- Produces: `CiPolicyTest.test_workflows_cover_autonomous_push_branches`.

- [ ] **Step 1: Add the failing policy regression**

Add a test that iterates over `(CI, SECURITY)` and requires all three branch entries:

```python
def test_workflows_cover_autonomous_push_branches(self) -> None:
    for path in (CI, SECURITY):
        source = self.read(path)
        for branch in ("main", "feature/**", "agent/**"):
            self.assertIn(f"      - {branch}\n", source, f"missing push branch {branch} in {path.name}")
```

- [ ] **Step 2: Run exact RED**

Run:

```bash
python -m unittest tools.tests.test_ci_policy.CiPolicyTest.test_workflows_cover_autonomous_push_branches
```

Expected: FAIL because `.github/workflows/ci.yml` does not contain `      - agent/**` on unchanged production/workflow content.

### Task 2: Add the minimum workflow trigger coverage

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/security.yml`

**Interfaces:**
- Consumes: existing `on.push.branches` allowlists.
- Produces: `agent/**` push coverage in both workflows without changing any job or permission.

- [ ] **Step 1: Add one branch glob to each workflow**

Change each push branch block from:

```yaml
    branches:
      - main
      - feature/**
```

to:

```yaml
    branches:
      - main
      - feature/**
      - agent/**
```

- [ ] **Step 2: Run exact GREEN**

Run:

```bash
python -m unittest tools.tests.test_ci_policy.CiPolicyTest.test_workflows_cover_autonomous_push_branches
```

Expected: PASS.

- [ ] **Step 3: Run complete CI policy and Python discovery**

Run:

```bash
python -m unittest tools.tests.test_ci_policy
python -m unittest discover -s tools/tests -p 'test_*.py'
```

Expected: PASS with only the already documented environmental hardlink skip if present.

### Task 3: Full verification, evidence and remote completion

**Files:**
- Modify: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/test-report.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

**Interfaces:**
- Consumes: observed RED/GREEN/full-gate evidence.
- Produces: durable evidence for the autonomous branch CI trigger milestone.

- [ ] **Step 1: Run relevant full repository gates**

Run the established pin/unit/assembly, forced lint, Python, Android artifact, release fail-closed,
and Go test/vet/build gates. Remove any generated relay binary and require zero release APKs.

- [ ] **Step 2: Review exact diff and security invariants**

Require `git diff --check`, exact expected dirty paths, no added secrets/personal data, no workflow
write permissions, no unpinned actions, no `pull_request_target`, and no unrelated changes.

- [ ] **Step 3: Update evidence only from observed results**

Record exact RED/GREEN jobs, full-gate results and workflow scope in the four evidence documents.
Do not alter the threat model because this milestone changes CI execution coverage rather than an
application trust boundary.

- [ ] **Step 4: Commit and push atomically**

Fetch and recheck branch/upstream/canonical state, stage only the exact milestone surface, run
cached whitespace/policy scans, commit once, push without force to
`origin/agent/workspace-47-autonomous-20260803`, fetch again and require exact remote/local SHA
with divergence `0/0` and canonical SHA unchanged.
