# Diputació de Lleida — Sede Electrónica (ES-PUB-0162) — IMPLEMENTED_NOT_E2E evidence — 2026-08-16

## Scope and safety boundary

Public, unauthenticated, read-only inspection of inventory surface `ES-PUB-0162` (`diputacion-lleida-sede`) with exact seed `https://seu.diputaciolleida.cat`.
Network timeout <= 15 seconds. No privileged authentication, remote form submission (POST), external storage call, electronic signature submission to production servers, cookie replay, APK launch, ADB, or administrative modification was performed.

## Public first-party observations and static contract

1. Exact seed entry URL `https://seu.diputaciolleida.cat`:
   - Returns HTTP 302 (Location: `/portal/inicio.do`), which in turn redirects via HTTP 302 to `/portal/entidades.do?ent_id=1&idioma=2`.
   - `https://seu.diputaciolleida.cat/portal/entidades.do?ent_id=1&idioma=2` returns HTTP 200 (93,707 bytes) with `Content-Type: text/html` and Apache-Coyote server headers.
   - The HTML contains dynamic content and is therefore not hash-pinned as contract evidence. An independent 2026-08-16 re-fetch changed the page SHA256 while preserving the exact signing markers below.
   - Page defines `var urlTrifasico=https://seu.diputaciolleida.cat/portal-afirma-server-triphase-signer/`.
2. First-party script inspection:
   - The portal loads `/sede/js/firmaDigital.js` (27,572 bytes, SHA256 `9e3dced47cdf634d120c4783b22ae0f9e00be3d42fad13429de38f5ef5921483`) and `/sede/js/login/login.js` (9,508 bytes).
   - `writeMini` sets `StorageService=https://seu.diputaciolleida.cat/portal-afirma-signature-storage/StorageService` and `RetrieveService=https://seu.diputaciolleida.cat/portal-afirma-signature-retriever/RetrieveService`.
   - Login calls: `firmar(formLogin.shaLogin.value, errorText, "", "TEXTO", 0, pulsarFirmarIdentificateCallback, pulsarFirmarIdentificateCallbackError, true)`.
   - Login forces WebSocket mode `true`.
   - Wrapper exact non-PDF branch:
     - Algorithm: `SHA256withRSA`
     - Format: `Cades` / `CAdES` (detached)
     - Payload: `MiniApplet.getBase64FromText(shaLogin, "default")`
     - ExtraProperties:
       ```
       policy=FirmaAGE
       headless=true
       filters=nonexpired:true;authCert:true
       ```
   - Success callback receives `resFirma`, stores it in `formLogin.firmaLogin` and continues login; error callback receives `(tipo, mensaje)`.

## Local implementation boundary

1. Native adapter `DiputacionLleidaCadesAdapter`:
   - Protocol ID: `diputacion-lleida-login-cades-v1`
   - Bounded CAdES-detached signer using SHA-256 with RSA via `CadesDetachedCodec`.
   - Validates exact initiator origin (`https://seu.diputaciolleida.cat`), safe description, algorithm (`SHA256_WITH_RSA`), format (`CADES`), and exact `extraProperties` (`policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true`).
   - Limits payload challenge size to <= 512 bytes with printable ASCII verification.
   - Bound in `ProtocolAdapterRegistry` with `miniapplet-autoscript-v1` input adapter and `miniapplet-sign-callback-v1` callback contract.
2. Network policy:
   - Local login signature requires no network endpoint (`endpointId: null`).
   - Triphase signer, StorageService, and RetrieveService endpoints are transport helpers and are not invoked for local sign.
3. Fail-closed security boundary:
   - Outbound and external authentication targets (`https://idcat.aoc.cat`, `https://clave.gob.es`, `https://giltza.euskadi.eus`, `https://www.diputaciolleida.cat`, `https://www.diputaciolleida.es`, subpaths) remain untrusted.
   - Generic PDF/batch branches and unverified form submission POSTs remain strictly blocked.

## Limitation: No physical E2E verification

