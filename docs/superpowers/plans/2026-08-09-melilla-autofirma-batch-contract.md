# Melilla AutoFirma batch contract implementation plan

> Follow `docs/agents/matt-pocock-workflow.md` and the red → green TDD loop.
> All Gradle commands are Cloud-only through `w47-cloud` as required by
> `docs/agents/codex-cloud-gradle.md`. This worker performs only the RED phase.

## Goal

Define a fail-closed, QA-only Melilla AutoFirma batch contract without
changing the existing ordinary `MiniApplet.sign` route. The first tracer
slice must make the missing dedicated batch route observable through the
public native-routing seam.

## Global constraints

- Evidence is limited to `build/autonomous-evidence/g36-melilla-batch/EVIDENCE_PACKET.md`.
- Do not add or change production Kotlin/JavaScript, profile config, inventory,
  generated catalog, endpoints, or network behavior in RED.
- Do not infer a portal ABI from AutoScript names. Runtime `operacionId` and
  `docId` values are accepted only as bounded opaque query values.
- Preserve ordinary `MiniApplet.sign`, its `NormalizedSignRequest`, its
  callback, its registry bindings, and its lifecycle unchanged.
- The eventual profile is `melilla-sede` with `VERIFIED_CONTRACT` / `QA_ONLY`;
  the eventual inventory/catalog states are `IMPLEMENTED_NOT_E2E` /
  `E2E_PENDING`. No physical E2E or release enablement follows from this work.

## Task 1 — establish the first RED tracer slice

**Files changed now:**

- `app/src/test/java/dev/junta/firmamobile/browser/MelillaBatchBridgeAdapterTest.kt`.

The test sends a synthetic internal batch envelope containing only the
evidence-backed `signInfo` fields: `batchPreSignerUrl`, `batchPostSignerUrl`,
`documentos[].id`, `documentos[].datareference`, the RSA/SHA256 + CAdES + sign
defaults, and `stopOnError=false`. The URLs use only the evidence-backed
same-origin `/sta/AutofirmaLote` family and the exact `presign`, `postsign`, and
`getdata` query shapes.

The observable assertions are:

1. the public route is not `MiniAppletBridgeRouteResult.Accepted`; and
2. the result is not `MiniAppletBridgeRouteResult.NotApplicable`.

The second assertion is intentionally RED today: the current single-sign
adapter does not recognize the dedicated batch discriminator and returns
`NotApplicable`. A later implementation must add a distinct batch result and
dispatch it before the ordinary single-sign adapter. It must not make the
batch envelope satisfy the existing single-sign key set.

### Cloud-only RED command (orchestrator runs after push)

```bash
BRANCH="$(git branch --show-current)"
SHA="$(git rev-parse HEAD)"
w47-cloud gradle --branch "$BRANCH" --sha "$SHA" :app:testDebugUnitTest --tests 'dev.junta.firmamobile.browser.MelillaBatchBridgeAdapterTest'
```

Expected concrete failure: the focused test reaches the current public
`MiniAppletBridgeAdapter.route` and fails its dedicated-route assertion with
the actual result `MiniAppletBridgeRouteResult.NotApplicable`; no production
batch route exists yet. This worker does not run the command.

## Task 2 — first GREEN vertical slice (later worker phase)

**Intended files:**

- `app/src/main/java/dev/junta/firmamobile/browser/MelillaBatchBridgeAdapter.kt`;
- `app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt`;
- `app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt`;
- `app/src/main/res/raw/afirma_shim.js`;
- `app/src/main/java/dev/junta/firmamobile/network/MelillaBatchUrlPolicy.kt`;
- `app/src/test/java/dev/junta/firmamobile/browser/MelillaBatchBridgeAdapterTest.kt`;
- additions to `app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt`.

Add a profile-scoped document-start hook for `AutoScript.signBatchProcess`
and a dedicated native batch message/result. The native bridge validates the
exact active profile/origin/main-frame/navigation epoch before dispatching to
the batch adapter. The existing single-sign route remains the fallback only
for its exact `MINIAPPLET_SIGN` key set and never consumes the batch envelope.

The URL policy must enforce exactly:

- effective origin `https://sede.melilla.es`, HTTPS/443, no user-info or
  fragment;
