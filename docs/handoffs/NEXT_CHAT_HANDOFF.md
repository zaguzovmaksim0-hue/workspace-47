# NEXT CHAT HANDOFF — Workspace-47 autonomous portal-first cycle

Date: 2026-08-11
Task: `workspace-47-autonomous-20260803-01`
Last product/test checkpoint before this documentation publication:
`c36e98d73634f3a4d57f6d99a4465e08ed2e0cfc` on
`agent/workspace-47-autonomous-20260803`. The commit containing this handoff is the newer documentation
checkpoint; always begin continuation with `git fetch --prune origin` and verify exact local/remote HEAD,
upstream divergence, cleanliness, and canonical SHA rather than assuming a stale literal SHA.
Canonical `origin/feature/ws024-secure-tunnel-20260728` remains exactly
`9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

## Melilla current automated boundary

- Exact production commit `d56741ac44f4ffb4a9f731d38776003ffb2144ee` adds only
  `BatchSigningModels.kt`, `BatchSigningProtocolAdapter.kt`, and `MelillaBatchProtocolAdapter.kt`.
- Test-harness commit `c36e98d73634f3a4d57f6d99a4465e08ed2e0cfc` adds only the standard
  Robolectric runner/modes to `MelillaBatchProtocolAdapterTest`; assertions and contract fixtures are
  unchanged.
- Clean TDD RED: Cloud task `task_e_6a7b1aeaf50c83239ef6ca5e3a02307d` at `46dbdc...` failed only
  on the intended absent batch execution types/seam.
- Focused GREEN: Cloud task `task_e_6a7b2400628883239ab095c5333bd3f7` at exact `c36e98d...` passed
  4/4, `BUILD SUCCESSFUL in 4m 3s`, dependency verification enabled/unchanged, checkout clean.
- Combined Debug+QA: Cloud task `task_e_6a7b25c89b6c83238ec3cde02a7c6e75` at exact `c36e98d...`
  passed, `BUILD SUCCESSFUL in 7m 46s`; Debug XML 633/633 and QA XML 14/14 with zero
  failures/errors/skips across the Melilla URL policy, bridge, and protocol-adapter scope; checkout clean.
- Execution core semantics now verified: exact pre/post/getdata revalidation, evidence-backed batch JSON,
  URL-safe Base64 `json`/`certs`/`tridata`, standard Base64 `PK1`, ordered PRE inputs, `NEED_PRE`, injected
  `ProfileHttpTransport`, one-shot prepared state, bounded/cleared owned byte arrays, no retry/new network
  stack/TLS weakening.

## What remains for Melilla

Do not promote Melilla yet. The public generated entry remains unbound / `BROWSE_ONLY`. Next bounded
behavior slice must have a fresh subordinate design/plan and TDD seam for:

1. batch coordinator ownership of a certificate snapshot and explicit user-confirmation lifecycle;
2. exactly-once local PKCS#1 signing of each prepared PRE input with existing signing primitives;
3. runtime composition of the already-tested batch protocol adapter with the approved profile transport;
4. safe reply/callback delivery into the existing Melilla bridge lifecycle, including abandonment on
   navigation/profile changes and no callback after terminal failure;
5. no release enablement and no catalog/profile promotion until the full automated path is Cloud-verified.

Do not widen ordinary `SigningCoordinator` unless a demonstrated shared seam requires it. Preserve
current single-sign behavior and use existing certificate/signing primitives. All Gradle RED/GREEN gates
remain Codex Cloud only in saved environment `workspace-47-android`; commit/push exact SHA before every
Cloud Gradle run. Never fall back to phone-local Gradle/JVM/Kotlin.

## Portal KPI and queue

- Catalog: 183 entries; 14 bound surfaces; 13 unique profile IDs; 169 unbound.
- Inventory: 163 `BROWSE_ONLY`, 9 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`,
  4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 91 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 9 `E2E_PENDING`, 4
  `E2E_VERIFIED`; classified public research buffer remains at least 16 surfaces.
- Portals newly integrated in generation 53: zero; Melilla execution-core work is not a catalog/profile
  integration yet.
- Exact implementation order: finish Melilla coordinator/runtime path, then `extremadura-tramites`
  (`ES-PUB-0109`), then La Palma (`ES-PUB-0130`). Eivissa (`ES-PUB-0122`) and Formentera
  (`ES-PUB-0124`) remain research-only.
- Manual/physical E2E remains pending for Sevilla, UGR, DGT, Cantabria, JCCM, and AEAT Client-TLS.
  Add Melilla to the manual-E2E-only queue only after its full automated integration is complete; never
  promote it to `VERIFIED_E2E` without separate physical evidence.

## Safety / execution constraints

Worker delegation remains disabled: no native Codex/Luna implementation subagents, no `agent_spawn`,
and no delegated `codex/code-review`. Use the main Watchdog with Matt Pocock TDD/implementation and a
direct bounded Standards + Spec review. No APK install/launch, ADB/UIAutomator/Mobilerun/device control,
authenticated government-portal navigation, credentials/cookies/bearer data, certificate unlock/private
key material, real signing, form submission, upload, payment, or administrative action.
