# ES-PUB-0170 — Diputación de Sevilla current bounded evidence — 2026-08-21

Scope: current public Sede/authentication navigation and the Cl@ve DNIe/certificate boundary only. No client certificate was supplied, no private-key operation or document signature was executed, no authenticated session was established, and no filing/registration/payment action was performed. Opaque SAMLRequest, RelayState, cookie and session values were not retained.

## Public entry and authentication surface

- `https://sedeelectronicadipusevilla.es` redirects within the same official origin to the current Sede Electrónica and returned HTTP 200 on 2026-08-21.
- The public Sede exposes `Identificarse` at `https://sedeelectronicadipusevilla.es/opencms/system/modules/gsede/elements/secciones/autenticacion/autenticacion.jsp` and a public Registro electrónico page at `/opencms/opencms/sede/contenido/registroElectronico.jsp`.
- The current authentication page exposes `Acceso mediante Cl@ve`. Its `loginClave()` submits the existing `formLogin` to `https://pasarela.clave.gob.es/Proxy2/ServiceProvider`.
- The page also loads first-party `https://sedeelectronicadipusevilla.es/opencms/common-js/clientSigner.js`, 69,110 bytes with SHA-256 `26a95d819eadba817078ecdb77a1ee5ea3d2864c713fd6641b0bfd6d2dea7ac1`. This is generic signer support: the live public authentication page does not invoke `authenticate(...)` or expose a procedure-specific sign algorithm/format/hash/callback contract. It therefore does not justify a `SIGN` capability.

## Observed Cl@ve / client-TLS boundary

- Posting the complete public `formLogin` to `https://pasarela.clave.gob.es/Proxy2/ServiceProvider` returned the current Cl@ve method selector. It explicitly offered `DNIe / Certificado electrónico` as `AFIRMA` (`Cualquier certificado electrónico cualificado`).
- Selecting only that public method sets `SelectedIdP=AFIRMA` and submits to `https://pasarela.clave.gob.es/Proxy2/ServiceRedirect`.
- The AFIRMA response contains an auto-submit POST to exactly `https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`; opaque SAML fields were deliberately not retained.
- A real headless Chromium session reproduced the top-level document chain: Sede authentication GET → `Proxy2/ServiceProvider` POST → `Proxy2/ServiceRedirect` POST → `IdP2/AuthenticateCitizen` POST. With no certificate configured, control returned through Cl@ve to the Sede authentication page; no certificate or private-key action was supplied.
- A direct TLS 1.2 handshake to `pasarela-ident.clave.gob.es:443` emitted a client `CertificateRequest`. RSA and ECDSA signing certificate types/signature algorithms are accepted, and the server sends no client-certificate CA-name list. This supports `allowEmptyIssuerList=true` and bounded RSA/EC certificate selection.

## Signing boundary

After the unauthenticated Cl@ve attempt returns to the Sede, the page states that AutoFirma is needed for presentation of requests, incorporation of documents, or receipt of notifications. That statement establishes a later signing/product dependency, not the exact wire contract for a specific procedure. No live procedure-specific `AutoScript.sign`/`MiniApplet.sign`, algorithm, cryptographic format, callback, signer endpoint, or final submission contract was observed in this pass.

## Decision

Implement a QA-only `diputacion-sevilla-sede` profile bound to the exact public Sede entry and only to the observed Cl@ve client-TLS transition. Trust the official Sede initiator, the exact Cl@ve redirect origin, and the exact client-auth request origin/path. Keep document signing and final filing E2E explicitly unverified.
