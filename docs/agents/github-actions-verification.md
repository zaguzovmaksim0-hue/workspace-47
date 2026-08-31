# GitHub Actions verification policy

## Canonical rule

GitHub Actions is the canonical broad verification environment for current `workspace-47` development.
Agents must not use Codex Cloud as the default Android/Gradle execution path and must not run the full Android gate on the phone by default.

The repository workflows execute on the exact pushed commit and are the required integration evidence before merge.

## Development feedback

During implementation, an agent may run a **narrow local check** when it materially shortens the RED/GREEN loop, for example one test class, one test method, `git diff --check`, or a focused Python test. Keep local Gradle work bounded; do not routinely run the full unit/lint/assembly matrix on the phone.

Examples of acceptable focused local feedback:

```bash
./gradlew testQaUnitTest --tests '*SiteDataCleanerTest*' --no-daemon --console=plain
python -m unittest tools.tests.test_generate_public_portal_catalog.PublicPortalCatalogGeneratorTest.test_junta_andalucia_vea_peg_profile_binds_exact_public_start -v
git diff --check
```

Local focused results are supporting evidence only. They do not replace the required GitHub Actions gate.

## Candidate sequence

1. Start a bounded change from current `origin/main` in an isolated branch/worktree.
2. Implement and use only focused local checks when useful.
3. Commit and push the candidate branch.
4. Open or update a pull request against `main`.
5. Treat the pull-request head SHA as the candidate SHA.
6. Wait for the repository GitHub Actions checks on that exact SHA.
7. Merge only when the applicable required checks pass and any required manual/physical acceptance has been completed.

Do not invoke `$HOME/bin/w47-cloud`, `tools/w47-cloud`, or `codex cloud` unless the operator explicitly requests Codex Cloud for a specific task.

## Required automated checks

The current CI/security workflows provide the broad candidate gate:

- **Android unit, lint, APK**
  - `verifyResolvedCoreVersion`
  - `verifyPortableAapt2Configuration`
  - `testDebugUnitTest`
  - `testQaUnitTest`
  - `lintDebug`
  - `lintQa`
  - `assembleDebug`
  - `assembleQa`
  - `assembleQaAndroidTest`
  - Android artifact verification
  - release-signing fail-closed verification
- **Android emulator instrumentation**
  - API 36 x86_64 emulator on a standard GitHub-hosted Ubuntu runner
  - `connectedQaAndroidTest`
  - synthetic/local instrumentation only; no real user certificate or administrative submission
- **Python catalog and policy tests**
- **Go relay tests, race detector, vet, build and govulncheck**
- **Git history secret scan**
- **OSV source dependency scan**

The exact workflow result for the current PR head is authoritative. A pass from an older SHA is not sufficient after candidate changes.

## Emulator boundary

The emulator job is for repository instrumentation tests using synthetic fixtures, local WebView content and test doubles. It must not be extended to import a real user PKCS#12, authenticate to a private account, sign a real document, submit a government form, upload a real file, make a payment, or perform another administrative action.

Physical-device E2E remains separate when a feature genuinely requires device KeyChain/WebView/provider behavior or an operator-controlled authenticated flow.

## Failure and fallback policy

If GitHub Actions is unavailable or a required job cannot start, diagnose the CI failure first. Do not automatically fall back to a full phone-local Gradle gate or Codex Cloud.

A different full-gate environment is an operator-authorized incident fallback only. The operator must explicitly request that fallback for the incident.

## Historical Codex Cloud tooling

`tools/w47-cloud`, `$HOME/bin/w47-cloud`, the saved `workspace-47-android` environment, and historical Cloud acceptance records remain repository history/tooling. They are not the current default verification route.
