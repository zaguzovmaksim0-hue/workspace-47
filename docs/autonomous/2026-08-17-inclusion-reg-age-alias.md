# Ministerio de Inclusión → REG-AGE delegation evidence — 2026-08-17

## Public evidence

- A public unauthenticated GET of `https://sede.inclusion.gob.es/` returned HTTP 200 and the official electronic-office landing for the Ministerio de Inclusión, Seguridad Social y Migraciones.
- The first-party page `https://sede.inclusion.gob.es/registroelectronico` returned HTTP 200. It states that the following address gives access to the `Registro Electrónico de la Administración General del Estado` and publishes the launch `https://rec.redsara.es/registro/action/are/acceso.do`.
- A bounded unauthenticated GET of that exact published legacy URL returned HTTP 301 with `Location: https://reg.redsara.es/`. No login, certificate, POST, upload, signature, or administrative action was performed.
- The current official Punto de Acceso General page `https://administracion.gob.es/pag_Home/atencionCiudadana/Registros-electronicos-AGE.html` identifies this service as `Registro Electrónico General (REG-AGE)` and publishes `Acceso al REG` on `https://reg.redsara.es/`.
- The existing QA-only `reg-age-redsara` profile has immutable canonical start URL `https://reg.redsara.es/es/`; a bounded unauthenticated GET of that exact URL returned HTTP 200 with title `REG - Registro Electrónico General` on 2026-08-17.

## Bounded implementation

`ES-PUB-0068` keeps `https://sede.inclusion.gob.es/` as institutional `entryUrl` and records the first-party `/registroelectronico` page as the procedure evidence. QA launches only the exact existing profile start URL `https://reg.redsara.es/es/` under `reg-age-redsara`.

The legacy `rec.redsara.es` URL is evidence of the Ministry's delegation and its migration to the current REG origin; it is not added as a trusted signing origin and is not used as the runtime launch URL. No Inclusion-specific signing endpoint, algorithm, signature format, callback, certificate rule, client-TLS rule, or cryptographic ABI is inferred.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. Release remains fail-closed until a safe physical E2E transition is demonstrated.
