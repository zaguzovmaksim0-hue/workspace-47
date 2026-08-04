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

1. Start a fresh architecture/lifecycle/concurrency/recovery pass, prioritizing
   browser post-dispose callbacks, stale asynchronous completions, renderer/profile
   transitions and ownership/cancellation boundaries.
2. Re-open signing/certificate/logging/storage only for a newly reproducible
   excess-lifetime, persistence, disclosure or failure-path defect; otherwise move to
   an independent UX/accessibility or CI/supply-chain pass.

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


## Finding G3-01 — CAdES pre-sign capture backing-buffer lifetime

**Reproduction.** `CadesDetachedCodec.CapturingContentSigner.close()` attempted to
clear captured signed attributes with `output.toByteArray().fill(0)` followed by
`output.reset()`. `ByteArrayOutputStream.toByteArray()` returns a copy and `reset()`
only resets the logical count. A standalone JVM probe using a subclass that exposes
protected `buf` wrote a canary, executed the exact old sequence, and reported
`retained=true`. Existing CAdES functional tests passed before mutation, confirming
that the gap was memory hygiene rather than signature correctness.

**TDD / debugging.** A new source-policy regression first failed on the exact old
`output.toByteArray().fill(0)` pattern. The first production implementation used a
clearing stream whose `close()` zeroed `buf`; that implementation was rejected when
focused `LocalCadesDetachedAdapterTest` produced two failures because BouncyCastle
closes the supplied output stream during generation and the captured attributes were
therefore erased before `signedBytes()` was read. Repository comparison showed the
established sensitive-stream pattern in `Pkcs12Loader`, `JuntaTriPhaseCodec`, and
`ProfileHttpTransport`: an explicit `clear()` method, not an overridden stream
`close()`. The corrected implementation preserves inherited close behavior and calls
`output.clear()` only from `CapturingContentSigner.close()`.

**Remediation.** The capturer now owns `ClearingByteArrayOutputStream`; its explicit
`clear()` zeroes the actual protected `buf` and resets the logical length. The
intentional `signedBytes()` copy remains owned by `PreSignResult` and its existing
lifecycle. No CAdES algorithm, signed attribute, certificate, provider, portal,
network/TLS/WebView, release, or public API behavior changed. This is managed-heap
best-effort zeroization, not a physical RAM/JVM-copy secure-erasure claim.

**Fresh verification.** Corrected focused source-policy plus CAdES/LocalSignature
Debug+QA tests passed. The final full Android gate passed separately: toolchain pin
checks; Debug 510/510 and QA 510/510 with zero failures/errors/skips; Debug/QA/
QA-AndroidTest assemble (`BUILD SUCCESSFUL`, 127 actionable tasks). `lintDebug` and
`lintQa` passed in a separate invocation (`BUILD SUCCESSFUL`, 0 errors / 27 warnings
per variant). Android artifact verification and release signing fail-closed passed.
Python passed 97 tests with one environmental hardlink skip. Go test/vet/build
passed and the generated relay binary was removed. An earlier all-in-one verification
wrapper hit its external 1800-second job timeout after unit/assemble tasks while lint
was still analyzing; no test failure was attributed to that wrapper timeout, and all
component gates were rerun/completed explicitly as described above.

APK SHA-256:

