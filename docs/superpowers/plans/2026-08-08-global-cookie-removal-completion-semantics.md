# G32-01 Global cookie-removal completion semantics — Implementation plan

- [x] **Step 1 — RED:** add a `SiteDataCleanerTest` case where global WebStorage deletion succeeds
  and `removeAllCookies` completes with `false`; require success and zero `flush()` calls. Run only
  that test against unchanged production and observe the expected failure.
- [x] **Step 2 — minimum GREEN:** rename the callback value to its documented meaning
  `cookiesRemoved`. If true, preserve explicit flush and require it to succeed. If false, treat the
  cookie operation as a completed no-op. Overall success still requires WebStorage deletion.
- [x] **Step 3 — failure controls:** prove WebStorage deletion failure and flush failure when
  cookies were removed still report failure; keep synchronous remove-all exceptions fail closed.
- [x] **Step 4 — focused/adjacent:** run `SiteDataCleanerTest`, browser-data-clear completion and
  `BrowserSecurityRegressionTest` in Debug+QA.
- [x] **Step 5 — full gates:** fresh runtime locks/core/AAPT2 + complete Debug/QA JVM, lint,
  Debug/QA/QA-AndroidTest assemblies, Python, Go test/vet/build, Android artifact verification,
  release-signing fail-closed, relay cleanup and zero release APKs.
- [x] **Step 6 — evidence/review:** inspect complete diff, `git diff --check`, sensitive/unsafe
  additions, obtain independent focused review when useful, update only changed evidence, then
  rerun focused/policy checks.
- [ ] **Step 7 — atomic publish:** fetch, verify divergence/canonical ref, stage exact scope,
  inspect cached diff, make one atomic commit, push the autonomous branch and verify exact remote
  SHA equality.
