# Melilla batch coordinator design

Date: 2026-08-11
Status: approved subordinate design for the next portal-first vertical slice
Parent: `docs/superpowers/specs/2026-08-09-portal-coverage-first-autonomous-priority-design.md`

## Problem

The Melilla bridge, URL policy and batch protocol execution core are independently Cloud-verified, but there is no ownership layer between an accepted batch request and certificate/private-key use. `SigningCoordinator` is deliberately single-sign and must not be widened merely to reuse its state machine. A Melilla batch needs the same security lifecycle: capture the selected certificate identity before confirmation, show a safe confirmation state without network/private-key use, revalidate origin/navigation/certificate after confirmation, sign each server-prepared input exactly once, deliver exactly one terminal result, and fail closed on cancellation or stale context.

## Boundary

Add a separate `BatchSigningCoordinator` in the signing package. It reuses existing `SigningUiState`, `SigningPreparationResult`, `SigningExecutionResult`, `SigningCancelReason`, `CertificateSigningSnapshot`, `LocalSignatureEngine` and `SigningExpiryScheduler`; it does not modify ordinary `SigningCoordinator` behavior.

Add a generic `BatchSigningReplySink` to `BatchSigningModels.kt` so the signing package does not depend on browser/WebView classes. The later runtime slice will adapt `MelillaBatchReplyChannel` to this sink.

### Public seam

`BatchSigningCoordinator` exposes:

- `state: StateFlow<SigningUiState>`;
- `prepare(request: NormalizedBatchSigningRequest, reply: BatchSigningReplySink): SigningPreparationResult`;
- `suspend confirm(requestId: UUID): SigningExecutionResult`;
- `cancel(reason: SigningCancelReason, requestId: UUID? = null): Boolean`;
- `dismissTerminalState()` and `close()`.

Constructor dependencies are injected and narrow: `CertificateSession`, one `BatchSigningProtocolAdapter`, `LocalSignatureEngine`, current origin, current navigation epoch, `SigningExpiryScheduler`, monotonic time, and safe profile display/support labels supplied by the later runtime composition.

## Prepare contract

Before confirmation, `prepare` must:

1. require reply/request id equality;
2. require the exact adapter protocol id and current origin/navigation epoch to match the normalized request;
3. reject concurrent pending/active operations;
4. capture `CertificateSigningSnapshot`; if unavailable, fail `CERTIFICATE_LOCKED`;
5. take ownership of the normalized request and schedule a bounded monotonic confirmation expiry;
6. publish `SigningUiState.AwaitingConfirmation` containing only safe data: request id, host, supplied profile name/support level, document count, batch format/algorithm and certificate owner;
7. perform no network call and no private-key operation.

The confirmation state must not expose presign/postsign/getdata URLs, operation id, document ids, certificate bytes, PRE/PK1 data or private-key material.

## Confirm contract

After explicit confirmation, `confirm` must atomically move the exact pending request to active ownership, then repeatedly revalidate current origin/navigation and the captured certificate snapshot around all externally observable steps.

The sequence is:

1. resolve the same unlocked identity through `identityForSigning(snapshot)`;
2. call batch protocol `prepare` once using that certificate chain;
3. for each ordered PRE input, call `LocalSignatureEngine.sign` exactly once with the same identity and request algorithm;
4. if any local signature fails, close all acquired signatures/pre-sign state and fail without postsign;
5. call batch protocol `complete` once with ordered local signatures;
6. before delivery, revalidate context/certificate again;
7. hand the bounded final batch response to `BatchSigningReplySink.success` exactly once and close owned response bytes;
8. publish `Completed` only if delivery returns true; otherwise `RESULT_DELIVERY_FAILED`.

No retry is permitted. A transport result marked uncertain remains uncertain through the protocol adapter. No alternate HTTP/TLS stack is introduced.

## Cancellation and expiry

Reuse `SigningCancelReason` semantics. Pending cancellation clears the request/snapshot/expiry and sends failure or abandon exactly once. Active cancellation claims terminal ownership so late network/signing completion cannot deliver success. Navigation/profile/document lifecycle cancellation will be wired in the later runtime slice. Confirmation expiry uses monotonic time and must clear the certificate snapshot and request without private-key/network use.

## Exact files for this slice

Production:

- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningModels.kt` — add `BatchSigningReplySink` only.
- `app/src/main/java/dev/junta/firmamobile/signing/BatchSigningCoordinator.kt` — new coordinator.

Tests:

- `app/src/test/java/dev/junta/firmamobile/signing/BatchSigningCoordinatorTest.kt` — vertical TDD tests through the coordinator public seam.

Documentation:

- this design;
- `docs/superpowers/plans/2026-08-11-melilla-batch-coordinator.md`.

## Explicitly out of scope

- `SigningCoordinator` changes;
- `WebMessageBridge`, `BrowserScreen`, `MainActivity` runtime wiring;
- `MelillaBatchBridgeAdapter` or reply-registry behavior changes;
- profile/binding/catalog/release enablement;
- new transport, retry, TLS, hostname, certificate or key-storage behavior;
- physical E2E or `VERIFIED_E2E` claims.

Those are later slices after this coordinator is independently Cloud-verified.