- Debug: `f8d819a0de57e40ad7e1575a2c44ff8577d9b70a55ff5b53942a2fd3d2f1227e`;
- QA: `96331ee7bddd782981a5b4900e906e27887ddc0dfd28698e62c17c38cbdb7f1b`;
- QA AndroidTest: `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

No APK was installed or launched; no ADB/device control, portal interaction,
credential/certificate use, real signing, upload, payment, or administrative
submission occurred. Next trust-boundary lead: continue XAdES/final-signature and
certificate temporary-copy lifetime review, then move to an independent
architecture/lifecycle or UX/accessibility pass if no reproducible defect remains.

## Finding G4-01 — XAdES serialization/canonicalization backing-buffer lifetime

**Reproduction.** `XadesDetachedCodec.serialize()` and `canonicalize()` used
ordinary `ByteArrayOutputStream` instances and returned `toByteArray()` copies.
Closing an ordinary byte-array stream is a no-op for its protected backing `buf`.
A standalone JVM subclass probe wrote an XML canary, called `toByteArray()` and
`close()`, and observed `returnedHasCanary=true` and
`backingHasCanaryAfterClose=true`. The two XAdES source sites matched that ownership
pattern. Depending on the helper call, the redundant backing copy could contain the
serialized unsigned/final XAdES document or canonicalized document content,
SignedInfo, SignedProperties or KeyInfo until garbage collection.

**TDD and remediation.** A narrow source-policy regression was added first and
observed RED on the ordinary-stream patterns. The production helpers now allocate a
private `ClearingByteArrayOutputStream`, obtain only the intentional returned copy,
and execute `output.clear()` in `finally`; `clear()` zeros protected `buf` and then
resets it. Inherited stream close semantics remain unchanged. No XAdES algorithm,
namespace, canonicalization method, digest, certificate chain, profile, callback,
network/TLS/WebView or release policy changed. This is best-effort managed-heap
hygiene, not a physical-memory secure-erasure claim and not a claim about internal
copies owned by XML/JCA implementations.

During the transition from RED to production mutation, the pre-mutation guard found
the exact planned XAdES source diff already present before the guarded patch script
could write it; the script stopped on its old-source assertion. A process/stability
check found no active workspace mutator, the file hash remained stable, and the diff
matched the subordinate design with no unrelated source edits. The origin of that
transient/in-flight write was not established, so no stronger attribution is made.
The required RED had already been captured against the old source before this state
appeared.

**Fresh verification.** The source-policy regression passed. A forced focused XAdES
Debug+QA rerun executed all 60 Gradle tasks and passed. The full Android gate then
passed toolchain pin checks, Debug 510/510 and QA 510/510 JVM tests with zero
failures/errors/skips, plus `assembleDebug`, `assembleQa`, and
`assembleQaAndroidTest` (`BUILD SUCCESSFUL`, 127 actionable tasks). Separate
`lintDebug`/`lintQa` passed (`BUILD SUCCESSFUL`, 0 errors / 27 warnings per variant).
Python passed 98 tests with one environmental hardlink skip. Android artifact
verification and release signing fail-closed passed. Go `test ./... -count=1`,
`go vet ./...`, and `go build ./cmd/ws024-relay` passed; the generated relay binary
was removed.

APK SHA-256:

- Debug: `6a6b6e72006048ea9191de2b4b509cda21bb9f60b226386afa54ea872e753139`;
- QA: `20740737b0e977e263192367de217f8f03262f59e4ba972e2a233da08b5e8810`;
- QA AndroidTest: `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

No APK was installed or launched; no ADB/device control, portal interaction,
credential/certificate use, real signing, upload, payment or administrative
submission occurred. Go race remains an external supported-Linux CI gate. With the
XAdES application-owned stream-copy defect closed, the next autonomous pass should
move to a fresh architecture/lifecycle or UX/accessibility audit unless another
reproducible signing-copy excess-lifetime or persistence boundary is found.


## Finding G4-02 — persisted certificate-unlock threat-model reconciliation

**Reproduction.** Runtime behavior introduced by commit
`32b27caf95c039020dd8512018c0875ed483c291` intentionally permits automatic
certificate re-unlock for no longer than the original 24-hour window after one
successful manual password entry. The unlock password is persisted only as
authenticated AES-256-GCM ciphertext in `noBackupFilesDir`, with the AES key owned by
Android Keystore; PKCS#12 bytes and the private-key object are not persisted by this
feature. The pre-existing T5 text in `docs/threat-model.md` still stated that
lifecycle/process death locks the identity, which contradicted the implemented
process-recreation and memory-pressure recovery path.

**Remediation.** This milestone changes documentation and its policy regression only.
The threat model now names the encrypted unlock record and Keystore key as assets,
shows the bounded recovery trust boundary, records the original-expiry/non-extension
rule, exact clearing conditions, process-death/memory-pressure semantics and residual
risk, and removes the obsolete assertion that process death guarantees persistent
locking. No runtime, resource, certificate-cache implementation, signing, profile,
network, WebView, build or dependency behavior changed.

**TDD and verification.** The focused documentation-policy test was first observed
RED against the stale T5 text and GREEN after reconciliation. Fresh complete Python
discovery passed 99 tests with zero failures/errors and one environmental hardlink
skip. Fresh task-scoped lifecycle verification then passed the three named
`CertificateSession`/`CertificateViewModel` regressions in both Debug and QA with
`--no-daemon --rerun-tasks`: `BUILD SUCCESSFUL`, 60 actionable tasks, all 60
executed. Two earlier retry invocations were not accepted as product evidence: the
`--tests` options were placed after both Gradle tasks and therefore broadened Debug
discovery while duplicate Gradle jobs overlapped; those runs failed at test-class
execution. After duplicate jobs were removed and the exact previously successful
task-scoped command was restored, the focused lifecycle gate passed.

No APK was installed or launched; no ADB/device control, portal interaction,
credential/certificate use, real signing, upload, payment or administrative
submission occurred. Physical AEAT F-03 remains a separate manual acceptance gate,
and Go race remains an external supported-Linux CI gate.

