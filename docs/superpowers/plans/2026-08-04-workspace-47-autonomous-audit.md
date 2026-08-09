# Workspace-47 Autonomous Audit Implementation Plan

> **Agent workflow:** follow `docs/agents/matt-pocock-workflow.md` for this master plan.
> Use Matt Pocock `codex/implement`/`codex/tdd` for behavior changes,
> `codex/diagnosing-bugs` for failures, and `codex/code-review` before integration.
> All Gradle verification follows `docs/agents/codex-cloud-gradle.md`.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute a 12-hour confirmed-active-time autonomous audit and remediation
cycle over Junta Firma Mobile without physical-device or authenticated-portal
actions.

**Architecture:** A durable audit ledger controls successive narrow milestones.
Each milestone derives from reproducible evidence, receives its own exact-file
sub-plan before production mutation, follows RED → GREEN, passes focused and
relevant full gates, and is pushed atomically to the isolated branch. Rolling
Chats carries exact state between 40-minute generations.

**Tech Stack:** Kotlin, Jetpack Compose/Material 3, AndroidX WebKit, JCA and
BouncyCastle, OkHttp, XMLSec, Gradle 9.4.1, Python `unittest`, Go 1.26.5, GitHub
Actions, Gitleaks, OSV-Scanner, and govulncheck where available.

## Binding priority amendment — 2026-08-09: Portal Coverage First

The original task ordering below is retained as historical structure, but its scheduling priority is
superseded by `docs/superpowers/specs/2026-08-09-portal-coverage-first-autonomous-priority-design.md`
and `docs/superpowers/plans/2026-08-09-portal-coverage-first-autonomous-priority.md`.

While public-evidence portal candidates remain autonomously actionable, portal contract research,
profile/adapter implementation, tests, and catalog binding are Priority 1. Generic security,
architecture, lifecycle, UX, accessibility, CI and fresh-audit milestones may preempt only for a
concrete P0/P1-equivalent issue or a direct portal/publication blocker. The orchestration target is
up to eight concurrent isolated native Codex/GPT-5.6 Luna Max implementation subagents whenever eight implementation-ready portal candidates exist. The `@Termuх agent_spawn` gateway cap of 3 is fallback-only and does not limit native Codex multi-agent concurrency. The primary KPI is growth in exact profile bindings and truthful implemented
coverage, not audit-finding count.

## Global Constraints

- The orchestrator integrates only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on branch `agent/workspace-47-autonomous-20260803`.
- Every implementation subagent owns a separate isolated Git worktree/branch based on the current autonomous integration head; never let two workers share a writable worktree.
- The immutable canonical base remains `9c99bbfb36e13f88231d56001ccef8c4cbbce128`; do not mutate the canonical source branch.
- Commit and push each worker candidate before Gradle verification, then integrate fully verified milestones sequentially; never force-push.
- No APK installation, app launch, device automation, authenticated portal use,
  credentials, certificate use, real signing, draft creation, upload, payment, or
  submission.
- Public portal research is unauthenticated and read-only.
- New profiles remain QA-only and never become `VERIFIED_E2E` automatically.
- Dependencies change only for demonstrated necessity.
- Ordinary handoffs remain `ACTIVE`; the task-wide budget or a direct user stop is
  the task completion trigger.

---

### Task 1: Establish the authoritative baseline and audit ledger

**Files:**
- Create: `docs/autonomous/2026-08-04-audit-ledger.md`
- Review: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Review: `docs/security-roadmap.md`
- Review: `docs/test-plan.md`
- Review: `docs/test-report.md`
- Review: `docs/threat-model.md`

- [ ] Verify branch, exact ancestry, clean worktree, upstream tracking, and remote
  divergence before any production mutation.
- [ ] Record the baseline Android Debug/QA unit counts, lint, APK builds, Python
  results, Go results, APK artifact checks, release fail-closed result, and
  environmental skips in the audit ledger.
- [ ] Enumerate open roadmap items, incomplete plans, handoff residuals, test
  warnings, flaky-test notes, QA-only profiles, and documentation inconsistencies.
- [ ] Rank findings by exploitability, data impact, trust-boundary reach,
  reproducibility, and safe autonomous feasibility.
- [ ] Commit and push the ledger/spec/plan milestone after diff and sensitive-data
  review.

### Task 2: Execute security and privacy remediation milestones

**Files:**
- Update per finding: exact source/test/docs paths named in a new subordinate plan
  under `docs/superpowers/plans/` before production mutation.
- Maintain: `docs/autonomous/2026-08-04-audit-ledger.md`
- Update when evidence changes: `docs/security-roadmap.md`, `docs/threat-model.md`,
  `docs/test-plan.md`, `docs/test-report.md`.

- [ ] Audit certificate lifecycle, in-memory key handling, recovery cache, logging,
  exported components, intents, storage, screenshots, clipboard, backups, and
  release boundaries.
- [ ] Audit signing request identity, TTL, epoch, replay, concurrency, confirmation,
  algorithm policy, CAdES/XAdES parsing, callback delivery, and cancellation.
- [ ] For each reproducible defect, write an exact-file sub-plan, observe RED,
  implement minimal GREEN, run focused security tests, then run the relevant full
  Android/Python/Go/artifact gates.
