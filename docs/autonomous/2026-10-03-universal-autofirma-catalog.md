# One default AutoFirma route for the complete public catalog

## User outcome and baseline

The user asked for a single working AutoFirma path for all180 catalog sites, instead of requiring another per-site implementation. The bundled catalog currently contains183 entries,183 distinct inventory IDs and137 distinct effective launch addresses (46 aliases). All entry and effective launch addresses are public HTTPS/default443.

Before this change, MainActivity's primary catalog action called resolveOpenTarget, which preferred an enabled site-specific profile. The common native AutoFirma engine already accepted supported direct/indirect protocol requests without a profile, but an active profile could select different script interception and signing adapters first. Therefore having the generic engine in the project did not mean the user selected that same route for every catalog item.

## Default and explicit compatibility

The primary button is now “Abrir con AutoFirma”. It calls resolveUniversalOpenTarget, which requires a real bundled portal ID and exact bundled entry URL, then opens its authoritative launchUrl (or entryUrl when no alias exists) in the ordinary profileId=null browser. The same rule applies to QA and release profile trust modes. Altered item flags or a forged profile ID cannot grant another destination or a profile capability.

This is an actual MainActivity/catalog change, not just an additional hidden API or a status label. The common native sign/selectcert/batch/triphase handlers and independent TLS/HTTP authentication handling remain the same modules for every entry. No existing request is replayed, origin spoofed, certificate auto-approved or in-progress form switched to another engine.

Reviewed per-site integrations remain as the clearly separate “Modo específico del portal” action. This preserves optional proprietary adapters instead of deleting known working exceptions. The old strict resolver, activation rules and test assertions are not rewritten to claim those profiles are universally verified. Two existing tests of a specific reviewed profile now select the explicitly named profile action; their original consent and retained-WebView assertions remain unchanged.

The catalog notice explicitly says that opening a site through the common engine does not mean its real administrative procedure has been verified. A unified launcher cannot make an unsupported requested signature policy/format, inaccessible service, missing certificate or provider-dependent login succeed by silently dropping requirements.

## Protocol coverage for every entry

Two new unit test classes execute the actual bundled catalog in QA and release trust modes. The route test covers every item and alias, identical default targets, forged inputs, retained compatibility behavior and invalid alternate addresses. The dispatch test feeds each of the183 effective sources through the actual WebViewClient, native parser and operation factory for eight representative forms: CAdES, XAdES, PDF/PAdES, certificate selection, local JSON batch, XAdES three-phase, an indirect fileid request and an Android intent wrapper.

This gives2928 route/parse/operation-selection checks (183×2×8), not2928 cryptographic or government signatures. Input fixtures are synthetic and no key is used by this catalog-level test. Existing engine tests separately validate cryptographic output, consent, one-shot delivery and partial/uncertain protocol outcomes. Iframes, POST callbacks and a stale WebView still cannot acquire the main page's authority.

The Android regression traverses the real catalog UI for both a direct portal and an alias with reviewed profiles. It selects the default button, requires the public-browser notice, confirms absence of the legacy profile JavaScript object and exercises a synthetic native request to the genuine locked-certificate consent screen. It checks the same original WebView and retained form text. It neither unlocks nor signs nor sends a server cancellation.

## Public website inventory check

A separate bounded public probe visits each distinct effective HTTPS target once, follows up to five HTTPS redirects, verifies TLS and public DNS results, and reads at most256KiB of public HTML. It sends no cookies, client certificate, credentials, scripts or form submissions. The results are joined back to all183 catalog entries; duplicate aliases are not falsely counted as separate network visits.

HTTP status, timeout, access restriction and visible AutoScript/MiniApplet text are observations of that public request only. They do not prove live signature compatibility. No visible library on a landing page does not prove the authenticated procedure lacks it; a script-free probe's403 does not prove that a real user's WebView is blocked. Every row retains liveSignatureAcceptance=NOT_TESTED. The audit report is separate from native route coverage.

## Primary protocol reference

The pinned official AutoFirma client source is ctt-gob-es/clienteafirma at0d7f3cf01fb65d2be5b245622d2c8f490f36e718, afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js. Its mobile selection uses the intermediate web-service client on Android/iOS, and Chrome invocation uses document.location. The page's original library should select its protocol normally; this patch does not inject a counterfeit AutoScript, force a desktop socket route or substitute a signing result.

https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js

## Authorship and deployment

A bounded GPT-6.1 Sol Max request for the catalog test class timed out without any source output. It is not counted as accepted code or a completed review. The integrator implemented the launch selection, UI wiring, catalog/protocol tests and public audit. Earlier accepted project code is retained.

All six GitHub Actions checks on the exact head are required for code acceptance. The previously blocked installation-preparation step is not retried or bypassed. These changes are not described as installed while the device retains its previous APK. Main remains unmerged. Real certificate/private-provider/government E2E is separate, and WebAuthn provider authorization remains unknown.

The first catalog-dispatch run correctly rejected the PDF fixture because it contained ordinary text rather than PDF bytes. The shared fixture now builds a genuine one-page procedural PDF; the production pdf_data_expected check was retained. A focused run of the same non-Android fixtures passed all1281 native parser/factory cases (183 sources×7 forms). It does not cover Android intent extraction or WebView dispatch; those remain in the actual Android unit suite. The initial failed XML report is retained.
