# Dedicated Client TLS Subframe Navigation Confinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Confine modern dedicated Client TLS subframes to the existing allowed origins while preventing frame-unowned blocked-navigation callbacks from mutating top-level UI.

**Architecture:** Reuse `ClientAuthWebViewClient.isAllowed()` for every modern navigation request. Disallowed requests always abandon the one-shot grant and are consumed; application notification is limited to authoritative modern main-frame requests. The deprecated callback stays fail-closed but UI-silent because it has no frame metadata.

**Tech Stack:** Kotlin, Android `WebViewClient`/`WebResourceRequest`, Robolectric, JUnit 4, Gradle.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- Preserve exact Client TLS allowlists, profile/release status, certificate validation, preference clearing, TTL/epoch and one-shot behavior.
- No APK installation/launch, ADB/device control, credential/certificate use, authenticated portal interaction, real signing, upload, payment or submission.
- No dependency or toolchain changes.

---

### Task 1: Reproduce the dedicated Client TLS subframe origin bypass

**Files:**
- Modify test first: `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthWebViewClientTest.kt`
- Production later: `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthWebViewClient.kt`

**Interfaces:**
- Consumes: `ClientAuthWebViewClient.shouldOverrideUrlLoading(WebView, WebResourceRequest)`, `ClientAuthWebViewClient.shouldOverrideUrlLoading(WebView, String)`, existing `client(...)`, `RecordingCallbacks`, and `isAllowed()` behavior.
- Produces: direct regressions for off-origin subframe consumption/grant abandonment, allowed-origin subframe compatibility, main-frame callback preservation, and legacy callback UI isolation.

- [ ] **Step 1: Add a `WebResourceRequest` test helper with explicit `isForMainFrame` and a regression covering the four contract cases.**

Use synthetic URLs only. For the off-origin subframe assert `shouldOverrideUrlLoading(...) == true`, preference clear count `1`, and no callback event. For an allowed `RETURN` subframe on a fresh client assert `false`, clear count `0`, no callback. For an off-origin main-frame on a fresh client assert `true`, clear count `1`, and exactly `blocked:INVALID_URL`. For an off-origin deprecated callback on a fresh client assert `true`, clear count `1`, and no callback.

- [ ] **Step 2: Run only the new Debug regression with `--rerun-tasks` and require RED.**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests 'dev.junta.firmamobile.browser.ClientAuthWebViewClientTest.dedicatedClientTlsNavigationConfinesSubframesAndOwnsUiOnlyForMainFrame' --rerun-tasks
```
Expected: FAIL on unchanged production because the off-origin subframe returns `false`; the legacy callback assertion may also expose its existing top-level callback.

- [ ] **Step 3: Implement the minimum production fix.**

Change only `ClientAuthWebViewClient` navigation branching:
- allowed modern URLs return `false` regardless of frame;
- disallowed modern URLs call `blockNavigation(notifyApplication = request.isForMainFrame)` and return `true`;
- disallowed deprecated URLs call `blockNavigation(notifyApplication = false)` and return `true`;
- `blockNavigation` always abandons the handler and publishes `INVALID_URL` only when `notifyApplication` is true.

- [ ] **Step 4: Run focused GREEN in Debug and QA.**

Run the full `ClientAuthWebViewClientTest` class for both variants with `--rerun-tasks`. Require zero failures/errors/skips in the XML reports.

### Task 2: Full verification, evidence and atomic push

**Files:**
- Modify evidence: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify evidence: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Modify evidence when claim changes: `docs/security-roadmap.md`
- Modify evidence when test contract changes: `docs/test-plan.md`
- Modify evidence: `docs/test-report.md`
- Modify trust model: `docs/threat-model.md`

**Interfaces:**
- Consumes: Task 1 GREEN behavior and the repository's existing Android/Python/Go/artifact/release gates.
- Produces: auditable G26 evidence, one atomic commit, and exact remote SHA verification.

- [ ] **Step 1: Run dependency/toolchain checks and full Debug/QA JVM suites.**

Run the repository's current runtime lock/core/AAPT2 verification tasks plus `testDebugUnitTest` and `testQaUnitTest`; aggregate XML totals and require zero failures/errors/skips.

- [ ] **Step 2: Run lint and non-release assemblies.**

Run `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`; require zero lint errors and successful assemblies. Record APK SHA-256 values.

- [ ] **Step 3: Run Python, Go, Android artifact and release fail-closed gates.**

Run the existing Python suite, Go test/vet/build, Android artifact verification and no-private-signing release rejection. Remove any generated relay binary and prove release APK count is zero.

- [ ] **Step 4: Review exact diff and security boundary before evidence mutation.**

Require `git diff --check`; confirm production changes are limited to the Client TLS navigation callback; confirm no allowlist/profile/release/TLS/certificate/signing/dependency change, no unsafe `proceed`/trust-all addition, no credential-like addition, no release APK and no generated relay residue.

- [ ] **Step 5: Update evidence docs with exact RED/GREEN/full-gate job IDs and scoped claim.**

Document that off-origin dedicated Client TLS subframes are now consumed and abandon the grant, allowed-origin subframes remain permitted, application blocked-navigation UI is main-frame-owned, and no certificate disclosure/E2E expansion is claimed.

- [ ] **Step 6: Re-run focused ClientAuth tests, CI policy tests and `git diff --check` after evidence edits.**

Require fresh PASS before commit.

- [ ] **Step 7: Commit atomically, push, fetch, and verify exact remote SHA/divergence/clean worktree.**

Suggested commit message:
```text
fix(client-tls): confine dedicated subframe navigation
```
Never force-push. Confirm `HEAD == origin/agent/workspace-47-autonomous-20260803`, divergence `0/0`, canonical SHA unchanged, generated relay absent and release APK count zero.
