# AEAT — E2E Client TLS login accepted

- **Date:** 2026-08-27 UTC
- **Device:** physical Android device with the Shizuku bridge
- **Profile:** `aeat-mis-datos-censales`
- **Run:** `e2e-manual-aeat-2`
- **APK:** QA build used for the physical gate

The physical run used the then-current exact AEAT contract. After the result
was captured, the profile was promoted from contract-only version 1 to version
2 with `VERIFIED_E2E` and `ENABLED`; no origin or capability was broadened.

## Observed flow

1. The app opened the exact `Mi área personal` entry.
2. The exact `Mis datos censales` route opened the native certificate
   confirmation.
3. The WebView observed `ClientCertRequest` for
   `www1.agenciatributaria.gob.es:443`.
4. The certificate was accepted, and navigation reached the exact allowed
   `DialogoRepresentacion` gateway.

The runtime snapshot recorded `browserSessionBound=true`,
`webViewActive=true`, `currentUrlAllowed=true`,
`clientCertRequestObserved=true`, `clientCertAcceptedObserved=true`, and no
failure code. A transient controller result of `WEBVIEW_NOT_ACTIVE` occurred
while the WebView identity was being replaced; the subsequent runtime
snapshot showed the active WebView and the exact allowed gateway. The final
data view was not used as evidence.

## Scope and promotion

This verifies only the exact client-TLS authentication/gateway access for the
read-only AEAT entry. It does not verify or enable signing, data modification,
payment, filing, or any other administrative operation. The public inventory
is `VERIFIED_E2E`, and the release profile is enabled only for this bounded
`CLIENT_TLS_AUTH` capability.

No request bodies, headers, cookies, tokens, certificate material, private
keys, passwords, or personal data were retained.
