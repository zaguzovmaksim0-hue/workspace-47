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
- Android Gradle/JVM/Kotlin execution boundary: **Codex Cloud only**, environment `workspace-47-android`, submitted from Termux through `$HOME/bin/w47-cloud`

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

Do not keep rewriting CI YAML without new evidence. GitHub Actions is not required to finish this private pre-publication gate: Termux performs the non-Android checks and submits the exact same SHA to the existing Codex Cloud Android environment.

## Preferred final execution — one command from Termux

Run from a clean `workspace-47` checkout:

```bash
bash scripts/oss/run-termux-publication-gates.sh "$PWD"
```

The runner is fail-closed and performs the complete orchestration:

1. verifies the expected repository and refuses dirty local work;
2. fetches all refs/tags, switches to and fast-forwards `oss/publication-readiness-20260811`, and records the exact 40-hex candidate SHA;
3. installs only Termux-safe prerequisites used for Git/Python/Gitleaks;
4. downloads **Gitleaks 8.29.1 ARM64**, verifies its official release SHA-256, and extracts a fresh executable from the verified archive;
5. proves the scanner works by requiring detection of a runtime-only synthetic GitHub-PAT canary;
6. scans Git history across **all reachable refs** with `--log-opts="--all"`;
7. runs `tools/test_publication_visual_assets.py` and the repository Python test suite locally;
8. invokes the canonical Android gate **only in Codex Cloud**:

   ```bash
   $HOME/bin/w47-cloud full \
     --branch oss/publication-readiness-20260811 \
     --sha <exact-candidate-sha>
   ```

9. requires Cloud evidence to contain the requested exact SHA and a `task_e_...` task id;
10. hashes and stores the Gitleaks SARIF and Cloud transcript under ignored `.gradle/oss-publication-gates/<sha>/`.

No local `./gradlew`, JVM/Kotlin compilation, APK build, install, ADB or device/portal action is performed by this runner.

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

Do **not** use Gitleaks 8.30.1 for the publication decision. Upstream issue `gitleaks/gitleaks#2170` documented a regression in which 8.30.1 could return a false clean result for canonical secrets.

The current gate pins Gitleaks **8.29.1** and official release digests:

- Linux ARM64: `691f826ce7c1c564c9c02d0f9025e8e70803e3816707a4be6224408a06a81eaa`
- Linux x64: `e4eb209d04e20339d77122a3bdf9cd41351255cfb27ebcb75e85325e04f88924`

Before scanning the repository, the binary must detect a synthetic GitHub-PAT-shaped canary and return the configured detector exit code. This prevents a silently broken scanner from producing a publication PASS.

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

## Gate D — Android verification, Codex Cloud only

Canonical command:

```bash
$HOME/bin/w47-cloud full \
  --branch oss/publication-readiness-20260811 \
  --sha <exact-40-hex-sha>
```

The Cloud gate uses environment `workspace-47-android` and executes the canonical Android verification set:

```text
verifyResolvedCoreVersion
verifyPortableAapt2Configuration
testDebugUnitTest
testQaUnitTest
lintDebug
lintQa
assembleDebug
assembleQa
assembleQaAndroidTest
```

Cloud acceptance requires:

- Cloud `git rev-parse HEAD` exactly equals the requested SHA;
- dependency verification remains enabled;
- Android SDK/API 36 and Build Tools 36.0.0 resolve;
- every requested Gradle task succeeds;
- required artifact/release fail-closed verification succeeds;
- Cloud checkout remains clean;
- task id/URL and transcript are recorded.

There is **no phone-local Gradle fallback**.

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

These checks do not substitute for the secret-history scan or Cloud Android gate.

## After every source-publication gate passes

Only after fresh evidence for the **same final commit** shows:

- all-refs Gitleaks PASS after canary self-test;
- publication visual/Python policy PASS;
- canonical Codex Cloud Android gate PASS;
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
