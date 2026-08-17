# Cantabria Sede → Registro Electrónico Común alias — 2026-08-16

## Public first-party evidence

- Source entry: `https://sede.cantabria.es/sede/`.
- A fresh unauthenticated TLS-verified GET on 2026-08-16 returned HTTP 200.
- The current first-party HTML contains the exact public anchor `https://rec.cantabria.es/rec/bienvenida.htm`, labelled `Registro Electrónico General`.
- That URL is byte-for-byte equal to the existing QA-only `cantabria-rec-cert-login` profile `startUrl`.

The Sede page is dynamic (session cookies and changing content), so its complete HTML body is not hash-pinned as a contract. The durable contract is the exact HTTPS delegation target plus the already separately reviewed REC profile.

## Implementation boundary

`cantabria-sede` remains the catalog entry at `https://sede.cantabria.es/sede/` and receives only an exact `launchUrl` to `https://rec.cantabria.es/rec/bienvenida.htm`. It does not grant the REC signing ABI, initiator trust, redirect trust, certificate capability, or callback contract to `sede.cantabria.es`. `PortalCatalogRepository` enables the alias only when the launch URL exactly equals the registered profile start URL; any path substitution fails closed.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; release remains disabled pending separate physical E2E evidence. No authentication, certificate/private key, signature, form POST, upload, payment, or administrative submission was performed.
