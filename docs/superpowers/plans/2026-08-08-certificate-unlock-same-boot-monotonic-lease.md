# Certificate Unlock Same-Boot Monotonic Lease Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent civil-clock rollback or device reboot from extending/restoring the persisted certificate-unlock authorization window while preserving same-boot process recovery.

**Architecture:** Keep civil expiry as a conservative secondary cap, add an authenticated Android same-boot lease (`BOOT_COUNT` + `elapsedRealtimeNanos`) to persisted records, and hand the exact remaining duration into a `CertificateSession` lease in the same Android elapsed-realtime domain. Legacy cache records fail closed instead of being upgraded without trustworthy monotonic evidence.

**Tech Stack:** Kotlin, Android API 26+, `SystemClock`, `Settings.Global`, AES-GCM authenticated records, coroutines, JUnit/Robolectric, Gradle.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- TDD is mandatory: observe the intended RED before each behavior-bearing production step.
- Reboot invalidates persisted auto-unlock; same-boot process death remains recoverable within the original lease.
- Production certificate lease time must use `SystemClock.elapsedRealtimeNanos()` so deep sleep consumes the window.
- Never persist/log plaintext passwords, PKCS#12/private-key material, signatures or certificate bodies.
- Preserve current fail-closed cancellation, storage, Keystore, signing, TLS, WebView, profile and release boundaries.

---

### Task 1: Make the in-memory certificate session monotonic

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateSessionTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateSession.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt`

**Interfaces:**
- Add constructor dependency `monotonicNanos: () -> Long = MonotonicSecurityTime::nowNanos` for injectable JVM tests.
- Add internal `CertificateUnlockLease(expiresAt, observedAtMonotonicNanos, lifetimeNanos)` with fail-closed validation/remaining checks.
- Add internal `createUnlockLease(expiresAt, lifetime)` and `unlock(identity, lease)` so callers can preserve an original lease across asynchronous work.
- Production `JuntaFirmaApplication` constructs `CertificateSession(monotonicNanos = SystemClock::elapsedRealtimeNanos)`; the existing process-local default is not the production 24-hour Android clock.

- [x] **Step 1: Write the failing rollback test**

Add a mutable monotonic test clock and a regression that unlocks for ten minutes, advances both clocks near expiry, rewinds civil time into the apparent validity window, advances monotonic time beyond ten minutes, and expects `identityForSigning()` to return null. Instantiate `CertificateSession(..., monotonicNanos = monotonic::nowNanos)` so unchanged production cannot satisfy the test.

- [x] **Step 2: Run the focused test and observe RED**

Run:
`./gradlew testDebugUnitTest --tests 'dev.junta.firmamobile.certificate.CertificateSessionTest' --rerun-tasks --no-daemon --console=plain`

Expected: compile/test failure attributable to the missing monotonic session contract, with no production mutation yet.

- [x] **Step 3: Implement the minimum monotonic session lease**

Use `MonotonicSecurityTime.durationNanos`, `isExpiredOrInvalid` and `remaining`; lock when either `clock.instant() >= expiresAt` or the injected monotonic lease expires/rolls back. Keep the public state exposing only summary + civil `expiresAt`. Wire Android production session time to `SystemClock.elapsedRealtimeNanos()`.

- [x] **Step 4: Run `CertificateSessionTest` GREEN**

Require all session tests to pass; do not change the test to match implementation output.

---

### Task 2: Bind persisted unlock recovery to the current device boot

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateUnlockCacheTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt`

**Interfaces:**
- Add internal `CertificateUnlockBootTime(bootCount: Int, elapsedRealtimeNanos: Long)` and injectable `CertificateUnlockBootTimeSource`.
- `EncryptedCertificateUnlockCache` receives the same-boot source and an injected session monotonic source; Android production supplies `SystemClock.elapsedRealtimeNanos()` for both elapsed observations/session leases.
- V2 authenticated header stores civil issue/expiry, boot count, elapsed-realtime observation and reference digest.
- `CachedCertificateUnlock` carries a `CertificateUnlockLease` whose lifetime is only the remaining authenticated same-boot duration.

- [x] **Step 1: Write failing cache regressions**

Add tests that inject deterministic boot/elapsed time and prove: (a) civil rollback after issue cannot restore once monotonic age reaches the original duration; (b) a changed boot count rejects and clears the record; (c) same boot before exact monotonic expiry restores; (d) exact elapsed boundary fails closed. The new constructor/time-source arguments intentionally do not exist before production mutation.

- [x] **Step 2: Run `CertificateUnlockCacheTest` and observe RED**

