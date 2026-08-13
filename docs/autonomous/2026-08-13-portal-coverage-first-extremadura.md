# Portal coverage first — ACCEDA gate and Extremadura binding — 2026-08-13

## Safety boundary

This milestone used only unauthenticated, read-only public HTTPS GETs and local repository tests.
No certificate was selected, no authenticated session or credential was used, and no form, signature,
upload, payment, or administrative submission was performed.

## ACCEDA remains blocked for implementation

The mandatory first candidate, `age-acceda` (`ES-PUB-0003`), was refreshed before selecting another
portal. The public validation page and first-party helper still expose two pieces that are not safely
bound into one exact procedure contract:

- `https://sede.administracionespublicas.gob.es/certificado/valida` — HTTP 200, 80,274 bytes,
  SHA-256 `44b46483ff1f2b8e55a33c88d3a415c41915f16558e148885b99efb50ec52cb1`;
- `https://sede.administracionespublicas.gob.es/js/afirma/afirma_funciones.js` — HTTP 200, 4,061 bytes,
  SHA-256 `b522f95b00836f420cb9c52b0075b3f1db4856bf93c19cef2af3cdc563e7c6a5`.

The page still invokes the `afirma.firmar(...)` wrapper while the helper separately contains
`doSignSolicitud(...)` / `AutoScript.sign(...)`. Public evidence does not prove that the wrapper calls
that exact helper branch for the catalogued ACCEDA surface, nor does it expose the exact procedure
input/callback binding needed for a fail-closed implementation. `age-acceda` therefore remains
`VERIFIED_CONTRACT` and unimplemented; no authentication was attempted to obtain stronger evidence.

## Extremadura exact public contract refresh

`extremadura-tramites` (`ES-PUB-0109`) was selected next because its protocol, bridge, URL-policy and
signing-adapter seams are already present on current `main`, while the profile/catalog binding was the
remaining bounded integration step. The first-party resources were refreshed on 2026-08-13:

- `/sta/resources/js/autoscript.js` — HTTP 200, 222,756 bytes, SHA-256
  `dd77491f6e514ca22d40a1737e6bb13a11f05469c38ddf12ac4a90a7e35f0af5`;
- `/sta/resources/js/sta-autofirma-lote.js` — HTTP 200, 12,522 bytes, SHA-256
  `03f80b989f04d8f0a7fcbd1500831023f5d332eaed599cb48740c0af12a1706a`;
- `/sta/pages/webapps/js/webAppsFwk.js?ver=2605.0.2` — HTTP 200, 86,696 bytes, SHA-256
  `0960256cac00d1aea5f5e496031b37de1207d77683e1ae4e109fa5803c3bf5aa`.

These hashes are unchanged from the previously recorded 2026-08-11 refresh. The public helper still
binds `firmarLote` to `createBatch` / `addDocumentToBatch` / `signBatchProcess`, with the observed
default tuple `SHA256withRSA`, `CAdES`, suboperation `sign`, `stopOnError=false`. The framework still
returns the opaque result through `PRESENTAR_FIRMA` as `validationResponse`.

The mobile profile introduced by this milestone remains narrower than the public helper: QA-only,
exact origin `https://tramites.juntaex.es`, CAdES detached, SHA-256 with RSA, and the already existing
Extremadura-specific batch adapter/callback IDs. Backend-supplied presign/postsign/getdata URLs remain
ephemeral and are not promoted to static endpoints. No E2E or release-support claim is made.
