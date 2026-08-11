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

## Finding G6-02 — stale global browser-data-clear completion ownership

**Reproduction.** `BrowserScreen` started process-wide WebView-data deletion through
`SiteDataCleaner.clearAllConfirmed()`, then posted its asynchronous completion to the
main handler. The callback captured the request profile URL but read `webViewRef` only
when the runnable executed. Because the reference survives recomposition and is
replaced after profile-keyed disposal, a delayed completion from an old profile could
set current UI result state and reload the old profile URL on a newly active WebView.
The source-policy RED job rejected the missing lease at
`BrowserSecurityRegressionTest.kt:249`; a separate behavioral RED failed to compile
because the planned lease type did not yet exist.

**Remediation.** A small generic `BrowserDataClearCompletionLease` now gives each
confirmed clear a unique request bound to its initiating WebView. A later request
supersedes an earlier one; profile change/disposal invalidates pending ownership; and
completion is consumed atomically once. Successful completion reloads only when the
initiating owner is non-null and still identical to the active `webViewRef`. Stale
completion is ignored while the already-started global deletion is neither cancelled
nor represented as rolled back. Callback work remains marshalled through the main
handler. No cookie/storage deletion scope, URL/origin policy, WebView TLS, Client TLS,
certificate, signing, profile/catalog, release or dependency policy changed.

**TDD and verification.** Source-policy RED job
`job_20260804_181217_616624bb` failed as expected after 30/30 tasks. Behavioral RED
job `job_20260804_181902_691aaf9e` failed as expected before the lease existed after
28/28 tasks. Minimum GREEN job `job_20260804_182201_7546f3be` passed the helper and
integration-policy regressions; focused Debug+QA job
`job_20260804_182900_2034c491` passed the lease, browser security, BrowserScreen and
SiteDataCleaner suites with 60/60 tasks. Fresh full Android job
`job_20260804_184100_327d7ebe` passed pin checks, Debug 517/517 and QA 517/517 JVM
tests with zero failures/errors/skips, and Debug/QA/QA-AndroidTest assembly
(`BUILD SUCCESSFUL`, 127/127 tasks). Forced lint passed 55/55 tasks with zero errors
and 27 warnings per variant; the existing `ProfileHttpCallPhaseTracker` parameter-name
warning is outside this diff. Python passed 100 tests with one environmental hardlink
skip. Android artifact verification and release-signing fail-closed passed with no
release APK. Go test/vet/build passed and the generated relay binary was removed.
Whitespace, exact-scope, sensitive-content, personal-data and unsafe WebView/TLS/
backup scans passed; an initial local scan wrapper stopped before scanning because of
shell quoting and was rerun successfully with simplified expressions.

APK SHA-256:

- Debug: `e02c14c9383b480a7ca9792136737e0e1b71932ae7b8bd517459d76eab43702f`;
- QA: `e14387a60d88127762ba552d7b34dcd39384cc6f36757da21dae0488d13c2742`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK was installed or launched; no ADB/device control, portal interaction,
credential/certificate material use, real signature, upload, payment or
administrative submission occurred. Physical AEAT F-03 and supported-Linux Go race
remain external gates.

## Finding G7-01 — stale WebMessageBridge compatibility-error delivery

**Reproduction.** `BrowserScreen` posts WebMessageBridge listener/document-start
attachment failure through the initiating WebView, but the posted runnable previously
set `compatibilityError` without verifying that this WebView was still active. Because
`webViewRef` survives selected-profile changes while the profile-keyed disposal path
can destroy the old WebView and install a replacement, a queued runnable from the old
instance could publish its local attachment failure into the current browser UI.
Adjacent page-progress and WebViewClient callbacks already enforce exact active-WebView
identity.

**Remediation.** The existing WebView-posted delivery now sets compatibility state only
when `webViewRef.get() === webView`. A released, destroyed or replaced WebView cannot
mutate the later UI; a failure from the current active WebView remains visible. No
bridge attachment API, origin rules, script content, profile/catalog status, WebView
TLS, Client TLS, certificate, signing, release or dependency policy changed.

**TDD and verification.** Source-policy RED job
`job_20260804_192114_5aab8616` failed as expected at
`BrowserSecurityRegressionTest.kt:280` after 30/30 tasks. Focused GREEN job
`job_20260804_192637_31b56bc1` passed after the minimum identity guard with 30/30
tasks. Focused Debug+QA job `job_20260804_193202_5d9e8290` passed
`BrowserSecurityRegressionTest` and `BrowserScreenTest` with 60/60 tasks. Fresh full
Android job `job_20260804_193946_0b04588e` passed pin checks, Debug 518/518 and QA
518/518 JVM tests with zero failures/errors/skips, and Debug/QA/QA-AndroidTest
assembly (`BUILD SUCCESSFUL`, 127/127 tasks). Forced lint job
`job_20260804_195110_a0e5e68a` passed 55/55 tasks with zero errors and 27 warnings per
variant. Python passed 100 tests with one environmental hardlink skip. Android artifact
verification and release-signing fail-closed passed with no release APK. Go
test/vet/build passed and the generated relay binary was removed. Complete-diff
whitespace, exact-scope, sensitive-content, personal-data and unsafe WebView/TLS/backup
scans passed.

APK SHA-256:

- Debug: `6c97ea151ffe4bfc8c1a0b53ac6657f03760a880d78e62dbec2284da72f7edc2`;
- QA: `875b38927595c7f4b153d79f33e09395825ffeee38c1829e2d0333bcc85c233a`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK was installed or launched; no ADB/device control, portal interaction,
credential/certificate material use, real signature, upload, payment or
administrative submission occurred. Physical AEAT F-03 and supported-Linux Go race
remain external gates.

## Finding G7-02 — certificate unlock invalidation linearization

**Reproduction.** Explicit certificate-cache invalidation was not linearizable with an
already-started blocking store. The corrected deterministic cache regression first
failed in `job_20260804_203419_2ce1ec18`: after `clear()` completed while the physical
writer remained blocked, the late writer recreated the record and `store.await()`
returned `true`. A separate ViewModel regression was freshly reconfirmed RED in
`job_20260804_215510_e95834c9`: while cache persistence was suspended,
`CertificateSession.identityForSigning()` already exposed the new identity
(`expected null, but was UnlockedIdentity`).

**Remediation.** `EncryptedCertificateUnlockCache` now owns an `AtomicLong`
invalidation generation. Every store captures the generation before entering its IO
work; `clear()` increments it before deleting storage. A successful physical write
must still match the captured generation or the late record is cleared and the store
returns `false`. `CertificateViewModel.unlock()` now awaits cache persistence and then
checks coroutine cancellation before publishing `session.unlock`; the matching
`Unlocked` state follows immediately with no suspension between session and UI commit.
Retention duration, AES-GCM/Keystore policy, cache payload, password zeroization,
certificate selection, signing, WebView, portal and release policy are unchanged. The
existing threat-model contract already required manual lock/session clear to eliminate
the persisted record, so no threat-model wording change was required.

**TDD and fresh verification.** Both exact regressions passed together in
`job_20260804_220006_94a6661d` (`BUILD SUCCESSFUL`, 30/30 tasks). The relevant
`CertificateUnlockCacheTest`, `CertificateViewModelTest` and
`CertificateSessionTest` suites passed Debug+QA in `job_20260804_220546_1789baad`
(`BUILD SUCCESSFUL`, 60/60 tasks). Full Android job
`job_20260804_221205_041a15fa` passed pin checks, Debug 520/520 and QA 520/520 JVM
tests with zero failures/errors/skips, plus Debug/QA/QA-AndroidTest assembly
(`BUILD SUCCESSFUL`, 127/127 tasks). Forced lint job
`job_20260805_103210_50f11051` passed 55/55 tasks with 0 errors and 27 warnings per
variant. Python passed 100 tests with one environmental hardlink skip. Android artifact
verification and release-signing fail-closed passed; no release APK remained. Go
test/vet/build passed and the generated relay binary was removed. `git diff --check`,
exact dirty-scope review, high-confidence secret scan and unsafe WebView/TLS/backup
added-line scan passed before evidence mutation.

APK SHA-256:

- Debug: `b2d414f4a74eb3f42dbf4cb6c63a4403e82a3e199b5b4fcd2d3c111a62345547`;
- QA: `833081836caf0feb5060f9daee90ce4a0ee00646fb136006c8181aba1d1a376e`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK was installed or launched; no ADB/device control, portal interaction,
credential/certificate material use, real signature, upload, payment or administrative
submission occurred. Physical AEAT F-03 and supported-Linux Go race remain external
acceptance gates.

## Finding G8-01 — cancelled certificate selection URI-permission cleanup

**Reproduction.** `CertificateRepository.select()` acquires a persistable read permission
before the suspending reference-store write. The deterministic regression
`cancelledSelectionBeforeReferenceCommitReleasesNewPermission` blocked the store before it
mutated its reference, then cancelled the selection. On unchanged production, RED job
`job_20260805_105940_21cd09b2` failed after 30/30 tasks: the XML reported
`expected:<[content://documents/cancelled-selection]> but was:<[]>` for the released-permission
list, while no reference had been committed. The app therefore retained durable provider access
to a cancelled, uncommitted certificate document.

**Remediation.** The existing write-failure rollback now also runs for
`CancellationException`: when the selected URI differs from the previously persisted URI,
`releaseQuietly(uri)` runs before the original cancellation is rethrown. Same-URI selections do
not release their pre-existing permission; ordinary failures and successful replacement order
are unchanged. The fix is intentionally limited to cancellation while the reference write has
not committed; it makes no claim about arbitrary hostile store implementations that commit and
then throw cancellation.

**TDD and fresh verification.** Exact GREEN job `job_20260805_110609_9d68b59d` passed
(`BUILD SUCCESSFUL`, 30/30 tasks). Complete `CertificateRepositoryTest` passed Debug+QA in
`job_20260805_111233_b8e16ac4`. Full Android job `job_20260805_112149_c6983e7c` passed
`verifyResolvedCoreVersion`, `verifyPortableAapt2Configuration`, Debug 521/521 and QA 521/521
JVM tests with zero failures/errors/skips, and Debug/QA/QA-AndroidTest assembly. Forced lint
`job_20260805_113412_665bf0a2` passed 55/55 tasks with 0 errors / 27 warnings per variant.
Python passed 100 tests with one environmental hardlink skip. Android artifact verification and
release-signing fail-closed passed with no release APK. Go test/vet/build passed and the relay
binary was removed. Pre-evidence exact-scope, whitespace, secret, personal-data and unsafe
WebView/TLS/backup scans passed.

APK SHA-256:

- Debug: `6ceca12ed1254d6627c89406875bb57669c2ac64ae8b4852b4352cda7ed673d7`;
- QA: `0e4789a79f4d0d4849825605f768dc677a1e7d844bdce449d6e952ed5d2b9096`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK was installed or launched; no ADB/device control, portal interaction,
credential/certificate material use, real signature, upload, payment or administrative
submission occurred. Physical AEAT F-03 and supported-Linux Go race remain external gates.

## Finding G8-02 — cancelled certificate unlock stale-reference write

**Reproduction.** `CertificateRepository.unlock()` performs blocking document/PKCS#12 loading
inside the IO context and then persists a safe certificate summary into the selected reference.
Cancellation is not observed while that blocking loader is running. A deterministic regression
blocked a valid synthetic PKCS#12 read, cancelled the unlock, released the read, and used a
non-suspending reference store to observe the first side effect. On unchanged production, RED
job `job_20260805_115511_14d81020` failed exactly because `store.writes` was non-empty
(tests=1, failures=1, errors=0): the cancelled unlock initiated a stale old-reference summary
write after blocking load returned.

**Remediation.** `CertificateRepository.unlock()` now calls
`currentCoroutineContext().ensureActive()` immediately after blocking certificate loading and
before any successful-result reference-summary persistence. Cancellation therefore remains the
original coroutine cancellation and is observed before a stale write can begin. Non-cancelled
success, certificate validation/error mapping, summary contents, selection, password/cache,
signing, WebView/network/TLS, portal-profile, release and dependency behavior are unchanged.
The existing threat model already treats the selected certificate reference and coroutine/session
lifecycle as protected state; no new asset or trust boundary was introduced, so threat-model
wording is unchanged.

**TDD and fresh verification.** Exact GREEN job `job_20260805_120103_a7e93b2b` passed; complete
`CertificateRepositoryTest` Debug+QA job `job_20260805_120546_d5ea1fd2` passed. Full Android
job `job_20260805_121301_ef67a622` passed pin checks, Debug 522/522 and QA 522/522 JVM tests
with zero failures/errors/skips, Debug/QA/QA-AndroidTest assembly, and 127/127 tasks. Forced lint
`job_20260805_123048_ba6c0459` passed 55/55 tasks with 0 errors / 27 warnings per variant.
Python `job_20260805_123629_7317943c` passed 100 tests with one environmental hardlink skip.
Android artifact verification `job_20260805_123802_8de46e94` passed. Release fail-closed
`job_20260805_124233_27c083ee` passed with zero release APK. Go test/vet/build
`job_20260805_123929_9870b5cf` passed and the generated relay binary was removed. Final
whitespace, exact-scope, high-confidence secret, personal/certificate-literal and unsafe
WebView/TLS/backup added-line scans passed. The earlier scan-wrapper command failure was traced
to shell quoting and rerun successfully with a Python regex wrapper; it was not a product failure.

APK SHA-256:

- Debug: `5f7ccda5ed3aafc1800f8ec2e6190ff263f5c07d3abb01f67ced74104c863fe5`;
- QA: `f89f4f5a8009ced7cb5eb97777d7a6e6ac99a4416908e45dd3fb303328d46146`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK was installed or launched; no ADB/device control, portal interaction,
credential/certificate material use, real signature, upload, payment or administrative
submission occurred. Physical AEAT F-03 and supported-Linux Go race remain external gates.

## Finding G9-01 — autonomous branch CI push coverage

**Reproduction.** The autonomous execution contract requires every completed milestone to be
pushed to `agent/workspace-47-autonomous-20260803`, but both GitHub Actions workflows limited
`push.branches` to `main` and `feature/**`. A new policy regression first ran against unchanged
workflow files. RED job `job_20260805_132116_bf00a316` failed exactly because `ci.yml` lacked
`      - agent/**`; no workflow production content had changed before that run.

**Remediation.** The ordinary CI and security workflow push allowlists now each include only one
additional namespace, `agent/**`, next to the existing `main` and `feature/**` entries. The policy
suite requires all three entries in both workflows. Workflow permissions remain `contents: read`;
checkout retains `persist-credentials: false`; all action references remain pinned to the same
40-character SHAs; jobs, commands, schedules, concurrency, timeouts, dependency pins, credentials
and release-signing policy are unchanged. No `pull_request_target`, write permission, secret,
artifact upload or broad all-branch trigger was added.

**TDD and fresh verification.** GREEN/policy/Python job `job_20260805_132135_fe5674af` passed the
exact regression, complete `CiPolicyTest` 19/19 and Python discovery 101 tests with zero
failures/errors and one environmental hardlink skip. Full Android job
`job_20260805_132209_da78308f` passed pin/AAPT2 checks, Debug 522/522 and QA 522/522 JVM tests with
zero failures/errors/skips, all Debug/QA/QA-AndroidTest assemblies and 127/127 tasks. Forced lint
job `job_20260805_133103_ecb4c60e` passed 55/55 tasks with zero errors and 27 warnings per variant.
Go job `job_20260805_132223_437dc850` passed `go test ./... -count=1`, `go vet ./...` and
`go build ./cmd/ws024-relay`; the generated relay binary was removed. Artifact job
`job_20260805_133111_c4ee32c9` passed alignment, signature, manifest and forbidden-canary checks.
Release job `job_20260805_133814_d06bfb4e` completed with exit 0, reported
`Release signing fail-closed verification passed`, and confirmed `release_apk_count=0`.
Pre-evidence scan job `job_20260805_132521_00581e82` confirmed the exact five-file pre-evidence
scope, no high-confidence secrets or personal identifiers, no workflow write-all permission,
`pull_request_target` or `persist-credentials: true`, and unchanged immutable pins for all 7 CI and
5 security action uses.

APK SHA-256 remained:

- Debug: `5f7ccda5ed3aafc1800f8ec2e6190ff263f5c07d3abb01f67ced74104c863fe5`;
- QA: `f89f4f5a8009ced7cb5eb97777d7a6e6ac99a4416908e45dd3fb303328d46146`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

Local evidence verifies workflow policy and every command referenced by the changed trigger scope;
it does not by itself claim that GitHub executed a server-side workflow run. No APK was installed
or launched; no ADB/device control, portal interaction, credential/certificate material use, real
signature, upload, payment or administrative submission occurred. Physical AEAT F-03 and the
supported-Linux Go race gate remain external.
## Finding G10-01 — browser notice accessibility announcement

**Reproduction.** `BrowserNoticeBanner` rendered dynamic portal/network failures and the optional
retry action without a Compose live-region property. The focused regression was added before
production mutation. RED job `job_20260805_191209_05c712cb` executed 30/30 tasks and failed its
single test exactly because the node tagged `browser_notice` did not contain
`LiveRegion = 'Assertive'`; the XML reported one test, one failure, zero errors/skips.

**Remediation.** The existing banner `Surface` now adds only
`.semantics { liveRegion = LiveRegionMode.Assertive }`. Existing message text, visual hierarchy,
retry behavior, test tag, 48 dp retry touch target and descendant semantics remain unchanged. The
change does not request or transfer focus and does not alter WebView lifecycle, navigation,
network/TLS, Client TLS, certificate, signing, portal-profile, dependency, release or workflow
behavior.

**Verification.** Exact GREEN job `job_20260805_191610_b601f0fe` passed 30/30 tasks. Focused
Debug+QA job `job_20260805_191930_64b43357` passed the complete two-test class in each variant and
60/60 tasks. Full Android job `job_20260805_192433_0a9882e0` passed pin/AAPT2 checks, Debug 523/523
and QA 523/523 JVM tests with zero failures/errors/skips, all Debug/QA/QA-AndroidTest assemblies
and 127/127 tasks. Forced lint job `job_20260805_193250_fbcb35e0` passed 55/55 tasks with zero
errors and 27 warnings per variant. Python job `job_20260805_192440_7b0b9c8e` passed 101 tests with
one environmental hardlink skip. Go job `job_20260805_192506_91c193e3` passed test/vet/build and
removed the generated relay binary. Artifact job `job_20260805_193317_e2ccbe58` passed alignment,
signature, manifest and forbidden-canary checks. Release job `job_20260805_193923_b5015fe3`
passed the expected fail-closed gate and confirmed `release_apk_count=0`.

APK SHA-256:

