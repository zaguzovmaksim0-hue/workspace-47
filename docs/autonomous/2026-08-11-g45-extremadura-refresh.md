# Generation 45 — Extremadura STA public-contract refresh — 2026-08-11

## Scope and safety boundary

This refresh used only unauthenticated HTTPS GET requests to three already-known first-party static
resources under `https://tramites.juntaex.es`. No form POST, certificate selection, authentication,
cookie jar, credential, real signature, upload, payment, or administrative submission was used.
Static response bodies are retained only under ignored `build/autonomous-evidence/` for bounded local
comparison; no runtime challenge, personal data, session value, or credential-like material was
recorded.

## Current first-party resources

All three resources returned HTTP 200 and are byte-for-byte unchanged from the generation-42 evidence:

- `/sta/resources/js/autoscript.js`: 222756 bytes, SHA-256
  `dd77491f6e514ca22d40a1737e6bb13a11f05469c38ddf12ac4a90a7e35f0af5`;
- `/sta/resources/js/sta-autofirma-lote.js`: 12522 bytes, SHA-256
  `03f80b989f04d8f0a7fcbd1500831023f5d332eaed599cb48740c0af12a1706a`;
- `/sta/pages/webapps/js/webAppsFwk.js?ver=2605.0.2`: 86696 bytes, SHA-256
  `0960256cac00d1aea5f5e496031b37de1207d77683e1ae4e109fa5803c3bf5aa`.

The batch helper still exposes `STAAutofirmaLote.firmarLote(params, onSuccess, onError)` and builds the
operation with `AutoScript.createBatch`, `addDocumentToBatch`, and `signBatchProcess`. The current
default tuple remains `SHA256withRSA`, `CAdES`, suboperation `sign`, and `stopOnError=false`; PAdES
adds `signatureSubFilter=ETSI.CAdES.detached`, XAdES adds `mode=implicit`, and CAdES adds no
format-specific extra parameter. Runtime `batchPreSignerUrl`, `batchPostSignerUrl`, and per-document
`datareference` remain backend-supplied values rather than constants.

The current `webAppsFwk.js` still binds the helper through `startFirma(signInfo)`, invokes
`STAAutofirmaLote.firmarLote(signInfo, ...)`, returns the opaque result through `PRESENTAR_FIRMA` with
`validationResponse=JSON.stringify(resultado)`, and calls `AutoScript.cargarAppAfirma()` through its
safe loader when the signing UI is opened.

## Product implication

`extremadura-tramites` (`ES-PUB-0109`) remains implementation-ready in the research queue only. This
refresh does not change its product profile, inventory status, generated catalog status, release
enablement, or E2E claim. Implementation still depends on a stable verified shared STA batch seam;
the preserved Melilla slice has not yet obtained retrievable terminal Cloud Gradle evidence, so no
Extremadura production mutation is justified yet.
