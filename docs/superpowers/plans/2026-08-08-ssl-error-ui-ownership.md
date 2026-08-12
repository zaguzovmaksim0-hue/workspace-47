# SSL Error UI Ownership Implementation Plan

**Goal:** Prevent frame-unowned SSL callbacks from mutating top-level browser UI while preserving unconditional TLS cancellation and Client TLS grant abandonment.

## Exact files

- Modify test first: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`
- Modify test first: `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthWebViewClientTest.kt`
- Then minimal production change: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Then minimal production change: `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthWebViewClient.kt`
- Update evidence docs only after full verification.

## TDD sequence

1. RED: change/add focused regressions proving SSL cancellation still occurs but top-level `onBrowserError` is not delivered from the ownership-ambiguous callback; for Client TLS also prove the grant is abandoned/cleared.
2. Run only the two focused SSL regressions against unchanged production and observe expected callback-related failures.
3. GREEN: remove only `callbacks.onBrowserError(BrowserErrorCode.SSL_ERROR)` from the two `onReceivedSslError` implementations. Preserve `handler.cancel()` first, normal sanitized logging, and dedicated `requestHandler.abandon()`.
4. Re-run focused Debug and QA browser/Client TLS tests.
5. Run full dependency/toolchain, Debug/QA JVM, lint/build, Python, Go, artifact and release-fail-closed gates.
6. Run `git diff --check`, inspect exact diff, scan for sensitive data and unsafe TLS/WebView patterns.
7. Update ledger/handoff/security/test/threat evidence, run post-evidence focused/policy checks, commit atomically, push, and verify exact remote SHA.
