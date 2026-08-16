# Consell Insular de Formentera OVAC / Sede electrónica evidence — 2026-08-16

## Scope and safety boundary

Public, unauthenticated, read-only investigation for `formentera-sede-electronica` (`ES-PUB-0124`).
Exact seed URL: `https://ovac.conselldeformentera.cat/`.
All network probes were conducted using bounded HTTPS GET requests (`curl --connect-timeout 5 --max-time 15`).
No POST request, form submission, authentication flow, digital certificate selection, private key
operation, cookie replay, APK launch, or device control workflow was performed.

## First-party evidence

- Root seed URL: `https://ovac.conselldeformentera.cat/`
  - HTTP 200 (Microsoft-IIS/10.0, ASP.NET)
  - Content: XHTML document with meta-refresh:
    `<meta http-equiv="refresh" content="0; url=https://ovac.conselldeformentera.cat/ovac/catala/emiservicio/41E6BF9D755E4825AF8E6B49E85B5079.asp" />`
- Resolved pre-auth portal URL: `https://ovac.conselldeformentera.cat/ovac/catala/emiservicio/41E6BF9D755E4825AF8E6B49E85B5079.asp`
  - HTTP 200 (Microsoft-IIS/10.0, ASP.NET)
  - Exposes standard ABSIS/EAD OVAC public procedures and citizen services catalog.
  - Unauthenticated static scripts (e.g. `absEdiForm.js`) implement legacy generic form helpers (`hayElementoFirma`, `FormLoginCertificatDigital`), but do not publish an exact AutoFirma / AutoScript / MiniApplet integration contract, signature algorithm (`SHA*withRSA`), signature format (`CAdES`, `PAdES`, `XAdES`), local transport endpoint, or native message callback ABI.
  - The public informational section (`Certificats digitals i signatures electròniques admeses`) lists admitted certificate issuers but publishes no client-side bridge or cryptographic signing parameters.

## Exact observed contract & fail-closed classification

Because no verified privileged signing, certificate selection, or client TLS authentication ABI is proven on the public surface:
- Classification: `BROWSE_ONLY` (fail-closed)
- Capabilities: `[]` (no `SIGN`, no `SELECT_CERTIFICATE`, no `CLIENT_TLS_AUTH`, no `AFIRMA_URI`)
- Endpoints: `[]`
- Operation policies: `[]`
- Initiator origins: `["https://ovac.conselldeformentera.cat"]`
- Redirect origins: `[]`
- Trusted browse origins: `[]`
- Any redirect or login target remains untrusted until separate verified evidence is produced.

## Implementation boundary

- Profile ID: `formentera-sede-electronica`
- Profile version: `1`
- Display name: `Sede electrónica / OVAC del Consell Insular de Formentera`
- Start URL: `https://ovac.conselldeformentera.cat/`
- Activation: `ENABLED` (as safe browse-only profile)
- Compatibility status: `BROWSE_ONLY`
- Catalog status: `CATALOGED`
- Inventory status: `BROWSE_ONLY`
- Reviewed on: `2026-08-16`
