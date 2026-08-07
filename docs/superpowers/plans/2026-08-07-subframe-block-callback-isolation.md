# Subframe Block Callback Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent subframe and legacy blocked-navigation events from mutating top-level application UI while preserving fail-closed navigation and diagnostics.

**Architecture:** Keep `JuntaNavigationPolicy` unchanged. Gate only the application callback in `JuntaWebViewClient.handleNavigation()` using the existing `isModernMainFrame` signal; blocked requests remain consumed and logged.

**Tech Stack:** Kotlin, Android WebView/WebResourceRequest, Robolectric/JUnit, Gradle.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- Before every mutation fetch/reverify branch, HEAD/remote, divergence and canonical SHA.
- Do not modify `JuntaNavigationPolicy`, allowlists, G19/G20 behavior or other browser trust boundaries.
- Do not weaken navigation consumption, TLS checks, origin checks, signature checks or release gates.
- No APK installation/launch, ADB/device control, authenticated portal interaction or credential/certificate use.
- One atomic G22 commit only after fresh full verification and evidence updates.

---

### Task 1: Reproduce cross-frame application callback delivery

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`

**Interfaces:**
- Consumes: `JuntaWebViewClient.shouldOverrideUrlLoading(...)`, `subframeRequest(...)`, deprecated String callback, `RecordingBrowserCallbacks.events`.
- Produces: regression proving non-main-frame blocked navigation must not reach application callbacks.

- [ ] **Step 1: Write the failing test**

Add `subframeAndLegacyBlockedNavigationCannotReachApplicationCallback()` covering subframe insecure HTTP, cross-profile HTTPS, invalid custom scheme, and deprecated-callback blocked HTTPS/intent policy input. Assert every navigation is consumed and `events` is empty; assert sanitized logs include representative original reasons and `main_frame=false` without a secret query canary.

- [ ] **Step 2: Run RED**

Run only that Debug test with `--rerun-tasks`. Expected: failure because current `UpgradeToHttps`/`Block` paths invoke `onNavigationBlocked` for non-main-frame requests.

### Task 2: Apply the minimum callback-ownership fix

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`

**Interfaces:**
- Consumes: `isModernMainFrame` already passed to `handleNavigation`.
- Produces: `onNavigationBlocked(...)` only for modern main-frame blocked decisions.

- [ ] **Step 1: Implement minimum production change**

For `UpgradeToHttps`, retain the existing main-frame GET upgrade. In the blocked branch, always log but call `callbacks.onNavigationBlocked(INSECURE_HTTP)` only when `isModernMainFrame` is true.

For `NavigationDecision.Block`, retain event selection and logging, then call `callbacks.onNavigationBlocked(decision.reason)` only when `isModernMainFrame` is true.

- [ ] **Step 2: Reconcile the existing positive/negative HTTP-upgrade regression**

Keep the modern main-frame POST expectation as `blocked:INSECURE_HTTP`; change only the subframe expectation so it contributes no application callback while still being consumed and not loaded.

- [ ] **Step 3: Run focused GREEN**

Run Debug and QA `JuntaWebViewClientTest`, `JuntaNavigationPolicyTest`, `WebMessageRouterTest`, and `WebMessageProtocolTest`; aggregate JUnit XML and require zero failures/errors/skips.

### Task 3: Full verification and evidence

**Files:**
- Modify after evidence changes: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify after evidence changes: `docs/test-report.md`
- Modify after evidence changes: `docs/security-roadmap.md`
- Modify after evidence changes: `docs/threat-model.md`
- Modify after evidence changes: `docs/test-plan.md`
- Modify after evidence changes: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

- [ ] **Step 1: Run full behavior-change matrix**

Run runtime dependency locks, resolved core, portable AAPT2, all Debug/QA JVM tests with `--rerun-tasks`; then lint/build three non-release APKs; then Python discovery, Go test/vet/build, Android artifact verification and release-signing fail-closed. Remove generated relay and require zero release APKs.

- [ ] **Step 2: Run pre-evidence review**

Require exact code/test/spec/plan scope, `git diff --check`, sensitive-data scan, unsafe WebView/TLS scan, and unchanged `JuntaNavigationPolicy`.

- [ ] **Step 3: Update evidence**

Record RED, production scope, focused/full results and limited claim: navigation was already blocked; remediation isolates top-level UI callback ownership.

- [ ] **Step 4: Run post-evidence gate**

Re-run focused Debug/QA browser suites plus `python -m unittest tools.tests.test_ci_policy -v`.

- [ ] **Step 5: Stage, review, commit and push**

Stage exact G22 files only; repeat staged `diff --check`, sensitive/unsafe scans and exact production scope; fetch/recheck divergence; commit atomically; push without force; fetch and require exact remote SHA, canonical unchanged and clean worktree.
