# Melilla AutoFirma batch execution core — implementation plan

Date: 2026-08-11
Design: `docs/superpowers/specs/2026-08-11-melilla-batch-execution-core-design.md`

## Task 1 — dedicated TDD seam

Create only:

- `app/src/test/java/dev/junta/firmamobile/signing/MelillaBatchProtocolAdapterTest.kt`

The first test set must use synthetic operation/document IDs, synthetic X.509
certificates, and a recording fake `ProfileHttpTransport` to assert:

1. exact pre POST URL and `json`/`certs` form fields;
2. exact reconstructed AutoScript batch JSON, including only the known PAdES/XAdES
   extra-parameter mappings;
3. strict parsing of a synthetic presign response with more than one `PRE`;
4. exact local-sign input bytes exposed in deterministic order;
5. exact post body `json`/`certs`/`tridata` after supplied synthetic PK1 values;
6. `PRE` removal unless `NEED_PRE=true`;
7. opaque strict JSON final response;
8. zero network on malformed binding/response and no post on invalid local-signature
   cardinality or reused prepared state.

Commit and push this intended RED before Cloud execution. Cloud command:

```bash
BRANCH="$(git branch --show-current)"
SHA="$(git rev-parse HEAD)"
w47-cloud gradle --branch "$BRANCH" --sha "$SHA" \
  :app:testDebugUnitTest \
  --tests dev.junta.firmamobile.signing.MelillaBatchProtocolAdapterTest
```

The expected RED is missing batch execution types/adapter or failing new behavior;
compilation/environmental failures unrelated to that seam must be diagnosed first.

## Task 2 — minimum execution core

Create only:

- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningModels.kt`
- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningProtocolAdapter.kt`
- `app/src/main/java/dev/junta/firmamobile/signing/MelillaBatchProtocolAdapter.kt`

Do not change `SigningCoordinator`, `SigningProtocolAdapter`, profile registry,
`MainActivity`, `BrowserScreen`, JS shim, profile JSON, inventory, or generated catalog.

Implement only enough to make the Task 1 behavior pass while preserving all bounds
from the design. Use injected `ProfileHttpTransport`; do not add a new network stack.

## Task 3 — review and GREEN

Before commit:

- inspect the complete diff;
- `git diff --check`;
- scan changed content for private-key/certificate-body logging, TLS/hostname bypass,
  URL allowlist widening, retry behavior, arbitrary query parameters, and unrelated
  changes;
- confirm the only browser-side inputs are already-normalized values represented in
  the dedicated request model.

Commit/push exact production candidate and rerun the same focused Cloud test. Then
run the existing Melilla URL/bridge tests plus the new adapter test in Debug and QA.
No local Gradle or Kotlin execution is permitted.

## Deferred to the next slice

- `BatchSigningCoordinator` and certificate snapshot/user-confirmation lifecycle;
- `BrowserScreen` / `MainActivity` runtime wiring;
- runtime response media-type evidence/default transport composition;
- profile/catalog promotion;
- any physical/manual E2E claim.
