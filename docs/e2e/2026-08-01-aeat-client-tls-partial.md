# AEAT Client TLS physical gate — partial WebView activation

Date: 2026-08-01

This record continues `docs/e2e/2026-07-31-aeat-client-tls-blocked.md`.

## Exact build

- package: `dev.junta.firmamobile`
- version: `0.1.0-qa`
- QA APK / installed `base.apk` SHA-256:
  `ca5b351656cb41904f3774ed2a84ac002041d9babdf5fe877d6191d04d6befe2`

## Sanitized observations after manual unlock

The user manually unlocked the existing certificate. The password was not sent
to chat, read, copied, logged or automated.

Unlocked-state UI markers observed:

- `Bloquear certificado`
- `Elegir otro`
- `Olvidar certificado`

Protected QA smoke for `aeat-sede`:

- `total=1`
- `webViewActive=1`
- `profileResolvedOnly=0`
- `catalogOnly=0`
- `failures=0`

After restoring `dev.junta.firmamobile/.MainActivity` to foreground, a bounded
system UI query observed the exact public WebView label `Mis datos censales`.
The application process remained alive.

This proves the unlocked certificate permits the exact QA profile to reach an
active WebView and the reviewed AEAT source page. It does not prove the target
Client TLS authentication yet.

## Why the target click was not executed

Android Control Bridge repeatedly brought `io.termux.androidcontrol/.MainActivity`
to foreground while accessibility operations were attempted. The first exact
click attempt consequently inspected the control application's UI rather than
the AEAT WebView and was rejected.

The control UI was then force-stopped and `Junta Firma` restored to foreground,
but the next `uiautomator dump` returned empty. No coordinate guess or blind tap
was used.

Therefore `Mis datos censales` was observed but not clicked.

## Gates still open

Not yet proven:

- WebView `ClientCertRequest` callback for the exact target;
- runtime request host/port/key-types/issuer metadata from that callback;
- native certificate confirmation;
- accepted AEAT certificate authentication;
- authenticated read-only `Mis datos censales` landing.

Current status remains `VERIFIED_CONTRACT / QA_ONLY`; public catalog remains
`E2E_PENDING / IMPLEMENTED_NOT_E2E`; release trust is unchanged.

## Next attempt

Use the existing installed QA build and prefer rish-only foreground control.
Avoid launching Android Control Bridge UI while the portal is active. Require
`WEBVIEW_ACTIVE`, obtain a bounded exact-node dump, click only the exact
`Mis datos censales` node, collect only sanitized Client TLS metadata and stop
before every modification, payment, signature or submission control.

No screenshot, authenticated-page content, password, PKCS#12, private key,
certificate body, cookie or personal identifier was retained.
