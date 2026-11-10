# Kotlin static analysis

CI runs the standalone detekt 1.23.8 CLI against `app/src/main/java` and `app/src/release/java` (the latter is also
included in the optimized/daily variant):

```sh
bash scripts/ci/detekt.sh
```

Java 17, Bash, curl and sha256sum are required. The official release JAR is
cached under `${XDG_CACHE_HOME:-$HOME/.cache}/firmamobile/detekt/1.23.8`.
Both downloads and cached JARs must match the SHA-256 pinned in the script
before execution. A corrupt cached file fails closed; remove that file and retry.
No Gradle plugin or application dependency is added.

## Compose naming

`config/detekt/config.yml` configures only
`naming.FunctionNaming.ignoreAnnotated: ['Composable']`, following the
[detekt 1.23.8 Compose guidance](https://detekt.dev/docs/1.23.8/introduction/compose/).
Unit-returning `@Composable` functions use UpperCamelCase; Android lint's
`ComposableNaming` check remains responsible for their framework convention.
The CLI retains `--build-upon-default-config`, so ordinary functions still use
the default lowerCamelCase rule and all other rule defaults remain unchanged.
Both CI workflows use the shared script above and therefore the same config.

## Baseline policy

`config/detekt/baseline.xml` records existing findings, not fixes or approvals.
The initial baseline contains 1,847 existing findings. CI uses `--max-issues 0`:
findings not covered by the baseline fail the job. Reports are retained as the
`detekt-reports` artifact, including when analysis fails.

Do not regenerate the baseline to make a failing check green. Fix new findings.
For an explicitly reviewed rule/tool migration, generation is available locally:

```sh
bash scripts/ci/detekt.sh --create-baseline
```

Review every baseline addition in the PR. Keep manual suppressions empty and
never add machine-specific paths. To update detekt, pin a release version and
its verified SHA-256 together, inspect rule changes and run the policy tests
and CI before merging. CI never regenerates the baseline.

## Scope and limitations

This is source-level analysis with default rules and the Compose naming
configuration above, without compiler type resolution or the Android classpath.
It does not replace Kotlin compilation, Android lint, unit tests, instrumentation
or R8 startup smoke. Test sources are not included in this first gate. Legacy
findings remain technical debt:
unchanged baseline signatures can suppress a method even when it grows.
Reduce the baseline through focused fixes rather than treating it as a clean
bill of health. Tool parser compatibility must be rechecked as Kotlin evolves.
