# Portal Coverage First autonomous priority design

## Problem

The autonomous cycle is technically healthy but product-priority inverted. The public catalog has
182 entries while only 8 have a concrete `profileId`; only 4 are `VERIFIED_E2E`. The current task
orders security/privacy, architecture/concurrency, and WebView/network/TLS/signing audits before
portal implementation. Because a fresh audit can always find another hardening lead, portal work can
starve even though the dominant user-visible gap is catalog entries that remain unavailable.

This design changes scheduling and delegation, not the security model. Existing fail-closed
boundaries, release eligibility, physical-E2E requirements, credential restrictions, and verification
gates remain mandatory.

## Product objective

Maximize the number of Spanish public portals that progress from catalog-only knowledge to an exact,
tested Junta Firma integration. While autonomously researchable candidates remain, the default next
milestone must increase portal coverage or remove a direct blocker to that increase.

Baseline at reprioritization time:

- public catalog entries: 182;
- concrete profile bindings: 8;
- `VERIFIED_E2E`: 4;
- `IMPLEMENTED_NOT_E2E`: 3;
- `VERIFIED_CONTRACT`: 1;
- `BROWSE_ONLY`: 168;
- unbound catalog entries: 174.

## Priority policy

### Priority 0 — finish only already in-flight work safely

G34 legacy persisted certificate display-name hardening was already implemented and in verification
when this policy was adopted. Finish that one milestone to a safe publication boundary because
abandoning a verified dirty diff would waste completed work. Do not start another unrelated audit
milestone afterward.

### Priority 1 — portal coverage

Continuously select portal milestones while an official-public-evidence candidate is actionable. A
portal milestone may be:

1. public contract research that classifies a previously catalog-only portal;
2. creation of an exact `SiteProfile`;
3. reuse of an existing adapter or the smallest portal-specific adapter required by evidence;
4. deterministic catalog/profile binding;
5. synthetic/contract regression coverage;
6. removal of a direct implementation blocker for the selected portal.

A newly implemented sensitive portal remains at most `EXPERIMENTAL` or `VERIFIED_CONTRACT`,
`QA_ONLY`, and `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. `VERIFIED_E2E`, release enablement, or any
expansion of an E2E claim still requires separate physical evidence supplied by the user.

### Priority 2 — only blocking/important defects

Security, privacy, architecture, concurrency, WebView, network, TLS, signing, storage,
accessibility, CI, and supply-chain work may preempt portal coverage only when at least one condition
is true:

- concrete P0/P1-equivalent defect;
- defect directly blocks the selected portal or its required gate;
- defect would make the selected portal materially unsafe or its support claim untruthful;
- required build/test/release gate is broken and prevents publication.

Low-impact hardening, speculative cleanup, cosmetic accessibility work, and generic fresh-audit
passes must not displace a viable portal milestone.

## Luna Max implementation architecture

The installed Temu/Codex worker runtime is fixed to `gpt-5.6-luna` with reasoning effort `max` and
uses native Codex Multi-Agent v1 (`multi_agent = true`) with operator-selected `[agents].max_threads = 8`. Portal coverage must use up to eight native Codex/Luna implementation subagents concurrently whenever eight independent implementation-ready candidates exist. The separate `@Termuх agent_spawn` gateway has its own 3-worker cap and is a fallback only; it does not define the native Codex concurrency target.

The workers are implementation capacity, not a review pool:

- preferred role while implementation-ready candidates exist: `coder`;
- target occupancy: 8/8 concurrent native Codex/Luna Max implementation subagents;
- do not consume a worker slot with a reviewer if doing so leaves an implementation-ready candidate
  waiting;
- use a reviewer only as a bounded final gate after implementation capacity is no longer waiting, or
  run review in the orchestrator when appropriate;
- an `explorer`/`tester` may occupy an otherwise idle slot only when fewer than three candidates are
  implementation-ready and its work directly fills the next implementation slot.

### Isolation

Parallel coders must never mutate the autonomous worktree together. The orchestrator creates one
isolated temporary Git worktree/branch per portal candidate, all from a recorded common integration
base. Each coder owns exactly one portal worktree and cannot touch another worker's worktree.

A worker may implement portal-specific source/config/tests in its own worktree. Shared generated
catalog output, durable audit documents, aggregate reports, and final publication remain orchestrator
owned. The orchestrator runs tests, records the worker diff, commits the worker result when valid,
and integrates worker results into `agent/workspace-47-autonomous-20260803` sequentially. After each
integration it reruns the affected gates so a clean worker result is never assumed to compose safely
with previously integrated workers.

If two portal diffs touch a shared source such as `config/site_profiles_v1.json`, isolation prevents
write races. Integration may require a narrow deterministic merge; the orchestrator must preserve
both exact profiles and rerun generator/tests rather than accepting conflict markers or dropping one
candidate.

## Evidence pipeline

Luna workers are bounded/offline. The orchestrator therefore owns live public evidence collection.
For each candidate it prepares a small evidence packet from official public sources only, containing:

- exact portal identity and public URL;
- retrieval date;
- exact origin/path boundaries;
- observed public JavaScript/API/Client-TLS contract;
- signing/auth operation;
- algorithm/format/mode/callback or key-type/issuer constraints when present;
- evidence URLs/identifiers and sanitized excerpts;
- explicit unknowns and forbidden assumptions;
- expected target status and adapter reuse decision.

A coder receives that evidence packet plus exact local file ownership. It implements from the packet;
it does not invent missing network or cryptographic contract details.

## Candidate queue and anti-starvation

Maintain a research buffer ahead of the coders. Candidate ranking favors:

1. official evidence sufficient for an exact bounded contract;
2. high user value / broadly used public administration portal;
3. reuse of an already verified adapter/protocol family;
4. implementation possible without credentials or authenticated interaction;
5. smallest new trust surface and easiest truthful QA-only boundary.

Operational rules:

- when 8 implementation-ready candidates exist, keep 8/8 coder subagents active;
- maintain a target buffer of at least 16 researched candidates when public evidence allows it;
- target at least 80% of autonomous engineering effort toward portal coverage and its direct gates;
- never start two consecutive non-portal milestones while a viable portal candidate exists;
- after each integrated portal batch, select/refill the next portal batch before any broad audit;
- a portal blocked only by physical/authenticated E2E goes to the manual queue and does not block the
  next autonomously actionable candidate;
- broad subsystem audits are fallback work only when the portal queue cannot currently advance.

## Success metrics

Track in every handoff:

- total catalog entries;
- number with `profileId`;
- counts of `BROWSE_ONLY`, `VERIFIED_CONTRACT`, `IMPLEMENTED_NOT_E2E`, and `VERIFIED_E2E`;
- implementation-ready research queue depth;
- active native Codex/Luna coder occupancy (`0..8`);
- portals integrated during the generation;
- portals blocked only on manual E2E;
- exact next eight implementation candidates.

The primary autonomous KPI is growth in exact profile bindings and truthful
`IMPLEMENTED_NOT_E2E`/`VERIFIED_CONTRACT` coverage, not the number of audit findings.

## Verification and safety

Every integrated portal still requires TDD/contract tests, focused tests, applicable full Android and
catalog/tool gates, policy checks, diff review, sensitive-data scans, and atomic push with remote SHA
verification. Never weaken TLS, origin/path restrictions, signature checks, consent, release gating,
or status truthfulness to increase the coverage count.
