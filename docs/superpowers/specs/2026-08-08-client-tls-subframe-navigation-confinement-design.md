# Dedicated Client TLS Subframe Navigation Confinement Design

## Finding

`ClientAuthWebViewClient.shouldOverrideUrlLoading(WebView, WebResourceRequest)` currently returns `false` for every request with `isForMainFrame=false` before applying its existing `isAllowed()` origin check. As a result, the dedicated one-shot Client TLS WebView can load an arbitrary off-origin subframe even though its main-frame navigation is confined to the profile's exact Client TLS request/source origins.

The binding Client TLS design requires a new dedicated bridge-free WebView, an exact authorized target, and source/request-origin confinement. The universal design also treats frame ambiguity as hostile because `ClientCertRequest` itself exposes host/port but not frame or path. This finding does not demonstrate certificate exfiltration or a TLS-validation bypass; it is an unnecessary remote-content/trust-surface expansion inside a certificate-authenticated dedicated WebView.

A related ownership defect exists on the same boundary: `blockNavigation()` always publishes `onNavigationBlocked(INVALID_URL)`. If an off-origin subframe is newly consumed, that callback would let a subframe mutate top-level application UI. The deprecated String callback likewise cannot prove main-frame ownership and currently publishes that callback for blocked URLs.

## Constraints

- Preserve the exact Client TLS profile, target authorization, host/port certificate checks, TTL/epoch checks, issuer/keyUsage/EKU policy, preference-clearing barrier, and one-shot grant semantics.
- Preserve allowed source/request-origin subframes for compatibility; do not impose an unsupported path-level scope after authentication.
- Do not broaden any origin allowlist or release profile.
- Do not create a new WebView bridge, restore state, external handoff, or custom TLS behavior.
- An unsafe modern subframe must be consumed and must abandon/clear the one-shot Client TLS grant, but must not publish a top-level blocked-navigation callback.
- An unsafe modern main-frame navigation remains consumed, abandons the grant, and retains the existing blocked-navigation callback.
- A deprecated String callback remains unable to expand the grant; an unsafe URL is consumed and abandons the grant, but no top-level callback is published because frame ownership is unknown.

## Approaches considered

1. **Block every subframe in the dedicated WebView.** Strongest isolation, but it risks breaking authenticated pages that legitimately load same-authority frames and exceeds the demonstrated defect.
2. **Apply the existing origin predicate to all modern requests and gate application UI on authoritative main-frame metadata.** Recommended. It closes only the off-origin subframe gap, preserves same-origin behavior, and reuses the established exact-origin policy.
3. **Leave subframe loading unchanged and only suppress its UI callback.** Rejected because the external-content surface remains open.

## Design

For the modern `WebResourceRequest` callback:

1. Reject stale WebView ownership exactly as today.
2. Evaluate the existing `isAllowed(request.url.toString())` for both main-frame and subframe requests.
3. If allowed, return `false` unchanged.
4. If disallowed, abandon the request handler/grant and return `true`.
5. Publish `onNavigationBlocked(INVALID_URL)` only when `request.isForMainFrame` is true.

For the deprecated String callback:

1. Preserve exact allowed-URL behavior.
2. For a disallowed URL, abandon the request handler/grant and return `true`.
3. Do not publish an application blocked-navigation callback because frame ownership is unavailable.

The smallest implementation is to make `blockNavigation` accept an explicit `notifyApplication` boolean and call it with authoritative ownership only.

## Exact files

Production:
- `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthWebViewClient.kt`

Tests:
- `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthWebViewClientTest.kt`

Evidence after GREEN/full gates:
- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `docs/threat-model.md`

## Test contract

RED must prove on unchanged production that:

- an off-origin modern subframe returns `true`, abandons/clears the grant, and emits no application callback;
- an allowed source/request-origin modern subframe still returns `false` and does not abandon the grant;
- an off-origin modern main-frame remains blocked and still emits `blocked:INVALID_URL`;
- an off-origin deprecated String callback remains blocked/abandoned but emits no application callback.

The first negative must fail before production mutation specifically because unchanged production returns `false` for every subframe. The legacy UI assertion may also fail on unchanged production and is part of the same frame-ownership boundary.

## Non-goals and external gates

- No claim that existing Client TLS credentials were previously disclosed.
- No AEAT profile promotion and no change to Carné Joven's existing E2E scope.
- No physical portal/device validation in this autonomous milestone. Carné Joven/AEAT compatibility remains subject to existing manual gates where applicable.
