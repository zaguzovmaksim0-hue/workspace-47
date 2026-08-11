# Melilla live AutoFirmaLote contract refresh — generation 51 — 2026-08-11

## Scope

This checkpoint uses only unauthenticated public `GET` requests with synthetic identifiers against
`https://sede.melilla.es/sta/AutofirmaLote`. No authenticated page, certificate, personal data,
real operation identifier, signature, upload, form submission, payment, or administrative action was
used.

The purpose is to resolve a material conflict between the currently served static
`sta-autofirma-lote.js` comments and the live servlet routing contract before native batch execution
is implemented.

## Public evidence

The currently served wrapper still documents query-style runtime URLs such as
`/sta/AutofirmaLote?op=presign&operacionId=...`. A bounded synthetic GET to that shape returned HTTP
400 with JSON stating that the URL was incorrect and that `/{op}/{operacionId}` was expected.

The same live servlet accepted the path routing layer for synthetic identifiers:

- `GET /sta/AutofirmaLote/presign/<synthetic-id>` returned HTTP 400 stating that parameter `json` is
  required for `op=presign`;
- `GET /sta/AutofirmaLote/postsign/<synthetic-id>` returned HTTP 400 stating that parameter `json` is
  required for `op=postsign`;
- `GET /sta/AutofirmaLote/getdata/<synthetic-id>/<synthetic-doc-id>` reached operation lookup and
  returned HTTP 404 because the synthetic operation did not exist;
- `GET /sta/AutofirmaLote/presign/<synthetic-id>?json=e30&certs=eA` reached JSON validation and
  returned HTTP 400 stating that the JSON lacked `singlesigns`.

These responses establish, without a real signing operation, that the current servlet dispatches
batch operation identity in path segments and expects AutoFirma's `json`/`certs` request parameters
only after that base path. This also removes the prior ambiguity in the official AutoFirma v1.9
client code, which appends `?json=...&certs=...` to the supplied pre-sign URL and adds `tridata` for
the post-sign URL.

## Contract consequence

The earlier query-style `MelillaBatchUrlPolicy` is stale and must not be used as the basis for batch
network execution. The runtime base URLs accepted from the portal must instead be restricted to:

- `https://sede.melilla.es/sta/AutofirmaLote/presign/{operacionId}`;
- `https://sede.melilla.es/sta/AutofirmaLote/postsign/{operacionId}`;
- `https://sede.melilla.es/sta/AutofirmaLote/getdata/{operacionId}/{docId}`.

The received base URLs must contain no query or fragment. The native execution layer may later add
only the evidence-backed AutoFirma parameters for the relevant operation and must revalidate the
final request URL before transport. Runtime identifiers remain opaque and are never synthesized.

This refresh does not prove an authenticated Melilla flow, a successful pre-sign/post-sign response,
a real certificate operation, or end-to-end callback behavior. Melilla therefore remains QA-only and
not E2E-verified.
