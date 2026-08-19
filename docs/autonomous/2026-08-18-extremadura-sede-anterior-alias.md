# Junta de Extremadura — legacy Sede alias evidence — 2026-08-18

## Scope and safety

Target: `ES-PUB-0110` (`extremadura-sede-anterior`). This pass used only public, unauthenticated, read-only browser/HTTPS inspection. No credential, client certificate, signing operation, upload, payment, protected endpoint invocation, or administrative submission was used.

## Current first-party entry

Official entry reviewed in a real Chromium public session:

- `https://sede.juntaex.es/SEDE/`
- HTTP 200; page title `Sede Electrónica (e-GOBEX)`.
- Public page footer identifies Junta de Extremadura / Dirección General de Digitalización de la Administración.
- Current page exposes an official `Tramita` banner whose exact href is `https://tramites.juntaex.es`.
- The same page exposes `Registro Electrónico General` whose exact public href is `https://tramites.juntaex.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_REGGENERAL_INFO`.

A fresh unauthenticated GET of the entry returned 128,023 bytes with SHA-256 `ebe258b375375fdcbd1b7025a49c8de167732a64fe5049e62320a7072845f68f`. The HTML contained both decisive first-party links above.

## Browser/network observation

The real Chromium load of `https://sede.juntaex.es/SEDE/` returned HTTP 200 and loaded the legacy JSF/RichFaces surface plus first-party `/SEDE/js/funcionesComunes.js`, `/SEDE/js/reloj.js`, CSS/assets, and analytics/accessibility resources. No cryptographic operation was triggered.

Navigating the public `Registro Electrónico General` URL reached the current `tramites.juntaex.es` STA surface titled `Registro Electrónico General` without authentication. Separately, a fresh unauthenticated GET of `https://tramites.juntaex.es/` returned HTTP 200 after the public redirect to `https://tramites.juntaex.es/sta/CarpetaPublic/?APP_CODE=STA&PAGE_CODE=PTS2_HOME`, 31,629 bytes, SHA-256 `53fc7a7401cbeebe4d2f3230e497a751c6453b32403e8adf79a4038097d1fea8`.

## Binding decision

Current `main` already contains QA-only profile `extremadura-tramites` with canonical `startUrl` `https://tramites.juntaex.es/` and its separately reviewed STA signing contract. The legacy Sede's current first-party page explicitly delegates electronic processing to that same portal family and exposes both the portal root and the current General Register STA page.

Therefore `ES-PUB-0110` is an `ALIAS_ONLY` binding to the existing `extremadura-tramites` profile using `launch_url: https://tramites.juntaex.es/`. The legacy origin `https://sede.juntaex.es` is retained only as `entry_url`; it is not added to the profile's initiator/trust boundary and no signing, REG-AGE, certificate, algorithm, format, endpoint, or callback constant is inferred from the legacy page.

Status remains truthful: `IMPLEMENTED_NOT_E2E` / generated `E2E_PENDING`; physical accepted-flow E2E remains pending.
