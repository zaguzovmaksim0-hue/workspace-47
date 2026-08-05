# Stale WebView network-diagnostic ownership design

## Finding

`JuntaWebViewClient` already receives an exact active-WebView predicate from
`BrowserScreen`. Navigation, page lifecycle, error UI and renderer callbacks use
that predicate so a released or replaced WebView cannot mutate the current browser
state. `shouldInterceptRequest()`, however, records every main-frame request into the
sanitized diagnostic logger without first checking that the callback belongs to the
active WebView.

A WebView may still deliver a late request callback while it is being released or
after a replacement has become current. The stale callback does not alter network
handling because `shouldInterceptRequest()` always returns `null`, but it can append
obsolete host/method/path-hash metadata to the process logger and, in QA, the
app-private diagnostic journal. This crosses the existing lifecycle/logging boundary:
diagnostics attributed to the current browser session can outlive their WebView
owner. It is not evidence of secret logging, TLS bypass, request interception or a
portal-origin bypass.

## Scope

Production:

- Modify
  `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt` so
  `shouldInterceptRequest()` records diagnostics only for the active exact WebView.

Tests:

- Extend
  `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt` with a
  stale-owner diagnostic regression. Existing active-request metadata coverage
  remains the positive control.

Evidence after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No request is intercepted, cancelled, retried or redirected. No origin/path rule,
DNS, TLS, Client TLS, cookie, bridge, certificate, signing, portal profile/catalog,
release, dependency or UI behavior changes.

## Approaches considered

1. **Guard request diagnostics at the WebView callback boundary — selected.** Reuse
   `isCurrentWebView(view)` at the start of `shouldInterceptRequest()` and return the
   unchanged `null` result for stale owners. This matches the existing ownership
   boundary and is the minimum behavior change.
2. Add WebView ownership tokens to `SanitizedLogger`. This would couple a generic
   process logger to browser lifecycle and require unrelated callers to understand a
   WebView-specific concept.
3. Keep stale diagnostics because they are sanitized. Sanitization limits content but
   does not establish lifecycle provenance; obsolete requests can still pollute the
   current QA diagnostic record.

## Required behavior

1. A main-frame request callback from the active WebView continues to record the same
   sanitized `NETWORK_REQUEST` metadata.
2. A main-frame request callback from a stale/released/replaced WebView returns `null`
   and records no request diagnostic.
3. If the active-owner predicate throws, request diagnostics fail closed and nothing
   is recorded; the request still receives the unchanged `null` interception result.
4. Subframe behavior remains unchanged: no `NETWORK_REQUEST` record is added.
5. SSL and Safe Browsing callbacks retain unconditional platform rejection before any
   UI ownership check; this milestone does not change those security paths or their
   existing diagnostic events.

## Verification strategy

- Add the stale-owner regression before production mutation and observe RED because
  the current client logs `NETWORK_REQUEST` without checking ownership.
- Add only the active-owner guard to `shouldInterceptRequest()` and observe focused
  GREEN.
- Run the complete `JuntaWebViewClientTest` in Debug and QA so the existing active
  metadata test proves logging was not disabled globally.
- Run full Android unit/build/lint, Python, Go, APK artifact and release fail-closed
  gates; inspect the complete diff and security/sensitive-content scans before
  evidence mutation, commit and push.
