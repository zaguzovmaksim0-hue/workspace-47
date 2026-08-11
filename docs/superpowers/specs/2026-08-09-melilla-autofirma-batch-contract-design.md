# Melilla AutoFirma batch contract design

## Status and evidence boundary

This is the RED-phase design for the isolated `melilla-sede` candidate. The
only contract source is
`build/autonomous-evidence/g36-melilla-batch/EVIDENCE_PACKET.md`. That packet
contains official public unauthenticated GET/static-resource observations
only. It does not prove an authenticated flow, a real signature, a submitted
procedure, or an end-to-end callback.

The eventual lifecycle is deliberately bounded to:

- profile: `VERIFIED_CONTRACT` / `QA_ONLY`;
- inventory: `IMPLEMENTED_NOT_E2E`;
- public catalog: `E2E_PENDING`.

The profile must never become `VERIFIED_E2E` or release-enabled from this
evidence. The RED phase does not add the profile, inventory record, or catalog
entry.

## Seam decision

Melilla's portal-owned `signInfo` JSON is a batch contract, not a variant of
the existing `MiniApplet.sign` request. There are two seams in the native
composition, and they must not be conflated:

1. `MiniAppletBridgeAdapter.route` is the public ordinary single-sign adapter.
   Its `MiniAppletBridgeRequest`/`NormalizedSignRequest` path is intentionally
   single-sign only. For `MINIAPPLET_BATCH` it must return exactly
   `MiniAppletBridgeRouteResult.NotApplicable`; making it return a dedicated
   result would couple the ordinary adapter to the batch protocol.
2. `WebMessageBridge.receive` is the composition boundary. It currently runs
   portal diagnostics, then the ordinary `miniAppletAdapter`, then the generic
   `WebMessageRouter`. The eventual `MelillaBatchBridgeAdapter` must be
   dispatched at this boundary before the ordinary adapter and must own a
   dedicated `MelillaBatchBridgeRouteResult` / `MelillaBatchRequest` contract.

The current bridge has no batch-adapter injection or batch reply channel, and
`receive` is private. A unit test cannot observe dedicated acceptance there
without first inventing a production API or an unproven reply shape. If a raw
`MINIAPPLET_BATCH` envelope is manually sent to the current bridge, the
ordinary adapter correctly returns `NotApplicable` and the generic router
rejects the unsupported type. The closest existing public behavior seam is
therefore the document-start `AfirmaJavascriptShim.load` contract: its RED
tracer requires recognition of `AutoScript.signBatchProcess` and emission of
the `MINIAPPLET_BATCH` discriminator, without asserting an invented result
schema. A later bridge-level behavioral test belongs at `WebMessageBridge`
once the dedicated production route exists.

The future batch input adapter id is `melilla-batch-autoscript-v1`; it is an
internal binding identifier, not a claim about a portal ABI. The future profile
may use the existing `SIGN` operation with that input adapter so the existing
single-sign route rejects the profile's batch message by input-adapter
mismatch. A separate batch protocol-adapter registry/execution path is still
required; extending ordinary `SigningCoordinator.prepare` to accept batch
payloads is out of scope.

## Evidence-backed batch data

The future public batch request model may expose only the fields observed in
the packet:

- `batchPreSignerUrl` and `batchPostSignerUrl`;
- a non-empty `documentos` array;
- each document's required `id` and `datareference`, with optional `format`
  and `suboperation` overrides;
- default algorithm `SHA256withRSA`, default format `CAdES`, default
  suboperation `sign`, and default `stopOnError=false`.

The native JSON construction must preserve the observed AutoScript semantics:
`createBatch`, `addDocumentToBatch`, and `signBatchProcess`. The native
transport may carry the observed `jsonbatch=true`, `needcert=true`,
`batchpresignerurl`, `batchpostsignerurl`, and encoded `dat` values, but no
new portal endpoint or native protocol claim may be inferred from those names.

Supported document formats are only the evidence-backed `CAdES`, `PAdES`, and
`XAdES`. The exact observed extra parameters are
`signatureSubFilter=ETSI.CAdES.detached` for PAdES, `mode=implicit` for XAdES,
and none for CAdES. RSA/SHA256 is the only accepted batch algorithm. The RED
fixture uses only the defaults and does not assert an unobserved response
schema or suboperation.

The portal result remains an opaque, bounded JSON value because the packet
only proves that `resultado` is serialized into the portal's
`PRESENTAR_FIRMA` event as `validationResponse=JSON.stringify(resultado)`.
The implementation must not invent result fields or an administrative
submission endpoint.

