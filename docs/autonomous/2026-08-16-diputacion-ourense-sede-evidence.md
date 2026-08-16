# Sede electrónica de Deputación de Ourense — Public BROWSE_ONLY Evidence — 2026-08-16

## Scope and safety boundary

Only official public, unauthenticated HTTPS GET inspection and existing reviewed repository inventory evidence were used. No client certificate, private key, authenticated session, cookie replay, signature, POST, upload, payment, or administrative submission was used.

## Current public observation

- Inventory: `ES-PUB-0165` / `diputacion-ourense-sede`.
- Exact catalog seed: `https://sede.depourense.es`.
- 2026-08-16 bounded public check: strict TLS-verified GET returned HTTP 200 and stayed on the same official origin (redirected to the public STA home path).
- Existing reviewed inventory records conditional certificate access and electronic-signature capability, but does not establish one exact signing algorithm, format/packaging, payload, callback, signer endpoint, or client-TLS contract for this seed.

## Contract conclusion

The exact catalog seed is enabled only as `BROWSE_ONLY`. The runtime profile trusts only `https://sede.depourense.es` as its initiator origin and grants no privileged capabilities, operation policies, endpoints, redirect origins, trusted browse origins, or client-auth policy. Inventory status remains `BROWSE_ONLY`.