- Debug: `340114fc16b6603bb972d9f409fa4f0d3b4aa1a0eeb8ec0a177ffbea530788f9`;
- QA: `d951d33a6f616242348a16a3ff3ae9017165a480253cffd8848a8e4bd4cc8061`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

Robolectric verifies the semantics property, not actual announcement timing or assistive-technology
interaction on hardware. Physical TalkBack behavior remains an explicit manual acceptance gate.
No APK was installed or launched; no ADB/device control, portal interaction, credential or
certificate material use, real signature, upload, payment or administrative submission occurred.

## Finding G11-01 — WebMessage bridge release ownership

**Reproduction.** `BrowserScreen` kept the normal WebView's
`WebMessageBridgeAttachment` in an unowned atomic reference. `AndroidView.onRelease`
destroyed the released WebView without closing that attachment. A temporary removal
and later recreation of the AndroidView could therefore leave the old WebMessage
listener, document-start script and pending MiniApplet reply registry alive, then
replace the only attachment reference. The pure ownership regression first failed to
compile because no owner-bound lease existed: RED job
`job_20260805_195612_9b1c5899`, 28/28 executed tasks, exact unresolved reference
`BrowserOwnedResourceLease`. The integration regression then ran against unchanged
`BrowserScreen`; fresh XML read job `job_20260805_201142_349e55bb` reported 15 tests,
one failure, zero errors/skips, exactly because the bridge was not bound to the exact
WebView owner.

**Remediation.** A small atomic `BrowserOwnedResourceLease` now binds one
`AutoCloseable` resource to one exact owner identity. Replacement installs the new
binding and closes the superseded resource; exact-owner release clears and closes it
once; stale-owner release cannot close a replacement; full close clears whichever
binding remains. `BrowserScreen` uses the lease as the sole normal bridge lifecycle
holder. Attachment creation binds `(webView, attachment)`, navigation invalidation
addresses only the current attachment, renderer/Client-TLS/disposal paths close the
current attachment, and `AndroidView.onRelease` releases the exact owner before
stopping and destroying the WebView. WebMessage payloads, JavaScript shim, origin/path
policy, WebView TLS, Client TLS grant, certificate, signing, profile/catalog, release,
dependency and UI behavior are unchanged.

**TDD and fresh verification.** Focused Debug GREEN read job
`job_20260805_202100_6c1c3977` passed the lease and browser-security regressions
(16/16 tests, 30/30 tasks). Focused Debug+QA job
`job_20260805_202539_f1cc3955` passed 16/16 selected tests per variant and 60/60
tasks. Full Android job `job_20260805_203513_76ad0a12` passed resolved-core,
portable-AAPT2 and runtime-lock gates, Debug 525/525 and QA 525/525 JVM tests with
zero failures/errors/skips, all Debug/QA/QA-AndroidTest assemblies and 128/128 tasks.
Lint job `job_20260805_204136_e5c97e7b` passed 55/55 tasks with zero errors and 27
warnings per variant. Python/Go result job `job_20260805_202651_47c08720` confirmed
Python 101 tests with one environmental hardlink skip and Go test/vet/build PASS.
Artifact result job `job_20260805_203536_e7dd25ed` passed alignment, signature,
manifest and forbidden-canary checks. Release job
`job_20260805_203636_635bafd7` passed the expected private-signing rejection;
`job_20260805_204226_28765586` confirmed zero release APKs and removed the generated
relay binary. Exact-scope, whitespace, sensitive-content and unsafe WebView/TLS scans
passed in `job_20260805_204243_51a14b98`.

APK SHA-256:

- Debug: `6bf8e4722fe865b1137a7a4498bc824b83e4413ca9b9dd4c8c8e64414703e195`;
- QA: `3a263176016595ec449bbaab3ee352c7a674bf79c48f5d9f0e954efa06aa8f37`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

The existing threat model already treats WebView/MiniApplet lifecycle and cancellation
as protected boundaries; this milestone enforces that boundary without adding an asset
or trust edge, so threat-model wording is unchanged. No APK was installed or launched;
no ADB/device control, portal interaction, credential/certificate material use, real
signature, upload, payment or administrative submission occurred. Physical AEAT F-03,
physical TalkBack/visual validation and supported-Linux Go race remain external gates.

## Finding G12-01 — stale WebView network-diagnostic ownership

**Reproduction.** The exact active-WebView predicate already guarded navigation, page
lifecycle, error UI and renderer callbacks in `JuntaWebViewClient`, but
`shouldInterceptRequest()` recorded every main-frame request without checking that the
callback owner was still active. A released/replaced WebView could therefore append
obsolete sanitized host/method/path-hash metadata to process/QA diagnostics even though
the interception result remained `null`. RED job `job_20260805_205546_7e6ca54a`
executed 30/30 tasks and failed the single new regression. XML read
`job_20260805_205837_885abec9` confirmed one test, one failure, zero errors/skips and
the exact unexpected stale record:
`event=NETWORK_REQUEST host=ws072.juntadeandalucia.es method=POST`.

**Remediation.** `shouldInterceptRequest()` now returns its unchanged `null` result
before logging whenever `!isCurrentWebView(view)`. Active main-frame logging and
subframe behavior are unchanged. The existing `isCurrentWebView` helper also makes an
owner-predicate exception fail closed for diagnostics. Request interception,
navigation, SSL/Safe Browsing rejection, origin/path policy, DNS/TLS/Client TLS,
cookies, bridge, certificate, signing, portal profiles/catalog, release and dependency
policy are unchanged.

**TDD and fresh verification.** Exact GREEN job `job_20260805_205906_3890a3e5`
passed 30/30 tasks. Focused Debug+QA job `job_20260805_210208_77f0117c` passed the
complete `JuntaWebViewClientTest` 18/18 in each variant and 60/60 tasks, preserving the
existing active-request logging positive control. Full Android job
`job_20260805_210652_14457a72` passed resolved-core, portable-AAPT2 and runtime-lock
gates, all Debug/QA/QA-AndroidTest assemblies and 128/128 tasks; XML aggregation
`job_20260805_211450_91c4e83d` reported Debug 526/526 and QA 526/526 JVM tests with
zero failures/errors/skips. Lint `job_20260805_211457_1604b9df` passed 55/55 tasks;
`job_20260805_212114_570c9a57` confirmed zero errors and the unchanged 27 warnings per
variant. Python/Go `job_20260805_210659_0143787d` passed Python 101 tests with one
environmental hardlink skip and Go test/vet/build. Artifact job
`job_20260805_211505_15af8337` passed alignment/signature/manifest/canary checks.
Release job `job_20260805_212127_371468b7` passed the expected private-signing
fail-closed gate.

An initial cleanup whitelist assertion `job_20260805_212254_962eeacb` stopped before
mutation because it omitted the generated untracked `ws024-relay/ws024-relay` binary.
Diagnostic job `job_20260805_212313_afba868a` confirmed that file was the default
untracked ARM64 ELF output of the just-completed `go build`, not a source change.
Corrected cleanup `job_20260805_212329_ecd65e0a` removed it and confirmed zero release
APKs. Exact four-file pre-evidence scope, whitespace, sensitive-material and unsafe
WebView/TLS added-line scans passed in `job_20260805_212358_bb60a813`.

APK SHA-256:

- Debug: `3beacea548b78ce09d110820212603ed538e5dc2072c8f218a6ec01658bf2b3f`;
- QA: `cb34cce2fc515a6a20d7cab68eed742d9d5d0fe023912d9b8371175fcf78e546`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

The threat model already treats diagnostics as an output channel and stale WebView
ownership as a lifecycle boundary. This remediation narrows lifetime provenance
without adding an asset, trust edge or new externally reachable behavior, so threat-
model wording is unchanged. No APK was installed or launched; no ADB/device control,
portal interaction, credential/certificate material use, real signature, upload,
payment or administrative submission occurred. Physical AEAT F-03, physical
TalkBack/visual validation and supported-Linux Go race remain external gates.

## Finding G12-02 — Python Dependabot update-monitoring coverage

**Reproduction.** The repository has one explicit Python source dependency manifest,
`tools/requirements.txt` (`PyYAML==6.0.3`). The security workflow already scans that
manifest with OSV-Scanner, while `.github/dependabot.yml` covered only Gradle, Go
modules and GitHub Actions. Current official GitHub Dependabot documentation checked
on 2026-08-05 supports the `pip` ecosystem and `requirements.txt` manifests and uses a
separate ecosystem/directory/schedule entry for version updates. The pinned Python
dependency therefore had vulnerability-scan coverage but no matching automated
version-update monitoring. This finding does not assert a PyYAML vulnerability and did
not justify a dependency upgrade.

**TDD remediation.** The existing CI policy regression was first strengthened to
require exactly one `pip` ecosystem entry and its `/tools`, weekly Monday and PR-limit
scope. RED job `job_20260805_213934_d8a7096a` ran one test and failed exactly because
`package-ecosystem: "pip"` occurred zero times. Production/runtime and Dependabot
configuration were unchanged at RED. The minimum configuration adds one weekly `pip`
entry at `/tools` with `open-pull-requests-limit: 5`; existing Gradle, Go and GitHub
Actions entries are unchanged. Exact GREEN plus the complete CI policy module
`job_20260805_213957_df23d7c9` passed the target test and all 19/19 policy tests.

**Fresh full verification.** Full Android `job_20260805_214008_05cba7fd` passed
resolved-core, portable-AAPT2 and runtime dependency-lock gates, all Debug/QA/QA-
AndroidTest assemblies and 128/128 tasks. XML/hash read
`job_20260805_214720_ad44529f` reported Debug 526/526 and QA 526/526 JVM tests with
zero failures/errors/skips and confirmed `tools/requirements.txt` is byte-for-byte
unchanged from HEAD. Python/Go `job_20260805_214014_3f1f0af3` passed Python 101 tests
with one environmental hardlink skip plus Go test/vet/build. Lint
`job_20260805_214728_d0d3ec61` passed 55/55 tasks; count read
`job_20260805_215335_c01cf437` reported zero errors and unchanged 27 warnings per
variant. Android artifact verification `job_20260805_214736_54d890ad` passed and
release-without-private-signing `job_20260805_215344_817e8e2b` passed fail-closed.
Cleanup `job_20260805_215505_a917c8de` removed the expected untracked Go build binary
and confirmed zero release APKs.

Pre-evidence scope/security validation `job_20260805_215529_fe814d2d` passed: exactly
four milestone files, whitespace clean, Dependabot YAML parsed to exactly
`gradle/gomod/github-actions/pip`, the pip entry is `/tools` + weekly Monday + PR limit
5, workflows/permissions/action pins, runtime source, lockfiles, verification metadata
and requirements are unchanged, sensitive-content scan passed, and the workflow
SHA/permission policy tests remained green.

APK SHA-256 is unchanged from G12-01 because no Android source/build configuration
changed:

- Debug: `3beacea548b78ce09d110820212603ed538e5dc2072c8f218a6ec01658bf2b3f`;
- QA: `cb34cce2fc515a6a20d7cab68eed742d9d5d0fe023912d9b8371175fcf78e546`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No hosted Dependabot execution is claimed from this branch change. No dependency or
tool version was changed, no runtime trust boundary was changed, and threat-model
wording remains unchanged. No APK was installed/launched; no device control, portal
interaction, credential/certificate material use, real signing, upload, payment or
administrative submission occurred.

## Finding G13-01 — authoritative test-plan Dependabot reconciliation — 2026-08-06

**Reproduction.** G12-02 had already added and policy-tested a fourth weekly Dependabot
entry for the Python `pip` ecosystem at `/tools`, while the authoritative
`docs/test-plan.md` still stated that weekly Dependabot covered only Gradle, Go
modules and GitHub Actions. Runtime code, workflows, dependency manifests and pins
were already correct; the stale sentence made the required test plan disagree with
the verified supply-chain configuration.

**Remediation.** The single stale test-plan bullet now names the existing weekly
`pip` coverage at `/tools`. No dependency, tool, Action, workflow, runtime source,
lockfile, verification metadata, portal/profile or release rule changed. This is a
documentation reconciliation, not a behavior change, so no TDD RED was applicable.

**Verification.** `python -m unittest tools.tests.test_ci_policy -v` passed 19/19.
An independent YAML/document consistency assertion confirmed exactly one weekly
`pip` entry scoped to `/tools` and the matching test-plan statement. The initial
attempt to invoke `python -m pytest` failed before test collection because the
Termux system Python has no `pytest`; the repository's documented `unittest` runner
was then used without installing or changing any package. `tools/requirements.txt`,
`.github/dependabot.yml` and `tools/tests/test_ci_policy.py` remain unchanged from
HEAD. `git diff --check` passed on the direct test-plan edit.

## Finding G13-02 — browser notice live-region severity — 2026-08-06

**Reproduction.** `BrowserNoticeBanner` applied `LiveRegionMode.Assertive` to every
notice. `BrowserScreen` uses that same banner for urgent failures and for non-error
states including Client TLS preference `CLEARING` and successful exact site/global
browser-data deletion. The new tests were written first. RED
`job_20260805_222337_0803500c` failed at `compileDebugUnitTestKotlin` exactly because
the banner had no explicit `liveRegionMode` parameter and the state policy helper did
not exist; production sources were unchanged at RED.

**Remediation.** `BrowserNoticeBanner` now accepts an explicit `LiveRegionMode` while
retaining `Assertive` as its default. `BrowserScreen` uses one pure precedence policy:
Client TLS preference `CLEARING`, exact current-site clear success and global clear
success are `Polite`; Client TLS preference failure, compatibility/browser errors,
navigation blocks, global clear failure and limited/failed site clear remain
`Assertive`. Error precedence wins over a lower-priority success state. Strings,
visual styling, Retry behavior, WebView/network/TLS/Client TLS/certificate/signing/
data-clear/profile/release behavior and dependencies are unchanged.

**Verification.** Focused GREEN `job_20260805_222533_f589b871` passed Debug+QA;
XML aggregation `job_20260805_222740_d7eee693` confirmed 11/11 tests per variant,
zero failures/errors/skips. Two attempted monolithic full-Android invocations lost
the Termux connector transport with HTTP 502 while their Gradle wrappers continued;
those calls are not used as pass evidence. Kati_Stable also returned 502 and Kati a
network error during diagnosis. Fresh observed split gates then passed:
`job_20260805_224216_debaec44` (resolved-core, portable AAPT2 and runtime locks),
`job_20260805_224421_3aee3897` (full Debug+QA JVM), and
`job_20260805_224514_53d85d71` (lint and Debug/QA/QA-AndroidTest assemblies).
`job_20260805_224616_c849f08f` read 528/528 Debug and 528/528 QA JVM tests with zero
failures/errors/skips, lint zero errors and 27 warnings per variant, and APK SHA-256
Debug `cd499662a3fafc00f5b9370b5deaf604393611b0071b36487e47fba7aa13c2ae`, QA
`c9732852c88117ab09b49f786bf2adc8f03c2144174534a7ee100ec6c84be098`, QA
AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
Python/Go `job_20260805_224626_7301ac9b` passed 101 Python tests with one environmental
hardlink skip plus Go test/vet/build. Android artifact verification
`job_20260805_224646_40ff453a` and release fail-closed
`job_20260805_224658_b2416ba2` passed. The generated ARM64 relay executable identified
in `job_20260805_224754_055676e0` was removed by
`job_20260805_224803_03dcec46`; release APK count is zero. Robolectric proves Compose
semantics only; physical TalkBack timing/interruption and visual correctness remain
manual gates. Threat-model wording is unchanged because no new trust edge was added.


## Finding G14-01 — security-roadmap Dependabot reconciliation — 2026-08-06

**Reproduction.** The verified G12-02 configuration and CI-policy test already cover
weekly Dependabot version monitoring for the explicit Python manifest under `/tools`,
and G13-01 reconciled `docs/test-plan.md`. Two older summary bullets in the authoritative
security roadmap still described Dependabot as covering only Gradle, Go modules and
GitHub Actions. The roadmap therefore understated an already-pushed supply-chain
control.

**Remediation.** Both stale roadmap summaries now include the existing `pip` `/tools`
coverage. No workflow, Dependabot configuration, dependency, lockfile, verification
metadata, runtime source, profile/catalog or release behavior changed. This is a
documentation-only reconciliation, so a TDD RED is not applicable.

**Verification.** `job_20260805_225840_0dc0db98` passed all 19/19 CI-policy
`unittest` cases, parsed `.github/dependabot.yml` to exactly one weekly `pip` entry at
`/tools`, confirmed both roadmap summaries name that coverage and reject both stale
phrasings, passed `git diff --check`, and found no sensitive-material pattern in added
lines.


## Finding G14-02 — Client TLS issuer-filter hardening — 2026-08-06

**Reproduction.** Android `ClientCertRequest.getPrincipals()` defines a non-empty
principal list as acceptable certificate issuers, and Android Conscrypt
`KeyManagerImpl.chooseAlias()` matches that list against each chain certificate's
`issuerX500Principal`. `ClientAuthRequestHandler.isValidFor()` additionally accepted
`subjectX500Principal`, broadening the platform CA-filter contract. A new two-certificate
RSA fixture made leaf subject and issuer distinct. RED job
`job_20260805_230810_b70c1333`, confirmed by XML in
`job_20260805_231028_8c556d8a`, failed Debug and QA exactly in
`aeatLeafSubjectIsNotAcceptedAsAnIssuer`: 7 tests per variant, one failure,
`expected:<0> but was:<1>`. Production source was unchanged at RED.

**Remediation.** Non-empty Client TLS principal matching now compares only each chain
certificate's `issuerX500Principal.encoded` with the requested principal DER using the
existing `MessageDigest.isEqual`. Exact host/443, navigation epoch, grant TTL, key type,
certificate validity, digital-signature key usage, EKU, one-shot handling, preference
cleanup, empty-issuer profile policy and profile/release activation are unchanged.

