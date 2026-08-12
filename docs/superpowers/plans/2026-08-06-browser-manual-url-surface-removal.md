# Browser Manual-URL Surface Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Structurally remove the unused manual URL editor and dormant toolbar extension
hooks while preserving the current read-only, profile-bound browser behavior.

**Architecture:** Keep `BrowserAddressPresentation.hostOf` and current browser chrome.
Delete the unreferenced composable/input path and optional click/edit slots, then pin
absence of those capabilities with a source-policy regression in the existing browser
security test suite.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric/JUnit, Android resources, Gradle.

## Global Constraints

- Current production toolbar remains read-only; no generic URL entry point is added.
- Preserve profile/host display, exact navigation policy and all WebView/TLS/Client TLS/
  certificate/signing/cookie/release boundaries.
- Preserve `BrowserAddressPresentation.hostOf`, `BROWSER_ADDRESS_LABEL_TAG`, toolbar,
  bottom-bar and content test tags.
- Remove editor-only resources only after proving no remaining references.
- No dependency/toolchain change.

---

### Task 1: Pin structural absence with RED

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`

**Interfaces:**
- Consumes source files `BrowserAddressBar.kt`, `BrowserChromeComponents.kt`, and
  `BrowserScreen.kt` through existing `projectSource(...)` helper.
- Produces `manualUrlEditorSurfaceIsAbsentFromProductionBrowserChrome()`.

- [ ] Add a regression that asserts `BrowserAddressBar.kt` contains
  `BrowserAddressPresentation` but not `internal fun BrowserAddressBar(`,
  `BasicTextField`, `onEditingChange`, or `onSubmit`.
- [ ] Assert `BrowserChromeComponents.kt` contains neither `onIdentityClick` nor
  `editingContent`, and `BrowserScreen.kt` contains no `editingContent = null`.
- [ ] Run only the new Debug test. Expected RED: current main source still contains the
  dead composable/hooks. Do not change production sources before this failure is seen.

---

### Task 2: Remove minimum dead/manual-editor surface

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserAddressBar.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/BrowserScreenTest.kt`

- [ ] In `BrowserAddressBar.kt`, retain only the address-presentation object and current
  shared tags/constants; remove `BrowserAddressBar`, `BrowserToolbarHeight`,
  `BROWSER_ADDRESS_FIELD_TAG`, editor state/callback logic and now-unused Compose imports.
- [ ] In `IndustrialBrowserTopBar`, remove `onIdentityClick` and `editingContent`; always
  render the same passive `BrowserServiceIdentity` with `BROWSER_ADDRESS_LABEL_TAG`.
- [ ] Remove `editingContent = null` from the production `BrowserLayout` call.
- [ ] Remove editor-only strings `browser_address_current_description` and
  `browser_address_edit_description` after a repository reference check.
- [ ] Remove G17 tests that deliberately wired the now-deleted optional hook; retain
  ordinary chrome/status tests and passive current UI checks. Replace the three
  `BROWSER_ADDRESS_FIELD_TAG` negative assertions in `BrowserScreenTest` with the exact
  test-local literal `"browser_address_field"` so the production editor tag can be
  deleted without weakening the no-editor UI regression.
- [ ] Run the new source-policy test plus `BrowserChromeComponentsTest` and
  `BrowserScreenTest` in Debug and QA. Require GREEN with the existing
  `toolbarIdentityCannotOpenManualUrlEditor` test still passing.

---

### Task 3: Full verification, evidence, atomic push

**Files:**
- Update after observed gates: `docs/autonomous/2026-08-04-audit-ledger.md`,
  `docs/test-report.md`, `docs/security-roadmap.md`, `docs/handoffs/NEXT_CHAT_HANDOFF.md`.
- Include this spec/plan in the atomic milestone.

- [ ] Run runtime lock/core/AAPT2 gates and fresh full Debug+QA JVM suites.
- [ ] Run `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`;
  parse lint and APK hashes.
- [ ] Run full Python unittest discovery, Go test/vet/build, Android artifact verifier
  and release-signing fail-closed with explicit Termux bash where necessary.
- [ ] Confirm no generated relay/release APK remains; inspect complete diff, run
  `git diff --check`, exact-scope, sensitive-data and unsafe WebView/TLS scans.
- [ ] Update evidence to state this is structural attack-surface reduction with no
  current runtime behavior expansion and that G17's dormant hook is now removed.
- [ ] Re-run focused regression and `CiPolicyTest`, stage exact files, perform staged
  scans, fetch/recheck divergence, commit atomically, push without force, then fetch and
  verify exact remote SHA and unchanged canonical SHA.
