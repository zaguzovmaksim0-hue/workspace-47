# Afirma Frame UI Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prevent subframe and legacy Afirma rejection events from mutating top-level browser UI
while preserving G19 main-frame native delivery and fail-closed diagnostics.

**Architecture:** Keep `JuntaNavigationPolicy` and Afirma parsing unchanged. Change only the
application callback ownership inside the existing `NavigationDecision.HandleAfirma` branch.

**Tech Stack:** Kotlin, Android WebView/WebResourceRequest, Robolectric/JUnit 4, Gradle.

## Global constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on the autonomous
  branch.
- Preserve G19 native-delivery, G20 external-handoff, Client TLS, WebMessage, certificate/signing,
  profile/release and dependency contracts.
- No APK installation/launch, ADB/device control, authenticated portal use, credential/private-
  certificate use, real signing, upload, payment or submission.

### Task 1: RED — Afirma rejection UI ownership

**Files:**
- Test first: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`
- Production later: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`

- [ ] Change `subframeAfirmaAndEmbeddedIntentCannotReachNativeCallbacks` so both consumed
  subframe requests must leave `RecordingBrowserCallbacks.events` empty, while the logger retains
  sanitized `UNTRUSTED_AFIRMA_ORIGIN` / `main_frame=false` evidence.
- [ ] Change `legacyAfirmaCallbackCannotReachNativeCallbacks` so the consumed deprecated callback
  must leave application events empty.
- [ ] Run those two Debug tests with `--rerun-tasks`; require RED on unchanged production.

### Task 2: Minimum production fix and focused GREEN

- [ ] In `NavigationDecision.HandleAfirma`, keep the non-main-frame sanitized log and `true`
  return but delete only the `onNavigationBlocked` application callback.
- [ ] Run full `JuntaWebViewClientTest` Debug+QA with `--rerun-tasks`; require zero
  failures/errors/skips and retain the existing main-frame Afirma positive control.

### Task 3: Full gates, evidence, commit and push

- [ ] Run fresh runtime-lock/core/AAPT2 checks plus all Debug/QA JVM tests and aggregate XML.
- [ ] Run lint Debug/QA and assemble Debug/QA/QA AndroidTest; record lint counts and APK hashes.
- [ ] Run Python, Go test/vet/build, Android artifact verifier and release-signing fail-closed;
  remove generated relay and prove zero release APKs.
- [ ] Review exact code/test/spec/plan diff, `git diff --check`, sensitive/unsafe additions,
  unchanged `JuntaNavigationPolicy`, unchanged Afirma parser/signing/profile/release/dependencies.
- [ ] Update ledger/handoff/roadmap/test plan/test report/threat model with the limited UI-ownership
  claim and exact verification evidence.
- [ ] Re-run focused Debug/QA WebView client tests, `CiPolicyTest` and `git diff --check`.
- [ ] Stage exact files, review staged-only diff, commit atomically as
  `fix(webview): isolate Afirma frame UI`, push, fetch, and verify exact remote SHA, divergence
  `0/0`, clean worktree, immutable canonical SHA, relay absent and release APK count zero.
