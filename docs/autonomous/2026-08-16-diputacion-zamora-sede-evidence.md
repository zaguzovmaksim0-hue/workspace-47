# Sede electrónica de Diputación Provincial de Zamora — Public BROWSE_ONLY Evidence — 2026-08-16

## Scope and safety boundary

Only official public, unauthenticated HTTPS GET inspection and existing reviewed repository inventory evidence were used. No client certificate, private key, authenticated session, cookie replay, signature, POST, upload, payment, or administrative submission was used.

## Current public observation

- Inventory: `ES-PUB-0177` / `diputacion-zamora-sede`.
- Exact catalog seed: `https://diputaciondezamora.sedelectronica.es`.
- 2026-08-16 bounded public check: bounded GET reached the same-origin /info.0 redirect but did not yield a public body within the approved fetch path; no redirect/session/TLS bypass was attempted.
- Existing reviewed inventory records conditional certificate access and electronic-signature capability, but does not establish one exact signing algorithm, format/packaging, payload, callback, signer endpoint, or client-TLS contract for this seed.

## Contract conclusion

The exact catalog seed is enabled only as `BROWSE_ONLY`. The runtime profile trusts only `https://diputaciondezamora.sedelectronica.es` as its initiator origin and grants no privileged capabilities, operation policies, endpoints, redirect origins, trusted browse origins, or client-auth policy. Inventory status remains `BROWSE_ONLY`.
