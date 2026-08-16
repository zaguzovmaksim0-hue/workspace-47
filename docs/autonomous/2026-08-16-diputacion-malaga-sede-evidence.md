# Diputación Provincial de Málaga — Public Sede BROWSE_ONLY Evidence — 2026-08-16

## Scope and Safety Boundary

Only official public, unauthenticated HTTPS GET requests were used. No client certificate, private key, authenticated session, credential, cookie replay, signature, POST, upload, payment, or administrative submission was used.

## Public Observations

1. Inventory `ES-PUB-0164` binds the official seed `https://sede.malaga.es`.
2. A strict TLS-verified public GET on 2026-08-16 returned HTTP 200 on the same origin.
3. Existing reviewed inventory evidence binds a real `Instancia General` flow and confirms identification/electronic signature requirements, but the exact signing client invocation remains behind the authentication/stateful boundary; no fixed algorithm, format/packaging, payload, callback, endpoint, or client-TLS contract is public before that boundary.
4. Therefore no signing or certificate-sharing capability is promoted into the runtime profile.

## Contract Conclusion

The exact seed is enabled only as `BROWSE_ONLY`, with the single initiator origin `https://sede.malaga.es`, no redirect/trusted browse origins, no endpoints, no operation policies, no capabilities, and no client-auth policy. Inventory status remains `BROWSE_ONLY`.
