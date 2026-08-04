# Browser data-clear completion lease implementation plan

**Goal:** Prevent a delayed global-data-clear completion from mutating a later
profile or WebView instance.

**Exact files:**

- Modify `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`
- Add `app/src/test/java/dev/junta/firmamobile/ui/BrowserDataClearCompletionLeaseTest.kt`
- Add `app/src/main/java/dev/junta/firmamobile/ui/BrowserDataClearCompletionLease.kt`
- Modify `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Update evidence documents only after fresh verification.

## TDD sequence

1. Add a source-policy regression requiring a global-clear completion lease,
   profile/disposal invalidation, one-shot token consumption and exact initiating-
   WebView identity before reload. Run it and observe RED against current source.
2. Add focused behavioral tests for current one-shot consumption, supersession and
   invalidation of stale requests.
3. Implement the minimum generic atomic lease.
4. Integrate it only into the confirmed global-clear path and existing profile-
   keyed disposal path.
5. Run the new helper and policy tests, BrowserScreen/SiteDataCleaner tests, then
   Debug+QA focused browser regressions.
6. Run full Android, lint, build, Python, artifact, release fail-closed and Go gates.
7. Review exact diff, whitespace, secrets, personal data, unsafe WebView/TLS and
   generated artifacts; update evidence with observed results only.
8. Stage exact milestone files, commit atomically, push, fetch and verify remote SHA
   plus divergence 0/0.
