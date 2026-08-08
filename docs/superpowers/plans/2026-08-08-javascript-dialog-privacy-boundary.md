# JavaScript Dialog Privacy Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate Android's insecure default WebView JavaScript dialog windows while preserving the hardened `JuntaWebChromeClient` progress and permission controls.

**Architecture:** Keep the existing `JuntaWebChromeClient` as the single chrome-policy boundary. Override the four JavaScript modal callbacks, resolve their `JsResult` immediately using Android's documented no-client semantics, and return `true` so WebView never creates the platform default dialog. No new UI, lifecycle owner, profile exception, or dependency is introduced.

**Tech Stack:** Kotlin, Android WebView API 36, JUnit 4, Robolectric 4.16.1, Gradle 9.4.1.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- Fetch and re-verify branch, remote SHA, divergence, canonical SHA, and valid unfinished work before every mutation.
- Production scope is exactly `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebChromeClient.kt`.
- Preserve progress clamping, popup rejection, generic permission denial, geolocation denial, navigation/TLS/Client-TLS/signing/profile/release/dependency behavior.
- Never display, log, persist, or forward JavaScript dialog `url`, `message`, or `defaultValue`.
- Do not install or launch an APK; do not use device automation, authenticated portals, credentials, private certificate material, real signing, upload, payment, or submission.
- Portal modal-dialog compatibility remains an external/manual acceptance gate; do not claim physical E2E.

---

### Task 1: Reproduce the insecure platform-default dialog path

**Files:**
- Create: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebChromeClientTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`

**Interfaces:**
- Consumes: existing `JuntaWebChromeClient : WebChromeClient` and Android `JsResult` / `JsPromptResult`.
- Produces: regressions that require explicit handling for all four JavaScript modal callbacks and fail-closed confirm/prompt settlement.

- [ ] **Step 1: Add the runtime RED tests**

Create `JuntaWebChromeClientTest.kt` with Robolectric SDK 36 configuration matching `TrustedJuntaWebViewTest`. Instantiate package-private `JsResult`/`JsPromptResult` through reflection only in test code. Assert that each callback returns `true`; for confirm and prompt additionally extract Robolectric's `ShadowJsResult` and assert `wasCancelled()`.

```kotlin
private fun newJsResult(): JsResult =
    JsResult::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

private fun newJsPromptResult(): JsPromptResult =
    JsPromptResult::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
