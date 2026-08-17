# INAP → REG-AGE delegation evidence — 2026-08-17

## Public evidence

- Public unauthenticated GET of `https://sede.inap.gob.es/` returned the official INAP electronic-office page.
- The page contains three public links to `https://reg.redsara.es/`; one has visible text and title `Acceso al Registro Electrónico General`, and two additional links are labelled `Registro electrónico`.
- The already implemented `reg-age-redsara` profile has immutable QA start URL `https://reg.redsara.es/es/`. A public unauthenticated GET of that exact URL returned HTTP 200 and the title `REG - Registro Electrónico General` on 2026-08-17.

## Bounded implementation

`ES-PUB-0055` is therefore represented as a catalog alias to the existing REG-AGE profile. The INAP page remains the public `entryUrl`; the launch target is only the exact existing profile start URL. No new INAP signing origin, endpoint, algorithm, callback, certificate-selection rule, or client-TLS rule is inferred.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`, QA-only through the referenced REG-AGE profile. No credentials, real certificate/private key, signature, upload, POST, or administrative submission were used.
