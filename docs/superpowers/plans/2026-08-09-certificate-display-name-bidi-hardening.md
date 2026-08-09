# Certificate Display-Name Bidi Hardening Implementation Plan

> Use `executing-plans`, `test-driven-development`, `systematic-debugging`, and
> `verification-before-completion` for this milestone.

**Goal:** prevent an external certificate provider from using Unicode bidi controls to visually
spoof the selected certificate name in trusted native UI.

**Architecture:** keep certificate selection and persistence unchanged; harden only the existing
`sanitizeDisplayName()` presentation boundary and pin it with a repository-level regression.

## Task 1: Reproduce bidi controls surviving certificate display-name sanitization

- [x] Modify only `CertificateRepositoryTest.kt` after fresh repository-state verification.
- [x] Add an official-PKCS12-MIME provider display name containing U+202E and U+2066.
- [x] Require returned and persisted `displayName` to equal the same text with bidi controls removed.
- [x] Run the exact Debug test with `--rerun-tasks` and observe the expected RED on the display-name
      assertion; inspect XML to prove the failure is the intended policy gap.

## Task 2: Implement the minimum display-name policy

- [x] Modify only `CertificateRepository.kt` after fresh repository-state verification.
- [x] Add an explicit predicate for the Unicode `Bidi_Control` code points U+061C, U+200E..U+200F,
      U+202A..U+202E and U+2066..U+2069.
- [x] Exclude those characters inside the existing `sanitizeDisplayName()` filter while preserving
      existing length, trim, fallback, URI/MIME/extension and permission behavior.
- [x] Run the exact GREEN in Debug+QA and then complete `CertificateRepositoryTest` plus adjacent
      certificate-reference/view-model tests.

## Task 3: Review and full verification

- [x] Request a focused independent reviewer after GREEN if a Luna slot is available; resolve any
      Critical/Important finding before publication.
- [x] Run fresh runtime dependency-lock/resolved-core/portable-AAPT2 checks plus all Debug/QA JVM
      tests and aggregate XML counts.
- [x] Run `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`; record lint
      counts and APK SHA-256 values.
- [x] Run complete Python tests, Go test/vet/build, Android artifact verification and release-signing
      fail-closed; remove generated relay and require zero release APKs.
- [x] Inspect complete diff; run `git diff --check`, exact-scope, sensitive-data and unsafe
      WebView/TLS/signing scans; run `CiPolicyTest`.

## Task 4: Evidence and publication

- [x] Update only evidence documents whose facts changed, stating the limited provider-display-name
      spoofing claim and that no physical certificate/device/portal evidence is inferred.
- [ ] Re-run focused Debug+QA tests, `CiPolicyTest` and `git diff --check` after evidence edits.
- [ ] Fetch/reverify divergence/canonical ref; stage exact G33 files, inspect staged-only full diff
      and staged sensitive/unsafe scans.
- [ ] Commit atomically as `fix(certificate): strip bidi controls from display names`.
- [ ] Push the autonomous branch, fetch, verify local/tracking/`ls-remote` SHA equality, divergence
      `0/0`, clean worktree, immutable canonical SHA, generated relay absent and release APK count 0.

## Generation 30 observed progress

- RED `job_20260809_061849_7ac334d7`, parsed by `job_20260809_062203_2df5dc47`: 1 test,
  1 intended failure, zero errors/skips; expected plain `certevil.p12` but the provider name still
  contained U+202E/U+2066.
- Minimum production fix in `CertificateRepository.kt` removes only the Unicode `Bidi_Control`
  set named by the design.
- Focused Debug+QA GREEN `job_20260809_062238_b55ea965` exited 0.
- Adjacent certificate/session/view-model Debug+QA `job_20260809_062810_5a8ca124`, parsed by
  `job_20260809_063542_038903d0`: 61/61 per variant, zero failures/errors/skips.
- Full JVM/lint/build/Python/Go/artifact/release/policy gates, evidence-document updates, atomic
  commit and push have not started for G33-01.

## Generation 31 pre-publication evidence

- Independent reviewer `worker-6`: no Critical/Important findings; one Minor about exhaustive
  per-code-point test coverage. `worker-5` timed out and is not counted as evidence.
- Fresh full runtime-lock/core/AAPT2 + JVM `job_20260809_064502_0a3b3fd4` passed 63/63 tasks;
  `job_20260809_065218_d0b5d4aa` = Debug 569/569 + QA 569/569, zero failures/errors/skips.
- Fresh lint/build `job_20260809_065233_fa7e0074` passed 124/124 tasks, 0 lint errors / 26
  warnings per variant. Artifact `job_20260809_070110_c77aadef` and release fail-closed
  `job_20260809_070134_ca0d3d1d` passed; release APK count zero.
- Fresh Python/Go `job_20260809_064534_ce140f90` passed Python 102 with one environmental
  hardlink skip and Go test/vet/build. Cleanup `job_20260809_070255_f1a08f2f` removed relay.
- Evidence documents are updated in this pre-publication tree. Post-evidence focused/policy checks,
  exact staged review, atomic commit, push and remote SHA verification remain next.
