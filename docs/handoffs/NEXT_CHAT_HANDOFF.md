# NEXT CHAT HANDOFF — Workspace-47 autonomous portal-first cycle

Date: 2026-08-11
Task: `workspace-47-autonomous-20260803-01`
Last Cloud-accepted product/test checkpoint before this documentation publication:
`351554e0f69fd2ebf758386ca4d83baeb064d561` on
`agent/workspace-47-autonomous-20260803`. The commit containing this handoff is a newer documentation
checkpoint; continuation must begin with `git fetch --prune origin` and exact local/remote HEAD,
upstream-divergence, cleanliness, and canonical-SHA verification. Canonical
`origin/feature/ws024-secure-tunnel-20260728` remains exactly
`9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

## Melilla current automated boundary

Execution core, coordinator/PRE ownership, runtime request/reply adaptation, and explicit bridge batch
cancellation are Cloud-accepted. Do not repeat them and do not promote Melilla yet.

- Slice-2 production callback commit: `60325e5bae1e8ba4315e6d4cd59c90bf224432bf`; focused Cloud
  GREEN `task_e_6a7b62c88a9c8323b506c89023c3167b` passed 1/1 at that exact SHA.
- Bridge-wide teardown tracer bullet: `351554e0f69fd2ebf758386ca4d83baeb064d561`; Cloud acceptance
  `task_e_6a7b65953d38832381228db34634c769` passed 2/2 at that exact SHA with dependency verification
  enabled, no verification-metadata mutation, and a clean Cloud checkout.
- Direct Standards + Spec review found no remaining Critical/Important Slice-2 defect.

## Exact next Melilla slice — BrowserScreen/MainActivity composition + arbitration

Use Slice 3 of `docs/superpowers/plans/2026-08-11-melilla-runtime-wiring.md`; do not create a new design.

1. Add one bounded runtime-wiring RED tracer bullet at a time. First prove `BrowserScreen` exposes and
   forwards Melilla batch request/cancel callbacks into `WebMessageBridge`. Commit/push the test-only
   SHA before Cloud Gradle and accept RED only for the missing runtime wiring.
2. Implement only that forwarding seam, commit/push, and Cloud-verify GREEN.
3. Then add the MainActivity composition/arbitration RED: construct `MelillaBatchProtocolAdapter` over
   existing `HttpsProfileHttpTransport`, own one dedicated `BatchSigningCoordinator`, and enforce one
   fail-closed ordinary-versus-batch signing owner before either coordinator can reach certificate/
   private-key confirmation. Route confirm/cancel/UI state only to the current owner.
4. Commit/push every exact RED/GREEN SHA before Cloud Gradle. After focused acceptance, run the
   applicable broader Cloud Android gate and direct Standards + Spec review.
5. Only after complete runtime acceptance perform separate Melilla profile/public-catalog promotion;
   retain `VERIFIED_CONTRACT` / `QA_ONLY` and at most `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` until
   user-supplied physical evidence exists.

## Portal KPI and queue

- Catalog: 183 entries; 14 bound surfaces; 13 unique profile IDs; 169 unbound.
- Inventory: 163 `BROWSE_ONLY`, 9 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`,
  4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 91 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 9 `E2E_PENDING`, 4
  `E2E_VERIFIED`; classified public research buffer remains at least 16 surfaces.
- Portals newly integrated in generation 58 so far: zero; Melilla runtime work is not yet a catalog
  integration.
- Exact implementation order: finish Melilla runtime/profile/catalog, then `extremadura-tramites`
  (`ES-PUB-0109`), then La Palma (`ES-PUB-0130`). Eivissa (`ES-PUB-0122`) and Formentera
  (`ES-PUB-0124`) remain research-only.
- Manual/physical E2E remains pending for Melilla, Sevilla, UGR, DGT, Cantabria, JCCM, and AEAT
  Client-TLS. Never infer `VERIFIED_E2E` from automated evidence.

## Safety / execution constraints

Worker delegation remains disabled: no native Codex/Luna implementation subagents, no `agent_spawn`,
and no delegated `codex/code-review`. Use the main Watchdog with Matt Pocock TDD/implementation and a
direct bounded Standards + Spec review. Every Android Gradle command stays in Codex Cloud
`workspace-47-android` through `$HOME/bin/w47-cloud`; never fall back to phone-local Gradle/JVM/Kotlin.
No APK install/launch, ADB/UIAutomator/Mobilerun/device control, authenticated government-portal
navigation, credentials/cookies/bearer data, certificate unlock/private-key material, real signing,
form submission, upload, payment, or administrative action.
