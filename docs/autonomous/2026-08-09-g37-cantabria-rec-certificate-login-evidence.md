# G37 candidate evidence — Cantabria REC certificate login

Date: 2026-08-09
Candidate: `cantabria-registro-electronico-comun` (`ES-PUB-0101`)
Scope: official public unauthenticated read-only GET/static-resource evidence only.

## Concrete public surface

A public Registro Electrónico Común login page under `https://rec.cantabria.es/rec/` was retrieved without authentication. The preserved public page is the certificate-login surface for forward target `EMP-S-002`; its UI says “Pulse \"Acceder\" y seleccione el certificado electrónico” and binds the Acceder button to `firmaCert()`.

The page loads first-party/public signing resources:

- `https://rec.cantabria.es/rec/js/autoFirma.js`;
- `https://clientefirma.cantabria.es/clientefirma/js/autofirma/afirmaClienteMiniapplet.js`;
- `https://clientefirma.cantabria.es/clientefirma/js/autofirma/miniapplet.js`.

No cookie, session identifier, personal value, certificate material or response body from an authenticated area is copied into this durable evidence document.

## Exact observed login-signing contract

The public page contains:

- hidden `docOriginal` with a server-provided 40-character lowercase hexadecimal challenge in the captured response;
- empty hidden `docFirmado`;
- hidden `tipoFirma=101`;
- `firmaCert()` which reads `docOriginal` and `tipoFirma`, uses all signing filters/policy/multiple/output flags as false, then calls `firmar(..., fOk, fError)`;
- `fOk(firmas, certificadosFirmantes)` which writes `firmas[0]` into `docFirmado` and then submits the HTML form.

The loaded `afirmaClienteMiniapplet.js` maps format code `101` exactly to:

- algorithm `SHA512withRSA`;
- format `CAdES`;
- mode `implicit`;
- extra parameters beginning `filters=` and then `mode=implicit`;
- ordinary `MiniApplet.sign(data, algoritmo, formato, parametros, successCallback, errorCallback)`.

The generic wrapper's success callback returns signature and certificate arrays to the page callback. For this single-item login path, the page consumes `firmas[0]` as the signed result. The application must not reproduce the page's subsequent form submission; that is outside the autonomous safety boundary.

## Product consequence

This evidence is sufficient for a QA-only, pre-auth signing contract for the Cantabria REC login surface. It does not prove successful authentication or any post-login procedure.

A safe implementation may support only the exact observed contract:

- source/initiator origin exactly `https://rec.cantabria.es`;
- server challenge supplied by the page at runtime; never hard-code the captured value;
- challenge lexical contract restricted to 40 lowercase hexadecimal characters because that is the directly observed public shape;
- algorithm exactly `SHA512withRSA`;
- format exactly `CAdES`;
- extra properties exactly the canonical `filters=` + newline + `mode=implicit` representation accepted by the portal wrapper;
- MiniApplet ordinary sign callback only;
- no form submission, login completion, authenticated navigation, cookies, or session replay.

The existing generic MiniApplet shim does not currently admit SHA512withRSA+CAdES, so this must be a profile-scoped compatibility path and must not broaden the generic CAdES algorithm allowlist.

## Lifecycle boundary

After a tested functional implementation, the profile may be `VERIFIED_CONTRACT` / `QA_ONLY`, inventory `IMPLEMENTED_NOT_E2E`, and public catalog `E2E_PENDING`. Never assign `VERIFIED_E2E` or release enablement without separate physical evidence.

## Preserved capture hashes

- public page: `a4a83b819366fd87e021ce2d950df3769b1108dbdb76d05b4d2243d8cca1c11b`;
- `autoFirma.js`: `74d36593100a74c262a35b760e972205ad1a4e7225a3cd97ff35942298f614c6`;
- `afirmaClienteMiniapplet.js`: `a1ae2ecbd9400ef404db2c8a0b4a843ca39fa8f9ec33576fec2dda8c4b735ab0`;
- `miniapplet.js`: `e5f17e93816d1875c57198917ed9fd1c6d6f9e71dd2d5c9fec3650d76544c713`.

No authenticated action, certificate selection, signature, form POST, upload, payment, APK launch or device control was performed.
