# Ministerio de la Presidencia, Justicia y Relaciones con las Cortes → REG-AGE delegation evidence — 2026-08-17

## Public unauthenticated evidence

- `https://mpr.sede.gob.es/` returned HTTP 200 in an unauthenticated Chromium session and identified itself as the official electronic office of the **Ministerio de la Presidencia, Justicia y Relaciones con las Cortes**.
- The public home page exposes **Registro Electrónico General** / **Acceso al Registro Electrónico General** and links to `https://mpr.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General`.
- That official service page explicitly describes the **Registro Electrónico General de la Administración General del Estado (REG-AGE)**, states that it can receive solicitudes, escritos y comunicaciones without a normalized electronic procedure/form, and publishes `https://reg.redsara.es/` as the **Registro Electrónico** access link.
- In a fresh unauthenticated Chromium context configured with locale `es-ES`, navigation to that published REG root returned HTTP 200 and resolved exactly to `https://reg.redsara.es/es/`, the immutable `startUrl` of the already implemented `reg-age-redsara` profile.
- Direct command-line HTTP access to the institutional Sede returned HTTP 403, so the institutional evidence was read through an unauthenticated browser context. No login, Cl@ve, credential, certificate, private key, form submission, upload, signature, payment, or administrative presentation was attempted.

## Bounded implementation

`ES-PUB-0071` is represented only as a QA catalog alias to the existing `reg-age-redsara` launch. The official MPR REG service page remains the `entryUrl`/`procedure_page`; the resolved launch is the exact existing profile start URL.

No signing origin, signing endpoint, algorithm, callback contract, certificate-selection rule, client-TLS rule, observed signature format, or other cryptographic capability is inferred for `mpr.sede.gob.es`. Release remains fail-closed. Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; no `VERIFIED_E2E` claim is made.
