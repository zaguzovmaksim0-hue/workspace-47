# SSL Error UI Ownership Design

## Finding

Android `WebViewClient.onReceivedSslError(WebView, SslErrorHandler, SslError)` requires the host to cancel or proceed, and secure handling requires `cancel()`. Unlike modern resource/error callbacks, the API provides no `WebResourceRequest` and therefore no authoritative `isForMainFrame` ownership signal. The callback is documented as occurring while loading a resource.

Both `JuntaWebViewClient` and the dedicated `ClientAuthWebViewClient` currently convert every SSL callback from the active WebView into `BrowserErrorCode.SSL_ERROR`, which drives the browser-level assertive error banner and retry surface. A resource whose frame ownership is not proven can therefore mutate top-level application UI.

## Security invariant

An SSL callback without authoritative main-frame metadata must never weaken TLS handling and must not be promoted into top-level UI state solely from that callback.

- `SslErrorHandler.cancel()` remains unconditional and first in both clients.
- `JuntaWebViewClient` retains the sanitized `SSL_ERROR_CANCELLED` diagnostic.
- `ClientAuthWebViewClient` retains unconditional `requestHandler.abandon()` so the one-shot Client TLS grant cannot survive an SSL error.
- Neither client calls `callbacks.onBrowserError(SSL_ERROR)` from `onReceivedSslError` because frame ownership cannot be established.
- Modern `onReceivedError` and `onReceivedHttpError` keep their existing `request.isForMainFrame` UI ownership rules.
- No URL equality heuristic is introduced: `SslError.getUrl()` does not prove frame ownership.

## Scope

Production:
- `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthWebViewClient.kt`

Tests:
- `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`
- `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthWebViewClientTest.kt`

Evidence after GREEN:
- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `docs/threat-model.md`

## Non-goals

No change to certificate validation, hostname verification, Safe Browsing, navigation policy, origin/path allowlists, Client TLS authorization, portal profiles, retry policy, release policy, dependencies, or physical E2E claims. No APK/device/portal operation is required.

## Acceptance

1. A normal-client SSL callback always cancels, never proceeds, records the existing sanitized diagnostic, and does not publish a browser-level error callback.
2. A dedicated Client TLS SSL callback always cancels and abandons the grant, but does not publish a browser-level error callback.
3. Stale WebView behavior remains fail-closed.
4. Focused Debug/QA tests pass, followed by the complete relevant project gates.
