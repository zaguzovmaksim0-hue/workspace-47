# Portal Coverage First Autonomous Execution Plan

> **For agentic workers:** REQUIRED SUB-SKILLS: use `superpowers:dispatching-parallel-agents` for
> independent portal batches and `superpowers:subagent-driven-development` principles for bounded
> implementer ownership. Use `superpowers:test-driven-development` for behavior changes,
> `superpowers:systematic-debugging` for failures, and
> `superpowers:verification-before-completion` before commit/push. Steps use checkbox (`- [ ]`).

**Goal:** turn the 182-entry public catalog into progressively larger exact, tested portal coverage,
using the maximum safe GPT-5.6 Luna Max implementation parallelism instead of allowing general audit
work to starve portal delivery.

**Architecture:** the Watchdog is the orchestrator and public-evidence collector. It maintains a
research buffer, creates up to eight isolated portal worktrees when possible, dispatches up to eight concurrent native Codex/Luna Max implementation subagents, then integrates and verifies their results sequentially on the autonomous
branch. Security/audit work preempts portal delivery only for concrete critical/important or direct
blocking defects.

**Tech Stack:** Kotlin, Android/Jetpack Compose/WebView, site-profile JSON, Python catalog generator,
JUnit/Robolectric, Gradle, Go relay gates, Git worktrees, Temu Codex workers (`gpt-5.6-luna`, effort
`max`, native Codex Multi-Agent v1 `[agents].max_threads = 8`; the separate `@Termuх agent_spawn` gateway remains capped at 3 and is fallback-only).

## Global Constraints

- Work only from the autonomous project lineage rooted at
  `/data/data/com.termux/files/home/workspace-47-autonomous-20260803`.
- Final integration branch remains `agent/workspace-47-autonomous-20260803`; never mutate the
  canonical branch and never force-push.
- Preserve the in-flight G34 work; finish it once, then switch to portal-first scheduling.
- New sensitive profiles remain at most `EXPERIMENTAL`/`VERIFIED_CONTRACT`, `QA_ONLY`, and
  `IMPLEMENTED_NOT_E2E` until separate physical user evidence supports promotion.
- Public research is unauthenticated and read-only. No credentials, certificate material, real
  signing, upload, payment, draft, or submission.
- Never broaden TLS/origin/path/signature/consent/release boundaries to make a portal work.
- Maintain eight concurrent native Codex/Luna Max implementation subagents whenever eight independent implementation-ready portal candidates exist.
- Parallel coders never share a writable worktree.
- Shared generated catalog/docs and final integration/push are orchestrator-owned.

---

### Task 1: Close the already in-flight G34 milestone and stop audit starvation

**Files:**
- Preserve current G34 source/test/spec/plan files already dirty in the autonomous worktree.
- Update G34 evidence files only after its existing verification finishes.

**Interfaces:**
- Consumes: current G34 handoff and already completed focused/full gates.
- Produces: one clean pushed G34 commit or a precisely documented blocker; no new unrelated audit.

- [ ] Verify the already running G34 gate result; do not repeat completed expensive gates without a
  reason.
- [ ] Complete only remaining G34 review/evidence/staging/commit/push work.
- [ ] Verify local/tracking/remote SHA equality, clean intended G34 scope, generated relay cleanup,
  and zero release APKs.
- [ ] Immediately transition to Task 2; do not choose G35 from a generic audit queue.

### Task 2: Build and continuously refill the portal implementation queue

**Files:**
- Read/update: `docs/compatibility/all-spanish-public-portals-inventory.md`
- Read/update as evidence changes: `docs/compatibility/spanish-government-signing-matrix.md`
- Create orchestrator scratch evidence packets under a git-ignored per-plan workspace.

**Interfaces:**
- Produces: ranked candidate records containing exact public evidence and an implementation decision.

- [ ] Recompute catalog metrics and unbound candidates from generated/source data.
- [ ] Rank candidates by official-evidence completeness, user value, adapter reuse, autonomous
  feasibility, and trust-surface size.
- [ ] Use official public GET/HEAD/page/JavaScript evidence only; record origin/path, protocol,
  operation, algorithm/format/mode/callback or Client-TLS constraints, and unknowns.
- [ ] Maintain at least 16 researched candidates when evidence permits, so eight implementation slots do not wait on research.
- [ ] Mark each candidate `IMPLEMENTATION_READY`, `NEEDS_MORE_PUBLIC_EVIDENCE`,
  `MANUAL_E2E_ONLY`, or `UNSUPPORTED/INACCESSIBLE`; never infer a contract from branding alone.
