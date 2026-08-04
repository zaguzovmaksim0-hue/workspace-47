# Autonomous Audit Ledger — 2026-08-04

## Execution identity

- branch: `agent/workspace-47-autonomous-20260803`
- base: `9c99bbfb36e13f88231d56001ccef8c4cbbce128`
- worktree: `/data/data/com.termux/files/home/workspace-47-autonomous-20260803`
- task active-time budget: 43,200,000 ms
- generation active-time budget: 2,400,000 ms
- manual/device/credential operations: prohibited

## Baseline evidence

- Gradle: 143 actionable tasks; Debug unit 509/509 and QA unit 509/509 passed
  with zero failures, errors, or skips; `lintDebug`, `lintQa`, `assembleDebug`,
  `assembleQa`, and `assembleQaAndroidTest` passed.
- Python: 94 tests passed with one environmental hardlink skip.
- Go: `go test ./... -count=1`, `go vet ./...`, and relay build passed.
- Android artifact verification passed.
- Release without private signing inputs failed closed as required.
- The relay build produced one untracked local binary; it was deleted after its
  successful build and the worktree returned clean.
- No APK was installed or launched and no portal was opened.

## Queue discipline

Findings are appended with evidence, severity, autonomous feasibility, exact
sub-plan path, focused/full verification, commit SHA, push result, and residual
manual gate. A finding is not marked complete from reasoning alone.

## Remaining audit queue

1. Continue the fresh security/privacy trust-boundary audit with final-signature and
   certificate temporary-copy lifetime as the next explicit review lead; re-open
   logging/storage only if a new reproducible retention or failure-path defect appears.
2. Start a fresh independent architecture/lifecycle or UX/accessibility pass after
   the trust-boundary line reaches a clean checkpoint.

## Queue reconciliation — generation 1

- The DNS-executor determinism item was stale in this ledger. The current baseline
  already contains `DirectTestExecutorService`, explicit test-owned DNS executors
  for JVM transports, and the completed evidence recorded in the security roadmap,
  test report and durable handoff. No runtime-network change was repeated.
- The AEAT F-03 physical continuation is intentionally not an autonomous action:
  device control, certificate use and portal authentication are prohibited in this
  audit cycle. Its existing `VERIFIED_CONTRACT / QA_ONLY` status is unchanged.
- The exposed-network-type suppression remains an open architecture review item;
  no visibility/API mutation has been made yet.

## Finding G1-01 — QA WebView remote debugging boundary

**Reproduction.** `TrustedJuntaWebView` called
`setWebContentsDebuggingEnabled(BuildConfig.DEBUG)`. The QA build inherits from
`debug`, explicitly remains debuggable, and its generated BuildConfig showed
`DEBUG=true`. QA is the controlled acceptance variant used by existing real-portal
validation, so the broad debug flag also enabled application-wide WebView DevTools
for that variant.

**Remediation.** `ENABLE_WEBVIEW_CONTENTS_DEBUGGING` is now an explicit build
policy: default `false`, ordinary developer `debug=true`, `qa=false`, and
`release=false`. `TrustedJuntaWebView` consumes only that field; QA debuggability,
portal profiles, origin/TLS/signing policy and certificate behavior are unchanged.

**TDD evidence.** The new CI policy test first failed because the explicit field
was absent. An initial loose matcher then exposed a malformed intermediate edit
(debug carried both values and QA lacked its explicit override); that result was
rejected. The test was tightened to parse each build block independently, observed
RED on the malformed state, and passed only after the minimal corrected policy.

**Fresh verification.** Debug and QA JVM suites each passed 509/509 with zero
failures/errors/skips. `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa` and
`assembleQaAndroidTest` passed in one Gradle invocation (`BUILD SUCCESSFUL`, 140
actionable tasks); lint contained 0 errors and 27 warnings per variant. Python
passed 95 tests with one environmental hardlink skip. Go test/vet/build passed.
Android artifact verification passed. Release without private signing inputs was
rejected fail-closed as required. Generated BuildConfig evidence is `true` for
Debug and `false` for QA; release does not generate BuildConfig because its signing
gate rejects configuration before that stage, and the source policy test pins its
explicit `false` branch.