- raw path `/sta/AutofirmaLote` with no alternate spelling;
- `presign`/`postsign` query keys `{op, operacionId}` and `getdata` keys
  `{op, operacionId, docId}`;
- exact lowercase `op` values, unique names, no unknown parameters, and
  non-empty bounded control-free opaque identifiers;
- no redirects and no URL outside the current active batch binding.

### Cloud-only first GREEN command

```bash
BRANCH="$(git branch --show-current)"
SHA="$(git rev-parse HEAD)"
w47-cloud gradle --branch "$BRANCH" --sha "$SHA" :app:testDebugUnitTest --tests 'dev.junta.firmamobile.browser.MelillaBatchBridgeAdapterTest' --tests 'dev.junta.firmamobile.browser.AfirmaJavascriptShimTest'
```

## Task 3 — profile, registry, and dedicated execution

**Intended files:**

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
- tests in `app/src/test/java/dev/junta/firmamobile/profile`,
  `app/src/test/java/dev/junta/firmamobile/signing`, and
  `app/src/test/java/dev/junta/firmamobile/browser`.

Bind the exact Melilla profile to the dedicated batch input adapter and
separate batch execution interface. Keep the normal single-sign registry and
coordinator contract intact. Revalidate profile/version/origin/algorithm/
format, document count/size, response size, certificate state, callback
ownership, and navigation epoch at every execution boundary. Do not add an
administrative submission endpoint or infer a response schema beyond the
bounded JSON value delivered to `PRESENTAR_FIRMA`.

### Cloud-only focused GREEN command

```bash
BRANCH="$(git branch --show-current)"
SHA="$(git rev-parse HEAD)"
w47-cloud gradle --branch "$BRANCH" --sha "$SHA" :app:testDebugUnitTest --tests 'dev.junta.firmamobile.browser.MelillaBatchBridgeAdapterTest' --tests 'dev.junta.firmamobile.browser.AfirmaJavascriptShimTest' --tests 'dev.junta.firmamobile.profile.SiteProfileCatalogParserTest' --tests 'dev.junta.firmamobile.profile.SiteProfileRegistryTest' --tests 'dev.junta.firmamobile.signing.MelillaBatchProtocolAdapterTest' --tests 'dev.junta.firmamobile.signing.ProtocolAdapterRegistryTest'
```

## Task 4 — truthful inventory/catalog binding (later phase)

**Intended files:**

- `docs/compatibility/all-spanish-public-portals-inventory.md` (Melilla
  record only);
- regenerated `app/src/main/res/raw/public_portal_catalog_v1.json`;
- `app/src/test/java/dev/junta/firmamobile/catalog/PublicPortalCatalogParserTest.kt`;
- `app/src/test/java/dev/junta/firmamobile/catalog/PortalCatalogRepositoryTest.kt`;
- `tools/tests/test_generate_public_portal_catalog.py`.

Update the Melilla source record only after the functional contract exists:
exact public entry URL, observed AutoScript/batch metadata, profile binding,
`IMPLEMENTED_NOT_E2E`, and a limitation stating that no portal E2E was
performed. Regenerate the catalog with the canonical generator; do not hand
edit generated JSON. Verify that the catalog reports `E2E_PENDING` and that
QA-only launch remains unavailable in the release registry.

The generator command is local/static and does not replace the Cloud Gradle
boundary:

```bash
python3 tools/generate_public_portal_catalog.py --source docs/compatibility/all-spanish-public-portals-inventory.md --profiles config/site_profiles_v1.json --output app/src/main/res/raw/public_portal_catalog_v1.json
```

## Task 5 — final verification and commit boundary

For the later implementation phase, run the focused Cloud tests, the Python
generator test, `git diff --check`, and a complete diff inspection. Confirm no
network call, endpoint guess, callback invention, credential/private-key
material, E2E claim, or ordinary single-sign relaxation. Commit only the
candidate-specific files.

For this RED phase, run only `git diff --check`, inspect the complete diff,
and commit these three files with:

```bash
git add docs/superpowers/specs/2026-08-09-melilla-autofirma-batch-contract-design.md docs/superpowers/plans/2026-08-09-melilla-autofirma-batch-contract.md app/src/test/java/dev/junta/firmamobile/browser/MelillaBatchBridgeAdapterTest.kt
git commit -m "test(portal): define Melilla batch contract"
```
