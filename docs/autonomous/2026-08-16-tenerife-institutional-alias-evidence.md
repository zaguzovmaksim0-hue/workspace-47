# Tenerife institutional portal → Sede alias evidence — 2026-08-16

## Scope

Public, unauthenticated, read-only revalidation of `tenerife-portal-institucional` (`ES-PUB-0127`).
No login, cookie replay, certificate use, signature, form submission, upload, payment, or administrative action was performed.

## First-party delegation

The Cabildo de Tenerife institutional portal states that its electronic Sede is located at
`https://sede.tenerife.es/` and that services are accessed from that address. The same institutional
site's current service information also exposes the Sede at that exact HTTPS origin.

Relevant first-party pages:

- `https://www.tenerife.es/w/que-normativa-establece-la-creacion-y-regulacion-de-la-sede-electronica-del-cabildo-de-tenerife?redirect=%2Fsede-electronica`
- `https://www.tenerife.es/carta-de-servicios`

## Implementation boundary

- catalog surface remains `tenerife-portal-institucional` with entry URL `https://www.tenerife.es/`;
- exact `launch_url` is `https://sede.tenerife.es/`;
- launch binds only to the existing `tenerife-sede-electronica` QA-only profile because the URL is
  byte-for-byte the profile start URL;
- no signing, certificate, callback, endpoint, or TLS-client capability is granted to `www.tenerife.es`;
- release remains disabled and E2E remains pending;
- any launch URL mismatch fails closed in `PortalCatalogRepository`.

This is a delegation alias, not evidence that the institutional origin itself implements AutoFirma.