## Finding G6-01 — public inventory deadline cleanup

**Reproduction.** The fresh Python gate ran 99 tests and produced one failure plus
one environmental hardlink skip. The failure was
`DeadlineTest.test_one_blocking_read_is_cancelled_at_the_wall_clock_deadline`:
a blocking body-read worker had not observed `connection.close()` when the caller
returned the deadline error. Five immediate focused reruns passed, demonstrating
scheduler sensitivity. A deterministic probe then supplied an already expired
post-start deadline to `_run_with_deadline()` and observed
`HTTPS request deadline exceeded` with `on_timeout_called=False`.

**Root cause and remediation.** `_run_with_deadline()` started its worker before
calling `_remaining_seconds(deadline)`. If that calculation itself raised, control
never reached the existing `worker.is_alive()` cleanup branch. The helper now uses
one private best-effort cleanup function both when post-start deadline calculation
raises and when a timed join leaves the worker alive. Cleanup exceptions remain
suppressed; no deadline, retry, redirect, DNS, address, TLS, portal or catalog
policy changed.

**TDD and verification.** The deterministic regression first failed because the
cleanup event remained unset. After the minimum helper change, it passed. The
complete `DeadlineTest` passed 3/3; the new regression plus the original blocking
read regression passed ten sequential repetitions; complete Python discovery
passed 100 tests with zero failures/errors and one environmental hardlink skip.
`py_compile` and `git diff --check` passed. The pre-existing G5-01 Android/WebView
work remained preserved and unstaged during this independent Python milestone.

No APK installation/launch, device control, portal request, credential/certificate
use, real signing, upload, payment or administrative submission occurred.

## Finding G5-01 — stale WebView callback ownership lease

**Reproduction.** `BrowserScreen` already guarded progress delivery and renderer
recovery with exact `WebView` identity, but ordinary `JuntaWebViewClient` callbacks
and dedicated `ClientAuthWebViewClient` callbacks had no equivalent ownership
lease. A released/replaced view could therefore deliver obsolete navigation,
Afirma, page-state, browser-error or renderer callbacks into state owned by the
new active view. The Client TLS handler could abandon its one-shot grant, but that
did not prevent stale UI/native callback delivery. Two focused RED jobs failed at
compile time because neither client exposed an active-view predicate, proving the
missing dependency before production mutation.

**Remediation.** Both clients now accept an `isActiveWebView` predicate;
`BrowserScreen` binds it to `webViewRef.get() === candidate`. Predicate exceptions
fail closed. Stale normal and Client TLS navigation is consumed; stale page,
Afirma, state, error and renderer-recovery callbacks are suppressed. Security-
critical platform actions remain fail closed: SSL is cancelled, safe browsing
returns to safety, stale Client TLS requests are ignored, and the Client TLS
request handler is abandoned so process-scoped certificate preferences are cleared
through the existing one-shot lifecycle. No origin/path allowlist, TLS trust,
certificate selection, signing, profile/catalog, release or dependency policy
changed.

**Verification.** Focused stale-callback Debug GREEN passed, followed by complete
`JuntaWebViewClientTest`, `ClientAuthWebViewClientTest` and renderer regressions in
both Debug and QA. A fresh full rerun passed Debug 513/513 and QA 513/513 with zero
failures/errors/skips (`BUILD SUCCESSFUL`, 60/60 tasks executed). The previously
completed final-tree gates also passed: toolchain pin checks; Debug/QA/QA-
AndroidTest assembly (`BUILD SUCCESSFUL`, 110/110 tasks); `lintDebug` and `lintQa`
with zero errors and 27 warnings per variant (`BUILD SUCCESSFUL`, 55/55 tasks);
Android artifact verification; release signing fail-closed with no residual release
APK; Go test/vet/build with the generated relay binary removed; and final Python
discovery after G6-01 at 100 tests with zero failures/errors and one environmental
hardlink skip. Exact-scope, whitespace, sensitive-content and unsafe WebView/TLS/
backup scans passed. The first local scan wrapper stopped before scanning because
Android `/tmp` was not writable by the Termux app user; the same scan reran under
`$TMPDIR` and passed.

APK SHA-256:

- Debug: `ee01227e286ab371a24d326a1a414f822e7e975b80892c6e2266ba866aaf3365`;
- QA: `d4eb3e09b4430e3a6a0007064577943195a1d8c9bfa02335aa33ab0ec9820dae`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK was installed or launched; no ADB/device control, portal interaction,
credential/certificate material use, real signing, upload, payment or
administrative submission occurred. Physical AEAT F-03 and supported-Linux Go race
remain external gates.
