# ES-PUB-0087 — Secretaría de Estado de Comercio → REG-AGE public alias evidence

Reviewed: 2026-08-17

## Scope and safety boundary

This pass used public unauthenticated observation only. No login, credential, certificate selection, signature, form submission, upload, payment, administrative filing, authenticated session or private endpoint was used.

The implemented contract is **ALIAS_ONLY**. `sede.mineco.gob.es` remains institutional/procedure metadata and is not added to the existing REG-AGE profile's cryptographic trust. No certificate requirement, signing algorithm, signature format, AutoFirma ABI, server endpoint or client-TLS behavior is inferred from REG-AGE or from another ministry.

## Current ownership and surface correction

The historical D11 entry `https://sede.comercio.gob.es/` is no longer the current Comercio Sede surface. A bounded public redirect check on 2026-08-17 showed it redirecting to `sede.serviciosmin.gob.es`, whose final public landing identifies itself as the Sede electrónica del Ministerio de Industria y Turismo. That redirect therefore cannot establish a current Secretaría de Estado de Comercio signing or REG contract.

The current first-party Commerce portal `https://comercio.gob.es/` identifies the Comercio area and publishes the link **Procedimientos de la Sede: Servicios** to:

`https://sede.mineco.gob.es/es/procedimientos-y-servicios-electronicos/areas-tematicas/comercio`

The current MINECO Commerce thematic page returned HTTP 200 and has static HTML SHA-256 `d6a049accf0eca2e2ae342ad7f8aab9ae32c468dfdd94148bc1722f9ae7daa1e`.

Its public SPFx configuration queries `/Lists/SedeProcedures` and uses a closed Commerce management-center filter. An unauthenticated public SharePoint REST GET returned 55 visible Commerce procedures, including records assigned to the Secretaría de Estado de Comercio and its current Commerce directorates.

## Exact current REG delegation

The current public procedure selected as evidence is:

- SIA: `3057517`
- title: `Excepciones al comercio internacional de servicios sujeto a restricciones UE`
- public detail URL: `https://sede.mineco.gob.es/es/procedimientos-y-servicios-electronicos/areas-tematicas/comercio/detalle-procedimiento?val=3057517`
- current management center in the public list: `Dirección General de Política Comercial`
- current warning: while the dedicated administrative procedure is enabled, the request may be submitted through the electronic registry of the AGE
- published REG destination: `https://reg.redsara.es`
- published target unit: `Subdirección General de Comercio Internacional de Servicios, Comercio Digital y Cadenas de Valor`, DIR3 `EA0038920`

The exact anonymous REST response used for the selected item had SHA-256 `284701c2aab713eaaaa958a33177abecc5f00f0866c10288a001a6d2344f7593`. The static detail shell had SHA-256 `24c5e60cd554bbe13565a87ee6e457d851b23357ab2f9022798540eefdae61f2`; the decisive warning itself is rendered at runtime from the public SharePoint list.

## Static resource map

The public Commerce thematic renderer uses:

- `https://sede.mineco.gob.es/Sede-Styles/handlebars/Mineco.Sede.ThematicAreas.hbs` — SHA-256 `7fc329136b900f27e999e2758133ee7d3cb5b2d2db09387d7706366ee07f13d2`, 2,189 bytes. The template builds non-custom detail links from the exact SIA code as `detalle-procedimiento?val=...`.
- `https://sede.mineco.gob.es/Sede-Styles/js/mineco_sede.min.js` — SHA-256 `b1674a24037f5281ff2779ea9fbcff617df5a73f547706efdde3c07875bec795`, 7,221 bytes.

These resources establish the public rendering path; they do not establish a cryptographic signing ABI.

## Browser / network / runtime proof

Headless Chromium was attempted first in fresh off-the-record contexts, but the current MINECO/Commerce front door returned its generic `No se puede acceder al sitio Web` shell specifically to that runtime mode. Static public GET remained available. No promotion was based on that failed mode.

The decisive runtime pass used a dedicated non-headless Chromium 149 process on Termux X11 with a new temporary browser profile and fresh off-the-record CDP BrowserContexts. It did not reuse the user's browser profile.

Durable browser evidence job: `job_20260817_190421_e9137254`.

On the Commerce thematic page the runtime DOM rendered the SIA `3057517` card and the network log showed the public Fetch GET to `/es/_api/web/getList('/es/Lists/SedeProcedures')/items?...` with the closed Commerce management-center filter.

On the exact SIA `3057517` detail page the runtime DOM rendered the current warning that the request may be made through the AGE electronic registry and displayed the literal REG destination `https://reg.redsara.es`. The page's network log showed the public Fetch GET for the same exact SIA code from `SedeProcedures`. The ordinary ASP.NET page form was not submitted.

A second fresh off-the-record browser context then navigated only to the published public REG root. Network evidence recorded:

1. `GET https://reg.redsara.es/`
2. HTTP `302`
3. `GET https://reg.redsara.es/es/`
4. final title `REG - Registro Electrónico General`, document language `es`

The final URL `https://reg.redsara.es/es/` is exactly the existing `reg-age-redsara.startUrl`.

No login, certificate, signing dialog, submission, PRE/POST signing endpoint, Storage/Retrieve endpoint or administrative action was triggered. The dedicated Chromium process was stopped and its temporary profile was deleted after observation.

## Decision

Classification: `ALIAS_ONLY`.

- current institutional official site: `https://comercio.gob.es/`
- current electronic-sede origin used by the exact procedure: `https://sede.mineco.gob.es`
- evidence/procedure entry: `https://sede.mineco.gob.es/es/procedimientos-y-servicios-electronicos/areas-tematicas/comercio/detalle-procedimiento?val=3057517`
- exact delegated launch: `https://reg.redsara.es/es/`
- existing profile: `reg-age-redsara`
- inventory protocol family: `DELEGACION_REG_AGE`
- target status: `IMPLEMENTED_NOT_E2E` / generated `E2E_PENDING`
- ministry-specific observed signature mechanisms/formats: none claimed
- physical E2E acceptance: not performed and not claimed
