# Safe Browsing Frame UI Isolation Design

## Finding

`JuntaWebViewClient.onSafeBrowsingHit(...)` correctly invokes
`SafeBrowsingResponse.backToSafety(true)` for every hit, but it also publishes
`BrowserErrorCode.SAFE_BROWSING` to the application whenever the WebView owner is current,
without checking `request.isForMainFrame`.

Android defines `WebResourceRequest.isForMainFrame()` as false for subresources and
iframes. Therefore a Safe Browsing hit caused by an iframe can create a top-level browser
error/retry UI even though the malicious subresource is already rejected by the platform
response.

This is a frame-ownership/UI-confusion issue. It must not be fixed by weakening Safe
Browsing or by allowing the unsafe resource.

## Chosen approach

Always call `backToSafety(true)` before any ownership/UI decision, preserving the existing
fail-closed platform response. Keep the sanitized `SAFE_BROWSING_BLOCKED` diagnostic for
all active-owner hits. Deliver `onBrowserError(SAFE_BROWSING)` only when both:

- the callback belongs to the current WebView; and
- `request.isForMainFrame` is true.

A stale WebView remains unable to mutate application UI while its platform response still
returns to safety.

## Scope

In scope:

- add main-frame ownership to Safe Browsing application-error delivery;
- add main-frame positive, subframe negative, and stale-owner regression assertions;
- preserve unconditional `backToSafety(true)` and no `proceed()`/interstitial;
- preserve diagnostic sanitization;
- update evidence after fresh verification.

Out of scope:

- SSL error handling;
- changing Safe Browsing enablement, threat classification or reporting choice;
- navigation policy, Afirma/external handoff, DNS/TLS/Client TLS, WebMessage, cookies,
  certificates, signing, portal profiles or dependencies;
- device/browser/portal E2E.

## Contract

- Every Safe Browsing hit calls `callback.backToSafety(true)` regardless of frame or
  active WebView ownership.
- An active main-frame hit logs `SAFE_BROWSING_BLOCKED` and publishes
  `BrowserErrorCode.SAFE_BROWSING`.
- An active subframe hit logs `SAFE_BROWSING_BLOCKED` but publishes no application error.
- A stale/replaced WebView still calls `backToSafety(true)` but publishes no application
  error; existing stale-callback behavior remains fail-closed.
- `proceed()` and `showInterstitial()` are never called by this client.

## Test strategy

Extend `safeBrowsingHitsAlwaysReturnToSafety()` with a separate subframe response after
clearing the main-frame callback events. The RED state must show that subframe Safe
Browsing currently appends `error:SAFE_BROWSING`. After the minimum fix, both main-frame
and subframe responses must call `backToSafety`, while only main-frame changes
application state. Existing stale-WebView regression remains a third ownership control.

## Security/UX claim

The remediation isolates top-level Safe Browsing error UI from iframe hits while keeping
platform rejection unconditional. It does not reduce Safe Browsing protection and does
not claim the unsafe subresource was previously allowed.
