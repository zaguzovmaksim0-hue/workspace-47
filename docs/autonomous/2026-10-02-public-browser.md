# Ordinary browsing without a per-site profile

## Goal

Continue PR338 from verified `8729dc48421195f53ae1485e43c390b75877be92`. Viewing a public HTTPS page must not depend on implementing its certificate/signature protocol first. Two explicit entry routes are added: a public catalog fallback and manual `Abrir otra web` address input.

A profile-bound launch remains the original `PortalLaunchTarget` and is resolved by the strict existing registry/adapter/start-URL checks. A new `PortalOpenTarget.PublicWeb` carries only the exact public entry URL. It does not substitute a synthetic profile, borrow another same-origin profile, enable an excluded build profile or change capability metadata.

## Security and compatibility separation

The ordinary browser's selected profile is genuinely null. URL/trust policy never assigns a profile even when the page navigates to a known signature host or an active profile ID is supplied by a caller. No per-profile JavaScript bridge is attached. Generic native AutoFirma and real TLS-client-certificate callbacks retain their separately owned user consent paths. Opening a website is not certificate selection or permission to sign.

Normal profile-bound sessions retain their prior behavior; malformed explicit profile IDs do not silently become public browsing. The release/QA activation distinction remains effective for profile-specific signing, not for merely viewing the bundled public page.

Manual addresses are transient UI input and not persisted as catalog/trust entries. Validation requires HTTPS with a public DNS-shaped host and default/443 port, rejects raw controls/userinfo/local/IP literals and malformed URIs, and preserves the accepted path/query/fragment. It performs no DNS/network access and is not claimed to prevent arbitrary DNS rebinding. Invalid or oversized input is not silently truncated into a different accepted URL. Address input is protected from screenshots like other sensitive dialogs.

## UI behavior

`isEnabled` retains its strict profile-readiness meaning. Separate `canOpen` includes ordinary public viewing; `opensWithoutProfile` distinguishes the fallback label `Abrir web`. Existing support status is not promoted to verified merely because a URL can be viewed. Unknown/inaccessible/deprecated observations are not a new signing capability; the user can still try a syntactically valid public address.

Public browsing shows `Navegador sin perfil específico` with a short compatibility notice. Manual entry is reachable from the existing catalog search section. A fallback uses `metadata.entryUrl`, not a mismatched technical `launchUrl`. Forged item URLs and unknown catalog IDs are rejected. Current-site clearing reports platform limitations and clears certificate preferences separately; it does not invent a multi-origin profile or claim server logout.

## Implementation authors

Three isolated text-output coding tasks were requested with `gpt-6.1-sol` and `model_reasoning_effort="max"`: address validator/tests, catalog fallback/tests, and notice UI/tests. The broader catalog writer timed out; a smaller follow-up authored catalog tests. The integrator implemented the repository fallback. Returned code is applied by the integrator only after exact-source/path checks. A model task's launch is not proof of completed code or passing tests; actual authored results and integration changes are recorded separately.

The integrator owns nullable-profile policy wiring, MainActivity/manual entry, scoped data clearing, and integrated native/UI/trust tests. No claim is made that any subagent ran a real government workflow.

## Acceptance

Focused pure-JVM address tests, exact ordinary-vs-profile launch checks, registry/trust/navigation tests, synthetic Android public-browser TLS and native AutoFirma confirmation tests, manual-address UI tests, existing native protocol regressions and all canonical GitHub Actions checks on the exact candidate SHA. Existing strict `isEnabled`, `resolveLaunch`, activation, origin and adapter assertions are retained. Only the open-target test gains explicit public fallback assertions.

Official Android references checked on 2026-10-02:
- https://developer.android.com/reference/android/webkit/WebViewClient
- https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
- https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading

## Limits

This does not implement missing AutoFirma formats (batch/triphase/PDF/XAdES), WebAuthn, popup/opener, downloads, global intent registration or arbitrary non-HTTPS navigation. Native methods already supported remain available only through their actual confirmed protocol requests. No personal certificate, account, real administrative submission or unrelated device/network setting is used in automated tests.
