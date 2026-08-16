# Deputación de Lugo — Public Sede BROWSE_ONLY Evidence — 2026-08-16

## Scope and Safety Boundary

Only official public, unauthenticated HTTPS GET requests were used. No client certificate, private key, authenticated session, credential, cookie replay, signature, POST, upload, payment, or administrative submission was used.

## Public Observations

1. Inventory `ES-PUB-0163` binds the official seed `https://sede.deputacionlugo.org`.
2. A strict TLS-verified public GET on 2026-08-16 returned HTTP 200 and resolved on the same origin to `/opencms/system/modules/sede/index`.
3. Existing reviewed inventory evidence records conditional certificate access and electronic-signature capability, but no fixed signing algorithm, format/packaging, payload, callback, endpoint, or client-TLS contract.
4. Therefore no signing or certificate-sharing capability is promoted into the runtime profile.

## Contract Conclusion

The exact seed is enabled only as `BROWSE_ONLY`, with the single initiator origin `https://sede.deputacionlugo.org`, no redirect/trusted browse origins, no endpoints, no operation policies, no capabilities, and no client-auth policy. Inventory status remains `BROWSE_ONLY`.
