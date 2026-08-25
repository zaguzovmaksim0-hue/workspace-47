# Comunidad de Madrid — Cuenta Digital / Carné Joven 53F1

Reviewed: 2026-08-19

## Bounded current evidence

- Official entry: `https://digital.comunidad.madrid/ext/53F1`.
- Current SPA still serves `main.84fe736d0b3d70bb.js`; lazy chunk `948.c7e949fb454f7c61.js` has SHA-256 `92afddd42b59769a0ea02946ea2f3330ed379923d9b0464787ae7c0f86f37fb8`.
- A fresh Chromium session redirected the external procedure to `https://digital.comunidad.madrid/login`.
- The current identity selector exposed IDentifica, Cl@ve Móvil, Cl@ve Permanente, Certificado Digital and DNIe.
- The certificate branch used `https://gestiona.comunidad.madrid/auto_login/seleccion-idp.jsf` and then a **POST** to `https://gestiona2.comunidad.madrid/auto_certificado/SelCertificado`.
- Without a client certificate the official flow returned the certificate-not-found error. With the operator-authorized PKCS#12 presented transiently to `gestiona2.comunidad.madrid`, the flow advanced through `reqAuto.jsf` and `RespuestaCertificado.jsf` before returning to Cuenta Digital. Temporary key material was deleted after the run. No credential, cookie, token, query value or request body is retained here.

## Implemented boundary

The QA profile trusts only the exact 53F1 Cuenta Digital launch and the observed first-party `gestiona.comunidad.madrid` login navigation. It exposes no signing, certificate-selection or client-TLS capability.

The current Android `CLIENT_TLS_AUTH` helper cannot truthfully model this certificate branch because it recreates an authorized TLS target with URL loading, while the observed transition to `SelCertificado` is POST-based. `gestiona2.comunidad.madrid` therefore remains outside the profile's browser trust and client-auth policy.

No document signature, final submission, registration or payment was performed.