**Verification.** Focused GREEN `job_20260805_231054_95780151` passed 7/7 Debug and
7/7 QA. Adjacent browser/profile regression `job_20260805_231253_bce0565d` passed
55/55 per variant. Dependency/toolchain job `job_20260805_231523_b4599725` passed
`:app:verifyRuntimeDependencyLocks`, `verifyResolvedCoreVersion` and
`verifyPortableAapt2Configuration`. Fresh isolated full JVM rerun
`job_20260805_232254_97d24413` executed all 60 tasks and passed 529/529 Debug and
529/529 QA with zero failures/errors/skips. An earlier overlapping retry
`job_20260805_232044_edd244d7` was not counted: concurrent connector-triggered reruns
caused the Gradle test executor to report broad class-execution failures rather than
assertion failures. Durable lint `job_20260805_233550_b6edf4ed` passed with 0 errors /
27 warnings per variant; two earlier duplicate lint jobs timed out by infrastructure and
were not counted. Build `job_20260805_233933_8242422b` passed Debug, QA and QA
AndroidTest; SHA-256 values are Debug
`a31bb8cdfdb05af38a26c3ec32bddf5415e6991d00453553e61f54bb01f32fa9`, QA
`53dd0a15d69fc59a0fa70dde0032005ddf2f6425c9758d745e31d60b8e71f6e9`, and QA
AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
Python/Go job `job_20260805_234055_b7910c71` passed 101 Python tests with one
environmental hardlink skip plus Go test/vet/build. Artifact/release job
`job_20260805_234127_1ff5af35` passed Android artifact verification and expected
release-signing fail-closed with zero release APKs. The generated relay executable was
removed in `job_20260805_234305_6a9be3dc`.


## Finding G14-03 — persisted unlock stale-restore invalidation — 2026-08-06

**Reproduction.** `EncryptedCertificateUnlockCache.store()` already bound an in-flight
write to `invalidationGeneration`, but `restore()` did not. A test storage captured an
owned encrypted-record snapshot inside `read()`, blocked, then allowed `clear()` to
complete before returning the snapshot. RED job `job_20260805_235400_7d3f1417` failed
identically in Debug and QA: one test, one failure per variant, because
`clearDuringBlockingRestoreCannotReturnCachedUnlock` expected null but received a
password-backed `CachedCertificateUnlock`. Production source was unchanged at RED.

**Remediation.** Restore now captures the invalidation generation before entering IO. It
rejects a stale generation immediately after obtaining the owned record snapshot and
checks again after password decoding, zeroing the decoded password before returning null
if a concurrent clear occurred during cryptographic work. AES-GCM record format/AAD,
Android Keystore key handling, reference digest, retention bounds, cancellation
propagation, store invalidation behavior and ViewModel/session policy are unchanged.

**Verification.** Focused GREEN `job_20260805_235700_940d2cca` passed 9/9
`CertificateUnlockCacheTest` cases per variant. Adjacent certificate/session/ViewModel
regression plus dependency/toolchain job `job_20260805_235934_6f90c772` passed 45/45 per
variant and passed runtime locks, resolved-core and portable-AAPT2 gates. Fresh full JVM
`job_20260806_000346_09dda276` executed all 60 tasks and passed 530/530 Debug plus
530/530 QA with zero failures/errors/skips. Lint/build `job_20260806_000750_feb3c236`
passed with 0 errors / 27 warnings per variant and built Debug, QA and QA AndroidTest. APK
SHA-256: Debug `b771e02dacc454a0f83c0e6049d73de09e0a231dd318a48469b5a2a8545e7daf`; QA
`c717d9c212566c372331a68365c9b75006af92f2f3f503c37d3a66651896e660`; QA AndroidTest
`5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`. Python/Go/
artifact/release job `job_20260806_001426_21a0f58a` passed 101 Python tests with one
environmental hardlink skip, Go test/vet/build, Android artifact checks and release
signing fail-closed with zero release APKs. Generated relay executable SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` was removed in
`job_20260806_001604_23dbe0ac`.

## Finding G14-04 — complete Android backup/D2D domain exclusion — 2026-08-06

**Reproduction.** The manifest already set `android:allowBackup="false"` and referenced
both legacy `backup_rules.xml` and Android 12+ `data_extraction_rules.xml`, but each
resource excluded only `domain="root" path="."`. Android backup domains are distinct;
`file`, `database`, `sharedpref`, `external` and device-protected domains are not covered
by a root-domain exclusion. Android 12+ device-to-device behavior can also ignore
`allowBackup=false`, making the explicit `<device-transfer>` rules the relevant
fail-closed boundary. Junta Firma Mobile persists non-secret certificate-reference
metadata through Preferences DataStore under app files storage, while the encrypted
unlock record remains in `noBackupFilesDir`. RED `job_20260806_002638_74b167aa` failed
exactly because the legacy policy lacked eight required domains; production resources
were unchanged at RED.

**Remediation.** Legacy `full-backup-content` and both Android 12+ `cloud-backup` and
`device-transfer` now exclude `path="."` independently for exactly `root`, `file`,
`database`, `sharedpref`, `external`, `device_root`, `device_file`, `device_database`
and `device_sharedpref`. No include rules were added. Runtime storage, certificate
selection/persisted URI permission, AES-GCM/Keystore unlock cache, diagnostics, signing,
network/TLS, profile/catalog and release behavior are unchanged.

**Verification.** Focused GREEN `job_20260806_002711_d709b2b3` passed the new
parser-based policy regression and complete `CiPolicyTest` 20/20. Dependency/toolchain
and fresh full JVM `job_20260806_002722_41fae726` passed runtime locks, resolved-core,
portable AAPT2 and all 60 JVM tasks with Debug 530/530 and QA 530/530, zero
failures/errors/skips. Lint/build `job_20260806_003314_e0f0f679` passed at 0 errors / 27
warnings per variant and built Debug, QA and QA AndroidTest. APK SHA-256: Debug
`2885b12708dd3e25beebf04fed55a76c945d09fc59ceca78821312b3c86ef40a`, QA
`f1252cbaefcb16063e1006558c4b03c630fe4b401f9a7a9723b52444d92a0842`, QA AndroidTest
`5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
Python/Go/artifact/release `job_20260806_003806_d8526dbe` passed 101 Python tests with
one environmental hardlink skip, Go test/vet/build, Android artifact verification and
expected release-signing fail-closed; release APK count was zero. The generated relay
executable SHA-256 was `b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` and was
removed before final staging. No APK was installed/launched and no device control,
authenticated portal interaction, credential/private-certificate use, real signature,
upload, payment or administrative submission occurred.

## Finding G15-01 — Client TLS monotonic grant TTL — 2026-08-06

**Reproduction.** Client TLS pending-transition, direct-replay and granted authorization expiry used civil `Clock`/`Instant`, so rewinding the system clock could extend an already elapsed short authorization window. This violated the binding universal-client invariant that security expiry/state windows use process-monotonic time. RED `job_20260806_180534_6ae3e8d9` reproduced the defect before production mutation: authorizer and request-handler rollback regressions failed with the expected assertions.

**Remediation.** Client TLS pending transitions, direct replay suppression and confirmed grants now carry monotonic observation time plus bounded lifetime and validate through existing `MonotonicSecurityTime`. Request handling and post-client-cert-preference-clear revalidation use the same fail-closed boundary. Civil `Clock` remains only for X.509 validity via `certificate.checkValidity(Date.from(clock.instant()))`. Exact profile/origin/path/host/port, navigation epoch, issuer/key/usage/EKU, one-shot cleanup and QA/release activation boundaries are unchanged. No new design scope was required because the approved 2026-07-15 universal-client design already mandates monotonic security expiry and short-TTL `CLIENT_TLS_AUTH` state.

**Verification.** Dependency/toolchain and focused Client TLS `job_20260806_194539_847585e9` PASS. Full JVM `job_20260806_195244_56763d36`: 60 tasks executed, Debug 532/532 and QA 532/532, zero failures/errors/skips. Assembly `job_20260806_194307_a1adf27b` PASS for Debug, QA and QA AndroidTest. Lint `job_20260806_200521_58fbcc2f`: 0 errors / 27 warnings per variant. Python/Go `job_20260806_201618_656cbb46`: 102 Python tests PASS with one environmental hardlink skip and Go test/vet/build PASS; its later exit 126 was only the Termux `/usr/bin/env` shebang incompatibility. Unchanged CI scripts passed syntax and Android artifact verification via explicit Termux bash in `job_20260806_201734_39e57c20`; release signing fail-closed itself passed in `job_20260806_201817_7ccfec9b`. `job_20260806_202036_f8686d7a` confirmed zero release APKs and APK SHA-256 Debug `cb361265a636712ed584d6235ee0a877b7268b486074558874d32cb6dc841dc4`, QA `f97b5f4ac075ab70729b77d365b67d7698c53ef0cf66be176196078f6577f5fb`, QA AndroidTest `08ed3f916acb55c5586a52a93dfdb2c2c66c7832b385b9f74a1d7182d9cba449`. Generated relay SHA-256 was `b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` and was removed. Local `govulncheck`, `osv-scanner` and `gitleaks` executables were unavailable; pinned workflow/scanner policy remained covered by passing `CiPolicyTest`. No device/portal/credential/signing/upload/payment action occurred.

## G16 read-only certificate/session clock and logging audit — 2026-08-06

A fresh trust-boundary pass found no separate logging/export defect: the only production-source Android `Log.i` calls are the sanitized QA diagnostic mirror and the DUMP-protected catalog smoke bridge; release sets `ALLOW_QA_PROFILES=false`, uses a no-op diagnostic sink, and production code has no caller of `SanitizedLogger.snapshot()` or `exportText()`.

The certificate expiry pass identified an unresolved clock-model risk that is intentionally **not remediated in this audit step**. `CertificateSession` expires the in-memory `UnlockedIdentity` from civil `Clock/Instant`, so a backward system-clock adjustment after unlock can extend same-process signing availability relative to elapsed time. The encrypted persisted unlock cache also validates authenticated `issuedAt`/`expiresAt` UTC timestamps against current civil time. It rejects a clock earlier than `issuedAt`, but a partial rollback that remains after `issuedAt` can lengthen the apparent validity window. This matters because the documented product contract promises restoration for at most 24 hours across process death and device restart, while process-monotonic time is not persistent across a device restart.

A same-process monotonic cap is feasible but does not by itself solve the persisted/device-restart guarantee. A complete remediation therefore requires an explicit trust-time/product decision: preserve device-restart restoration and document the residual civil-clock assumption, fail closed across device restarts/clock anomalies at the cost of that restoration promise, or introduce a separately justified trusted-time mechanism. No runtime/test mutation was made, no guarantee was broadened, and G15-01's short Client TLS monotonic boundary is unaffected.

## Finding G17-01 — browser identity button-role semantics — 2026-08-06

**Reproduction.** `IndustrialBrowserTopBar` contains an optional internal
`onIdentityClick` branch. When explicitly wired, its merged Compose node exposed
`OnClick` without an explicit role. Initial RED `job_20260806_204953_52915fe0` also
probed target size; the node measured 69 px high and reached the role assertion, so the
size hypothesis was rejected. Narrowed RED `job_20260806_205704_31ea5f2a`, parsed in
`job_20260806_210524_6ad34925`, ran 5 tests with exactly one failure: the role was
absent while `OnClick` remained present; production source was unchanged.

**Remediation and exact runtime scope.** The optional non-null branch now supplies
`role = Role.Button`; the null branch remains non-clickable/role-free. A post-commit
call-site audit (`job_20260806_213403_11ab2535`, history/blame
`job_20260806_213437_188b61c4`) established that current production `BrowserLayout`
does not pass `onIdentityClick`, supplies `editingContent = null`, and has an existing
`toolbarIdentityCannotOpenManualUrlEditor` regression. Therefore G17-01 hardens a
dormant internal optional API and does **not** change the current production user path
or enable manual URL editing. No layout, string, navigation, WebView/network/TLS,
Client TLS, certificate, signing, profile, release, resource or dependency behavior
changed.

**Verification.** Focused GREEN `job_20260806_210559_522f6433` passed 5/5 Debug and
5/5 QA; full dependency/toolchain/JVM `job_20260806_210837_45835074` executed all 63
tasks and XML `job_20260806_211559_f3df9f5b` reported 534/534 per variant with zero
failures/errors/skips. Lint/build `job_20260806_211608_f9af0293` passed 124 tasks;
`job_20260806_212122_622207d7` parsed 0 errors / 27 warnings per variant and APK
SHA-256 Debug `16a334f13900d06559dbf56e8736976255712e2d5345cbc2fc6b2bff800d309a`, QA
`e96e72c3902e4d6c9d8d7eeb04aacf48c760d2fd65a4b799ea58621ba1192230`, QA AndroidTest
`08ed3f916acb55c5586a52a93dfdb2c2c66c7832b385b9f74a1d7182d9cba449`.
`job_20260806_212150_46b3db2d` passed Python 102 with one environmental hardlink skip,
Go test/vet/build, Android artifacts and release fail-closed. Final focused/CiPolicy
`job_20260806_212752_eee24aa7` passed and `job_20260806_212946_15a7f8ac` confirmed 5/5
per variant, relay absent and zero release APKs. Commit
`1e6b7a611635476185ca819d7d2641580a3d5c91` was pushed and exact remote SHA verified.
No current-user TalkBack/visual improvement is claimed; no threat-model change is
required because no runtime trust edge changed.
## Finding G18-01 — remove dormant manual-URL browser surface — 2026-08-06

**Reproduction.** The live `BrowserLayout` was already read-only, but exact symbol audit
`job_20260806_214058_9ef9999b` showed main-source still retained an unreferenced
`BrowserAddressBar` containing `BasicTextField` and arbitrary `onSubmit(String)`, plus
unused `IndustrialBrowserTopBar.onIdentityClick` / `editingContent` slots. No production
consumer existed, so this was dormant attack surface rather than a current navigation
bypass. RED `job_20260806_214334_dbb2fc34`, parsed by
`job_20260806_214501_7a424a95`, ran the new source-policy test once and failed exactly
with `Production main source must not retain the dormant manual URL editor`; production
sources were unchanged at RED.

**Remediation.** The dead `BrowserAddressBar` composable, editor state/callbacks/imports,
editor-only resource strings, `BrowserToolbarHeight`, production
`BROWSER_ADDRESS_FIELD_TAG`, and the dormant `onIdentityClick` / `editingContent` slots
were removed. `BrowserAddressPresentation.hostOf`, the read-only profile/host identity,
current browser tags and every actual navigation/security boundary remain. Existing
negative UI checks use the exact removed tag as a test-local literal, so their
no-editor assertion is not weakened. G17-01 remains historical evidence for the hook
while it existed; G18-01 removes that hook structurally.

**Verification.** Focused GREEN `job_20260806_214656_e8869677` passed browser security,
chrome and screen suites in Debug and QA; `job_20260806_215018_c60f84a6` parsed 27/27
per variant with zero failures/errors/skips and confirmed
`toolbarIdentityCannotOpenManualUrlEditor` remains present. Dependency/toolchain plus
fresh full JVM `job_20260806_215028_a94e7588` executed 63/63 tasks; XML aggregation
`job_20260806_215812_66037d60` reported Debug 533/533 and QA 533/533, zero
failures/errors/skips. Lint/build `job_20260806_215827_fe79aaec` passed 124 tasks and all
three assemblies; `job_20260806_220447_5a1738c0` parsed 0 errors / 26 warnings per
variant. APK SHA-256: Debug
`39fded02c7dcd0280ace68ec02083615dabb774e79786685e56c3b4912d143c3`, QA
`b20a394f812b7d7718c0724508a17c7c513b8cd97b183df25e1a6072a7048705`, QA AndroidTest
`08ed3f916acb55c5586a52a93dfdb2c2c66c7832b385b9f74a1d7182d9cba449`.
Python/Go/artifact/release `job_20260806_220500_65771d83` passed 102 Python tests with
one environmental hardlink skip, Go test/vet/build, Android artifact verification and
release-signing fail-closed. `job_20260806_220857_877aaf8f` confirmed relay absent,
release APK count zero and autonomous divergence 0/0 before evidence edits. Complete
pre-evidence review `job_20260806_220924_691a63aa` passed exact scope, `git diff
--check`, structural-absence, sensitive-data and unsafe WebView/TLS scans. No current
runtime navigation was broadened, no threat edge was added, and no physical/device or
portal claim is made.

## Finding G19-01 — Afirma main-frame native-delivery boundary — 2026-08-06

**Reproduction.** `JuntaWebViewClient` already received authoritative modern
`WebResourceRequest.isForMainFrame` metadata, but `NavigationDecision.HandleAfirma`
did not enforce it before `callbacks.onAfirmaRequest(...)`. A trusted top-level page
could therefore cause a valid direct `afirma:` or embedded-Afirma `intent:` subframe
navigation to reach the native Afirma request surface, and the deprecated String
callback could do the same without proving frame ownership. RED
`job_20260806_222113_c06f171b`, parsed by `job_20260806_222241_725e79f7`, ran three
Debug regressions with two expected failures: subframe routing produced
`[afirma:sign, afirma:sign]` and the legacy callback produced `[afirma:sign]`; the
modern main-frame positive control already passed. This establishes a native request
trust-boundary bypass, not an automatic-signature exploit.

**Remediation.** `HandleAfirma` now requires `isModernMainFrame` before native request
delivery. Subframe and deprecated-callback Afirma decisions are consumed, logged only
through the existing sanitized blocked-navigation event, report
`UNTRUSTED_AFIRMA_ORIGIN`, and never call `recordAfirmaRequest` or
`onAfirmaRequest`. Modern main-frame direct and embedded-Afirma routing remains
unchanged. `JuntaNavigationPolicy`, ordinary HTTPS/external navigation, WebMessage,
Client TLS, certificate/signing execution, profile/release policy and dependencies are
unchanged.

**Verification.** Focused GREEN `job_20260806_222530_6d61bc8c` / parser
`job_20260806_222921_23a53387` passed 40/40 Debug and 40/40 QA. Fresh
runtime-lock/core/AAPT2 plus full JVM `job_20260806_222931_bb0d28b8` executed 63/63
tasks; XML aggregation `job_20260806_223615_f97d9406` reported Debug 535/535 and QA
535/535, zero failures/errors/skips. Lint/build `job_20260806_223624_b4579b73`
passed 124 tasks including all three assemblies; parser
`job_20260806_224214_259200f6` reported 0 errors / 26 warnings per variant. APK
SHA-256: Debug `16589a5492c7b689a7492791d3fe22a71dbb69873b46db27f2305750553fb1e2`, QA
`f4c8f34765debdfdcb4bfe73712819939996e8ee791b71e8da7ea84679088df8`, QA
AndroidTest `08ed3f916acb55c5586a52a93dfdb2c2c66c7832b385b9f74a1d7182d9cba449`.
Python/Go/artifact/release `job_20260806_224224_6882554f` passed 102 Python tests
with one environmental hardlink skip, Go test/vet/build, Android artifact verification
and release-signing fail-closed. Pre-evidence exact-scope/diff/sensitive/unsafe-pattern
scan `job_20260806_224602_80996800` passed. No APK installation/launch, device
control, authenticated portal interaction, credential/private-certificate use, real
signature, upload, payment or administrative submission occurred; physical portal E2E
is not claimed.

## Finding G20-01 — external-browser main-frame native-delivery boundary — 2026-08-07

