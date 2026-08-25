# Ministerio de Ciencia → REG-AGE delegation evidence — 2026-08-17

## Public evidence

- Public unauthenticated GET of `https://ciencia.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General` returned the Ministry's public service page.
- The page explicitly identifies the service as the `Registro Electrónico General de la Administración General del Estado (REG-AGE)` and contains an HTTPS link to `https://reg.redsara.es/`.
- The observed body for this verification was 23,561 bytes with SHA-256 `2fc8f30dc72d10471d41aca76b1e1b1b0ef89fcb04a97d6466710137b816f50f`.

## Bounded implementation

`ES-PUB-0061` is represented only as a QA catalog alias to the existing `reg-age-redsara` profile. The Ministry Sede remains the `entryUrl`; `launchUrl` is the immutable canonical profile start URL `https://reg.redsara.es/es/`.

No Ministry-specific signing origin, endpoint, algorithm, callback, certificate-selection rule, signature format, or client-TLS rule is inferred. The alias must fail closed if its launch URL differs from the profile start URL.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. No credentials, real certificate/private key, signature, upload, POST, payment, or administrative submission were used.
