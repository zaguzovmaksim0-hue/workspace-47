# Melilla batch runtime wiring — implementation plan

Date: 2026-08-11
Design: `docs/superpowers/specs/2026-08-11-melilla-runtime-wiring-design.md`

## Method

Use the repository Matt Pocock `codex/implement` + `codex/tdd` workflow in the main Watchdog only. One vertical tracer bullet at a time. Every Android Gradle RED/GREEN runs only in Codex Cloud `workspace-47-android` against an exact committed and pushed SHA. No local Gradle/JVM/Kotlin and no implementation/review workers.

## Slice 1 — bridge request/reply adaptation

1. Add `MelillaBatchSigningAdapterTest` asserting exact conversion of a synthetic bridge request to the normalized batch request and one-shot reply-sink forwarding.
2. Commit/push RED and run only this test in Cloud. Valid RED must be the missing adapter seam, not an unrelated compilation/environment failure.
3. Add the minimal `MelillaBatchSigningAdapter.kt` implementation. Reject contract/profile mismatches before coordinator prepare.
4. Commit/push and run focused Cloud GREEN.

## Slice 2 — explicit batch cancellation

1. Add bridge test(s) proving a validated batch cancel notifies the runtime exactly once for the owned request and that bridge-wide abandonment returns/notifies the same owned request once.
2. Commit/push RED; run the exact bridge test in Cloud.
3. Change `WebMessageBridge`/`MelillaBatchReplyRegistry` minimally so terminal abandon returns owned ids and invokes the new batch-cancel callback. No new JS message types or widened origins.
4. Commit/push and focused Cloud GREEN.

## Slice 3 — BrowserScreen/MainActivity composition + arbitration

1. Add bounded runtime wiring regression coverage first: BrowserScreen forwards batch request/cancel callbacks; MainActivity constructs `MelillaBatchProtocolAdapter` over `HttpsProfileHttpTransport`, owns `BatchSigningCoordinator`, and routes only one ordinary/batch signing owner.
2. Commit/push RED and run the focused tests in Cloud.
3. Implement the minimal runtime composition and synchronized request-id/kind gate. Use the existing `SigningJobRegistry`; route lifecycle/navigation/certificate cancel to the current owner; render only the current owner's `SigningUiState`.
4. Commit/push and focused Cloud GREEN.

## Acceptance

Run in Codex Cloud on the exact pushed candidate SHA:

- `MelillaBatchSigningAdapterTest`;
- relevant `WebMessageBridge`/Melilla bridge tests;
- `BatchSigningCoordinatorTest`;
- `MelillaBatchProtocolAdapterTest`;
- `BrowserScreenTest` and `BrowserSecurityRegressionTest` if changed;
- Debug + QA focused variants as applicable.

Then inspect complete diff, run local non-Gradle `git diff --check`, bounded secret/TLS/retry/URL scans, and direct Standards + Spec review. If focused acceptance is clean, run the applicable broader Cloud Android gate required by the parent task before publication. Keep Melilla at `VERIFIED_CONTRACT` / `QA_ONLY` / at most `IMPLEMENTED_NOT_E2E`; profile/public-catalog promotion is a separate subsequent slice.
