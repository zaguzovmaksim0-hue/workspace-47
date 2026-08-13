# Diputación de Valladolid — bounded client-TLS evidence — 2026-08-13

## Scope

This note records only public, unauthenticated evidence for the certificate-login boundary of the Diputación de Valladolid electronic office. No certificate, credential, authenticated session, signature, upload, or administrative submission was used.

## Public observations

1. The official procedure page `https://www.sede.diputaciondevalladolid.es/tramites-disponibles/12S203/` exposes an electronic start action and states that the applicant identifies with a certificate and later signs the request. This establishes the procedure context, not a document-signing protocol contract.
2. The unauthenticated procedure start redirects to `/tgauth/login?...`. The generic public login page `https://www.sede.diputaciondevalladolid.es/tgauth/login` exposes the exact certificate-login link `/c/portal/cert-login`.
3. An unauthenticated request to `https://www.sede.diputaciondevalladolid.es/c/portal/cert-login` returns an HTTP redirect to `https://www.sede.diputaciondevalladolid.es:21460/c/portal/cert-login`.
4. A TLS 1.2 handshake to the exact `:21460` target sends a client `CertificateRequest`. The server advertises RSA/DSA/ECDSA signing certificate types and sends no client-certificate CA-name list. With no client certificate supplied, the HTTP response redirects to `/errors/no-certificate`.

## Implementation boundary

The supported contract is limited to one QA-only `CLIENT_TLS_AUTH` transition from the exact 443 source path to the exact same-host `:21460` target path. The grant is one-shot and port-pinned. Document signing, co-signing, counter-signing, payloads, signing formats, signing algorithms, and legal submission remain unimplemented and must not be inferred from this evidence.

## E2E status

Not verified E2E. Release activation remains disabled until a separately authorized physical-device test verifies only the certificate-login boundary without performing a document signature or administrative submission.
