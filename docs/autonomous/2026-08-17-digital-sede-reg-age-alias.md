# SEDIA/SETID migrated Sede → REG-AGE delegation evidence — 2026-08-17

## Public evidence

- Public unauthenticated navigation of the AGE-directory entry `https://sedediatid.digital.gob.es/es-es/Paginas/Index.aspx` returned an HTTP 301 migration to the current Ministry portal at `https://digital.gob.es/ministerio/servicios-a-la-ciudadania/procedimientos-y-servicios`.
- That current first-party portal links the Department-associated electronic office `https://digital.sede.gob.es/` and identifies electronic procedures for both the Secretaría de Estado de Digitalización e Inteligencia Artificial and the Secretaría de Estado de Telecomunicaciones e Infraestructuras Digitales.
- A fresh unauthenticated Chromium session loaded `https://digital.sede.gob.es/` with HTTP 200 and exposed `Registro Electrónico General` / `Acceso al Registro Electrónico General` with public href `https://reg.redsara.es/`.
- The decisive first-party service page `https://digital.sede.gob.es/servicio?id=Procedimientos-electr%C3%B3nicos-disponibles-en-la-Sede-Electr%C3%B3nica` states that when a matter is within the Ministry's competence but no dedicated electronic procedure is enabled, the request, writing or communication may be presented through the Registro Electrónico General (REG), and links that REG destination to `https://reg.redsara.es/`.
- In the same fresh public browser runtime, `https://reg.redsara.es/` resolved by HTTP 302 to `https://reg.redsara.es/es/`, exactly the immutable `startUrl` of the existing `reg-age-redsara` profile.

## Bounded implementation

`ES-PUB-0089` is represented as an `ALIAS_ONLY` catalog binding to the existing QA-only REG-AGE profile. The historical SEDIA/SETID directory URL remains the catalog `entryUrl` to preserve the source surface identity; the current Ministry-associated Sede service page is retained as `procedure_page`; the only launch target is the exact existing profile start URL.

No `digital.gob.es`, `digital.sede.gob.es`, or `sedediatid.digital.gob.es` origin is added to REG-AGE signing trust. No REG-AGE algorithm, signature format, callback, certificate rule, endpoint, client-TLS behavior, or other cryptographic ABI is copied into the institutional record by analogy. Those inventory fields remain `NO_VERIFICADO` unless independently proven for the institutional surface.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. No credentials, Cl@ve authentication, certificate/private key use, signature, upload, POST/form submission, payment, or administrative filing was performed.
