# Browser Compatibility-Error Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent a queued WebMessageBridge attachment failure from mutating browser UI state after its initiating WebView has been replaced.

**Architecture:** Keep the existing WebView-posted delivery and add the same exact-instance ownership check already used by page-progress delivery. Do not introduce a new lifecycle object or change bridge attachment semantics.

**Tech Stack:** Kotlin, Android WebView, Jetpack Compose state, JUnit source-policy regression, Gradle Debug/QA gates.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- Preserve canonical branch `feature/ws024-secure-tunnel-20260728` at `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- No APK installation/launch, ADB/device control, portal interaction, credentials, certificates, signatures, upload, payment or submission.
- Do not change bridge attachment, origin, TLS, signing, profile, release or dependency policy.
- Evidence documents change only after fresh final verification.

---

### Task 1: Pin stale compatibility-error delivery

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Evidence after verification: `docs/autonomous/2026-08-04-audit-ledger.md`, `docs/security-roadmap.md`, `docs/test-report.md`, `docs/handoffs/NEXT_CHAT_HANDOFF.md`

**Interfaces:**
- Consumes: `webViewRef: AtomicReference<WebView?>`, the initiating `webView`, and existing `webView.post { ... }` delivery.
- Produces: compatibility-state mutation guarded by `webViewRef.get() === webView`.

- [ ] **Step 1: Write the failing source-policy regression**

Add a test that reads `BrowserScreen.kt`, isolates the bridge-attachment failure block,
and requires the posted runnable to contain both exact identity comparison and the
compatibility-state assignment.

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew --no-daemon --rerun-tasks testDebugUnitTest \
  --tests dev.junta.firmamobile.browser.BrowserSecurityRegressionTest
```

Expected: FAIL because the current runnable is
`webView.post { compatibilityError = true }` without an active-instance check.

- [ ] **Step 3: Implement the minimum repair**

Replace only the deferred assignment with:

```kotlin
webView.post {
    if (webViewRef.get() === webView) compatibilityError = true
}
```

- [ ] **Step 4: Run focused GREEN**

Run the same Debug regression command and require zero failures.

- [ ] **Step 5: Run relevant Debug and QA suites**

Run:

```bash
./gradlew --no-daemon --rerun-tasks \
  testDebugUnitTest testQaUnitTest \
  --tests dev.junta.firmamobile.browser.BrowserSecurityRegressionTest \
  --tests dev.junta.firmamobile.ui.BrowserScreenTest
```

Require `BUILD SUCCESSFUL` and all selected tests passing in both variants.

- [ ] **Step 6: Run full gates**

Run full pin/unit/assembly, lint, Python, Android artifact, release fail-closed and Go
test/vet/build gates. Remove the generated relay binary and verify no release APK.

- [ ] **Step 7: Review and record evidence**

Inspect the complete diff, run whitespace and added-line secret/PII/unsafe-WebView/TLS
scans, then update only the four evidence documents with observed RED/GREEN/full-gate
results and artifact hashes.

- [ ] **Step 8: Commit and push**

Stage only the exact milestone files, run staged checks, create one atomic commit,
push to `origin/agent/workspace-47-autonomous-20260803`, fetch, and verify exact remote
SHA plus divergence `0/0`.
