# Diputació de Tarragona — bounded AOC VALId client-TLS contract

Reviewed: 2026-08-21

## Current public first-party path

- `https://seuelectronica.dipta.cat/instancia-generica` is currently reachable and links the active `Sol·licitud genèrica` transaction route `https://seuelectronica.dipta.cat/tramits-online/fr/administracions/8004330008/procediments/DIP80_EGIST_00001/crearInstancia`.
- A fresh unauthenticated redirect observation of that route, without retaining authenticated state, reaches Diputació's `egovern.altanet.org` VALId integrator and then `https://valid.aoc.cat/o/oauth2/auth` with the fixed public tuple `response_type=code`, `client_id=valid.dipta.cat`, `redirect_uri=https://egovern.altanet.org/valid/code`, `scope=autenticacio_usuari`, `access_type=online`, `approval_prompt=auto`, plus one ephemeral `state` value. The ephemeral value was not persisted in repository evidence.
- Current first-party `https://valid.aoc.cat/o/oauth2/js/login.js` is 21,303 bytes with SHA-256 `cfeb53df13636f043ebc3ff71cb18272526af771e7af4ecc1d0e78b0138212e2`. Its certificate-login branch constructs `https://cert.valid.aoc.cat/o/oauth2/cert` and submits the certificate form with HTTP POST.
- A fresh TLS 1.2 handshake to `cert.valid.aoc.cat:443`, without presenting a client certificate, returned a TLS `CertificateRequest`. The server accepted `RSA sign` and `ECDSA sign` client certificate types and sent no client-certificate CA names.

## Android implementation boundary

Android WebView does not expose POST navigations through the existing `shouldOverrideUrlLoading` transition seam, and replacing this request with the existing dedicated GET client-auth WebView would discard the original OAuth POST body. The bounded implementation therefore adds `IN_PLACE_FROM_SOURCE` client authentication:

- only a top-level resource request for the active Tarragona QA profile can arm the grant;
- the exact VALId source path must carry exactly the fixed OAuth tuple above plus one bounded ephemeral `state`;
- the request method must be POST;
- the target must be exactly `https://cert.valid.aoc.cat:443/o/oauth2/cert`, with no query or fragment;
- authorization is same-navigation-epoch, one-shot and limited to 15 seconds;
- the original WebView POST is allowed to continue unchanged;
- the subsequent client-certificate challenge must match the authorized host and port and requires explicit user confirmation before the unlocked certificate/private key is supplied;
- WebView client-certificate preferences are cleared before this in-place flow and cleared again when the bounded grant is abandoned.

## Explicit limitations

- QA-only; no release enablement.
- No real client certificate or private key was presented during portal research or TLS probing.
- No authenticated account/session, OTP, upload, document signature, administrative submission/registration, or payment was executed.
- This implements only the independently observed certificate-authentication boundary. It does not infer or implement Tarragona's later document-signing ABI, signature format/algorithm, filing payload, registration callback, or successful end-to-end procedure.
- The truthful catalog state is therefore `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`, not `VERIFIED_E2E`.
