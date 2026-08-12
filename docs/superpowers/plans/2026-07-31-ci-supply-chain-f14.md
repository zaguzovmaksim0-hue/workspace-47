# CI and Supply-Chain Gate F-14 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a fail-closed, reproducible CI and supply-chain gate for Android, Python, and the WS024 Go relay without introducing release secrets or weakening direct-only production policy.

**Architecture:** GitHub Actions runs independent Android, Python, Go, secret-history, and dependency-vulnerability jobs with read-only permissions and immutable action SHAs. Gradle verifies dependency artifacts and its own distribution checksum; local shell gates verify APK alignment, signer count, manifest hardening, forbidden canaries, and release-signing failure without private credentials. A Python policy test guards the workflow contract as repository data.

**Tech Stack:** GitHub Actions, Gradle 9.4.1, Android Gradle Plugin 9.2.1, Python `unittest`, Go 1.26.5, Gitleaks 8.30.1, govulncheck 1.6.0, OSV-Scanner 2.3.8.

## Global Constraints

- Work only on `feature/ws024-secure-tunnel-20260728` in the existing isolated worktree.
- Do not store passwords, PKCS#12 bytes, private keys, certificate bodies, signatures, cookies, relay credentials, or personal identifiers.
- Release remains direct-only and must fail closed when private signing material is absent.
- GitHub Actions permissions remain `contents: read`; do not use `pull_request_target`.
- Every third-party action is pinned to an exact 40-character commit SHA.
- Keep temporary scanners, exports, reports, and downloaded binaries outside the commit.

---

### Task 1: Close CI Policy Gaps with TDD

**Files:**
- Modify: `tools/tests/test_ci_policy.py`
- Modify: `.github/workflows/ci.yml`
- Modify: `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Consumes: existing F-14 workflow and policy-test draft in the worktree.
- Produces: policy assertions for a valid Go cache contract, safe Android SDK license pipeline, and pinned Gradle distribution checksum.

- [x] **Step 1: Write failing policy assertions**

Add assertions that:

```python
wrapper = self.read(GRADLE_WRAPPER_PROPERTIES)
self.assertRegex(wrapper, r"(?m)^distributionSha256Sum=[0-9a-f]{64}$")
self.assertNotIn("cache-dependency-path: ws024-relay/go.sum", ci_source)
self.assertIn("cache: false", ci_source)
self.assertIn("set +o pipefail", ci_source)
self.assertIn("set -o pipefail", ci_source)
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
python -m unittest tools.tests.test_ci_policy.CiPolicyTest -v
```

Expected: failure because the wrapper checksum is absent, the workflow references a missing `go.sum`, and the license pipeline keeps `pipefail` active around `yes`.

- [x] **Step 3: Implement the minimal workflow/configuration correction**

Change the Go setup to:

```yaml
with:
  go-version: ${{ env.GO_VERSION }}
  cache: false
```

Change Android license acceptance to disable `pipefail` only around the `yes | sdkmanager --licenses` pipeline, then restore it before installing SDK packages. Add the official SHA-256 value for `gradle-9.4.1-bin.zip` as `distributionSha256Sum`.

- [x] **Step 4: Run the focused test and verify GREEN**

Run:

```bash
python -m unittest tools.tests.test_ci_policy.CiPolicyTest -v
```

Expected: all policy tests pass.

---

### Task 2: Validate Immutable Inputs and Local Security Scans

**Files:**
- Review: `.github/workflows/ci.yml`
- Review: `.github/workflows/security.yml`
- Review: `.gitleaks.toml`
- Review: `gradle/verification-metadata.xml`

**Interfaces:**
- Consumes: public release tags/checksums and the repository's complete Git history.
- Produces: evidence that action refs, scanner versions, checksums, and allowlists resolve exactly and do not hide findings broadly.

- [x] **Step 1: Verify action tag commits**

For every action comment/tag pair, compare the pinned SHA with the peeled tag returned by the action's official Git repository.

- [x] **Step 2: Verify Gradle and Gitleaks checksums**

Run:

```bash
curl --fail --location --proto '=https' --tlsv1.2 \
  https://services.gradle.org/distributions/gradle-9.4.1-bin.zip.sha256
sha256sum .tmp-f14/gitleaks/gitleaks.tar.gz
```

Expected: exact matches to tracked workflow/properties values.

- [x] **Step 3: Scan complete Git history with redaction**

Run:

```bash
.tmp-f14/gitleaks/gitleaks git --redact --no-banner \
  --report-format json --report-path .tmp-f14/gitleaks-history-report.json .