**Reproduction.** `JuntaWebViewClient` received authoritative modern
`WebResourceRequest.isForMainFrame` metadata, but `NavigationDecision.OpenExternal`
called `callbacks.openExternal(...)` for every frame and the deprecated String callback
could reach the same branch without frame ownership. In production that callback clears
pending Client TLS/Afirma state, abandons Client TLS, advances the navigation epoch,
cancels signing and then reaches the Activity external-browser handoff. RED
`job_20260806_231912_7675aebf` ran two Debug regressions against unchanged production
and failed 2/2 as expected: direct external HTTPS from subframe plus legacy callbacks
produced `[external:example.org, external:example.org]`, while a validated `intent:`
HTTPS browser fallback from a subframe produced `[external:example.org]`. The same
fallback's modern main-frame positive control passed before the negative assertion.

**Remediation.** `NavigationDecision.OpenExternal` native delivery now requires
`isModernMainFrame`. Non-main and deprecated-callback paths are consumed, emit only the
existing sanitized `NAVIGATION_BLOCKED` diagnostic with typed reason
`UNTRUSTED_EXTERNAL_NAVIGATION`, and call neither `openExternal` nor
`onNavigationBlocked`. Modern main-frame direct HTTPS and validated browser-fallback
handoff are unchanged. `JuntaNavigationPolicy.decide*`, URL validation/allowlists,
profiles/releases, Client TLS, WebMessage, Afirma parsing/signing, certificate/cookie
handling and dependencies are unchanged. No user-gesture requirement was added.

**Verification.** Focused GREEN `job_20260806_232243_54481b9e` passed 42/42 Debug
and 42/42 QA with zero failures/errors/skips. Fresh runtime-lock/core/AAPT2 plus full
JVM `job_20260806_232842_a997f44f` executed 63/63 tasks and passed Debug 537/537 and
QA 537/537, zero failures/errors/skips. Lint/build
`job_20260806_233635_8c200dbd` executed 124/124 tasks, passed all Debug/QA/
QA-AndroidTest assemblies and reported 0 lint errors / 26 warnings per variant. APK
SHA-256: Debug `eac33d40a71eb3a01d4af8be4dc48ef504e2617a72c3fda891f759b59c4b5b8b`, QA
`8b451a495c43bce6ed3bbc934986c092564f4d784e4d20afc037c84a269579c9`, QA
AndroidTest `fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.
Python/Go/artifact/release `job_20260806_234613_6d1f3271` passed 102 Python tests
with one environmental hardlink skip, Go test/vet/build, Android artifact verification
and release-signing fail-closed; generated relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` was removed
and release APK count is zero. Pre-evidence review
`job_20260806_234851_95328bfb` passed exact five-file scope, `git diff --check`,
sensitive-data and unsafe WebView/TLS scans. The direct-HTTPS query canary remains
absent from sanitized diagnostics. No APK installation/launch, device control,
authenticated portal interaction, credential/private-certificate use, real signature,
upload, payment or administrative submission occurred; physical portal/device E2E is
not claimed.

## Finding G21-01 — explicit WebView geolocation disable — 2026-08-07

**Finding.** The approved browser hardening plan requires WebView geolocation to be
disabled, but `TrustedJuntaWebView.configureSettings()` did not call
`WebSettings.setGeolocationEnabled(false)`. Android's `WebSettings` API documents the
geolocation setting default as `true` and separately requires app location permission
plus a `WebChromeClient.onGeolocationPermissionsShowPrompt(...)` implementation for a
page to use the API. The application already requested neither
`ACCESS_COARSE_LOCATION` nor `ACCESS_FINE_LOCATION`, and `JuntaWebChromeClient`
explicitly replied to the geolocation prompt with `allow=false, retain=false`.
Accordingly this was a missing defense-in-depth/configuration invariant, not evidence of
a current location disclosure.

**TDD.** RED `job_20260807_000337_d952a714` ran only
`BrowserSecurityRegressionTest.trustedWebViewExplicitlyDisablesGeolocation` against
unchanged production and failed 1/1 on the missing explicit setter. The minimum fix adds
exactly `setGeolocationEnabled(false)` inside the existing `settings.apply` block; no
other production file or WebView setting changed. Focused GREEN
`job_20260807_000642_b53b1105` passed 19/19 Debug and 19/19 QA across
`BrowserSecurityRegressionTest` and `TrustedJuntaWebViewTest`.

**Verification.** Fresh runtime-lock/core/AAPT2 plus full JVM
`job_20260807_001123_3f7a18b3` executed 63/63 tasks and passed Debug 538/538 and QA
538/538, zero failures/errors/skips. Lint/build `job_20260807_001945_02964833`
executed 124/124 tasks, reported 0 lint errors / 26 warnings per variant, and passed
Debug/QA/QA-AndroidTest assemblies. APK SHA-256: Debug
`d5c403511ebf626653294765af932ecd1af15130dd0d375cdfd2252b1226fc3f`, QA
`1bc8cba1c9f5cda1152ec81de0edd1b9144196405501aa5cb621bf66b04aa5a0`, QA
AndroidTest `fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.
Python/Go/artifact/release `job_20260807_002844_33f907ea` passed 102 Python tests
with one environmental hardlink skip, Go test/vet/build, Android artifact verification
and release-signing fail-closed; generated relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` was removed
and release APK count is zero. Pre-evidence review `job_20260807_003157_3a70d376`
passed exact four-file scope, `git diff --check`, sensitive-data and unsafe WebView/TLS
scans, proved the production diff is one setter only, confirmed location permissions
remain absent and the WebChrome geolocation deny callback remains present. No APK was
installed/launched and no device, portal, credential, certificate or signing action was
performed.

## Finding G22-01 — blocked subframe callback isolation — 2026-08-07

**Finding.** `JuntaWebViewClient` already consumed disallowed subframe navigation, but
`NavigationDecision.UpgradeToHttps` and generic `NavigationDecision.Block` still called
`onNavigationBlocked(...)` without modern main-frame ownership. `BrowserScreen` maps
that callback to the assertive browser-level blocked notice, so iframe or deprecated
String-callback input could alter top-level UI state despite being unable to navigate,
launch an external activity, or reach signing. This was a frame-ownership/UI-confusion
boundary, not an allowlist bypass.

**TDD.** RED `job_20260807_194449_951b1e66` ran only
`subframeAndLegacyBlockedNavigationCannotReachApplicationCallback` and failed 1/1 with
actual application callbacks `[INSECURE_HTTP, CROSS_PROFILE_NAVIGATION,
UNSUPPORTED_SCHEME, CROSS_PROFILE_NAVIGATION]`. The minimum production change adds two
`isModernMainFrame` gates around application callback delivery; policy decisions,
logging and consumed return values are unchanged. Existing regressions were reconciled so
modern main-frame POST/cross-profile blocks remain positive callback controls while
subframe/legacy paths remain consumed and silent. Focused GREEN
`job_20260807_194829_53f2ca45` passed 43/43 Debug and 43/43 QA, zero
failures/errors/skips.

**Verification.** Fresh dependency/toolchain/full JVM
`job_20260807_195356_8e29be36` executed 63/63 tasks and passed Debug 539/539 and QA
539/539, zero failures/errors/skips. Lint/build `job_20260807_200103_7951a70e`
executed 124/124 tasks, reported 0 lint errors / 26 warnings per variant and passed
Debug/QA/QA-AndroidTest assemblies. APK SHA-256: Debug
`00eb14ac71858a1d4900246113b8521a5777a6d43c1f4860156208b82d373884`, QA
`8f7adf449a078b5bb025a25489a102779fe5177abce5bdb165e11de46ed31ed7`, QA
AndroidTest `fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.
Python/Go/artifact/release `job_20260807_200735_f0f9c26a` passed 102 Python tests
with one environmental hardlink skip, Go test/vet/build, Android artifact verification
and release-signing fail-closed; generated relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` was removed
and release APK count is zero. Pre-evidence review `job_20260807_200936_9ca16e8c`
passed exact four-file scope, `git diff --check`, sensitive-data and unsafe WebView/TLS
scans, proved `JuntaNavigationPolicy` unchanged and production scope limited to two
callback gates with main-frame callbacks retained. No APK installation/launch, device
control, portal interaction, credential/certificate use, real signing, upload, payment or
submission occurred.

## Finding G23-01 — Safe Browsing frame UI isolation — 2026-08-07

**Finding.** `onSafeBrowsingHit` always returned to safety, but an active subframe hit also published `SAFE_BROWSING` to top-level application UI. Android's request contract distinguishes iframe/subresource requests with `isForMainFrame=false`. This was UI/frame ownership, not a Safe Browsing bypass.

**TDD.** RED `job_20260807_202101_b4a1c845` failed 1/1 because the subframe produced `[error:SAFE_BROWSING]` while `backToSafety` succeeded. Minimum production change adds `request.isForMainFrame` only to the application-error predicate; `callback.backToSafety(true)` remains unconditional and first, diagnostic logging unchanged. Focused GREEN `job_20260807_202432_3e0878c4` passed 43/43 Debug and QA.

**Verification.** Full dependency/toolchain/JVM `job_20260807_202910_d5948958` passed 63/63 tasks and 539/539 Debug + 539/539 QA. Lint/build `job_20260807_203651_fe397862` passed 124/124 tasks, 0 errors / 26 warnings per variant; APK SHA-256 Debug `011909d3945c7e62c3e1240d008a26fe5d679e59cf19cc3492d2cce2c2715176`, QA `d546cc59b2f2b376f605b62ecd535b4ee933242a7fde02826f49ce61bc5a7af7`, QA AndroidTest `fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`. Python/Go/artifact/release `job_20260807_204415_280a3cff` passed 102 Python tests with one environmental hardlink skip, Go test/vet/build, Android artifact verification and release fail-closed; relay removed and release APK count zero.

## Finding G24-01 — SSL error UI ownership isolation — 2026-08-08

**Finding.** Android `WebViewClient.onReceivedSslError(WebView, SslErrorHandler,
SslError)` exposes no `WebResourceRequest` and therefore no authoritative
`isForMainFrame` signal. The platform contract describes an SSL error while loading a
resource and requires the host to choose `cancel()` or `proceed()`; the secure choice is
always `cancel()`. Both active-WebView clients nevertheless promoted every such callback
to `BrowserErrorCode.SSL_ERROR`, and `BrowserScreen` maps that application callback to a
top-level assertive error/retry state. Active WebView ownership does not prove top-level
frame ownership, and `SslError.url` is not a frame-ownership primitive. This is a
frame/UI ownership hardening defect, not a TLS-validation bypass.

**TDD.** RED `job_20260808_065907_5c6f66a3` ran two Debug regressions against unchanged
production and failed 2/2 exactly on `expected:<[]> but was:<[error:SSL_ERROR]>`. The
preceding assertions established normal `handler.cancel()`/no `proceed()` and dedicated
Client TLS cancellation/cleanup behavior. The minimum production fix removes only the
two `callbacks.onBrowserError(BrowserErrorCode.SSL_ERROR)` deliveries. Normal
`handler.cancel()` remains unconditional and first; its sanitized
`SSL_ERROR_CANCELLED` diagnostic remains. Dedicated Client TLS still calls
`handler.cancel()` first and then unconditionally `requestHandler.abandon()`, so an SSL
error cannot preserve the one-shot grant. No URL heuristic was added.

**Verification.** Focused GREEN `job_20260808_070120_e4fa34e2`, with exact class-report
aggregation `job_20260808_070439_a2246eef`, passed 28/28 Debug and 28/28 QA. Fresh
runtime-lock/core/AAPT2 plus full JVM `job_20260808_070459_04773656` executed 63/63
tasks and passed Debug 540/540 and QA 540/540, zero failures/errors/skips. Lint/build
`job_20260808_070942_1ac8fa05` executed 124/124 tasks, reported 0 lint errors / 26
warnings per variant, and passed Debug/QA/QA-AndroidTest assemblies. APK SHA-256: Debug
`b97fe660c4444fd5b9f2be810a07bd919a6ee491b978d154ec540bd60b61032d`, QA
`cb731a7a5e4ba143a42502a3a7c9c76a9b92510a8fbec1e6fa92918903d150d4`, QA
AndroidTest `fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.
Python/Go/artifact/release `job_20260808_071433_6302eb28` substantively passed 102
Python tests with one environmental hardlink skip, Go test/vet/build, Android artifact
verification and release-signing fail-closed. That wrapper's exit 1 was diagnosed as its
post-check calling `find` on the intentionally absent release directory under
`pipefail`, after all substantive gates had passed; cleanup
`job_20260808_071710_97062e0e` removed generated relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` and confirmed
release APK count zero. Pre-evidence review `job_20260808_071753_187af180` passed exact
scope, `git diff --check`, cancel-first/Client-TLS-abandon invariants, no SSL `proceed`,
no navigation-policy diff, sensitive-addition scan, relay absence and zero release APKs.
Post-evidence focused/policy `job_20260808_071939_62390312` passed 28/28 Debug and 28/28 QA plus CiPolicyTest 20/20.
No APK installation/launch, device control, authenticated portal interaction,
credential/private-certificate use, real signing, upload, payment or administrative
submission occurred.

## Finding G25-01 — JavaScript dialog secure-display boundary — 2026-08-08

**Finding.** The privileged browser already applies `FLAG_SECURE`, but `TrustedJuntaWebView`
installs `JuntaWebChromeClient` and that client inherited all four JavaScript modal callbacks.
Android's current `WebChromeClient` contract states that the platform-default `alert`,
`confirm`, `prompt` and `beforeunload` dialogs do not inherit the parent's secure-display
flag. Returning `false` therefore allowed remote JavaScript to create a modal surface outside
the browser window's screenshot/screen-share protection. This is a privacy/UI boundary;
no navigation, TLS, Client TLS, certificate-validation or signing bypass was reproduced.

**TDD.** RED `job_20260808_074356_4a7ea42f` failed 5/5 targeted Debug checks on unchanged
production: the four inherited callbacks returned the default-dialog path and the source
contract was absent. Minimum production handling is explicit and immediate:
`onJsAlert -> confirm()+true`, `onJsBeforeUnload -> confirm()+true`,
`onJsConfirm -> cancel()+true`, and `onJsPrompt -> cancel()+true`. Callback URL/message/
default prompt text is not displayed, logged, persisted or forwarded. A first GREEN attempt
`job_20260808_074612_e2fbd115` exposed only a Robolectric fixture defect: synthetic no-arg
`JsResult` had `mReceiver=null`, so `confirm()` threw before the assertion; the production
mapping was unchanged and the fixture was corrected to instantiate the instrumented hidden
`ResultReceiver` constructor. Focused GREEN `job_20260808_075020_27f778b0` then passed;
Debug reports passed `JuntaWebChromeClientTest` 4/4 and `BrowserSecurityRegressionTest`
18/18, while QA passed 4/4 plus the selected source regression 1/1.

**Verification.** Fresh runtime-lock/core/AAPT2 + full JVM
`job_20260808_075543_13f562c7` executed 63/63 tasks and passed Debug 545/545 and QA
545/545 with zero failures/errors/skips. Lint/build `job_20260808_080300_d47d142d`
executed 124/124 tasks, reported 0 lint errors / 26 warnings per variant and passed
Debug/QA/QA-AndroidTest assemblies. APK SHA-256: Debug
`343cab768435e7c348f553597a0989f7152afa2c7cbf6643c5314218296d072f`, QA
`d2f270433688f28fbc891e3ff76976bf2f68485bab8a74cbba95f60e50e59323`, QA AndroidTest
`fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.
Python/Go/artifact/release `job_20260808_081051_288402b5` passed 102 Python tests with
one environmental hardlink skip, Go test/vet/build, Android artifact verification and
release-signing fail-closed; relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` was removed
and release APK count is zero. Pre-evidence diff/security review
`job_20260808_081400_9994048c` found no default-dialog delegation/UI construction,
`addJavascriptInterface`, TLS trust/hostname bypass or SSL proceed path in the changed
production file. Physical portal compatibility with pages that require JavaScript modal
dialogs remains an external/manual acceptance gate. No APK installation/launch, device
control, authenticated portal interaction, credential/private-certificate use, real signing,
upload, payment or administrative submission occurred.
Post-evidence `job_20260808_081630_1ba06e79` reran the focused Android contract with no build cache and passed Debug 22/22 reported focused tests, QA 5/5, plus `CiPolicyTest` 20/20.

## Finding G26-01 — dedicated Client TLS subframe navigation confinement — 2026-08-08

**Finding.** `ClientAuthWebViewClient.shouldOverrideUrlLoading(WebView, WebResourceRequest)`
returned `false` for every `isForMainFrame=false` request before applying the existing
`isAllowed()` origin predicate. A dedicated one-shot Client TLS WebView could therefore load
an arbitrary off-origin subframe even though main-frame navigation was confined to the
profile's Client TLS request/source origins. This did not reproduce certificate disclosure,
TLS-validation bypass or profile escalation; it unnecessarily enlarged the remote-content
surface inside a certificate-authenticated dedicated WebView. The same callback boundary
also needed UI ownership discipline: consuming a hostile subframe must not let that frame
publish a top-level `onNavigationBlocked`, and the deprecated String callback cannot prove
main-frame ownership.

**TDD.** Narrow design and implementation plan are
`docs/superpowers/specs/2026-08-08-client-tls-subframe-navigation-confinement-design.md` and
`docs/superpowers/plans/2026-08-08-client-tls-subframe-navigation-confinement.md`. RED
`job_20260808_101639_d1700821` ran the new Debug regression against unchanged production and
failed at `ClientAuthWebViewClientTest.kt:156`: the off-origin subframe returned `false`
instead of being consumed. Minimum production change applies the pre-existing `isAllowed()`
origin predicate to every modern request. Allowed request/source-origin subframes still
return `false`; disallowed modern requests abandon the one-shot handler and return `true`;
only authoritative modern main-frame requests publish `INVALID_URL`. A disallowed deprecated
String callback remains consumed/abandoned but is UI-silent because frame ownership is
unknown. No allowlist or certificate rule changed.

