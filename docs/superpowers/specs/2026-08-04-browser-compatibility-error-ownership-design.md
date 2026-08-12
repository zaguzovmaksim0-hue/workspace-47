# Browser compatibility-error ownership design

## Finding

`BrowserScreen` reports failure to attach the WebMessage listener or document-start
script by posting `compatibilityError = true` through the initiating WebView. The
posted runnable does not verify that this WebView is still the active instance.

`webViewRef` survives a selected-profile change while the profile-keyed disposal path
removes and destroys the old WebView and a later `AndroidView` factory can install a
replacement. If the old WebView's queued runnable executes after replacement, it can
set compatibility error state owned by the current browser UI. This is the same stale
asynchronous ownership class already guarded for page-progress and WebViewClient
callbacks.

The bridge attachment failure is local to the initiating WebView. It must not be
reported against a replacement WebView or later profile.

## Scope

Production:

- Modify `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt` only at the
  deferred WebMessageBridge compatibility-error delivery.

Tests:

- Modify
  `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`
  to pin exact active-WebView ownership before compatibility error mutation.

Evidence after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No bridge attachment API, origin rule, JavaScript content, profile/catalog status,
WebView TLS, Client TLS, certificate, signing, release or dependency policy changes.

## Approaches considered

1. **Exact identity guard inside the existing posted runnable — selected.** Check
   `webViewRef.get() === webView` immediately before setting compatibility state.
   This follows the adjacent progress-callback pattern and changes only stale delivery.
2. Key `compatibilityError` state by selected profile. This is broader and still does
   not distinguish two WebView instances created for the same profile.
3. Introduce another generic completion lease. This would work but adds lifecycle
   machinery for a single one-shot posted state mutation already carrying its owner.

The exact identity guard is the smallest complete repair.

## Required behavior

1. A bridge attachment failure may set `compatibilityError` only while the initiating
   WebView remains the exact active `webViewRef` instance.
2. A queued runnable from a released, destroyed or replaced WebView is ignored.
3. A failure on the current active WebView is still surfaced unchanged.
4. Delivery remains posted through the WebView thread boundary.
5. Existing bridge close, profile disposal, progress, navigation, TLS and signing
   behavior remains unchanged.

## Verification strategy

- Add a source-policy regression first and observe RED on the unguarded assignment.
- Apply the minimum identity check and observe focused GREEN.
- Run relevant BrowserSecurityRegression and BrowserScreen suites in Debug and QA.
- Run the full Android, lint, build, Python, artifact, release fail-closed and Go
  gates before evidence updates, commit and push.