- This implementation is verified through local unit tests (`DiputacionLleidaCadesAdapterTest`, `MiniAppletBridgeAdapterTest`, `DiputacionLleidaProfileCatalogBindingTest`, `ProtocolAdapterRegistryTest`).
- No physical end-to-end device execution or actual submission to Diputació de Lleida backend was performed.
- Status is therefore strictly `IMPLEMENTED_NOT_E2E` (QA-only) and requires physical device verification before any release promotion.

## Runtime contract summary

- Profile ID: `diputacion-lleida-sede`
- Inventory ID: `ES-PUB-0162`
- Start URL: `https://seu.diputaciolleida.cat`
- Compatibility status: `VERIFIED_CONTRACT`
- Activation: `QA_ONLY`
- Initiator origins: `["https://seu.diputaciolleida.cat"]`
- Redirect origins: `[]`
- Trusted browse origins: `[]`
- Endpoints: `[]`
- Operation policies: `[SIGN: CAdES detached SHA256_WITH_RSA explicit mode]`
- Capabilities: `["SIGN"]`
- Client TLS auth policy: `null`
- Certificate rules: `{"allowedKeyAlgorithms":["RSA"],"requireDigitalSignatureKeyUsage":true}`
- Inventory metadata: `reviewed_at: "2026-07-16"`, `discovery_state: "REVIEWED"`, `inventory_status: "IMPLEMENTED_NOT_E2E"`.

## Orchestrator acceptance hardening — 2026-08-16

- The public login page is bound exactly to `https://seu.diputaciolleida.cat/portal/entidades.do?ent_id=1&idioma=2`; the MiniApplet bridge rejects the same signing tuple from other paths on the origin.
- The public wrapper passes `SHA256withRSA`, `Cades`, and no `mode` extra property for the certificate-login branch. In the official Cliente @firma source, `CAdESParameters` falls back to `AOSignConstants.DEFAULT_SIGN_MODE`, and that constant is `SIGN_MODE_EXPLICIT`; the local detached CAdES implementation therefore matches the default mode rather than inventing a portal property.
- The actual `shaLogin` value is not present in the unauthenticated GET response. The runtime adapter therefore does not pretend a fixed hash length is observed; it accepts only bounded printable text (1..512 bytes) under the exact profile, origin, page, algorithm, format and fixed-property tuple. This is a deliberate QA boundary, not an E2E claim.
- The reviewed MiniApplet flow does not prove absence of TLS client-certificate authentication elsewhere on the portal, so inventory remains `client_tls_auth: NO_VERIFICADO`.
- Runtime execution is wired through `MainActivity` to `DiputacionLleidaCadesAdapter`; registry-only presence is not treated as sufficient implementation.

## Fresh public recheck — 2026-08-17

- `GET https://seu.diputaciolleida.cat/portal/entidades.do?ent_id=1&idioma=2` remained public and returned 93,707 bytes. Its dynamic HTML SHA-256 changed to `b03690ada7ff01c6c6eaf03317f97a66ec06747c86a57bc5d135433f58741d57`, while the contract markers remained present.
- The page still defines `urlTrifasico = 'https://seu.diputaciolleida.cat/portal-afirma-server-triphase-signer/'` and still invokes `firmar(document.forms['formLogin'].shaLogin.value, ..., "TEXTO", 0, pulsarFirmarIdentificateCallback, pulsarFirmarIdentificateCallbackError, true)`; the callback still writes the returned signature to `formLogin.firmaLogin`.
- `/sede/js/firmaDigital.js` remained byte-identical at 27,572 bytes and SHA-256 `9e3dced47cdf634d120c4783b22ae0f9e00be3d42fad13429de38f5ef5921483`. It still exposes `SHA256withRSA`, `Cades`/`CAdES`, `policy=FirmaAGE`, `headless=true`, `filters=nonexpired:true;authCert:true`, and the StorageService/RetrieveService derivation.
- `/sede/js/login/login.js` remained 9,508 bytes with SHA-256 `19ac013e5553fc829ac9a11f02f9671d3bf843faa8c4ae63248660d6d3729ec0`.
- Recheck used unauthenticated read-only GETs only; no cookie replay, POST, certificate use, signature, or submission was performed.
