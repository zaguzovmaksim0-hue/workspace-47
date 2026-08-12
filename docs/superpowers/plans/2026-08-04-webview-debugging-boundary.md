# WebView Debugging Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent Chrome DevTools/WebView remote debugging in QA and release while retaining it in the ordinary developer debug variant.

**Architecture:** Replace the broad `BuildConfig.DEBUG` decision with one explicit build-variant security flag. Keep QA debuggability and all portal/runtime policy otherwise unchanged.

**Tech Stack:** Android Gradle Plugin BuildConfig, Kotlin WebView, Python unittest policy checks.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803`.
- Do not install or launch an APK or use device-control tooling.
- Do not weaken QA/release portal, TLS, origin, signing, or certificate checks.
- No dependency changes.

---

### Task 1: Pin the WebView debugging build boundary

**Files:**
- Modify: `tools/tests/test_ci_policy.py`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/TrustedJuntaWebView.kt`
- Update after GREEN: `docs/autonomous/2026-08-04-audit-ledger.md`
- Update after GREEN: `docs/security-roadmap.md`
- Update after GREEN: `docs/test-report.md`

**Interfaces:**
- Consumes: Android build type name and generated `BuildConfig` fields.
- Produces: `BuildConfig.ENABLE_WEBVIEW_CONTENTS_DEBUGGING: boolean`.

- [ ] **Step 1: Write the failing policy test**

Add a `CiPolicyTest` method that requires the explicit field, requires `true` in
the `debug` block, requires `false` in `qa` and `release`, requires
`TrustedJuntaWebView` to call
`setWebContentsDebuggingEnabled(BuildConfig.ENABLE_WEBVIEW_CONTENTS_DEBUGGING)`,
and forbids `setWebContentsDebuggingEnabled(BuildConfig.DEBUG)`.

- [ ] **Step 2: Run RED**

Run:
`python -m unittest tools.tests.test_ci_policy.CiPolicyTest.test_webview_debugging_is_debug_only -v`

Expected: FAIL because the explicit field/call is absent.

- [ ] **Step 3: Implement the minimal build policy**

Add default `ENABLE_WEBVIEW_CONTENTS_DEBUGGING=false`; override only `debug` to
`true`; explicitly retain `false` for `qa` and `release`. Replace the WebView call
to use this field. Make no other WebView/build-type change.

- [ ] **Step 4: Run focused GREEN**

Run the exact Python test from Step 2, then
`./gradlew --console=plain testDebugUnitTest testQaUnitTest`.

- [ ] **Step 5: Run relevant full gates**

Run lint/builds, full Python, Go test/vet/build, Android artifact checks, and
release fail-closed verification. Record environmental skips exactly.

- [ ] **Step 6: Review and document evidence**

Run `git diff --check`, inspect the complete diff, scan changed files for secrets,
private material, `BuildConfig.DEBUG` WebView use, disabled verification, and
unrelated changes; then update the ledger/roadmap/test report.

- [ ] **Step 7: Commit, push, verify remote SHA**

Create one atomic remediation commit, push to
`origin/agent/workspace-47-autonomous-20260803`, fetch, and verify remote HEAD is
the exact local commit.
