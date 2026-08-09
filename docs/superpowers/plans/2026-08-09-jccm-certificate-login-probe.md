# JCCM certificate-login probe implementation plan — 2026-08-09

1. Create an isolated worker worktree/branch from the current autonomous HEAD. No worker shares a
   writable worktree.
2. Matt `codex/tdd`: add a RED shim test at `AfirmaJavascriptShimTest` proving the exact profile and
   origin can intercept `QUJDREU=` + `SHA1withRSA` + `CAdES` + null properties, while generic/wrong
   variants remain unintercepted.
3. Implement only enough JS/Kotlin shim plumbing for GREEN; no submit or authenticated navigation.
4. Add a RED `MiniAppletBridgeAdapter` seam for decoded bytes `ABCDE`, exact profile/origin,
   `SHA1withRSA`, `CAdES`, and empty properties; add negative cases for every contract dimension.
5. Implement the minimal profile-scoped adapter GREEN using existing RSA certificate policy. Do not
   broaden generic CAdES acceptance.
6. Add a RED profile-registry test and the minimal `QA_ONLY` `jccm-certificate-login-probe` profile
   with the exact public `startUrl`; release must remain disabled.
7. Worker runs no Gradle locally. After each useful worker slice, commit/push the worker branch and
   run focused Gradle through `$HOME/bin/w47-cloud` against the exact pushed SHA.
8. After worker code is focused-GREEN, the orchestrator updates the shared JCCM inventory/catalog
   row, generator expectations, and generated resource; run lightweight Python generator tests.
9. Run Matt `codex/code-review`, `git diff --check`, sensitive/unsafe-pattern scan, then the canonical
   full Android Cloud gate against the exact pushed worker/integration SHA.
10. Integrate sequentially into `agent/workspace-47-autonomous-20260803`, push, verify remote SHA,
    and update ledger/handoff. Leave physical certificate-login/E2E as a manual future gate.