```

Expected: exit 0 and an empty report.

- [x] **Step 4: Validate workflow YAML and shell syntax**

Run a YAML parser over both workflow files and:

```bash
bash -n scripts/ci/verify-android-artifacts.sh
bash -n scripts/ci/verify-release-fail-closed.sh
```

Expected: no parse or shell syntax errors.

---

### Task 3: Run the Full F-14 Verification Gate

**Files:**
- No production-file changes expected.

**Interfaces:**
- Consumes: all F-14 configuration and existing application/relay tests.
- Produces: fresh local evidence for the final commit.

- [x] **Step 1: Run all Python tests**

```bash
python -m unittest discover -s tools/tests -p 'test_*.py' -v
```

- [x] **Step 2: Run Android dependency, unit, lint, and build gates**

```bash
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration \
  testDebugUnitTest testQaUnitTest lintDebug lintQa \
  assembleDebug assembleQa assembleQaAndroidTest --no-daemon
```

- [x] **Step 3: Verify Android artifacts and release failure**

```bash
scripts/ci/verify-android-artifacts.sh
scripts/ci/verify-release-fail-closed.sh
```

- [x] **Step 4: Run Go relay gates**

```bash
cd ws024-relay
go test ./... -count=1
go vet ./...
go build ./cmd/ws024-relay
```

Run `go test ./... -race -count=1` only on a supported Linux toolchain; record Android/arm64 inability as environmental, not PASS.

- [x] **Step 5: Run vulnerability scanners where locally supported**

```bash
govulncheck ./...
osv-scanner scan source --lockfile tools/requirements.txt --lockfile ws024-relay/go.mod
```

Expected: no known reachable/source dependency vulnerability gate failures. Record network/toolchain blockers explicitly.

---

### Task 4: Document, Clean, Review, and Commit F-14

**Files:**
- Modify: `docs/security-roadmap.md`
- Modify: `docs/test-plan.md`
- Modify: `docs/test-report.md`
- Modify: `docs/threat-model.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Delete locally only: `.tmp-f14/`

**Interfaces:**
- Consumes: fresh command output from Task 3.
- Produces: auditable F-14 scope, limitations, and next-task handoff.

- [x] **Step 1: Update documentation with exact evidence**

Record immutable action pins, wrapper/dependency verification, history scan, Dependabot coverage, APK/signer/manifest checks, release fail-closed behavior, vulnerability scan status, and the Linux-only race-test limitation.

- [x] **Step 2: Remove temporary artifacts**

```bash
rm -rf .tmp-f14
```

Confirm no downloaded binaries, scanner reports, APKs, or exports are staged.

- [x] **Step 3: Review the complete diff and sensitive-value scan**

```bash
git diff --check
git status --short
git diff --stat
git diff -- . ':!gradle/verification-metadata.xml'
grep -RIlE 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|PRIVATE KEY-----|PKCS12_BYTES_CANARY' \
  -- .github .gitleaks.toml scripts/ci tools/tests docs gradle/verification-metadata.xml
```

Expected: no secret material and no temporary paths.

- [x] **Step 4: Commit F-14**

```bash
git add .github .gitleaks.toml gradle/verification-metadata.xml \
  gradle/wrapper/gradle-wrapper.properties scripts/ci tools/tests/test_ci_policy.py \
  docs/security-roadmap.md docs/test-plan.md docs/test-report.md docs/threat-model.md \
  docs/handoffs/NEXT_CHAT_HANDOFF.md docs/superpowers/plans/2026-07-31-ci-supply-chain-f14.md
git commit -m "ci(security): add supply-chain and artifact gates"
```

- [x] **Step 5: Verify committed state**

```bash
git status --short --branch
git show --stat --oneline --decorate HEAD
git log -1 --format='%H%n%s'
```

Expected: clean worktree on the target branch with one F-14 commit above `06a81e7`.

## Execution Notes

- The TDD policy exposed and fixed a wrapper JAR/version mismatch in addition to
  the planned checksum/cache/pipefail defects.
- Recursive OSV scanning was rejected after it treated Gradle verification
  metadata as a runtime lockfile. The final gate explicitly scans the Python and
  Go manifests and documents the uncovered Gradle SCA boundary.
- Gitleaks native execution was blocked by Android seccomp `faccessat2`; the same
  checksum-verified ARM64 binary completed against the full repository under
  Debian/proot with zero findings.
- Local Go race remains environmentally unavailable on Android/arm64 and is not
  marked as passed; Linux CI keeps the required race job.
