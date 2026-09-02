# Diputación de Sevilla — Cl@ve AFIRMA boundary evidence — 2026-09-02

Scope: authentication-method selection only. No administrative form is filled or submitted by this recipe.

The reviewed profile `diputacion-sevilla-sede` is a QA-only `CLIENT_TLS_AUTH` contract. Its expected TLS source transition is Cl@ve `https://pasarela.clave.gob.es/Proxy2/ServiceRedirect` to `https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`.

A fresh sanitized REAL E2E artifact (`9835360749.zip`, run result timestamp 2026-09-02 07:01 UTC) shows the portal completing its own navigation without network, SSL, or navigation-policy errors. The final main-frame navigation is a POST to host `pasarela.clave.gob.es` with path length 23 and `path_sha256_8=398b68b7`.

For the two relevant Cl@ve paths:

- `/Proxy2/ServiceProvider` has length 23 and SHA-256 prefix `398b68b7`;
- `/Proxy2/ServiceRedirect` has length 23 and SHA-256 prefix `5d85c029`.

Therefore the observed REAL E2E terminal page is exactly `/Proxy2/ServiceProvider`. The result remains `RECIPE_REQUIRED`, with `pageStarted=true`, `pageFinished=true`, `clientAuthObserved=false`, `certificateSelectionObserved=false`, and no network/SSL error. This is precisely the boundary handled by the already reviewed fail-closed `clickClaveAfirmaProvider` helper.

The Diputación de Sevilla portal ID is consequently dispatched directly to that shared helper. The helper still requires the exact live Cl@ve ServiceProvider form contract, SAML state, empty `SelectedIdP`, unique AFIRMA handler, and `IDP_AFIRMA` image before clicking.
