# OSS execution gates and GitHub Actions startup-failure runbook

**Scope:** final technical gates before a root project license and public visibility.

A gate is PASS only from successful execution on the exact publication candidate. Configuration, an older successful SHA, or expected behavior is not sufficient.

## Current publication model

- Publication branch: `oss/publication-readiness-20260811`
- Last accepted product-code cutoff: `4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb`
- Root-license candidate: Apache License 2.0
- Maintainer source-rights attestation: confirmed 2026-08-12
- Existing author/committer Gmail metadata: explicitly accepted for publication
- Former unresolved WebP/launcher PNG set: removed and replaced with project-origin XML/vector resources
- Android Gradle/JVM/Kotlin execution boundary: **native Termux** on aarch64 with project-local verified AAPT2; Gradle/compilation runs on Java 17 and Robolectric Android-SDK-36 test workers run on Java 21

Public visibility and the root `LICENSE` remain blocked until the final orchestrated run passes on one exact candidate SHA.

## GitHub Actions diagnosis

Repository-level workflow configuration was reviewed and the missing `oss/**` trigger was corrected in CI and Security workflows.

Observed facts:

1. Real workflow `CI` (`.github/workflows/ci.yml`, id `332256667`) is active.
2. Real workflow `Security scans` (`.github/workflows/security.yml`, id `332256669`) is active.
3. Push and pull-request events still create historical deleted workflow `BuildFailed` id `324591298` with `startup_failure` before any job/check-run exists.
4. Fresh failing suites contain zero jobs/check-runs and are non-rerequestable.
5. Repository Actions-permissions, secret-scanning and user Actions-billing endpoints return `403 Resource not accessible by integration` to the connected GitHub App.
6. A minimal one-step `echo` workflow previously failed with the same pre-job signature, excluding Gradle, Android, Gitleaks and workflow complexity as the root cause.

### Operational conclusion

Do not keep rewriting CI YAML without new evidence. GitHub Actions is not required to finish this private pre-publication gate: the exact candidate is verified directly in native Termux with the project-local AAPT2 bootstrap and pinned Java runtimes.

## Preferred final execution — one command from Termux

Run from a clean `workspace-47` checkout:

```bash
bash scripts/oss/run-termux-publication-gates.sh "$PWD"
```

The runner is fail-closed and performs the complete orchestration:

1. verifies the expected repository and refuses dirty local work;
2. fetches all refs/tags, switches to and fast-forwards `oss/publication-readiness-20260811`, and records the exact 40-hex candidate SHA;
3. installs the minimal Termux prerequisites, including OpenJDK 17/21 and Go;
4. builds exact **Gitleaks 8.30.1** from the official Go module and verifies the embedded module/version identity;
5. proves the scanner works by requiring detection of a high-entropy runtime-only synthetic GitHub-PAT canary;
6. scans Git history across **all reachable refs** with `--log-opts="--all"`;
7. runs publication visual policy and Python checks;
8. runs the complete Android Gradle/artifact/release-fail-closed gate locally in native Termux, then local Go relay supporting checks;
9. stores ignored evidence under `.gradle/oss-publication-gates/<sha>/` and refuses tracked worktree mutations.

The runner never installs or launches an APK, never uses ADB/Shizuku/UI automation, and never supplies real signing credentials.

## Gate A — exact candidate integrity

The runner performs:

```bash
git fetch --all --tags --prune
git switch oss/publication-readiness-20260811
git pull --ff-only origin oss/publication-readiness-20260811
git status --short
git rev-parse HEAD
```

PASS requires a clean worktree and a fixed 40-hex candidate SHA.

The publication candidate intentionally uses the last Cloud-green product boundary rather than the later interrupted TDD RED work. Do not reintroduce the removed RED-only Extremadura catalog test without its corresponding GREEN implementation and verification.

## Gate B — full-history Gitleaks

### Scanner selection

The publication gate uses exact Gitleaks **8.30.1**. Native Android/Termux does not execute the upstream Linux ARM64 release binary reliably because the binary can hit Android seccomp (`SIGSYS`) while resolving `git`; therefore the Termux runner builds the exact `github.com/zricethezav/gitleaks/v8@v8.30.1` official Go module locally and verifies its module/version identity before use.

A high-entropy synthetic GitHub-PAT-shaped canary must return the configured detector exit code before the repository scan starts. This specifically guards against a false-clean scanner or an invalid low-entropy canary without treating the canary as a real credential.

Required history scope:

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

`.gitleaks.toml` contains narrow documented allowlists only. One exact synthetic canary appeared in a private historical workflow commit before the runtime canary was split; its exception is limited to the `github-pat` rule, that exact fake value and `.github/workflows/security.yml`.

Any real secret finding stops publication until the credential is handled appropriately and every public-bound ref/history is clean on a fresh full scan.

## Gate C — publication visual and Python policy

```bash
python tools/test_publication_visual_assets.py
python -m unittest discover -s tools/tests -p 'test_*.py' -v
```

The visual policy must prove that none of the 21 former unresolved binary asset paths has been reintroduced.

## Gate D — Android verification, native Termux

Run with Java 17 as the Gradle launcher. The tracked `gradlew` Termux hook supplies the verified project-local AAPT2 override and exposes the installed Java 17/21 homes to Gradle toolchain discovery; Robolectric SDK 36 tests use a forked Java 21 worker without changing the Java 17 compile/bytecode target.

```bash
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --version
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration --no-daemon
./gradlew testDebugUnitTest testQaUnitTest --no-daemon
./gradlew lintDebug lintQa --no-daemon
./gradlew assembleDebug assembleQa assembleQaAndroidTest --no-daemon
scripts/ci/verify-android-artifacts.sh
scripts/ci/verify-release-fail-closed.sh
```

PASS requires every command to exit `0`. `verify-release-fail-closed.sh` must prove that an unsigned release build fails closed without producing a release APK. No APK install, app launch, ADB/Shizuku/UI automation, production signing, or real signing credential is part of this gate.

## Gate E — supporting relay checks

When independently needed and an approved execution environment is available:

```bash
(
  cd ws024-relay
  go test ./... -count=1
  go test ./... -race -count=1
  go vet ./...
  go build ./cmd/ws024-relay
)
```

These checks do not substitute for the secret-history scan or native Termux Android gate.

## After every source-publication gate passes

Only after fresh evidence for the **same final commit** shows:

- all-refs Gitleaks PASS after canary self-test;
- publication visual/Python policy PASS;
- native Termux Android/Gradle/artifact/release-fail-closed gate PASS;
- no newly introduced provenance/license blocker;

then:

1. re-review `docs/provenance.md`, `docs/license-selection.md`, `NOTICE`, `README.md`, `SECURITY.md`, and `CONTRIBUTING.md` against that exact SHA;
2. add the Apache-2.0 root `LICENSE` for project-origin material without relicensing separately licensed third-party material;
3. mark `docs/oss-publication-status.md` approved for source publication;
4. only then change repository visibility to public;
5. verify public URL, default branch, README/license rendering, maintainer control and public metadata;
6. only then prepare the truthful Codex for OSS application.

## Binary distribution remains separate

A source-publication PASS does not approve APK/AAB redistribution. Before binary distribution, complete the exact-artifact dependency/NOTICE procedure in `docs/licenses/runtime-dependency-audit.md`.
