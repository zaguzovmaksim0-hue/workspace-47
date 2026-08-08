# Browser data-clear epoch isolation design

**Milestone:** G29-01
**Date:** 2026-08-08

## Problem

`BrowserScreen` treats reload, back/home navigation and top-level page starts as sensitive
navigation boundaries: they call `advanceNavigationEpoch()`, which abandons pending
MiniApplet replies, advances the native navigation generation and notifies the signing owner.

The confirmed **clear current site** and **delete all browser data** actions already stop the
active WebView, cancel current signing state and clear pending UI state, but they do not
advance the navigation epoch until a later `loadUrl()` eventually produces
`onTopLevelNavigationStarted`.

That delay leaves a reproducible ownership window. The standard Afirma WebMessage route is
validated against the still-current main-frame origin but carries no epoch itself; a new
message from the old document can therefore reach `onAfirmaRequest` after the user has
confirmed data clearing and before the reload callback. MiniApplet routing snapshots
`currentNavigationEpoch()` when a new message arrives, so the same window can create a new
reply binding at the old epoch. The global delete path is especially exposed because cookie
deletion is asynchronous before reload.

This is a lifecycle/invalidation defect, not a URL-policy, TLS, certificate, signature or
cookie-deletion bypass.

## Security invariant

A user-confirmed browser-data clear is an immediate sensitive-flow boundary. Before site or
global deletion/reload can proceed, the application must:

1. abandon Client TLS state using the existing path;
2. advance the browser navigation epoch using `advanceNavigationEpoch()`;
3. thereby abandon pending MiniApplet replies and invalidate the old native signing context;
4. cancel current signing through the existing `onCancelSigning` callback;
5. only then perform data clear/reload work.

The later `onTopLevelNavigationStarted` may advance the epoch again. This is intentional and
matches the existing explicit-reload pattern; epochs are generations, not a page counter.

## Minimal implementation

Exact production file:

- `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`

Add one `advanceNavigationEpoch()` call to each confirmed handler:

- `onClearCurrentSite`, after `abandonClientAuth()` and before signing cancellation/data clear;
- `onDeleteAllBrowserData`, after `abandonClientAuth()` and before signing cancellation/async
  global deletion.

No bridge, router, cookie/storage implementation, network transport, TLS, profile catalog,
certificate code, signing algorithm or dependency changes are permitted in this milestone.

## Regression test

Exact test file:

- `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`

Add a narrow source-order regression that extracts the two confirmed action blocks and proves
that each contains `advanceNavigationEpoch()` before its corresponding data-clear operation.
It must fail on the current production source before the fix. Existing MiniApplet tests already
prove that an epoch change makes stale replies terminal without delivery; this new regression
binds the user action to that established invalidation primitive.

## Evidence and acceptance

After RED/GREEN, run:

- focused `BrowserSecurityRegressionTest` Debug + QA;
- adjacent `MiniAppletReplyRegistryTest`, `MiniAppletBridgeAdapterTest`, `WebMessageRouterTest`
  Debug + QA;
- runtime dependency locks, resolved core and portable AAPT2 gates;
- full Debug + QA JVM suites;
- `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`;
- full Python suite, Go test/vet/build;
- Android artifact verification and release-signing fail-closed;
- `git diff --check`, exact-scope review and sensitive/unsafe-addition scan.

Only after all gates pass update the authoritative evidence documents, rerun focused/policy
checks, stage exactly the milestone files, commit atomically and push the autonomous branch.
No device, APK launch/install, portal, credential, private-certificate, real-signing, upload,
payment or administrative action is part of this milestone.
