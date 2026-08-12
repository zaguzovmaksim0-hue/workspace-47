# OSS execution gates and GitHub Actions startup-failure runbook

**Scope:** final technical gates before a root project license and public visibility.

This document is evidence-oriented. A gate is PASS only from a successful execution on the exact publication candidate; configuration, a historical run on another SHA, or an expected result is not enough.

## Current publication model

- Publication branch: `oss/publication-readiness-20260811`
- Last accepted product-code cutoff: `4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb`
- Root-license candidate: Apache License 2.0
- Maintainer source-rights attestation: confirmed 2026-08-12
- Existing author/committer Gmail metadata: explicitly accepted for publication
- Former unresolved WebP/launcher PNG set: removed and replaced with project-origin XML/vector resources
- Autonomous worker: stopped; its working/cutoff refs were pinned back to the last Cloud-green product SHA after later in-flight TDD RED commits were identified

Public visibility and the root `LICENSE` remain blocked until the execution gates below pass on the same final candidate.

## GitHub Actions diagnosis

Repository-level workflow configuration has been reviewed and the `oss/**` trigger omission was corrected in both CI and Security workflows.

Observed facts:

1. GitHub reports real workflow `CI` (`.github/workflows/ci.yml`, id `332256667`) as active.
2. GitHub reports real workflow `Security scans` (`.github/workflows/security.yml`, id `332256669`) as active.
3. Push and pull-request events still create historical deleted workflow `BuildFailed` id `324591298` with `startup_failure` before any job/check-run exists.
4. Fresh failing suites have zero jobs/check-runs and are non-rerequestable.
5. Repository Actions-permissions, secret-scanning and user Actions-billing endpoints return `403 Resource not accessible by integration` to the connected GitHub App.
6. A minimal one-step `echo` workflow previously failed with the same pre-job signature, excluding Gradle, Android, Gitleaks and project YAML complexity as the root cause.

### Operational conclusion

Do not keep rewriting CI YAML without new evidence. Until the GitHub account/repository execution problem is resolved, a native Termux run is an accepted final verification channel for these source-publication gates.

## Preferred final execution — Termux aarch64

The repository contains a one-shot runner:

```bash
bash scripts/oss/run-termux-publication-gates.sh "$PWD"
```

The runner is fail-closed. It:

1. refuses a dirty or wrong repository;
2. fetches all refs/tags and fast-forwards the exact OSS branch;
3. installs the required Termux build utilities;
4. provisions the existing checksum-pinned project-local AAPT2 through `tools/bootstrap-termux-aapt2.sh`;
5. downloads and SHA-256 verifies Gitleaks 8.29.1 for Linux ARM64;
6. proves the detector works using a synthetic runtime-only GitHub-PAT canary;
7. scans Git history across **all reachable refs**;
8. runs the publication visual-path policy and repository Python tests;
9. runs Android unit tests, lint, Debug/QA/androidTest assembly, artifact checks and the release-signing fail-closed gate;
10. records the exact verified commit and Gitleaks report hash under ignored `.gradle/oss-publication-gates/`.

The runner does not reset or overwrite dirty local work.

## Gate A — exact candidate / branch integrity

Immediately before verification:

```bash
git fetch --all --tags --prune
git switch oss/publication-readiness-20260811
git pull --ff-only origin oss/publication-readiness-20260811
git status --short
git rev-parse HEAD
```

PASS requires a clean worktree and a known exact candidate SHA.

The publication candidate intentionally uses the last Cloud-green product boundary rather than the later interrupted TDD RED work. Do not reintroduce the removed RED-only Extremadura catalog test without its corresponding GREEN implementation and verification.

## Gate B — full-history Gitleaks

### Scanner selection

Do **not** use Gitleaks 8.30.1 for the publication decision. Upstream issue `gitleaks/gitleaks#2170` documented a regression where 8.30.1 could return `no leaks found` for canonical secrets.

The current gate pins Gitleaks **8.29.1** and verifies official GitHub release digests:

