# Ministerio de Transportes — Quejas y Sugerencias XAdES evidence (2026-08-17)

## Current public surface

The historical AGE-directory entry `https://sede.mitma.gob.es/sede_electronica/lang_castellano/` now redirects to the current official Sede at `https://sede.transportes.gob.es/`. The current first-party procedure page is:

`https://sede.transportes.gob.es/proc-servicios-comunes/presentacion-quejas-sugerencias-ambito-ministerio-transportes-movilidad-sostenible`

Its public **Iniciar trámite** action is the exact same-origin launch `https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002`. A public session GET redirects that launch to `https://sede.transportes.gob.es/MFOM.genericprocedure.web/Autenticacion.aspx`; no authentication or POST is required to inspect the page.

The procedure page states that the interested party must sign the presentation electronically. The public authentication page states that a valid digital certificate is required and that only the authenticated person can sign/finalize the procedure. It loads the first-party `/CIM/js/CIM.js` integration.

## Exact public login-signature contract

The authentication page's `Sign_firmar()` creates `SignParams`, reads the public challenge field, sets `signatureFormat = 'XAdES'`, `filter = FILTER_AUTHENTICATION`, `idToSign = 'tag1'`, and passes callbacks to `doSign`.

Current first-party CIM resources were retrieved by unauthenticated GET only:

- `CIM_Functions.js` SHA-256 `9e2ba0585332d3fa1ca295ad7cc3071b0c5b5b75a6360467fd03ab419e7715ea`
- `CIM_Classes.js` SHA-256 `c6dafe6ece1b5501bfce090368a772b6602b9439904149838ec8ce5deadb9bf0`
- `CIM_Constants.js` SHA-256 `a6ec5bc3352f7265a2e4ad490ce3a5d8f7f3c9c8f61f2c26376324eb9c58fbcb`
- `autoscript.js` SHA-256 `dd77491f6e514ca22d40a1737e6bb13a11f05469c38ddf12ac4a90a7e35f0af5`

The defaults and helper functions resolve this call to `MiniApplet.sign` with:

- `SHA1withRSA` (portal-specific legacy contract);
- `XAdES` with `format=XAdES Enveloped`;
- `includeOnlySigningCertificate=true`;
- `nodeToSign=tag1`;
- `applySystemDate=false`;
- `filters.1=keyusage.digitalsignature:true;nonexpired:`;
- `sticky=true`.

Three independent public GET sessions were used to validate **shape only**. After Base64 decoding, each challenge was exactly 113 bytes and matched this redacted structure:

`<?xml version="1.0" encoding="UTF-8"?><tag1 Id="tag1"><tag1_timestamp>REDACTED_TIMESTAMP</tag1_timestamp></tag1>`

No transient timestamp/challenge value was persisted in the repository.

## Bounded implementation

`ES-PUB-0075` binds QA-only profile `transportes-qys-cert-login` by the exact first-party **Iniciar trámite** URL used as `entry_url`; the human-readable official procedure remains recorded separately as `procedure_page`. Trust is limited to exact origin `https://sede.transportes.gob.es`; the historical `sede.mitma.gob.es`, CDN, and `fire.transportes.gob.es` are not promoted to trusted signing origins.

The native adapter and bridge accept only the exact public authentication page, challenge grammar, SHA1withRSA/XAdES tuple and observed properties. This implementation covers **only the initial certificate-login XAdES signature**. It does not implement or claim the subsequent administrative submission, and it does not call the Storage/Retrieve services declared by the generic CIM library.

Status is strictly `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. Research performed GET/HEAD/static-resource inspection only: no certificate selection, real signature, login completion, form POST, submit, payment, or other private action.
