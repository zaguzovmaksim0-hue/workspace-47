# Legacy Certificate Display-Name Read Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** prevent a certificate display name persisted by a pre-G33 app version from reintroducing Unicode bidi controls into trusted native UI after upgrade.

**Architecture:** centralize the already-approved G33 presentation policy in one internal pure helper. Apply it both when selecting a new certificate and when reading a persisted certificate reference, without adding a write/migration side effect to the read path.

**Tech Stack:** Kotlin, AndroidX Preferences DataStore, Robolectric/JUnit, Gradle.

## Global Constraints

- Work only on `agent/workspace-47-autonomous-20260803` in the autonomous worktree.
- Preserve canonical `origin/feature/ws024-secure-tunnel-20260728` at `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- Preserve the G33 Unicode set exactly: U+061C, U+200E..U+200F, U+202A..U+202E, U+2066..U+2069.
- Preserve existing C0/DEL filtering, 256-character bound, trimming, ordinary printable Unicode, and default fallback.
- Preserve raw trimmed provider filename use for octet-stream `.p12`/`.pfx` admission.
- Do not change URI/MIME/size/summary/SAF/PKCS#12/password/session/cache/signing/WebView/network/TLS/portal/dependency behavior.
- No APK install/launch, device control, credentials, authenticated portal actions, real signing, upload, payment, or submission.

---

### Task 1: Reproduce the legacy persisted-name bypass

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateReferenceStoreTest.kt`

**Interfaces:**
- Consumes: `PreferencesCertificateReferenceStore.read(): StoredCertificateReference?`
- Produces: a deterministic regression proving a pre-G33 `display_name` cannot cross the read boundary unchanged.

- [x] **Step 1: Seed a complete legacy DataStore record**

Use the public Preferences DataStore keys by their persisted names (`uri`, `display_name`,
`mime_type`) and set `display_name` to `cert\u202Eevil\u2066.p12`.

- [x] **Step 2: Assert the trusted read result**

Call `store.read()` and require `displayName == "certevil.p12"` while URI and MIME remain unchanged.

- [x] **Step 3: Run the exact Debug test and verify RED**

Run:
`./gradlew testDebugUnitTest --tests 'dev.junta.firmamobile.certificate.CertificateReferenceStoreTest.stripsBidiControlsFromLegacyPersistedDisplayName' --rerun-tasks --no-daemon --console=plain`

Expected: one assertion failure showing the stored U+202E/U+2066 still survive. Parse the generated
JUnit XML to confirm the failure is the intended display-name policy gap rather than setup or DataStore failure.

---

### Task 2: Share the display-name policy across selection and persisted reads

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateDisplayNamePolicy.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateReferenceStore.kt`

**Interfaces:**
- Produces: `internal object CertificateDisplayNamePolicy` with `sanitize(String?): String` and the existing default display-name constant.
- Consumes: `CertificateRepository.select()` and `PreferencesCertificateReferenceStore.read()`.

- [x] **Step 1: Move, do not broaden, the G33 predicate**

Implement the exact existing C0/DEL + closed bidi-control filter, 256-character bound, trim, and blank fallback in the shared internal object.

- [x] **Step 2: Keep selection behavior identical**

Replace the repository-private sanitizer with `CertificateDisplayNamePolicy.sanitize(rawDisplayName)` and retain `CertificateRepository.DEFAULT_DISPLAY_NAME` as the same public test-facing constant value.

- [x] **Step 3: Normalize only the returned persisted name**

In `PreferencesCertificateReferenceStore.read()`, pass the persisted `display_name` through the shared policy before constructing `StoredCertificateReference`. Do not call `dataStore.edit` from `read()`.

- [x] **Step 4: Run focused Debug+QA GREEN**

Run the new store regression and existing G33 `CertificateRepositoryTest` in both variants with `--rerun-tasks`; require zero failures/errors/skips.

---

### Task 3: Review and full verification

**Files:**
- Review the four production/test files from Tasks 1-2 and this subordinate spec/plan.

**Interfaces:**
- Consumes: the complete G34 diff.
- Produces: publication evidence with no unresolved Critical/Important review finding.

- [x] **Step 1: Obtain narrow independent review**

Review migration semantics, no read-side write, shared-policy exactness, octet-stream admission separation, Unicode/length/fallback compatibility, and test adequacy.

- [x] **Step 2: Run full Android/JVM gates**

Run runtime dependency locks, resolved core version, portable AAPT2, all Debug/QA JVM tests and aggregate JUnit XML. Then run `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, and `assembleQaAndroidTest`.

