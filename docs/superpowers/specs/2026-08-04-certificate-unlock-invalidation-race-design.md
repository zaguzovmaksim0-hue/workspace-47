# Certificate unlock invalidation race design

## Finding

The persisted certificate-unlock cache is intentionally invalidated by manual lock,
session clear, certificate replacement and forget. `CertificateViewModel.unlock()`
currently publishes the identity into `CertificateSession` before awaiting
`CertificateUnlockCache.store()`. The production cache performs encryption and an
atomic file write on an IO dispatcher; the write includes blocking flush and `fsync`.

A lock can therefore cancel the unlock coroutine, clear the cache and lock the session
while an already-started blocking cache write remains in progress. When that write
later completes, it can recreate the encrypted 24-hour unlock record after the clear.
The following cancellation check prevents the UI from becoming unlocked, but there is
no rollback for the resurrected record. A later process recreation or memory-pressure
restore can then unlock without a password despite the explicit manual lock.

The same ordering temporarily exposes a signing identity through
`CertificateSession.identityForSigning()` while the UI is still `Unlocking` and cache
commit has not completed.

This contradicts the documented lifecycle contract that explicit manual lock and
session clear eliminate the persisted unlock record and require the password again.

## Scope

Production:

- Modify
  `app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt`
  to make explicit invalidation linearizable against an in-flight store.
- Modify `app/src/main/java/dev/junta/firmamobile/ui/CertificateViewModel.kt`
  so session/UI unlock commit occurs only after cache persistence returns and the
  coroutine is still active.

Tests:

- Modify
  `app/src/test/java/dev/junta/firmamobile/certificate/CertificateUnlockCacheTest.kt`
  with a deterministic blocked-write invalidation race.
- Modify `app/src/test/java/dev/junta/firmamobile/ui/CertificateViewModelTest.kt`
  with a deterministic suspended cache-store ordering test.

Evidence after fresh full verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/threat-model.md` only if the implemented boundary materially changes its
  wording; otherwise preserve the existing correct contract.
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No retention duration, cache encryption, Android Keystore key policy, certificate
selection, PKCS#12 parsing, signing, WebView, portal profile, release or dependency
policy changes.

## Approaches considered

1. **Monotonic cache invalidation epoch plus commit reordering — selected.** Each
   `clear()` advances an atomic epoch before deleting storage. A store captures the
   epoch before blocking work and, after a successful write, verifies that no clear
   occurred; if invalidated, it deletes the stale record and returns false. The
   ViewModel then commits session and UI only after store returns and cancellation is
   checked. This is local, deterministic and fail-closed.
2. Synchronize `clear()` and the complete store under one monitor. This would make a
   UI-thread clear wait for encryption, file flush and `fsync`, creating unbounded
   blocking and potential ANR risk.
3. Rely on coroutine cancellation. Blocking file/crypto operations are not guaranteed
   to observe cancellation before writing, so this cannot enforce the documented
   invalidation boundary.
4. Add a second post-cancellation clear only in the ViewModel. It reduces one caller's
   exposure but does not make the cache abstraction itself safe against any concurrent
   explicit invalidation.

## Required behavior

1. `CertificateUnlockCache.clear()` wins over every store that began before that
   invalidation, even when the physical write completes afterwards.
2. A store invalidated during its blocking work returns `false` and leaves no record.
3. A store with no intervening invalidation retains existing successful behavior.
4. Cache failure or invalidation remains fail-closed; availability loss is acceptable
   but stale unlock resurrection is not.
5. `CertificateSession` must not expose the new identity before cache persistence has
   returned and the unlock coroutine has passed its cancellation check.
6. Once session unlock begins, no suspending operation may occur before the matching
   `Unlocked` UI state is published.
7. Password buffers retain existing best-effort zeroization and completion cleanup.
8. Manual lock, selection, forget, background/memory lifecycle and restore semantics
   remain otherwise unchanged.

## Concurrency model

`EncryptedCertificateUnlockCache` owns an `AtomicLong` invalidation generation.
`store()` captures the current generation before entering the IO operation. `clear()`
increments the generation before deleting the storage. After a successful write,
`store()` compares the current generation with its captured generation. On mismatch it
clears the just-written record and returns `false`.

The final comparison is sufficient without holding a lock: if clear precedes the
comparison, the mismatch removes the late record; if clear follows the comparison, its
own deletion occurs after the store commit. Concurrent stores may conservatively cause
a cache miss if one invalidated store clears a later write; this is fail-closed and does
not expose a stale unlock secret.

`CertificateViewModel.unlock()` awaits `unlockCache.store()`, checks cancellation, then
updates `CertificateSession` and `mutableState` without another suspension. Cache store
return value does not change current product behavior: the app may remain unlocked in
memory even when persistence is unavailable, but a cancelled/invalidated operation may
not publish the identity.

## Verification strategy

- Add the deterministic blocked-write cache test and observe RED on stale record
  resurrection.
- Add the deterministic ViewModel ordering test and observe RED because the current
  session is unlocked while store is suspended.
- Implement only the epoch and ordering changes.
- Run focused cache/ViewModel tests in Debug, then relevant Debug+QA suites.
- Run full Android pin/unit/assembly, lint, Python, artifact, release fail-closed and
  Go test/vet/build gates.
- Review complete and staged diffs, whitespace, exact scope, sensitive-content,
  personal-data and unsafe WebView/TLS/backup patterns before commit and push.