**Verification.** Focused Debug+QA `job_20260808_102029_d1a3fa55` completed successfully.
Fresh AAPT2/runtime-lock/core plus full JVM `job_20260808_102415_d6343e1f` executed 63/63
tasks; XML aggregation `job_20260808_103110_53ac7cc8` confirmed Debug 546/546 and QA
546/546 with zero failures/errors/skips, including `ClientAuthWebViewClientTest` 6/6 per
variant. Lint/build `job_20260808_103119_f0920883` executed 124/124 tasks; lint summaries
`job_20260808_103907_b151ecd0` reported 0 errors / 26 warnings per variant and all
Debug/QA/QA-AndroidTest assemblies passed. APK SHA-256: Debug
`ec5f461e5994a9314f2cbc7a8bbf68731250eaee84a31422fd40e36754820c03`, QA
`74570f5f5f11cb94d182f076fc306df88da75a807b23ccf3e0ca574177231964`, QA AndroidTest
`fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.

The first combined Python/Go/artifact/release wrapper `job_20260808_103918_cd9b8365`
completed every substantive gate successfully but exited 1 only after them: its final
`find app/build/outputs/apk/release ...` ran under `pipefail` while the intentionally absent
release directory did not exist. Diagnostic `job_20260808_104105_e4ac2a50` confirmed
`find_rc=1`, relay absent, release directory absent and only the three expected non-release
APKs. Corrected wrapper `job_20260808_104117_fd61de5c` exited 0: Python 102 PASS with one
environmental hardlink skip, Go test/vet/build PASS, relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` then removed,
Android artifact verification PASS, release-signing fail-closed PASS and release APK count
zero. Pre-evidence review `job_20260808_104257_46e521a3` passed `git diff --check`, exact
production/test scope, sensitive/unsafe-addition scans, no profile/release/allowlist diff,
relay absence and zero release APKs. No APK installation/launch, device control,
authenticated portal interaction, credential/private-certificate use, real signing, upload,
payment or administrative submission occurred. Physical Client TLS/portal compatibility
claims are unchanged.
Post-evidence verification `job_20260808_104522_fbc787af` passed focused Debug/QA Client TLS tests (6/6 per variant by `job_20260808_104912_d8e853c1`), `CiPolicyTest` 20/20 and `git diff --check`.

## Finding G27-01 — certificate error announcement semantics — 2026-08-08

**Reproduction.** `AppRoot` renders certificate-selection and unlock failures through
`CertificateError`, but that dynamic `Text` had no Compose live-region property. A failed
selection or unlock therefore changed visible state without a semantics instruction for
assistive technology to announce the new blocking error while focus remained on the action.
This is a distinct home/certificate surface from the G10/G13 browser-notice live-region work.

**TDD and remediation.** The narrow design/plan are
`docs/superpowers/specs/2026-08-08-certificate-error-live-region-design.md` and
`docs/superpowers/plans/2026-08-08-certificate-error-live-region.md`. RED
`job_20260808_110244_d42572e9` executed 30/30 Gradle tasks against unchanged production and
failed the new `AppRootTest` at line 117 because the error node lacked the expected
`LiveRegion`. The minimum production change adds only
`Modifier.semantics { liveRegion = LiveRegionMode.Assertive }` to `CertificateError`.
No focus request, copy/resource, layout, certificate state, validation, persistence, password,
signing, network/WebView, profile/release or dependency behavior changed.

**Verification.** Focused Debug+QA `job_20260808_110529_8196a66e` exited 0; XML
`job_20260808_110936_430a3fbb` confirmed `AppRootTest` 5/5 per variant. Fresh
runtime-lock/core/AAPT2 plus full JVM `job_20260808_110944_0ab85eb4` exited 0; XML
aggregation `job_20260808_111518_48dd183f` confirmed Debug 547/547 and QA 547/547 with zero
failures/errors/skips. Lint/build `job_20260808_111524_3e428e52` exited 0; summary/hash
`job_20260808_112235_a830b108` recorded 0 errors / 26 warnings per variant and successful
Debug, QA and QA AndroidTest assemblies. APK SHA-256: Debug
`3206584a8aee6767a7bdaabae044b9c1fac0c9848bff49b1c7f6c7c81c0b2dda`, QA
`c005843d77c092217bd3c60c7605eeddc5216ef93a0fa801cc631997865c7214`, QA AndroidTest
`fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.
Non-Android gate `job_20260808_112245_c9471347` exited 0: Python 102 PASS with one
environmental hardlink skip, Go test/vet/build PASS, relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` then removed,
Android artifact verification PASS, release-signing fail-closed PASS and release APK count
zero. Pre-evidence review `job_20260808_112427_c4088aa7` passed `git diff --check`, exact
production/test semantic scope, protected-boundary scan, sensitive/unsafe-addition scan, relay
absence and zero release APKs.

Robolectric proves only the Compose semantics tree. Physical TalkBack announcement timing,
interruption behavior and visual correctness remain manual gates. Threat-model wording is
unchanged because no asset, credential flow, trust edge or externally reachable behavior was
added. No APK installation/launch, device control, portal interaction, credential/private-
certificate use, real signing, upload, payment or administrative submission occurred.
Post-evidence verification `job_20260808_112600_4bc9515f` exited 0 with focused Debug/QA `AppRootTest` still 5/5 per variant, `CiPolicyTest` 20/20 and `git diff --check` PASS.

## Finding G28-01 — Afirma frame UI isolation — 2026-08-08

**Finding.** G19 already prevented modern subframe and deprecated String-callback Afirma
navigation from reaching native `onAfirmaRequest`, but its rejection branch still published
`onNavigationBlocked(UNTRUSTED_AFIRMA_ORIGIN)`. `BrowserScreen` maps that application
callback to the top-level assertive browser notice, so a valid-looking `afirma:` or embedded
Afirma `intent:` in an iframe could still mutate application UI despite native delivery being
correctly denied. This is a frame/UI ownership residual, not a signing or allowlist bypass.

**TDD and remediation.** Narrow design/plan:
`docs/superpowers/specs/2026-08-08-afirma-frame-ui-isolation-design.md` and
`docs/superpowers/plans/2026-08-08-afirma-frame-ui-isolation.md`. RED
`job_20260808_113324_cc1c7bad` failed the two targeted Debug regressions at
`JuntaWebViewClientTest.kt:378` and `:399` because subframe/legacy rejection still populated
`blocked:UNTRUSTED_AFIRMA_ORIGIN`. Minimum production change deletes only that application
callback from the non-main-frame `NavigationDecision.HandleAfirma` branch. Fail-closed
consumption, sanitized `NAVIGATION_BLOCKED` diagnostics with `main_frame=false`, and modern
main-frame `onAfirmaRequest` delivery are preserved. Focused GREEN
`job_20260808_113729_37d51c56`; parser `job_20260808_114130_af278f04` confirmed
`JuntaWebViewClientTest` 23/23 Debug and 23/23 QA, zero failures/errors/skips.

**Verification.** Fresh runtime-lock/core/AAPT2 + full JVM
`job_20260808_121723_ad7a78a4` exited 0; XML aggregation
`job_20260808_122208_ea86d9cc` confirmed Debug 547/547 and QA 547/547, zero
failures/errors/skips, with `JuntaWebViewClientTest` 23/23 per variant. Lint/build
`job_20260808_114918_257f34ca` exited 0 with all Debug/QA/QA-AndroidTest assemblies;
current-report/hash review `job_20260808_121552_89de2c70` recorded 0 lint errors / 26
warnings per variant and APK SHA-256 Debug
`747cf2df7dfa234d6b915d2e76b54d186a1ffd8f9fb8133912da515ebb762c01`, QA
`fe0723fcf202e4b8f112dbd052624e362f770575ea0be5de68dc5c0bc9a2a284`, QA AndroidTest
`fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.
Non-Android/artifact/release `job_20260808_121533_e7361b19` exited 0: Python 102 PASS
with one environmental hardlink skip, Go test/vet/build PASS, Android artifact verification
PASS, release-signing fail-closed PASS, generated relay removed and release APK count zero.
Pre-evidence `job_20260808_121552_89de2c70` also passed `git diff --check`, protected-file
scope and unsafe-addition scans. No APK installation/launch, device control, portal interaction,
credential/private-certificate use, real signing, upload, payment or administrative submission
occurred. Physical portal/TalkBack behavior is not claimed.

## Finding G29-01 — browser data-clear navigation-epoch isolation — 2026-08-08

**Finding.** The confirmed `onClearCurrentSite` and `onDeleteAllBrowserData` actions already
abandoned Client TLS, cancelled the current signing flow and cleared pending UI state, but they
did not call the browser's existing `advanceNavigationEpoch()` invalidation primitive until a
later reload produced `onTopLevelNavigationStarted`. During that interval the old main-frame
document remained the current trusted origin. The ordinary Afirma WebMessage route carries no
navigation epoch, while a new MiniApplet request snapshots `currentNavigationEpoch()` at
receipt, so remote JavaScript could create a new native signing request after the user had
confirmed data clearing but before the reload callback. The global clear path widened that
window because cookie deletion is asynchronous. No completed signature, TLS/certificate
bypass or cross-origin navigation was reproduced; the defect is lifecycle invalidation.

**TDD and remediation.** Narrow design/plan:
`docs/superpowers/specs/2026-08-08-browser-data-clear-epoch-isolation-design.md` and
`docs/superpowers/plans/2026-08-08-browser-data-clear-epoch-isolation.md`. RED
`job_20260808_123759_3297c0cd` executed 30/30 Gradle tasks and failed the new Debug regression
at `BrowserSecurityRegressionTest.kt:257`; XML `job_20260808_124016_a735632c` confirmed 1/1
failure exactly because current-site clear did not invalidate the epoch before deletion.
Minimum production remediation adds exactly one `advanceNavigationEpoch()` after
`abandonClientAuth()` in each confirmed data-clear handler and before signing cancellation or
any clear operation. This reuses the established primitive that abandons pending MiniApplet
replies and notifies the signing owner. A later page-start may increment the generation again,
matching the existing explicit-reload lifecycle. Cookie deletion scope, WebMessage/MiniApplet
parsers, navigation policy, network/TLS, Client TLS authorization, certificate/signature
algorithms, profiles/releases and dependencies are unchanged.

**Verification.** Focused GREEN `job_20260808_124051_04776725` exited 0 for the new regression
in Debug and QA. Adjacent bridge/epoch `job_20260808_124317_23ee90cc` exited 0; parser
`job_20260808_124656_fe3635ac` confirmed 44/44 per variant across
`BrowserSecurityRegressionTest`, `MiniAppletReplyRegistryTest`, `MiniAppletBridgeAdapterTest`
and `WebMessageRouterTest`, zero failures/errors/skips. Fresh runtime-lock/core/AAPT2 + full
JVM `job_20260808_124709_0278fa79` exited 0; aggregation
`job_20260808_125353_a51b3ca1` confirmed Debug 548/548 and QA 548/548, zero
failures/errors/skips. Lint/build `job_20260808_125402_5d674e68` exited 0; summary/hash
`job_20260808_130056_95a51bf3` recorded 0 lint errors / 26 warnings per variant and APK
SHA-256 Debug `ad03b9afe9f20deb6050fb11726022523e13ea014e641ad5e81b9bd85fead5ba`, QA
`4b97ad41fff60b256e24c2a5e3e5e6c099ff104bf91414ea1e1e67391063c071`, QA AndroidTest
`fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.
Non-Android/artifact/release `job_20260808_130108_d85546fd` exited 0: Python 102 PASS with one
environmental hardlink skip, Go test/vet/build PASS, Android artifact verification PASS,
release-signing fail-closed PASS, generated relay absent after cleanup and release APK count
zero. Pre-evidence review `job_20260808_130312_b403adcb` proved the exact four-file scope,
production 0 removals / 2 additions, protected-surface and sensitive/unsafe-addition scans,
`git diff --check`, relay absence and zero release APKs. No APK installation/launch, device
control, portal interaction, credential/private-certificate use, real signing, upload, payment
or administrative submission occurred.


## Finding G30-01 — tunnel-route diagnostic request ownership — 2026-08-08

**Finding.** `SigningCoordinator.onTunnelRouteEvent` already ignored callbacks that no longer
belonged to the active signing operation, but `MainActivity.onTunnelRouteEvent` recorded the
same sanitized `TunnelRouteEvent` unconditionally afterwards. A cancelled, completed, foreign
or otherwise stale tri-phase transport callback could therefore repopulate application/QA
route diagnostics after its signing owner had disappeared. The event schema remains closed and
contains no request ID, URL, host, credential or payload, so no raw-secret disclosure was
reproduced; the defect is diagnostic provenance/lifecycle ownership.

**TDD and remediation.** Narrow design/plan:
`docs/superpowers/specs/2026-08-08-tunnel-route-diagnostic-ownership-design.md` and
`docs/superpowers/plans/2026-08-08-tunnel-route-diagnostic-ownership.md`. RED
`job_20260808_133736_97b71ce3` failed the new source regression against the pre-fix activity
callback. Minimum remediation changes `SigningCoordinator.onTunnelRouteEvent` to return an
ownership `Boolean`: absent, wrong-request and cancelled/non-active operations return `false`;
an active matching event returns `true`, while only secure-tunnel stages retain the existing UI
state transitions. `MainActivity` records the sanitized route diagnostic only after `true`.
Direct-fallback observations therefore remain diagnostic-visible for the owning request without
changing UI. No route-event fields, transport/fallback/retry/timeout semantics, network/TLS,
origin/path policy, certificate/signing behavior, portal profile, release status or dependency
changed. Focused GREEN `job_20260808_135551_c54e52e6` passed
`BrowserSecurityRegressionTest` 20/20 and `SigningCoordinatorTest` 19/19; adjacent
`job_20260808_140501_f1fccc18` passed 39/39.