- [ ] Commit and push each independent security milestone separately.

### Task 3: Execute architecture and reliability remediation milestones

**Files:**
- Update per finding: exact source/test paths named in the milestone sub-plan.
- Maintain: `docs/autonomous/2026-08-04-audit-ledger.md`.

- [ ] Audit lifecycle ownership, process/activity boundaries, renderer loss,
  profile switching, session cleanup, coroutine cancellation, executors, bounded
  queues, timeouts, retry ownership, and order-dependent tests.
- [ ] Reproduce concurrency and determinism defects with hostile automated tests.
- [ ] Apply the milestone protocol and push each verified remediation atomically.

### Task 4: Audit WebView, network, Client TLS, cookies, and bridge boundaries

**Files:**
- Review: `app/src/main/java/dev/junta/firmamobile/browser/`
- Review: `app/src/main/java/dev/junta/firmamobile/network/`
- Review: `app/src/main/java/dev/junta/firmamobile/signing/`
- Review: associated tests under `app/src/test/` and `app/src/androidTest/` without
  executing physical instrumentation.
- Maintain: `docs/autonomous/2026-08-04-audit-ledger.md`.

- [ ] Audit exact-origin navigation, redirects, DNS/IP policy, peer pinning, TLS,
  tunnel boundaries, Client TLS grants, cookie scope, document-start scripts,
  message routing, external intents, downloads, and renderer recovery.
- [ ] Reject any proposed fix that broadens an allowlist, disables verification,
  accepts mixed content, trusts arbitrary JavaScript, or hides a failed contract.
- [ ] Implement only reproducible fail-closed improvements through exact-file
  subordinate plans and atomic pushed milestones.

### Task 5: Research and implement public-only portal contracts

**Files:**
- Review/update: `config/site_profiles_v1.json`
- Review/update: `docs/compatibility/all-spanish-public-portals-inventory.md`
- Review/update: `tools/public_portal_inventory.py`
- Review/update: `tools/generate_public_portal_catalog.py`
- Review/update: compatibility documents under `docs/compatibility/`
- Add exact Kotlin adapter/profile tests only when public evidence supports them.

- [ ] Select candidates only from official public evidence and record source URL,
  retrieval date, sanitized contract observations, and uncertainty.
- [ ] Use safe unauthenticated `GET`/`HEAD` and public JavaScript inspection only;
  never enter authentication or mutation flows.
- [ ] Implement a profile only when exact origins, paths, request shape, algorithm,
  and fail-closed boundaries are supportable by evidence and synthetic tests.
- [ ] Keep every new profile `EXPERIMENTAL` or `VERIFIED_CONTRACT`, `QA_ONLY`, and
  `IMPLEMENTED_NOT_E2E`; add explicit manual acceptance steps.
- [ ] Run catalog reproducibility and complete relevant gates before each push.

### Task 6: Improve UX and accessibility within the existing design system

**Files:**
- Review: Compose UI and resources under `app/src/main/`.
- Review/update: corresponding JVM/Robolectric/Compose contract tests.
- Maintain: `docs/autonomous/2026-08-04-audit-ledger.md`.

- [ ] Audit semantics, focus, touch targets, state descriptions, error recovery,
  localization consistency, adaptive constraints, and destructive confirmations.
- [ ] Make only local, reversible changes supported by automated tests.
- [ ] Do not claim visual/device validation; record a manual visual gate where
  rendering evidence is required.
- [ ] Commit and push each independent UX/accessibility milestone.

### Task 7: Harden CI, dependencies, supply chain, tests, and documentation

**Files:**
- Review: `.github/workflows/`, `.github/dependabot.yml`, `.gitleaks.toml`.
- Review: `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`,
  `app/gradle.lockfile`, `ws024-relay/go.mod`, `tools/requirements.txt`.
- Review/update: `tools/tests/`, `scripts/ci/`, and project documentation.

- [ ] Audit action pinning, permissions, cache keys, timeouts, concurrency,
  dependency locking, artifact verification, scanner coverage, and release
  fail-closed behavior.
- [ ] Diagnose and remove order-dependent or flaky tests without weakening
  assertions or increasing unsafe retries.
- [ ] Change a dependency only after recording the exact demonstrated reason and
  passing its isolated lock/SCA/full-gate milestone.
- [ ] Reconcile documentation with actual code and evidence statuses.

### Task 8: Repeat audit passes until terminal task-budget wrap-up

**Files:**
- Maintain: `docs/autonomous/2026-08-04-audit-ledger.md`
- Maintain: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Update evidence documents touched by completed milestones.

- [ ] Re-run fresh static searches, roadmap reconciliation, test-warning review,
  trust-boundary review, and catalog-status consistency checks after each phase.
- [ ] When one audit pass has no actionable item, start another pass from a
  different subsystem instead of ending the task.
- [ ] Keep local blockers and manual gates in the ledger while continuing
  independent work.
- [ ] At terminal budget wrap-up, run the freshest feasible full gate, push all
  verified commits, and emit a final DONE handoff with remaining work and exact
  manual acceptance gates.
