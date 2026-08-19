# UNED → REG-AGE delegation evidence — 2026-08-17

## Public evidence

- Fresh unauthenticated Chromium loaded the legacy official UNED Sede `https://sede.uned.es/` with HTTP 200. The page states that, from **1 November 2025**, procedure requests must be made through the new Sede `https://uned.sede.gob.es`, and its public `Registro Electrónico` link targets `https://uned.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General`. The rendered legacy page SHA-256 was `32e57c7ea74a2543b4834548deb6fae2d838ca5cdf5ad48fb9d3538b4f4863b4`.
- Fresh unauthenticated Chromium loaded `https://uned.sede.gob.es/` with HTTP 200, title `Sede Electrónica de UNED - Inicio`, rendered SHA-256 `59e28fcdaef48b7eeff37b58aa4639edf1a563b8340d5d924c3945de2f2f59e6`. Its public catalog exposes `Registro Electrónico` for documentation and requests without a specific electronic procedure or form.
- Fresh unauthenticated Chromium loaded the exact UNED service `https://uned.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General` with HTTP 200, title `Sede Electrónica de UNED - Servicio`, rendered SHA-256 `46e638927f30a396ad80040e0ec0b648d4591c657624d33bb3d970b295552fbf`.
- That UNED service explicitly identifies the service as the **Registro Electrónico General de la Administración General del Estado (REG-AGE)**, states that registrations generate entries in the REG-AGE book, and publishes `Acceso al Registro Electrónico` to `https://reg.redsara.es/`.
- A separate fresh unauthenticated Chromium session loaded `https://reg.redsara.es/` and observed the exact public chain `302 https://reg.redsara.es/` → `200 https://reg.redsara.es/es/`, title `REG - Registro Electrónico General`. No POST was sent.

## Bounded implementation

`ES-PUB-0092` is represented only as an `ALIAS_ONLY` catalog binding to the existing `reg-age-redsara` profile. The exact current UNED Registro service remains the public `entryUrl`; the only integrated launch target is the profile's existing canonical `https://reg.redsara.es/es/` start URL.

No UNED origin is added to REG-AGE signing trust. No UNED-specific signing origin, endpoint, algorithm, callback, certificate-selection rule, signature format, client-TLS rule, or AC2 signing behavior is inferred. Although the new UNED Sede loads generic ACCEDA2 scripts, those are not used as the implementation contract because the selected UNED service explicitly delegates to REG-AGE.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`, QA-only through the referenced REG-AGE profile. Research remained public and unauthenticated: no credentials, Cl@ve completion, certificate/private-key use, signature, upload, mutating form submission, payment, or administrative filing occurred. Telemetry POSTs encountered during browser observation were intercepted and aborted.
