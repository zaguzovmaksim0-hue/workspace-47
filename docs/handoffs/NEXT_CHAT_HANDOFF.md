# NEXT CHAT HANDOFF — Workspace-47 autonomous portal-first cycle

Date: 2026-08-12
Task: `workspace-47-autonomous-20260803-01`
Last Cloud-accepted product/test checkpoint before this documentation publication:
`c57b3d0a3865b96b7e2a92ee773f09c594d287ad` on
`agent/workspace-47-autonomous-20260803`. The commit containing this handoff is a newer documentation
checkpoint; continuation must begin with `git fetch --prune origin` and exact local/remote HEAD,
upstream-divergence, cleanliness, and canonical-SHA verification. Canonical
`origin/feature/ws024-secure-tunnel-20260728` remains exactly
`9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

## Accepted automated portal boundaries

Do not repeat Melilla or Extremadura Slices 1-3.

- Melilla (`ES-PUB-0107`) is already automated-QA accepted at `IMPLEMENTED_NOT_E2E` /
  `E2E_PENDING`; release remains `VERIFIED_CONTRACT`, disabled, and physical E2E is still pending.
- Extremadura Slice 1 coordinator: RED `e63799299cf742ff7080cb2f0740a53512c7f321`; GREEN
  `cea8a1cce1fe799af20f5d44c2f5c541c3ca1e32`; focused Cloud
  `task_e_6a7b9c22701c83239a638302621e52dd` 4/4 Debug and Debug+QA Cloud
  `task_e_6a7b9da85e408323b4a995a865976efc` 8/8 accepted.
- Extremadura Slice 2 URL policy: RED `43a3dc288c7e84e7b86e3dc7aad5536745be2441`; GREEN
  `39a726986c727d9c0ba9354104576c78cc2334c9`; focused Cloud
  `task_e_6a7ba503d4508323aa465cb26b2b1872` 5/5 Debug and Debug+QA Cloud
  `task_e_6a7ba6e33e308323a7cb3e6c87fe2733` 10/10 accepted.
- Extremadura Slice 3 protocol RED `a38bf24b3e70529024b1f855e982bedda857ef52`; Cloud
  `task_e_6a7bab9930dc8323a8fdd7aa3bdca909` failed as intended at compile because the Extremadura
  adapter was absent. Protocol GREEN `f91f13183031075129e182784f0a30acd8912f13`; Cloud
  `task_e_6a7bae3ff44883239b6fa651eac2de15` accepted 6/6 Debug tests.
- Extremadura Slice 3 bridge/normalization RED `4cf1120dd45c233b1763d1ec07a41903adca0183`; Cloud
  `task_e_6a7bb047a488832396cf197cbdc366c6` failed as intended at compile because the bridge/signing
  wrappers were absent. GREEN `c57b3d0a3865b96b7e2a92ee773f09c594d287ad`; Debug Cloud
  `task_e_6a7bb2607b40832397a89d20654b9ad0` passed 11/11 and QA Cloud
  `task_e_6a7bb40f3148832382a0dea621bb2037` passed 11/11. Both exact-SHA checkouts were clean with
  dependency verification enabled and unchanged verification metadata.
- Direct Standards + Spec review and `git diff --check` found no remaining Critical/Important Slice 3
  defect. No Browser/MainActivity composition or profile/catalog mutation exists yet for Extremadura.

## Portal KPI and queue

- Catalog: 183 entries; 15 bound surfaces; 14 unique profile IDs; 168 unbound.
- Inventory: 162 `BROWSE_ONLY`, 10 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`,
  4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 90 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 10 `E2E_PENDING`, 4
  `E2E_VERIFIED`; classified research queue depth remains at least 16 surfaces.
- Portals integrated in generation 64: 0. Extremadura remains implementation-in-progress until Slice 5.
- Manual/physical E2E remains pending for Melilla, Extremadura, Sevilla, UGR, DGT, Cantabria, JCCM,
  and AEAT Client-TLS.

## Exact next portal implementation order

1. Continue only with Extremadura Slice 4 from
   `docs/superpowers/specs/2026-08-11-extremadura-sta-batch-integration-design.md` and
   `docs/superpowers/plans/2026-08-11-extremadura-sta-batch-integration.md`: wire the exact
   Extremadura STA bridge/signing/protocol path into `WebMessageBridge` / `BrowserScreen` /
   `MainActivity`, use the existing batch adapter resolver, preserve Melilla behavior and all
   ordinary-versus-batch arbitration/cancel/ownership boundaries, and TDD the behavior before GREEN.
2. Slice 5: add exact QA-only `extremadura-tramites` profile, protocol-registry binding, and
   `ES-PUB-0109` catalog promotion. Terminal automated state is at most
   `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; release remains disabled/non-E2E.
3. After full Extremadura automated acceptance, next implementation candidate is La Palma
   (`ES-PUB-0130`). Eivissa (`ES-PUB-0122`) and Formentera (`ES-PUB-0124`) remain research-only.
4. After Slice 5, push the exact SHA and run the canonical full Android gate in Codex Cloud only, plus
   permitted local Python catalog/regeneration and policy scans; update evidence truthfully.

## Safety / execution constraints

Worker delegation remains disabled: no native Codex/Luna implementation subagents, no `agent_spawn`,
and no delegated `codex/code-review`. Use the main Watchdog with Matt Pocock TDD/implementation and a
direct bounded Standards + Spec review. Every Android Gradle command stays in Codex Cloud
`workspace-47-android` through `$HOME/bin/w47-cloud`; never fall back to phone-local Gradle/JVM/Kotlin.
No APK install/launch, ADB/UIAutomator/Mobilerun/device control, authenticated government-portal
navigation, credentials/cookies/bearer data, certificate unlock/private-key material, real signing,
form submission, upload, payment, or administrative action.