**Verification.** Fresh runtime-lock/core/AAPT2 + complete JVM
`job_20260808_174353_866492dd` exited 0 with 63/63 tasks; XML aggregation
`job_20260808_175618_12626cee` confirmed Debug 550/550 and QA 550/550 with zero
failures/errors/skips. Fresh lint/build `job_20260808_175625_6cba0329` exited 0 with 124/124
tasks; `job_20260808_180708_c0599793` recorded 0 lint errors / 26 warnings per variant and APK
SHA-256 Debug `8f856038e86eed7124636cd21fae73220b2acff154058f924fa18321fe965232`, QA
`78de84ec090e4dc075e7f5cdb6fbde10012d480ca6b4ab03c18bef9bf7f1ef9b`, QA AndroidTest
`fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`; Android artifact
verification passed. Non-Android `job_20260808_174358_0d8b0fab` exited 0: Python 102 PASS with
one environmental hardlink skip and Go test/vet/build PASS; generated relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` was removed.
Release fail-closed `job_20260808_180721_ca2abb4a` passed without private signing inputs and
left zero release APKs. Pre-evidence review `job_20260808_180738_392984fb` passed
`git diff --check`, complete production/test diff review, sensitive/certificate-material and
unsafe-added-line scans; only expected ownership/logging/request-ID control lines matched the
broad diagnostic grep. Local `govulncheck`, `osv-scanner` and `gitleaks` executables remain
unavailable, so no local scanner execution is claimed; the unchanged pinned CI policy remains
covered by `CiPolicyTest`. No APK installation/launch, device control, authenticated portal
interaction, credential/private-certificate use, real signing, upload, payment or administrative
submission occurred.

Post-evidence verification `job_20260808_181212_a67641dd` exited 0; XML `job_20260808_182000_b760b566` confirmed the focused `BrowserSecurityRegressionTest` + `SigningCoordinatorTest` set at 39/39 Debug and 39/39 QA with zero failures/errors/skips. `job_20260808_181217_771655c9` passed `CiPolicyTest` 20/20 and `git diff --check`.


## Finding G16-01 — certificate unlock same-boot monotonic lease — 2026-08-08

**Decision and remediation.** The G16 clock-model lead is resolved by narrowing persisted automatic certificate recovery to a same-device-boot lease. `CertificateSession` expires an unlocked identity on the earlier of original civil expiry and an injected monotonic lease; Android production explicitly uses `SystemClock.elapsedRealtimeNanos()`, so deep sleep consumes the window and monotonic rollback fails closed. The encrypted cache format is `JFMUC002`; AES-GCM AAD binds certificate-reference digest, original civil issue/expiry, `Settings.Global.BOOT_COUNT`, and the **original manual-session elapsed-realtime observation**. A delayed persistence write therefore cannot reset the authorization origin. Restore rejects boot change, elapsed-time rollback, exact/over expiry, unavailable boot-time evidence and legacy/unknown records. Device reboot invalidates automatic recovery and requires PKCS#12 password re-entry. Civil rollback cannot lengthen either in-memory or persisted authorization; civil forward jumps remain a conservative shortening cap.

`CertificateViewModel` creates the manual session lease before asynchronous cache persistence and passes that same observation into `CertificateUnlockCache.store`. Cache restore creates only a lease for the authenticated remaining same-boot duration and passes it into `CertificateSession` before publishing the restored identity; gateway reload cannot renew authorization from civil expiry. No PKCS#12 bytes, private-key objects or plaintext password are newly persisted or logged; AES-GCM/Keystore, zeroization, invalidation-generation, per-signature confirmation, WebView/TLS, profile and release boundaries remain unchanged. The subordinate design/plan are `docs/superpowers/specs/2026-08-08-certificate-unlock-same-boot-monotonic-lease-design.md` and `docs/superpowers/plans/2026-08-08-certificate-unlock-same-boot-monotonic-lease.md`.

**TDD and verification.** Initial session/cache/ViewModel RED→GREEN evidence remains valid: cache RED `job_20260808_195521_ff85e193`, ViewModel RED with exactly one intended renewal failure, and GREEN `job_20260808_201952_adf76600`. The pre-review full gates were intentionally superseded after independent review found the delayed-persistence origin defect.

The first independent review found one real post-GREEN defect before commit: cache persistence sampled elapsed realtime inside `store()`, so delayed IO could move the persisted authorization origin later than the already-created manual session lease. Regression RED `job_20260808_214657_077bd47b` failed exactly because the cache API had no `observedAtMonotonicNanos` parameter. The minimum fix passes the original `CertificateUnlockLease` observation into cache storage, authenticates that observation in `JFMUC002`, rejects a current same-boot elapsed clock behind it, and rejects a store after the original retention is already consumed. Focused cache GREEN `job_20260808_215055_9273b77a` passed; added coverage also exercises elapsed rollback, unavailable boot-time evidence, legacy `JFMUC001`, exact session boundary and monotonic rollback.

The same review raised a possible `clear()`/restore-generation interleaving. Direct inspection classified it as non-defect: the final generation check is the restore operation's linearization point; a later `clear()` is ordered after that restore, while every production ViewModel clear path cancels the owning operation before cache clear and restore crosses a cancellable dispatcher boundary. A second focused reviewer timed out without a verdict and is not counted as evidence. Deterministic follow-up review `job_20260808_220813_0e4b1017` plus source/arithmetic inspection `job_20260808_220821_aa67dcfe` confirmed Android production uses the same `SystemClock.elapsedRealtimeNanos()` domain for session and cache observations; non-negative same-boot operands make the elapsed subtraction bounded, while retention is capped at 24 hours.

Post-review focused Debug+QA `job_20260808_215453_1e468d7f`, parsed by `job_20260808_215957_d4b95a4f`, passed 42/42 per variant: `CertificateSessionTest` 12, `CertificateUnlockCacheTest` 17 and `CertificateViewModelTest` 13, zero failures/errors/skips. The first post-evidence policy run `job_20260808_215458_febb05c4` failed only because the revised T5 prose had dropped the literal existing contract marker `no renueva`; the compatible wording was restored without weakening the policy and `job_20260808_215517_6eecde70` then passed `CiPolicyTest` 20/20.

Fresh post-review full Android `job_20260808_220015_d81bd713` passed runtime dependency locks, resolved-core and portable-AAPT2 checks plus 63/63 Gradle tasks. Clean XML aggregation `job_20260808_221818_30afbe69` confirms Debug 562/562 and QA 562/562 with zero failures/errors/skips. Fresh lint/build `job_20260808_220756_9bfd9ab5` passed 124/124 tasks; `job_20260808_221614_1f718713` recorded 0 lint errors / 26 warnings per variant and APK SHA-256 Debug `af911e7665a1f2df50edc6ce8db33c08a1c98d9087dc1ea573c29982f28a3cd9`, QA `7a259e1357a134352d559481062c77f4e7eb9de20ab6926381ff6f91f59bfeda`, QA AndroidTest `93fafec9159e4d229324522587da903147769f2de74e0e8d64fc8b3a422c0302`.

Fresh non-Android `job_20260808_220021_1e46b313` passed Python 102 tests with one environmental hardlink skip plus Go `test ./... -count=1`, `vet ./...` and relay build; relay SHA-256 remained `b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6`. Android artifact verification `job_20260808_221623_3b8ce21e` passed against the final APKs. Release-signing fail-closed `job_20260808_221629_e10f293d` passed without private signing inputs and left zero release APKs. Cleanup `job_20260808_221737_935fc2da` removed the regenerated relay and reconfirmed zero release APKs.

Pre-evidence `job_20260808_213421_e2ba21c3` had already passed `git diff --check`, production/test diff and sensitive/unsafe-added-line review before the reviewer follow-up; final staged review is performed immediately before commit. The earlier wrapper `job_20260808_213348_179f0b94` was diagnosed by `job_20260808_213507_c5351007` as only an added `find`/absent-release-directory `pipefail` artifact; the actual release script passed then and the fresh final release gate above also exits 0.

No APK installation/launch, ADB/device control, authenticated portal interaction, credential/private-certificate use, real signing, upload, payment or administrative submission occurred. Physical AEAT F-03 Client TLS, real-portal JavaScript-dialog compatibility, TalkBack/visual validation and supported-Linux Go race remain external gates.

## Finding G31-01 — global browser resource-cache erasure — 2026-08-08

**Finding.** The confirmed global `Borrar todos los datos web` path removed all WebView cookies,
WebStorage, the initiating WebView history and the visible form-autocomplete popup, but did not
call `WebView.clearCache(true)`. Android documents that call as the resource-cache erasure API and
that `includeDiskFiles=true` also deletes disk cache files. Because the command is global and its
success state represents deletion of the app's web data, retaining cacheable portal resources was
a privacy/completeness gap. The current-site command must not inherit this call because WebView
resource-cache deletion is application-wide rather than exact-origin scoped.

**TDD and review.** Narrow design/plan:
`docs/superpowers/specs/2026-08-08-global-browser-resource-cache-erasure-design.md` and
`docs/superpowers/plans/2026-08-08-global-browser-resource-cache-erasure.md`. Initial RED
`job_20260808_224058_08008908` failed 1/1 exactly on the absent global `clearCache(true)` contract;
the first minimum GREEN candidate passed Debug+QA in `job_20260808_224303_a464c145`. Independent
read-only review then found a real second defect before commit: `webViewRef` can be null after
renderer death/disposal/barrier while the global menu remains reachable, so the first candidate
could skip resource-cache deletion yet still execute cookie/WebStorage deletion and publish
success. Main-path lifecycle inspection confirmed reachability. Refined follow-up RED
`job_20260808_230414_c766a918` again failed 1/1 on nullable completion ownership. The final fix
types the completion lease to non-null `WebView`; a missing active owner invalidates any stale
lease, publishes the existing failure state and does not start partial global deletion. With an
owner, the handler stops the view, calls `clearCache(true)`, clears history/form UI state, then
starts cookie/WebStorage deletion; completion reload remains bound to the exact initiating view.
Targeted final GREEN `job_20260808_230630_b944e90a` passed the resource-cache scope, null-owner
fail-closed and exact completion-owner regressions in Debug+QA. G29 epoch invalidation, Client TLS
abandonment, signing cancellation and current-site origin scope are unchanged.

**Verification.** Fresh post-review runtime-lock/core/AAPT2 + complete JVM
`job_20260808_231000_b61cad9d` passed 63/63 executed tasks; XML aggregation
`job_20260808_231858_9dd1bfe1` confirms Debug 564/564 and QA 564/564 with zero
failures/errors/skips. Fresh lint/build `job_20260808_231915_400eab46` passed 124/124 executed
tasks; `job_20260808_233512_211243c6` recorded 0 lint errors / 26 warnings per variant and APK
SHA-256 Debug `33c87e4b9c3516194d24f78b87a3e5cd9088a4de1b6e959a9ef5c58be91a2f15`, QA
`77d362dd6ae31ce6f577f27b3a77e4d72499dba87b46cdff873355da361b8e00`, QA AndroidTest
`93fafec9159e4d229324522587da903147769f2de74e0e8d64fc8b3a422c0302`. Fresh non-Android
`job_20260808_231008_213b2f22` passed Python 102 tests with one environmental hardlink skip plus
Go `test ./... -count=1`, `vet ./...` and relay build; relay SHA-256 remained
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6`. Android artifact
verification `job_20260808_233525_2b71b527` passed. Release-signing fail-closed
`job_20260808_233551_f86bb662` passed without private signing inputs and left release APK count
zero. Cleanup `job_20260808_233748_dea2c6c6` removed the generated relay and reconfirmed zero
release APKs. Post-evidence `BrowserSecurityRegressionTest` rerun `job_20260808_234001_3b18dcf8`
passed Debug 22/22 + QA 22/22 (zero failures/errors/skips; 60/60 tasks), and final policy/diff
recheck `job_20260808_234015_16462724` passed `CiPolicyTest` 20/20 plus `git diff --check`. No APK installation/launch, device control, authenticated portal interaction,
credential/private-certificate use, real signing, upload, payment or administrative submission
occurred. Physical AEAT F-03 Client TLS, real-portal JavaScript-dialog compatibility,
TalkBack/visual validation and supported-Linux Go race remain external gates.

## Finding G32-01 — global cookie-removal completion semantics — 2026-08-09

**Finding.** `SiteDataCleaner.clearAllConfirmed` used the Boolean from
`CookieManager.removeAllCookies` as a success bit. Android defines that Boolean as whether any
cookies were removed. A valid zero-cookie completion therefore produced `false` even though the
asynchronous removal operation had completed and global WebStorage deletion had succeeded. The UI
reported failure and withheld its exact-owner reload. No evidence showed retained cookies in this
case; the defect was completion/status semantics.

**TDD and remediation.** Narrow design/plan:
`docs/superpowers/specs/2026-08-08-global-cookie-removal-completion-semantics-design.md` and
`docs/superpowers/plans/2026-08-08-global-cookie-removal-completion-semantics.md`. RED
`job_20260808_235600_41550741` failed the new Debug regression exactly with expected `true` versus
actual `false`; XML `job_20260808_235737_4c16fb1a` confirms one test, one intended failure, zero
errors/skips. Minimum remediation treats callback delivery with `cookiesRemoved=false` as a
completed no-op and does not flush. When `cookiesRemoved=true`, the existing explicit flush remains
required. WebStorage exception, synchronous remove-all exception and required-flush exception stay
failure paths. Targeted Debug+QA GREEN `job_20260808_235805_2207a2e6` passed; adjacent
`job_20260809_000237_d45fd816` / `job_20260809_000839_58a1d080` passed 34/34 tests per variant
with zero failures/errors/skips.

**Verification.** Fresh runtime-lock/core/AAPT2 + complete JVM
`job_20260809_000918_6f64ad07` passed 63/63 executed tasks; XML aggregation
`job_20260809_001837_6a360eef` confirms Debug 568/568 and QA 568/568 with zero
failures/errors/skips. Fresh lint/build `job_20260809_001848_27c31a82` passed 124/124 executed
tasks, with 0 lint errors / 26 warnings per variant. APK SHA-256 from artifact gate
`job_20260809_055602_c2cc7c42`: Debug
`8e5d559db59442813436e5a3f971e79ba1a8f2eb55700c543996815c698a938e`, QA
`26f99e4d1000e52048a7e3bf8d97ad136b1788e786301e8d1d90d4fc0da1eeeb`, QA AndroidTest
`93fafec9159e4d229324522587da903147769f2de74e0e8d64fc8b3a422c0302`; Android artifact
verification passed. Fresh non-Android `job_20260809_000927_2110067c` passed Python 102 with one
environmental hardlink skip plus Go test/vet/build; relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6`. Release fail-closed
`job_20260809_055624_6cb60b76` passed with zero release APKs; cleanup
`job_20260809_055744_db11b800` removed the relay. Pre-evidence review
`job_20260809_055800_766bc466` passed diff/scope/sensitive/unsafe checks. Independent reviewer
`worker-2` found no Critical/Important issue; its only Minor note was synchronous fake-callback
coverage, not an implementation defect. Post-evidence focused rerun
`job_20260809_060109_5c1beee4`, parsed by `job_20260809_060833_11f7c4a3`, passed Debug 34/34 + QA
34/34 with zero failures/errors/skips and 60/60 tasks. Policy/scope gate
`job_20260809_060121_368c3e24` passed `CiPolicyTest` 20/20, `git diff --check`, exact 10-file scope
and unsafe/sensitive added-line review.

Current-site cookie deletion, G31 resource-cache/non-null-owner admission, G29 navigation epoch,
Client TLS, signing, origin/path allowlists, profile/release state and dependencies are unchanged.
No APK installation/launch, device control, authenticated portal interaction,
credential/private-certificate use, real signing, upload, payment or administrative submission
occurred. Physical AEAT F-03 Client TLS, real-portal JavaScript-dialog compatibility,
TalkBack/visual validation and supported-Linux Go race remain external gates.

## Finding G33-01 — certificate provider display-name bidi hardening — 2026-08-09

**Finding.** `CertificateRepository.select()` accepts `OpenableColumns.DISPLAY_NAME` from an
external `ContentProvider`, persists it in `StoredCertificateReference` and later presents it in
trusted native certificate UI. The existing sanitizer removed C0 controls and DEL but retained
Unicode bidi formatting controls, permitting a provider-supplied PKCS#12 name to visually reorder
text. This is a UI-integrity/spoofing boundary; no certificate bytes, password, private key or
signature disclosure was reproduced.

**TDD and remediation.** Subordinate design/plan:
`docs/superpowers/specs/2026-08-09-certificate-display-name-bidi-hardening-design.md` and
`docs/superpowers/plans/2026-08-09-certificate-display-name-bidi-hardening.md`. RED
`job_20260809_061849_7ac334d7`, parsed by `job_20260809_062203_2df5dc47`, failed exactly 1/1
because U+202E and U+2066 survived the provider display name. The minimum production change removes
only Unicode `Bidi_Control` U+061C, U+200E..U+200F, U+202A..U+202E and U+2066..U+2069 inside
`sanitizeDisplayName()`. Ordinary printable Unicode, the 256-character presentation bound, blank
fallback, URI/SAF policy and PKCS#12 MIME handling are unchanged. Octet-stream extension admission
continues to use the original trimmed provider filename before presentation sanitization. Returned
and persisted display names are identical. Focused Debug+QA GREEN
`job_20260809_062238_b55ea965` passed; adjacent certificate/session/view-model
`job_20260809_062810_5a8ca124`, parsed by `job_20260809_063542_038903d0`, passed 61/61 per
variant with zero failures/errors/skips.

**Independent review and verification.** Narrow Luna reviewer `worker-6` reported no Critical or
Important findings. Its only Minor note was that the regression uses two representative controls
rather than enumerating every code point; the approved design deliberately requires one repository
regression while production uses the explicit closed BMP set. Earlier reviewer `worker-5` timed
out without a verdict and is not counted as evidence. Fresh runtime-lock/core/AAPT2 + complete JVM
`job_20260809_064502_0a3b3fd4` passed 63/63 Gradle tasks; XML aggregation
`job_20260809_065218_d0b5d4aa` confirms Debug 569/569 and QA 569/569, zero
failures/errors/skips. Fresh lint/build `job_20260809_065233_fa7e0074` passed 124/124 tasks with
0 lint errors / 26 warnings per variant. Android artifact verification
`job_20260809_070110_c77aadef` passed; APK SHA-256: Debug
`84849c9626a1b0eefafca87a7e395ce852bf4196e25707d1d2129842f0155c4b`, QA
`538d8082c187b55a0d3d2d8aec675b5fdaba660409550360ab6595479e6eec36`, QA AndroidTest
`93fafec9159e4d229324522587da903147769f2de74e0e8d64fc8b3a422c0302`. Fresh non-Android
`job_20260809_064534_ce140f90` passed Python 102 tests with one environmental hardlink skip plus
Go `test ./... -count=1`, `vet ./...` and relay build; relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6`. Release fail-closed
`job_20260809_070134_ca0d3d1d` passed with zero release APKs; cleanup
`job_20260809_070255_f1a08f2f` removed the generated relay. Pre-evidence diff/sensitive/unsafe
scan `job_20260809_065443_8a005566` passed. One initial `exec_start` transport failure and two
later connector 502 status reads were transient transport failures; the preserved jobs completed
successfully and are not product failures.

No APK was installed/launched; no device control, authenticated portal interaction,
credential/private-certificate use, real signing, upload, payment or administrative submission
occurred. Automated evidence proves string-policy behavior only; physical SAF/certificate and
portal acceptance are not inferred.

## Finding G34-01 — legacy persisted certificate display-name read hardening — 2026-08-09

**Finding.** G33-01 sanitized external `ContentProvider` certificate names at new selection time,
but `PreferencesCertificateReferenceStore.read()` returned a previously persisted `display_name`
verbatim. A certificate reference written by a pre-G33 version could therefore retain Unicode bidi
controls across an app upgrade and re-enter trusted native certificate UI. This is a legacy
persistence/read-boundary bypass of the G33 presentation invariant; it does not demonstrate exposure
of certificate bytes, password, private key or signature material.

**TDD and remediation.** Subordinate design/plan:
`docs/superpowers/specs/2026-08-09-legacy-certificate-display-name-read-hardening-design.md` and
`docs/superpowers/plans/2026-08-09-legacy-certificate-display-name-read-hardening.md`. RED
`job_20260809_072053_79a11036`, parsed by `job_20260809_072421_6d6cc68d`, produced exactly one
failure, zero errors/skips: expected `certevil.p12`, while persisted U+202E/U+2066 survived. The
minimum fix extracts the exact G33 C0/DEL/bidi/256-character/trim/fallback rule into pure internal
`CertificateDisplayNamePolicy`, used both by `CertificateRepository.select()` and
`PreferencesCertificateReferenceStore.read()`. The read path performs no DataStore `edit`; it only
normalizes the returned reference. Octet-stream `.p12`/`.pfx` admission still uses the original
trimmed provider filename before presentation sanitization. URI, MIME, size, summary, SAF,
PKCS#12/password/session/signing, WebView/network/TLS, profiles and dependencies are unchanged.
Focused Debug+QA `job_20260809_072506_a0d1a7a2`, parsed by
`job_20260809_073101_28ecad0c`, passed 20/20 per variant.

