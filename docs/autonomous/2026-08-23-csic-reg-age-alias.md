# CSIC → REG-AGE delegation evidence — 2026-08-23

## Public evidence

- Public unauthenticated GET of `https://sede.csic.gob.es/` returned HTTP 200 and exposed the official `Registro electrónico` page at `https://sede.csic.gob.es/registro-electronico`.
- That first-party page states that electronic submissions use recognized electronic signature, that access requires a current DNIe or recognized digital certificate, and that AutoFirma must be installed.
- The same page publishes `Registro Electrónico Común de la Administración` to `https://rec.redsara.es/registro/action/are/acceso.do`.
- With ordinary Spanish browser language negotiation, that public target resolved by bounded GET as `rec.redsara.es/...` → `https://reg.redsara.es/` → `https://reg.redsara.es/es/` → HTTP 200. The terminal URL is the exact immutable start URL of the existing `reg-age-redsara` profile.

## Bounded implementation

`ES-PUB-0037` is represented only as a catalog alias to the existing REG-AGE profile. The CSIC registration page remains the public `entryUrl`; the launch target is only `https://reg.redsara.es/es/`. The certificate/AutoFirma/signature statements are recorded as first-party observations, but no CSIC-specific signing ABI, signature format, algorithm, endpoint, callback, client-TLS rule, or trusted origin is inferred.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`, QA-only through the referenced REG-AGE profile. No authentication, certificate selection, private-key operation, upload, POST, payment, filing, or administrative submission was performed.
