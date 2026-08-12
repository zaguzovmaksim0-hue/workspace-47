# Certificate unlock same-boot monotonic lease design

## Finding

`CertificateSession` expires an in-memory `UnlockedIdentity` only against civil `Clock/Instant`.
`EncryptedCertificateUnlockCache` authenticates a civil `issuedAt`/`expiresAt` pair and rejects a
restore before `issuedAt` or at/after `expiresAt`, but a backward wall-clock adjustment that
remains after `issuedAt` can extend both same-process signing availability and persisted unlock
recovery relative to real elapsed time.

This is a security-time defect. The existing documented cache contract also permits restoration
after a device reboot, but Android's monotonic same-boot clock resets across boot. A civil
timestamp alone therefore cannot provide a monotonic cross-boot 24-hour guarantee.

## Selected product contract

Narrow recovery semantics to a **same-device-boot monotonic lease**:

- A successful manual unlock may remain usable for at most the original 24-hour lease during the
  same boot, including deep sleep, process death and memory-pressure recovery.
- A device reboot invalidates persisted automatic unlock recovery. The encrypted record is cleared
  on the next restore attempt and the user must enter the PKCS#12 password again.
- A backward civil-clock change never lengthens an in-memory or persisted unlock lease. Existing
  civil issue/expiry validation remains as an additional conservative cap, so a forward civil jump
  may still shorten the lease.
- If the Android same-boot time source or boot counter is unavailable/invalid, persisted recovery
  fails closed. A successful manual unlock may still create the current in-memory session; failure
  to persist recovery must not become a signing bypass or retain plaintext.
- Existing `JFMUC001` records do not contain same-boot evidence and are therefore not trusted by the
  new policy. The new authenticated record format is `JFMUC002`; v1/unknown records are cleared on
  restore. This intentionally causes at most a one-time password re-entry after upgrade.

## Time model

Android production certificate-unlock time uses:

- `SystemClock.elapsedRealtimeNanos()` — monotonic time since boot, including deep sleep;
- `Settings.Global.BOOT_COUNT` — boot count (available from API 24; project minSdk is 26).

`System.nanoTime()` / the existing process-local `MonotonicSecurityTime` is appropriate for the
short signing/authorization windows that already use it, but Android's uptime-based clock is not
the selected 24-hour certificate lease source because deep sleep must consume the unlock window.
Existing G15/signing monotonic behavior is not changed.

The encrypted/authenticated v2 header binds `bootCount` and the elapsed-realtime observation made
for the stored lease alongside the existing civil issue/expiry and certificate-reference digest.
Restore requires the current boot count to match and elapsed-realtime age to remain strictly below
the authenticated lease duration. Elapsed-realtime rollback/invalid values fail closed.

`CertificateSession` carries a process-resident `CertificateUnlockLease` in the same Android
`elapsedRealtimeNanos` domain in production. It expires when either the civil expiry is reached or
same-boot monotonic elapsed time reaches the lease lifetime; monotonic rollback fails closed. A
restored cache entry converts the remaining authenticated same-boot duration into a session lease
**before** password-based gateway reload, so parsing/reload time cannot renew the remaining
authorization window.

For JVM tests and non-Android constructor use, time remains injectable. Production wiring must
explicitly use Android elapsed realtime for both session and cache so a lease is never compared
across different monotonic domains.

## Exact runtime scope

Production:

- `app/src/main/java/dev/junta/firmamobile/certificate/CertificateSession.kt`
  - add internal `CertificateUnlockLease` value/invariants;
  - inject monotonic time, with the existing process clock permitted as a test/portable default;
  - expose an internal lease-creation path so `CertificateViewModel` captures the original lease
    before asynchronous persistence/reload work;
  - expire on either civil or monotonic boundary.
- `app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt`
  - add injectable same-boot time source plus Android `BOOT_COUNT`/`elapsedRealtimeNanos` source;
  - authenticate/parse v2 boot-count + elapsed-realtime fields;
  - fail closed for boot change, rollback, expiration, unavailable time and legacy v1 records;
  - return a `CertificateUnlockLease` carrying only the remaining allowed duration;
  - Android wrapper uses `SystemClock.elapsedRealtimeNanos()` for the returned session lease too.
- `app/src/main/java/dev/junta/firmamobile/ui/CertificateViewModel.kt`
  - create the manual session lease before persistence work;
  - on restore pass the cache-owned remaining lease to the session rather than recreating a
    24-hour window from civil time.
- `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt`
  - construct the production `CertificateSession` with `SystemClock::elapsedRealtimeNanos`, making
    deep sleep consume the lease and matching the cache's Android monotonic domain.

Tests:

- `app/src/test/java/dev/junta/firmamobile/certificate/CertificateSessionTest.kt`
- `app/src/test/java/dev/junta/firmamobile/certificate/CertificateUnlockCacheTest.kt`
- `app/src/test/java/dev/junta/firmamobile/ui/CertificateViewModelTest.kt`

Evidence after GREEN/full verification only:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `docs/threat-model.md`

## Invariants and non-goals

- Do not persist PKCS#12 bytes, private-key objects, plaintext passwords, civil-clock change logs,
  or any new user/profile/portal identifier.
- Do not weaken AES-256-GCM authentication, Android Keystore ownership, reference binding,
  cancellation/invalidation generation barriers, zeroization, owner-only storage, backup/D2D
  exclusions, certificate validation, per-signature confirmation, signing, WebView, network, TLS,
  profile or release boundaries.
- Do not introduce network/GNSS time as a security oracle.
- Do not claim monotonic recovery across device reboot. Reboot is intentionally a lock boundary.
- Do not alter the existing short-lived signing/Client-TLS monotonic clocks as part of G16.
- Do not install/launch the APK or use a real certificate/password to validate this milestone.

## Acceptance

The milestone is complete only after observed TDD RED(s), focused GREEN for session/cache/ViewModel,
full Debug/QA JVM and lint/build, Python/Go/artifact/release fail-closed gates, complete diff and
sensitive-data review, atomic commit/push, fresh fetch, exact remote SHA equality, 0/0 divergence
and a clean autonomous worktree.

## Independent review follow-up — persistence observation ownership

Independent review found one real lease-origin defect before commit: the ViewModel creates the
session lease before cache persistence, but the cache currently samples elapsed realtime only
inside `store()`. Slow persistence can therefore move the persisted monotonic origin later than
the already-active session lease. The cache store contract must receive the original monotonic
observation from the session lease, reject a current same-boot clock that is behind that
observation, and authenticate the original observation rather than the later IO-start sample.
This keeps memory and persisted recovery on one original authorization horizon.

The review also raised a generation-clear interleaving after the final restore generation check.
That check is the restore operation's linearization point: a later `clear()` is ordered after that
restore. All production concurrent clear paths additionally cancel the owning ViewModel operation
before clearing, and restore crosses a cancellable dispatcher boundary. No new cache lock or
password lifetime is introduced for that non-defect.
