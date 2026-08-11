# NEXT CHAT HANDOFF — Workspace-47 autonomous portal-first cycle

Date: 2026-08-11
Task: `workspace-47-autonomous-20260803-01`
Last verified product/test checkpoint before this documentation publication:
`35b2c1084f003f8e97a5cb610045c05e1b83838e` on
`agent/workspace-47-autonomous-20260803`. The commit containing this handoff is a newer documentation
checkpoint; continuation must begin with `git fetch --prune origin` and exact local/remote HEAD,
upstream-divergence, cleanliness, and canonical-SHA verification. Canonical
`origin/feature/ws024-secure-tunnel-20260728` remains exactly
`9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

## Melilla current automated boundary

The execution core and certificate/user-confirmation coordinator are independently Cloud-accepted.
Do not repeat them and do not promote Melilla yet.

- Execution-core evidence remains `d56741ac44f4ffb4a9f731d38776003ffb2144ee` plus test harness
  `c36e98d73634f3a4d57f6d99a4465e08ed2e0cfc`; combined Cloud task
  `task_e_6a7b25c89b6c83238ec3cde02a7c6e75` passed Debug 633/633 and QA 14/14.
- Coordinator prepare/ordered-confirm path is published through
  `2d89c2592564920b6e57664ef2c13dc5861faf0c`; focused Cloud task
  `task_e_6a7b3555442c8323a4d9ac81349dfa33` passed 2/2.
- Foreign-PRE ownership regression harness is published through test-only
  `6b1b0931a33580b8456006de44b8f7df4c5c7ca7`. Valid Cloud RED
  `task_e_6a7b3c7b461483239c8de9a1dd304692` ran 3 tests with exactly one intended failure:
  actual `[prepare, sign:pre-one, complete, failure:PROTOCOL_FAILED]` versus required
  `[prepare, failure:PROTOCOL_FAILED]`.
- Production ownership hardening is `35b2c1084f003f8e97a5cb610045c05e1b83838e`: a foreign
  `BatchPreSignResult` is rejected before `withInput`, local private-key signing, and adapter completion;
  one-shot prepared-state consumption remains unchanged.
- The first GREEN task `task_e_6a7b3e828acc8323b1f177ab0834f99a` was Cloud infrastructure
  non-acceptance only: Gradle wrapper download returned HTTP 503 before tasks ran. No local fallback.
- Bounded retry `task_e_6a7b3ef125bc8323a83846a6022db832` passed 3/3 coordinator tests at exact
  `35b2c...`, `BUILD SUCCESSFUL in 4m 2s`.
- Final coordinator acceptance `task_e_6a7b40692674832395efc1143bd08ac3` verified exact `35b2c...`,
  dependency verification enabled/unchanged, `BUILD SUCCESSFUL in 12m 25s`, Debug 636/636 plus QA
  17/17 focused (653/653 observed total), and a clean Cloud checkout.
- Direct Standards + Spec review found no Critical/Important issue. `git diff --check` and bounded
  secret/TLS/retry scans passed. Ordinary single-sign `SigningCoordinator` was not changed.

## Exact next Melilla slice — runtime wiring

Create a fresh subordinate design/plan before production mutation. The bounded runtime slice should:

1. adapt accepted `MelillaBatchBridgeRequest` into `NormalizedBatchSigningRequest` using the already
   validated profile/origin/navigation/document binding; do not re-parse or broaden the portal ABI;
2. adapt `MelillaBatchReplyChannel` to `BatchSigningReplySink` without copying or persisting opaque
   response bytes longer than delivery requires;
3. add an explicit native batch-cancel notification from `WebMessageBridge`, including navigation,
   document teardown, JS cancel, background/certificate-lock paths, and no late success callback;
4. compose `MelillaBatchProtocolAdapter` with the existing `HttpsProfileHttpTransport` and a dedicated
   `BatchSigningCoordinator` in `MainActivity`; do not create another TLS/network stack or retry path;
5. wire `BrowserScreen` so batch and ordinary signing share confirmation/status UX safely without two
   concurrent operations claiming the same certificate/private-key UI state;
6. TDD the request/reply conversion and cancellation/terminal-ownership seams first; commit/push every
   RED/GREEN SHA before Codex Cloud Gradle; no phone-local Gradle/JVM/Kotlin;
7. only after the complete automated runtime path is Cloud-verified, perform a separate profile/public
   catalog promotion slice using the canonical catalog generator. Keep Melilla `VERIFIED_CONTRACT` /
   `QA_ONLY`, inventory at most `IMPLEMENTED_NOT_E2E`, generated state at most `E2E_PENDING`, and
   release disabled. Never infer `VERIFIED_E2E`.

## Portal KPI and queue

- Catalog: 183 entries; 14 bound surfaces; 13 unique profile IDs; 169 unbound.
- Inventory: 163 `BROWSE_ONLY`, 9 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`,
  4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 91 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 9 `E2E_PENDING`, 4
  `E2E_VERIFIED`; classified public research buffer remains at least 16 surfaces.
- Portals newly integrated in generations 54/55: zero; Melilla coordinator hardening is not a catalog
  integration.
- Exact implementation order: finish Melilla runtime/profile/catalog, then `extremadura-tramites`
  (`ES-PUB-0109`), then La Palma (`ES-PUB-0130`). Eivissa (`ES-PUB-0122`) and Formentera
  (`ES-PUB-0124`) remain research-only.
- Manual/physical E2E remains pending for Sevilla, UGR, DGT, Cantabria, JCCM, and AEAT Client-TLS.
  Melilla also lacks physical E2E and must never become `VERIFIED_E2E` without separate user-supplied
  physical evidence.

## Safety / execution constraints

Worker delegation remains disabled: no native Codex/Luna implementation subagents, no `agent_spawn`,
and no delegated `codex/code-review`. Use the main Watchdog with Matt Pocock TDD/implementation and a
direct bounded Standards + Spec review. Every Android Gradle command stays in Codex Cloud
`workspace-47-android` through `$HOME/bin/w47-cloud`; never fall back to phone-local Gradle/JVM/Kotlin.
No APK install/launch, ADB/UIAutomator/Mobilerun/device control, authenticated government-portal
navigation, credentials/cookies/bearer data, certificate unlock/private-key material, real signing,
form submission, upload, payment, or administrative action.