- Linux ARM64: `691f826ce7c1c564c9c02d0f9025e8e70803e3816707a4be6224408a06a81eaa`
- Linux x64: `e4eb209d04e20339d77122a3bdf9cd41351255cfb27ebcb75e85325e04f88924`

Before the repository scan, the binary must detect a synthetic GitHub-PAT-shaped canary and return the expected non-zero detector exit code. This prevents a silently broken detector from producing a false publication PASS.

### Required scan scope

After `git fetch --all --tags --prune`, scan all reachable refs:

```bash
gitleaks git \
  --redact \
  --no-banner \
  --log-opts="--all" \
  --report-format sarif \
  --report-path gitleaks.sarif \
  .
```

PASS requires exit code `0` and no unresolved real-secret finding.

The current `.gitleaks.toml` contains only narrow documented allowlists. One exact synthetic canary string appeared in a private historical workflow commit before the runtime canary was split into two strings; the allowlist is constrained to rule `github-pat`, that exact fake value and `.github/workflows/security.yml` only.

If a real secret is found, stop publication. Rotate/revoke where applicable, remove the secret from every ref/history that will become public, and rerun the entire scan.

## Gate C — publication visual-asset policy

```bash
python tools/test_publication_visual_assets.py
```

PASS requires that none of the 21 former unresolved binary asset paths is present.

This path-level policy is not a substitute for Android resource compilation.

## Gate D — Android/Gradle verification

Native Termux uses the tracked project-local AAPT2 bootstrap. `gradlew` verifies that bootstrap and automatically injects the project property override only for Termux aarch64.

Provision first when needed:

```bash
./tools/bootstrap-termux-aapt2.sh bootstrap
```

Then run on the same candidate SHA:

```bash
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration --no-daemon
./gradlew testDebugUnitTest testQaUnitTest --no-daemon
./gradlew lintDebug lintQa --no-daemon
./gradlew assembleDebug assembleQa assembleQaAndroidTest --no-daemon
scripts/ci/verify-android-artifacts.sh
scripts/ci/verify-release-fail-closed.sh
```

PASS requires every command to exit `0`.

The publication-specific purpose is to prove that the project-origin XML/vector replacements compile/package correctly and that release signing remains fail-closed without private signing material.

## Gate E — supporting repository checks

```bash
python -m unittest discover -s tools/tests -p 'test_*.py' -v
```

When the required Go toolchain is available, also run:

```bash
(
  cd ws024-relay
  go test ./... -count=1
  go test ./... -race -count=1
  go vet ./...
  go build ./cmd/ws024-relay
)
```

These supporting checks do not substitute for the history scan or Android build gates.

## Alternative execution — Codex Cloud

Codex Cloud remains acceptable if the exact final OSS candidate can be checked out read-only and the same commands above are executed. Record the exact requested/checked-out SHA and complete logs. A prior Cloud PASS on `4bf6afb...` establishes the product-code baseline but does not compile the later OSS visual-resource replacements, so a fresh final-candidate run is still required.

## After every source-publication gate passes

Only after fresh evidence for the **same final commit** shows:

- full-history/all-refs Gitleaks PASS with canary self-test;
- publication visual policy PASS;
- Android unit/lint/build/artifact/fail-closed checks PASS;
- no newly introduced provenance/license blocker;

then:

1. re-review `docs/provenance.md`, `docs/license-selection.md`, `NOTICE`, `README.md`, `SECURITY.md`, and `CONTRIBUTING.md` against that exact SHA;
2. add the selected Apache-2.0 root `LICENSE` for project-origin material without relicensing separately licensed third-party material;
3. mark `docs/oss-publication-status.md` approved for source publication;
4. make the repository public only after those changes are verified;
5. verify the public repository URL, default branch, README/license rendering, maintainer control and public metadata;
6. only then prepare the truthful Codex for OSS application.

## Binary distribution remains separate

A source-publication PASS does not approve APK/AAB redistribution. Before distributing binaries, complete the exact-artifact dependency/NOTICE procedure in `docs/licenses/runtime-dependency-audit.md`.
