# NEXT CHAT HANDOFF — workspace-47 autonomous portal-first cycle

Updated: 2026-08-10, generation 42.

## Repository state to verify first

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on
  `agent/workspace-47-autonomous-20260803`.
- Generation-42 research/product checkpoint immediately before this handoff-only commit:
  `77b820d74121862127969cf871218588639be1d0`, clean and 0/0 versus upstream.
- Canonical `origin/feature/ws024-secure-tunnel-20260728` remained exactly
  `9c99bbfb36e13f88231d56001ccef8c4cbbce128`; the checkpoint was ahead 76 / behind 0 with canonical
  as merge-base.
- This file is committed after that checkpoint, so on continuation resolve the containing published
  branch HEAD rather than treating `77b820d...` as the final handoff commit SHA.
- Re-run `prepare_task`, `git fetch --prune origin`, and verify branch/HEAD/upstream/divergence/status/
  worktree state before any mutation. Do not reset, merge, or replace an unfinished worker worktree.

## Generation 42 completed/published state

- Main documentation/evidence commits published during generation 42 before this handoff-only commit:
  `169a63ecda697b6c8ed1db6c8e40e5eeb386faed`,
  `2e4796542d19968275208a95ae0865b180964ddd`,
  `db06bc57ae4daacaf9d4d3b221d24969a3338c3c`, and
  `77b820d74121862127969cf871218588639be1d0`.
- JCCM catalog history was preserved rather than recreated: RED `a9b12c1`, prior incorrect broad-row
  GREEN `13616f8`, and corrective published worker commit
  `cd839875e13b60ea8009bb2c9ab18d3482a8c40e` on `agent/g39-jccm-catalog`.
- Correct JCCM P1 semantics on that worker branch: broad `ES-PUB-0103` remains
  `https://www.jccm.es/` / `BROWSE_ONLY`; exact certificate probe is a separate `ES-PUB-0183` bound
  to `jccm-certificate-login-probe` and marked `IMPLEMENTED_NOT_E2E`. It is not yet integrated into
  autonomous main.
- JCCM exact-page RED remains published at
  `5eaad0966242fa30e35b8011ac3746c0012d9347` (`agent/g39-jccm-exact-path`). Two focused Codex Cloud
  submissions in generation 42 failed at task creation with HTTP 429 `Usage limit exceeded`; Gradle
  never started and no phone-local Gradle fallback was used.
- Sevilla remains published at `069c6fd73a19b54b92dc4771867fff712617301d`; focused Cloud GREEN is
  still required before native profile/adapter expansion.
- Melilla remote branch remains `25df9f7ed5bef0387568d6c2db5c7083f154fa9b`, while the existing local
  Melilla worktree was observed at `ce1b1639b322b616fb71cce12c73305db26e6a1a`. Preserve and inspect
  this local/remote discrepancy before any mutation; do not reset or replace either state blindly.
  Existing Cloud task `task_e_6a78dc14b2d48323887a6abf2ad48bce` currently renders `READY` but no
  terminal PASS/FAIL verdict was recovered, so success must not be inferred.
- `docs/autonomous/2026-08-10-g42-portal-research-evidence.md` now records bounded official public
  research for Asturias, ACCEDA, Justicia, MJusticia, SEPE, Comunidad de Madrid and Extremadura.
  No volatile values or credential-like/static authorization values discovered during research were
  retained or used.
- `extremadura-tramites` (`ES-PUB-0109`) is now implementation-ready at the **research** level. Its
  public STA helper/framework directly proves the AutoScript batch caller/callback contract and the
  relevant current public JS resources are byte-identical to Melilla. No profile, inventory status,
  generated catalog status, release state or E2E claim was changed.
- `extremadura-portal-tributario` (`ES-PUB-0111`) remains `BROWSE_ONLY` because its current public
  model pages hand off to the common Junta de Extremadura Sede and expose no portal-specific ABI.
- Portals integrated in generation 42: 0.

## Current portal KPI

