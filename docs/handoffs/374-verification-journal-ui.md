# #374 roadmap slice: real local CMS verification journal and explicit SAF JSON export

Base: `53f7e14cf13c0f04b40272b7ccfff81f1b768664` (exact, no refresh).
Branch: `feat/verification-journal-ui-20261019`.
Worktree: `~/.cache/w47-verification-journal-ui-20261019`.
Final local head: the commit containing this handoff; its full SHA is emitted in
its implementation handoff and recorded in
`~/.cache/w47-verification-journal-ui-checks-20261019/handoff.json` after committing.
This avoids a self-referential commit hash in a committed file.

The parent frozen worktree `~/.cache/w47-user-verification-integration-20261019`
was not changed. No main refresh or integration onto the parent's newer draft
candidate was attempted. Parent owns integration and all GitHub actions.

## Connected user flow and ownership

AppRoot creates exactly one `InMemoryOperationJournal` with ordinary `remember`,
not saved state, a static/global, a ViewModel, an application container or disk.
The small `verificationJournalHome` composable owns the independent verification
and journal dialog visibility and their home entries. No imported/unlocked
certificate is required. Closing the journal dialog does not clear its session
journal; a fresh AppRoot composition receives a new journal.

The real OfflineVerificationController now accepts a direct `(LocalOutcome) -> Unit`
terminal callback. Only an accepted explicit Verify owns an active generation.
Accepted completion consumes that ownership before publishing its callback;
cancel/reselection/clear-original/close during an active operation consumes it
with one CANCELLED. Obsolete worker completions cannot add COMPLETED or FAILED.
Invalid/busy clicks, picker failure without a started operation, recomposition
and no-start dismissal do not append. There is no StateFlow-to-journal collector.
The existing UI state collection is used only for rendering.

The home callback appends only `OperationKind.VERIFY`, the coarse outcome and an
Instant; the journal allocates sequence numbers. VERIFIED maps to COMPLETED only
after local CMS integrity acceptance; every other accepted result maps to FAILED.
This does not establish certificate trust, validity, revocation, timestamp validity
or remote acceptance. The callback has no string/error/data/request payload and
is best-effort: even a throwing journal callback cannot change verification or
cancellation behavior. Existing crypto, input limits and consent behavior remain
unchanged.

The SecureOn, scrollable journal dialog displays only sequence, ISO UTC time,
local CMS operation type and localized coarse outcome. It explicitly states that
this is this session's local CMS checks, not complete signing history, and that
entries disappear when the app is recreated or closed. Buttons have at least
48 dp height, text semantics, and export-status live-region announcements.
A clear requires a user click. Display and export snapshots are detached;
clear cannot revoke previously issued snapshots or exported copies.

## Export and privacy boundary

Export starts only with an explicit button click, snapshots the currently displayed
metadata and uses the unchanged typed OperationJournalJson serializer. The
CreateDocument contract is `application/json` with a fixed generic proposed name.
A per-ticket composition/registry key captures an immutable generation ticket,
not a mutable latest-request slot. Replayed/cancelled/cleared/dismissed or obsolete
callbacks cannot open a destination or consume a newer request.

Each owned export is bounded to 16,384 bytes. The original default 32 / maximum 64
entry bounds and serializer allowlist are unchanged. No free-text serialization
or additional journal record fields were added. Provider open/write/flush/close
run on Dispatchers.IO through the tested controller/writer seam, with use-based
stream closure and cancellation checks before open and after a blocking open.
The transient URI stays in the private destination adapter, never display state,
history, saved state, diagnostics or an event. No destination-name query,
automatic write/share, clipboard, application network call or persistable URI
grant is added. EXTRA_LOCAL_ONLY is a hint, not a guarantee about provider internals.

Cancel/clear/dismiss/launch failure clear pending owned bytes. Successful/failed
IO clears its bytes in finally, with completion cleanup also covering a coroutine
cancelled before starting. A nonpreemptible call retains its in-use buffer until
it returns, then clears it; JVM, serializer-string and provider internal copies
cannot be guaranteed zeroized. Cancellation cannot undo an already running write.
A provider write/flush/close failure can leave a partial (or already complete)
external file. Fixed localized messages explicitly acknowledge this; the app does
not promise atomic external writes, deletion or revocation. No selected filename,
URI/path, raw exception, document/hash/certificate/origin/request ID is displayed
or logged by this slice.

