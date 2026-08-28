# Junta de Andalucía VEA PEG — public CLIENT_TLS_AUTH contract evidence

Reviewed: 2026-08-28
Inventory: `ES-PUB-0093`
Profile: `junta-andalucia-vea-peg`
Status: `IMPLEMENTED_NOT_E2E`

Public unauthenticated navigation established the following bounded certificate-login contract:

1. `https://veaja.cloud.juntadeandalucia.es/authFacade` initiates `GET https://api-veaja.cloud.juntadeandalucia.es/auth/login` with `modoAcceso=afirma`, `codigoProcedimiento=PEG_VEA`, a Base64 callback to the exact VEA `/authFacade`, and a Base64 redirect URL restricted to the PEG_VEA procedure route.
2. `/auth/login` redirects to `https://ws235.juntadeandalucia.es/authenticationFacade` with `action=validateCert`, `appId=CHIE.VEA`, a Base64 callback to the exact API `/auth/returnLogin`, and ephemeral `ticketId` / `webSessionId` values.
3. The same shared authentication facade has been observed issuing a client-certificate request without an advertised CA list. The profile therefore permits an empty issuer list while retaining the existing RSA/EC key constraints.
4. The public no-certificate failure path returns through `/auth/returnLogin`, `/auth/endLogin`, then VEA `/authFacade` with an error result. The current public VEA bundle also handles the successful `/authFacade` shape with `token` plus `redirectUrl`.

Runtime trust is deliberately narrower than each origin. `api-veaja` is accepted only for the exact source/return paths and query contracts above. `ws235` is accepted only for the exact VEA source-to-target transition and its exact `appId`/callback contract. The client certificate challenge is handled in the same WebView so the public authentication session is not copied between WebViews.

No real certificate, authenticated POST, private-key operation, document signing, final filing, or payment was performed while collecting this evidence. `SIGN`, `SELECT_CERTIFICATE`, and `AFIRMA_URI` remain outside this profile.
