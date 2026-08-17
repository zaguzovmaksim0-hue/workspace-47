# AEMPS → REG-AGE delegation evidence — 2026-08-17

## Public evidence

- Public unauthenticated GET of `https://sede.aemps.gob.es/` returned the official AEMPS electronic-office page.
- The page exposes a public link with visible text `Registro`, target `https://reg.redsara.es/`, and title `Abre en una pestaña nueva: portal del registro electrónico general`.
- The already implemented `reg-age-redsara` profile has immutable QA start URL `https://reg.redsara.es/es/`. A public unauthenticated GET of that exact canonical URL returned HTTP 200 with title `REG - Registro Electrónico General` on 2026-08-17.

## Bounded implementation

`ES-PUB-0023` is represented only as a catalog alias to the existing REG-AGE profile. The AEMPS Sede remains the public `entryUrl`; the launch target is only the exact existing profile start URL. No new AEMPS signing origin, endpoint, algorithm, callback, certificate-selection rule, signature format, or client-TLS rule is inferred.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`, QA-only through the referenced REG-AGE profile. No credentials, real certificate/private key, signature, upload, POST, or administrative submission were used.
