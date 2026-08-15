# ISCIII certificate-selection contract — 2026-08-15

## Scope

Public, unauthenticated, read-only evidence for `age-instituto-de-salud-carlos-iii`.
No POST, form submission, authentication, certificate use, signature, private-key operation,
TLS bypass, cookie replay, or navigation beyond the public pre-authentication pages was performed.

## First-party evidence

- `https://sede.isciii.gob.es/infoComprobar.jsp?accion=generico`
  - 9,431 bytes
  - SHA-256 `9c6dbab25445313c8ec5b7c9469bda443cfa29a8435c88151c3650cc6e527377`
- `https://sede.isciii.gob.es/cargaApplet.jsp?accion=generico&recurso.opcion=null`
  - 6,101 bytes
  - SHA-256 `df6fc0cf2b074a0fb54b3ef4df9a964321a2e3921ba328e5c98c9095681b822d`
- `https://sede.isciii.gob.es/js/autoscript/constantes.js`
  - 278 bytes
  - SHA-256 `ecd49d05b301bafb7ae77f71aac17c3de8e6db8e6d4e4035dde9a526dd59c074`
- `https://sede.isciii.gob.es/js/autoscript/autoscript.js`
  - 188,700 bytes
  - SHA-256 `f5551facf7a54ec83e1f8712076a9ec7b051c2ee76f4ee3b92a6f1b43590dd05`

## Exact observed contract

The public `cargaApplet.jsp` page loads `autoscript.js` and `constantes.js`, calls
`AutoScript.cargarAppAfirma()`, configures storage/retrieval servlets, and on page load executes:

- `params = "serverUrl=" + Constants.URL_BASE_SERVICES + "/afirma-server-triphase-signer/SignatureService"`
- `AutoScript.selectCertificate(params, enviarCertificadoCallback, mostrarErrorCallback)`
- the success callback receives exactly `certB64`, places it in the hidden `firma` field, and then
  submits the page form.

The current first-party `constantes.js` literal is
`http://dtomcat7.isciiides.es:8080`. Workspace-47 does **not** connect to that HTTP service.
The full `serverUrl` is retained only as an exact input guard for the observed JavaScript call.
The native implementation selects the already-unlocked local certificate and returns its DER
encoding as Base64 after explicit user confirmation.

## Implementation boundary

- profile: `isciii-certificate-selection`
- activation: `QA_ONLY`
- compatibility: `VERIFIED_CONTRACT`
- operation: `SELECT_CERTIFICATE`
- input adapter: `autoscript-select-certificate-v1`
- callback: `autoscript-select-certificate-callback-v1`
- no network endpoint is registered for the operation
- no signing algorithm or signature format is asserted
- release remains disabled until physical E2E verification is completed
