# OSS execution gates and GitHub Actions startup-failure runbook

**Scope:** final technical gates before a root project license/public visibility.

This document records the current execution blocker and the exact checks that must be run on the final synchronized publication candidate. It is intentionally evidence-oriented: do not mark a gate PASS from configuration, previous runs on another commit, or expected behavior.

## Current publication candidate

- Publication branch: `oss/publication-readiness-20260811`
- Project root-license candidate: Apache License 2.0
- Maintainer source-rights attestation: confirmed 2026-08-12
- Existing author/committer Gmail metadata: explicitly accepted for publication
- Former unresolved WebP/launcher PNG set: removed and replaced with project-origin XML/vector resources

Public visibility and a root `LICENSE` remain blocked until the execution gates below pass on a final synchronized candidate.

## GitHub Actions diagnosis

Repository-level workflow configuration has been checked and the publication-branch trigger gap has been corrected.

Observed facts:

1. Repository default branch is `feature/redsara-profile`.
2. GitHub reports workflow `CI` (`.github/workflows/ci.yml`, workflow id `332256667`) as `active`.
3. GitHub reports workflow `Security scans` (`.github/workflows/security.yml`, workflow id `332256669`) as `active`.
4. Both workflows originally matched `main`, `feature/**`, and `agent/**`, but not `oss/**`.
5. The OSS candidate now adds `oss/**` to both push filters.
6. Even after that correction, the real `CI` and `Security scans` workflow IDs report zero runs for the OSS branch.
7. Pushes instead create a check suite associated with historical workflow id `324591298`, path `BuildFailed`, state `deleted`, and conclusion `startup_failure`.
8. Fresh failing check suites contain zero jobs/check-runs and report `rerequestable=false` / `runs_rerequestable=false`.
9. A direct retry request for a startup-failed run returned `403 This workflow run cannot be retried`.
10. Repository Actions-permissions API, secret-scanning API, and user Actions-billing API return `403 Resource not accessible by integration` to the connected GitHub App.
11. No successful Actions run is currently exposed for this repository through the Actions run API.

### Operational conclusion

Do not keep rewriting CI YAML to chase this symptom without new evidence. The publication-branch trigger omission was real and is fixed, but it did not cause the pre-job `BuildFailed/startup_failure` condition.

The remaining failure must be resolved through a working execution channel or GitHub account/repository administration/support that can see the unavailable Actions settings/billing diagnostics.

## Gate A — final synchronization

Immediately before execution:

```bash
git fetch --all --prune
git checkout oss/publication-readiness-20260811
git status --short
git rev-parse HEAD
git rev-list --left-right --count \
  origin/agent/workspace-47-autonomous-20260803...HEAD
```

Requirements:

- working tree is clean;
- publication branch contains the intended latest autonomous head (`behind` count must be `0` at the chosen publication cutoff);
- inspect all newly synchronized files for new binaries, vendored source, credentials, release-signing material, or material that changes the license/provenance conclusion;
- if autonomous development advances after this check, repeat synchronization and all tree-dependent checks.

## Gate B — full-history Gitleaks

Use the same pinned scanner and checksum as `.github/workflows/security.yml`.

```bash
set -euo pipefail
archive="gitleaks_8.30.1_linux_x64.tar.gz"
url="https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/${archive}"
curl --fail --location --proto '=https' --tlsv1.2 "$url" --output "$archive"
echo "551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb  ${archive}" \
  | sha256sum --check --strict
tar --extract --gzip --file "$archive" gitleaks
chmod 700 gitleaks
./gitleaks git --redact --no-banner --report-format sarif \
  --report-path gitleaks.sarif .
```

PASS requires exit code `0` on the final synchronized candidate and no unresolved secret finding.

If a real secret is found, stop publication. Rotate/revoke the affected credential where applicable, remove it from the candidate/history as required, then rerun the complete scan. Do not dismiss a finding merely because the current tree no longer contains the value.

## Gate C — publication visual-asset policy

Run the path-level regression test after synchronization:

```bash
python tools/test_publication_visual_assets.py
```

PASS requires that none of the 21 former unresolved binary asset paths has been reintroduced.

This test is not a substitute for Android resource compilation.

## Gate D — Android/Gradle verification

Use Java 17 and Android API/build-tools 36 as declared by CI.

```bash
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration --no-daemon
./gradlew testDebugUnitTest testQaUnitTest --no-daemon
./gradlew lintDebug lintQa --no-daemon
./gradlew assembleDebug assembleQa assembleQaAndroidTest --no-daemon
scripts/ci/verify-android-artifacts.sh
scripts/ci/verify-release-fail-closed.sh
```

PASS requires every command to exit `0` on the same synchronized commit.

The key publication-specific purpose is to prove that the project-origin vector/XML replacements compile/package correctly and that release signing still fails closed without private signing material.

## Gate E — supporting project checks

Run the repository Python policy/catalog tests:

```bash
python -m unittest discover -s tools/tests -p 'test_*.py' -v
```

Run relay checks when the required Go toolchain is available:

```bash
(
  cd ws024-relay
  go test ./... -count=1
  go test ./... -race -count=1
  go vet ./...
  go build ./cmd/ws024-relay
)
```

These checks are supporting verification; do not substitute them for the history scan or Android build gates.

## After all source-publication gates pass

Only after fresh evidence for the same final commit shows:

- autonomous synchronization behind-count `0` at the chosen cutoff;
- full-history Gitleaks PASS;
- publication visual policy PASS;
- Android unit/lint/build/artifact/fail-closed checks PASS;
- no newly introduced provenance/license blocker;

then:

1. re-review `docs/provenance.md`, `docs/license-selection.md`, `NOTICE`, `README.md`, `SECURITY.md`, and `CONTRIBUTING.md` against that exact commit;
2. add the selected root Apache-2.0 `LICENSE` for project-origin material without relicensing separately licensed third-party material;
3. update `docs/oss-publication-status.md` from pre-publication to approved source-publication state;
4. only then change repository visibility to public;
5. verify the public repository URL, maintainer access, README/license rendering and public metadata before preparing the Codex for OSS form.

## Binary distribution remains separate

A source-publication PASS does not approve APK/AAB redistribution. Before distributing binaries, complete the exact-artifact dependency/NOTICE procedure in `docs/licenses/runtime-dependency-audit.md`.
