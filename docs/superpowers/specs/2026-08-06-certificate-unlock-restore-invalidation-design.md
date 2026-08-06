# Certificate unlock restore-invalidation design

## Finding

`EncryptedCertificateUnlockCache` already uses `invalidationGeneration` to prevent an
in-flight persistent `store()` from recreating a record after `clear()`. `restore()` does
not bind its work to that generation. `CertificateUnlockRecordStorage.read()` returns an
owned byte-array snapshot, so a restore that has already obtained that snapshot can keep
decrypting it even after a concurrent `clear()` has incremented the generation and removed
the persisted record.

This leaves the cache-level clear boundary asymmetric: a clear during an in-flight restore
can still allow that stale restore to return a password-backed `CachedCertificateUnlock`.
Higher layers cancel several user-driven operations, but the cache itself is process-shared
security state and should fail closed under its own concurrent `clear()` contract.

## Scope

Change only stale-restore invalidation in `EncryptedCertificateUnlockCache`. Preserve:

- AES-GCM record format, AAD, key provider and retention limits;
- reference digest matching and record bounds;
- cancellation propagation;
- existing zeroization of raw record, parsed fields and plaintext bytes;
- `store()` generation behavior;
- ViewModel/session lifecycle and persisted-unlock duration.

## TDD contract

Add a test storage whose `read()` copies the encrypted record, signals that the snapshot is
owned by the restore, and blocks before returning it. Start `restore()`, wait until the
snapshot exists, call `clear()`, then release the read. Current production code must prove
RED by returning a non-null cached unlock. The corrected behavior is `null`, with storage
still clear.

The minimum production change captures the invalidation generation at restore start and,
after decoding the password but before returning it, rejects and zeroes that password if
the generation changed. This closes the deterministic stale-snapshot race without changing
record serialization or higher-level policy.

## Exact files

Production:

- `app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt`

Test:

- `app/src/test/java/dev/junta/firmamobile/certificate/CertificateUnlockCacheTest.kt`

Evidence after GREEN:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
