# Multiple saved certificate references — gated design handoff

Date: 2026-10-24

## Status, evidence and scope

**Design only. No production code, resources or tests are changed.** Task 8
explicitly prohibits implementation while either prerequisite is not merged.
The first phone commands checked `gh pr view 369 --json state` and
`gh pr view 408 --json state` (with an explicit repository selector).
Both returned **OPEN** at 08:31 UTC on 2026-10-24:

| Prerequisite | Observed head | State / consequence |
| --- | --- | --- |
| [#369: authenticated saved unlock][pr369] | `1c31d577a4f9f6c519c06f60a09ed8e6700cffed` | OPEN; native-auth cache contract is not integrated. |
| [#408: typed PKCS12 failures][pr408] | `a92df28d281b8bd9553a9b166f7ffc6e347821f2` | OPEN; import/error/UI surfaces are still changing. |

Audited fresh `origin/main`: `ae22e7aa15c6d09644287063db3d25a46d05f4a7`.
These are dated observations, not claims about future PR state. Start any later
implementation from newly fetched main only after **both** PRs are MERGED;
re-read their integrated contracts rather than cherry-picking either branch.
This handoff does not implement task 4, close the feature, or establish device,
portal, authentication or signing acceptance.

## Verified starting point

- [Reference storage][store] holds one `StoredCertificateReference` in the
  `certificate_reference` Preferences DataStore: URI, sanitized display name,
  MIME, optional size and optional public summary. It is not a PKCS12 vault.
- [Repository selection][repository] validates content URIs, metadata and
  persistable read permission; replacing a reference releases the previous
  URI grant. Successful unlock writes the summary back to that single record.
  Multi-record writes must not retain that replace-and-release behavior.
- [Current cache][cache] has one encrypted record and a reference digest over
  URI, display name, MIME and size. That digest is **not** proof of certificate
  identity or immutable provider contents.
- [Session][session] owns one unlocked identity, a bounded monotonic lease and
  an exact-certificate signing snapshot. Listing several references must not
  create several unlocked sessions or broaden identity compatibility.
- #369's unmerged [cache interface][auth-cache] adds explicit-restore and
  awaiting-authentication state, immediate invalidation and verified durable
  clearing. Its PR description specifies native authentication for save and
  restore. Preserve the merged implementation, not today's main behavior.
- #408's described diagnostics are typed and deliberately ambiguous where the
  provider cannot distinguish causes. A future list must reuse the merged
  errors, never inspect/display provider exception messages or add a loader
  fallback. Existing input, alias, chain and key-validation limits stay intact.

## Proposed storage: bounded references, not key material

Keep one catalogue in the existing app-private Preferences DataStore using a
versioned schema and a **single atomic edit** for records plus active selection.
A catalogue record contains an opaque stable entry ID, reference revision and
existing reference fields. Store the SHA-256 fingerprint of the exact leaf DER
only after a successful validated unlock; it is identity-binding metadata, not
trust/revocation evidence. No private key, password, PKCS12 bytes or chain dump
belongs in the catalogue. Do not widen backup/export eligibility or diagnostics.

Proposed initial bound: **16 saved references**, independently of the existing
16-certificate chain limit; this new UI/storage bound needs review before code.
Bound decoded size and field lengths as well as count; retain existing stricter
URI/name/input constraints. Reject overflow, duplicate IDs, unknown versions,
invalid active IDs and malformed data without partially loading a catalogue or
silently falling back to stale legacy selection. Use existing dependencies.

`activeId` is null or names exactly one surviving record. Duplicate exact URIs
reuse the existing record rather than consume another grant. Distinct URIs may
refer to the same certificate; never merge them by subject, issuer or public
key. Removing and later re-adding a URI creates a fresh entry ID. Explicit
replacement/refresh increments its revision and invalidates prior approval.
Provider changes cannot be detected by URI or size alone: revalidate the actual
certificate on each unlock and compare the pinned fingerprint before use.

### Legacy migration and failure recovery

Migrate a valid legacy reference once to a one-entry catalogue with that entry
active **and locked**. Preserve its grant and sanitized metadata; an old summary
is display metadata, not proof that the current document is usable. Write the
schema marker, entry and active ID and remove old scalar keys in one edit.
Migration is idempotent; process death before commit leaves the old state, and
a restart after commit must not duplicate the entry or generate another ID.

Do not decrypt, copy, relabel or renew a legacy password cache. Revoke its use
and verify durable clearing before completing migration; incompatible binding
formats require fresh authentication. If clearing cannot be verified, remain
locked and report a recoverable error instead of marking migration complete.
No read/write/grant failure may manufacture an empty successful migration,
restore an old identity or release a grant still owned by a surviving record.

## Active choice and state transitions

A list row has a stable identity and explicit Select and Remove actions. Public
summary and expiry are optional; unknown/unavailable is not a valid-unlocked
status. A selected row and an unlocked session are different concepts.

| User action | Catalogue / session result |
| --- | --- |
| Add through the document picker | Validate and persist the new reference; retain existing entries/grants. Do not change active identity or unlock automatically. Offer explicit selection, including for the first entry. |
| Select the already active unchanged row | No-op; do not renew lease, create approval or reauthenticate. |
| Select a different existing row | Immediately revoke old work and lock; commit the new active ID only after verified cache revocation. New selection stays locked. |
| Select an unavailable or unknown row | Remain locked with an actionable error; never fall back to another identity. |
| Remove an inactive row | Remove only that record; leave unrelated active session/cache unchanged. |
| Remove the active or last row | Revoke and lock first; remove it and set active ID to null atomically. Other entries remain saved but are not auto-selected. |

Use one serialized mutation path within the certificate module, not independent
UI edits. Capture entry ID, reference revision and selection/operation generation
for every async load, summary write, cache operation and native-auth request.
Serialize commit/clear phases, but do not hold the mutation gate across a native
prompt: selection/removal must still be able to revoke that pending request.
Recheck captured ownership on re-entry, after suspension and immediately before
publishing state or using an identity. Summary writes must be conditional updates of that exact surviving
record; cancellation alone must not let a delayed unlock overwrite a newer
reference, recreate a deleted row or change B's summary with A's result.

For a switch/active removal: synchronously advance generation, invalidate cache
use, cancel pending load/auth and clear the in-memory identity **before** disk or
Keystore I/O. Persist a non-secret pending-revocation marker before clearing
the cache; marked records cannot restore after process restart. If that marker
cannot be written, do not proceed. Verify durable cache revocation, then commit
catalogue mutation and remove the marker in the same edit. Explicit recovery
may remove a marker only after verified clearing, never by bypassing it.
On storage failure preserve the previously committed selection/row but keep it
locked; never roll back into an unlocked session. Clear the former session's
summary as well as its identity so A is not presented as locked B. An interrupted
transition restarts locked under the marker and #369's explicit-restore rule.
Apply the same marker/recovery rule to legacy cache retirement during migration.
Clear stale password
input and owned sensitive buffers on all success, rejection and cancellation
paths. Disable repeated UI actions while the transition is pending.

Switching is not consent. Invalidate old pending review/identity ownership via
existing controller/session seams; switching away and back, including between
two references containing the same DER certificate, must not revive a prior
approval. Preserve request-local [exact-certificate checks][exact-binding],
origin/profile checks, final consent and upload/send limits. If an operation is
already in a non-cancellable signing/delivery phase, reject the mutation as busy
rather than claim a lock can undo a sent result. If these guarantees require
changing shared portal protocols/adapters, stop and hand off, not partial code.

## Unlock cache and #369

The minimal slice keeps **one authenticated encrypted cache slot**, for the
active entry only, not an N-password vault. Extend its authenticated binding to
include the stable entry ID, reference revision, existing bounded reference
digest and validated exact leaf-DER fingerprint. A mutable display label,
subject/issuer, same public key, active list index or equal password is never
sufficient binding. On restore, verify record binding and, after the normal
loader, compare the actual certificate before unlocking or publishing summary.
Missing/unverifiable fingerprint, changed certificate, wrong entry/revision, tampering
or incompatible record format means locked plus explicit re-import/unlock;
never silently rebind the record. Format changes discard secrets rather than
migrate them around native authentication.

A switch clears/revokes the previous slot before committing the new selection;
switching back requires fresh user unlock. Removing inactive B must not clear
active A's unrelated slot. Removing active A revokes its slot before deletion.
Do not implement per-entry Keystore aliases, silent bulk decryption or a new
cache provider just to support the list.

Carry forward #369's accepted security contract after merge:

- API 30+: per-use Keystore authentication tied to the exact Cipher CryptoObject,
  with the allowed strong-biometric/device-credential policy; API 26–29 stays
  memory-only. No weaker fallback or fabricated native-auth success.
- Save as well as restore requires the native prompt; restore is an explicit
  user action on the same resumed host, not startup/foreground/memory-pressure
  automation. Preserve the bounded 60-second host approval and cancellation.
- Keep the existing 15-minute/1-hour/24-hour same-boot monotonic lease rules.
  Selection, list reads, summary refresh, restore and cancellation never renew
  the original expiry or convert an expired/rebooted record into a new lease.
- Guard completion after authentication with the captured generation and exact
  binding. An A prompt completed after A-to-B-to-A must not unlock/store A.
  A stale callback must not clear a newer valid record either.
- `invalidate()` denies use immediately; `clearAndReport() == false` means
  revocation is unverified. Do not proceed with an unlocked session, committed
  switch/deletion or success UI while durable revocation is unverified. Preserve
  #369's memory-only fallback only where its own verified-cleanup policy allows.

## Removal and URI permission ownership

Removal forgets the app's reference, not the user's provider document. Never
invoke provider delete, export, upload or overwrite. Release a persistable read
grant only after successful catalogue removal and only if no surviving owner
needs that exact URI; rollback after failed add releases only a newly acquired,
unowned grant. Do not release all app grants. A failed best-effort release
leaves a stale non-secret grant, not a reason to resurrect a removed record or
report that the source file was deleted. Report/safely retry cleanup without
logging URI, owner, serial, password or fingerprint. A storage failure leaves
the row present; active-row failure remains locked and accurately reported.

## Parallel task 4: expiry reminders

Task 4 is independently based on main. Do not read or modify its worktree,
cherry-pick its branch, or alter notification scheduling in this candidate.
The later integration owner must reconcile the certificate ViewModel/storage
surfaces after both prerequisites and the reminders candidate are integrated.

Proposed minimal semantics: reminders follow the explicitly active saved entry,
not all list rows. This is a product decision to confirm, not task 4's current
implemented contract. After a **committed** selection, removal or validated
summary change, its scheduler should reconcile from the authoritative snapshot
(active entry ID/revision, optional public expiry, existing user opt-in).
Ignore stale revisions, cancel obsolete active-entry schedules, and do not
change the user's permission/opt-in. An unknown expiry cannot create a reminder.
The scheduler must not unlock certificates or read PKCS12/passwords, and must
retain generic notifications without personal data. A failed mutation must not
announce B or leave A reminders presenting as B. Do not add a shared portal
protocol or speculative event bus to provide this future integration point.

## Behavioral acceptance for a later implementation

Use generated synthetic identities, fake document access/cache/authentication,
controlled coroutine dispatchers and real Preferences DataStore where relevant.
Test through the module interface, not Python substring assertions over Kotlin.

| Behavioral seam | Required observations |
| --- | --- |
| Catalogue/migration | Empty, one and several entries; stable IDs; duplicate URI; bound and malformed schema rejection; idempotent migration; atomic active ID; storage failure and process-restart recovery; no password-cache migration. |
| Selection | A unlocked to B is locked before suspended I/O; same-row no lease renewal; A-to-B-to-A rejects old completion, even for equal certificate DER; missing grant/provider fails closed; no fallback identity. |
| Removal | Inactive B leaves A usable; active/last removal locks and selects none; survivors keep references/grants; cancellation/storage failure is truthful; grant-release failure cannot recreate a row or delete the source document. |
| Cache/auth | A cannot restore B; a different certificate behind the same URI with identical metadata/password is rejected; tamper/reboot/expiry stays locked; no automatic prompt; native denial/cancel/host change/revocation failure stays locked; stale save/restore/clear cannot overwrite or erase newer work. |
| Async/consent | Explicit barriers before delayed load, summary write and native-auth completion; switch/delete in between cannot resurrect identity; previous consent cannot sign/send after reselection; non-cancellable delivery rejects mutation without retrying the send. |
| UI/localization | Stable row keys, active marker, empty/unavailable/error states, accessible Select/Remove/confirm actions; no unintended unlock/send; all new UI text in `values`, `values-en`, `values-ca`, `values-gl`, `values-eu`. |
| Reminder integration | After owner reconciliation: committed revision drives reschedule/cancel idempotently, failed/stale mutations do not; scheduler never reads secrets or auto-unlocks. |

Do not add dependencies, change locks/verification/signing/secrets, grow the
baseline, add suppressions or relax security/consent/origin/loader limits.
Future production scope should stay in certificate storage/repository/cache/
session, certificate ViewModel/state/list UI, necessary composition and the five
locale files. Inventory the actual merged files first: if it exceeds about
20 production files or needs shared portal protocol/adapter changes, keep a
reasoned handoff instead of implementing only the easy pieces.

## Validation boundary and remaining risks

This document is not an implementation/test success claim. This candidate's
local checks are documentation whitespace, relative-link existence and exact
single-file scope. Android/JVM behavioral tests are **planned, not run**, since
no behavior is changed. Use automatically triggered PR CI on the exact pushed
head SHA for integration evidence, without manual workflow dispatch. No full
phone Gradle, E2E, instrumentation, emulator, phone-local APK build, APK install/
distribution or physical-auth acceptance is authorized by this handoff. Ordinary
automatic CI artifacts are not installation/distribution acceptance.

Open risks: prerequisites may change their contracts; providers can revoke
permissions or replace content behind a URI; durable revocation can fail; auth
callbacks may arrive after selection/deletion; existing consent seams must be
proven to reject stale identity ownership; active-only reminder semantics need
owner confirmation. OEM native-auth acceptance remains separate and pending.
None of these gaps authorizes bypassing the gate or weakening checks.

[pr369]: https://github.com/zaguzovmaksim0-hue/workspace-47/pull/369
[pr408]: https://github.com/zaguzovmaksim0-hue/workspace-47/pull/408
[store]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/ae22e7aa15c6d09644287063db3d25a46d05f4a7/app/src/main/java/dev/junta/firmamobile/certificate/CertificateReferenceStore.kt
[repository]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/ae22e7aa15c6d09644287063db3d25a46d05f4a7/app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt
[cache]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/ae22e7aa15c6d09644287063db3d25a46d05f4a7/app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt
[session]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/ae22e7aa15c6d09644287063db3d25a46d05f4a7/app/src/main/java/dev/junta/firmamobile/certificate/CertificateSession.kt
[auth-cache]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/1c31d577a4f9f6c519c06f60a09ed8e6700cffed/app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt
[exact-binding]: ../autonomous/2026-10-03-exact-certificate-binding.md
