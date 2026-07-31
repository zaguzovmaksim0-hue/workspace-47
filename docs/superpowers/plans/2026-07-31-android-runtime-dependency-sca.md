# Android Runtime Dependency SCA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a fail-closed, source-controlled Android runtime dependency lock and scan those exact resolved Maven components with pinned OSV-Scanner.

**Architecture:** The `:app` project activates strict Gradle locking only for Debug, QA, and Release runtime classpaths. A dedicated verification task resolves those three configurations, `app/gradle.lockfile` records their exact direct/transitive versions, and the scheduled security workflow verifies the lock before explicitly scanning it with OSV.

**Tech Stack:** Gradle 9.4.1 Kotlin DSL, Android Gradle Plugin 9.2.1, Gradle dependency locking, Python 3 `unittest`, GitHub Actions, OSV-Scanner v2.3.8.

## Global Constraints

- Lock exactly `debugRuntimeClasspath`, `qaRuntimeClasspath`, and `releaseRuntimeClasspath`.
- Use `LockMode.STRICT`; do not call `lockAllConfigurations()`.
- Do not lock or claim test, lint, Android-test, buildscript, or plugin configurations.
- Keep Gradle dependency verification strict and unchanged.
- Keep OSV-Scanner pinned to `v2.3.8` and use explicit lockfile arguments only.
- Do not add a vulnerability allowlist, ignored dependency, recursive scan, new Gradle plugin, or runtime dependency.
- Do not change application behavior, dependency versions, portal support, signing, networking, or trust policy.
- Do not perform portal access, authentication, signing, certificate operations, APK installation, or device testing.
- Do not push.

---

### Task 1: Add failing repository-policy tests

**Files:**
- Modify: `tools/tests/test_ci_policy.py`

**Interfaces:**
- Consumes: `app/build.gradle.kts`, `.github/workflows/security.yml`, and the future `app/gradle.lockfile`.
- Produces: policy tests that enforce runtime-only locking and explicit OSV coverage.

- [x] **Step 1: Add paths and approved configuration constants**

Add:

```python
APP_BUILD = ROOT / "app" / "build.gradle.kts"
APP_RUNTIME_LOCK = ROOT / "app" / "gradle.lockfile"
APP_RUNTIME_CONFIGURATIONS = {
    "debugRuntimeClasspath",
    "qaRuntimeClasspath",
    "releaseRuntimeClasspath",
}
```

Include `APP_BUILD` and `APP_RUNTIME_LOCK` in `test_required_ci_files_exist`.

- [x] **Step 2: Add the strict/scoped build policy test**

Add a test that reads `APP_BUILD` and requires:

```python
self.assertIn("LockMode.STRICT", source)
self.assertIn("verifyRuntimeDependencyLocks", source)
self.assertNotIn("lockAllConfigurations()", source)
for name in APP_RUNTIME_CONFIGURATIONS:
    self.assertIn(f'"{name}"', source)
```

Extract the literal `runtimeDependencyLockConfigurations = setOf(...)` block and assert its quoted values equal `APP_RUNTIME_CONFIGURATIONS` exactly.

- [x] **Step 3: Add the lockfile structure test**

Parse non-comment, nonblank rows from `app/gradle.lockfile`. Require:

```python
row = re.compile(
    r"^[^:=\s]+:[^:=\s]+:[^=\s]+="
    r"(?:debugRuntimeClasspath|qaRuntimeClasspath|releaseRuntimeClasspath)"
    r"(?:,(?:debugRuntimeClasspath|qaRuntimeClasspath|releaseRuntimeClasspath))*$"
)
```

Assert rows are nonempty, sorted, unique, all match the pattern, every configuration token is approved, and no dependency version contains `+`, `SNAPSHOT`, `latest.`, `[` or `(`.

- [x] **Step 4: Extend the security workflow policy test**

Require all of:

