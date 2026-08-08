# G31-01 — global browser resource-cache erasure plan

## Goal

Close the reproducible privacy/completeness gap in the user-confirmed global
browser-data erasure without widening current-site cleanup or changing existing
async completion ownership.

## Files

Behavior/test scope:

- `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`
- `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`

Evidence after GREEN/full gates only:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `docs/threat-model.md` only if its stated global-clear control needs precision

## TDD sequence

- [x] **Step 1 — RED:** add a source regression proving the global clear handler
  must call `clearCache(true)` before `clearAllConfirmed`, while the current-site
  handler must not call it. Run only that test and observe the expected failure.
- [x] **Step 2 — GREEN:** add the single global `clearCache(true)` call in the
  initiating WebView block, preserving stop/history/form ordering and all existing
  navigation/completion ownership. Run the focused regression GREEN.
- [x] **Step 2b — reviewer follow-up RED/GREEN:** prove that global deletion cannot
  start with a null WebView owner. Type the completion lease to non-null `WebView`;
  when no active owner exists, invalidate any older lease, publish failure, and
  return before cache or cookie/WebStorage deletion. Do not disable the menu or
  create a throwaway WebView.
- [x] **Step 3 — focused/adjacent:** run `BrowserSecurityRegressionTest`,
  `SiteDataCleanerTest`, `BrowserDataClearCompletionLeaseTest` and relevant
  `BrowserScreenTest` in Debug + QA.
- [x] **Step 4 — full gates:** run fresh runtime locks/resolved-core/AAPT2 + full
  Debug/QA JVM; lint/build; Python; Go test/vet/build; Android artifacts; release
  fail-closed; remove generated relay; `git diff --check` and sensitive/unsafe
  scans.
- [x] **Step 5 — evidence/recheck:** update only evidence whose facts changed and
  rerun focused Debug+QA plus `CiPolicyTest`.
- [ ] **Step 6 — atomic publish:** fetch, verify divergence/canonical ref, stage
  exact files, review cached diff, commit once, push autonomous branch, fetch and
  verify exact remote SHA + clean worktree.

## Acceptance

- Global confirmed browser-data deletion requires a non-null initiating WebView,
  clears its resource cache including disk files before process-wide data deletion
  begins, and fails closed without partial deletion if that owner is unavailable.
- Current-site clear remains origin-scoped and does not clear the global resource
  cache.
- G29 navigation-epoch and G6 completion-lease controls are unchanged.
- No device/portal/credential/private-signing action occurs.
