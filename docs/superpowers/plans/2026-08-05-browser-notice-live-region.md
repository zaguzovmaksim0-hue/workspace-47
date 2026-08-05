# Browser Notice Live Region Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure a newly appearing browser error notice is announced immediately through Compose accessibility semantics without moving focus.

**Architecture:** Preserve `BrowserNoticeBanner` as the single visual and semantic container. Pin its accessibility contract with the existing Robolectric Compose test, then add one assertive live-region property to the current `Surface` modifier.

**Tech Stack:** Kotlin, Jetpack Compose UI semantics, Material 3, Robolectric Compose UI tests, Gradle.

## Global Constraints

- Keep all user-visible strings, layout, colors, retry behavior and 48 dp icon-button touch target unchanged.
- Do not request or transfer focus.
- Do not alter WebView, network, TLS, Client TLS, certificate, signing or portal-profile behavior.
- Do not change dependencies, toolchain pins, release policy or workflows.
- Automated semantics evidence does not prove physical TalkBack announcement timing.
- Canonical branch remains immutable at `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.

---

### Task 1: Pin the missing live-region contract

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`

**Interfaces:**
- Consumes: `BrowserNoticeBanner`, `BROWSER_NOTICE_TAG`, `SemanticsProperties.LiveRegion`.
- Produces: test `noticeBannerIsAnAssertiveLiveRegion`.

- [ ] **Step 1: Add the failing Compose semantics test**

Add imports for `LiveRegionMode`, `SemanticsProperties`, `SemanticsMatcher` and `assert` support,
then add:

```kotlin
@Test
fun noticeBannerIsAnAssertiveLiveRegion() {
    rule.setContent {
        JuntaFirmaTheme {
            BrowserNoticeBanner(
                message = "No se pudo cargar el portal.",
                onRetry = {},
            )
        }
    }

    rule.onNodeWithTag(BROWSER_NOTICE_TAG)
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Assertive,
            ),
        )
}
```

- [ ] **Step 2: Run exact RED**

Run:

```bash
./gradlew --no-daemon --rerun-tasks :app:testDebugUnitTest \
  --tests dev.junta.firmamobile.ui.BrowserChromeComponentsTest.noticeBannerIsAnAssertiveLiveRegion
```

Expected: FAIL because the tagged banner semantics do not contain `LiveRegion`.

### Task 2: Add the minimum assertive semantics

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`

**Interfaces:**
- Consumes: `Modifier.semantics`, `liveRegion`, `LiveRegionMode.Assertive`.
- Produces: an assertive live-region property on `BrowserNoticeBanner` only.

- [ ] **Step 1: Add the minimum implementation**

Import:

```kotlin
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
```

Extend the existing `BrowserNoticeBanner` surface modifier without changing modifier order outside
this addition:

```kotlin
.testTag(BROWSER_NOTICE_TAG)
.semantics { liveRegion = LiveRegionMode.Assertive }
```

- [ ] **Step 2: Run exact GREEN**

Run the exact command from Task 1 Step 2.

Expected: PASS.

- [ ] **Step 3: Run complete focused Debug and QA tests**

Run:

```bash
./gradlew --no-daemon --rerun-tasks \
  :app:testDebugUnitTest :app:testQaUnitTest \
  --tests dev.junta.firmamobile.ui.BrowserChromeComponentsTest
```

Expected: PASS in both variants.

### Task 3: Full verification, evidence and remote completion

**Files:**
- Modify: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/test-report.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

**Interfaces:**
- Consumes: observed RED/GREEN and full-gate evidence.
- Produces: durable G10-01 evidence and one remotely verified atomic commit.

- [ ] **Step 1: Run relevant full repository gates**

Run pin/AAPT2 checks, full Debug and QA JVM tests, Debug/QA/QA-AndroidTest assemblies, forced
`lintDebug`/`lintQa`, complete Python discovery, Android artifact verification, release
fail-closed with zero release APKs, and Go test/vet/build. Remove the generated relay binary.

- [ ] **Step 2: Review exact diff and security invariants**

Require `git diff --check`, the exact expected source/test/spec/plan/evidence scope, no added
secrets or personal data, no unrelated WebView/network/TLS/certificate/signing changes, and no
generated release APK or relay binary.

- [ ] **Step 3: Update evidence only from observed results**

Record exact jobs, test counts, APK hashes and the limitation that Robolectric semantics do not
prove physical TalkBack timing. Do not modify `docs/threat-model.md` because no application trust
boundary changes.

- [ ] **Step 4: Commit and push atomically**

Fetch and recheck branch/upstream/canonical state, stage only the exact milestone surface, run
cached whitespace and focused semantics verification, commit once, push without force to
`origin/agent/workspace-47-autonomous-20260803`, fetch again and require exact local/remote/upstream
SHA equality with divergence `0/0` and canonical SHA unchanged.
