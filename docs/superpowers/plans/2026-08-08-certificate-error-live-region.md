# Certificate Error Live-Region Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make dynamic certificate-selection/unlock errors announceable through an assertive
Compose live region without changing focus, layout or certificate behavior.

**Architecture:** Pin the semantics contract at the real `AppRoot` error surface, observe RED on
unchanged production, then add one `LiveRegionMode.Assertive` property to `CertificateError`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 semantics, Robolectric, JUnit 4, Gradle.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on
  `agent/workspace-47-autonomous-20260803`.
- Preserve certificate validation/storage/unlock/signing/network/WebView/profile/release behavior.
- No focus request, copy/resource change, layout redesign, APK install/launch, ADB/device control,
  portal interaction, credential/private-certificate use, real signing, upload, payment or
  submission.
- No dependency/toolchain change.

---

### Task 1: Reproduce missing certificate-error announcement semantics

**Files:**
- Modify test first: `app/src/test/java/dev/junta/firmamobile/ui/AppRootTest.kt`
- Production later: `app/src/main/java/dev/junta/firmamobile/ui/AppRoot.kt`

- [ ] **Step 1: Add one AppRoot regression.**

Render a synthetic `CertificateUiState.Locked` with
`CertificateUiError.PASSWORD_INVALID_OR_FILE`; locate the existing Spanish error text and assert
`SemanticsProperties.LiveRegion == LiveRegionMode.Assertive`.

- [ ] **Step 2: Run only the new Debug regression with `--rerun-tasks` and require RED.**

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'dev.junta.firmamobile.ui.AppRootTest.certificateErrorIsAnAssertiveLiveRegion' \
  --rerun-tasks --no-daemon --console=plain
```

Expected: test executes and fails because unchanged production has no `LiveRegion` property.

- [ ] **Step 3: Implement the minimum semantic fix.**

Import `LiveRegionMode` and `liveRegion`, then add only
`Modifier.semantics { liveRegion = LiveRegionMode.Assertive }` to `CertificateError`'s `Text`.

- [ ] **Step 4: Run full `AppRootTest` in Debug and QA with `--rerun-tasks`; require GREEN.**

### Task 2: Full verification, evidence and atomic push

**Files:**
- Evidence: `docs/autonomous/2026-08-04-audit-ledger.md`
- Evidence: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Evidence: `docs/security-roadmap.md`
- Evidence: `docs/test-plan.md`
- Evidence: `docs/test-report.md`

- [ ] **Step 1:** Run fresh runtime-lock/core/AAPT2 + all Debug/QA JVM tests; aggregate XML.
- [ ] **Step 2:** Run `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`,
  `assembleQaAndroidTest`; record lint counts and APK SHA-256.
- [ ] **Step 3:** Run Python, Go test/vet/build, Android artifact verifier and release-signing
  fail-closed; remove relay and prove zero release APKs.
- [ ] **Step 4:** Inspect exact diff, run `git diff --check`, scan sensitive/unsafe additions and
  prove certificate/network/WebView/profile/release/dependency boundaries unchanged.
- [ ] **Step 5:** Update evidence with exact RED/GREEN/full-gate results and keep physical TalkBack
  behavior as a manual gate.
- [ ] **Step 6:** Re-run focused AppRoot Debug/QA tests, `CiPolicyTest`, and `git diff --check`.
- [ ] **Step 7:** Stage exact files, review staged diff, commit atomically as
  `fix(a11y): announce certificate errors`, push autonomous branch, fetch, and verify exact remote
  SHA, divergence `0/0`, clean worktree, canonical SHA unchanged, relay absent and release APK
  count zero.
