# ES-PUB-0165 — Deputación de Ourense current bounded evidence — 2026-08-20

Scope: current public/authentication navigation for **01.Solicitud de propósito general** only. No client certificate was supplied, no document signature was executed, and no filing/registration/payment action was performed. Opaque SAML, RelayState, cookie and session values were not retained.

## Exact public procedure

- `https://sede.depourense.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000946476474507610&lang=ES` returned HTTP 200 and identifies `01.Solicitud de propósito general.`.
- The detail exposes `Tramitación Electrónica` through `enterNewReg(window.catser.dboid,'es')`.
- Current first-party `catserv.js?ver=2602.0.4` is 4,892 bytes with SHA-256 `ffdf496a7486c190e4dc2b5e33ae785d99acb1a01daecb1aef2a3069360227e3`. Its general STA helpers retain `/frame.jsp`/AutoFirma transitions, but those do **not** establish the post-authentication signing contract for this exact procedure.

## Observed Cl@ve / client-TLS boundary

- GET `/sta/reg/auth/es/6269000946476474507610` returned 302 to `/sta/reg/auth/do/CLAVE/ES/6269000946476474507610`, then 302 to `/sta/Relec/STAClaveManager`.
- `STAClaveManager` produced an authentication bootstrap POST to `https://pasarela.clave.gob.es/Proxy2/ServiceProvider` containing only the expected opaque `SAMLRequest` and `RelayState` fields.
- With browser-equivalent request headers, Cl@ve returned its current method selector and exposed `DNIe / Certificado electrónico` as `AFIRMA`.
- The AFIRMA branch uses `https://pasarela.clave.gob.es/Proxy2/ServiceRedirect`, whose redirect envelope targets `https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen` with opaque SAML/RelayState fields.
- A direct TLS 1.2 handshake to `pasarela-ident.clave.gob.es:443` returned a client `CertificateRequest`: RSA and ECDSA signing types/signature algorithms are accepted and the server sends no client-certificate CA-name list. This supports a bounded `CLIENT_TLS_AUTH` policy with an empty issuer-list allowance.

## Decision

Implement a QA-only `diputacion-ourense-sede` profile bound to the exact general-request detail. Trust only `sede.depourense.es`, the observed Cl@ve redirect origin, and the exact client-auth request origin/path. Keep signing ABI, signature format/algorithm, signing endpoint/callback, and final filing E2E unverified.
