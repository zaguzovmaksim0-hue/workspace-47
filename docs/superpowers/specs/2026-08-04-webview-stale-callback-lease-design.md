# WebView stale-callback lease design

## Finding

`BrowserScreen` already binds progress updates and renderer-death recovery to the
exact active `WebView`. Normal `JuntaWebViewClient` callbacks and several
`ClientAuthWebViewClient` callbacks do not have the same ownership check. Their
shared `BrowserNavigationCallbacks` can therefore still mutate browser state,
open an external URI, surface an Afirma request, or report a browser error if an
obsolete client delivers a callback after its `WebView` has been released or
replaced.

This is a lifecycle/ownership defect rather than a URL-policy bypass: the current
navigation and TLS policies remain fail-closed for the callback they are given,
but an obsolete WebView instance must not be allowed to act on the state owned by
a newer instance.

## Evidence and reproduction

- `BrowserScreen` uses `webViewRef.get() === webView` for page-progress delivery.
- Renderer death is already protected with `webViewRef.compareAndSet(view, null)`.
- `JuntaWebViewClient` invokes navigation, Afirma, state and error callbacks without
  checking whether the callback's `view` is still the active WebView.
- `ClientAuthWebViewClient` similarly emits top-level, blocked/error and renderer
  callbacks after its one-shot view has become obsolete; `abandon()` protects the
  client-certificate grant but not those UI callbacks.
- The prior stale-session hardening commit `f84ea27ae47e1b85bd0dcef81979ff0ae377714b`
  intentionally bound renderer recovery to the exact affected WebView, establishing
  the active-instance ownership invariant, but did not generalize it to the other
  callbacks.

A deterministic Robolectric regression can model replacement by constructing a
client with an active-view predicate, flipping that predicate to false, and invoking
old-client callbacks directly. The desired behavior is that stale navigation is
consumed, stale state/Afirma/error callbacks are not delivered, and security-critical
platform responses (for example SSL cancellation and Client TLS request rejection)
remain fail-closed.

## Scope

Production:

- Modify `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`.
- Modify `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthWebViewClient.kt`.
- Modify `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt` only to pass
  active-WebView ownership predicates to each client.

Tests:

- Modify `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`.
- Modify `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthWebViewClientTest.kt`.
- Extend `BrowserSecurityRegressionTest.kt` only if integration/source coverage is
  needed after the behavior tests.

Evidence after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No profile/catalog, origin allowlist, TLS trust, signing protocol, certificate cache,
dependency, release, or portal status changes.

## Required behavior

1. A callback from the currently active normal or Client TLS WebView behaves exactly
   as before.
2. A stale normal WebView navigation is consumed and cannot open an external URI,
   emit an Afirma request, mutate navigation state, arm Client TLS, or overwrite UI
   error/address state.
3. A stale SSL/safe-browsing callback still rejects the platform request, but does
   not overwrite the active browser's UI state.
4. A stale normal client-certificate request remains ignored.
5. A stale Client TLS request is ignored and its request handler is abandoned so the
   process-scoped client-certificate preference cleanup remains fail-closed.
6. Stale Client TLS navigation is consumed and cannot mutate the active browser UI.
7. Renderer death from an obsolete view remains acknowledged without triggering
   active-view recovery; the existing exact-view invariant is preserved.
8. The active-view predicate itself must fail closed if it throws.

## Implementation shape

Each WebView client receives an `isActiveWebView: (WebView) -> Boolean` dependency.
The default remains `true` for isolated callers/tests, while `BrowserScreen` supplies
identity checks against `webViewRef`. A tiny private helper converts predicate
exceptions into `false`. Security-critical WebView platform callbacks perform their
reject/cleanup action before suppressing stale UI callbacks. No new asynchronous
state or persisted state is introduced.
