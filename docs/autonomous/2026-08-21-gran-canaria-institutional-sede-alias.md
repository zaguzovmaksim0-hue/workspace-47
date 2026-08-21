# Gran Canaria institutional portal → Instancia General alias — 2026-08-21

## Current first-party evidence

- `https://cabildo.grancanaria.com/` returned HTTP 200 and published `Sede Electrónica` links to `https://sede.grancanaria.com`.
- `https://sede.grancanaria.com/` returned HTTP 200 and published `Instancia General` to `https://sede.grancanaria.com/informacion-instancia`.
- `https://sede.grancanaria.com/informacion-instancia` returned HTTP 200 and published `Iniciar trámite` exactly to `https://sede.grancanaria.com/sede-privado/instancia-general?inicio`.
- That final URL is byte-for-byte equal to the existing QA-only `gran-canaria-sede-electronica` profile `startUrl`. A bounded GET currently reaches the pre-auth page `https://sede.grancanaria.com/sede-privado/auth`; no authentication or state-changing action was required for this alias evidence.

## Implementation boundary

`ES-PUB-0137` remains the institutional catalog entry and receives only the exact existing profile start URL as `launchUrl`. The institutional origin is not added to signing trust, initiator origins, callback trust, certificate handling or signer ABI. Certificate requirement, signature requirement, JavaScript client, algorithm, format, endpoint and client-TLS remain `NO_VERIFICADO` on the alias record.

Classification: `ALIAS_ONLY`. Status: `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. No authentication, private-key signature, upload, payment, final filing or administrative submission was performed.
