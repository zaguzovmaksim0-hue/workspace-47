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
