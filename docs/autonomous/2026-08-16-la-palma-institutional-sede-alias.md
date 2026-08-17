# La Palma institutional portal → Sede electrónica alias — 2026-08-16

## Public first-party evidence

- Source entry: `https://www.cabildodelapalma.es/`.
- A fresh unauthenticated TLS-verified GET on 2026-08-16 returned HTTP 200.
- The first-party HTML contains the exact anchor `https://sedeelectronica.cabildodelapalma.es/` multiple times, labelled `Sede electrónica`.
- That URL is byte-for-byte equal to the existing QA-only `la-palma-sede-electronica` profile `startUrl`.

## Implementation boundary

`la-palma-portal-institucional` remains the catalog entry at `https://www.cabildodelapalma.es/` and receives only the exact Sede `launchUrl`. No signing ABI, initiator trust, redirect trust, trusted-browse origin, certificate operation or callback contract is granted to the institutional origin. `PortalCatalogRepository` enables the alias only when the launch URL exactly equals the registered profile start URL; path substitution fails closed.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; release remains disabled pending separate physical E2E evidence. No authentication, certificate/private key, signature, POST, upload, payment or administrative submission was performed.
