# NEXT CHAT HANDOFF — workspace-47 autonomous portal-first cycle

Updated: 2026-08-11, generation 49.

## Repository state to verify first

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on
  `agent/workspace-47-autonomous-20260803`.
- The accepted Sevilla product checkpoint before this documentation commit is
  `8dc623e250031ef97c1e71e56284c59ee83d3a45`, verified clean and exactly equal to local HEAD,
  upstream, and `origin/agent/workspace-47-autonomous-20260803`, divergence `0/0`.
- This documentation update is committed afterward, so resolve and verify its containing published
  HEAD on continuation rather than assuming `8dc623e...` remains final.
- Canonical `origin/feature/ws024-secure-tunnel-20260728` remains exactly
  `9c99bbfb36e13f88231d56001ccef8c4cbbce128`; no merge, rebase, force-push, rewrite, or PR occurred.
- Start with `prepare_task`, `git fetch --prune origin`, then verify main HEAD/upstream/remote,
  divergence, cleanliness, canonical SHA, and preserved Melilla state before any mutation.
- All Android Gradle remains Codex Cloud only in `workspace-47-android`; never use phone-local Gradle,
  JVM, Kotlin compiler, or daemon fallback.

## Sevilla ATSE accepted automated state

- `sevilla-atse-certificate-login` is implemented on main through exact product/test checkpoint
  `8dc623e250031ef97c1e71e56284c59ee83d3a45` with exact ATSE start URL/origin, profile-scoped shim,
  native fail-closed bridge validation, dedicated XAdES Enveloping adapter, protocol registry/runtime
  resolver wiring, and exact `ES-PUB-0016` catalog binding.
- Profile remains `VERIFIED_CONTRACT` / `QA_ONLY`, release-disabled; inventory/catalog remain
  `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. Physical Sevilla E2E is still manual and must not be promoted
  to `VERIFIED_E2E` from automated evidence.
- Focused Cloud task `task_e_6a7ad2628604832393f3ebfe5816d53e` verified exact `8dc623e...`,
  dependency verification enabled, Gradle exit 0, `BUILD SUCCESSFUL in 7m25s`, 40/40 focused tests,
  and clean Cloud checkout.
- Canonical full Cloud task `task_e_6a7ad4aaa5d48323a9aaaee668bc0a02` verified exact `8dc623e...`,
  Gradle exit 0, `BUILD SUCCESSFUL in 16m`, Debug 614/614 and QA 614/614 (1,228/1,228 total), zero
  failures/errors/skips, `lintDebug` and `lintQa` both 0 errors / 26 warnings, and successful
  `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`. Dependency verification stayed enabled,
  verification metadata stayed unchanged, and final Cloud `git status --short` was empty.
- Direct Standards + Spec review found no Critical or Important defect. Static profile/catalog
  invariants, `node --check` for the shim, `git diff --check`, and bounded unsafe/sensitive scans pass.
  No local Gradle/JVM/Kotlin execution was used.

## Portal KPI and research queue

- Catalog: 183 entries; 14 bound surfaces; 13 unique profile IDs; 169 unbound.
- Inventory: 163 `BROWSE_ONLY`, 9 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4
  `VERIFIED_E2E`, 4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated catalog: 91 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 9 `E2E_PENDING`, 4
  `E2E_VERIFIED`; discovery: 105 `REVIEWED`, 5 `RECHECK_REQUIRED`, 73 `DISCOVERED`.
- Classified research buffer remains at least 16 public surfaces.
- Portals accepted by the G48/G49 Sevilla publication slice: 1 — Sevilla ATSE exact certificate-login
  surface. Manual/physical E2E remains pending for Sevilla, UGR, DGT, Cantabria, and JCCM; AEAT still
  requires Client-TLS E2E. Real-portal JavaScript-dialog compatibility, TalkBack/physical visual
  validation, and Go race on supported Linux remain external/manual gates.

## Preserved Melilla — exact next implementation candidate

- Worktree `/data/data/com.termux/files/home/workspace-47-autonomous-g36-melilla`, branch
  `agent/g36-melilla-batch-contract`, was reverified clean at local
  `ce1b1639b322b616fb71cce12c73305db26e6a1a`; upstream remains
  `25df9f7ed5bef0387568d6c2db5c7083f154fa9b`; `@{upstream}...HEAD` is `0 1` (one local commit ahead).
- Preserved exact Cloud RED task `task_e_6a78dc14b2d48323887a6abf2ad48bce` on remote SHA `25df9f7...`
  failed Kotlin compilation because Android `JSONObject` has no `keySet`; the preserved local commit
  changes only that call to `json.keys().asSequence().toSet()` and passes `git diff --check` plus the
  bounded candidate-specific safety/static scan.
- Exact first GREEN Cloud command after publishing `ce1b163...` is Debug+QA unit tests filtered to
  `dev.junta.firmamobile.browser.MelillaBatchBridgeAdapterTest` and
  `dev.junta.firmamobile.browser.AfirmaJavascriptShimTest`.
- Before publication, fetch/verify the worker and push the existing commit without reset/rebase or
  force-push. Require exact-SHA terminal Cloud evidence before integration.
- Current main and Melilla both changed `AfirmaJavascriptShim.kt`, `WebMessageBridge.kt`,
  `afirma_shim.js`, and `AfirmaJavascriptShimTest.kt`; merge-tree shows textual conflicts because main
  added Cantabria/JCCM/Sevilla compatibility after Melilla branched. Integrate additively and preserve
  all existing profile-specific gates; do not whole-file replace current main.
- The preserved Melilla branch is only the batch bridge/URL-policy lifecycle slice. After its first
  GREEN and deterministic main integration, continue the existing plan's profile, registry, dedicated
  batch execution, runtime wiring, and truthful catalog phases with new TDD/Cloud gates.

## Subsequent exact order and safety

1. Finish preserved Melilla STA sequentially.
2. Implement research-ready `extremadura-tramites` (`ES-PUB-0109`) through the verified shared STA
   mechanics but a separate exact origin/profile/runtime-URL policy.
3. Implement La Palma (`ES-PUB-0130`) through the same verified shared STA seam.
4. Keep Eivissa (`ES-PUB-0122`) and Formentera (`ES-PUB-0124`) research-only unless stronger public
   unauthenticated evidence appears.

Worker delegation remains disabled: no native Codex/Luna implementation subagents, no `agent_spawn`,
and no delegated `codex/code-review`; use direct Matt Pocock TDD/implementation and direct bounded
Standards + Spec review. Never install/launch an APK or use ADB/device control; never enter
authenticated portal areas or use credentials/cookies/bearer/private certificate material; never
perform real signing, form submission, upload, payment, or administrative action.
