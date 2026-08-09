# Codex Cloud Gradle policy

## Rule

All agent-initiated Gradle execution for workspace-47 runs in Codex Cloud. This includes focused tests, RED/GREEN tests, unit tests, lint, verification tasks, and APK/androidTest assembly.

Saved environment:

- name: `workspace-47-android`
- environment id: `6a785cdf2c8c8191ba25607f44962899`
- launcher: `$HOME/bin/w47-cloud`

The environment is already configured with Android API 36, Build Tools 36.0.0, Java, Gradle wrapper support, Robolectric Android 15/16 runtimes, and operator-selected unrestricted agent internet.

## Worker sequence

1. Work only in the worker's isolated branch/worktree.
2. Commit the candidate state.
3. Push the branch to `origin`.
4. Record `BRANCH=$(git branch --show-current)` and `SHA=$(git rev-parse HEAD)`.
5. Submit focused Gradle verification with:

   `w47-cloud gradle --branch "$BRANCH" --sha "$SHA" <tasks...>`

6. For the publication/integration Android gate use:

   `w47-cloud full --branch "$BRANCH" --sha "$SHA"`

7. Accept evidence only when Cloud reports that `git rev-parse HEAD` equals the requested SHA, dependency verification remains enabled, the requested Gradle command exits 0, and `git status --short` is clean for a read-only verification task.

## Canonical full gate

`verifyResolvedCoreVersion verifyPortableAapt2Configuration testDebugUnitTest testQaUnitTest lintDebug lintQa assembleDebug assembleQa assembleQaAndroidTest`

## Phone boundary

The normal phone-side operations are orchestration, Git, lightweight Python/Go/policy checks, Cloud submission, and Cloud status/result retrieval.

Direct local `./gradlew`, Gradle daemon startup, Kotlin compilation, lint, Android unit suites, or Android assembly are outside the automatic agent workflow. If Codex Cloud is unavailable, stop the Gradle gate and report the blocker. A local Gradle fallback requires explicit operator authorization for that specific incident; it is never automatic.
