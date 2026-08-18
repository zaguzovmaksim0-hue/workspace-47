# Ministerio del Interior → REG-AGE bounded delegation evidence — 2026-08-17

## Decision

`ES-PUB-0077` is an `ALIAS_ONLY` binding to the existing `reg-age-redsara` profile. The current Ministerio del Interior Sede does not require a guessed REG-AGE association: its public **Formulario de propósito general** page publishes an exact access target on the legacy REC hostname, and the current bounded redirect chain terminates exactly at the existing REG-AGE profile start URL.

## Public unauthenticated evidence

- Historical directory entry `https://sede.mir.gob.es/opencms/export/sites/default/es/inicio/` now redirects to the current Sede at `https://sede.interior.gob.es/portal/sede`. A fresh public Chromium session reached the new Sede with HTTP 200 and rendered the Ministerio del Interior landing page.
- The current first-party page `https://sede.interior.gob.es/portal/sede/tramites?codAgrupacion=GENERAL` describes **Formulario de propósito general** as the presentation form for procedures that do not have a specific form.
- That page publishes a first-party button `Acceso` whose literal `location.href` is `https://rec.redsara.es/registro/action/are/acceso.do`.
- Current public redirect chain with Spanish `Accept-Language`: `https://rec.redsara.es/registro/action/are/acceso.do` → HTTP 301 `https://reg.redsara.es/` → HTTP 302 `https://reg.redsara.es/es/` → HTTP 200. The final URL exactly equals `reg-age-redsara.startUrl`.
- The current detail page `https://sede.interior.gob.es/portal/sede/tramites/detalle-tramite?id=14` identifies the item as **Formulario de propósito general para presentación de escritos**. Its metadata currently reports access level `0` and person-type access flags `No`; these values are not interpreted as a cryptographic contract.
- The generic official page `https://sede.interior.gob.es/portal/sede/informacion-general/sistema-firmas` states that the Sede uses recognized X.509 certificates and the `@firma` platform for certificate validation. This is contextual only and is deliberately **not** used to infer the signature operation, format, algorithm, endpoint, callback or certificate policy for the generic-form alias.
- No credentials, Cl@ve login, certificate selection/use, private key, signing, POST submission, document upload, payment, or administrative presentation was performed.

## Current resource hashes

Public HTML/resources were fetched only for current unauthenticated inspection; no cookies/session values are persisted here.

- `Formulario de propósito general` page HTML SHA-256: `cc7d738abf340a8465335c4b7f2290389abc85ac7f96b36829d27f52b74572fa`
- detail page `id=14` HTML SHA-256: `088d53d179362e0f9fe2b454821168c8f45931fa3ab0bcbf51bdc7e60b889d76`
- `sistema-firmas` page HTML SHA-256: `6c5c792aff360699ccf0395967d196dd06dd8bdb691dac7ad85258e931ec1faa`
- `matomoSede.js` SHA-256: `2b37dacb82e429d49919bdef927a869918f86137a61e4e2b7032448aebd71a80`
- `bootstrap.min.js` SHA-256: `494ccfbbe7b08d90a3e82b7056cf6c361e90fcb3058b5c35459f53c692a65641`
- `popper.min.js` SHA-256: `7a409fd037337862ad8373afd1e77781984d6961c90c00d901ae04664768b01b`

## Bounded implementation

The Interior page remains the `entry_url` / `procedure_page`; the catalog `launch_url` is only the exact existing `https://reg.redsara.es/es/` start URL. `config/site_profiles_v1.json` is unchanged, so the Interior origin is **not** added to REG-AGE `initiatorOrigins`, trusted browse origins, redirect origins, endpoints, signing policies or certificate rules.

`certificate_required`, `signature_required`, `js_client`, signature format/algorithm, endpoint and client-TLS remain conservative/unverified at the Interior inventory layer. Existing REG-AGE cryptographic capabilities are not re-attributed to `sede.interior.gob.es`.

Status is `IMPLEMENTED_NOT_E2E` / generated `E2E_PENDING`. No physical accepted-flow E2E or `VERIFIED_E2E` claim is made; release remains fail-closed.
