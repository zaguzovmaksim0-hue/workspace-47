# Network Failure Detail Visibility Design

## Finding

`ProfileHttpResult.Failure` is a public data class whose public primary
constructor/property exposes the internal `ProfileHttpFailureDetail` type. Kotlin
2.3 therefore requires explicit `EXPOSED_PARAMETER_TYPE` and
`EXPOSED_PROPERTY_TYPE` suppressions.

The exposed detail contains route-safety state (`ProfileHttpFailurePhase` and
`httpWriteStarted`) that is an implementation detail of direct/tunnel fallback.
Production signing consumers use the stable `code` surface; detailed phase access
is confined to the network package. The app is not publishing this transport as a
separate library API.

A synthetic Kotlin 2.3 fixture showed that merely making a data-class primary
constructor internal creates a second warning because generated `copy()` remains
more visible. A normal class with an internal primary constructor/property and a
public secondary `Failure(ProfileHttpFailure)` constructor compiles without that
visibility warning.

## Security and architecture objective

Keep routing phase/write-state internal so callers cannot couple themselves to the
pre-write fallback classifier, while preserving the existing public failure-code
shape used by signing logic. Do not change retry/fallback classification or
network behavior.

## Chosen approach

Change only `ProfileHttpResult.Failure`:

- from `data class` to ordinary `class`;
- internal primary constructor taking `internal val detail`;
- retain public `val code: ProfileHttpFailure`;
- retain public secondary constructor `Failure(ProfileHttpFailure)`;
- remove `EXPOSED_*` suppression.

No `Failure` data-class behavior is used in production/tests: there is no
`Failure.copy`, destructuring, or structural-equality assertion. The only copy is
on the internal `ProfileHttpFailureDetail` itself.

This is narrower than making the complete HTTP transport API internal, which would
cause an unnecessary visibility cascade through codec and adapter interfaces.
Making `ProfileHttpFailureDetail` public is rejected because it would formalize an
internal route-safety state machine as public API.

## Files

- Modify `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpTransport.kt`.
- Add a source/API policy regression test in `tools/tests/test_ci_policy.py`.
- Update autonomous ledger, roadmap, test report and durable handoff only after
  verification evidence exists.

## Verification

1. RED policy test proves the current suppression/public-detail shape is rejected.
2. Minimal production change makes the focused policy test GREEN.
3. Compile Debug and QA and run focused network + signing tests.
4. Run complete Debug/QA JVM suites, lint/builds, Python, Go, artifact and release
   fail-closed gates.
5. Inspect complete diff, `git diff --check`, and sensitive/unsafe patterns.

No APK installation, app launch, device control, portal interaction, credential,
certificate or signing operation is part of this milestone.
