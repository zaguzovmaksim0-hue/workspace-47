# MAPA → REG-AGE delegation evidence — 2026-08-17

## Public evidence

- Public unauthenticated GET of `https://sede.mapa.gob.es/portal/site/seMAPA` returned the official electronic-office page of the Ministerio de Agricultura, Pesca y Alimentación.
- Its public footer contains an HTTPS anchor to `https://reg.redsara.es/`; the linked image is `reg_footer.png` and its alt text is the same REG URL.
- The root REG URL was WAF-blocked with HTTP 403 from the Termux network during this verification, so no redirect behavior is asserted.

## Bounded implementation

`ES-PUB-0059` is represented only as a QA catalog alias to the existing `reg-age-redsara` profile. The MAPA Sede remains the `entryUrl`; `launchUrl` is the immutable canonical start URL already configured for that profile: `https://reg.redsara.es/es/`.

No MAPA-specific signing origin, endpoint, algorithm, callback, certificate-selection rule, signature format, or client-TLS rule is inferred. The alias must fail closed if its launch URL diverges from the profile start URL.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. No credentials, real certificate/private key, signature, upload, POST, payment, or administrative submission were used.
