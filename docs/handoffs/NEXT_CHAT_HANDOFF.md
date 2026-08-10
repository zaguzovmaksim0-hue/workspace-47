# NEXT CHAT HANDOFF — workspace-47 autonomous portal-first cycle

Updated: 2026-08-10, generation 42.

## Repository state to verify first

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on
  `agent/workspace-47-autonomous-20260803`.
- Last verified autonomous main before this documentation commit:
  `6b70856a2740594d169fa85d68276aa0a03ea60d`, clean and 0/0 versus upstream.
- Canonical `origin/feature/ws024-secure-tunnel-20260728` remained exactly
  `9c99bbfb36e13f88231d56001ccef8c4cbbce128`; autonomous was ahead 72 / behind 0 with canonical as
  merge-base.
- Re-run `prepare_task`, `git fetch --prune origin`, branch/HEAD/upstream/divergence/status/worktree
  checks before any next mutation; resolve this documentation commit's exact published SHA rather
  than assuming the pre-commit SHA above.

## Generation 42 completed/published state

- JCCM catalog local history was preserved: RED `a9b12c1`, prior incorrect broad-row GREEN
  `13616f8`, and corrective separate-surface commit
  `cd839875e13b60ea8009bb2c9ab18d3482a8c40e` on published branch `agent/g39-jccm-catalog`.
- Correct JCCM P1 semantics: broad `ES-PUB-0103` stays `https://www.jccm.es/` / `BROWSE_ONLY`;
  certificate probe is separate `ES-PUB-0183` with exact profile start URL and
  `IMPLEMENTED_NOT_E2E` on that worker branch.
- JCCM exact-path RED remains published at
  `5eaad0966242fa30e35b8011ac3746c0012d9347` (`agent/g39-jccm-exact-path`). Focused Cloud task
  creation failed with HTTP 429 `Usage limit exceeded` before any Gradle execution; no local Gradle
  fallback occurred.
- Sevilla remains published at `069c6fd73a19b54b92dc4771867fff712617301d` and needs focused Cloud
  GREEN before native profile/adapter expansion.
- Melilla remote branch remains `25df9f7ed5bef0387568d6c2db5c7083f154fa9b`. Existing Cloud task
  `task_e_6a78dc14b2d48323887a6abf2ad48bce` currently renders `READY` but exposes no terminal PASS/FAIL
  verdict through `w47-cloud status`; do not infer success from that label.
- Research evidence: `docs/autonomous/2026-08-10-g42-portal-research-evidence.md` records current
  first-party Asturias, ACCEDA, Justicia, MJusticia, SEPE and Comunidad de Madrid findings without
  persisting volatile values or credential-like/static authorization material. Madrid Registro stops
  at a prohibited upload/POST boundary; Cuenta Digital's published signing service is authenticated
  and server-mediated, not a browser-local AutoFirma ABI. Do not promote these candidates by
  inference.

## Current portal KPI

- Main committed catalog: 182 entries; 12 bound catalog surfaces; 170 unbound.
- Unique profile IDs: 11. The difference from 12 bound surfaces is intentional: `us-sede` aliases
  `reg-age-redsara` through exact `launch_url`; generator and tests explicitly cover it.
- Inventory: 164 `BROWSE_ONLY`, 7 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`,
  4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 92 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 7 `E2E_PENDING`, 4
  `E2E_VERIFIED`.
- Research queue: at least 16 classified public surfaces. Generation 42 integrated 0 portals.

## Exact next actions

1. When Codex Cloud quota is available, submit focused RED against exact JCCM path SHA
   `5eaad0966242fa30e35b8011ac3746c0012d9347`; accept only an exact-SHA expected RED with dependency
   verification and clean checkout.
2. Implement JCCM runtime `currentPageUrl` plumbing sequentially in the main Watchdog context:
   `BrowserScreen` -> `WebMessageBridge` -> `MiniAppletBridgeAdapter`; require exact JCCM start URL
   only for `jccm-certificate-login-probe`, including null/wrong path/query/fragment rejection.
3. Add the same-document/history-change pending-reply regression only if it demonstrates stale
   delivery; change reply-registry semantics only if that RED proves the defect.
4. Commit/push JCCM GREEN before Gradle, run focused Cloud GREEN, perform direct bounded Standards +
   Spec review, then manually integrate the JCCM product + corrected catalog slice atop current main,
   preserving the existing `us-sede` REG-AGE alias and regenerating the catalog from current sources.
5. Run focused Cloud GREEN for Sevilla exact SHA
   `069c6fd73a19b54b92dc4771867fff712617301d`; only then continue its native adapter/profile/catalog
   phase.
6. Resolve a terminal Melilla Cloud verdict without guessing; continue execution/profile/registry/
   catalog only after verified gate evidence.
7. Continue exact public unauthenticated research without inference. Priority after in-flight portal
   slices: `justicia-sede-judicial`, `age-acceda`, `sepe-sede`, `mjusticia-sede`,
   `asturias-sede-tramite-autofirma`.

## Manual/external gates and prohibitions

- Manual only: UGR, DGT and Cantabria physical portal E2E; AEAT Client-TLS E2E; real-portal
  JavaScript-dialog compatibility; TalkBack/physical visual accessibility; Go race on supported Linux.
- Worker delegation remains disabled by operator revision 9: no native Codex/Luna implementation
  subagents and no `agent_spawn`.
- All Watchdog-initiated Gradle remains Codex Cloud only. Cloud quota failure is not authorization for
  local Gradle on the phone.
- No APK installation/launch, ADB/device control, authenticated portal navigation, credentials,
  private-certificate material, real signing, upload, payment or administrative submission is
  authorized.
