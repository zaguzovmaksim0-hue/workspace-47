# Diputación Provincial de Huesca OVC — bounded STA batch contract

Reviewed: 2026-08-16
Portal inventory ID: `ES-PUB-0159`
Portal ID / profile ID: `diputacion-huesca-portal`
Implementation state: `IMPLEMENTED_NOT_E2E` (`QA_ONLY`)

## Supported public surface

The implementation is scoped to the exact HTTPS origin `https://ovc24.dphuesca.es` and starts at:

`https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_HOME`

The public OVC documents AutoFirma integration and exposes Portafirmas. Its public JavaScript uses the STA batch helper `STAAutofirmaLote.firmarLote(...)`, with the observed batch tuple `SHA256withRSA` / `CAdES` / `sign` and a `PRESENTAR_FIRMA` result path. The helper also contains its documented per-document PAdES/XAdES handling, but the profile operation remains the already-supported bounded STA CAdES batch contract.

## First-party evidence

Public pages:

- `https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_HOME`
- `https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_FAQS2`
- `https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_REQUISITOS`

Static JavaScript captured through normal TLS-verified unauthenticated GET:

- `autoscript.js`: SHA-256 `dd77491f6e514ca22d40a1737e6bb13a11f05469c38ddf12ac4a90a7e35f0af5`
- `sta-autofirma-lote.js`: SHA-256 `03f80b989f04d8f0a7fcbd1500831023f5d332eaed599cb48740c0af12a1706a`
- `webAppsFwk.js?ver=2605.0.2`: SHA-256 `0960256cac00d1aea5f5e496031b37de1207d77683e1ae4e109fa5803c3bf5aa`

## Exact runtime URL grammar

The existing shared STA runtime policy was revalidated against the Huesca-owned servlet with synthetic, unauthenticated GET requests only:

- `/sta/AutofirmaLote/presign/w47-g51-synthetic` → HTTP 400, server recognized `op=presign` and required `json`.
- `/sta/AutofirmaLote/postsign/w47-g51-synthetic` → HTTP 400, server recognized `op=postsign` and required `json`.
- `/sta/AutofirmaLote/getdata/w47-g51-synthetic/doc-synthetic` → HTTP 404, server reached operation lookup and reported the synthetic operation absent/expired.
- query-style `/sta/AutofirmaLote?op=presign&operacionId=w47-g51-synthetic` → HTTP 400 and explicitly required the path form `/{op}/{operacionId}`.

No POST, upload, authenticated session, certificate, private key, signature or administrative submission was used.

## Implementation boundary

Workspace-47 reuses the existing STA batch parser/protocol core, but Huesca has independent exact-profile wrappers for:

- origin and runtime URL validation;
- WebMessage request ownership;
- signing normalization;
- protocol registry identity;
- QA-only profile/catalog binding.

The document-start shim receives the exact STA origin selected by the native profile runtime instead of trusting a wildcard or a JavaScript-side host list. Release activation remains disabled.

## Limitations

This milestone does **not** establish portal E2E success. It does not prove authentication, certificate selection in the real Huesca transaction, a real signature, server acceptance, filing, upload, payment or any other administrative effect. Promotion to `VERIFIED_E2E` requires separately authorized real-flow evidence.
