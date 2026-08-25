# ES-PUB-0152 — Deputación da Coruña current bounded evidence — 2026-08-21

Scope: current official portal delegation, exact `Solicitude Xeral` X004 launch, Cl@ve certificate branch and client-TLS boundary only. No client certificate was supplied, no authenticated session was established, no private-key operation or document signature was executed, and no filing, registration, submission or payment action was performed. Opaque Cl@ve/session values were not retained.

## Official delegation and exact procedure

- `https://www.dacoruna.gal/portada` returned HTTP 200 and directly links the institution's `Sede electrónica` at `https://sede.dacoruna.gal`.
- The current Sede catalog at `https://sede.dacoruna.gal/sxc/gl/procedimientosytramites/` exposes `Solicitude Xeral` at `https://sede.dacoruna.gal/sxc/gl/procedimientosytramites/tramites/SolicitudGeneral_N`.
- That exact procedure exposes `Iniciar trámite` at `https://sede.dacoruna.gal/tramitador/entrada?idLogica=accesoDirecto&entrada=ciudadano&idEntidad=diputacion&idExpediente=X004&fkIdioma=GL`. The public procedure page observed in this pass had SHA-256 `47b0fffbc2c1ce8d65183c4cf077bec979531702813a3b842fb2c0b622041437`.
- The X004 access page posts to `/SP2/TiWorksRequest` with the exact procedure identity and states that electronic procedures use Cl@ve. It loads generic `implementacionIFirma_GL.js`, but no X004-specific sign invocation, algorithm, format, callback or signing endpoint was observed; generic library presence is not treated as a signing contract.

## Real Chromium Cl@ve observation

A real headless Chromium 149 session loaded the exact X004 launch and clicked only the public `Entrar con Cl@ve` control. Top-level navigation was:

`X004` → POST `https://sede.dacoruna.gal/SP2/TiWorksRequest` → POST `https://pasarela.clave.gob.es/Proxy2/ServiceProvider`.

The live Cl@ve selector offered `eIdentifier` / any qualified electronic certificate. Selecting only that public method invoked `selectedIdP('AFIRMA')` and continued:

POST `https://pasarela.clave.gob.es/Proxy2/ServiceRedirect` → POST `https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`.

No certificate was configured. The flow returned to the Sede with the visible error that no certificate had been selected. No authenticated continuation occurred. Transient cookies and opaque Cl@ve state were not retained.

## TLS client-certificate boundary

A direct TLS 1.2 handshake to `pasarela-ident.clave.gob.es:443` emitted a server client `CertificateRequest`:

- no client-certificate CA names were sent;
- certificate types included RSA sign and ECDSA sign (the repository capability is therefore bounded to RSA/EC);
- the request occurred at exactly `/IdP2/AuthenticateCitizen` in the observed browser flow.

This supports `allowEmptyIssuerList=true`, port 443, digital-signature key usage, and the exact direct transition from `Proxy2/ServiceRedirect` to the client-auth origin/path.

## Boundary / decision

Implement a QA-only `diputacion-a-coruna-solicitud-general` profile with only `CLIENT_TLS_AUTH`, launched from the exact X004 URL while keeping the public catalog entry at the institutional portal URL. Trust only the Sede initiator, the reviewed Cl@ve redirect origin, and the exact client-auth request origin/path. Do not infer `SIGN`, a signing algorithm/format/callback, or final presentation/registration behavior from the generic IFirma script.
