# WebView Geolocation Explicit-Disable Implementation Plan

> **For agentic workers:** Use the approved autonomous master plan with TDD and
> verification-before-completion for every step.

**Goal:** Make the intended no-geolocation WebView policy explicit and regression-safe
without changing any other browser capability.

**Architecture:** Keep existing manifest and `JuntaWebChromeClient` deny controls.
Add only `setGeolocationEnabled(false)` to `TrustedJuntaWebView` and enforce the
write-only setting through the existing source-contract regression pattern.

## Global constraints

- Work only in the autonomous worktree and branch.
- Before every mutation fetch/reverify branch, HEAD/remote, divergence, canonical and
  exact dirty scope.
- Do not add location permissions or any dependency.
- Do not change `JuntaWebChromeClient`, navigation, TLS, signing, profiles, portal
  contracts or unrelated WebView settings.
- No APK installation/launch or device-control workflow.
- One atomic commit only after fresh full verification and evidence updates.

### Task 1: Reproduce the missing hardening invariant

**Modify:**
- `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`

1. Add `trustedWebViewExplicitlyDisablesGeolocation()`.
2. Read `TrustedJuntaWebView.kt` through `projectSource(...)`.
3. Require exact `setGeolocationEnabled(false)` with a message explaining that the
   setting must be explicit.
4. Run only that Debug test with `--rerun-tasks` and require the expected assertion
   failure before production mutation.

### Task 2: Apply the minimum production fix

**Modify:**
- `app/src/main/java/dev/junta/firmamobile/browser/TrustedJuntaWebView.kt`

1. Add exactly `setGeolocationEnabled(false)` inside the existing `settings.apply`.
2. Do not change any other setting or browser class.
3. Run Debug and QA focused tests for `BrowserSecurityRegressionTest` and
   `TrustedJuntaWebViewTest`; require zero failures/errors/skips.

### Task 3: Full verification and evidence

1. Run runtime dependency locks, resolved-core, portable-AAPT2 and complete Debug/QA
   JVM suites with `--rerun-tasks`; aggregate JUnit XML.
2. Run `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, and
   `assembleQaAndroidTest`; parse lint and hash the three APKs.
3. Run full Python unittest discovery, Go test/vet/build, Android artifact verification
   and release-signing fail-closed. Remove generated relay and require zero release APKs.
4. Before evidence edits run exact-scope, `git diff --check`, sensitive-data and unsafe
   WebView/TLS scans.
5. Update only evidence whose state changed:
   - `docs/autonomous/2026-08-04-audit-ledger.md`
   - `docs/test-report.md`
   - `docs/security-roadmap.md`
   - `docs/threat-model.md`
   - `docs/test-plan.md`
   - `docs/handoffs/NEXT_CHAT_HANDOFF.md`
6. Re-run focused Debug/QA tests plus `python -m unittest tools.tests.test_ci_policy -v`.
7. Review and stage exact G21 files; repeat staged whitespace/sensitive/unsafe scans.
8. Fetch/recheck divergence, make one atomic commit, push without force, fetch and
   require exact remote SHA, canonical unchanged and a clean worktree.