- Main committed/reproducible catalog: 182 entries, 12 bound catalog surfaces, 170 unbound.
- Unique profile IDs: 11. The difference from 12 bound surfaces is intentional: `us-sede` is an exact
  `launch_url` alias of `reg-age-redsara`; generator logic and tests explicitly cover it.
- Inventory states: 164 `BROWSE_ONLY`, 7 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`,
  4 `VERIFIED_E2E`, 4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated states: 92 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 7 `E2E_PENDING`,
  4 `E2E_VERIFIED`.
- Research buffer remains at least 16 classified public surfaces.
- Exact implementation priority after in-flight JCCM, Sevilla and Melilla is
  `extremadura-tramites`. Research-only candidates still needing a complete public binding include
  `justicia-sede-judicial`, `age-acceda`, `sepe-sede`, `mjusticia-sede`, and
  `asturias-sede-tramite-autofirma`.

## Exact next actions

1. When Codex Cloud quota is available, submit focused RED against exact JCCM SHA
   `5eaad0966242fa30e35b8011ac3746c0012d9347`; accept only an exact-SHA expected RED with dependency
   verification and clean checkout. Do not use local Gradle after another 429.
2. After accepted RED, implement JCCM `currentPageUrl` plumbing sequentially in the main Watchdog
   context: `BrowserScreen` -> `WebMessageBridge` -> `MiniAppletBridgeAdapter`. Require the exact JCCM
   start URL only for `jccm-certificate-login-probe`, including null/wrong-path/query/fragment
   rejection. Add the same-document/history-change pending-reply regression only if it proves stale
   delivery.
3. Commit/push JCCM GREEN before Gradle, run focused Cloud GREEN, perform a bounded direct Standards +
   Spec review, then integrate the JCCM product slice together with the corrected catalog branch
   without whole-file replacement. Regenerate catalog from current sources and preserve the REG-AGE
   alias.
4. Run focused Cloud GREEN for Sevilla exact SHA
   `069c6fd73a19b54b92dc4771867fff712617301d`; only after PASS continue its native
   adapter/profile/catalog phase.
5. Inspect the existing Melilla local worktree at `ce1b1639...` against remote `25df9f7...`, preserve
   all valid unfinished work, and recover a terminal Cloud verdict without guessing. Continue planned
   execution/profile/registry/catalog phases only after verified gate evidence.
6. After the verified Melilla STA batch seam is integrated, create a narrow Matt Pocock design/plan for
   `extremadura-tramites` that generalizes the identical STA protocol while retaining separate exact
   profile/origin/runtime-URL policy. Do not clone or broaden Melilla allowlists blindly.
7. Continue official public unauthenticated research for the remaining research-only candidates when
   Cloud-blocked, without inferring missing algorithms, formats, payloads, callbacks, endpoints or
   authenticated behavior.

## Verification and safety state

- Main catalog regeneration was byte-for-byte identical to committed
  `app/src/main/res/raw/public_portal_catalog_v1.json` at checkpoint `77b820d...`.
- `python -m unittest tools.tests.test_generate_public_portal_catalog -q` exited 0 at that checkpoint.
- JCCM catalog worker verification: 8/8 generator tests passed, regeneration byte-for-byte
  reproducible, `git diff --check` passed, sensitive/policy scan passed, worker commit pushed and
  exact remote SHA verified.
- All generation-42 main documentation commits listed above were pushed and exact remote SHAs were
  verified when published. No Android Gradle command completed in generation 42 because both JCCM
  Cloud task-creation attempts hit the quota before Gradle execution.
- Manual/external gates remain UGR, DGT and Cantabria physical portal E2E; AEAT Client-TLS E2E;
  real-portal JavaScript-dialog compatibility; TalkBack/physical visual accessibility; and Go race on
  supported Linux.
- Worker delegation remains disabled: no native Codex/Luna implementation subagents and no
  `agent_spawn`.
- No APK was installed or launched; no ADB/device control, authenticated portal navigation,
  credential/private-certificate use, real signing, upload, payment or administrative submission
  occurred.
