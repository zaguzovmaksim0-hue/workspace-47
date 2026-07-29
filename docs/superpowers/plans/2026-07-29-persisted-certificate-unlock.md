# Persisted 24-Hour Certificate Unlock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After one successful PKCS#12 password entry, restore the selected certificate automatically for at most 24 hours across Activity recreation, app dismissal, process death, force-stop, device reboot, and app update without storing the password in plaintext.

**Architecture:** Keep the private key only in the existing in-memory `CertificateSession`. Persist only the PKCS#12 password as an AES-256-GCM ciphertext in `noBackupFilesDir`; the non-exportable AES key lives in Android Keystore and is usable only while the device is unlocked on API 28+. Bind the ciphertext to the exact stored certificate reference and to immutable issued/expiry timestamps through GCM AAD. On startup, decrypt once, reload the PKCS#12 through the existing validated loader, zero temporary password buffers, and reuse the original expiry so process restarts cannot extend the 24-hour window.

**Tech Stack:** Kotlin/JVM 17, Android API 26–36, Android Keystore, AES/GCM/NoPadding, `AtomicFile`, coroutines, JUnit 4, Robolectric.

## Global Constraints

- Retention is exactly 24 hours from the last successful manual password entry; automatic restoration never renews it.
- Never persist the PKCS#12 bytes, private key, certificate chain, plaintext password, portal payload, signature, cookie, or token.
- The encrypted record is excluded from cloud backup and device transfer by storing it in `Context.noBackupFilesDir`; the existing manifest also keeps backup disabled.
- Manual lock, clear session, certificate replacement, forget, expired record, reference mismatch, malformed record, authentication-tag failure, or failed cached unlock clears the persisted record immediately.
- Backgrounding, app dismissal, process death, force-stop, reboot, and ordinary app update do not clear a valid persisted record.
- Password conversion must avoid constructing a `String`; all temporary `CharArray` and plaintext `ByteArray` buffers are zeroed.
- Android Keystore encryption lets `Cipher` generate a random GCM IV; decryption accepts only the stored 12-byte IV and 128-bit tag.
- No new third-party cryptography dependency.

---

### Task 1: Define expiry-preserving session and encrypted-cache contracts

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateSession.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateSessionTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateUnlockCacheTest.kt`

**Interfaces:**
- Produces: `CachedCertificateUnlock`, `CertificateUnlockCache`, `EncryptedCertificateUnlockCache`, `CertificateUnlockRecordStorage`, `CertificateUnlockKeyProvider`.
- Produces: `CertificateSession.unlock(identity, expiresAt)` with a maximum 24-hour session window.

- [ ] Write failing tests for a 24-hour default session, explicit original expiry, encrypted round-trip, expiry, reference binding, future timestamps, tamper rejection, and plaintext-canary absence.
- [ ] Run focused tests and confirm RED due to missing cache contracts and old two-hour default.
- [ ] Implement bounded binary record parsing, reference digest, UTF-8 conversion without `String`, AES-GCM authenticated encryption, zeroization, and expiry-preserving session unlock.
- [ ] Run focused tests and confirm GREEN.
- [ ] Commit `feat: add encrypted certificate unlock cache`.

### Task 2: Add Android Keystore and no-backup persistence

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateUnlockCacheTest.kt`

**Interfaces:**
- Consumes: cache contracts from Task 1.
- Produces: `AndroidKeystoreCertificateUnlockCache(Context)` wired as `JuntaFirmaApplication.certificateUnlockCache`.

- [ ] Write failing tests for atomic storage failure/cleanup behavior through injected storage and key providers.
- [ ] Run focused tests and confirm RED.
- [ ] Implement `AtomicFile` storage under `noBackupFilesDir` and an Android Keystore AES-256 key restricted to GCM/no-padding, encryption/decryption, and unlocked-device use where supported.
- [ ] Wire the cache in `JuntaFirmaApplication` without changing release trust or signing configuration.
- [ ] Run focused tests and confirm GREEN.
- [ ] Commit `feat: persist certificate unlock with Android Keystore`.

### Task 3: Restore and clear the cache through the certificate lifecycle

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/CertificateViewModel.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/ui/CertificateViewModelTest.kt`

**Interfaces:**
- Consumes: `CertificateUnlockCache` and `CertificateSession.unlock(identity, expiresAt)`.
- Produces: automatic restoration during ViewModel initialization and foreground recovery without renewing expiry.

- [ ] Write failing tests for successful process-recreation restore, expired restore, failed cached password, manual unlock persistence, manual lock clearing, certificate replacement clearing, forget clearing, and memory-pressure recovery.
- [ ] Run focused tests and confirm RED.
- [ ] Implement startup restoration, exact 24-hour persistence after successful manual unlock, immediate lifecycle clearing rules, and automatic foreground/memory-pressure recovery.
- [ ] Pass the production cache through `CertificateViewModel.Factory`; call foreground restoration from `MainActivity.onStart()`.
- [ ] Run focused tests and confirm GREEN.
- [ ] Commit `feat: restore certificate unlock for 24 hours`.

### Task 4: Full verification and physical-device QA

**Files:**
- Modify: `docs/test-report.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

- [ ] Run `./gradlew testDebugUnitTest testQaUnitTest --no-daemon`.
- [ ] Run `./gradlew lintDebug lintQa assembleDebug assembleQa assembleQaAndroidTest --no-daemon`.
- [ ] Verify APK signatures, alignment, hashes, backup policy, and absence of plaintext canaries/private material.
- [ ] Install QA APK with `pm install -r`, preserving app data; verify installed hash equals the built APK.
- [ ] Enter the real password once manually, close/force-stop the app, relaunch it, and verify the certificate restores without another password prompt.
- [ ] Re-enter the verified Oficina Virtual profile and confirm the already authenticated portal remains usable or can authenticate again without re-entering the PKCS#12 password; do not capture personal data.
- [ ] Record only sanitized evidence and limitations.
- [ ] Commit `test: verify 24 hour certificate unlock`.