## Exact URL policy for runtime-issued batch URLs

The two batch URLs and every per-document `datareference` are accepted only
after the same policy. Runtime/server-issued identifiers are opaque values;
the client never guesses, hard-codes, or rewrites them.

1. The effective origin is exactly `https://sede.melilla.es` with HTTPS and
   port 443. Reject every other host, scheme, port, user-info, or fragment.
2. The raw path is exactly `/sta/AutofirmaLote`. Reject a trailing slash,
   path parameters, encoded path separators, dot segments, or any other raw
   path spelling.
3. The query must contain exactly one occurrence of each required name, no
   unknown names, and no fragment. The allowlisted key sets are:
   - `op=presign` with `operacionId`;
   - `op=postsign` with `operacionId`;
   - `op=getdata` with `operacionId` and `docId`.
4. `op` values are exact lowercase values from that allowlist. Every
   `operacionId` and `docId` is required, non-empty, control-free, and within
   the existing bounded URL/query policy (`URL <= 8192`, raw query `<= 4096`,
   ephemeral value `<= 1024` characters). Duplicate parameters, malformed
   percent-encoding, empty pairs, whitespace/control values, and over-limit
   values fail closed.
5. The accepted pre-sign, post-sign, and data URLs are bound to the same
   active profile, source origin, navigation epoch, and runtime batch
   operation. A mismatched `operacionId` or document binding is rejected; no
   identifier is synthesized. The transport denies redirects and validates
   the final request URL again before use.

The URL policy is a dedicated public module, planned as
`app/src/main/java/dev/junta/firmamobile/network/MelillaBatchUrlPolicy.kt`.
It must be tested through its public validation interface or through the
batch adapter, not through private parsing helpers.

## Lifecycle and callback boundary

The batch request captures the selected `melilla-sede` profile, exact source
origin, current navigation epoch, request id, and active certificate snapshot.
There is at most one pending/active batch operation. Navigation, page teardown,
profile change, origin change, callback timeout, certificate lock, or a second
request abandons the batch and zeroizes owned request/result material. A stale
batch result cannot reach the page callback.

The dedicated batch reply channel emits only the evidence-bound portal callback
shape: `PRESENTAR_FIRMA` with `validationResponse` containing the serialized
batch result. It does not reuse the single-document `MINIAPPLET_RESULT`
callback and it does not submit the procedure.

## Intended files

Only the RED files below are changed in this phase:

- `docs/superpowers/specs/2026-08-09-melilla-autofirma-batch-contract-design.md`;
- `docs/superpowers/plans/2026-08-09-melilla-autofirma-batch-contract.md`;
- `app/src/test/java/dev/junta/firmamobile/browser/MelillaBatchBridgeAdapterTest.kt`;
- `app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt`.

The later GREEN implementation is intentionally named here so the RED test
has a stable public target:

- `app/src/main/java/dev/junta/firmamobile/browser/MelillaBatchBridgeAdapter.kt`;
- `app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt`;
- `app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt`;
- `app/src/main/res/raw/afirma_shim.js`;
- `app/src/main/java/dev/junta/firmamobile/network/MelillaBatchUrlPolicy.kt`;
- `app/src/main/java/dev/junta/firmamobile/profile/ProfileModels.kt`;
- `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt`;
- `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileRegistry.kt`;
- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningModels.kt`;
- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningProtocolAdapter.kt`;
- `app/src/main/java/dev/junta/firmamobile/signing/MelillaBatchProtocolAdapter.kt`;
- `app/src/main/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistry.kt`;
- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningCoordinator.kt`;
- `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`;
- `config/site_profiles_v1.json`;
- `docs/compatibility/all-spanish-public-portals-inventory.md`;
- regenerated `app/src/main/res/raw/public_portal_catalog_v1.json`;
- focused tests in `browser`, `network`, `profile`, `signing`, and `catalog`,
  plus `tools/tests/test_generate_public_portal_catalog.py`.

The future profile/parser/registry work must keep all existing ordinary
MiniApplet profiles and release behavior unchanged. The generated catalog is
updated only by the canonical generator, never by hand.

## Verification boundary

The RED command is Cloud-only and is not run by this worker. The first GREEN
command is the same focused Cloud test after the dedicated route exists. The
full GREEN gate adds the profile/parser, URL policy, batch execution, registry,
shim, catalog, and generator tests; it still does not claim physical E2E.

No network research, WebView/device run, Gradle execution, authentication,
credential/certificate use, signing, upload, payment, or form submission is
part of this phase.
