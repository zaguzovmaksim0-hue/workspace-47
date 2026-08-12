# Browser data-clear epoch isolation implementation plan

**Milestone:** G29-01
**Design:** `docs/superpowers/specs/2026-08-08-browser-data-clear-epoch-isolation-design.md`

## Hypothesis

A confirmed current-site or global browser-data clear does not immediately invalidate the
active WebMessage/MiniApplet navigation generation. Until the later reload starts, the old
main-frame document can still originate a new native signing request using the old context.

## Files

Behavior/test:

- `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`

Subordinate design/plan:

- `docs/superpowers/specs/2026-08-08-browser-data-clear-epoch-isolation-design.md`
- `docs/superpowers/plans/2026-08-08-browser-data-clear-epoch-isolation.md`

Evidence after full PASS only:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `docs/threat-model.md`

## TDD sequence

1. Re-fetch and verify branch, HEAD, remote SHA, 0/0 divergence, canonical SHA and worktree.
2. Add one regression to `BrowserSecurityRegressionTest` that extracts the
   `onClearCurrentSite` and `onDeleteAllBrowserData` handler blocks and requires
   `advanceNavigationEpoch()` before `siteDataCleaner.clearOrigin` /
   `siteDataCleaner.clearAllConfirmed` respectively.
3. Run only that regression on Debug and observe RED on unchanged production.
4. In `BrowserScreen.kt`, add exactly one `advanceNavigationEpoch()` call after
   `abandonClientAuth()` in each clear handler, before `onCancelSigning` and any clear work.
5. Run the focused regression Debug + QA and the adjacent bridge/epoch suites GREEN.
6. Run all Android/JVM/lint/build, Python, Go, artifact and release fail-closed gates from the
   design.
7. Review full diff and `git diff --check`; prove no protected network/TLS/certificate/signing/
   profile/dependency surface changed and no sensitive data was introduced.
8. Update evidence documents only with observed results.
9. Rerun focused Debug/QA regression, `CiPolicyTest`, and `git diff --check` after evidence.
10. Stage only the exact milestone files, staged-only review, atomic commit
    `fix(browser): invalidate flows before data clear`, push, fetch and verify exact remote SHA,
    0/0 divergence and clean worktree.
