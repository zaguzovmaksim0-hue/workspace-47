# WebMessage bridge release ownership design

## Finding

`BrowserScreen` stores the normal WebView's `WebMessageBridgeAttachment` in a
process-local `AtomicReference`, but `AndroidView.onRelease` destroys the released
WebView without closing its attachment. Full profile disposal, renderer death and
explicit Client TLS entry do close the reference, but an `AndroidView` can also be
removed and recreated while the surrounding `BrowserScreen` remains composed. The
process-scoped Client TLS preference barrier is one such path: `CLEARING` or `FAILED`
suppresses the `AndroidView`, invokes `onRelease`, and a later successful recovery
creates another WebView.

`WebMessageBridgeAttachment.close()` is the ownership boundary that abandons pending
MiniApplet reply channels, invokes signing cancellation callbacks, removes the
document-start script, and removes the WebMessage listener. If `onRelease` does not
call it, the old attachment and its reply registry remain reachable until another
unrelated path closes the global reference. A new factory currently uses
`bridgeRef.set(attachment)`, which can overwrite that reference without closing the
old attachment. This is a stale-resource lifetime defect, not evidence of a portal,
origin, TLS or signing-policy bypass.

## Scope

Production:

- Add `app/src/main/java/dev/junta/firmamobile/ui/BrowserOwnedResourceLease.kt`.
- Modify `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt` to bind each
  bridge attachment to its exact WebView owner and release it from `onRelease`.

Tests:

- Add `app/src/test/java/dev/junta/firmamobile/ui/BrowserOwnedResourceLeaseTest.kt`.
- Extend
  `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`
  with the integration ownership invariant.

Evidence after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No WebMessage payload, JavaScript shim, origin allowlist, profile/catalog status,
WebView TLS, Client TLS grant, certificate, signing, release, dependency or UI-copy
policy changes.

## Approaches considered

1. **Owner-bound closeable lease — selected.** Keep one atomic binding of exact owner
   identity plus attachment. Binding a replacement closes the superseded resource;
   releasing an owner closes only that owner's current resource; full disposal closes
   whichever resource is current. The helper is pure Kotlin and behavior-testable.
2. Close `bridgeRef.getAndSet(null)` unconditionally in every `onRelease`. This can let
   a late release callback from an old WebView close a newer WebView's attachment.
3. Store a `Pair<WebView, WebMessageBridgeAttachment>` directly in `BrowserScreen` and
   duplicate compare-and-set loops locally. This can enforce ownership but is harder
   to test and repeats lifecycle mechanics inside the composable.

The owner-bound lease is the smallest reusable boundary that covers both missing
cleanup and stale-release ordering without broad refactoring.

## Required behavior

1. Binding an attachment establishes exact identity ownership by its WebView.
2. Binding a replacement atomically supersedes and closes the previous attachment.
3. Releasing the current exact owner clears and closes its attachment exactly once.
4. Releasing a stale or unrelated owner does not clear or close the current
   attachment.
5. Full BrowserScreen disposal closes the current attachment.
6. Navigation-epoch invalidation continues to abandon pending MiniApplet requests on
   only the current attachment.
7. Existing renderer-death and Client TLS entry paths continue to close current bridge
   state, now through the same lease.
8. `AndroidView.onRelease` releases the bridge before destroying the WebView.

## Verification strategy

- Add the pure lease behavior regression first and observe RED because the owner-bound
  lease does not exist.
- Implement only the lease and observe focused GREEN.
- Add the BrowserScreen source-policy integration regression and observe RED because
  the screen still uses an unowned `bridgeRef` and does not release it in `onRelease`.
- Replace only bridge-reference lifecycle operations with the lease and observe
  focused Debug and QA GREEN.
- Run full Android unit/build/lint, Python, Go, APK artifact and release fail-closed
  gates; inspect the complete diff and sensitive/unsafe-pattern scans before evidence,
  commit and push.