- [ ] Select the next eight `IMPLEMENTATION_READY` candidates as one parallel batch when eight are available; otherwise fill every ready slot up to eight.

### Task 3: Dispatch the maximum eight-way native Codex/Luna implementation batch

**Files:**
- Temporary isolated Git worktree/branch per candidate, created by orchestrator.
- Candidate-specific source/config/test files identified from the evidence packet.

**Interfaces:**
- Consumes: up to eight independent evidence packets from Task 2.
- Produces: up to eight isolated implementation diffs, each attributable to one portal.

- [ ] Record integration HEAD and verify the autonomous branch/upstream before fan-out.
- [ ] Create one isolated temporary worktree/branch per selected portal from the same recorded base, up to eight worktrees.
- [ ] Dispatch **up to eight concurrent native Codex multi-agent implementation subagents** when candidates exist, targeting 8/8 occupancy. The active Codex v1 configuration is `multi_agent = true`, `max_threads = 8`; use GPT-5.6 Luna Max for these implementation agents. Give each agent only its portal evidence packet, worktree, exact ownership, target tests, and forbidden assumptions.
- [ ] Do **not** mistake `@Termuх agent_spawn`'s separate 3-worker gateway cap for the Codex native subagent limit. Use that gateway only as fallback when native Codex multi-agent execution is unavailable.
- [ ] Require coders to implement the actual profile/adapter/config/test changes in their own
  worktree. Do not spend implementation slots on general review.
- [ ] If fewer than eight candidates are ready, use remaining native Codex slots only for directly pipeline-filling research/test work; replace them with implementation agents immediately when candidates become ready.
- [ ] Never let two workers write the same worktree or another worker's files.

### Task 4: Verify and integrate worker implementations sequentially

**Files:**
- Final integration: `config/site_profiles_v1.json`, relevant Kotlin adapter/profile/tests,
  catalog generator/tests, and exact portal-specific compatibility evidence.

**Interfaces:**
- Consumes: isolated worker diffs.
- Produces: integrated autonomous-branch portal implementations with truthful statuses.

- [ ] Inspect each worker diff for scope and evidence compliance before accepting it.
- [ ] Run the portal's focused RED/GREEN or contract tests in its isolated worktree; fix through the
  same worker only when the defect is within that portal's owned scope.
- [ ] Commit the valid worker result locally, then integrate results one at a time onto the
  autonomous branch.
- [ ] Resolve shared JSON/registry conflicts deterministically by retaining every evidence-backed
  profile; rerun the generator and relevant tests after each integration.
- [ ] Reject a worker result that guesses an endpoint/algorithm/origin, broadens an allowlist, or
  inflates status.
- [ ] After the batch is integrated, run applicable Debug/QA JVM, lint/build, Python catalog, Go,
  artifact, release fail-closed, policy, diff, and sensitive/unsafe-content gates.

### Task 5: Publish the portal batch and update truthful catalog state

**Files:**
- Update generated/public catalog only through the deterministic generator.
- Update relevant compatibility docs, test plan/report, durable handoff, and audit ledger.

**Interfaces:**
- Produces: one or more atomic portal commits on the autonomous branch, pushed and remote-verified.

- [ ] Record exact profile ID, portal, operation, protocol, status, activation, evidence boundary,
  and manual E2E gate for every integrated portal.
- [ ] Keep newly implemented sensitive portals `QA_ONLY` and non-E2E unless prior exact physical
  evidence already exists for that same contract.
- [ ] Stage exact scope, inspect full staged diff, run `git diff --check` and sensitive/unsafe scans.
- [ ] Commit/push and require local/tracking/`ls-remote` SHA equality with divergence `0/0`.
- [ ] Delete temporary portal worktrees/branches only after their integrated commit is durable.

### Task 6: Loop on portal throughput, not audit-findings count

**Files:**
- Maintain the portal queue/evidence workspace and durable handoff metrics.

**Interfaces:**
- Consumes: post-batch catalog state.
- Produces: the next three portal candidates and a continuously running implementation pipeline.

- [ ] Recompute: catalog total, `profileId` count, `BROWSE_ONLY`, `VERIFIED_CONTRACT`,
  `IMPLEMENTED_NOT_E2E`, `VERIFIED_E2E`, manual-E2E queue, and research-buffer depth.
- [ ] Refill the research buffer before implementation occupancy drops below 8 when public evidence exists.
- [ ] Start the next up-to-eight-agent implementation batch immediately.
- [ ] Permit a non-portal milestone only for P0/P1-equivalent or direct portal/gate blockers.
- [ ] If no candidate is autonomously actionable, perform bounded fallback audit/research only until
  a candidate becomes actionable; do not convert fallback work into the default scheduler again.