APK SHA-256 after the remediation:

- Debug: `2f45274f105faac67c5cedd3272278cad1a7b77bae592730fecea3786da7b4c4`;
- QA: `d326174c55a470f5a857574342ebad9dd0e6a68e82768f95c061e895d4749e62`;
- QA AndroidTest: `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

No APK was installed or launched; no device control, portal interaction,
certificate operation, credential use or signature occurred.


## Finding G1-02 — network failure-detail visibility boundary

**Reproduction.** `ProfileHttpResult.Failure` was a public data class with a
public property/primary constructor of internal type `ProfileHttpFailureDetail`.
Kotlin 2.3 required `EXPOSED_PARAMETER_TYPE` and `EXPOSED_PROPERTY_TYPE`
suppressions. The detail contains fallback-only phase/write-state, while signing
consumers use the stable public `code` surface.

**Design check.** A synthetic Kotlin fixture showed that only making the data-class
constructor internal creates a separate generated-`copy()` visibility warning
(which is scheduled to become an error in a future language version). The chosen
ordinary-class shape compiled without visibility warnings; the Termux `kotlinc`
launcher still emitted its environmental Jansi native-library diagnostic but
returned exit 0. Repository search found no `Failure.copy`, destructuring, or
structural-equality dependency.

**Remediation.** `Failure` is now an ordinary class with an internal primary
constructor and `internal val detail`. Public `Failure(ProfileHttpFailure)` and
`code: ProfileHttpFailure` are preserved. The `EXPOSED_*` suppression is removed.
No retry, fallback, DNS, TLS, tunnel, timeout or signing path changed.

**TDD and fresh verification.** The new source/API policy test first failed on the
suppression/public data-class shape and then passed after the minimal visibility
change. Debug and QA Kotlin compilation emitted no exposed-type/copy-visibility
diagnostics. Focused transport/direct-first/tri-phase tests passed. Full Debug and
QA JVM suites each passed 509/509 with zero failures/errors/skips. `lintDebug`,
`lintQa`, `assembleDebug`, `assembleQa`, `assembleQaAndroidTest` passed
(`BUILD SUCCESSFUL`, 140 actionable tasks); lint has 0 errors and 27 warnings per
variant. Python passed 96 tests with one environmental hardlink skip. Go
test/vet/build, Android artifact verification and release fail-closed verification
all passed.

APK SHA-256:

- Debug: `a28a116087eead23c183bd54f4fabbc6e8c3d449a740c3f5fac6595c5bdab7fe`;
- QA: `e6e51ec7a92f072e806310937db1968016d04793ff8269344ea3ffaa2811dc0c`;
- QA AndroidTest: `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

No APK installation, app launch, device control, portal interaction, certificate,
credential or signing operation occurred.


## Finding G2-01 — release-registry invariant evidence repair

**Reproduction.** The runtime registry was already fail-closed for sensitive
capabilities: an `ENABLED` profile containing `SIGN`, `SELECT_CERTIFICATE`, or
`CLIENT_TLS_AUTH` is release-eligible only with `VERIFIED_E2E`, and `QA_ONLY` is
never release-active. The regression test intended to prove downgrade rejection did
not prove its claim: `replaceFirst` downgraded the first `VERIFIED_E2E` profile
(`unizar-tramitador`) but the assertion checked `junta-andalucia`, which was already
release-ineligible as `EXPERIMENTAL`. The test therefore passed even if that specific
downgrade was not the reason for rejection.

