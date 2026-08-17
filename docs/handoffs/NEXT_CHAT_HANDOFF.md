# NEXT CHAT HANDOFF — Workspace-47 autonomous portal-first cycle

> **HISTÓRICO — NO USAR COMO CONTABILIDAD ACTUAL.** Este handoff corresponde al ciclo del 2026-08-12 y conserva cifras de aquel checkpoint. El estado operativo actual debe obtenerse desde `main` con `python tools/report_public_portal_coverage.py`; no se deben propagar los KPI de este documento a nuevos handoffs.

Date: 2026-08-12
Task: `workspace-47-autonomous-20260803-01`
Last Cloud-accepted product checkpoint before this documentation publication:
`4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb` on
`agent/workspace-47-autonomous-20260803`. The documentation commit containing this handoff will be
newer; continuation must begin with `git fetch --prune origin` and exact HEAD/origin, divergence,
cleanliness, and canonical-SHA verification. Canonical
`origin/feature/ws024-secure-tunnel-20260728` remains
`9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

## Accepted automated portal boundaries

Do not repeat Melilla or Extremadura Slices 1-4.

- Melilla (`ES-PUB-0107`) remains automated-QA accepted at `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`;
  release is `VERIFIED_CONTRACT`, disabled, and physical E2E remains pending.
- Extremadura Slices 1-3 remain accepted as recorded in the audit ledger.
- Slice 4 WebMessage GREEN `b3eab817d9deb1d9993e382c23c9c04232e4381e`: Debug Cloud
  `task_e_6a7bf747e4a48323ad44977079fd3f2c` 16/16 and QA
  `task_e_6a7bf8e6e2388323980feff3d0b230d3` 16/16 accepted.
- Slice 4 profile-bound confirmation metadata GREEN
  `ad72137f7c06ae31dc33fa3c7e89dc9b2c740380`: focused Cloud
  `task_e_6a7bfca1045c832386fb7feeb6de84ee` 1/1 and broader Debug+QA
  `task_e_6a7bfe64a3c883238d8b56aacb37672b` 10/10 accepted.
- MainActivity RED `3ddc244f62752f08e050973b737396e316be0bbf`: Cloud
  `task_e_6a7c00eee0e48323923a6107224a6462` failed the intended new composition assertion 1/1.
- MainActivity GREEN `4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb`: focused Cloud
  `task_e_6a7c05e2564c8323ac84c07066c60d74` passed 1/1 (`BUILD SUCCESSFUL in 5m34s`), then
  `task_e_6a7c0b0d611883239597c9ea3d22e278` passed Debug 656/656 + QA 35/35 = 691/691
  (`BUILD SUCCESSFUL in 6m18s`), all with exact SHA, dependency verification enabled, no Cloud error,
  and clean read-only checkout evidence.
- Direct Standards + Spec review and local permitted checks found no Critical/Important Slice 4 defect.
  Extremadura now uses exact profile-bound normalizer/reply selection, exact two-protocol resolver,
  runtime profile metadata, and the existing single BATCH ownership gate.

## Portal KPI and queue

- Catalog: 183 entries; 15 bound surfaces; 14 unique profile IDs; 168 unbound.
- Inventory: 162 `BROWSE_ONLY`, 10 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`,
  4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 90 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 10 `E2E_PENDING`, 4
  `E2E_VERIFIED`; classified research queue depth remains at least 16 surfaces.
- Portals integrated in generation 66 so far: 0. Extremadura is not integrated until Slice 5 succeeds.
- Manual/physical E2E remains pending for Melilla, Extremadura, Sevilla, UGR, DGT, Cantabria, JCCM,
  and AEAT Client-TLS.

## Exact next portal implementation order

1. Execute Extremadura Slice 5 from
   `docs/superpowers/specs/2026-08-11-extremadura-sta-batch-integration-design.md` and
   `docs/superpowers/plans/2026-08-11-extremadura-sta-batch-integration.md` only: add exact QA-only
   `extremadura-tramites` `VERIFIED_CONTRACT` profile, distinct protocol-registry SIGN binding, bind
   `ES-PUB-0109`, regenerate the canonical public catalog, and prove release remains disabled.
2. Terminal automated state is at most `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; never promote to
   `VERIFIED_E2E` without separate user-supplied physical evidence.
3. Push each RED/GREEN SHA before Cloud Gradle. After Slice 5, run the canonical full Android Cloud
   gate plus permitted local Python catalog/regeneration/policy checks and direct Standards + Spec review.
4. After full Extremadura automated acceptance, next implementation candidate is La Palma
   (`ES-PUB-0130`). Eivissa (`ES-PUB-0122`) and Formentera (`ES-PUB-0124`) remain research-only.

## Safety / execution constraints

Worker delegation remains disabled: no native Codex/Luna implementation subagents, no `agent_spawn`,
and no delegated `codex/code-review`. Use the main Watchdog with Matt Pocock TDD/implementation and
direct bounded Standards + Spec review. Every Android Gradle command stays in Codex Cloud
`workspace-47-android` through `$HOME/bin/w47-cloud`; never fall back to phone-local Gradle/JVM/Kotlin.
No APK install/launch, ADB/UIAutomator/Mobilerun/device control, authenticated government-portal
navigation, credentials/cookies/bearer data, certificate unlock/private-key material, real signing,
form submission, upload, payment, or administrative action.
