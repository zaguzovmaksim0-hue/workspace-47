# Browser Identity Button-Role Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the dormant optional identity-click branch with explicit `Role.Button`
semantics while preserving the current production read-only toolbar contract.

**Architecture:** Keep `BrowserServiceIdentity`, toolbar layout and the existing optional
callback API. Add role metadata through the existing `Modifier.clickable` only for an
explicit non-null callback. Production `BrowserLayout` currently leaves the callback
null and keeps manual URL editing unreachable.

**Tech Stack:** Kotlin, Jetpack Compose Foundation semantics, Compose UI test APIs,
Robolectric, Gradle JVM/lint/build gates.

## Global Constraints

- Production `BrowserLayout` must remain read-only: it does not wire `onIdentityClick`
  and uses `editingContent = null`.
- Preserve toolbar dimensions, typography, strings, colors, host/trust text and current
  navigation behavior.
- Do not add `heightIn`, padding or another layout change: focused RED measured the
  tagged optional interactive node at 69 px.
- Only an explicitly non-null `onIdentityClick` branch gains `Role.Button`; the current
  production/default identity remains non-clickable and role-free.
- Preserve all WebView/network/TLS/Client TLS/certificate/signing/profile/release
  behavior and dependencies.
- Automated evidence covers Compose semantics only; no current production TalkBack or
  visual improvement is claimed from this dormant branch.

---

### Task 1: Pin the optional-branch role contract with RED tests

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`

**Interfaces:**
- Consumes: `IndustrialBrowserTopBar(...)` and `BROWSER_ADDRESS_LABEL_TAG`.
- Produces: a regression that an explicitly wired identity click has `Role.Button` and
  remains clickable, while the null/default path has neither click action nor role.

- [ ] Add an interactive test using non-null `onIdentityClick`; assert
  `SemanticsProperties.Role == Role.Button`, perform the click and verify one callback.
- [ ] Add a passive control with `onIdentityClick = null`; assert no
  `SemanticsActions.OnClick` and no role.
- [ ] Run `./gradlew testDebugUnitTest --tests dev.junta.firmamobile.ui.BrowserChromeComponentsTest`.
  Expected RED: only the missing button role fails; the node remains clickable. Treat
  the observed 69 px bounds as evidence against a separate size remediation.

---

### Task 2: Implement the minimum dormant-branch semantics fix

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`

**Interfaces:**
- Consumes: Task 1 RED.
- Produces: `Modifier.clickable(role = Role.Button, onClick = onIdentityClick)` only in
  the non-null branch.

- [ ] Import `androidx.compose.ui.semantics.Role`.
- [ ] Add `role = Role.Button` to the existing conditional clickable. Do not change the
  null branch, layout, toolbar dimensions, strings or callbacks.
- [ ] Run focused Debug+QA `BrowserChromeComponentsTest`; require both variants green.

---

### Task 3: Verify runtime scope, full gates, evidence, commit and push

**Files:**
- Verify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Verify: `app/src/test/java/dev/junta/firmamobile/ui/BrowserScreenTest.kt`
- Modify evidence: `docs/autonomous/2026-08-04-audit-ledger.md`,
  `docs/test-report.md`, `docs/security-roadmap.md`,
  `docs/handoffs/NEXT_CHAT_HANDOFF.md`.

- [ ] Verify production `BrowserLayout` does not pass `onIdentityClick`, uses
  `editingContent = null`, and the existing `toolbarIdentityCannotOpenManualUrlEditor`
  contract remains intact. Classify the milestone as dormant internal API hardening.
- [ ] Run runtime dependency locks, resolved-core, portable-AAPT2, fresh full Debug/QA
  JVM, lintDebug/lintQa and Debug/QA/QA-AndroidTest assemblies; parse exact counts.
- [ ] Run full Python unittest discovery, Go test/vet/build, Android artifact checks and
  release-signing fail-closed; confirm zero release APKs and no generated relay remains.
- [ ] Inspect exact scope, run `git diff --check`, sensitive-data and unsafe-WebView/TLS
  scans, then update evidence with observed jobs and the dormant-runtime limitation.
- [ ] Re-run focused Debug/QA and `CiPolicyTest`, stage only milestone files, run staged
  checks, fetch with zero remote-behind divergence, commit atomically and push without
  force; verify exact local/remote SHA and unchanged canonical SHA.
