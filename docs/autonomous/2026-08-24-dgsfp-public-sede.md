# ES-PUB-0042 — DGSFP public Sede boundary

Reviewed 2026-08-24 using unauthenticated first-party HTTPS and static public JavaScript only. `https://www.sededgsfp.gob.es/` redirects on the same origin to `/es/Paginas/inicio.aspx`, which returned HTTP 200. The live SharePoint Sede publishes first-party bundles under `Style Library/DGSFP.SedeElectronica/Scripts/`.

The public bundles contain resource/configuration names for certificate and Cl@ve access, procedures, notifications, `IsAfirmaEnabled`, `TestFirma`, and @firma signature retriever/storage parameters. This proves those concepts exist in the public client, but does not prove the current authenticated flow, exact signer ABI, signature format, algorithm, payload packaging, callback, client-TLS contract, or final administrative acceptance.

The implemented profile therefore exposes only QA-only public navigation on the exact first-party origin `https://www.sededgsfp.gob.es`. It exposes no SIGN, SELECT_CERTIFICATE, CLIENT_TLS_AUTH, AFIRMA_URI, endpoint, operation policy, redirect origin, or external browse trust. No certificate was selected, no login was completed, no document was uploaded or signed, and no filing was submitted. Transient SharePoint cookies and request identifiers were not stored.