**Review and verification.** Reviewer `worker-9` found no Critical/Important issue; its only Minor
note was that the regression asserts the returned value but does not independently snapshot DataStore
before/after `read()`, so side-effect-free behavior is supported by direct implementation inspection,
not that assertion alone. Explorers `worker-7` and `worker-8` timed out without results and are not
counted as evidence. Fresh runtime-lock/core/AAPT2 + complete JVM
`job_20260809_073131_74318c66`, parsed by `job_20260809_073943_d784901b`, passed Debug 570/570
and QA 570/570 with zero failures/errors/skips. Fresh lint/build
`job_20260809_074007_0d7b0afc` passed 124/124 tasks with 0 lint errors / 26 warnings per variant.
Artifact gate `job_20260809_075044_6379aee6` passed; APK SHA-256: Debug
`6087fb12fd9f4fa26156263df2a27b332cd4259e3299a055043e4396008cb24f`, QA
`36f7b37c047c9fc8f1d4712c5f25dbf8c0457966e3050067f330768e9f5dd755`, QA AndroidTest
`93fafec9159e4d229324522587da903147769f2de74e0e8d64fc8b3a422c0302`. Non-Android
`job_20260809_073139_d5df8daf` passed Python 102 tests with one environmental hardlink skip plus
Go `test ./... -count=1`, `vet ./...` and relay build; relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6`. Release fail-closed
`job_20260809_075113_55f3f9cf` passed with zero release APKs; cleanup
`job_20260809_075219_03557bd7` removed the generated relay. `CiPolicyTest` passed 20/20 during
`job_20260809_075259_40566609`; that command's exact-scope assertion correctly detected a separate
concurrent portal-priority documentation mutation. G34-only reconciliation/full-diff/security review
`job_20260809_075510_041aaf04` then confirmed six owned pre-evidence files, three preserved foreign
documentation files, raw bidi controls zero in G34 files and no sensitive/unsafe addition.
Post-evidence focused `job_20260809_075747_639efd2f` passed 20/20 Debug and 20/20 QA with zero
failures/errors/skips. Post-evidence policy/scope/safety `job_20260809_075808_88ee788e` passed
`CiPolicyTest` 20/20, `git diff --check`, exact 12-file G34 ownership with the three portal-priority
files preserved foreign, zero raw bidi controls in G34 content and no sensitive/unsafe added line.

No APK was installed/launched; no device control, authenticated portal interaction,
credential/private-certificate use, real signing, upload, payment or administrative submission
occurred. Automated evidence establishes the legacy DataStore/string boundary only; physical SAF,
certificate, TalkBack/visual and portal E2E validation are not inferred.

## Portal G34-P01 — DGT public verification CAdES contract — 2026-08-09

**Evidence and bounded implementation.** Official public unauthenticated DGT evidence at
`https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/verificacion-de-mi-equipo/`
loads DGT-owned `padi/main.js`; the observed exact local verification call is
`MiniApplet.sign("Q2FkZW5hIGEgZmlybWFy", "SHA1withRSA", "CAdES", "filter=nonexpired:", ...)`,
where the payload decodes to the fixed 15-byte ASCII text `Cadena a firmar`. The call exposes no
signing/result endpoint, so none was invented. Local integration commit
`fc52b0a68348f0f26e4ac368526ba7b58f62972f` adds QA-only profile
`dgt-verificacion-equipo`, a fail-closed 15-byte detached CAdES adapter/bridge binding and exact
catalog/inventory contract. The inventory state is `IMPLEMENTED_NOT_E2E`; the public catalog is
`E2E_PENDING`. Release enablement and `VERIFIED_E2E` were not assigned.

**TDD/debugging and verification.** The post-integration focused command initially exposed one stale
aggregate catalog assertion: `PublicPortalCatalogParserTest` still expected eight bindings after the
intentional ninth DGT binding. `job_20260809_093831_1bcb76fb` therefore failed exactly that invariant;
the minimum follow-up changes only the expected count/set to include `dgt-verificacion-equipo`.
Focused rerun `job_20260809_094540_9c864d10` passed the DGT/profile/bridge/adapter/catalog scope,
the DGT/generator Python tests and `git diff --check`. Fresh runtime-lock/core/AAPT2 + full JVM
`job_20260809_095254_f7473c75` passed Debug 580/580 and QA 580/580 with zero
failures/errors/skips. Fresh non-Android `job_20260809_095302_c8db4835` passed Python 104 tests
with one known environmental hardlink skip plus Go `test ./... -count=1`, `vet ./...` and relay
build; relay SHA-256 was
`dfc597a3db003d95b2cff09cf01d7ee758c7dc007d1a6187f27b8284441edc1d`.
Lint/build `job_20260809_100359_43cefa6c` passed `lintDebug`, `lintQa`, `assembleDebug`,
`assembleQa` and `assembleQaAndroidTest`; lint reports contain zero errors and 26 warnings per
variant. Android artifact gate `job_20260809_101616_e7db7cc0` passed; APK SHA-256: Debug
`facddad3559aa157c42beb6a1eefd4b89e8dd7a0beef714f7a4d588a75d13da7`, QA
`a1fb1345fe9aa3e64a9857a48117f762770b7bb65a4624c724d49f439a1c95ba`, QA AndroidTest
`efe2df331a82053cdba552ea89d2941757b6b6baec14cdc93169c7329cec9647`. Release fail-closed
`job_20260809_101643_0c950db9` passed without private signing inputs and left zero release APKs.
Cleanup `job_20260809_101830_145b9832` removed the generated relay and reconfirmed
`git diff --check`.

Added-hunk review found no private-key/certificate bodies, high-confidence cloud/token literals,
TLS bypass, unsafe WebView bridge enablement or release QA-profile enablement. No APK was installed
or launched; no ADB/device control, authenticated portal interaction, credentials/private-certificate
material, real signing, upload, payment or administrative submission occurred. Physical DGT portal
acceptance remains a manual E2E gate and is not inferred from static/public evidence or local tests.


## Autonomous Portal G35-P01 — UGR certificate-login CAdES contract — 2026-08-09

**Public contract and scope.** Official unauthenticated UGR surface
`https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp` exposes the exact first-party local-signing
contract used for certificate login: the fixed text `Universidad de Granada`, `SHA1withRSA`,
`CAdES`, an empty filter, `setForceWSMode(true)`, `cargarAppAfirma()` and UGR-owned
Storage/Retrieve service URLs. The public callback form targets `/Hades/ValidacionCertificado`;
the app does not call or invent that administrative endpoint. The implementation therefore adds
`ugr-certificado-login` as `VERIFIED_CONTRACT` / `QA_ONLY`, inventory
`IMPLEMENTED_NOT_E2E`, catalog `E2E_PENDING`. It is not release-enabled and is not
`VERIFIED_E2E`.

**TDD, integration and debugging.** Isolated worker commit
`efbaec48099d59b1a5073c59ac3b0a97358accc5` preserved the UGR-first TDD work. Worker focused
Debug/QA, Python generator and generator-idempotence gates were GREEN before integration. During
sequential integration with the already-published DGT profile, `job_20260809_110346_f4c4c80c`
correctly exposed two merge defects: a missing closing brace in the combined MiniApplet payload
checks and `CadesPreSignState` remaining private to the DGT-era implementation. The minimum repair
restored the brace and made that shared pre-sign state `internal`; tight Debug+QA compilation
`job_20260809_110747_72ac53e2` passed. A later combined run
`job_20260809_111214_e09df3ff` exposed only stale aggregate expectations in
`PortalCatalogScreenTest` and `JuntaOriginPolicyTest`; they were updated to the intentional tenth
profile binding and exact UGR host/origin. Exclusive regression rerun
`job_20260809_111813_594c58d6` passed.

**Fresh verification.** Full runtime-lock/core/AAPT2 + JVM rerun
`job_20260809_112549_86ad3219` passed Debug 590/590 and QA 590/590 with zero
failures/errors/skips. Non-Android `job_20260809_113452_d6c2fa05` passed Python discovery 103
with one known environmental hardlink skip, separate `CiPolicyTest` 20/20, plus Go
`test ./... -count=1`, `vet ./...` and relay build; relay SHA-256
`b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6`.
The combined `--rerun-tasks` lint/build job `job_20260809_113545_e83211e4` hit the connector's
600-second execution limit after `assembleDebug`, `assembleQa`, `assembleQaAndroidTest` and fresh
Debug lint had completed; this timeout is infrastructure evidence, not a product failure. Dedicated
QA lint recovery `job_20260809_114615_8f54482e` completed successfully; final Debug and QA lint
reports each contain 0 errors / 26 warnings. Android artifact gate
`job_20260809_115034_135469ba` passed; APK SHA-256: Debug
`0fbccd2252e7af13e9df4192671c095413a40347972ebb7d8a217b118d5f8ec7`, QA
`d214c133d757af841e10ab52587fc6a8e97a70c402920219f4f7998f55d5f125`, QA AndroidTest
`26e3b13c7c021012dcd7f6a44a671e523edcb72b10da5dcb6def858b48ac6af2`. Release fail-closed
`job_20260809_115102_4ad9f309` passed without private signing inputs and left zero release APKs.
Generated relay and external diagnostic `error.log` are cleanup-only and must be absent at
publication.

**Catalog and next pipeline.** The generated catalog is 182 entries with 10 exact profile bindings
and 172 unbound. Inventory states are 166 `BROWSE_ONLY`, 5 `IMPLEMENTED_NOT_E2E`, 1
`VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`, 4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
The public catalog has 94 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 5 `E2E_PENDING`, 4
`E2E_VERIFIED`. Sixteen researched public surfaces are buffered; after UGR there is no currently
implementation-ready unintegrated candidate. Continue exact public-contract research in order:
`justicia-sede-judicial`, `age-acceda`, `sepe-sede`, `mjusticia-sede`, `sevilla-sede`, `us-sede`,
`age-direccion-general-del-catastro`, `asturias-sede-tramite-autofirma`. Do not infer an ABI from a
loaded AutoFirma library or from descriptive help text.

No APK was installed or launched; no device control, authenticated portal interaction,
credential/private-certificate use, real signing, upload, payment or administrative submission
occurred. Physical UGR and DGT acceptance remain manual E2E gates. Existing AEAT Client-TLS,
real-portal JavaScript-dialog, TalkBack/physical visual and supported-Linux Go-race gates remain
external.

## Portal G36-R01 — priority public-contract research refresh — 2026-08-09

**Repository continuity.** Generation 36 began with `git fetch --prune origin` and discovered that
both local and remote autonomous HEAD had legitimately advanced beyond the G35 UGR publication
`ce06961f976c363280988cba81ca89e682dcc3b3` to
`80b95d3ef8876438156f42b287ff37bfe579e976`. The intervening already-pushed commits are
`483e917` (dependency verification metadata), `1f969b8` (Cloud Gradle routing), `c578a46`
(Cloud-side Gradle executor), `599961e` (Cloud launcher policy test), and `80b95d3` (migration
evidence). They establish the current Matt-Pocock/Codex-Cloud workflow baseline and were preserved.
At the final pre-documentation check the autonomous branch was 0 ahead / 0 behind its upstream and
the canonical branch remained exactly `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

**Public unauthenticated evidence.** Eight priority portals were re-fetched with bounded HTTPS GETs
and first-party static resources only. `justicia-sede-judicial` still exposes its global theme helper
with `SHA256withRSA`, `PAdES`, `mode=implicit`, `documentoDeclaracion`, `formFirmaBorrador` and
same-origin Storage/Retrieve servlets, but neither the generic trámite index nor five selected public
procedure pages contain the matching form DOM or a `firma()` binding. This remains insufficient for
a portal profile. `age-acceda` exposes a public `/certificado/valida` form, a server-issued changing
`afirma.formularioweb`, and `afirma.firmar(callback)`; its first-party helper file contains multiple
distinct signing functions, including a `SHA1withRSA`/`PAdES` `doSignSolicitud()`, but no public
source observed this generation proves that the wrapper calls that exact helper. The dynamic field
must not be hard-coded or inferred. SEPE, Ministerio de Justicia, Sevilla, Universidad de Sevilla and
Catastro exposed only descriptive/certificate or pre-authentication surfaces without a complete
algorithm/format/payload/callback contract.

**Asturias near-ready evidence.** The official public utility
`https://miprincipado.asturias.es/utilidades/comprobacion-firma` contains a concrete inline call
`MiniApplet.sign('SG9sYQ==', getAlgoritmoFirma(), 'XAdES', getParamsFirma(),
saveSignatureFunction0, showErrorFunction)` behind its simulated-signature button. The fixed payload
is Base64 for `Hola`, and the success callback decodes the returned signature into `signoutput0`.
Algorithm and extra parameters are delegated to first-party
`https://www30.asturias.es/Esign2/esign.jsp`; bounded retrieval of that host failed through the
available network path with `CONNECT tunnel failed, response 502`. No stored evidence defines
`getAlgoritmoFirma()` or `getParamsFirma()`, so the candidate was not promoted and no values were
guessed.

**Pipeline state.** The catalog remains 182 entries, 10 exact profile bindings and 172 unbound.
Inventory states remain 166 `BROWSE_ONLY`, 5 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`,
4 `VERIFIED_E2E`, 4 `INACCESSIBLE`, and 2 `UNSUPPORTED_PROTOCOL`; generated catalog states remain
94 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 5 `E2E_PENDING`, and 4 `E2E_VERIFIED`. The preserved
research buffer is 16 classified public surfaces; no unintegrated candidate is implementation-ready,
so native Codex/Luna implementation occupancy correctly remains 0/8 and no worker branch was
created. Generation 36 integrated zero portals. The ignored evidence packet is
`build/autonomous-evidence/g36-portal-research/CLASSIFICATION.md`, SHA-256
`c7a7f9d41a108fa0057d37924657624fd08bd82e80e7b6fc3c848cdd5d13496e`.

No Gradle command was run in generation 36 because no product behavior changed. No APK was
installed/launched; no device control, authenticated portal navigation, credentials, certificate
unlock/private-key material, real signing, form POST, upload, payment or administrative submission
occurred. Physical UGR/DGT acceptance and the pre-existing AEAT Client-TLS, JavaScript-dialog,
TalkBack/visual and supported-Linux Go-race gates remain manual/external.

## Portal G42 — JCCM catalog recovery, Cloud quota blocker, and research refresh — 2026-08-10

**Repository continuity and JCCM catalog slice.** Generation 42 re-established Termux access and
began from clean autonomous HEAD `6b70856a2740594d169fa85d68276aa0a03ea60d`, exactly synchronized
with `origin/agent/workspace-47-autonomous-20260803`; canonical remained
`9c99bbfb36e13f88231d56001ccef8c4cbbce128` and autonomous remained ahead 72 / behind 0 from that
merge-base. The durable local JCCM catalog branch was recovered rather than recreated. Its preserved
RED commit is `a9b12c1`; the existing local GREEN `13616f8` was found to have overwritten broad
JCCM surface `ES-PUB-0103` with the certificate-probe URL. A new red-capable generator test now
requires the broad `https://www.jccm.es/` surface to remain `BROWSE_ONLY` and binds the exact
certificate probe as a separate `ES-PUB-0183` surface. The corrected branch is published at
`cd839875e13b60ea8009bb2c9ab18d3482a8c40e` (`agent/g39-jccm-catalog`). Eight generator tests pass,
regeneration is byte-for-byte reproducible, `git diff --check` passes, and the added-content scan
found no secret/TLS-bypass/dependency-verification weakening pattern. This branch is intentionally
not integrated before the JCCM runtime/product slice is Cloud-verified.

**JCCM exact-page RED and infrastructure result.** The exact-path RED branch remains published at
`5eaad0966242fa30e35b8011ac3746c0012d9347`. Its `MiniAppletBridgeAdapterTest` requires the JCCM
profile to accept only the exact public start URL and reject another path, query and fragment. The
required Cloud-only focused submission was attempted through `$HOME/bin/w47-cloud`; Cloud task
creation failed before Gradle execution with HTTP 429 `Usage limit exceeded`. No local Gradle
fallback was used. Production `currentPageUrl` plumbing therefore remains unimplemented pending an
accepted Cloud RED.

**Public research refresh.** Current first-party Asturias evidence reconfirmed the public
`MiniApplet.sign('SG9sYQ==', getAlgoritmoFirma(), 'XAdES', getParamsFirma(), ...)` simulation; the
fixed payload decodes to `Hola`, while the algorithm and extra parameters remain unresolved because
the referenced `www30.asturias.es/Esign2/esign.jsp` could not be recovered through the available
network path. ACCEDA `/certificado/valida` reconfirmed a real public `afirma.firmar(callback)` hook
with a changing server-issued `formularioweb`; current first-party helpers expose multiple signing
branches, including `doSignSolicitud()` with SHA1withRSA/PAdES, but no loaded source proves that the
wrapper selects that branch. Justicia's first-party theme helper proves SHA256withRSA/PAdES/implicit
mode, but 11 current public procedure pages expose none of its expected DOM/call seam. MJusticia's
public pre-auth login module exposes an inactive `accAfirma` XAdES Detached certificate-flow branch,
but the current DOM omits that branch and none of the 10 directly loaded first-party scripts defines
`accAfirma.signData` or its algorithm. SEPE's official FAQ confirms the certificate-signature
AutoFirma handoff back to Sede but publishes no algorithm/format/payload/callback contract. No
candidate was promoted by inference. Comunidad de Madrid Registro now has an exact public launch URL,
but the next functional step is an upload/POST that was not crossed. Cuenta Digital exposes a
`firmar-documentos` microfrontend, but its published `SignsApiService` proves an authenticated
server-mediated POST boundary rather than a browser-local AutoFirma ABI; credential-like/static
authorization material observed in the public app configuration was deliberately not retained or
used. Detailed hashes and bounded findings are in
`docs/autonomous/2026-08-10-g42-portal-research-evidence.md`.

**Extremadura STA promotion in the research queue.** `extremadura-portal-tributario` remains
`BROWSE_ONLY`: its current model pages hand off to the common Sede and expose no portal-specific ABI.
By contrast, public `tramites.juntaex.es` pages directly load the current AutoScript/STA batch helper
and framework. The helper fixes SHA256withRSA, CAdES defaults, PAdES/XAdES format extras and the
trifásico pre/post/datareference input shape; `webAppsFwk.js` directly invokes
`STAAutofirmaLote.firmarLote(signInfo, ...)` and returns the opaque result through
`PRESENTAR_FIRMA` / `validationResponse`. The current AutoScript, STA batch helper and framework are
byte-for-byte identical to the same three public resources on `sede.melilla.es`.
`extremadura-tramites` is therefore implementation-ready in the research queue, with no catalog or
profile state change yet and no authenticated/E2E claim. It should reuse a generalized STA batch seam
only after the in-flight Melilla batch implementation is Cloud-verified and integrated.

**Cloud quota recheck.** A second bounded submission of the exact JCCM focused RED against published
SHA `5eaad0966242fa30e35b8011ac3746c0012d9347` again failed at Cloud task creation with HTTP 429
`Usage limit exceeded`; Gradle never started and no local fallback was used.

**Current catalog accounting.** The committed/reproducible main catalog remains 182 entries with 12
bound catalog surfaces and 170 unbound surfaces. There are 11 unique profile IDs because `us-sede`
is an intentional QA-only alias to `reg-age-redsara` via its exact `launch_url`; generator logic and
regression coverage explicitly support that alias. Inventory states are 164 `BROWSE_ONLY`, 7
`IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`, 4 `INACCESSIBLE`, and 2
`UNSUPPORTED_PROTOCOL`. Generated states are 92 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 7
`E2E_PENDING`, and 4 `E2E_VERIFIED`. The research buffer remains at least 16 classified public
surfaces. Generation 42 integrated zero portals; the corrected JCCM P1 work is published only on its
worker branch.

No APK was installed or launched; no ADB/device control, authenticated portal navigation,
credentials/private-certificate material, real signing, form POST, upload, payment or administrative
submission occurred. Physical UGR, DGT and Cantabria portal E2E; AEAT Client-TLS E2E; real-portal
JavaScript-dialog compatibility; TalkBack/physical visual accessibility; and Go race on supported
Linux remain manual/external gates.

## Portal G43/G44 — JCCM certificate-login integration and recovered acceptance — 2026-08-11

**Exact integration boundary.** The preserved JCCM work was integrated without replacing newer
Cantabria or REG-AGE history. The autonomous product checkpoint is
`0afd632d8b22691da7cde87c7e587fe8b49b306b`, eight commits ahead of the previously published
`d8c43aa3ff5b92ec93757b01d62af795679d58b6`. The sequence is `c63bea6` (contract RED),
`9731ad6` (profile/adapter GREEN), `88b413a` (exact-runtime-page RED), `0b79d60`
(`currentPageUrl` fail-closed GREEN), `643043f` (separate exact catalog surface), and three bounded
regression-alignment commits `b79f821`, `5370f5e`, `0afd632`. The runtime profile remains exactly
`jccm-certificate-login-probe`, `VERIFIED_CONTRACT` / `QA_ONLY`; release remains disabled. The
native bridge accepts the public probe only on the exact runtime URL
`https://ventanillaelectronica.jccm.es/administracion_electronica/formularios/identificacion.phtml`,
for exact origin, five decoded bytes `ABCDE`, `SHA1withRSA`, `CAdES`, and null/empty extra
properties. Wrong path, query, fragment, origin, profile, algorithm, format, payload, main-frame state,
or properties fail closed. No `FORMPROC.submit`, authenticated redirect, session/cookie transfer,
server upload, or administrative action was added.

