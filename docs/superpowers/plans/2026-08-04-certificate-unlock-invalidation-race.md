# Certificate Unlock Invalidation Race Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make explicit certificate-cache invalidation linearizable against in-flight writes and prevent certificate session publication before cache commit completes.

**Architecture:** Add a monotonic invalidation generation inside `EncryptedCertificateUnlockCache` and validate it after every successful physical write. Reorder the ViewModel success path so cache persistence and cancellation complete before the non-suspending session/UI commit.

**Tech Stack:** Kotlin, coroutines, `AtomicLong`, Android `AtomicFile`, StateFlow, Robolectric/JUnit, Gradle Debug/QA gates.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- Preserve canonical branch `feature/ws024-secure-tunnel-20260728` at `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- No APK installation/launch, ADB/device control, portal interaction, credentials, real certificate material, signatures, upload, payment or submission.
- Preserve the encrypted 24-hour retention design, Android Keystore policy and password zeroization.
- Do not change certificate selection, PKCS#12 validation, signing, WebView, portal profile, release or dependency policy.
- Evidence documents change only after fresh final verification.

---

### Task 1: Reproduce cache resurrection after explicit clear

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateUnlockCacheTest.kt`

**Interfaces:**
- Consumes: `EncryptedCertificateUnlockCache.store()`, `clear()`, and
  `CertificateUnlockRecordStorage`.
- Produces: deterministic regression proving that a clear occurring during a blocked
  physical write must leave no record and make the store report failure.

- [x] **Step 1: Add a blocking storage test double**

Use `CountDownLatch` fields for `writeStarted` and `allowWrite`. Its `write()` must
signal start, await release with a bounded timeout, then copy the record. Its `clear()`
must zero and remove any current bytes without waiting for the blocked writer.

- [x] **Step 2: Add the failing race test**

Start `cache.store(...)` with `async`, wait for `writeStarted`, call `cache.clear()`,
release the writer, then require both `store.await() == false` and `storage.read() ==
null`.

- [x] **Step 3: Run RED**

Run:

```bash
./gradlew --no-daemon --rerun-tasks testDebugUnitTest \
  --tests dev.junta.firmamobile.certificate.CertificateUnlockCacheTest.clearDuringBlockingWriteCannotResurrectUnlockRecord
```

Expected: FAIL because the current store returns true and recreates a record after
clear.

---

### Task 2: Reproduce premature session publication

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/CertificateViewModelTest.kt`

**Interfaces:**
- Consumes: `CertificateViewModel.unlock()`, `CertificateSession`, and a blocking
  `CertificateUnlockCache` test double.
- Produces: regression proving session identity stays unavailable while cache store is
  suspended and remains locked after cancellation.

- [x] **Step 1: Add a blocking cache test double**

Its `store()` must signal `storeStarted`, suspend inside `withContext(NonCancellable)`
until `finishStore` completes, then return true. `clear()` records invalidation but does
not release the store.

- [x] **Step 2: Add the failing ordering test**

Initialize a selected certificate and successful identity, call `unlock()`, use
`runCurrent()` until cache store suspends, then require
`session.identityForSigning() == null`. Call `lock()`, release the store and advance
until idle; require session null, locked UI and cleared caller password.

- [x] **Step 3: Run RED**

Run:

```bash
./gradlew --no-daemon --rerun-tasks testDebugUnitTest \
  --tests dev.junta.firmamobile.ui.CertificateViewModelTest.sessionUnlockIsNotPublishedBeforeCacheCommitCompletes
```

Expected: FAIL at the pre-commit session assertion because current production calls
`session.unlock()` before awaiting cache store.

---

### Task 3: Implement the minimum concurrency repair

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/CertificateViewModel.kt`

**Interfaces:**
- Produces: `clear()` invalidation generation and post-write validation; cache-first,
cancellation-checked, non-suspending session/UI commit.

- [x] **Step 1: Add cache invalidation generation**

Add `private val invalidationGeneration = AtomicLong(0)`. Capture its value at the
start of `store()`. Advance it at the start of `clear()`. After a successful write,
compare generations; on mismatch clear storage and return false.

- [x] **Step 2: Reorder ViewModel unlock commit**

Move `session.unlock(result.identity, expiresAt)` after `unlockCache.store(...)` and the
following `ensureActive()`. Keep session unlock immediately followed by
`mutableState.value = CertificateUiState.Unlocked(...)` with no suspension between.

- [x] **Step 3: Run both focused GREEN tests**

Run both exact tests from Tasks 1 and 2 and require `BUILD SUCCESSFUL`.

- [x] **Step 4: Run relevant Debug+QA suites**

Run:

```bash
./gradlew --no-daemon --rerun-tasks \
  testDebugUnitTest testQaUnitTest \
  --tests dev.junta.firmamobile.certificate.CertificateUnlockCacheTest \
  --tests dev.junta.firmamobile.ui.CertificateViewModelTest \
  --tests dev.junta.firmamobile.certificate.CertificateSessionTest
```

Require all selected tests passing in both variants.

---

### Task 4: Full verification, evidence, commit and push

**Files:**
- Modify after verification: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify after verification: `docs/security-roadmap.md`
- Modify after verification: `docs/test-report.md`
- Modify after verification only if wording changes: `docs/threat-model.md`
- Modify after verification: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

- [x] **Step 1: Run full gates**

Run full pin/unit/assembly, forced lint, Python, Android artifact, release fail-closed
and Go test/vet/build gates. Remove the generated relay binary and verify no release
APK remains.

- [x] **Step 2: Review and record exact evidence**

Inspect the complete diff and run whitespace, exact-scope, sensitive-content,
personal-data and unsafe WebView/TLS/backup scans. Update only evidence supported by the
observed RED/GREEN/full-gate results.

- [x] **Step 3: Stage exact files and verify**

Fetch origin, verify branch/upstream/canonical/divergence, stage only the milestone
surface, run `git diff --cached --check` and staged added-line security scans.

- [ ] **Step 4: Commit and push**

Create one atomic commit, push to
`origin/agent/workspace-47-autonomous-20260803`, fetch again, and require exact remote
SHA plus divergence `0/0` before starting another audit line.
