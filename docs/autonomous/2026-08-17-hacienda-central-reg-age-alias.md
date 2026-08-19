# Ministerio de Hacienda central Sede / REG-AGE alias evidence — 2026-08-17

## Decision

`ES-PUB-0088` is an `ALIAS_ONLY` implementation. The institutional Sede remains the catalog `entryUrl`; the alias launches only the immutable canonical `startUrl` of the existing QA-only `reg-age-redsara` profile: `https://reg.redsara.es/es/`.

No Hacienda origin is added to REG-AGE signing trust. No Hacienda-specific signing algorithm, format, callback, endpoint, certificate rule or client-TLS contract is inferred.

## Current first-party Hacienda evidence

Public unauthenticated GET of `https://sede.hacienda.gob.es/` on 2026-08-17 followed the same-origin redirect to `/es-es/paginas/home`, returned HTTP 200, title `Sede electrónica del Ministerio de Hacienda`, 112,110 bytes, SHA-256 `e6145ad5c9d9f8691e95c9e7cea73c78e4dc652ead0f7550b6e95525e16c1590`.

The current first-party `Información` page is:

`https://sede.hacienda.gob.es/es-es/paginas/informacion`

Public GET returned HTTP 200, 161,959 bytes, SHA-256 `fcb250ae7e81af910d9f53b3899162cb0df0726dd262ca4a8d245071f6686364`.

Its `Registro electrónico` section states that the **Ministerio de Hacienda utiliza el Registro Electrónico General de la Administración General del Estado (REG-AGE)** for receipt/remission of requests, writings and communications in its scope. It also explains that special procedures can have their own required presentation route, so this evidence is not generalized to every specific procedure hosted by the central Sede.

That same Hacienda section publishes the exact link labelled `Registro Electrónico General AGE` to the official PAG Sede service page:

`https://sede.administracion.gob.es/PAG_Sede/ServiciosElectronicos/RegistroElectronicoGeneral.html`

In current Chromium this navigates to the current PAG route:

`https://sede.administracion.gob.es/servicios-electronicos/registro-electronico-general-age`

## Current official PAG evidence

Public unauthenticated GET of the REG-AGE PAG page returned HTTP 200, title `Sede Punto Acceso General | Registro Electrónico General de la Administración General del Estado`, 84,462 bytes, SHA-256 `ee38f515dcbdfe267311b5ef1078cc4d20553124778b30a8b734b14e6025e63c`.

The page explicitly identifies REG-AGE as the point for presenting documents addressed to AGE bodies and publishes the link `Acceso al Registro Electrónico General` with target:

`https://reg.redsara.es/`

A real unauthenticated Chromium pass followed the Hacienda REG-AGE link to this PAG page and activated that public access link. The root REG URL selected a locale according to browser context (`/en/` in the current English browser context), proving that the root is the locale entry of the same REG application rather than a Hacienda-specific endpoint.

## Canonical existing profile start

The existing `reg-age-redsara` profile has immutable QA start URL:

`https://reg.redsara.es/es/`

A current unauthenticated direct GET/Chromium navigation to that exact Spanish start returned HTTP 200 with title `REG - Registro Electrónico General` on 2026-08-17. This is the exact launch URL used by the alias, matching the repository's existing root-to-canonical-locale REG alias convention.

The existing REG signing contract was researched independently and is not re-derived or modified by this pass.

## Scope boundary

The central Hacienda Sede is an aggregator and its procedure catalog links multiple distinct systems (for example ROLECE and IGAE services). The alias does not claim those systems share REG-AGE's signing ABI. It represents only the generic REG-AGE route explicitly declared by Hacienda itself.

`certificate_required`, `signature_required`, `js_client`, signature format/algorithm, endpoint and client-TLS fields therefore remain `NO_VERIFICADO` at the Hacienda alias record. `protocol_family` is only `DELEGACION_REG_AGE`.

## Safety

Research was public and unauthenticated. No credentials were entered; no certificate was selected or supplied; no form was submitted; no protected endpoint was replayed; no document was uploaded or signed; no payment or administrative filing was made.

## Truthful status

The implementation is `IMPLEMENTED_NOT_E2E / E2E_PENDING`. Physical validation of the institutional-to-REG transition remains pending, and the reused REG profile remains QA-only.
