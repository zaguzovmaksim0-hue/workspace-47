# External Navigation Main-Frame Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent iframe and deprecated WebView callbacks from invoking the native
external-browser handoff while preserving approved modern main-frame external HTTPS
behavior.

**Architecture:** Keep `JuntaNavigationPolicy` URL/profile decisions unchanged. Add a
typed frame-ownership block reason, then enforce authoritative
`WebResourceRequest.isForMainFrame` at `JuntaWebViewClient` immediately before the
`OpenExternal` native callback. Non-main/legacy paths are consumed and logged with
sanitized metadata only; they emit no application callback.

**Tech Stack:** Kotlin, Android WebView/WebResourceRequest, Robolectric/JUnit, Gradle,
Python unittest, Go.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on
  `agent/workspace-47-autonomous-20260803`.
- Before every mutation, fetch and require HEAD/remote
  `18fadd446ce02270424ef44bdd9a7360c66b230b`, divergence `0/0`, and canonical
  `9c99bbfb36e13f88231d56001ccef8c4cbbce128` until the G20 commit is created.
- Do not change `JuntaNavigationPolicy.decide*` behavior, URL validation, allowlists,
  profile/release policy, Client TLS, WebMessage, Afirma parsing/signing, certificates,
  cookies, dependencies, or toolchain.
- Do not add a user-gesture requirement.
- Never let subframe/legacy `OpenExternal` call `openExternal` or
  `onNavigationBlocked`; only sanitized diagnostics are allowed.
- Preserve modern main-frame direct HTTPS and validated `intent:` browser-fallback
  external handoff.
- No APK installation/launch, device control, authenticated portal interaction,
  credentials, private certificates, real signing, uploads, payments, or submissions.
- The autonomous task requires one atomic behavior-milestone commit after all evidence
  is updated and verified; do not make intermediate implementation commits.

---

### Task 1: Reproduce the external handoff frame bypass

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`

**Interfaces:**
- Consumes: `JuntaWebViewClient.shouldOverrideUrlLoading(WebView, WebResourceRequest)`,
  deprecated `shouldOverrideUrlLoading(WebView, String)`, existing `request(...)`,
  `subframeRequest(...)`, and `RecordingBrowserCallbacks.events`.
- Produces: RED regression coverage proving non-main/legacy `OpenExternal` delivery.

- [ ] **Step 1: Add the direct HTTPS frame-negative regression**

Add a test named
`subframeAndLegacyExternalHttpsCannotReachNativeHandoff` that uses
`https://example.org/help` with `currentPageUrl = { TRUSTED_PAGE }`. Assert both calls
return `true`, but expect `RecordingBrowserCallbacks.events` to remain empty. Also
assert the sanitized logger contains `reason=UNTRUSTED_EXTERNAL_NAVIGATION`,
`main_frame=false`, and does not contain `event=EXTERNAL_NAVIGATION` after the
production fix. Before that fix, the callback expectation must fail because current
production records `external:example.org` for both paths.

- [ ] **Step 2: Run only that Debug test and observe RED**

```bash
./gradlew --no-daemon testDebugUnitTest \
  --tests 'dev.junta.firmamobile.browser.JuntaWebViewClientTest.subframeAndLegacyExternalHttpsCannotReachNativeHandoff' \
  --rerun-tasks
```

Expected before production mutation: FAIL because callback events contain external
handoff delivery.

- [ ] **Step 3: Add browser-fallback coverage while production is still unchanged**

Use the validated fixture:

```text
intent://scan/#Intent;scheme=zxing;S.browser_fallback_url=https%3A%2F%2Fexample.org%2Fhelp;end
```

Add `subframeExternalIntentFallbackCannotReachNativeHandoff` expecting the subframe to
be consumed with no callbacks, and add/retain a modern main-frame positive assertion
that the same fixture produces exactly `external:example.org`.

- [ ] **Step 4: Run the new Debug frame-boundary tests and preserve the RED evidence**

Run both new external-frame tests together. Expected: non-main path fails against
unchanged production, while the modern main-frame positive control passes.

---

