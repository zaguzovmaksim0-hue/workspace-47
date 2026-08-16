# Deputación de Lugo — public clientSigner contract revalidation (2026-08-16)

## Scope

Portal claim: `diputacion-lugo-sede` (`ES-PUB-0163`). This review used only public, first-party, unauthenticated GETs. No login was submitted, no certificate/private key was used, no callback was invoked, no signature was produced, and no administrative action was sent.

## Current public entry and active signer call

The public Sede authentication fragment is:

`https://sede.deputacionlugo.org/opencms/system/modules/gsede/elements/secciones/autenticacion/autenticacion.jsp`

Fresh GET on 2026-08-16 returned HTTP 200. The raw body is dynamic because it embeds current session/challenge values, so a raw page SHA-256 is not treated as a durable contract identifier.

The page loads the first-party signer helper:

`https://sede.deputacionlugo.org/opencms/common-js/clientSigner.js`

Fresh HTTP 200 SHA-256:

`6888095b7635489f1ab6dec42ae1d37a5ebb9df881aa8694774a726d0f457233`

The active public authentication call supplies one synthetic shape with:

- `mode=explicit`
- `format=CAdES`
- `algorithm=SHA256withRSA`
- a Base64 SHA-256 prehash (`hashToSign`, 32 decoded bytes)
- a dynamic transaction id
- callback `loginCert`

`clientSigner.js` converts that call into a one-item XML batch and calls `AutoScript.signBatch`. For SHA-256 explicit mode it fixes `precalculatedHashAlgorithm=SHA-256`.

## Exact same-origin batch endpoints

The live helper derives all batch services from the same Sede origin and, in its active multi-node mode, appends the current 32-hex JSESSIONID:

- `/opencms/clientsigner/BatchPresigner/service/{JSESSIONID}`
- `/opencms/clientsigner/BatchPostsigner/service/{JSESSIONID}`
- `/opencms/clientsigner/StorageService/service/{JSESSIONID}`
- `/opencms/clientsigner/RetrieveService/service/{JSESSIONID}`

The implemented QA contract accepts only HTTPS `sede.deputacionlugo.org`, the exact pre/post paths above, a 32-hex session id, and a pre/post pair with the same session id.

The one-item batch must contain:

- root `signbatch`, `stoponerror=true`, `algorithm=SHA256withRSA`
- one `singlesign`
- `datasource` equal to a Base64 SHA-256 prehash
- `format=CAdES`
- `suboperation=sign`
- Base64 extra params equal to `mode=explicit\nprecalculatedHashAlgorithm=SHA-256\n`
- signer saver class `es.guadaltel.framework.clientsigner.servlet.batch.util.SignSaverFile`

## PRE / local PKCS#1 / POST wire

The public Lugo helper is consistent with the official Cliente @firma batch client: batch XML and certificate chain are URL-safe Base64 query parameters on an HTTP POST to the pre-signer; the returned `PRE` bytes are locally signed as PKCS#1; the post-signer receives the original `xml`, `certs`, and URL-safe Base64 `tridata` carrying `PK1`.

Workspace-47 therefore uses an empty HTTP request body plus a bounded encoded query for this exact protocol. The generic transport remains fail-closed: an HTTP request may use a body or an encoded query, never both.

Primary implementation references used for the wire semantics:

- `https://github.com/ctt-gob-es/clienteafirma/blob/master/afirma-crypto-batch-client/src/main/java/es/gob/afirma/signers/batch/client/BatchSigner.java`
- `https://github.com/ctt-gob-es/clienteafirma/blob/master/afirma-core/src/main/java/es/gob/afirma/core/signers/TriphaseDataSigner.java`

`BatchSigner.signXML` sends `xml`/`certs` and later `xml`/`certs`/`tridata` in the URL query while using HTTP POST. `TriphaseDataSigner` adds `PK1` and removes `PRE` unless `NEED_PRE` is true.

## Final result contract

The live Lugo callback Base64-decodes the post-signer XML and accepts only `signresult` values:

- `DONE_AND_SAVED`
- `DONE_BUT_NOT_SAVED_YET`

The QA protocol implementation therefore requires exactly one `signresult`, the same transaction id, exactly the observed `id`/`result` attributes, and one of those two success values before returning the Base64 XML to the page callback. Malformed XML, DOCTYPE, duplicate/foreign results, extra attributes, and failure states are rejected.

## Additional first-party capability evidence

Live public FAQ:

`https://sede.deputacionlugo.org/opencms/system/modules/sede/contents/faq/acceso_sede`

Fresh unauthenticated GET returned HTTP 200. The FAQ is server-rendered and is not assigned a durable body hash here.

It states that certificate access to the Sede requires a valid certificate and AutoFirma.

Live public AutoFirma FAQ:

`https://sede.deputacionlugo.org/opencms/system/modules/sede/contents/faq/instalar_autofirma`

Fresh unauthenticated GET returned HTTP 200. This server-rendered FAQ is likewise not assigned a durable body hash.

The previously drafted `.../faq/requisitos_tecnicos?lang=es` URL returned HTTP 404 on revalidation and must not be used as evidence.

## Product boundary

This is `IMPLEMENTED_NOT_E2E`, QA-only. Public evidence establishes the active pre-authentication clientSigner contract, but Workspace-47 did not use a real certificate, perform the authentication callback, retrieve a real stored signature, or continue into a citizen procedure. No `VERIFIED_E2E` claim is made.
