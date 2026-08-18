# MITES certificate-login evidence — 2026-08-17

## Public unauthenticated evidence

- Official entry: `https://sede.mites.gob.es/`.
- Public procedure page: `https://sede.mites.gob.es/inicio/detalleProcedimiento/38`.
- Its public API response at `https://sede.mites.gob.es/nuevasede-ciudadano/api/public/procedimientos/38` identifies active procedure **Quejas y Sugerencias** and states that the telematic form requires identification and digital signature according to the Electronic Registry access requirements.
- The public route table in `https://sede.mites.gob.es/main.js` exposes `path:"auth"` and loads `auth.component-3JUEHJQO.js`.
- Public `https://sede.mites.gob.es/auth.component-3JUEHJQO.js` implements certificate login with AutoScript. It generates exactly 10 random lowercase ASCII letters, calls `AutoScript.cargarAppAfirma(...)`, then calls `AutoScript.sign` with `SHA512withRSA`, `CAdES`, and the exact properties `mode=implicit` plus `filters.1=signingCert:;keyusage.nonrepudiation:true;nonexpired:`.
- Public `https://sede.mites.gob.es/chunk-MX4YJU4O.js` sets the AutoFirma script URL to `https://expinterweb.mites.gob.es/scriptautofirma/autoscript.js` and identifies the production environment. This dependency is evidence only; it is not promoted to a trusted navigation/signing origin.

## Bounded implementation

`ES-PUB-0074` gets a new QA-only profile `mites-certificate-login` whose public start URL remains the official MITES Sede root. Native bridge acceptance is narrower: it requires exact page `https://sede.mites.gob.es/auth`, exact origin `https://sede.mites.gob.es`, a 10-byte lowercase ASCII challenge, `SHA512withRSA`, `CAdES`, and the exact observed property string. The protocol adapter signs only that bounded challenge contract and rejects other payloads/tuples.

The profile does **not** trust `expinterweb.mites.gob.es` as an initiator/browse/signing origin and has no network signing endpoint. The static private bundle also contains later PAdES submission logic; that flow is explicitly outside this implementation and is neither opened nor exercised.

Status remains `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. Research used public GET/static resources only: no credentials, certificate selection, real signature, form submission, POST, payment, or private administrative action.
