# Android Runtime Dependency SCA Design

## Scope

The current repository verifies Gradle artifact integrity with
`gradle/verification-metadata.xml` and scans explicit Python and Go manifests
with OSV-Scanner. It does not yet maintain a source-controlled, runtime-scoped
record of the exact Android dependency graph that is installed in Debug, QA, or
Release APKs.

This task adds a fail-closed Android runtime software-composition-analysis gate.
It does not change application behavior, portal support, signing logic, network
policy, or dependency versions.

## Product outcome

A dependency change must be explicit and reviewable:

1. Gradle resolves the same exact direct and transitive runtime module versions
   recorded in source control;
2. CI fails when runtime lock state is missing or stale;
3. OSV-Scanner checks the runtime lockfile for known vulnerabilities;
4. buildscript, lint, unit-test, Android-test, and plugin dependencies remain
   outside the runtime SCA claim.

The gate therefore answers the narrow product question: “Which external Maven
components can be packaged into an installable application variant, and are
those exact versions known vulnerable?”

## Chosen design

### Runtime-scoped Gradle locking

Activate dependency locking only for these resolvable configurations in the
`:app` project:

- `debugRuntimeClasspath`;
- `qaRuntimeClasspath`;
- `releaseRuntimeClasspath`.

Set `LockMode.STRICT`. The committed lock state is `app/gradle.lockfile`.
Strict mode makes a missing lock state or a graph mismatch a resolution failure.

Do not call `lockAllConfigurations()`. The SCA claim is deliberately limited to
installable application runtime graphs and must not silently expand to test,
lint, buildscript, compiler-plugin, or Android-test dependencies.

### Deterministic resolution task

Add `:app:verifyRuntimeDependencyLocks` in `app/build.gradle.kts`.

The task resolves exactly the three approved runtime configurations. Normal
execution verifies the committed lock state. Lock generation or review uses the
same task with `--write-locks`:

```bash
./gradlew :app:verifyRuntimeDependencyLocks --write-locks
```

The task fails closed when:

- one of the three named configurations is missing or not resolvable;
- strict lock state is absent;
- a resolved module or version differs from the lockfile;
- a dependency is added or removed without regenerating and reviewing the lock.

### OSV security workflow

Extend the scheduled security workflow with the Android toolchain needed to
configure and resolve the app graph. Run the Gradle verification task before
OSV installation and scanning.

OSV-Scanner remains pinned to `v2.3.8` and receives only explicit inputs:

```bash
osv-scanner scan source \
  --lockfile app/gradle.lockfile \
  --lockfile tools/requirements.txt \
  --lockfile ws024-relay/go.mod
```

Do not reintroduce recursive source scanning. Do not scan
`gradle/verification-metadata.xml` as the Android runtime source because it also
contains build and test tooling and therefore cannot support a runtime-only
claim.

### Repository policy tests

Extend `tools/tests/test_ci_policy.py` to verify:

- `app/gradle.lockfile` exists;
- only the three approved runtime configuration names appear after `=`;
- every dependency row has canonical `group:name:version=configurations` form;
- the lockfile is sorted and contains no changing/dynamic version marker;
- the app build uses `LockMode.STRICT` and does not use
  `lockAllConfigurations()`;
- `verifyRuntimeDependencyLocks` names exactly the three approved
  configurations;
- the security workflow verifies Gradle lock state before scanning;
- OSV scans `app/gradle.lockfile` explicitly and recursive scanning remains
  prohibited.

## Security invariants

- Gradle dependency verification remains enabled and strict; lock state does not
  replace artifact checksum verification.
- Locking records versions, while verification metadata authenticates downloaded
  metadata and artifacts. Neither mechanism is represented as the other.
- No wildcard trust, ignored dependency, lenient lock mode, recursive OSV scope,
  or vulnerability allowlist is added.
- A vulnerability result remains a failing security workflow result; this task
  adds no blanket suppression policy.
- No repository credential, private registry, token, certificate, or signing
  material is introduced.
- No APK installation, portal navigation, authentication, signing, or physical
  device action is performed.

## Alternatives rejected

1. **Scan `gradle/verification-metadata.xml` directly.** OSV supports the format,
   but the file includes build, test, plugin, and runtime artifacts. It would be
   useful for a broad supply-chain inventory, not for the explicit runtime claim
   required here.
2. **Apply the CycloneDX Gradle plugin.** It can produce resolved dependency
   SBOMs, but adds a new build plugin and its dependency graph. Native Gradle
   locking plus OSV’s supported lockfile format provides the required result with
   less supply-chain and maintenance surface.
3. **Generate a custom OSV JSON manifest.** This duplicates parser and ecosystem
   mapping logic already supplied by Gradle and OSV.
4. **Lock every Gradle configuration.** This creates large noisy updates and
   conflates production runtime exposure with test/build tooling.

## Testing and verification

Use TDD:

1. add policy tests that fail because runtime lock state and workflow coverage do
   not exist;
2. activate strict runtime locking and add the deterministic resolver task;
3. generate `app/gradle.lockfile` from the three exact configurations;
4. verify a stale/mutated temporary lock copy is rejected without modifying the
   committed lock;
5. install/run pinned OSV-Scanner locally when the environment permits and record
   the exact result;
6. run all Python policy tests, Debug/QA JVM suites, lint, APK assemblies,
   artifact verification, release fail-closed, Go test/vet/build, and final
   diff/sensitive-data checks.

The final report must distinguish:

- dependency graph reproducibility;
- artifact integrity verification;
- known-vulnerability database results;
- limitations such as OSV database coverage and unavailable local Linux-only
  gates.
