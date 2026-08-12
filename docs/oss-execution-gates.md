# OSS execution gates

**Final candidate branch:** `oss/publication-candidate-final-20260812`

**Product baseline:** `4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb`, the last autonomous product SHA with recorded Codex Cloud acceptance (Debug 656/656 plus QA 35/35, 691/691 total). Later interrupted TDD RED work is intentionally excluded.

**License candidate:** Apache License 2.0.

The repository stays private and has no root project `LICENSE` until every mandatory gate below passes on the **same exact final-candidate SHA**.

## GitHub Actions status

The real `CI` and `Security scans` workflows are registered and active and include `oss/**`. Push and pull-request events nevertheless still terminate as historical deleted workflow `BuildFailed` / `startup_failure` before any job or check-run exists. A minimal one-step workflow reproduced the same pre-job condition.

Do not keep modifying project CI YAML without new evidence. Private pre-publication verification uses Termux for non-Android checks and the existing Codex Cloud Android environment for Gradle/JVM/Kotlin.

## One-shot final verification from Termux

From a clean checkout run:

```bash
git fetch --all --tags --prune
git switch --force-create oss/publication-candidate-final-20260812 \
  origin/oss/publication-candidate-final-20260812
bash scripts/oss/run-termux-publication-gates.sh "$PWD"
```

The tracked runner is fail-closed. It:

1. verifies native Termux aarch64, the expected repository, and a clean worktree;
2. fetches all refs/tags and fast-forwards the final candidate branch;
3. records the exact 40-hex candidate SHA;
4. installs only Termux-safe prerequisites for Git, Python and Gitleaks;
5. downloads Gitleaks 8.29.1 ARM64 and verifies the official release SHA-256;
6. proves the detector works using a runtime-only synthetic GitHub-PAT canary;
7. scans Git history across **all reachable refs** with `--log-opts="--all"`;
8. runs the publication visual-path policy and repository Python tests locally;
9. submits the exact same SHA to Codex Cloud using:

   ```bash
   $HOME/bin/w47-cloud full \
     --branch oss/publication-candidate-final-20260812 \
     --sha <exact-candidate-sha>
   ```

10. captures complete Cloud stdout/stderr, requires the exact SHA and a `task_e_...` task id in the evidence, and hashes the evidence files under ignored `.gradle/oss-publication-gates/<sha>/`.

The runner does **not** execute local Android Gradle/JVM/Kotlin, build/install an APK, use ADB, or interact with government portals.

## Gate A — exact candidate integrity

PASS requires:

- branch `oss/publication-candidate-final-20260812`;
- clean worktree;
- exact candidate SHA recorded before any scan;
- no candidate mutation between local and Cloud verification.

If the branch moves after a PASS, all SHA-dependent gates must be rerun.

## Gate B — full-history Gitleaks

Do **not** use Gitleaks 8.30.1 for the publication decision. Upstream issue `gitleaks/gitleaks#2170` documents a false-negative regression affecting that version.

Pinned Gitleaks 8.29.1 release digests:

- Linux ARM64: `691f826ce7c1c564c9c02d0f9025e8e70803e3816707a4be6224408a06a81eaa`
- Linux x64: `e4eb209d04e20339d77122a3bdf9cd41351255cfb27ebcb75e85325e04f88924`

Required scan scope:

```bash
gitleaks git \
  --redact \
  --no-banner \
  --log-opts="--all" \
  --report-format sarif \
  --report-path gitleaks.sarif \
  .
```

PASS requires the detector canary to fire first, then the repository scan to exit `0` with no unresolved real-secret finding.

`.gitleaks.toml` retains default rules and only narrow documented exceptions. One exact fake canary string appeared in a private historical workflow commit before the runtime canary was split; its allowlist is limited to rule `github-pat`, that exact fake value, and `.github/workflows/security.yml`.

Any real secret finding stops publication until it is appropriately revoked/rotated where applicable, removed from public-bound refs/history, and the complete scan passes again.

## Gate C — visual and Python policy

```bash
python tools/test_publication_visual_assets.py
python -m unittest discover -s tools/tests -p 'test_*.py' -v
```

PASS requires that none of the 21 former unresolved binary visual paths is present and all policy/catalog tests pass.

## Gate D — Android verification in Codex Cloud only

Canonical submission:

```bash
$HOME/bin/w47-cloud full \
  --branch oss/publication-candidate-final-20260812 \
  --sha <exact-40-hex-sha>
```

Environment: `workspace-47-android`.

Canonical Android gate:

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
- Android API 36 / Build Tools 36.0.0 resolve;
- every requested Gradle task exits `0`;
- required artifact and release fail-closed checks pass;
- Cloud verification checkout remains clean;
- task id/URL and complete transcript are retained.

There is no automatic phone-local Gradle fallback.

## After all source-publication gates pass

Only after fresh evidence for the same SHA shows:

- all-refs Gitleaks PASS after detector canary;
- visual/Python policy PASS;
- canonical Codex Cloud Android PASS;
- no new provenance/license blocker;

then:

1. re-review `README.md`, `SECURITY.md`, `CONTRIBUTING.md`, `NOTICE`, `docs/provenance.md`, and `docs/license-selection.md` against that SHA;
2. add root Apache-2.0 `LICENSE` for project-origin material without relicensing separately licensed third-party material;
3. mark `docs/oss-publication-status.md` approved for source publication;
4. fast-forward `main` to the approved candidate;
5. set `main` as the public default branch;
6. only then change repository visibility to public;
7. verify the public URL, README/license rendering, maintainer control and repository metadata;
8. only then prepare the Codex for OSS application.

## Binary distribution remains separate

Source-publication approval does not approve APK/AAB redistribution. Complete the exact-artifact dependency/NOTICE procedure in `docs/licenses/runtime-dependency-audit.md` before binary distribution.
