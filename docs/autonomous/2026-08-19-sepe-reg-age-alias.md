# SEPE → REG-AGE delegation evidence — 2026-08-19

## Current first-party evidence

- Official SEPE electronic office: `https://sede.sepe.gob.es/`.
- Exact current SEPE Registro page: `https://sede.sepe.gob.es/portalSede/registro-electronico.html`.
- An unauthenticated GET of that page returned HTTP 200 with title **Registro electrónico**.
- The first-party page publishes a link labelled **Registro electrónico común** to `https://rec.redsara.es/`.
- A current public GET of `https://rec.redsara.es/` with Spanish locale resolves to `https://reg.redsara.es/es/`, which is the exact canonical `startUrl` of the existing QA-only `reg-age-redsara` profile.

This is sufficient under the current alias rule to represent the SEPE-to-REG delegation without entering an authenticated SEPE procedure. The previously observed SEPE AutoFirma FAQ and protected procedure launches are not used to infer a SEPE-specific signing ABI.

## Bounded implementation

`ES-PUB-0007` is promoted only to `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` as an `ALIAS_ONLY` capability. The SEPE Registro page remains institutional metadata and Workspace-47 launches only the already reviewed canonical REG-AGE start URL.

The SEPE origin is not added to the REG-AGE profile's initiator, redirect or trusted-browse origins. No SEPE-specific certificate rule, AutoFirma/MiniApplet invocation, signature algorithm, format, payload, callback, endpoint or client-TLS behavior is inferred.

Research used public GET/navigation only. No authentication, certificate/private-key operation, signature, form submission, upload, payment or administrative filing was performed.