```

Use four separate tests so unchanged production reports each missing explicit override independently:

```kotlin
assertTrue(client.onJsAlert(webView, "https://example.invalid/", "secret", result))
assertTrue(client.onJsBeforeUnload(webView, "https://example.invalid/", "secret", result))
assertTrue(client.onJsConfirm(webView, "https://example.invalid/", "secret", result))
assertTrue(client.onJsPrompt(webView, "https://example.invalid/", "secret", "default", result))
```

For confirm/prompt after the `assertTrue`, require:

```kotlin
assertTrue(Shadow.extract<ShadowJsResult>(result).wasCancelled())
```

- [ ] **Step 2: Add a source-contract RED regression**

Extend `BrowserSecurityRegressionTest` with `javascriptDialogsNeverUsePlatformDefaultWindows()`. Read `JuntaWebChromeClient.kt`, extract the blocks for `onJsAlert`, `onJsBeforeUnload`, `onJsConfirm`, and `onJsPrompt`, and assert:

```text
onJsAlert        -> result.confirm() + return true
onJsBeforeUnload -> result.confirm() + return true
onJsConfirm      -> result.cancel()  + return true
onJsPrompt       -> result.cancel()  + return true
```

Also assert that the production source contains none of `AlertDialog`, `Dialog(`, `super.onJsAlert`, `super.onJsBeforeUnload`, `super.onJsConfirm`, or `super.onJsPrompt`.

- [ ] **Step 3: Run RED and record exact failures**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests 'dev.junta.firmamobile.browser.JuntaWebChromeClientTest' \
  --tests 'dev.junta.firmamobile.browser.BrowserSecurityRegressionTest.javascriptDialogsNeverUsePlatformDefaultWindows'
```

Expected: FAIL on unchanged production because inherited WebChromeClient JavaScript callbacks return `false` and the explicit source blocks are absent. Confirm failures are assertions, not compilation/setup errors.

---

### Task 2: Implement the minimum explicit suppression policy

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebChromeClient.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebChromeClientTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`

**Interfaces:**
- Consumes: Android `WebChromeClient` callbacks.
- Produces: explicit no-dialog settlement with no new app/UI callback.

- [ ] **Step 1: Add only the required imports**

```kotlin
import android.webkit.JsPromptResult
import android.webkit.JsResult
```

- [ ] **Step 2: Add the four overrides without using untrusted callback text**

```kotlin
override fun onJsAlert(
    view: WebView,
    url: String,
    message: String,
    result: JsResult,
): Boolean {
    result.confirm()
    return true
}

override fun onJsBeforeUnload(
    view: WebView,
    url: String,
    message: String,
    result: JsResult,
): Boolean {
    result.confirm()
    return true
}

override fun onJsConfirm(
    view: WebView,
    url: String,
    message: String,
    result: JsResult,
): Boolean {
    result.cancel()
    return true
}

override fun onJsPrompt(
    view: WebView,
    url: String,
    message: String,
    defaultValue: String,
    result: JsPromptResult,
): Boolean {
    result.cancel()
    return true
}
```

Do not add logging, UI, profile checks, exceptions, `super` delegation, or any other production change.

- [ ] **Step 3: Run focused GREEN for Debug and QA**

Run:

```bash
./gradlew testDebugUnitTest testQaUnitTest \
  --tests 'dev.junta.firmamobile.browser.JuntaWebChromeClientTest' \
  --tests 'dev.junta.firmamobile.browser.BrowserSecurityRegressionTest.javascriptDialogsNeverUsePlatformDefaultWindows'
```

Expected: all focused tests pass with zero failures/errors/skips.

- [ ] **Step 4: Inspect the production diff before widening verification**

Require `git diff --check` success and verify the only production additions are two imports plus four callback overrides. Confirm no `AlertDialog`, `Dialog`, JavaScript dialog text logging, `super.onJs*`, TLS/navigation/policy/profile/release/dependency changes, or sensitive data were added.

---

### Task 3: Run full gates, update evidence, commit, push, and verify remote

**Files:**
- Modify: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/test-plan.md`
- Modify: `docs/test-report.md`
- Modify: `docs/threat-model.md`
- Retain: `docs/superpowers/specs/2026-08-08-javascript-dialog-privacy-boundary-design.md`
- Retain: `docs/superpowers/plans/2026-08-08-javascript-dialog-privacy-boundary.md`

**Interfaces:**
- Consumes: focused GREEN and existing CI/security scripts.
- Produces: pushed atomic G25-01 milestone with durable evidence and exact remote SHA.

- [ ] **Step 1: Run fresh dependency/toolchain and full JVM gates**

Use the same verified command family recorded for G24-01: strict runtime locks/core/AAPT2 checks followed by complete `testDebugUnitTest` and `testQaUnitTest`. Record task counts and exact Debug/QA test totals.

- [ ] **Step 2: Run lint and non-release assemblies**

Run `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, and `assembleQaAndroidTest`. Record lint error/warning counts and APK SHA-256 values.

- [ ] **Step 3: Run Python, Go, artifact, and release fail-closed gates**

Run full `tools/tests/test_*.py`, `go test ./...`, `go vet ./...`, relay build, Android artifact verification, and the release-without-private-signing-inputs rejection gate. Treat the intentionally absent release directory as a valid zero-artifact state rather than making a wrapper `find` failure substantive. Remove generated `ws024-relay/ws024-relay` and confirm release APK count is zero.

- [ ] **Step 4: Perform exact diff/security review**

Run `git diff --check`, inspect the complete diff, verify exact production/test/doc scope, scan changed additions for credential/private-key/certificate/personal-data patterns, and scan production for unsafe `handler.proceed`, trust-all/hostname bypass, `addJavascriptInterface`, JavaScript-dialog UI creation, or unrelated allowlist changes.

- [ ] **Step 5: Update evidence documents only with observed results**

Record G25-01 finding, official Android contract, RED job/result, minimum production mapping, focused/full gate results, artifact hashes, environmental skips, generated-artifact cleanup, and the manual compatibility boundary. Add a threat-model entry stating that remote JavaScript cannot create a non-secure modal surface through the privileged WebView.

- [ ] **Step 6: Re-run focused/policy verification after evidence edits**

Run Debug+QA focused G25 tests plus `tools.tests.test_ci_policy.CiPolicyTest`. Require zero failures before staging.

- [ ] **Step 7: Stage and verify exact scope**

Stage only the two test files, one production file, two G25 design/plan files, and six evidence documents. Run staged `git diff --check`, staged sensitive-data/unsafe-pattern scans, and verify no generated binary/release APK is staged or present.

- [ ] **Step 8: Create one atomic commit**

```bash
git commit -m "fix(webview): suppress insecure javascript dialogs"
```

- [ ] **Step 9: Push and verify exact remote SHA**

```bash
git push origin agent/workspace-47-autonomous-20260803
```

Then fetch, require `HEAD == origin/agent/workspace-47-autonomous-20260803`, divergence `0 0`, canonical SHA still `9c99bbfb36e13f88231d56001ccef8c4cbbce128`, clean worktree, relay absent, and release APK count zero. Only then record G25-01 as completed and continue a fresh independent audit line.
