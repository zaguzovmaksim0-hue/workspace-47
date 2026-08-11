# Extremadura STA batch integration plan — 2026-08-11

Design: `docs/superpowers/specs/2026-08-11-extremadura-sta-batch-integration-design.md`
Target: `extremadura-tramites` / `ES-PUB-0109`.

## Slice 1 — multi-adapter batch coordinator blocker

Files:
- `app/src/test/java/dev/junta/firmamobile/signing/BatchSigningCoordinatorTest.kt`
- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningCoordinator.kt`

1. RED: prove a coordinator configured with two exact adapters accepts a request for the second adapter
   and executes only that adapter. The current fixed-adapter constructor must fail this tracer bullet.
2. GREEN: add a fail-closed `adapterResolver`; resolve at prepare time and bind the exact adapter to the
   owned operation. Preserve default single-adapter behavior.
3. Cloud gate: focused Debug `BatchSigningCoordinatorTest`, then Debug+QA batch coordinator tests if the
   first GREEN passes.

## Slice 2 — exact Extremadura STA URL policy

Files:
- `app/src/test/java/dev/junta/firmamobile/network/MelillaBatchUrlPolicyTest.kt` and/or a new exact STA
  policy test file
- `app/src/main/java/dev/junta/firmamobile/network/MelillaBatchUrlPolicy.kt` and, if separation is
  cleaner, a new shared/internal STA policy file

RED/GREEN must prove both hosts independently accept only their exact
`/sta/AutofirmaLote/{presign,postsign,getdata}` bindings and reject cross-host/cross-operation/cross-id
inputs. Melilla behavior must remain byte-for-behavior compatible at the public class boundary.

## Slice 3 — Extremadura protocol + bridge normalization

Files selected after Slice 2 review, expected among:
- `app/src/main/java/dev/junta/firmamobile/signing/MelillaBatchProtocolAdapter.kt`
- `app/src/main/java/dev/junta/firmamobile/browser/MelillaBatchBridgeAdapter.kt`
- `app/src/main/java/dev/junta/firmamobile/browser/MelillaBatchSigningAdapter.kt`
- corresponding focused tests

Introduce the smallest shared internal STA contract/core needed for a distinct Extremadura adapter.
Keep profile id, version, origin, protocol id, runtime URL policy, request/reply ownership, and
navigation/document bindings exact. Do not generalize ordinary MiniApplet signing.

## Slice 4 — Browser/MainActivity composition

Files expected:
- `app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt`
- `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- focused bridge/security regression tests

Wire the selected STA profile to the existing single batch signing ownership flow. Use the new batch
adapter resolver; route confirm/cancel/UI state only to the one owned request. Preserve Melilla's
existing runtime behavior and all ordinary-vs-batch arbitration.

## Slice 5 — profile, registry, catalog promotion

Files expected:
- `config/site_profiles_v1.json`
- `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt`
- `app/src/main/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistry.kt`
- `docs/compatibility/all-spanish-public-portals-inventory.md`
- `app/src/main/res/raw/public_portal_catalog_v1.json`
- parser/registry/catalog/generator tests

Add exact Extremadura QA-only profile and bind `ES-PUB-0109`. Truthful terminal state is at most
`IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; release remains `VERIFIED_CONTRACT` and disabled. Regenerate
the public catalog from canonical sources and require byte identity.

## Final gates

Before acceptance, push the exact SHA and run the canonical full Android gate only in
`workspace-47-android`:

`verifyResolvedCoreVersion verifyPortableAapt2Configuration testDebugUnitTest testQaUnitTest lintDebug lintQa assembleDebug assembleQa assembleQaAndroidTest`

Locally run only permitted non-Gradle checks: Python catalog tests/regeneration, `git diff --check`,
changed-content safety scans, and direct Standards + Spec review. Update audit ledger/handoff only with
observed evidence.