**Catalog truthfulness.** Broad JCCM inventory surface `ES-PUB-0103` remains unbound
`BROWSE_ONLY`. Exact public certificate-probe surface `ES-PUB-0183` is separately bound to
`jccm-certificate-login-probe`, inventory `IMPLEMENTED_NOT_E2E`, generated catalog `E2E_PENDING`.
The recomputed catalog contains 183 entries, 13 bound surfaces, 12 unique profile IDs and 170 unbound
surfaces. Inventory counts are 164 `BROWSE_ONLY`, 8 `IMPLEMENTED_NOT_E2E`, 1
`VERIFIED_CONTRACT`, 4 `VERIFIED_E2E`, 4 `INACCESSIBLE`, and 2 `UNSUPPORTED_PROTOCOL`;
generated states are 92 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 8 `E2E_PENDING`, and 4
`E2E_VERIFIED`. The classified research buffer remains at least 16 surfaces. The exact next
implementation sequence is Sevilla ATSE, preserved Melilla STA, then research-ready
`extremadura-tramites`; Justicia, ACCEDA, SEPE, MJusticia and Asturias still need additional public
contract evidence.

**Gradle incident and terminal acceptance.** The one generation-43 Codex Cloud availability attempt
failed before Gradle with HTTP 429 `Usage limit exceeded`, activating the operator-authorized bounded
Termux fallback. The final clean-commit acceptance job `job_20260810_202832_2180e9fd` started with
`MemAvailable_kB=4199152` and ran one Gradle invocation with `--no-daemon --no-parallel
--max-workers=1`, JVM `-Xmx1536m -XX:MaxMetaspaceSize=512m`, and in-process Kotlin compilation.
Against exact SHA `0afd632d8b22691da7cde87c7e587fe8b49b306b`,
`verifyResolvedCoreVersion`, `verifyPortableAapt2Configuration`, `testDebugUnitTest`,
`testQaUnitTest`, `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, and
`assembleQaAndroidTest` all completed successfully. Terminal output is `BUILD SUCCESSFUL in 12m
59s`; 142 actionable tasks were reported (42 executed, 3 from cache, 97 up-to-date). No OOM,
thermal termination, TLS/checksum bypass, or dependency-verification bypass occurred. Post-gate
checks passed `git diff --check`, 9/9 public-catalog generator unit tests and byte-for-byte deterministic
catalog regeneration. A bounded net-diff scan found no private-key material, bearer/JWT material,
dependency-verification disabling, hostname/trust bypass, or session-persistence pattern.

**Direct Standards + Spec review.** Main-Watchdog review found no Critical or Important defect in the
JCCM net diff. The WebView shim is profile/origin/call scoped; the native bridge independently adds
the stricter exact-page check; parser/registry enforce `QA_ONLY`; the CAdES adapter independently
checks the exact profile/origin/algorithm/format/payload tuple; and the separate catalog row prevents
inflating the broad JCCM surface. Physical JCCM certificate-login/callback acceptance remains the
manual E2E gate and no `VERIFIED_E2E` claim is inferred.

No APK was installed or launched; no ADB/device control, authenticated portal navigation,
credential/private-certificate unlock, real signing, form submission, upload, payment or
administrative submission occurred.

## Portal G44 — Sevilla Cloud recovery and preserved pipeline reconciliation — 2026-08-11

**Sevilla Cloud availability and unresolved verdict.** The preserved Sevilla ATSE branch is clean and
exactly synchronized with its remote at `069c6fd73a19b54b92dc4771867fff712617301d`. Its existing
RED `108650f51765c2b59a74dee286928ea2e8f3cf65` defines the profile-scoped public shim contract and
`069c6fd...` contains only the minimal shim GREEN in `AfirmaJavascriptShim.kt` and
`afirma_shim.js`. Generation 44's single Cloud availability submission was accepted as task
`task_e_6a7a8bc263208323a3fbab7f5c11be4d` for exact SHA `069c6fd...` and the focused
`AfirmaJavascriptShimTest`; unlike generation 43, no HTTP 429 occurred. The task progressed from
`PENDING` to `READY`, proving Cloud accepts work again and therefore the phone-local Gradle fallback is
not active for generation 44. However, installed Codex CLI `0.148.0-alpha.6` exposes only Cloud
`exec/status/list/apply/diff`: `status` reports `READY` but does not expose the agent's terminal Gradle
report, and `diff` reports no diff for this read-only task. A separate blank Playwright session could
not retrieve the task UI (HTTP 403) and no bypass was attempted. Consequently `READY` is **not**
recorded as focused GREEN; Sevilla remains unintegrated until exact SHA/Gradle result is observable.
The shim diff itself passes `git diff --check` and a bounded added-content scan found no key/token,
TLS/trust bypass, dependency-verification bypass, or `authenticate(...)` implementation.

**Melilla preserved-state reconciliation.** Read-only inspection resolved the previously ambiguous
local/remote divergence without reset or rewrite. `agent/g36-melilla-batch-contract` is clean at local
`ce1b1639b322b616fb71cce12c73305db26e6a1a`, exactly one commit ahead of remote
`25df9f7ed5bef0387568d6c2db5c7083f154fa9b`. The sole local commit is
`fix(portal): use Android-compatible JSONObject key validation`; it changes only
`WebMessageBridge.kt`, replacing `json.keySet()` with `json.keys().asSequence().toSet()` for the exact
`MINIAPPLET_DOCUMENT_READY` key-set check. `git diff --check` passes. Prior Cloud task
`task_e_6a78dc14b2d48323887a6abf2ad48bce` still exposes only lifecycle `READY`, not a terminal
PASS/FAIL report, so its Gradle result is not inferred. The local fix is preserved and remains
unpushed pending a verifiable gate.

**Asturias bounded recheck.** The official public
`https://miprincipado.asturias.es/utilidades/comprobacion-firma` surface still returns HTTP 200 and
still exposes the fixed `MiniApplet.sign('SG9sYQ==', getAlgoritmoFirma(), 'XAdES',
getParamsFirma(), ...)` boundary. The required official helper
`https://www30.asturias.es/Esign2/esign.jsp` remains unavailable from the current routes: configured
proxy returns HTTP 502 on CONNECT and a direct no-proxy attempt fails DNS resolution. No algorithm or
extra-property value is inferred; the Asturias candidate remains research-only.

The catalog remains 183 entries / 13 bound / 12 unique profiles / 170 unbound, with the classified
research buffer at least 16. Portals integrated in generation 44 remain exactly one: the already
accepted and published JCCM certificate-login probe. The next implementation candidate is Sevilla
ATSE once its exact Cloud result is retrievable, followed by the preserved Melilla STA slice and then
research-ready `extremadura-tramites`.

No APK was installed or launched; no ADB/device control, authenticated government-portal navigation,
credentials/cookies/bearer/certificate-unlock/private-key material, real signing, upload, payment,
form POST, or administrative submission occurred.

## Portal G44 — bounded Asturias/SEPE research follow-up — 2026-08-11

Evidence packet: `docs/autonomous/2026-08-11-g44-portal-research-evidence.md`.

Asturias remains research-only: its official public signature-check page still publishes the fixed
`Hola` / `XAdES` simulation but delegates algorithm and extra properties to the inaccessible
`www30.asturias.es/Esign2/esign.jsp` helper. Proxy access returns CONNECT 502 and direct access cannot
resolve the host; no contract value was inferred.

SEPE also remains research-only after bounded GET-only traversal of current public procedure pages and
three concrete launches. `PSolicitudUnicaWEB/solicitudUnica` and
`PBajaPrestacion/flows/bajaprestacion` terminate at the official protected-resource authentication
page before any signing ABI is visible. `DServiciosPrestanetWEB/CertificadosPrestaWeb.do` is public
but exposes only authentication choices and a POST transition; it publishes no AutoScript/MiniApplet
algorithm/format/payload/callback tuple. No form was submitted. No profile or catalog status changed.

No new implementation-ready candidate was promoted. The classified research buffer remains at least
16; exact implementation priority remains Sevilla ATSE after terminal Cloud GREEN evidence, preserved
Melilla STA, then `extremadura-tramites`.

## Portal G44 — ACCEDA/MPTMD handoff research — 2026-08-11

The public ACCEDA `idp/509` route linked by SEPE is an ambit chooser, not a signing surface. Its two
observed official targets are MPTMD public procedure pages. Their loaded first-party
`ac2-formularios.js` (SHA-256
`ac1983eb5ed614c9f446ebbfbea38160a4d28ea99080cbb2ed0adf8a62d1c7cc`) proves a later generic
AutoFirma sequence only after an expediente-creation POST: it calls `doSignAsPromise(file, nifSol)`
and then uploads the resulting signed file through a separate POST. Neither POST was executed.
`doSignAsPromise` is not defined by the exact four same-origin scripts loaded in the unauthenticated
page and no algorithm/format tuple is exposed there. Consequently neither `age-acceda` nor MPTMD
surface `ES-PUB-0072` is promoted to implementation-ready; `ES-PUB-0072` is retained as a new
research lead only. No inventory/catalog mutation was made.

## Portal G44 — Justicia/MJusticia current-public recheck — 2026-08-11

`docs/autonomous/2026-08-11-g44-portal-research-evidence.md` now records two additional bounded
GET-only rechecks. The current Sede Judicial `/tramites` surface publishes six same-origin
procedure/article pages; all returned HTTP 200 and none binds the known `firma.js`
`SHA256withRSA`/`PAdES` helper through `documentoDeclaracion`, `formFirmaBorrador`, MiniApplet, or
AutoFirma markup. The helper remains library-level evidence only.

MJusticia `sede2` login idp/75 also remains unchanged at the product boundary: the current public DOM
contains no certificate controls, while the same first-party `index.js` module still references
`accAfirma`, `XAdES Detached`, and implicit mode. The other nine same-origin scripts loaded by the page
define neither `accAfirma` nor a signing algorithm. No profile/status promotion is justified.

The MPTMD public script set was additionally checked for explicit source maps or dynamic script
references that could reveal `doSignAsPromise`; none was found. The one dynamic `<script>` creation is
only repeat-form inline-script cloning. `ES-PUB-0072` remains a research lead, not an implementation
candidate.

## Portal G44 — Deputación de Ourense STA transition research — 2026-08-11

A bounded GET-only pass strengthened inventory surface `ES-PUB-0165` without changing its status.
The already-recorded official `PTS2_FIRMASELEC` URL and its directly linked `PTS2_FIRMA` page both
return HTTP 200 on STA `2602.0.4`; neither page nor their 16 loaded first-party scripts exposes a
local AutoFirma/MiniApplet signing tuple.

Two exact public catalogue details published by that surface were fetched. One procedure currently
offers a real "Con Autofirma" entry. Its first-party `catserv.js?ver=2602.0.4` (SHA-256
`ffdf496a7486c190e4dc2b5e33ae785d99acb1a01daecb1aef2a3069360227e3`) shows that
`submitFormulario(false,true)` only sets `autoFirma=true`, points the form to the configured auth
`/frame.jsp`, and submits. The POST was not performed. Because no algorithm/format/payload/callback
contract is public before that boundary, `diputacion-ourense-sede` remains `BROWSE_ONLY` and is a
research lead only. No inventory/catalog/profile mutation was made.

## Portal G45 — actual-state reconciliation and Extremadura STA refresh — 2026-08-11

Generation 45 began by re-running `prepare_task`, `git fetch --prune origin`, and the required
repository/worktree checks. The actual autonomous main was newer than the generation-44 prose handoff:
`agent/workspace-47-autonomous-20260803` was clean and exactly synchronized with origin at
`1208a2774d6a6ad7994b6c6f3d590b0b072998e0`, divergence `0/0`, while canonical
`origin/feature/ws024-secure-tunnel-20260728` remained exactly
`9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

The published main history after `7c4e302...` contains five Sevilla ATSE implementation/TDD commits:
`1f97c48bcfcc75363a0b2e0bf5931023da72ba53` (shim GREEN),
`c909a4a1ce1ed39cc196d31880c38bc46b14adef` (native-bridge RED),
`06316274733c7f2aa62638ca441e80dd5c36776d` (native-bridge GREEN),
`84f75e7c9c5130a30c85c4b66c2757dca7d2112e` (XAdES Enveloping adapter RED), and
`1208a2774d6a6ad7994b6c6f3d590b0b072998e0` (adapter implementation). These commits are preserved as
actual repository state; they are **not** recorded as accepted Gradle GREEN merely because they are
pushed. The current Cloud task `task_e_6a7aa19ae4a08323b0f8200f3f9584bc` progressed from `PENDING`
to lifecycle `READY`, but the installed supported CLI still exposes only lifecycle metadata and no
terminal stdout/exit-code/observed-SHA report. `READY` therefore remains insufficient acceptance
evidence. No phone-local Gradle, local Gradle daemon, or local Kotlin compiler was used.

Preserved worker state remains unchanged: Sevilla worker `069c6fd73a19b54b92dc4771867fff712617301d`
is clean and remote-identical; Melilla worker is clean at local
`ce1b1639b322b616fb71cce12c73305db26e6a1a`, one commit ahead of remote
`25df9f7ed5bef0387568d6c2db5c7083f154fa9b`. The Melilla local commit remains intentionally unpushed
pending verifiable terminal Cloud evidence.

Because Sevilla/Melilla cannot yet cross the required Gradle acceptance boundary, generation 45 used
independent portal-first work rather than local fallback. A bounded GET-only refresh of the three
first-party `tramites.juntaex.es` STA resources reconfirmed the exact generation-42 hashes and
contract. `autoscript.js`, `sta-autofirma-lote.js`, and `webAppsFwk.js?ver=2605.0.2` all returned HTTP
200 and remain byte-for-byte unchanged. The exact hashes and contract lines are recorded in
`docs/autonomous/2026-08-11-g45-extremadura-refresh.md`.

`extremadura-tramites` (`ES-PUB-0109`) therefore remains implementation-ready in the research queue,
but no production/profile/catalog mutation was made because its intended shared STA seam still
depends on verified Melilla integration. Catalog accounting remains 183 entries, 13 bound surfaces,
12 unique profile IDs and 170 unbound surfaces; generated states remain 92 `CATALOGED`, 73
`DISCOVERED`, 6 `BLOCKED`, 8 `E2E_PENDING`, and 4 `E2E_VERIFIED`. The classified research buffer
remains at least 16 surfaces.

No APK was installed or launched; no ADB/device control, authenticated government-portal navigation,
credential/cookie/bearer/certificate-unlock/private-key material, real signing, form POST, upload,
payment, or administrative submission occurred.

**Murcia bounded public recheck.** The current CARM Sede landing page and procedure `385` page remain
public HTTP 200 surfaces, but their exact loaded same-origin JavaScript is generic site plumbing and
contains no AutoScript/MiniApplet algorithm/format/callback contract. The procedure exposes current
`/presentador/inicio/385/...` electronic-start links; a bounded GET of one start and the previously
catalogued AutoFirma test page both reach the site's WAF boundary before a signing runtime is public.
No challenge value was retained and no WAF bypass was attempted. `murcia-sede` (`ES-PUB-0113`)
therefore remains `BROWSE_ONLY` / research-only with no profile or catalog promotion. Detailed bounded
evidence is in `docs/autonomous/2026-08-11-g45-murcia-research.md`.

**Sevilla profile RED Cloud task and result-retrieval blocker.** Commit
`44244f96933dbccfcd90bfa066eaf57e283c263a` adds only the exact QA-only Sevilla ATSE profile parser
contract test. The corresponding Cloud-only focused submission is
`task_e_6a7aa3b8def08323970f6b302ea0ad64`, created with
`:app:testDebugUnitTest --tests dev.junta.firmamobile.profile.SiteProfileCatalogParserTest.preservesTheExactSevillaAtseQaOnlyCertificateLoginContract`
against that exact pushed SHA. It progressed from `PENDING` to lifecycle `READY`. As with the Sevilla
adapter task `task_e_6a7aa19ae4a08323b0f8200f3f9584bc` for exact SHA
`1208a2774d6a6ad7994b6c6f3d590b0b072998e0`, the supported CLI exposes no terminal Gradle stdout,
exit code, observed checkout SHA, or conclusion, so neither task is accepted as RED/GREEN evidence.
A bounded attempt to open the supported interactive `codex cloud` TUI through the Termux connector PTY
failed before rendering the task list because the PTY did not answer the terminal cursor-position
handshake; no private endpoint or web-auth bypass was attempted. No local Gradle/JVM/Kotlin fallback
was used or authorized.

A generated untracked `error.log` from Cloud CLI diagnostics was inspected before deletion. It contained
CLI account/cursor diagnostics and repository/environment metadata, but the bounded scan found no
private-key, bearer, Authorization/Cookie, GitHub-token, OpenAI-key, or MCP-token material. The file was
removed and is not retained in the worktree.

**Uncommitted Sevilla profile GREEN preservation.** During generation-45 finalization, the main
worktree acquired unstaged changes in exactly `SiteProfileCatalogParser.kt` and
`config/site_profiles_v1.json`. They implement the expected narrow Sevilla QA-only profile/parser
contract corresponding to the published RED at `44244f96933dbccfcd90bfa066eaf57e283c263a`: exact start
URL/origin, `VERIFIED_CONTRACT`, `QA_ONLY`, `SIGN + LEGACY_SHA1`, RSA/digital-signature certificate
rules, `SHA1_WITH_RSA`, XAdES `ATTACHED`, null endpoint/mode and no extra properties. Static
`git diff --check` passes, the JSON parses and contains exactly one Sevilla profile, and a bounded
added-line scan found no sensitive material, insecure HTTP, trust/hostname bypass, or dependency-
verification disablement. These production changes were deliberately left **unstaged, uncommitted and
unpushed** because the corresponding Cloud RED task exposes only lifecycle `READY`, not a verifiable
expected test failure. Preserve this local work; do not discard or publish it until the required Cloud
evidence boundary is satisfied.

**Navarra / La Rioja bounded public research.** Current Navarra Registro General and its official
"Firmar documentos" help page both return HTTP 200. The help confirms AutoFirma is used by some
Navarra procedures, but the public registry surface exposes no exact algorithm/format/payload/callback
ABI before authentication; `navarra-sede-registro-general` remains `BROWSE_ONLY`. La Rioja's current
Oficina Electrónica publicly embeds an exact official file-signing utility under `ias1.larioja.org`.
The embedded GET response exposes a server-side file-input form followed by POST and contains no
AutoScript/MiniApplet contract before that boundary. Ephemeral server-session values from the raw
response were not retained and the raw iframe body was deleted; no upload or POST was performed.
`larioja-oficina-electronica` remains `BROWSE_ONLY`. Detailed safe findings are in
`docs/autonomous/2026-08-11-g45-navarra-larioja-research.md`.
