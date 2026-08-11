# NEXT CHAT HANDOFF — Workspace-47 autonomous portal-first cycle

Date: 2026-08-11
Task: `workspace-47-autonomous-20260803-01`
Last Cloud-accepted product/test checkpoint before this documentation publication:
`60afb7ab8b3682b6a85424a71aa0de172b1d26d6` on
`agent/workspace-47-autonomous-20260803`. The commit containing this handoff is a newer documentation
checkpoint; continuation must begin with `git fetch --prune origin` and exact local/remote HEAD,
upstream-divergence, cleanliness, and canonical-SHA verification. Canonical
`origin/feature/ws024-secure-tunnel-20260728` remains exactly
`9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

## Melilla accepted automated boundary

Do not repeat Melilla runtime/profile/catalog implementation. Melilla is accepted at the automated QA
boundary but remains explicitly non-E2E and release-disabled.

- Catalog promotion `037b3614298cf2c33089b30fb73b789a26077249` exposed the missing protocol
  registry binding in Cloud task `task_e_6a7b8975ac94832392f8ab5ce0fd9411`.
- Direct registry RED `f3b4fb089039a3c57cca67ecc6c27483266b4133` was confirmed by
  `task_e_6a7b8d54a73883238c96ae027f5dafd9`.
- Exact production binding `552189a26daddb329815a9deeec5e604e7421b78` passed the intended registry,
  parser, and repository seams in `task_e_6a7b8f6f7c5883239e9196b25afe1442`; only a stale exhaustive
  compatible-count test remained.
- Test-only accounting `60afb7ab8b3682b6a85424a71aa0de172b1d26d6` passed focused Cloud GREEN
  `task_e_6a7b916667288323a6ee8507b88818f7`: Debug 647/647 plus QA 35/35 = 682/682.
- Canonical full Cloud gate `task_e_6a7b9341699483238dbaba576b189ed8` accepted exact `60afb7a...`:
  `BUILD SUCCESSFUL in 15m40s`, Debug 647/647 and QA 647/647 = 1,294/1,294, lint Debug/QA zero errors,
  all requested assemble/verification tasks passed, dependency verification remained enabled,
  verification metadata was unchanged, and final Cloud checkout was clean.
- Local catalog suite is 10/10; canonical catalog regeneration is byte-identical; `git diff --check`
  and bounded unsafe/TLS/credential/retry/dependency-verification scans pass. Direct Standards + Spec
  review found no remaining Critical/Important defect in the bounded catalog/registry slice.
- QA state is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; release remains `VERIFIED_CONTRACT`, disabled,
  with `resolveLaunch == null`. Never promote Melilla to `VERIFIED_E2E` without user-supplied physical
  evidence.

## Portal KPI and queue

- Catalog: 183 entries; 15 bound surfaces; 14 unique profile IDs; 168 unbound.
- Inventory: 162 `BROWSE_ONLY`, 10 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`,
  4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 90 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 10 `E2E_PENDING`, 4
  `E2E_VERIFIED`; classified public research buffer remains at least 16 surfaces.
- Portals accepted in generation 62: Melilla (`ES-PUB-0107`) = 1.
- Manual/physical E2E remains pending for Melilla, Sevilla, UGR, DGT, Cantabria, JCCM, and AEAT
  Client-TLS.

## Exact next portal implementation order

1. `extremadura-tramites` (`ES-PUB-0109`). Reuse only the already researched public byte-identical STA
   batch ABI where official public evidence still supports it; preserve an exact profile-specific
   origin/URL policy and fail closed on any contract mismatch. Re-check current official public
   unauthenticated evidence before production mutation and create a narrow subordinate design/plan.
2. La Palma (`ES-PUB-0130`) after Extremadura is integrated and accepted.
3. Eivissa (`ES-PUB-0122`) and Formentera (`ES-PUB-0124`) remain research-only until their public
   evidence becomes implementation-ready.

## Safety / execution constraints

Worker delegation remains disabled: no native Codex/Luna implementation subagents, no `agent_spawn`,
and no delegated `codex/code-review`. Use the main Watchdog with Matt Pocock TDD/implementation and a
direct bounded Standards + Spec review. Every Android Gradle command stays in Codex Cloud
`workspace-47-android` through `$HOME/bin/w47-cloud`; never fall back to phone-local Gradle/JVM/Kotlin.
No APK install/launch, ADB/UIAutomator/Mobilerun/device control, authenticated government-portal
navigation, credentials/cookies/bearer data, certificate unlock/private-key material, real signing,
form submission, upload, payment, or administrative action.