**Remediation.** No production policy changed. The test now derives the sensitive
capability set directly, asserts every built-in sensitive profile whose status is not
`VERIFIED_E2E` is absent from the release registry, and independently downgrades each
current `ENABLED / VERIFIED_E2E` sensitive profile to `VERIFIED_CONTRACT`. Each
mutated profile must disappear from release while remaining visible in QA with the
downgraded status. The coverage is catalog-driven rather than name/order-driven, so a
future sensitive non-E2E catalog entry is automatically included.

**Verification.** Focused Debug passed after the first correction. A complete
cross-stack gate on the unchanged production tree then passed: toolchain pin checks;
Debug 509/509 and QA 509/509; `lintDebug`/`lintQa`; Debug/QA/QA-AndroidTest builds;
Android artifact verification; release fail-closed; Python 96 tests with one
environmental hardlink skip; Go test/vet/build. APK hashes remained identical to
G1-02 because runtime sources were unchanged. The final additional catalog-wide
non-E2E assertion then passed focused Debug and QA; the complete final Debug and QA
JVM suites were rerun on the final test diff and each passed 509/509 with zero
failures, errors, or skips. No APK was installed or launched and no device,
portal, certificate, credential, signature, upload, payment, or submission action
occurred.


## Finding G2-02 — QA diagnostic journal clear boundary

**Reproduction.** `docs/test-plan.md` explicitly requires that logger `clear`
remove the journal. The application logger has two app-controlled journal layers in
QA: the bounded in-memory `SanitizedLogger` deque and
`filesDir/qa-navigation.log` via `QaDiagnosticFileSink`. The existing
`SanitizedLogger.clear()` removed only memory. The new integration regression first
ran against the unmodified production implementation and failed exactly at
`ApplicationSanitizedLoggerFactoryTest.kt:48`: after `logger.clear()`, the in-memory
export was empty but the QA file still contained the pre-clear `NETWORK_ERROR`
record. Repository search found no current production call site for
`sanitizedLogger.clear()`, so this is a dormant privacy/API-contract defect, not
evidence that a user-triggered clear already leaked retained diagnostics.

**Design and remediation.** The narrow subordinate design/plan extends the existing
`SanitizedLogSink` fun interface with a default no-op `clear()` while keeping
`emit(record)` as its sole abstract method. Existing lambda/SAM sinks therefore
remain compatible. `SanitizedLogger.clear()` clears memory and then best-effort
delegates to its sink. `QaDiagnosticFileSink.clear()` synchronously truncates the
app-private journal to zero bytes, and the QA composite sink propagates clear to the
file sink plus any mirror clear hook. The current Logcat mirror is a lambda with the
default no-op clear; no claim is made that the app can erase system Logcat history
or physically secure-erase flash blocks. Non-QA mode remains a no-op sink and still
creates no QA diagnostic file.

**TDD and verification.** RED was the focused Debug integration failure described
above. After the minimum production change, the complete
`ApplicationSanitizedLoggerFactoryTest` passed in Debug and QA. Fresh full gates on
the changed production tree then passed: toolchain pin checks; Debug 510/510 and QA
510/510 JVM tests with zero failures/errors/skips; `lintDebug` and `lintQa` with 0
errors / 27 warnings each; `assembleDebug`, `assembleQa`,
`assembleQaAndroidTest`; Android artifact verification; release signing fail-closed;
Python 96 tests with one environmental hardlink skip; Go test/vet/build. No relay
binary was retained.

APK SHA-256 after the remediation:

- Debug: `079506fc28ee108c37b2a5bb929bfe5214dda767284fe8c9dac04e8e811adbec`;
- QA: `c253e07b0cb94321e31769dc96dc1fd7f142f8a907884ecc7617254d0cb53e85`;
- QA AndroidTest: `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

No APK was installed or launched; no ADB/device control, portal interaction,
certificate or credential use, real signature, upload, payment, or administrative
submission occurred. Go race remains an external supported-Linux CI gate and was not
claimed on Termux.
