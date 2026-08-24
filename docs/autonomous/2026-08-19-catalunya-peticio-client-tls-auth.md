# Generalitat de Catalunya — Petició genèrica: bounded CLIENT_TLS_AUTH evidence (2026-08-19)

## Scope

Target: `ES-PUB-0105`, Petició genèrica, service `ING001HTM2`. This note records only the certificate-authentication boundary. It does not claim a document-signature ABI or portal acceptance after authentication.

## Exact bounded contract

- Launch: `https://ovt.gencat.cat/gsitgf/AppJava/traint/renderitzar.do?reqCode=inicial&set-locale=ca_ES&idioma=ca_ES&idServei=ING001HTM2&urlRetorn=https%3A%2F%2Ftramits.gencat.cat%2Fca%2Ftramits%2Ftramits-temes%2FPeticio-generica%3Fcategory%3D72461610-a82c-11e3-a972-000c29052e2c`
- Cl@ve source: `https://pasarela.clave.gob.es/Proxy2/ServiceProvider`
- eIdentifier client-certificate request: `https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`
- Transition: `DIRECT_FROM_SOURCE`; HTTPS port 443; no fixed or ephemeral target query parameters were observed.

## Controlled runtime revalidation

Labuba job `job_20260819_123946_21de3921` replayed the already-established controlled certificate-auth path on 2026-08-19. Sanitized status chain:

1. eIdentifier `AuthenticateCitizen`: HTTP 200.
2. Cl@ve `ResponseRedirect`: HTTP 200.
3. GSIT `redirectservlet`: HTTP 302.
4. GSIT `inicial.do`: HTTP 302.
5. GSIT `j_acegi_security_check`: HTTP 500.

The client-certificate bridge reported one accepted connection and one successful upstream TLS connection; runtime cleanup passed. No private-key document signature, final filing, registration, submission, upload, or payment was executed.

## Capability boundary

The smallest truthful implementation is QA-only `CLIENT_TLS_AUTH` for the exact Cl@ve/eIdentifier request. The GSIT 500 occurs after the certificate-authentication boundary and prevents reaching the protected form/signing runtime. Therefore `js_client`, signature format, signature algorithm, signing endpoint/callback and final portal acceptance remain `NO_VERIFICADO`; inventory status is `IMPLEMENTED_NOT_E2E`.
