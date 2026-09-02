# Cl@ve ServiceProvider AFIRMA CLIENT_TLS_AUTH evidence — 2026-09-02

Scope: public authentication-method selection only. The DOM was captured from the current Cl@ve ServiceProvider reached through the reviewed MUGEJU browser flow. No credentials were entered, no client certificate was sent during inspection, and no administrative transaction was submitted.

## Current ServiceProvider contract

A fresh disposable headless Chromium session starting at `https://sedemugeju.gob.es/remisiondocumentacion` reached exactly:

`https://pasarela.clave.gob.es/Proxy2/ServiceProvider`

with a completed page titled `Página Principal - Clave`.

The current certificate-selection form is `form[name=idpRedirect]`, method `post`, action `ServiceRedirect`, which resolves to:

`https://pasarela.clave.gob.es/Proxy2/ServiceRedirect`

The live form contains the expected authentication transaction state:

- hidden `SAMLRequest`, populated with a large live signed request;
- hidden `RelayState`, populated with the live relay value;
- hidden `SelectedIdP`, initially empty.

The certificate/electronic-identifier card is uniquely bound to the site-owned handler:

`JAVASCRIPT:selectedIdP('AFIRMA');idpRedirect.submit();`

and the same card contains `img.spLogo` with source `ImageRetrieve?id=IDP_AFIRMA`.

Visible wording is intentionally not pinned because Cl@ve localizes the page (the reviewed browser session rendered English labels even though the surrounding portal workflow was Spanish). The handler, form structure, state fields and AFIRMA image identifier are language-independent.

## Shared fail-closed helper

`clickClaveAfirmaProvider` waits for HTTPS host `pasarela.clave.gob.es`, path `/Proxy2/ServiceProvider`, with no query. It validates the exact `idpRedirect` POST target, live SAML/Relay fields, initially empty `SelectedIdP`, exactly one `idp-button` with the AFIRMA handler, and exactly one `IDP_AFIRMA` image in the same `article.idp-card2` before clicking.

The helper is now used at the final authentication-method boundary for:

- `age-mutualidad-general-judicial-mugeju` directly after its portal-controlled redirect;
- `asturias-miprincipado-sede` after its reviewed `Con sistema Clave` action;
- `catalunya-tramits-peticio-generica` after the reviewed AOC `Cl@ve` action;
- `diputacion-ourense-sede` after the reviewed STA `Identificate` action.

This corrects an important boundary gap: merely reaching Cl@ve ServiceProvider is not yet a client-certificate attempt. The AFIRMA provider choice is required before the profile-scoped TLS request can occur.