## Actually executed focused checks

Artifacts: `~/.cache/w47-verification-journal-ui-checks-20261019/`.
`run-pure.sh` reproduces the cached JVM-only harness; no Android/Gradle is involved.

- Observed RED before GREEN at the actual controller terminal-event seam:
  missing callback API, four cancellation/reselection failures, and a throwing
  callback that interrupted cancellation. Logs: `event-api-red.log`,
  `event-cancel-red.log`, `event-isolation-red.log`; final event GREEN has 9 tests.
- Observed export API/clear RED, then a behavioral stale-SAF-after-clear RED
  (expected zero opens, observed one), followed by generation invalidation GREEN.
  Logs: `export-api-red.log`, `export-clear-api-red.log`,
  `export-clear-stale-red.log`, `export-first-green.log`.
- Cached kotlinc, `-Werror -jvm-target 17`, JUnit 4.13.2, coroutines 1.11.0 and
  BC 1.85: **68/68 pure tests passed**. This includes the existing **26 CMS UI**
  and **20 journal** regressions plus **9 terminal-event** and **13 export** tests.
  Actual synthetic expired self-signed CMS and malformed CMS pass through the real
  verifier/controller into the real journal. Export cases cover owned snapshots,
  clear, all coarse outcomes, cancel/replay/generation, no-operation callbacks,
  never-started jobs, nonpreemptible open/write cancellation, partial/flush/close
  failures, byte cleanup, 32/64 capacities, exact/over 16 KiB bounds and real-thread
  worker confinement. Logs: `pure-green.log`, `pure-final.log`, `compile-final.log`.
- **56/56 Python/XML locale/config tests passed**, including dedicated journal
  resources plus the strict ES/EN/CA/GL/EU completeness/format/escaping checks.
  Locale RED: `journal-locales-red.log`; final: `locales-final.log`.
- Cached checksum-pinned detekt **1.23.8: zero findings** across all **11 touched
  production files**, using the unchanged existing baseline. No suppression or
  baseline addition was made. SHA-256:
  `2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`.
  Logs: `detekt-final.log`, `detekt-final.xml`.
- `git diff --check` passed. The final handoff records the exact base/head,
  verification artifacts and clean local tree, not canonical CI acceptance.

## Added but not compiled or run here

Five synthetic Compose/Robolectric tests in VerificationJournalDialogTest and
VerificationJournalSafTest cover honest scope/coarse rows, explicit accessible
clear, detached display snapshots, dialog close/reopen, and the actual AppRoot →
SAF input → real Verify → session journal → CreateDocument/resolver export flow.
They also exercise no-start dismissal, empty fresh AppRoot composition, generic
JSON contract, no auto read/export, no persisted grants/name query, cancelled and
late/replayed/cleared callbacks, and provider failure without private-error display.

These Android/Compose/SAF tests were deliberately **not compiled or executed**
locally. No Android Gradle/build/bootstrap/download, device/emulator, E2E/portal,
real certificate, APK or permanent key was used. Broad exact-head Android/CI and
provider/device acceptance remain for parent-owned integration; the focused JVM,
XML and source-level detekt results do not replace them.

## Boundaries and remaining roadmap

AGENTS, CONTRIBUTING, CONTEXT, the Matt implementation/TDD skill text and Actions
verification policy were read. No Codex CLI/runtime/Cloud, review/audit/reviewer
session, push, PR, merge, workflow dispatch or GitHub state mutation was used.
Application/MainActivity/AppContainer/modules, LegalInformationDialog, signing,
batch/auth/import/cache/TLS/browser/origins, dependencies, workflows and baseline
were not changed.

This is one bounded implementation slice, **not completion of the #374 roadmap**.
The roadmap remains unfinished/unchecked: full operation history and actual
SIGN/COSIGN/COUNTERSIGN/BATCH/IDENTIFY producers remain TODO, as do broader
verification formats and trust-related checks. Per the parent's corrected tracker
context, GitHub #374 was historically CLOSED/completed at
2026-10-18T19:04:11Z; this handoff does not describe the issue as open or change it.
