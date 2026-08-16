# Plataforma ACCEDA (ES-PUB-0003) fail-closed browse contract — 2026-08-16

## Scope and safety boundary

Public, unauthenticated, read-only evidence for `age-acceda` (`ES-PUB-0003`), exact seed `https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES`.
No authentication, certificate selection, private key operation, signature submission, administrative submission, or form submission was performed.

## First-party evidence

- Seed entry URL: `https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES`
  - HTTP Status: 200 OK
  - Origin: `https://sede.administracionespublicas.gob.es`
  - Institution: Administración General del Estado

- First-party static resources referenced by P15/P15A/P15B/D11:
  - `https://sede.administracionespublicas.gob.es/js/afirma/constantes.js` — SHA-256 `150405151c4327bd88049b08a17943f13d82e5f811df2b9b194530e08ea55026`
  - `https://sede.administracionespublicas.gob.es/js/afirma/afirma_funciones.js` — SHA-256 `b522f95b00836f420cb9c52b0075b3f1db4856bf93c19cef2af3cdc563e7c6a5`
  - `https://sede.administracionespublicas.gob.es/js/afirma/autoscript.js` — SHA-256 `e5f17e93816d1875c57198917ed9fd1c6d6f9e71dd2d5c9fec3650d76544c713`

## Technical analysis and fail-closed determination

1. `afirma_funciones.js` exposes:
   - `doSignSolicitud(data, nif, tipo_certificado_logeado)` using `SHA1withRSA`, `PAdES`, `format=PAdES Detached`, `expPolicy=FirmaAGE`, `nonexpired:true`.
   - `doSign(data)` with dynamic DOM-selected algorithm/format values.
   - `showSignResultCallback(signatureB64, certificateB64, extraData)` writing to `#firma_formularioweb`.

2. Linkage and runtime limitations:
   - The public `/certificado/valida` entry invokes `afirma.firmar(...)` with volatile server-issued `formularioweb` parameters; no public script directly binds this wrapper to `doSignSolicitud()`.
   - The generic `doSign()` branch relies on runtime DOM parameters that are not statically verified.
   - The server endpoint and procedure backend remain unverified (`NO_VERIFICADO`).
   - The Android mobile client does not provide a local PAdES detached signature engine.

3. Fail-closed contract:
   - No privileged signing capability (`SIGN`, `SELECT_CERTIFICATE`, `CLIENT_TLS_AUTH`, `AFIRMA_URI`) is granted.
   - No endpoints, bridge injection, or operation policies are declared.
   - The profile is bound strictly in `BROWSE_ONLY` compatibility mode with `activation: ENABLED` to the exact seed URL `https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES` under `TrustMode.BROWSE_ONLY`.

## Profile specification

- `profileId`: `age-acceda`
- `displayName`: `Plataforma ACCEDA — Sede electrónica`
- `compatibilityStatus`: `BROWSE_ONLY`
- `activation`: `ENABLED`
- `startUrl`: `https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES`
- `initiatorOrigins`: `["https://sede.administracionespublicas.gob.es"]`
- `redirectOrigins`: `[]`
- `trustedBrowseOrigins`: `[]`
- `endpoints`: `[]`
- `operationPolicies`: `[]`
- `capabilities`: `[]`
- `clientAuthPolicy`: `null`
- `certificateRules`: `{"allowedKeyAlgorithms":["RSA"],"requireDigitalSignatureKeyUsage":true}`