Run:
`./gradlew testDebugUnitTest --tests 'dev.junta.firmamobile.certificate.CertificateUnlockCacheTest' --rerun-tasks --no-daemon --console=plain`

Expected: failure attributable to the missing same-boot/elapsed contract.

- [x] **Step 3: Implement v2 authenticated same-boot records**

Use `JFMUC002`; include `bootCount` and `elapsedRealtimeNanos` in AAD. Reject invalid/unavailable time, boot mismatch, monotonic rollback and exact/over expiry. Unknown/v1 records clear. Android source reads `Settings.Global.BOOT_COUNT` and `SystemClock.elapsedRealtimeNanos()`. Keep all byte/password zeroization and invalidation-generation barriers.

- [x] **Step 4: Run cache tests GREEN**

Require encryption/tamper/reference/cancellation/write-barrier legacy coverage plus the new same-boot cases to pass.

---

### Task 3: Preserve remaining lease across cache-to-session recovery

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/CertificateViewModelTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/CertificateViewModel.kt`

**Interfaces:**
- Manual unlock asks `CertificateSession` to create the original lease before asynchronous cache persistence and publishes the identity only with that lease.
- Cached restore passes `CachedCertificateUnlock.lease` to the session; it never creates a fresh 24-hour session from `expiresAt`.
- Production session/cache leases share the Android elapsed-realtime domain through the Task 1/2 wiring; ViewModel does not create a second monotonic domain.

- [x] **Step 1: Write a failing restore-handoff regression**

Make the fake cache return a lease with a short remaining duration, advance the shared monotonic test clock while `gateway.unlock` is in progress (or before session publication), and require the ViewModel to end locked with no signing identity instead of renewing from civil `expiresAt`.

- [x] **Step 2: Run `CertificateViewModelTest` and observe RED**

Run:
`./gradlew testDebugUnitTest --tests 'dev.junta.firmamobile.ui.CertificateViewModelTest' --rerun-tasks --no-daemon --console=plain`

Expected: failure proving current restore path recreates session authorization from civil expiry.

- [x] **Step 3: Implement the minimum lease handoff**

Create/pass `CertificateUnlockLease` without copying password material; if the lease is expired/invalid when session publication occurs, clear/lock and return the ordinary locked state. Preserve existing password zeroization/cancellation behavior.

- [x] **Step 4: Run focused session/cache/ViewModel GREEN in Debug and QA**

Run both variants for the three named test classes and require zero failures/errors/skips.

---

### Task 4: Full verification, evidence, atomic commit and push

**Files:**
- Modify after GREEN only: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify after GREEN only: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Modify after GREEN only: `docs/security-roadmap.md`
- Modify after GREEN only: `docs/test-plan.md`
- Modify after GREEN only: `docs/test-report.md`
- Modify after GREEN only: `docs/threat-model.md`

- [x] **Step 1: Run adjacent and complete Android gates**

Run runtime dependency locks/core/AAPT2, full Debug+QA JVM, lintDebug/lintQa, assembleDebug, assembleQa and assembleQaAndroidTest. Parse exact XML counts and lint counts.

- [x] **Step 2: Run non-Android/artifact/release gates**

Run Python unittest discovery, Go `test ./... -count=1`, `vet ./...`, relay build, Android artifact verification and release-signing fail-closed without private signing inputs. Hash the three APKs, remove generated relay, confirm zero release APKs.

- [x] **Step 3: Inspect and document only observed evidence**

Run `git diff --check`, complete diff review, changed-line sensitive/private-material scans and protected-boundary scans. Update T5 to state same-boot recovery and reboot invalidation; do not claim physical/device/portal E2E.

- [x] **Step 4: Re-run focused tests and policy gates after evidence edits**

Require the three G16 suites and `CiPolicyTest` GREEN, then inspect exact staged scope and staged diff.

- [ ] **Step 5: Commit and push atomically**

Fetch and verify no remote divergence, commit only the G16 scope, push to `origin/agent/workspace-47-autonomous-20260803`, fetch again, and require exact local/remote SHA equality, divergence `0/0`, clean worktree and unchanged canonical ref.

### Review follow-up: preserve the original persistence observation

- Add a cache regression whose store is intentionally delayed after the original monotonic
  observation; after the original lease horizon, a civil-clock rollback must not permit restore.
- Extend the cache store contract with the original session-lease monotonic observation and pass
  it from `CertificateViewModel`.
- In encrypted storage, authenticate that original observation, reject current elapsed realtime
  behind it, and reject a store that already consumed the full retention window.
- Add focused coverage for elapsed-realtime rollback, unavailable boot-time evidence and legacy
  record rejection where those branches are already implemented.
- Re-run the full G16 focused and acceptance gates before evidence completion.
