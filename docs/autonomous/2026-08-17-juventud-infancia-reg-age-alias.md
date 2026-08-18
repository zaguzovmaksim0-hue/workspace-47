# Ministerio de Juventud e Infancia → REG-AGE delegation evidence — 2026-08-17

## Public unauthenticated evidence

- `https://juventudeinfancia.sede.gob.es/` is the official electronic office for the Ministerio de Juventud e Infancia. Its public home page exposes a **Formulario genérico** for requests, writings and communications that are not associated with a normalized procedure and states that these can be presented through the **Registro Electrónico General (REG)**.
- The same public site exposes `https://juventudeinfancia.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General`. That service page explicitly describes the **Registro Electrónico General de la Administración General del Estado (REG-AGE)** and publishes the public launch link `https://reg.redsara.es/`.
- In a fresh unauthenticated Chromium context configured with locale `es-ES`, navigating the published REG root returned HTTP 200 and resolved exactly to `https://reg.redsara.es/es/`, the immutable `startUrl` of the already implemented `reg-age-redsara` profile.
- Direct command-line HTTP access to the institutional Sede returned HTTP 403, so evidence was read through an unauthenticated browser context. No login, Cl@ve, certificate, private key, form submission, upload, signature, payment or administrative presentation was attempted.

## Bounded implementation

`ES-PUB-0070` is represented only as a QA catalog alias to the existing `reg-age-redsara` launch. The official Ministerio service page remains the `entryUrl`/`procedure_page`; the resolved launch is the exact existing profile start URL.

No signing origin, signing endpoint, algorithm, callback contract, certificate-selection rule, client-TLS rule, observed signature format, or other cryptographic capability is inferred for `juventudeinfancia.sede.gob.es`. Release remains fail-closed. Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; no `VERIFIED_E2E` claim is made.
