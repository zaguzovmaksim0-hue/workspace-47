# Interactive client-certificate login

Continuation of PR338 from verified candidate 69f5c43b. This bounded slice handles a real WebView `ClientCertRequest` when no site-specific transition matched. It does not implement the independent AutoFirma signature/retrieve/store protocol.

## Outcome

A public HTTPS page can request a client certificate without a hard-coded profile, source URL, query layout or POST replay. The user sees the actual TLS host/port and the currently displayed page as separate contexts. Unlocking is available without destroying the current WebView and does not itself approve disclosure. Existing narrow profile flows remain compatible; normal browsing never becomes a trusted signing origin.

## Ownership and lifetime

- One pending platform callback, bound to its original WebView, navigation epoch and monotonic five-minute deadline. The callback receives exactly one terminal response. Cancellation uses ignore, not a sticky cancel.
- Confirmation is bound to the certificate fingerprint the dialog displayed. A changed/locked/incompatible certificate cannot be silently substituted. Key type, validity, signing key usage, client-auth EKU and requested issuer compatibility use the same validation as existing TLS handlers. Private-key encoding is never read.
- Only foreground explicit confirmation calls proceed; TLS errors still cancel. Duplicate clicks, stale callbacks, changed host/port, navigation, destroyed renderer, timeout, cancellation and owner replacement must not disclose the certificate.
- WebView caches positive client-certificate choices. Clear those choices on certificate change/lock, browser exit, unexpected background and bounded expiry; preserve the normal WebView during successful cache clearing. Clearing this choice is not represented as logging out of a server session.
- An application-launched external activity may receive a short, owner/epoch-bound return lease. The certificate/document picker and explicit handoff can return without a blanket browser recreation. It never approves a pending certificate callback, extends its deadline, restores a stale owner, or replays a form. Expiry/change invalidates sensitive work, not ordinary cookies by default.

## Evidence

Canonical platform contracts checked 2026-10-01:
- Android WebViewClient: onReceivedClientCertRequest is a UI-thread callback; proceed/cancel may be cached for the process lifetime by host and port, ignore is not intentionally cached.
- Android ClientCertRequest: host/port, offered key types and principals describe the requesting TLS endpoint. This callback does not expose an isForMainFrame flag, so the UI must not claim the current page itself necessarily requested the certificate.
- Android WebView.clearClientCertPreferences completes asynchronously.

References: https://developer.android.com/reference/android/webkit/WebViewClient ; https://developer.android.com/reference/android/webkit/ClientCertRequest ; https://developer.android.com/reference/android/webkit/WebView#clearClientCertPreferences(java.lang.Runnable)

## Acceptance

Focused controller/lease tests exercise a previously unknown host, locked-to-unlocked continuation, invalid input, certificate mismatch, expiration, duplicate responses, offscreen confirmation, cancellation, navigation, owner changes, external return and cache cleanup. A wiring test uses JuntaWebViewClient's actual callback; legacy default-deny behavior remains when no interactive delegate is supplied. Shared certificate validation preserves previous rejection tests. The exact candidate must pass the repository GitHub Actions unit/lint/APK/emulator/Python/Go/security gate before a physical QA replacement. No real certificate, login or administrative document is used for automated tests.