```python
"./gradlew :app:verifyRuntimeDependencyLocks --no-daemon"
"--lockfile app/gradle.lockfile"
"--lockfile tools/requirements.txt"
"--lockfile ws024-relay/go.mod"
```

Assert the Gradle verification command appears before OSV installation/scanning and retain the prohibition on `osv-scanner scan source -r .`.

- [x] **Step 5: Run the focused test and verify RED**

Run:

```bash
python3 -m unittest \
  tools.tests.test_ci_policy.CiPolicyTest.test_required_ci_files_exist \
  tools.tests.test_ci_policy.CiPolicyTest.test_android_runtime_dependency_lock_is_strict_and_scoped \
  tools.tests.test_ci_policy.CiPolicyTest.test_android_runtime_lockfile_is_canonical \
  tools.tests.test_ci_policy.CiPolicyTest.test_security_workflow_scans_history_and_dependencies_with_pinned_tools \
  -v
```

Expected: FAIL because `app/gradle.lockfile`, strict runtime locking, the resolver task, and Android OSV workflow input do not exist.

### Task 2: Activate strict runtime locking and generate lock state

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/gradle.lockfile`

**Interfaces:**
- Produces: `runtimeDependencyLockConfigurations: Set<String>` and Gradle task `verifyRuntimeDependencyLocks`.

- [x] **Step 1: Add the Gradle lock mode import and exact configuration set**

At the top of `app/build.gradle.kts`, add:

```kotlin
import org.gradle.api.artifacts.dsl.LockMode
```

After plugins, add:

```kotlin
val runtimeDependencyLockConfigurations = setOf(
    "debugRuntimeClasspath",
    "qaRuntimeClasspath",
    "releaseRuntimeClasspath",
)
```

- [x] **Step 2: Activate strict locking only for the approved configurations**

Add:

```kotlin
dependencyLocking {
    lockMode.set(LockMode.STRICT)
}

configurations.configureEach {
    if (name in runtimeDependencyLockConfigurations) {
        resolutionStrategy.activateDependencyLocking()
    }
}
```

Do not use `lockAllConfigurations()` or ignored dependencies.

- [x] **Step 3: Add the deterministic resolution task**

Add:

```kotlin
val verifyRuntimeDependencyLocks by tasks.registering {
    group = "verification"
    description = "Verifies the exact locked Android runtime dependency graphs."
    notCompatibleWithConfigurationCache("Resolves selected runtime configurations at execution time")

    doLast {
        runtimeDependencyLockConfigurations.sorted().forEach { configurationName ->
            val configuration = configurations.findByName(configurationName)
                ?: throw GradleException("Missing runtime configuration: $configurationName")
            check(configuration.isCanBeResolved) {
                "Runtime configuration is not resolvable: $configurationName"
            }
            configuration.incoming.artifactView { }.files.files.size
        }
    }
}
```

- [x] **Step 4: Generate the lockfile from the exact task**

Run:

```bash
./gradlew :app:verifyRuntimeDependencyLocks --write-locks --no-daemon --console=plain
```

Expected: PASS and create `app/gradle.lockfile` containing only the three approved runtime configurations.

- [x] **Step 5: Inspect lock scope before proceeding**

Run a parser that prints dependency-row count and distinct configuration names. Expected configuration set:

```text
{"debugRuntimeClasspath", "qaRuntimeClasspath", "releaseRuntimeClasspath"}
```

Reject the result if any `test`, `androidTest`, `lint`, `classpath`, or plugin configuration appears.

### Task 3: Extend the scheduled vulnerability workflow

**Files:**
- Modify: `.github/workflows/security.yml`
- Modify: `tools/tests/test_ci_policy.py` only if the workflow representation requires an exact, non-weakened parser adjustment.

**Interfaces:**
- Consumes: `:app:verifyRuntimeDependencyLocks` and `app/gradle.lockfile`.
- Produces: scheduled/PR/push OSV coverage for exact Android runtime dependencies.

- [x] **Step 1: Add Java and Gradle setup to the dependency job**

Before Go setup, add pinned existing actions already approved by policy:

```yaml
      - name: Set up Java
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: "17"
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0
        with:
          cache-read-only: true
