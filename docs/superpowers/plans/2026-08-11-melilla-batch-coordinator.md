# Melilla batch coordinator implementation plan

Date: 2026-08-11
Design: `docs/superpowers/specs/2026-08-11-melilla-batch-coordinator-design.md`

## Method

Use the repository Matt Pocock workflow in the main Watchdog only. Work in vertical TDD cycles. Every Gradle RED/GREEN runs only in Codex Cloud `workspace-47-android` against an exact committed and pushed SHA. Never run local Gradle/JVM/Kotlin and never delegate to implementation/review workers.

## Slice 1 — safe prepare ownership

1. Add one `BatchSigningCoordinatorTest` tracer bullet asserting that `prepare` captures a certificate snapshot and publishes a safe `AwaitingConfirmation` state while protocol/engine/reply remain untouched.
2. Commit/push the RED fixture and run only that test in Cloud. Accept RED only if it fails on the missing `BatchSigningCoordinator`/`BatchSigningReplySink` seam.
3. Add `BatchSigningReplySink` and the minimum coordinator implementation needed for the prepare contract: boundary checks, certificate snapshot ownership, monotonic expiry scheduling, safe confirmation state and cleanup.
4. Commit/push, run focused Cloud GREEN, inspect diff and security ownership.

## Slice 2 — exact ordered confirm

1. Add one test proving two PRE inputs are signed exactly once and in order, protocol prepare/complete each run once, and a single success response is delivered.
2. Commit/push RED, run focused Cloud RED.
3. Implement the minimum confirm path with repeated origin/navigation/certificate checks and deterministic cleanup.
4. Commit/push and Cloud GREEN.

## Slice 3 — fail-closed lifecycle

Add targeted tests one at a time for certificate replacement/lock, navigation/origin change, local signature failure, cancellation during active work, reply delivery failure, confirmation expiry, duplicate confirm and close. Do not weaken behavior to satisfy tests.

## Acceptance for coordinator slice

- all `BatchSigningCoordinatorTest` focused Debug tests pass in Cloud;
- existing `MelillaBatchProtocolAdapterTest`, `MelillaBatchBridgeAdapterTest` and `MelillaBatchUrlPolicyTest` remain green in Debug+QA;
- dependency verification enabled and metadata unchanged;
- Cloud checkout clean;
- `git diff --check` and bounded secret/TLS/retry scans pass;
- direct Standards + Spec review finds no Critical/Important issue;
- no runtime/profile/catalog/release mutation in this slice.

After this boundary, create a separate runtime-wiring subordinate design/plan. That later slice will adapt `MelillaBatchBridgeRequest` to `NormalizedBatchSigningRequest`, adapt `MelillaBatchReplyChannel` to `BatchSigningReplySink`, add explicit batch-cancel notification from `WebMessageBridge`, wire `BrowserScreen/MainActivity`, and verify navigation/document cancellation before any profile/catalog promotion.
