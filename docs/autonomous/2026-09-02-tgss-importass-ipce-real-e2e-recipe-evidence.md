# TGSS Import@ss IPCE CLIENT_TLS_AUTH REAL E2E evidence — 2026-09-02

Scope: current public authentication navigation inspected with a fresh disposable headless Chromium profile and the existing sanitized REAL E2E artifact. No credentials were entered, no client certificate was sent during the inspection, and no administrative transaction was submitted.

Catalog/profile: `tgss-importass` / `tgss-importass-client-auth`.

## Existing REAL E2E evidence

The sanitized REAL E2E artifact from run `33603924562` shows the normal browser flow reaches the Seguridad Social identity provider successfully:

- portal navigation starts at `portal.seg-social.gob.es`;
- the portal performs its own main-frame POST;
- WebView then performs a main-frame POST to `idp.seg-social.es` path `/PGIS/Login`;
- the IDP page starts and finishes normally;
- there is no network error, SSL error, blocked navigation, or unexpected client-auth host;
- classification remained `RECIPE_REQUIRED` because no authentication method had yet been selected.

## Fresh browser DOM evidence

A fresh headless Chromium session was allowed to execute the portal's own auto-submit chain. It reached exactly:

`https://idp.seg-social.es/PGIS/Login`

with title `Pasarela Seguridad Social` and a completed DOM. The authentication method form is:

- `form name="redirectForm"`;
- method `post`;
- action exactly `https://idp.seg-social.es/PGIS/Login`.

The form exposes four distinct authentication controls. The reviewed certificate control is uniquely identified by all of the following:

- `button#IPCEIdP`;
- `type="submit"`;
- `aria-label="Acceder a DNIe o certificado"`;
- normalized visible text `DNIe o certificado`;
- `formaction="https://idp.seg-social.es/PGIS/Login?seleccion=IPCE"`;
- exactly one child image whose path is `/PasarelaStaticAuth/images-pasarela/Componentes/Botones/IPCE.svg` and whose alt text is `Certificado admitido por la GISS`.

The other buttons are separately identified as Cl@ve Permanente, Cl@ve Móvil, and Vía SMS, with different IDs and form actions.

## Fail-closed recipe

The recipe does not try to reproduce the portal's WebSphere POST itself. It waits for the site's own browser flow to arrive at HTTPS host `idp.seg-social.es`, path `/PGIS/Login`. It then validates the exact `redirectForm`, exact IPCE button identity/label/formaction, and exact certificate icon before clicking that single button.

The existing profile independently constrains the next certificate-authentication boundary to source `https://idp.seg-social.es/PGIS/Login?seleccion=IPCE` and request `https://ipce.seg-social.es/IPCE/Login`.