### Task 2: Enforce modern main-frame external delivery

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaNavigationPolicy.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`

**Interfaces:**
- Consumes: `NavigationDecision.OpenExternal`, `isModernMainFrame`,
  `SanitizedLogger.recordNavigationEvent(...)`, `BrowserNavigationCallbacks.openExternal`.
- Produces: `NavigationBlockReason.UNTRUSTED_EXTERNAL_NAVIGATION` and a fail-closed
  callback boundary for non-main/legacy external navigation.

- [ ] **Step 1: Add only the typed block reason**

Add `UNTRUSTED_EXTERNAL_NAVIGATION` to `NavigationBlockReason`. Do not change any
policy branch that constructs `OpenExternal`.

- [ ] **Step 2: Gate `OpenExternal` at the WebView callback boundary**

Implement this behavior in the existing branch:

```kotlin
is NavigationDecision.OpenExternal -> {
    if (!isModernMainFrame) {
        logger.recordNavigationEvent(
            code = DiagnosticEventCode.NAVIGATION_BLOCKED,
            rawUrl = targetUrl,
            reason = NavigationBlockReason.UNTRUSTED_EXTERNAL_NAVIGATION.name,
            isMainFrame = false,
            method = method,
        )
    } else {
        logger.recordBrowserEvent(
            DiagnosticEventCode.EXTERNAL_NAVIGATION,
            decision.uri.host,
        )
        callbacks.openExternal(decision.uri)
    }
    true
}
```

Do not call `callbacks.onNavigationBlocked(...)` in the non-main branch.

- [ ] **Step 3: Run focused GREEN in Debug and QA**

```bash
./gradlew --no-daemon \
  testDebugUnitTest testQaUnitTest \
  --tests dev.junta.firmamobile.browser.JuntaWebViewClientTest \
  --tests dev.junta.firmamobile.browser.JuntaNavigationPolicyTest \
  --tests dev.junta.firmamobile.browser.WebMessageRouterTest \
  --tests dev.junta.firmamobile.browser.WebMessageProtocolTest \
  --rerun-tasks
```

Require zero failures/errors/skips. Parse XML instead of relying only on Gradle stdout.

---

### Task 3: Full verification, evidence, atomic commit and push

**Files:**
- Modify: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify: `docs/test-report.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/threat-model.md`
- Modify: `docs/test-plan.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Include: `docs/superpowers/specs/2026-08-07-external-navigation-main-frame-design.md`
- Include: `docs/superpowers/plans/2026-08-07-external-navigation-main-frame.md`

**Interfaces:**
- Consumes: observed RED/GREEN and all fresh gate outputs.
- Produces: durable G20 evidence and one remotely verified atomic milestone commit.

- [ ] **Step 1: Run fresh Android dependency/toolchain and full JVM gates**

Run the repository's existing runtime-lock/core/AAPT2 checks together with complete
Debug and QA JVM unit suites using `--rerun-tasks`. Aggregate JUnit XML and require
zero failures/errors/skips.

- [ ] **Step 2: Run lint and all non-release Android assemblies**

Run `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, and
`assembleQaAndroidTest`; parse lint reports and record APK SHA-256 values.

- [ ] **Step 3: Run non-Android policy gates**

Run full Python unittest discovery, Go test/vet/build, Android artifact verification,
and the release-signing fail-closed check using the repository's current scripts and
commands. Do not weaken or bypass a gate. Record environmental skips separately.

- [ ] **Step 4: Review scope and security before evidence mutation**

Require the exact production/test/spec/plan scope expected at that stage; run
`git diff --check`, sensitive-data scan, and unsafe WebView/TLS scan. Confirm
`ws024-relay/ws024-relay` is absent and release APK count is zero.

- [ ] **Step 5: Update evidence documents with bounded claims**

Document that subframe/legacy `OpenExternal` cannot reach application callbacks or
Android external routing, while modern main-frame direct/fallback behavior remains.
State explicitly that this is automated callback-boundary evidence, not physical portal
or device E2E.

- [ ] **Step 6: Re-run focused Debug/QA plus CI-policy after evidence edits**

Re-run the four focused WebView/navigation/WebMessage suites in Debug and QA, then:

```bash
python -m unittest tools.tests.test_ci_policy -v
```

Require all results GREEN.

- [ ] **Step 7: Stage exact G20 files and verify the index**

Stage only the two production files, one test file, two subordinate docs, and six
evidence docs. Require no unstaged/untracked extras; run `git diff --cached --check`,
staged sensitive-data scan, and staged unsafe WebView/TLS scan.

- [ ] **Step 8: Re-fetch, commit once, push without force, and verify remote SHA**

Require no remote divergence immediately before commit. Create one atomic commit such
as:

```bash
git commit -m "fix(webview): bind external handoff to main frame"
git push origin HEAD:refs/heads/agent/workspace-47-autonomous-20260803
```

Fetch again and require `HEAD == origin/agent/workspace-47-autonomous-20260803`,
divergence `0/0`, canonical still
`9c99bbfb36e13f88231d56001ccef8c4cbbce128`, and a clean worktree.
