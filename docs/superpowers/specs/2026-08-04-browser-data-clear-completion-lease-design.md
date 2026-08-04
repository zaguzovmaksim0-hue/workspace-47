# Browser data-clear completion lease design

## Finding

`BrowserScreen` starts global WebView-data deletion through
`SiteDataCleaner.clearAllConfirmed()`. Cookie removal completes asynchronously.
The callback captures the request's `validatedEntryUrl` but reads the current
`webViewRef` only when its main-thread runnable executes.

`webViewRef` is remembered without `selectedServiceId` as a key, so it survives a
profile change. The profile-keyed `DisposableEffect` destroys the old WebView and
a new `AndroidView` can install a different WebView in the same reference. With no
request lease or disposal invalidation, a delayed completion from the previous
profile can set current UI result state and call `loadUrl(oldValidatedEntryUrl)` on
the new active WebView.

This is a stale asynchronous-completion ownership defect. The global deletion
itself remains user-confirmed and process-wide; the defect is the obsolete UI and
navigation completion, not the data-deletion scope.

## Scope

Production:

- Add `app/src/main/java/dev/junta/firmamobile/ui/BrowserDataClearCompletionLease.kt`.
- Modify `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt` only around
  the global-clear request, completion and profile/disposal boundary.

Tests:

- Add `app/src/test/java/dev/junta/firmamobile/ui/BrowserDataClearCompletionLeaseTest.kt`.
- Modify `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`
  to pin the integration boundary.

Evidence after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No cookie/storage deletion semantics, origin policy, portal profile/catalog,
WebView TLS, certificate, signing, release or dependency policy changes.

## Required behavior

1. Each confirmed global-clear action receives a unique completion token bound to
   the WebView that initiated it.
2. Starting a later request supersedes an earlier request.
3. Profile change or BrowserScreen disposal invalidates the outstanding token.
4. A completion may update UI state only once and only while its token is current.
5. A successful completion may reload only the exact initiating WebView while it
   is still the active `webViewRef` owner.
6. Stale completion remains ignored; the already-started global deletion is not
   cancelled or falsely reported as rolled back.
7. Callback threads continue to marshal UI/WebView work through the main handler.

## Implementation shape

Use a small generic, thread-safe lease with unique request objects held by an
`AtomicReference`. `begin(owner)` replaces the current request, `consume(request)`
atomically accepts it once, and `invalidate()` clears ownership. BrowserScreen
remembers one lease, invalidates it from the existing profile-keyed disposal path,
and checks both token consumption and exact WebView identity before reload.
