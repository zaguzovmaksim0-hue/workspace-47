# Browser Identity Button-Role Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the existing clickable portal identity/address affordance explicit `Role.Button` semantics without changing layout or browser behavior.

**Architecture:** Keep the current `BrowserServiceIdentity`, toolbar dimensions and click callback. Add the role through the existing `Modifier.clickable` API and pin interactive/passive semantics with Compose/Robolectric tests.

**Tech Stack:** Kotlin, Jetpack Compose Foundation semantics, Compose UI test APIs, Robolectric, Gradle JVM/lint/build gates.

## Global Constraints

- Preserve toolbar dimensions, typography, strings, colors, host/trust text and address-editing behavior.
- Do not add `heightIn`, padding, or another layout change: focused RED measured the current tagged interactive node at 69 px.
- Preserve all WebView/network/TLS/Client TLS/certificate/signing/profile/release behavior.
- Only the `onIdentityClick != null` branch gains `Role.Button`.
- The passive identity remains non-clickable and role-free.
- No dependency or resource change.
- Automated evidence covers Compose semantics only; physical TalkBack/visual validation remains manual.

---

### Task 1: Pin the role contract with RED tests

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`

**Interfaces:**
- Consumes: `IndustrialBrowserTopBar(...)` and `BROWSER_ADDRESS_LABEL_TAG`.
- Produces: regression contract that the interactive identity has `Role.Button` and remains clickable, while the passive identity has neither click action nor role.

- [ ] **Step 1: Add the interactive role regression**

Render the top bar with non-null `onIdentityClick`, assert:

```kotlin
rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG)
    .assert(
        SemanticsMatcher.expectValue(
            SemanticsProperties.Role,
            Role.Button,
        ),
    )
    .performClick()
```

and verify the callback runs exactly once.

- [ ] **Step 2: Add the passive control**

Render with `onIdentityClick = null`; fetch the node semantics config and assert it contains neither `SemanticsActions.OnClick` nor `SemanticsProperties.Role`.

- [ ] **Step 3: Observe exact RED before production mutation**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests dev.junta.firmamobile.ui.BrowserChromeComponentsTest
```

Expected product RED: the interactive test fails on missing `(Role = 'Button')` while the node still exposes `OnClick`; passive control passes. The already-observed RED also reports node bounds `b=69.0 px`, so no touch-target/layout fix is part of this milestone.

---

### Task 2: Implement the minimum semantics fix

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`

**Interfaces:**
- Consumes: RED role test from Task 1.
- Produces: existing conditional clickable with explicit `Role.Button`.

- [ ] **Step 1: Import the role type**

Add `import androidx.compose.ui.semantics.Role`.

- [ ] **Step 2: Change only the interactive branch**

Replace:

```kotlin
Modifier.clickable(onClick = onIdentityClick)
```

with:

```kotlin
Modifier.clickable(
    role = Role.Button,
    onClick = onIdentityClick,
)
```

Do not change the null branch, layout modifiers, toolbar dimensions, `BrowserServiceIdentity`, strings, styling or callbacks.

- [ ] **Step 3: Run focused GREEN in Debug and QA**

Run:

```bash
./gradlew testDebugUnitTest testQaUnitTest \
  --tests dev.junta.firmamobile.ui.BrowserChromeComponentsTest
```

Expected: both variants pass, including the passive control and existing browser notice/chrome tests.

---

### Task 3: Full verification, evidence, commit, and push

**Files:**
- Modify: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify: `docs/test-report.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Include: `docs/superpowers/specs/2026-08-06-browser-identity-button-role-design.md`
- Include: `docs/superpowers/plans/2026-08-06-browser-identity-button-role.md`

**Interfaces:**
- Consumes: final production/test diff and observed job outputs.
- Produces: one atomic pushed accessibility milestone with exact evidence.

- [ ] **Step 1: Run dependency/toolchain and complete Android gates**

Run `verifyRuntimeDependencyLocks`, `verifyResolvedCoreVersion`, `verifyPortableAapt2Configuration`, fresh Debug/QA JVM unit suites, `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, and `assembleQaAndroidTest`. Parse XML/lint output for exact counts.

- [ ] **Step 2: Run non-Android and artifact gates**

Run complete Python `unittest` discovery, Go `test ./... -count=1`, `go vet ./...`, relay build, Android artifact verification, and release-without-private-signing-inputs fail-closed. Use explicit Termux `bash` for unchanged CI scripts if `/usr/bin/env` remains unavailable.

- [ ] **Step 3: Clean generated artifacts and inspect the exact diff**

Remove only generated `ws024-relay/ws024-relay` if present and confirm zero release APKs. Run `git diff --check`, inspect every changed file, and scan added/changed content for credentials, private certificate material, personal data, unsafe WebView/TLS weakening and unrelated edits.

- [ ] **Step 4: Update evidence documents from observed output only**

Record RED/GREEN job IDs, exact focused/full test counts, lint results, APK hashes, Python/Go results, environmental skips, artifact cleanup, and manual physical TalkBack/visual gate. Explicitly note that the initial size hypothesis was disproved by the 69 px RED node bounds and no layout change was made.

- [ ] **Step 5: Fresh pre-commit verification and atomic push**

Re-run the focused Debug/QA regression, `CiPolicyTest` if authoritative checked docs changed, `git diff --check`, stage exactly milestone files, run `git diff --cached --check` plus staged scope/sensitive/unsafe scans. Fetch and require divergence `0/0`, commit `fix: classify browser identity as button`, push without force, fetch again, and verify exact local/remote SHA equality plus unchanged canonical SHA.