```

- [x] **Step 2: Install the Android API 36 toolchain fail-closed**

Add the same bounded license/toolchain installation used by CI:

```yaml
      - name: Install Android API 36 toolchain
        shell: bash
        run: |
          set -euo pipefail
          set +o pipefail
          yes | sdkmanager --licenses >/dev/null
          set -o pipefail
          sdkmanager "platforms;android-36" "build-tools;36.0.0"
```

- [x] **Step 3: Verify runtime lock state before OSV installation**

Add:

```yaml
      - name: Verify Android runtime dependency lock
        run: ./gradlew :app:verifyRuntimeDependencyLocks --no-daemon
```

This step must precede `Install pinned OSV-Scanner`.

- [x] **Step 4: Add the Android lockfile to the explicit scan**

Change the scan command to:

```yaml
      - name: Scan explicit source dependency locks
        run: >-
          osv-scanner scan source
          --lockfile app/gradle.lockfile
          --lockfile tools/requirements.txt
          --lockfile ws024-relay/go.mod
```

Keep OSV pinned to `v2.3.8`; do not add recursive scanning or an ignore file.

### Task 4: Verify TDD GREEN and hostile lock behavior

**Files:**
- Modify only if failures are directly caused by this task.

- [x] **Step 1: Run focused policy tests and verify GREEN**

Run the Task 1 focused command. Expected: all four tests pass.

- [x] **Step 2: Run the complete Python suite**

```bash
python3 -m unittest discover -s tools/tests -p 'test_*.py' -v
```

Expected: all tests pass except the existing hardlink environmental skip when unavailable.

- [x] **Step 3: Verify the committed lock resolves unchanged**

```bash
./gradlew :app:verifyRuntimeDependencyLocks --no-daemon --console=plain
```

Expected: PASS without modifying `app/gradle.lockfile`.

- [x] **Step 4: Prove a stale lock fails closed and restore it automatically**

Run:

```bash
backup=$(mktemp)
cp app/gradle.lockfile "$backup"
trap 'cp "$backup" app/gradle.lockfile; rm -f "$backup"' EXIT
python3 - <<'PY'
from pathlib import Path
path = Path("app/gradle.lockfile")
lines = path.read_text().splitlines()
for index, line in enumerate(lines):
    if line and not line.startswith("#"):
        left, right = line.split("=", 1)
        group, name, _version = left.split(":", 2)
        lines[index] = f"{group}:{name}:0.0.0-stale-lock={right}"
        break
else:
    raise SystemExit("no dependency row to mutate")
path.write_text("\n".join(lines) + "\n")
PY
if ./gradlew :app:verifyRuntimeDependencyLocks --no-daemon --console=plain; then
  echo "stale runtime lock unexpectedly passed" >&2
  exit 1
fi
```

After the shell exits, verify `git diff -- app/gradle.lockfile` is empty.

- [x] **Step 5: Verify and run pinned OSV-Scanner locally**

The Termux `go install` attempt was interrupted by MCP restart, so use the
official release binary and publisher checksum instead. Download
`osv-scanner_linux_arm64` and `osv-scanner_SHA256SUMS` from release `v2.3.8`,
verify the exact checksum, and run the same binary in `ws024-gate-debian` proot
because native Android seccomp blocks `faccessat2` before scanner startup.

Run only explicit inputs:

```bash
osv-scanner scan source \
  --lockfile app/gradle.lockfile \
  --lockfile tools/requirements.txt \
  --lockfile ws024-relay/go.mod
