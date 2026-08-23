# Junta de Extremadura PATTEX — bounded client TLS evidence

Reviewed: 2026-08-19

## Promoted contract

Workspace-47 implements only the exact QA-only `CLIENT_TLS_AUTH` transition observed for PATTEX model 060:

- source: `https://pattex.juntaex.es/PATTEX/externos.jsf?info=060~user~pass~SEDE_ALTA~https://pattex.juntaex.es~codigo`
- target: `https://pattex.juntaex.es/PATTEX/accesoCertificadoSEDE.jsf`
- host/port: `pattex.juntaex.es:443`
- the current TLS flow requests a client certificate and accepts an RSA certificate with Digital Signature and TLS Web Client Authentication usage
- the request can present an empty CA-name list; the profile therefore permits an empty issuer list but remains RSA-only and exact-path scoped

Prior controlled authenticated-runtime evidence reached the PATTEX authenticated state with certificate-backed TLS. No certificate identity, private-key material, session token, cookie, or credential is retained in this repository note.

## Boundary

This promotion does **not** claim a document-signing contract. AutoFirma/AutoScript invocation, signature format, signature algorithm, packaging, pre-sign/post-sign endpoints, callbacks, final filing and payment remain unverified and disabled. The profile is `QA_ONLY`; release remains fail-closed pending separate physical E2E.
