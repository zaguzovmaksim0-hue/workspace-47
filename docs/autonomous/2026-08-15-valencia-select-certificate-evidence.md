# Diputació de València certificate-selection contract — 2026-08-15

## Scope

Public, unauthenticated, read-only evidence for `diputacion-valencia-sede` (ES-PUB-0175).
No POST, JSF form submission, authentication, certificate use, signature, private-key operation,
TLS bypass, cookie replay, or navigation beyond the public pre-authentication pages was performed.

## First-party evidence

- `https://www.sede.dival.es`
  - Final URL: `https://www.sede.dival.es/opencms/opencms/sede/paginas/`
  - 29,777 bytes
  - SHA-256 `8406eb3cf9c0237446c92d415841b0d1620fbc3a9d98f8c174acdfeec3c376e1`
- `https://portafirmas.dival.es`
  - Redirects to `https://portafirmas.dival.es/signingpad/xhtml/login.xhtml`
  - 30,554 bytes
- `https://portafirmas.dival.es/signingpad/xhtml/login.xhtml`
  - 30,554 bytes
- `https://portafirmas.dival.es/signingpad/js/autoscript.js`
  - 184,036 bytes
  - SHA-256 `b02c9fb6c4db037a5a9caee7b534c4e94294729e5e2edb417588ae133c6dfba4`
- `https://portafirmas.dival.es/signingpad/js/filtros.js`
  - 9,179 bytes
  - SHA-256 `07ce2531495fcea3eb9e90daf625ecb0fed1bc87b921c9a136fc266979379968`
- `https://portafirmas.dival.es/signingpad/js/util.js`
  - 33,795 bytes
  - SHA-256 `4b2e65058c298c7696fe959cbb7d824dee9c7248a5d690cdbc4264d648599582`

## Exact observed contract

The public `login.xhtml` page loads `autoscript.js`, `filtros.js`, and `util.js`, calls
`AutoScript.cargarAppAfirma()`, configures storage/retrieval servlets, and defines `lanzarAutofirma()`:

- `AutoScript.selectCertificate("filters=keyusage.nonrepudiation:true;nonexpired:true\nheadless=true", exitoCallback, errorCallback)`
- `exitoCallback(certB64)` writes `certB64` into the form input element `certB64` and invokes JSF `validarCertificado()`.
- Nearby commented signing operations (`AutoScript.sign(dataB64, "SHA256withRSA", "CAdES", params, exitoCallback, errorCallback)` and `AutoScript.createBatch` / `AutoScript.signBatchProcess`) are inactive in production and remain strictly blocked.

The native implementation selects the already-unlocked local certificate and delivers its DER
encoding as Base64 to the page's success callback after explicit user confirmation.
The subsequent JSF form submission and certificate validation remain entirely outside this milestone.

## Implementation boundary

- profile: `diputacion-valencia-sede`
- activation: `QA_ONLY`
- compatibility: `VERIFIED_CONTRACT`
- operation: `SELECT_CERTIFICATE`
- input adapter: `autoscript-select-certificate-v1`
- callback: `autoscript-select-certificate-callback-v1`
- no network endpoint is registered for the operation
- no signing algorithm or signature format is asserted
- release remains disabled until physical E2E verification is completed
