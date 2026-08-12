# Subframe Block Callback Isolation Design

## Finding

`JuntaWebViewClient.handleNavigation()` correctly consumes blocked subframe navigation,
but two paths still notify `BrowserNavigationCallbacks.onNavigationBlocked(...)` without
proved top-level ownership:

- `NavigationDecision.UpgradeToHttps` when the request is a subframe;
- every `NavigationDecision.Block`, including subframe and deprecated String-callback
  navigation.

`BrowserScreen` maps that callback to `blockedReason`, which renders an assertive
browser-level notice. Therefore untrusted iframe content can alter top-level UI state even
though the underlying navigation remains blocked and cannot reach native signing or
external-intent execution.

This is a frame-ownership / UI-confusion defect, not an allowlist bypass. The load is
already fail-closed.

## Chosen approach

Keep every existing `JuntaNavigationPolicy` decision and return value. For any request
that is not a modern main-frame navigation, consume and log the blocked decision without
calling application/UI callbacks. Only a modern main-frame request may publish
`onNavigationBlocked(...)`.

This is preferred over adding a new block-reason enum because the existing reason remains
accurate for diagnostics, and preferred over UI filtering because frame ownership belongs
at the WebView callback boundary where `isForMainFrame` is known.

## Scope

In scope:

- gate `onNavigationBlocked(...)` for `UpgradeToHttps` and `Block` on modern main-frame
  ownership;
- preserve sanitized diagnostic logging for blocked subframes/legacy callbacks;
- preserve main-frame POST/insecure/cross-profile/invalid/Play-fallback UI callbacks;
- add direct regression coverage for subframe and deprecated-callback paths;
- update authoritative evidence after fresh verification.

Out of scope:

- changing `JuntaNavigationPolicy`, URL parsing, allowlists, upgrade eligibility or return
  values;
- changing G19 Afirma delivery or G20 external-browser handoff behavior;
- changing SSL, Safe Browsing, network-error, renderer, download, file chooser, Client
  TLS, cookies, WebMessage, certificates or signing;
- device/portal interaction.

## Contract

For `NavigationDecision.UpgradeToHttps`:

- modern main-frame GET keeps the existing HTTPS upgrade and `view.loadUrl(...)`;
- modern main-frame non-GET remains consumed, logged and publishes
  `INSECURE_HTTP` to the application;
- subframe or legacy callback remains consumed and logged but publishes no application
  callback and never calls `loadUrl(...)`.

For `NavigationDecision.Block`:

- all requests remain consumed (`true`) and logged with the original reason;
- only modern main-frame requests publish `onNavigationBlocked(reason)`;
- subframe and deprecated String callbacks publish no application callback.

No diagnostic record may retain query/fragment secrets beyond the existing sanitizer.

## Test strategy

Add a RED regression in `JuntaWebViewClientTest` that sends a subframe insecure-HTTP
upgrade candidate, subframe cross-profile HTTPS, subframe invalid custom scheme, and a
deprecated-callback blocked URL. Before the fix, at least these paths populate
`RecordingBrowserCallbacks.events`; after the fix the list must stay empty while each
call returns `true` and diagnostics retain block reasons with `main_frame=false`.

Update the existing HTTP-upgrade regression so its main-frame POST remains the positive
application-callback control while the subframe case is expected to be silent at the
application boundary.

## Security/UX claim

The milestone prevents a non-main-frame navigation from creating a top-level blocked
notice through `onNavigationBlocked`. It does not change what URLs are allowed, and it
does not claim that subframes were previously able to navigate outside the policy; they
were already consumed fail-closed.
