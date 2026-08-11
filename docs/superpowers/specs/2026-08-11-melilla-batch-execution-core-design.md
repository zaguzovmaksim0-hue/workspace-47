# Melilla AutoFirma batch execution core — design

Date: 2026-08-11
Parent: `2026-08-09-melilla-autofirma-batch-contract-design.md`

## Scope

Implement only the protocol execution core behind the already-validated Melilla
batch bridge. This slice does **not** wire the adapter into `MainActivity` or
`BrowserScreen`, does not enable release behavior, and does not change the public
portal catalog. The normal single-sign `SigningCoordinator` and
`SigningProtocolAdapter` contracts remain untouched.

Intended production files:

- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningModels.kt`
- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningProtocolAdapter.kt`
- `app/src/main/java/dev/junta/firmamobile/signing/MelillaBatchProtocolAdapter.kt`

Intended tests:

- `app/src/test/java/dev/junta/firmamobile/signing/MelillaBatchProtocolAdapterTest.kt`

`MelillaBatchUrlPolicy.kt` may be used but is not widened to accept portal-supplied
query strings. `ProfileHttpTransport` is injected into this core; production
runtime transport composition is a later slice after response media-type evidence
is bounded.

## Evidence-backed contract

Official public source is pinned to `ctt-gob-es/clienteafirma` commit
`fe60ef3fdbae3c491e97c262a2179e2787b85776`.

- `BatchSigner.java` SHA-256
  `9ed1e0322a15b524b73df595b8c1a6355513e5b2f8bbcbfb032b6ec85b9df995`:
  JSON pre-sign is POST with `json` and `certs`; post-sign is POST with
  `json`, `certs`, and `tridata`. `json` and `tridata` are URL-safe Base64.
  Each returned `PRE` is locally signed as PKCS#1 using the requested algorithm;
  the client writes standard-Base64 `PK1` and removes `PRE` unless
  `NEED_PRE=true`.
- `UrlHttpManagerImpl.java` SHA-256
  `bb0de93da356ba70695c3849cf028c02fff6d139d992b5aaf4754f9d7caf9b48`:
  for POST the query portion supplied by `BatchSigner` is removed from the
  request URL and written as the UTF-8 POST body. Native execution therefore
  sends the exact validated path URL with a form body rather than broadening the
  URL allowlist to permit arbitrary query parameters.
- `JSONPreSignBatchParser.java` SHA-256
  `a468f132ec6b70e6a5375a7c722b70548d736d1efd5fe30ec8c438d0450c0883`,
  `TriphaseDataParser.java` SHA-256
  `2fde0eb6e4e7af5174cfc80376ac6708330ef053a717cdb3756e23e8a5544a51`,
  and `TriphaseDataSigner.java` SHA-256
  `9a132ee72207666b2103d269cdc632e6c5ab58b064fa6b1d70cb12720fda7060`
  define the bounded pre-sign/triphase transformation.
- `JSONBatchInfo.java` SHA-256
  `d8b943ef008ba9512bda5cf04e36f33ae9652cafe097ff9d9c7f0dc4081d21f9`
  and `JSONBatchInfoParser.java` SHA-256
  `14a031d49fcd378e75518a71ecd5eee705b804065c14c5421e7c6106fb733ff8`
  define partial pre-sign error propagation into the batch sent to post-sign.
- AutoScript `autoscript.js` SHA-256
  `649ace348a9e4478436ed0fab32922b401e31f3ef6461949882a9bd95c8be5ec`
  defines `createBatch`, `addDocumentToBatch`, and `signBatchProcess`.

The live Melilla base URLs remain exactly:

- `/sta/AutofirmaLote/presign/{operacionId}`
- `/sta/AutofirmaLote/postsign/{operacionId}`
- `/sta/AutofirmaLote/getdata/{operacionId}/{docId}`

## Model boundary

Add a dedicated batch protocol ID and normalized batch request. Reuse
`SigningContext`, `SigningAlgorithm`, `LocalSignature`, and certificate-chain
objects, but do not force PAdES into the single-sign `SigningFormat` enum.
A batch-specific format enum covers only `CAdES`, `PAdES`, and `XAdES`.

A prepared batch owns:

- the server-returned triphase state needed for post-sign;
- an ordered list of local PKCS#1 inputs;
- the possibly rewritten batch JSON after evidence-backed partial pre-sign errors.

All byte-array copies that can contain PRE/PK1/certificate or protocol payload
material are bounded and cleared on close. The final validation response remains
an opaque bounded JSON byte sequence until the later reply-delivery boundary.

## Protocol behavior

`prepare()` must:

1. verify the normalized request is Melilla, SHA256withRSA, `sign`, and uses the
   current operation binding;
2. revalidate exact pre/post/getdata base URLs with `MelillaBatchUrlPolicy`;
3. construct the exact AutoScript JSON batch from the already-validated bridge
   fields, adding only deterministic evidence-backed per-format extra parameters:
   - CAdES: empty extra params;
   - PAdES: `signatureSubFilter=ETSI.CAdES.detached`;
   - XAdES: `mode=implicit`;
4. URL-safe-base64 the JSON and each X.509 certificate DER; join the chain by
   `;` without a trailing separator;
5. POST an exact body containing only `json` and `certs` to the validated pre URL;
6. parse strict bounded JSON, reject duplicate/unknown structural ambiguity,
   unknown document IDs, malformed Base64, absent `PRE`, oversized values, or
   unsupported algorithms;
7. return ordered local-sign inputs without accessing a private key.

`complete()` must:

1. accept exactly one local signature per prepared PRE input, preserving order;
2. standard-base64 each PK1, replace only the matching `PRE` field, and keep
   `PRE` only when the server explicitly returned `NEED_PRE=true`;
3. URL-safe-base64 the bounded triphase JSON;
4. POST an exact body containing only `json`, `certs`, and `tridata` to the
   previously bound post URL;
5. return only a strict bounded JSON response; no form submission, callback
   execution, endpoint invention, or portal navigation occurs in this slice;
6. consume prepared state exactly once and close/clear all owned local signatures.

If pre-sign returns no triphase data but only evidence-backed per-document error
results, the adapter may return that bounded result without a post call, matching
official AutoFirma behavior.

## Failure boundary

Fail closed before the next sensitive/network step on:

- changed operation/document binding or wrong origin/path;
- duplicate or extra JSON keys at protected protocol objects;
- unknown/duplicate document IDs or triphase sign IDs;
- empty/oversized/malformed Base64 or UTF-8;
- wrong algorithm/format/suboperation;
- wrong number of local signatures;
- any retry/reuse of consumed prepared state;
- transport failure, redirect/session response, or uncertain post result.

No retry is introduced. No TLS/hostname/DNS/private-address control is weakened.

## Verification boundary

TDD starts with a dedicated adapter test on synthetic data and an injected fake
`ProfileHttpTransport`. The RED must prove the missing production interface/core,
not depend on a real Melilla transaction. Gradle RED/GREEN runs only in Codex
Cloud on exact pushed SHAs. Later runtime wiring and user-confirmation coordination
are separate milestones with separate tests.
