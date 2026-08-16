# Exact Sede alias group — Cantabria + La Palma — 2026-08-16

## Scope and safety boundary

This milestone reuses existing QA-only implemented profiles only when the public institutional/Sede page contains an exact HTTPS link equal to the target profile `startUrl`. Inspection used unauthenticated, read-only HTTPS GETs with normal TLS verification. No authentication, cookies/session replay, certificate/private key, signature, upload, POST, administrative submission, or TLS bypass was used.

## Cantabria Sede → Registro Electrónico Común

- Source entry: `https://sede.cantabria.es/sede/`.
- 2026-08-16 GET: HTTP 200, final URL unchanged, body SHA-256 `4ca5fc168cc09f4cf2a4e7e506178daeddbd91796656ef162a52709b53de5c7b`.
- The returned first-party HTML contains the exact anchor `https://rec.cantabria.es/rec/bienvenida.htm`.
- That URL is exactly the existing `cantabria-rec-cert-login` profile `startUrl` and the independently catalogued `cantabria-registro-electronico-comun` surface is already `IMPLEMENTED_NOT_E2E`.
- The alias does not claim that `sede.cantabria.es` itself runs the REC MiniApplet ABI. The entry URL remains the Sede; only the explicit launch target resolves to the existing QA profile.

## La Palma institutional portal → Sede electrónica

- Source entry: `https://www.cabildodelapalma.es/`.
- 2026-08-16 GET: HTTP 200, final URL unchanged, body SHA-256 `6a2a04a0eaa9820676b5350df8f59af3fc44d561c483fd6c591b0e6eaf90301c`.
- The returned first-party HTML contains the exact anchor `https://sedeelectronica.cabildodelapalma.es/` (multiple occurrences).
- That URL is exactly the existing `la-palma-sede-electronica` profile `startUrl`; the separately catalogued Sede is already `IMPLEMENTED_NOT_E2E`.
- The alias does not grant signing trust to `www.cabildodelapalma.es`; the institutional entry remains metadata and launches only the exact already-bounded Sede profile.

## Status boundary

Both aliases are `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` only. Release eligibility and `VERIFIED_E2E` are unchanged. A later physical/manual authorized E2E may validate the navigation handoff, but this milestone performs no real administrative transaction.
