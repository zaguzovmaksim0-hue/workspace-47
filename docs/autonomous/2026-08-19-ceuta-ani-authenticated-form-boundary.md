# Ciudad Autónoma de Ceuta ANI — bounded authenticated form evidence

Reviewed: 2026-08-19

## Promoted boundary

The QA-only `ceuta-sede` profile is limited to the exact ANI launch:

- public procedure: `https://sede.ceuta.es/controlador/controlador?cmd=tramite&modulo=tramites&tramite=ANI`
- QA launch: `https://sede.ceuta.es/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI`
- authenticated return: `https://sede.ceuta.es/controlador/controlador`

Reused controlled Cl@ve/certificate authentication and a fresh 2026-08-19 runtime reached `#frmAlta` with a first-party POST action, hidden `cmd=entrada-prepara-add`, `modulo=carpeta`, and the `Registrar` control. No AutoScript, MiniApplet, AutoFirma, PAdES/XAdES/CAdES, signer callback, signature algorithm, or signing-session marker was exposed at that boundary.

A v2.4-bounded direct submission of only that preflight-verified intermediate form returned HTTP 500. The resulting page had no forms or signer/pre-sign markers. No private-key document signature, final registration/filing/submission, or payment was performed.

## Product boundary

The candidate therefore exposes only an exact QA trusted-browse launch and the observed authenticated-form contract. `SIGN`, signer ABI, signature format/algorithm, signing endpoint, callbacks, client TLS authentication, final filing, and payment remain unimplemented/unverified. Existing conservative certificate-selection rules are preserved rather than broadened without new evidence.
