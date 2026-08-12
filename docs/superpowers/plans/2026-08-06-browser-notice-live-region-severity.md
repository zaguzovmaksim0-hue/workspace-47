# Browser Notice Live-Region Severity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep urgent browser failures assertive while announcing non-error browser progress and successful data-clear status updates politely.

**Architecture:** Extend the existing notice banner with an explicit Compose `LiveRegionMode` whose default remains assertive. Compute urgency in one pure `BrowserScreen` policy using the same state precedence as the displayed notice, so accessibility behavior is independent of Retry-button presence and is directly unit-testable.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 semantics, Robolectric Compose tests, Gradle JVM/lint/build gates.

## Global Constraints

- Preserve current strings and visual styling.
- Preserve all WebView/network/TLS/Client TLS/certificate/signing/data-clear/profile/release behavior.
- `BrowserNoticeBanner` defaults to `LiveRegionMode.Assertive`.
- Only `CLEARING`, exact site-clear success, and global-clear success are polite.
- Limited/failure/error/blocked/compatibility notices remain assertive.
- Physical TalkBack behavior remains a manual acceptance gate.

---

### Task 1: Pin live-region severity policy with RED tests

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/BrowserScreenTest.kt`

**Interfaces:**
- Consumes: existing `BrowserNoticeBanner`, `BrowserErrorCode`, `NavigationBlockReason`, `ClientCertPreferenceBarrierState`, and `SiteClearResult`.
- Produces: desired `BrowserNoticeBanner(..., liveRegionMode: LiveRegionMode)` API and `browserNoticeLiveRegionMode(...) : LiveRegionMode` policy contract.

- [ ] **Step 1: Add the failing component semantics test**

Add a test that renders:

```kotlin
BrowserNoticeBanner(
    message = "Se borraron los datos del sitio actual.",
    onRetry = null,
    liveRegionMode = LiveRegionMode.Polite,
)
```

and asserts `SemanticsProperties.LiveRegion == LiveRegionMode.Polite`. Preserve the
existing assertive error test as a positive control.

- [ ] **Step 2: Add the failing policy test**

In `BrowserScreenTest`, assert at minimum:

```kotlin
assertEquals(
    LiveRegionMode.Polite,
    browserNoticeLiveRegionMode(
        compatibilityError = false,
        blockedReason = null,
        browserError = null,
        clientCertPreferenceState = ClientCertPreferenceBarrierState.CLEARING,
        siteClearResult = null,
        globalClearResult = null,
    ),
)
assertEquals(
    LiveRegionMode.Polite,
    browserNoticeLiveRegionMode(
        compatibilityError = false,
        blockedReason = null,
        browserError = null,
        clientCertPreferenceState = ClientCertPreferenceBarrierState.IDLE,
        siteClearResult = SiteClearResult.CLEARED_EXACTLY,
        globalClearResult = null,
    ),
)
assertEquals(
    LiveRegionMode.Assertive,
    browserNoticeLiveRegionMode(
        compatibilityError = true,
        blockedReason = null,
        browserError = null,
        clientCertPreferenceState = ClientCertPreferenceBarrierState.IDLE,
        siteClearResult = SiteClearResult.CLEARED_EXACTLY,
        globalClearResult = null,
    ),
)
```

Also cover global success as polite and Client TLS preference failure, navigation
block, global-clear failure, and limited site clear as assertive.

- [ ] **Step 3: Run focused Debug tests and observe RED**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests dev.junta.firmamobile.ui.BrowserChromeComponentsTest \
  --tests dev.junta.firmamobile.ui.BrowserScreenTest
```

Expected: compilation/test failure because the explicit `liveRegionMode` argument
and `browserNoticeLiveRegionMode` policy do not exist yet. Production sources must
remain unchanged at this point.

---

### Task 2: Implement the minimum explicit severity policy

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`

**Interfaces:**
- Consumes: tests from Task 1.
- Produces: `BrowserNoticeBanner(..., liveRegionMode: LiveRegionMode = LiveRegionMode.Assertive)` and `browserNoticeLiveRegionMode(...) : LiveRegionMode`.

- [ ] **Step 1: Parameterize banner semantics without changing visuals**

Change the component signature to:

```kotlin
internal fun BrowserNoticeBanner(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    liveRegionMode: LiveRegionMode = LiveRegionMode.Assertive,
)
```

and set `liveRegion = liveRegionMode`. Do not change color, layout, strings, or Retry
behavior.

- [ ] **Step 2: Add the pure state policy**

Implement `browserNoticeLiveRegionMode(...)` in `BrowserScreen.kt` with the exact
precedence from the design: `CLEARING`/successful exact clears are polite; every
failure, warning, navigation block, compatibility/browser error is assertive.

- [ ] **Step 3: Pass explicit severity at the existing call site**

At the single normal browser `BrowserNoticeBanner` call, pass the helper result built
from the same state values used by `browserNotice(...)`.

- [ ] **Step 4: Run focused GREEN in Debug and QA**

Run:

```bash
./gradlew testDebugUnitTest testQaUnitTest \
  --tests dev.junta.firmamobile.ui.BrowserChromeComponentsTest \
  --tests dev.junta.firmamobile.ui.BrowserScreenTest
```

Expected: both variants pass with no regression in the existing assertive-error
positive control.

---

### Task 3: Full verification, evidence, commit, and push

**Files:**
- Modify: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify: `docs/test-report.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

**Interfaces:**
- Consumes: final production/test diff from Tasks 1–2.
- Produces: durable G13-02 evidence and one pushed atomic commit.

- [ ] **Step 1: Run complete relevant Android/Python/Go/artifact/release gates**

Use the repository's existing pinned/lock verification tasks plus full Debug/QA JVM,
lint, Debug/QA/QA-AndroidTest assemblies, Python unittest discovery, Go test/vet/build,
Android artifact validation, and the release-without-private-signing-inputs
fail-closed check. Do not install or launch an APK.

- [ ] **Step 2: Inspect and sanitize the complete diff**

Run `git diff --check`, inspect every changed file, confirm no credential/certificate/
personal material, no unsafe WebView/TLS weakening, no unrelated files, no release
APK, and remove any generated `ws024-relay/ws024-relay` executable after verification.

- [ ] **Step 3: Update evidence documents**

Record RED/GREEN/full-gate job IDs, exact test counts, lint warnings/errors, APK hashes,
artifact cleanup, manual TalkBack/visual gate, and unchanged threat boundary. Do not
claim physical announcement timing.

- [ ] **Step 4: Re-run fresh final verification on the exact staged diff**

Re-run the focused regressions, relevant policy/full checks needed to substantiate the
commit, `git diff --cached --check`, scope and sensitive-content scans.

- [ ] **Step 5: Commit and push atomically**

```bash
git commit -m "fix: classify browser notice live regions"
git fetch --prune origin
git push origin HEAD:agent/workspace-47-autonomous-20260803
```

Before push, require no remote divergence. After push, fetch and verify the exact
remote SHA equals local HEAD and the canonical branch remains at the immutable SHA.
