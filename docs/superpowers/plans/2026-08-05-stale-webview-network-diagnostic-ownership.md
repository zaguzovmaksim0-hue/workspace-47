# Stale WebView Network-Diagnostic Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent a released or replaced WebView from appending stale network-request metadata to current diagnostics while preserving all active request and network behavior.

**Architecture:** Reuse the exact active-WebView predicate already supplied to `JuntaWebViewClient`. The request-interception callback remains observational and always returns `null`; only its diagnostic side effect becomes owner-bound.

**Tech Stack:** Kotlin, Android WebView/WebViewClient, SanitizedLogger, JUnit/Robolectric, Gradle Debug/QA gates.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- Preserve canonical branch `feature/ws024-secure-tunnel-20260728` at `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- No APK installation/launch, ADB/device control, portal interaction, credentials, certificates, signatures, upload, payment or submission.
- Do not change request interception, navigation, origin/path policy, DNS/TLS/Client TLS, cookies, bridge, signing, profile/catalog, release or dependency policy.
- Evidence documents change only after fresh final verification.

---

### Task 1: Bind request diagnostics to the active WebView

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`

**Interfaces:**
- Consumes: existing `isActiveWebView: (WebView) -> Boolean` and fail-closed `isCurrentWebView(view)` helper.
- Produces: unchanged `WebResourceResponse? == null`; `NETWORK_REQUEST` diagnostic only when the exact callback owner is active.

- [ ] **Step 1: Write the failing stale-owner diagnostic test**

Create a dedicated `SanitizedLogger` and a `JuntaWebViewClient` whose active predicate becomes false. Invoke `shouldInterceptRequest()` with a main-frame POST and require the logger export to remain empty. Keep the existing active-request diagnostic test unchanged as the positive control.

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew --no-daemon --rerun-tasks testDebugUnitTest \
  --tests 'dev.junta.firmamobile.browser.JuntaWebViewClientTest.staleWebViewDoesNotRecordNetworkRequestDiagnostics'
```

Expected: FAIL because unchanged production records `event=NETWORK_REQUEST` for the stale callback.

- [ ] **Step 3: Implement the minimum ownership guard**

At the start of `shouldInterceptRequest()`, return `null` when `!isCurrentWebView(view)`. Do not alter the existing main-frame metadata block or any SSL, Safe Browsing, navigation or error callback.

- [ ] **Step 4: Run focused GREEN**

Run the exact regression, then run complete `JuntaWebViewClientTest` in Debug and QA. Require zero failures/errors/skips.

### Task 2: Full verification, evidence and remote integration

**Files:**
- Update after verification: `docs/autonomous/2026-08-04-audit-ledger.md`
- Update after verification: `docs/security-roadmap.md`
- Update after verification: `docs/test-report.md`
- Update after verification: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

**Interfaces:**
- Consumes: final source/test/design/plan diff from Task 1.
- Produces: fresh verification evidence and one pushed atomic milestone commit.

- [ ] **Step 1: Run full gates**

Run resolved-core/AAPT2/runtime-lock checks, complete Debug and QA JVM suites,
Debug/QA/QA-AndroidTest assemblies, lint, Python discovery, Go test/vet/build,
Android APK artifact checks and release-without-private-signing fail-closed. Remove
the generated relay binary and require zero release APKs.

- [ ] **Step 2: Review exact scope**

Inspect the complete diff, run `git diff --check`, and scan changed content for
secrets, personal/certificate/signature material, unsafe WebView/TLS patterns and
unrelated changes.

- [ ] **Step 3: Record observed evidence**

Update only the four evidence documents with exact RED/GREEN/full-gate jobs, counts,
artifact hashes, limitations and prohibited-action confirmation.

- [ ] **Step 4: Commit and push**

Stage only milestone files, rerun staged whitespace/sensitive/security checks, create
one atomic commit, push to `origin/agent/workspace-47-autonomous-20260803`, fetch, and
verify exact remote SHA, divergence `0/0`, clean worktree and immutable canonical SHA.
