# `afirma_shim.js` provenance review

**Status:** source-level review completed; maintainer authorship/authority attestation still required before repository-wide licensing.

## Scope

Reviewed project file:

- `app/src/main/res/raw/afirma_shim.js`

Reviewed official interoperability reference:

- public `ctt-gob-es/clienteafirma` repository;
- in particular the current official `afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js` and `miniapplet_JA.js` surfaces used by public web clients.

The official Cliente @firma repository states that AutoFirma/Cliente @firma is free software under **GPL 2+ and EUPL 1.1**. Therefore any substantial copied upstream implementation must be treated as licensed third-party source; it cannot simply be relabeled as project-owned permissive code.

## Project history evidence

The project path first appears in JFM commit `95e068b7d0a910f8ead42c04114d3875dd282d01` as a small project-specific WebView shim. The immediately reviewed parent did not contain the file. Later commits extend the file incrementally for bounded portal profiles, security validation, bridge messaging, lifecycle handling and compatibility observations.

History supports project-local development, but history alone is not proof of copyright authorship.

## Source-structure comparison

The official AutoScript implementation exposes the public client API and contains implementations such as `sign`, `createBatch`, `addDocumentToBatch`, batch processing, servlet configuration and other methods. Those public method names and protocol/data-field names necessarily appear in JFM because JFM interoperates with pages calling that API.

The reviewed JFM shim does **not** embed the official AutoScript/MiniApplet implementation as a vendored file. Instead it:

- runs in a JFM-specific IIFE and guards installation with `__jfmAfirmaShimInstalled`;
- obtains JFM native bridges from `window.JuntaFirmaMobile` / `window.JuntaFirmaProbe`;
- wraps already-present page methods using property descriptors and `Reflect.apply` rather than reimplementing the upstream client runtime;
- validates exact per-origin argument contracts before forwarding selected operations to native JFM code;
- uses JFM-specific UUID request correlation, timeouts, cancellation, pending-call maps and sanitized diagnostics;
- records a bounded Melilla batch model from observed `createBatch` / `addDocumentToBatch` calls and forwards a separate `MINIAPPLET_BATCH` message;
- applies fail-closed release/portal constraints that are project-specific.

In the high-risk batch section, the official AutoScript implementation constructs its own client-side batch request and delegates it to Cliente @firma. JFM instead observes calls on the page's existing object, validates a narrow contract and serializes an independent native-bridge message. The overlap identified by this review consists of interoperability/API identifiers and schema concepts rather than an identified copied implementation block.

## What this review establishes

`REVIEWED_NO_VENDORED_UPSTREAM_FILE_IDENTIFIED`:

- no complete upstream AutoScript/MiniApplet file is present under the JFM shim path;
- the file's repository history is incremental and project-specific;
- reviewed security/bridge implementation structure is materially JFM-specific;
- official API names, signature algorithms, format strings and batch schema terms are treated as interoperability facts, not as evidence that JFM owns the official implementation.

## What this review does **not** establish

It does not prove a negative for every line ever introduced, and it does not substitute for the maintainer's copyright/authority statement. Before applying a root project license, the maintainer should attest that, to the best of their knowledge:

1. JFM project source was authored for this project by contributors who were entitled to contribute it, except for components explicitly listed as third-party;
2. no substantial GPL/EUPL AutoFirma/Cliente @firma source was copied into `afirma_shim.js` without preserving the applicable license obligations;
3. official source was used only as an interoperability/reference surface where no copied implementation is identified, or any copied portion is separately disclosed and licensed.

If that attestation cannot be made, the affected portion must be identified and either relicensed/attributed under its actual terms or independently replaced before a blanket project license is selected.

## Publication treatment

Keep `afirma_shim.js` marked `PROJECT_SOURCE_REVIEWED_WITH_ATTESTATION_PENDING`. This review substantially narrows the provenance risk but does not by itself close the final authorship-authority gate.
