# Junta de Andalucía — PEG VEA deep public research — 2026-08-17

## Decision

`ES-PUB-0093` is implemented as a **new QA-only navigation profile**, `junta-andalucia-vea-peg`, for the exact current public start:

`https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA`

The implementation deliberately exposes no sensitive capabilities. It does **not** reuse the old `junta-andalucia` OVORION or `junta-ofvirtual` signing constants: current generic presentation has migrated to a distinct Ventanilla Electrónica (VEA) application and its signing algorithm/format/hash settings are supplied only after authenticated draft preparation.

## Current official route

- `https://www.juntadeandalucia.es/servicios/sede` redirects same-origin to `https://www.juntadeandalucia.es/servicios/sede.html`.
- Current Sede landing: HTTP 200, title `Servicios: Sede Electrónica General - Junta de Andalucía`, 144518 bytes, SHA-256 `fdfd685e11b72ce1639c5d8c82849a5019e18e5001ba719a51525f37ae6f1dfd`.
- Current first-party `Acceso a trámites`: `https://www.juntadeandalucia.es/servicios/sede/tramites.html`, HTTP 200, SHA-256 `cc539e720d1333a8584007e74027e1d8cf77f40bf794633a1553310ac306ed08`.
- It describes `Presentación electrónica general` as the generic route for writings/documents to the Junta and links `https://ws094.juntadeandalucia.es/V_virtual/SolicitarTicket?v=PEG`.
- The Sede landing also links `https://ws050.juntadeandalucia.es/vea/faces/vi/procedimientoDetalle.xhtml`.
- Current public redirects converge on VEA: the ws094 PEG route goes through `https://ws050.juntadeandalucia.es/vea/accesoDirecto?codProcedimiento=PEG_VEA` to the exact VEA page above; the ws050 legacy VEA route reaches the same current application.

## Public identification/signature policy

`https://www.juntadeandalucia.es/servicios/sede/sobre-sede/sistemas-identificacion.html` returned HTTP 200, SHA-256 `6a0a3fa211563611b8b1ccb2438deb0a11ba5220291cc3aef209ef40d4c4f1f8`. It states that common systems in this Sede include electronic certificate and Cl@ve. This is policy evidence only, not a signing ABI.

## BROWSER_PUBLIC_RUNTIME

A real unauthenticated Chromium session followed the Sede `Presentación electrónica general` link to the exact VEA page. The public page showed:

- `[PEG] Presentación electrónica general`;
- `INICIAR SOLICITUD` / `CONTINUAR BORRADOR`;
- steps `Iniciar la solicitud`, `Firmar el formulario y los documentos`, `Presentar el escrito`;
- `Se puede presentar con: Certificado Electrónico y Cl@ve`.

After dismissing the cookie notice, activating `INICIAR SOLICITUD` did not submit data: it opened the public authentication chooser with `Acceder con certificado electrónico`, `Acceder con cl@ve`, and a NIF/NIE option. An attempted certificate-button activation was blocked by the safety gateway before execution, so the certificate-auth boundary was not crossed.

No credentials were entered, no certificate was selected or supplied, and no private/draft state was created.

## Network / public configuration

The page publicly loaded the VEA application and unauthenticated configuration GETs from `api-veaja.cloud.juntadeandalucia.es`. Safe public metadata establishes:

- procedure `PEG_VEA`, Trewa code `PEG`;
- subsystem `VEA` / `Presentaciones electrónicas generales`;
- electronic presentation enabled;
- Cl@ve permitted;
- delivery definition does not permit unsigned delivery (`permiteSinFirma = N`), while a non-advanced-signature path may be available depending on authenticated role/state.

One public configuration response exposed a credential-like field. It was **not used**, is excluded from durable evidence, and is not reproduced here.

## Loaded first-party script graph

Current public assets:

- `assets/env.js`: SHA-256 `de64e6cb2a6882ea732d7278ddf486f8c548cd107293faff144834f5b6b475c3`
- `runtime.js`: `dc94d063402102bf1bbcc62c8c3f5d1048986c262b06e5f7c9735b3848d066e5`
- `polyfills.js`: `debda231ce7ad4d30aeae53f4900099f085510322b10d4bc9ce234d3308d3aa4`
- `scripts.js`: `8a8cb6cb4786d61c56188a759f357e89a077eb20124868142fa60ae14fbb21a9`
- `main.js`: `6ed33365863aa37bb7e25b9ae0f3e197e0064653b30737ee95b8e0965783970b`
- `assets/polyfills/autoScript.js`: `4f8a60742c9ccab7371270e82dedca5ca39758bac93d411358f6dfe049bab213`
- `assets/polyfills/batchScript.js`: `34462c0af209eef86176f79662136ecc63ba1feae8b45e5a4916575246c6c8af`

The current bundle dynamically loads AutoScript/batchScript and calls AutoFirma for certificate-based signing. It constructs an explicit-mode, pre-calculated-hash signing request and uses `AutoScript.multiModeSign`. However the decisive values are **not constants on the public PEG page**: `signAlgorithm`, `signFormat`, `hashAlgorithm`, certificate serial/filter details and signable hashes are supplied by authenticated draft/record preparation responses.

The public auth client routes certificate access through VEA auth with mode `afirma` and Cl@ve through mode `clave`, but the profile does not trust the auth API origin and does not implement either auth transition.

## Exact boundary proof

The first signing unknowns are the current PEG draft's exact `signAlgorithm`, `signFormat`, `hashAlgorithm`, qualified-certificate constraint and server-side sign-result binding. They become available only after authentication and creation/preparation of a draft/record. Observing them would require crossing the prohibited authentication/private-session boundary; completing the flow would additionally risk signing/submission.

Therefore:

- no `SIGN`, `SELECT_CERTIFICATE`, `AFIRMA_URI`, or `CLIENT_TLS_AUTH` capability is enabled;
- no endpoint/operation policy is added;
- no old SHA-1/SHA-256, XAdES/CAdES/PAdES, callback or certificate constant is copied from other Junta profiles;
- the QA profile trusts only the exact VEA public origin for navigation;
- `api-veaja.cloud.juntadeandalucia.es` remains outside profile browser/signing trust.

## Deep-public conclusion

`deep_public_research=PASS`, mode `BROWSER_PUBLIC_RUNTIME`.

The public navigation contract is sufficiently bounded to implement the exact VEA start fail-closed. The sensitive signing contract is intentionally left unimplemented at the authenticated-draft boundary. Truthful status is `IMPLEMENTED_NOT_E2E / E2E_PENDING`.
