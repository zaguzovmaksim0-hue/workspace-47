# WebView Geolocation Explicit-Disable Design

## Finding

`TrustedJuntaWebView.configureSettings()` hardens mixed content, file/content access,
file-URL network access, popups and media autoplay, but it does not call
`WebSettings.setGeolocationEnabled(false)`. The earlier approved implementation plans
explicitly require WebView geolocation to be disabled.

Android's `WebSettings` API treats geolocation as enabled unless explicitly disabled.
The current application still has two independent fail-closed controls: the manifest
requests neither coarse nor fine location permission, and `JuntaWebChromeClient`
denies every geolocation permission prompt. Therefore this finding is a missing
configuration invariant / defense-in-depth gap, not evidence that location data is
currently exposed.

## Scope

In scope:

- explicitly disable WebView geolocation in `TrustedJuntaWebView`;
- add a deterministic source-contract regression because Android exposes only the
  setter and the pinned Robolectric shadow has no reliable geolocation getter;
- preserve the existing manifest and `JuntaWebChromeClient` denial controls;
- record automated evidence and the limited security claim.

Out of scope:

- adding any Android location permission;
- implementing or enabling portal geolocation;
- changing JavaScript, DOM storage, cookies, Safe Browsing, mixed-content, file/content
  access, media playback, popups, user agent or WebView debugging;
- changing navigation, TLS, Client TLS, DNS, WebMessage, Afirma/signing, certificates,
  profiles/releases, dependencies or portal contracts;
- device/runtime geolocation testing.

## Contract

- `TrustedJuntaWebView.configureSettings()` must explicitly execute
  `setGeolocationEnabled(false)`.
- The production manifest must remain without `ACCESS_FINE_LOCATION` and
  `ACCESS_COARSE_LOCATION`.
- `JuntaWebChromeClient.onGeolocationPermissionsShowPrompt(...)` continues to deny the
  request with `allow=false` and `retain=false`.
- `JuntaWebChromeClient.onPermissionRequest(...)` continues to deny generic WebView
  resource permission requests.
- No compatibility claim is made for sites that require geolocation; this app does not
  currently expose that capability by design.

## Test strategy

`WebSettings` has no public geolocation getter and Robolectric 4.16.1 does not expose a
stable shadow getter for this flag. The smallest deterministic regression therefore
uses the repository's existing `BrowserSecurityRegressionTest.projectSource(...)`
pattern and requires the exact production call `setGeolocationEnabled(false)`.

The RED test must fail before production mutation. After the minimum setter is added,
run focused Debug/QA tests for `BrowserSecurityRegressionTest` and
`TrustedJuntaWebViewTest`, then the complete behavior-change verification matrix.

## Security claim

This milestone closes a missing WebSettings hardening layer. It does not establish a
previous location disclosure because the app already lacked Android location
permissions and explicitly rejected the WebChrome geolocation prompt. The resulting
boundary is intentionally redundant: platform permission absence, Chrome prompt denial
and WebSettings disablement all point to geolocation being unavailable.
