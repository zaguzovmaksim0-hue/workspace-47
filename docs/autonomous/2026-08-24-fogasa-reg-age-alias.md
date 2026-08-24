# FOGASA → REG-AGE alias boundary — 2026-08-24

## Decisive current evidence

- Strict Chromium (no `--ignore-certificate-errors`) loaded `https://sede.fogasa.mites.gob.es/` and the first-party page `https://sede.fogasa.mites.gob.es/SEDE/gestion/catalogoTramites/otrosTramites.xhtml` without a certificate error.
- The rendered first-party DOM exposes `https://rec.redsara.es/registro/action/are/acceso.do` as the Registro Electrónico General route for other administrative procedures.
- Strict TLS revalidation with Spanish browser negotiation followed that URL through `301 https://reg.redsara.es/` and `302 https://reg.redsara.es/es/` to HTTP 200 with `ssl_verify_result=0`.
- `https://reg.redsara.es/es/` is the exact existing `reg-age-redsara` start URL.

## Implementation boundary

`ES-PUB-0046` is therefore an `ALIAS_ONLY` reuse of `reg-age-redsara` for the specific FOGASA “otros trámites” route. No FOGASA origin is added to the REG profile, and no FOGASA-specific signing, certificate-selection, client-TLS, endpoint, callback, format or algorithm contract is inferred.

Earlier exploratory observations made only with disabled TLS verification are intentionally non-promotable and are not used as acceptance evidence. No authentication, certificate selection, private-key operation, upload, payment, POST, or administrative filing was performed.
