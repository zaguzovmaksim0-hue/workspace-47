# Afirma Main-Frame Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce the approved invariant that native AutoFirma URI routing is reachable
only from a modern main-frame WebView callback.

**Architecture:** Keep `JuntaNavigationPolicy` unchanged. Enforce frame ownership at
`JuntaWebViewClient`, where `WebResourceRequest.isForMainFrame` is authoritative and
where the deprecated callback is explicitly marked non-modern/non-main-frame.

**Tech Stack:** Kotlin, Android WebView/WebResourceRequest, Robolectric/JUnit, Gradle.

## Global Constraints

- Preserve modern main-frame `afirma:` and embedded-Afirma `intent:` acceptance.
- Subframe and legacy callbacks must not deliver an `AfirmaRequest` to native signing.
- Reuse `UNTRUSTED_AFIRMA_ORIGIN`; do not add a new externally visible reason.
- Do not add a user-gesture requirement in this milestone.
- Preserve HTTPS/external/Client TLS/WebMessage/certificate/signing/release behavior.
- No dependency/toolchain change.

---

### Task 1: Reproduce the frame-boundary defect

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`

- [ ] Add a test that sends a valid `afirma://sign?...` through `subframeRequest(...)`
  while the current top-level page is trusted; expect `true`, no `afirma:sign` callback,
  and exactly `blocked:UNTRUSTED_AFIRMA_ORIGIN`.
- [ ] In the same trust-boundary family, cover an embedded-Afirma `intent:` subframe and
  the deprecated String Afirma callback; neither may deliver native signing.
- [ ] Retain/add a modern-main-frame positive control proving direct Afirma and embedded
  Afirma still route normally.
- [ ] Run only the new Debug regression. Expected RED: current production delivers an
  `afirma:sign` callback from the subframe/legacy path.

---

### Task 2: Enforce modern main-frame delivery

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`

- [ ] In the `NavigationDecision.HandleAfirma` branch, if `isModernMainFrame` is false,
  record a sanitized blocked-navigation event with reason
  `UNTRUSTED_AFIRMA_ORIGIN`, call `onNavigationBlocked` with that reason, consume the
  navigation and do not call `recordAfirmaRequest` or `onAfirmaRequest`.
- [ ] Leave the existing successful main-frame branch byte-for-byte equivalent apart
  from the enclosing gate.
- [ ] Run focused Debug+QA `JuntaWebViewClientTest`, `JuntaNavigationPolicyTest`,
  `WebMessageRouterTest`, and `WebMessageProtocolTest`; require GREEN.

---

### Task 3: Full verification, evidence, atomic push

**Files:**
- Update after observed evidence: `docs/autonomous/2026-08-04-audit-ledger.md`,
  `docs/test-report.md`, `docs/security-roadmap.md`, `docs/threat-model.md`,
  `docs/test-plan.md`, `docs/handoffs/NEXT_CHAT_HANDOFF.md`.
- Include this spec/plan in the atomic milestone.

- [ ] Run runtime dependency lock/core/AAPT2 gates and fresh full Debug+QA JVM suites.
- [ ] Run lintDebug/lintQa plus Debug/QA/QA-AndroidTest assemblies and parse exact counts
  and APK hashes.
- [ ] Run full Python unittest discovery, Go test/vet/build, Android artifact verification
  and release-signing fail-closed.
- [ ] Confirm relay/release APK absence; inspect complete diff; run `git diff --check`,
  exact-scope, sensitive-data and unsafe WebView/TLS scans.
- [ ] Update evidence to state the precise claim: subframe/legacy Afirma URI routing
  cannot reach native request delivery; no claim of physical portal E2E.
- [ ] Re-run focused regressions and CiPolicy, stage exact files, perform staged scans,
  fetch/recheck divergence, commit atomically, push without force, then fetch and verify
  exact remote SHA and unchanged canonical SHA.
