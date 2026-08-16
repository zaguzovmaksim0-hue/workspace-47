# Diputación Provincial de Burgos — bounded STA batch contract

Reviewed: 2026-08-16
Inventory ID: `ES-PUB-0146`
Portal/profile ID: `diputacion-burgos-portal`
Implementation state: `IMPLEMENTED_NOT_E2E` (`QA_ONLY`)

## Supported surface

Workspace-47 support is scoped to the exact HTTPS origin:

`https://registro.diputaciondeburgos.es`

The bounded start surface is the public `Instancia Genérica` entry:

`https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&DETALLE=6269000968832920507194&PAGE_CODE=CATALOGO`

The public procedure requires a recognized digital certificate and electronic signature, and exposes AutoFirma as an identification/signing path. The first-party runtime serves the same STA batch helper family already supported by Workspace-47.

## First-party runtime evidence

Normal TLS-verified unauthenticated GETs on 2026-08-16 returned:

- `autoscript.js`: SHA-256 `dd77491f6e514ca22d40a1737e6bb13a11f05469c38ddf12ac4a90a7e35f0af5`
- `sta-autofirma-lote.js`: SHA-256 `03f80b989f04d8f0a7fcbd1500831023f5d332eaed599cb48740c0af12a1706a`
- `webAppsFwk.js?ver=2605.0.3`: SHA-256 `0960256cac00d1aea5f5e496031b37de1207d77683e1ae4e109fa5803c3bf5aa`

`webAppsFwk.js` invokes `STAAutofirmaLote.firmarLote(...)` and routes successful results through `PRESENTAR_FIRMA`. The helper defines the observed default batch tuple `SHA256withRSA` / `CAdES` / `sign`, while allowing per-document PAdES/XAdES behavior. The Workspace-47 operation policy intentionally remains the bounded CAdES batch contract.

## Runtime URL grammar

Synthetic unauthenticated GETs only were used to revalidate the servlet grammar:

- `/sta/AutofirmaLote/presign/w47-burgos-registro-synthetic` → HTTP 400; recognized `presign`, required `json`.
- `/sta/AutofirmaLote/postsign/w47-burgos-registro-synthetic` → HTTP 400; recognized `postsign`, required `json`.
- `/sta/AutofirmaLote/getdata/w47-burgos-registro-synthetic/doc-synthetic` → HTTP 404; reached operation lookup and reported the synthetic operation absent/expired.
- query-style `/sta/AutofirmaLote?op=presign&operacionId=...` → HTTP 400 and explicitly required `/{op}/{operacionId}`.

No POST, authentication, session, certificate, upload, private key, real signature or administrative submission was used.

## Implementation boundary

Burgos reuses the existing shared STA batch parser/protocol core but has independent exact-profile wrappers for URL validation, WebMessage ownership, normalization and protocol identity. `burgos.es`, `sede.diputaciondeburgos.es`, sibling subdomains and non-default ports are not trusted signing origins by this profile.

## Limitations

This is not E2E verification. It does not prove authenticated transaction state, real certificate selection, successful real signature, server acceptance, filing, document upload, payment or any administrative effect. Release activation remains disabled until separately authorized E2E evidence exists.
