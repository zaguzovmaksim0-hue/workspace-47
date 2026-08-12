# Safe Browsing Frame UI Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Safe Browsing fail-closed for every frame while preventing iframe hits from creating top-level application error state.

**Architecture:** Change only the UI callback predicate in `JuntaWebViewClient.onSafeBrowsingHit`. `backToSafety(true)` remains unconditional and precedes any owner/frame check; diagnostics remain sanitized.

**Tech Stack:** Kotlin, Android WebView Safe Browsing APIs, Robolectric/JUnit, Gradle.

## Global Constraints

- Work only in the autonomous worktree/branch; fetch/reverify identity before every mutation.
- Preserve unconditional Safe Browsing rejection; never introduce `proceed()` or interstitial behavior.
- Do not modify SSL handling, navigation policy, origin/TLS/signing boundaries, dependencies or portal contracts.
- No APK installation/launch, ADB/device control, authenticated portal use or credentials/certificates.
- One atomic commit only after fresh full verification and evidence updates.

---

### Task 1: Reproduce subframe top-level Safe Browsing error delivery

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`

- [ ] Add an active subframe Safe Browsing request to `safeBrowsingHitsAlwaysReturnToSafety()` or a dedicated test. Assert `backToSafety=true`, `proceed=false`, `interstitial=false`, but no application callback for the subframe.
- [ ] Run only the new/changed Debug test with `--rerun-tasks`; require RED on `error:SAFE_BROWSING` from the subframe while platform rejection passes.

### Task 2: Apply minimum production fix

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`

- [ ] Change only the application callback condition to `isCurrentWebView(view) && request.isForMainFrame`.
- [ ] Keep `callback.backToSafety(true)` unconditional and first; keep diagnostic event unchanged.
- [ ] Run Debug+QA `JuntaWebViewClientTest`, `JuntaNavigationPolicyTest`, `WebMessageRouterTest`, `WebMessageProtocolTest`; aggregate XML and require zero failures/errors/skips.

### Task 3: Full verification, evidence, commit and push

**Files after verification:**
- Modify: `docs/autonomous/2026-08-04-audit-ledger.md`
- Modify: `docs/test-report.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/threat-model.md`
- Modify: `docs/test-plan.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

- [ ] Run dependency locks/resolved-core/AAPT2 and full Debug/QA JVM tests with `--rerun-tasks`.
- [ ] Run lintDebug/lintQa and assembleDebug/assembleQa/assembleQaAndroidTest; parse lint and hash APKs.
- [ ] Run Python discovery, Go test/vet/build, Android artifact verifier and release-signing fail-closed; remove generated relay and require zero release APKs.
- [ ] Run pre-evidence exact-scope, `git diff --check`, sensitive-data and unsafe WebView/TLS scans; prove Safe Browsing production diff changes only UI callback predicate and preserves unconditional `backToSafety(true)`.
- [ ] Update six authoritative evidence files with limited claim: subresource already rejected; only top-level error ownership changed.
- [ ] Re-run focused Debug/QA suites plus `python -m unittest tools.tests.test_ci_policy -v`.
- [ ] Stage exact G23 files; repeat cached diff/security/scope checks; fetch/recheck divergence; commit atomically; push without force; fetch and verify exact remote SHA, canonical unchanged and clean worktree.