```

Expected: 140 Android packages, one Python package and one Go package; exit 0
with `No issues found`. Record native Termux as blocked, not passed.


### Task 4A: Add a fail-closed lock maintenance path discovered during execution

**Files:**
- Create: `scripts/ci/update-android-runtime-lock.sh`
- Modify: `tools/tests/test_ci_policy.py`

**Interfaces:**
- Consumes: Gradle `--write-locks` output.
- Produces: reproducible `app/gradle.lockfile` without a committed settings lock.

- [x] **Step 1: Treat generated settings lock state as a new RED**

The first exact `--write-locks` run generated
`settings-gradle.lockfile` with `empty=incomingCatalogForLibs0`. Add policy tests
requiring a dedicated updater and prohibiting root lockfiles.

- [x] **Step 2: Implement exact sentinel validation and cleanup**

The updater refuses pre-existing settings lock state, regenerates the runtime
lock, compares the settings file byte-for-byte with the reviewed canonical
sentinel, removes it only after a match, preserves unexpected content for
inspection, rejects any other root lock, and runs the canonical policy test.

- [x] **Step 3: Prove reproducibility**

Run the updater over the current lock and require the before/after SHA-256 to be
identical. Final lock SHA-256:
`286bcc684775520851aa5de6a4bb01fa172a72ca87dae2dc73e671fc76afa64d`.

### Task 5: Full regression, documentation, and local commit

**Files:**
- Modify: `docs/security-roadmap.md`
- Modify: `docs/test-plan.md`
- Modify: `docs/test-report.md`
- Modify: `docs/threat-model.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Modify: `docs/superpowers/plans/2026-07-31-android-runtime-dependency-sca.md`

- [x] **Step 1: Run the full Android and toolchain gate**

```bash
./gradlew \
  :app:verifyRuntimeDependencyLocks \
  verifyResolvedCoreVersion \
  verifyPortableAapt2Configuration \
  :app:testDebugUnitTest \
  :app:testQaUnitTest \
  :app:lintDebug \
  :app:lintQa \
  :app:assembleDebug \
  :app:assembleQa \
  :app:assembleQaAndroidTest \
  --no-daemon --console=plain
```

- [x] **Step 2: Run artifact, release, Python, and Go gates**

```bash
scripts/ci/verify-android-artifacts.sh
scripts/ci/verify-release-fail-closed.sh
python3 -m unittest discover -s tools/tests -p 'test_*.py' -v
(
  cd ws024-relay
  go test ./... -count=1
  go vet ./...
  go build ./cmd/ws024-relay
  rm -f ws024-relay
)
```

- [x] **Step 3: Record exact scope and results**

Document:

- runtime configurations and lockfile row count;
- strict-lock successful verification and hostile stale-lock rejection;
- OSV exact command/result or exact local limitation;
- distinction between version locking, artifact checksum verification, and OSV database coverage;
- Debug/QA test counts, lint/build/artifact/release/Python/Go results, APK hashes;
- no dependency version, application runtime, portal, device, certificate, or signing operation changed.

- [x] **Step 4: Mark completed plan checkboxes and review the final diff**

Run:

```bash
git diff --check
git status --short
git diff --stat
git diff
```

Confirm:

- `app/gradle.lockfile` contains only the three approved configurations;
- no verification metadata checksum disappeared;
- no new plugin/runtime dependency, credential, token, private path, vulnerability ignore, or recursive scan exists;
- no generated APK/binary is tracked.

- [x] **Step 5: Create one local implementation commit**

```bash
git add .github app tools docs
git commit -m "ci(security): scan locked Android runtime dependencies"
```

Do not push.


## Completion note — 2026-07-31

All policy, strict-lock, hostile-lock, updater, pinned OSV, Android, artifact,
release, Python, Go, documentation and staged-review steps completed. A first
hostile mutation exposed that `resolutionResult` alone was not a sufficient
lock gate; the final task materializes artifact views and rejects the same stale
version. No dependency version, application runtime, portal scope, device state,
certificate or signature operation changed. No push was performed.
