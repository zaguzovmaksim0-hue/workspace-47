# NEXT CHAT HANDOFF — workspace-47 autonomous portal-first cycle

Updated: 2026-08-11, generation 44.

## Repository state to verify first

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on
  `agent/workspace-47-autonomous-20260803`.
- Generation-44 JCCM product checkpoint before this documentation commit is
  `0afd632d8b22691da7cde87c7e587fe8b49b306b`.
- Canonical `origin/feature/ws024-secure-tunnel-20260728` remained exactly
  `9c99bbfb36e13f88231d56001ccef8c4cbbce128`; no merge, rebase, force-push or rewrite of canonical
  occurred.
- This handoff is committed after the product checkpoint, so resolve the containing published branch
  HEAD on continuation rather than treating `0afd632...` as the final documentation SHA.
- Start with `prepare_task`, `git fetch --prune origin`, then verify branch, exact HEAD, upstream,
  divergence and worktree state before any mutation.

## JCCM completed state

- JCCM product history integrated on autonomous main through exact checkpoint
  `0afd632d8b22691da7cde87c7e587fe8b49b306b`: `c63bea6`, `9731ad6`, `88b413a`, `0b79d60`,
  `643043f`, `b79f821`, `5370f5e`, `0afd632`.
- `jccm-certificate-login-probe` remains `VERIFIED_CONTRACT` / `QA_ONLY`; it is not release-enabled
  and is not `VERIFIED_E2E`.
- The bridge requires the exact runtime page
  `https://ventanillaelectronica.jccm.es/administracion_electronica/formularios/identificacion.phtml`
  plus the exact JCCM origin, payload `ABCDE`, `SHA1withRSA`, `CAdES`, and null/empty extra
  properties. Wrong path/query/fragment and every other tested contract dimension fail closed.
- Broad JCCM `ES-PUB-0103` remains unbound `BROWSE_ONLY`; separate exact probe `ES-PUB-0183` is bound
  to the JCCM profile as `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`.
- The generation-43 final local-fallback Android acceptance job
  `job_20260810_202832_2180e9fd` was recovered durably: `MemAvailable_kB=4199152`, exit 0,
  `BUILD SUCCESSFUL in 12m 59s`. It passed resolved-core/AAPT2 checks, Debug+QA unit tests,
  Debug+QA lint, Debug+QA assembly and QA AndroidTest assembly on exact SHA `0afd632...` under the
  bounded single-worker/no-parallel/in-process-Kotlin memory policy.
- Post-gate `git diff --check`, public catalog generator tests 9/9, deterministic regeneration and
  sensitive/unsafe pattern scan all passed. Direct Standards + Spec review found no Critical or
  Important issue.

## Current portal KPI

- Catalog: 183 entries; 13 bound surfaces; 12 unique profile IDs; 170 unbound surfaces.
- Inventory: 164 `BROWSE_ONLY`, 8 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4
  `VERIFIED_E2E`, 4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 92 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 8 `E2E_PENDING`, 4
  `E2E_VERIFIED`.
- Discovery maintenance: 105 `REVIEWED`, 73 `DISCOVERED`, 5 `RECHECK_REQUIRED`.
- Research buffer depth remains at least 16 classified public surfaces.
- Portals completed by the JCCM publication slice: JCCM certificate-login probe (1).
- Manual-E2E-only portal gates include UGR, DGT, Cantabria and JCCM; AEAT still requires Client-TLS
  E2E. Real-portal JavaScript-dialog compatibility, TalkBack/physical visual checks and supported-Linux
  Go race remain external/manual gates.

## Exact next implementation sequence

1. **Sevilla ATSE** — preserved published implementation checkpoint
   `069c6fd73a19b54b92dc4771867fff712617301d`. Verify its branch/worktree and remote identity, then
   run the required focused GREEN gate. For generation 44, make at most one `w47-cloud` availability
   attempt when Gradle is required; if it fails before Gradle with the current HTTP-429/quota incident,
   use the explicitly authorized bounded phone-local single-worker fallback for the rest of this
   generation. Continue Sevilla profile/adapter/catalog integration only after GREEN evidence.
2. **Melilla STA** — preserve and inspect the existing local worktree previously observed at
   `ce1b1639b322b616fb71cce12c73305db26e6a1a` against remote branch
   `25df9f7ed5bef0387568d6c2db5c7083f154fa9b`; do not reset, replace, rebase or force-push. Recover
   any terminal verdict for prior Cloud task `task_e_6a78dc14b2d48323887a6abf2ad48bce` without
   inferring success.
3. **extremadura-tramites** (`ES-PUB-0109`) — implementation-ready at the public-research level after
   Melilla. Its current public STA helper/framework is byte-identical to Melilla's observed STA
   resources. Reuse only genuinely common STA mechanics; retain a separate exact profile, origin and
   runtime-URL policy.
4. Continue official public unauthenticated research for `justicia-sede-judicial`, `age-acceda`,
   `sepe-sede`, `mjusticia-sede`, and `asturias-sede-tramite-autofirma`; do not infer missing
   algorithms, formats, payloads, callbacks, endpoints or authenticated behavior.

## Safety and execution constraints

- Worker delegation is disabled: no native Codex/Luna implementation subagents and no
  `@Termuх agent_spawn`.
- Use Matt Pocock `codex/implement` / `codex/tdd` semantics directly in the main Watchdog and direct
  Standards + Spec review instead of delegated `codex/code-review`.
- Cloud Gradle is preferred. The explicit phone-local fallback remains bounded to the current quota
  incident exactly as stated in the task: one Gradle invocation at a time, no parallel/background
  builds, at least 2 GiB `MemAvailable`, capped JVM memory, in-process Kotlin compiler, dependency
  verification enabled, and no retry loops after resource failure.
- Never install or launch an APK; never use ADB/device control; never enter authenticated portal areas
  or use credentials, cookies, bearer material, certificate unlock/private keys; never perform real
  signing, upload, payment, form submission or administrative action.
