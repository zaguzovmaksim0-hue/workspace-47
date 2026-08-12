# Melilla batch runtime wiring — design

Date: 2026-08-11
Status: approved subordinate design for the runtime-wiring vertical slice
Parent: `docs/superpowers/specs/2026-08-09-portal-coverage-first-autonomous-priority-design.md`

## Problem

The Melilla bridge, URL policy, batch protocol adapter and `BatchSigningCoordinator` are independently Cloud-verified, but the runtime does not connect them. `WebMessageBridge` can create `MelillaBatchBridgeRequest` and `MelillaBatchReplyChannel`, yet `BrowserScreen` does not forward them and `MainActivity` owns no Melilla batch coordinator. JavaScript batch cancellation and bridge abandonment currently terminate only bridge/reply-registry ownership, not an in-flight `BatchSigningCoordinator` operation.

A second independent signing coordinator also creates an arbitration problem: ordinary MiniApplet signing and Melilla batch signing must never concurrently own certificate/private-key confirmation or present competing `SigningUiState` dialogs.

## Boundary and public seams

This slice adds only runtime adaptation/composition. It does not change Melilla's public evidence, profile/catalog status, release activation, protocol URLs, TLS behavior or signing algorithm support.

### Bridge-to-signing adapter

Add a browser-layer adapter that converts an already-validated `MelillaBatchBridgeRequest` into `NormalizedBatchSigningRequest` using the active built-in Melilla profile contract. It must preserve request/document order and exact operation/URL bindings; derive the signing `NavigationId` from the bridge document id; use the bridge navigation epoch and trusted origin; accept only the profile's currently declared top-level CAdES/SHA256withRSA/sign/stopOnError=false contract; and map per-document formats only from the bridge-validated CAdES/PAdES/XAdES vocabulary.

The same adapter wraps `MelillaBatchReplyChannel` as `BatchSigningReplySink`. Success performs strict UTF-8 decoding of the already-bounded protocol response and delegates once to the channel; failure and abandon delegate directly. It introduces no callback execution, form submission or additional WebView API.

### Explicit batch cancellation

`WebMessageBridge` gains an explicit batch-cancel callback. It is fired only for a batch request that owned a reply-registry entry and is being terminally abandoned by:

- a validated `MINIAPPLET_BATCH_CANCEL`; or
- bridge-wide abandonment caused by document/navigation/WebView teardown.

The reply registry returns the exact abandoned request ids so runtime ownership can be cancelled. Existing ordinary MiniApplet cancellation semantics remain unchanged.

### Runtime composition and arbitration

`MainActivity` constructs one `MelillaBatchProtocolAdapter` over the existing `HttpsProfileHttpTransport` security stack and one dedicated `BatchSigningCoordinator`. No retry, alternate DNS/TLS/hostname path or tunnel route is added.

`BrowserScreen` forwards accepted batch request/reply pairs and explicit batch cancellation to `MainActivity`.

`MainActivity` owns a small synchronized signing-flow arbitration state keyed by request id and kind (ordinary or batch). A prepare call must claim the gate before either coordinator can own a request; competing ordinary/batch prepares fail closed before certificate/private-key use. Confirm/cancel route only to the owning coordinator. The same `SigningJobRegistry` tracks confirmation jobs. The displayed signing state is the single state of the current owner; terminal/idle dismissal releases ownership. Lifecycle/navigation/certificate cancellation applies to whichever signing kind owns the gate.

The arbitration gate is conservative: an expired terminal state may keep the gate until dismissal rather than permit overlapping UI. This is intentionally fail-closed.

## TDD seams

1. `MelillaBatchSigningAdapter` public/internal seam: exact request conversion, invalid-contract rejection, and `BatchSigningReplySink` terminal forwarding.
2. `WebMessageBridge` observable callback seam: JavaScript cancel and abandon-all notify exactly the owned batch request id once.
3. Runtime source/behavior seam: `BrowserScreen` forwards batch callbacks and `MainActivity` constructs the existing transport/protocol/coordinator path and routes a single signing owner. Existing browser security regression tests may assert the static wiring boundary where Android lifecycle construction is not cheaply unit-testable.

## Exact files

Production:

- `app/src/main/java/dev/junta/firmamobile/browser/MelillaBatchSigningAdapter.kt` — new bridge/signing normalization + reply-sink adapter.
- `app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt` — explicit batch cancellation notification and abandoned-id return.
- `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt` — forward batch request/cancel callbacks.
- `app/src/main/java/dev/junta/firmamobile/MainActivity.kt` — compose `HttpsProfileHttpTransport` + `MelillaBatchProtocolAdapter` + `BatchSigningCoordinator` and arbitrate one signing owner.

Tests:

- `app/src/test/java/dev/junta/firmamobile/browser/MelillaBatchSigningAdapterTest.kt` — conversion and reply-sink seam.
- existing `app/src/test/java/dev/junta/firmamobile/browser/WebMessageBridgeTest.kt` or the nearest existing bridge test — cancellation notification tracer bullets.
- `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt` and/or `app/src/test/java/dev/junta/firmamobile/ui/BrowserScreenTest.kt` — bounded runtime wiring contracts where needed.

Documentation:

- this design;
- `docs/superpowers/plans/2026-08-11-melilla-runtime-wiring.md`.

## Explicitly out of scope

- public-catalog/profile promotion or generated inventory mutation;
- `VERIFIED_E2E` or physical-device claims;
- release activation of Melilla;
- authenticated portal navigation or real signing;
- changes to certificate storage/unlock behavior;
- network/TLS/DNS/hostname policy widening;
- retries or alternate protocol endpoints;
- ordinary `SigningCoordinator` protocol semantics.
