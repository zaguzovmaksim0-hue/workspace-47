# CDTI certificate-validation contract — 2026-08-16

## Public surface

- Exact page: `https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx`.
- The unauthenticated page instructs the user to allow AutoFirma, select a certificate, and wait for the page to reload with the validation result.
- The page dynamically loads `miniappletAuto.js` (or `miniappletAuto19.js` on macOS) plus `constantes.js`.

## Exact non-macOS contract

The live public page calls `AutoScript.sign` with:

- data: a server-generated **unpadded Base64 token** with shape `CertExp` + 32 lowercase hexadecimal characters + 24 lowercase alphanumeric characters; the shim restores the single `=` padding character before native decoding; the live token may use non-zero unused trailing Base64 bits, so the adapter validates whether decoded bytes can originate from the exact token shape instead of requiring decode→encode textual identity;
- algorithm: `SHA512withRSA`;
- format: `XAdES Enveloping`;
- extra properties: `filters=nonexpired`;
- success callback: `SignatureOKFunction(signature, certificateB64)`, which writes the signature and certificate into hidden fields and submits the current form;
- error callback: `SignatureErrorFunction(type, message)`, which writes the error and submits the current form.

Three independent safe GETs on 2026-08-16 returned distinct tokens while preserving that exact shape, so the implementation validates the canonical unpadded Base64 representation rather than pinning one transient value. No POST, certificate selection, credential, signature, or administrative submission was performed during research.

## Scope

Workspace-47 implements only the exact non-macOS branch in QA. The public macOS branch switches to `SHA256withRSA`; it remains fail-closed. The profile is exact-URL, exact-origin, exact-format, exact-properties and exact-challenge-shape scoped, and remains `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` until safe physical Android validation.
