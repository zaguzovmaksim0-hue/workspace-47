# ES-PUB-0043 — Dirección General del Catastro — generic submissions

Reviewed: 2026-08-24

## Current official public evidence

- `https://www.sedecatastro.gob.es/` loads in Chromium and publishes `Declaraciones, recursos, solicitudes...` at `https://www.sedecatastro.gob.es/Accesos/SECAccTramites.aspx`.
- The current public list includes **Presentar otras solicitudes, escritos por discrepancias con la descripción catastral y documentos genéricos** at `https://www.sedecatastro.gob.es/Accesos/SECAccProcedimientos.aspx?Dest=22`.
- The page offers **Ir al formulario**. A fresh Chromium session followed that public control: the ASP.NET postback stayed first-party and reached `https://www.sedecatastro.gob.es/Accesos/SECAccDNI.aspx?Dest=22`.
- The authentication chooser offers **Certificado digital: CERES, DNIe, otros** and **Cl@ve: PIN24, Cl@ve permanente,...**. The stable Cl@ve route is `https://www.sedecatastro.gob.es/Accesos/SECAccPIN.aspx?Dest=22&texp=REGI`.
- That Cl@ve route exposes the public Cl@ve flow and a POST boundary to `https://pasarela.clave.gob.es/Proxy2/ResponseRedirect`. Only method/origin/path and field names were inspected; no ASP.NET state values, SAML values, cookies or credentials were retained.

## Boundary and implementation consequence

The pass stops before authentication. No credential was submitted, no certificate/private key was used, and no signature, upload, registration, final filing or payment occurred. The presence of a certificate option is not promoted into `CLIENT_TLS_AUTH`; no procedure-specific signing contract was observed.

The implementation is therefore `QA_ONLY`: exact `Dest=22` as start URL, Catastro Sede as initiator, and only `https://pasarela.clave.gob.es` as the observed cross-origin navigation boundary. `endpoints`, `operationPolicies`, `capabilities` and `clientAuthPolicy` remain empty/null. Inventory becomes `IMPLEMENTED_NOT_E2E`; generated catalog remains `E2E_PENDING`.
