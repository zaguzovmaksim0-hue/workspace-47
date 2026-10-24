# Issue #374: offline CMS/CAdES integrity UI implementation

Base: `6ad3408b123ddae7e3655527128669c0a1a49b36`.
Branch: `feat/offline-verification-ui-20261019`.
Worktree: `~/.cache/w47-offline-verification-ui-20261019`.

## Connected user flow

The independent home entry beside Legal opens a SecureOn dialog. It is available
without importing or unlocking a certificate. AppRoot changes are limited to two
imports, one ordinary remembered visibility flag, its dialog and its entry.
LegalInformationDialog and certificate/signing/import/browser/TLS/coordinator code
are unchanged.

1. Select a local CMS/CAdES file using an explicit SAF OpenDocument action.
2. Optionally select the original with a separate OpenDocument action. A selected
   empty original remains a present zero-length array, not null. It can be removed.
3. Press Verify. Selection itself does not open, read or verify the document.
4. The actual existing OfflineCmsVerifier runs through the bounded loader and
   controller. Attached CMS plus any extraneous original, including empty content,
   retains the verifier's fail-closed INVALID outcome. Detached missing content has
   its own actionable message.

Only fixed localized outcomes and a successful signature count from 1 to 8 are
shown. Failure never exposes a partial-success count. Success uses neutral text,
not a trusted-signer badge. Certificate trust, validity, revocation and timestamps
are explicitly not checked. PDF/XML are not supported by this flow.

Default Spanish resources are in their own XML file, with exactly matching new
keys in EN/CA/GL/EU. No dependencies, baseline, workflows, origins, keystores or
signing code were changed.

## Resource and privacy boundary

- The application makes no verification network requests. OpenDocument requests
  local-only files. EXTRA_LOCAL_ONLY is a provider hint, not an enforceable promise
  about another application's internals; providers remain outside this boundary.
- Sources keep temporary private URI references only. No persistable grant is
  taken, no display name is queried, and no application saved-state/ViewModel
  persistence or history/logging is added. Only SIZE is queried.
- Both metadata size and asset descriptor length are checked, followed by an
  actual limit-plus-one bounded read: CMS 1 MiB, original 512 KiB. Unknown or lying
  declared lengths cannot bypass the actual bound. Streams/cursors/descriptors
  are closed. The returned snapshot is allocated only after closing the stream.
- IO and parsing/crypto run on the worker dispatcher, never the UI dispatcher.
  Generation tickets plus structured cancellation reject stale picker results
  and stale verification completions after replacement, cancel or dismissal.
- A process-wide coroutine mutex allows only one active provider/crypto operation,
  including across dialog reopening. Cancelled waiters do not accumulate blocking
  reads. An uninterruptible provider or crypto call can delay a later operation;
  closing/cancelling the UI does not promise immediate native-call termination.
- Owned read scratch and verification snapshots are zeroed in finally. An active
  nonpreemptible call retains its in-use snapshot until it returns, then clears
  it without publishing the obsolete result. Provider, BC and JVM internal copies
  cannot be guaranteed to be zeroized. The dialog states the cancellation limit.
- URI, filename, document/digest/certificate contents and raw exception text never
  enter display state, displayed errors or application diagnostics/history.

## Actually executed focused checks

Local artifacts: `~/.cache/w47-offline-verification-ui-checks-20261019/`.

- Test-first loader and controller seams produced observed missing-API RED logs
  before production code, then GREEN. The English locale test also produced an
  observed missing-file-group RED before translations were added.
- Cached Kotlin 2.4.20 compilation, `-Werror -jvm-target 17`, cached BC 1.85,
  coroutines 1.11.0 and JUnit 4.13.2: **26/26 pure tests passed** in six classes.
  Compiler diagnostics were empty. The actual verifier/provider, not a replacement
  verification model, was compiled and used by the integration tests.
- Synthetic stream tests cover exact and excessive bounds, absent/negative/lying
  lengths, descriptor bounds, zero progress, empty data, read/close failures,
  cancellation, stream closure and scratch clearing. Controller tests cover no
  auto verification, null versus empty, stale selection/completion, dismissal,
  replacement, cancellation, count bounds and fixed error mapping. Real-thread
  tests cover off-thread IO/crypto and serialization after a nonpreemptible call.
- Actual synthetic CMS through the controller covers expired self-signed attached
  integrity, detached empty content, missing content, wrong original, extraneous
  attached original, malformed CMS and unsupported SHA-1. No real certificates,
  documents or permanent keys were used.
- **54/54 XML/Python locale/config tests passed**, including all four strict locale
  completeness/format/escaping suites.
- Scoped detekt **1.23.8: zero findings** over all nine new production files and
  AppRoot, with the existing baseline unchanged. Cached CLI SHA-256:
  `2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`.
  New Compose functions have only conventional FunctionNaming annotations.
- `git diff --check` passed. Final exact commit and clean-tree status are supplied
  with the implementation handoff, not substituted for canonical CI evidence.

Key logs: `loader-red.log`, `controller-red.log`, `locale-red.log`,
`pure-final.log`, `compile.log`, `locales-green.log`, `detekt-fixed.xml`.
`run-pure.sh` reproduces the narrow cached JVM harness without Android/Gradle.

## Added but not executed here

Seven Compose/Robolectric regression tests in OfflineVerificationEntryTest,
OfflineVerificationDialogTest and OfflineVerificationPickerDialogTest cover the
independent entry and reopening, explicit action, honest outcomes including expired
self-signed integrity, all bounded status labels, missing/empty/extraneous content,
private-error canaries, and the production SAF registry/resolver path with no read
before Verify and no persisted permission.

No Android Gradle/build/bootstrap/download, Android compilation, device/emulator,
E2E/portal, APK, release-key or canonical CI run was performed. These UI tests and
exact-head Android/CI acceptance remain for the parent. No review/audit/reviewer
session, Codex execution, push, PR or GitHub mutation was performed.

This is an implemented integrity-only UI candidate, not completion of all offline
verification in issue #374. Real CI/UI evidence is still required. PDF/XML, trust,
certificate validity, revocation and timestamp validation remain TODO/out of scope.
