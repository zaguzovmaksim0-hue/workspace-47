# NEXT CHAT HANDOFF — Workspace-47 autonomous portal-first cycle

Date: 2026-08-11
Task: `workspace-47-autonomous-20260803-01`
Last Cloud-accepted product/test checkpoint before this documentation publication:
`cf5e1475270fdc48c009f6c460cab1b9e5c367fc` on
`agent/workspace-47-autonomous-20260803`. The commit containing this handoff is a newer documentation
checkpoint; continuation must begin with `git fetch --prune origin` and exact local/remote HEAD,
upstream-divergence, cleanliness, and canonical-SHA verification. Canonical
`origin/feature/ws024-secure-tunnel-20260728` remains exactly
`9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

## Melilla current automated boundary

The execution core, certificate/user-confirmation coordinator, PRE-ownership hardening, and runtime
request/reply adapter are independently Cloud-accepted. Do not repeat them and do not promote Melilla
yet.

- Runtime adapter design/plan: `bc842fb818d7d81296425b7bf98bd442a315b2f5`.
- Request normalization: valid RED `b956c9dbda5161093e098a4c21c42495a9623aee`; production
  `87171e24d4ca01f89dffc3464da74a58f40740ae`; Cloud GREEN
  `task_e_6a7b4b61633c83239a7ab0c924c9f5ef` passed 1/1.
- Reply sink: valid RED `815c30c14974efbf2e2a0849b2821ce7305a12cd`; production
  `efbf0c0381a905ff2424598519a449c7f9ac9b25`; Cloud GREEN
  `task_e_6a7b4f8a27a88323a30e826331450419` passed 2/2.
- Malformed UTF-8 ownership: regression `9072248837d75c744dcd06856388bcdf503cc003` produced the
  intended Cloud RED in `task_e_6a7b51f4651483238cd9c4551322e78d`; production repair is
  `cf5e1475270fdc48c009f6c460cab1b9e5c367fc`.
- Final focused Cloud GREEN `task_e_6a7b53772a308323b4440ff56e93c3e8` verified exact `cf5e147...`,
  dependency verification enabled, verification metadata unchanged, Gradle exit 0,
  `BUILD SUCCESSFUL in 6m 31s`, 3/3 focused tests passed, final Cloud worktree clean.
- Direct Standards + Spec review found no remaining Critical/Important defect in the bounded adapter
  slice. `git diff --check` and bounded sensitive/TLS/retry/HTTP/debug scans passed.

## Exact next Melilla slice — explicit batch cancellation

Use the existing `docs/superpowers/plans/2026-08-11-melilla-runtime-wiring.md`; do not create a new
runtime design.

1. Add RED coverage proving bridge-owned Melilla cancellation is one-shot: a validated
   `MINIAPPLET_BATCH_CANCEL` notifies the runtime exactly once for the owned request and bridge-wide
   teardown returns/notifies that same owned request exactly once.
2. Commit and push the RED-only SHA before any Gradle execution; run the exact focused test only in
   Codex Cloud `workspace-47-android` through `$HOME/bin/w47-cloud`.
3. Minimally add `onMelillaBatchCancel: (UUID) -> Unit` to `WebMessageBridge`; invoke it only when the
   Melilla reply registry actually wins terminal abandonment. Change `MelillaBatchReplyRegistry.abandonAll`
   to return the successfully abandoned request ids, mirroring the ordinary reply registry. Do not add a
   JS message type, origin, endpoint, retry, or new network/TLS path.
4. Commit/push production GREEN and run focused Cloud verification. Inspect complete diff, run local
   non-Gradle `git diff --check` plus bounded scans, and perform direct Standards + Spec review.
5. Then continue Slice 3: BrowserScreen/MainActivity composition plus fail-closed shared
   ordinary-versus-batch signing arbitration. Only after complete runtime acceptance perform a separate
   profile/public-catalog promotion; keep Melilla `VERIFIED_CONTRACT` / `QA_ONLY` and at most
   `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` until user-supplied physical evidence exists.

## Portal KPI and queue

- Catalog: 183 entries; 14 bound surfaces; 13 unique profile IDs; 169 unbound.
- Inventory: 163 `BROWSE_ONLY`, 9 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`,
  4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 91 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 9 `E2E_PENDING`, 4
  `E2E_VERIFIED`; classified public research buffer remains at least 16 surfaces.
- Portals newly integrated in generation 57 so far: zero; Melilla runtime work is not yet a catalog
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
