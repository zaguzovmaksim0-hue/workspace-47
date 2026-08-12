# WebView stale-callback lease implementation plan

**Goal:** Prevent callbacks from a released/replaced WebView client from mutating the
state owned by the current browser instance while preserving fail-closed platform
handling.

**Exact files:**

- Modify `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`
- Modify `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthWebViewClientTest.kt`
- Modify `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Modify `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthWebViewClient.kt`
- Modify `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Update evidence docs only after fresh verification.

## TDD sequence

1. Add the smallest normal-WebView behavior test: model a stale client through an
   active-view predicate; prove stale external/Afirma/navigation lifecycle callbacks
   cannot be delivered while stale navigation is consumed. Run focused Debug test
   and observe RED because the ownership dependency/behavior does not exist.
2. Add the Client TLS stale-view test and observe RED for the same ownership gap;
   assert stale Client TLS navigation is consumed and a stale certificate request is
   ignored/cleanup-triggering.
3. Implement the minimum active-view predicate and fail-closed helper in each client.
4. Pass `webViewRef.get() === candidate` predicates from `BrowserScreen` for both
   normal and dedicated clients.
5. Run focused Debug+QA tests for both client test classes and existing renderer/
   client-TLS lifecycle regressions.
6. Run full Debug+QA JVM tests, lint, Debug/QA/QA-AndroidTest builds, Python suite,
   Android artifact checks, release-without-private-signing fail-closed, and Go
   test/vet/build if the milestone remains behavior-changing.
7. Inspect complete diff, `git diff --check`, secret/PII/unsafe-pattern scans and
   generated artifacts. Update evidence docs only with observed results.
8. Stage exact milestone files, rerun staged checks, commit atomically, push, fetch,
   and verify exact remote SHA plus divergence 0/0.