- [x] **Step 3: Run non-Android/artifact/release gates**

Run complete Python tests, Go test/vet/build, Android artifact verification, APK SHA-256 reporting and release-signing fail-closed. Remove the generated relay and require zero release APKs.

- [x] **Step 4: Run policy and security review**

Run `CiPolicyTest`, `git diff --check`, exact-scope review, complete diff inspection, changed-content sensitive-data scan and unsafe WebView/TLS/signing scan.

---

### Task 4: Evidence and publication

**Files:**
- Modify only evidence documents whose facts changed:
  `docs/autonomous/2026-08-04-audit-ledger.md`,
  `docs/handoffs/NEXT_CHAT_HANDOFF.md`,
  `docs/security-roadmap.md`,
  `docs/test-plan.md`,
  `docs/test-report.md`,
  `docs/threat-model.md`.

**Interfaces:**
- Consumes: verified G34 evidence.
- Produces: one atomic remotely verified G34 commit.

- [x] **Step 1: Record the narrow migration/read-boundary claim**

State that pre-G33 persisted names are normalized on read, no DataStore migration write was added, and no physical/device/portal evidence is inferred.

- [x] **Step 2: Re-run focused tests, `CiPolicyTest`, and `git diff --check` after evidence edits**

Require the same focused behavior and policy gates to remain green.

- [x] **Step 3: Fetch and stage exact G34 scope**

Reverify branch/HEAD/upstream/divergence/canonical, stage only G34 files, inspect the full staged diff, and rerun staged sensitive/unsafe scans.

- [ ] **Step 4: Commit and push**

Commit atomically as `fix(certificate): sanitize persisted display names`, push the autonomous branch, fetch, and require local/tracking/`ls-remote` SHA equality, divergence `0/0`, clean worktree, immutable canonical SHA, generated relay absent, and zero release APKs.

## Generation 31 pre-publication evidence

- RED `job_20260809_072053_79a11036` / parser `job_20260809_072421_6d6cc68d` confirmed the
  legacy persisted-name bypass. Focused GREEN `job_20260809_072506_a0d1a7a2` /
  `job_20260809_073101_28ecad0c` passed 20/20 per variant.
- Reviewer `worker-9` found no Critical/Important issue; one Minor notes that no explicit DataStore
  before/after snapshot assertion accompanies the source-inspected side-effect-free read path.
- Full runtime-lock/core/AAPT2 + JVM `job_20260809_073131_74318c66` /
  `job_20260809_073943_d784901b` passed 570/570 per variant. Lint/build
  `job_20260809_074007_0d7b0afc` passed 124/124 tasks, 0 lint errors / 26 warnings per variant.
- Python/Go `job_20260809_073139_d5df8daf`, artifact `job_20260809_075044_6379aee6`, release
  fail-closed `job_20260809_075113_55f3f9cf` and cleanup `job_20260809_075219_03557bd7` passed;
  generated relay is absent and release APK count is zero.
- A concurrent separate Termux job created three portal-priority documentation changes during the
  pre-evidence scope gate. They are preserved foreign work and are excluded from G34 staging. G34
  evidence documents are now updated. Post-evidence focused `job_20260809_075747_639efd2f`
  passed 20/20 Debug and 20/20 QA with zero failures/errors/skips; policy/scope/safety
  `job_20260809_075808_88ee788e` passed `CiPolicyTest` 20/20, `git diff --check`, exact 12-file
  G34 ownership with the three portal-priority files preserved foreign, zero raw bidi controls in
  G34 content, and no sensitive/unsafe added line. Exact staged verification
  `job_20260809_081345_5276077f` confirmed exactly 12 staged G34 files, exactly three preserved
  portal-priority foreign files, `git diff --cached --check`, zero raw bidi controls, no
  sensitive/unsafe added line, generated relay absent and zero release APKs.
