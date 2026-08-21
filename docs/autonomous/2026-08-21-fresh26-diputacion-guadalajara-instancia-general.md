# ES-PUB-0156 — Diputación Provincial de Guadalajara — Instancia General — 2026-08-21

## Scope and safety boundary

This pass used current public unauthenticated HTTPS navigation only. A normal cookie-aware public session was used because the current sede requires its own transient session cookie to render stable Wicket pages. No cookie, SAMLRequest, RelayState, credential, certificate, private key, or authenticated content is persisted in this note or in repository data. No document was uploaded or signed and no filing, registration, submission, or payment was performed.

## Current official route

`https://dguadalajara.sedelectronica.es/` currently reaches `/info.0` and renders the public `Carpeta Ciudadana` home. Its highlighted `Instancia General` resolves to the stable public procedure page:

- `https://dguadalajara.sedelectronica.es/catalog/t/5161fa8d-970e-4b48-a506-b2ac34ceafe5`
- visible procedure: `Instancia General`
- Código SIA: `2092234`

The page's `Iniciar tramitación electrónica` control points to the exact stable launch:

`https://dguadalajara.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5`

A direct current GET to the public `/catalog/t/` URL returned HTTP 200. A direct current GET to `/catalog/tw/` entered the sede's `Identificación electrónica` page.

## Observed authentication boundary

The current identification page generates an HTTP POST to exactly:

`https://pasarela.clave.gob.es/Proxy2/ServiceProvider`

with transient SAML fields and an assertion-consumer return into the Guadalajara sede. A bounded public unauthenticated POST using only that freshly generated request reached the Cl@ve service but returned HTTP 500 before any identity method was selected. Therefore this pass records only the observed `pasarela.clave.gob.es` navigation origin. It does **not** infer `pasarela-ident.clave.gob.es`, `pasarela-ident-sistemas.clave.gob.es`, certificate client TLS, or any downstream authentication mechanism from other `sedelectronica.es` tenants.

## Signature boundary

The current public `https://dguadalajara.sedelectronica.es/signature-systems` page documents generic risk-dependent signature policy, including certificate-based and non-cryptographic levels. It does not publish the procedure-specific signer ABI, JavaScript client, signature format, algorithm, endpoint, callback, or final filing contract for Instancia General. Those fields remain `NO_VERIFICADO`.

## Implementation decision

The smallest truthful capability is a QA-only navigation profile for the exact current Instancia General launch plus the one observed Cl@ve gateway origin. The profile has no `SIGN`, `SELECT_CERTIFICATE`, or `CLIENT_TLS_AUTH` capability, no operation policy, no endpoint, and no client-auth policy. Release remains disabled and the public catalog state is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` pending separate physical validation.
