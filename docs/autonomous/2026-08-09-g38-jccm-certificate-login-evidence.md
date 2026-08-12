# JCCM certificate-login public contract evidence — 2026-08-09

## Scope and safety boundary

Official public, unauthenticated, GET-only evidence was collected from Junta de Comunidades de
Castilla-La Mancha. No credentials, client certificate, authenticated navigation, signing, form POST,
upload, payment, submission, APK launch, or device-control workflow was used.

Candidate profile id: `jccm-certificate-login-probe`.

Public page:
`https://ventanillaelectronica.jccm.es/administracion_electronica/formularios/identificacion.phtml`

First-party MiniApplet implementation:
`https://ventanillaelectronica.jccm.es/administracion_electronica/afirma/miniapplet.js`

Ignored raw evidence:
`build/autonomous-evidence/g38-jccm-cert-login/`.

## Reproducible public contract

The public page was fetched again on 2026-08-09 and was byte-identical to two preserved G36
captures. Its SHA-256 is
`68539ed239db061e971e3f3ee961b0d31aa5de021af08cc751415bc074a084cc`.
The current first-party MiniApplet script SHA-256 is
`e1774d2f3ef4c3e6cae90f038f9120d4e1e8a2ef9c4caffc4448f17c9897bd37`.

The page defines the exact call:

```javascript
MiniApplet.sign(
    document.getElementById("datosPrimeraFirma").value,
    "SHA1withRSA",
    "CAdES",
    null,
    saveSignatureCallback,
    errorCallback);
```

The same public page supplies `datosPrimeraFirma` as the exact Base64 literal `QUJDREU=`, which
decodes to ASCII `ABCDE`. The page invokes `firmar()` automatically after load. The success callback
accepts one Base64 signature argument, writes it to the hidden `firmaCADENA` field, and then submits
`FORMPROC`.

Therefore the evidence-backed signing ABI is bounded to:

- exact initiator origin: `https://ventanillaelectronica.jccm.es`;
- exact public start page: `/administracion_electronica/formularios/identificacion.phtml`;
- input encoding/value: Base64 `QUJDREU=` (`ABCDE`) for this public validation probe only;
- algorithm: `SHA1withRSA`;
- format: `CAdES`;
- extra parameters: `null` (no parameters supplied);
- success callback shape: one signature-Base64 argument;
- error callback shape: `(type, message)`.

The loaded first-party `miniapplet.js` aliases `MiniApplet = AutoScript` and its public `sign()`
forwards the six call arguments to the native client implementation. No portal-specific value has to
be invented to reproduce the page's public call boundary.

## Product interpretation

This is a public certificate-login/component-validation probe, not evidence that a real administrative
procedure is supported end-to-end. A QA-only profile may emulate only the `MiniApplet.sign()` call
and return the result to the page callback. The app must not reproduce the page's subsequent
`FORMPROC.submit()` step. It must not navigate into an authenticated session and must not claim
`VERIFIED_E2E`.

The fixed `QUJDREU=` payload is evidence for this exact public probe. It must not be generalized to
other JCCM procedures. The profile should remain `VERIFIED_CONTRACT` / `QA_ONLY` until physical
portal evidence is supplied separately.
