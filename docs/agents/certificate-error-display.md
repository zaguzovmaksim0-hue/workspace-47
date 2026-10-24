# Certificate error display mapping

## Bounded implementation

- Base: `54bf22e51225622ba4b9252cd781f8168958b8b3` (actual `origin/main`, merge #396 when this worktree was created).
- Branch: `refactor/certificate-error-display-20261019`.
- Worktree: `~/.cache/w47-certificate-error-display-20261019`.
- #374 roadmap remains incomplete. The historical issue is closed; this work does not change its state or claim the whole roadmap is finished.
- Only `CertificateErrorCode -> CertificateUiError` display mapping is extracted. `CertificateViewModel` retains its private extension as a one-line delegate to `CertificateErrorPresentation.forCode(this)`.
- The internal presentation object owns a typed immutable table. Its initializer checks that the keys equal all `CertificateErrorCode.entries`; lookup is total, with no fallback, string conversion, raw exception/message input, or new error taxonomy.
- The operator explicitly approved the one additional production path: move the **unchanged** `CertificateUiError` declaration from `CertificateUiState.kt` to `CertificateErrorPresentation.kt`. Package/FQCN, public visibility, all values and their order are unchanged. No other byte of the remaining state source changes.
- `CertificateModels.kt` is already a pure JVM source and is compiled intact. The moved real UI enum and production table now compile intact alongside it, without Android, source slicing, stubs, or a test copy of the mapper.

No selection-error mapping, resource/localization text, constructor, gateway/session/ViewModel API, state function, lifecycle, coroutine, cache, unlock/restore, signing, auth, identity, or privacy behavior changes. Application, MainActivity, AppContainer, AppRoot, Legal, location, offline verification, and journal work are not included. The separate location change is not a dependency or part of this commit.

## Exact prechange behavior matrix

These are literal expectations taken from the old private mapping at the base SHA, not generated from production mapper output or enum names. Both runtime parameterized tests and documentation preserve every old entry.

| CertificateErrorCode | CertificateUiError |
| --- | --- |
| FILE_TOO_LARGE | FILE_TOO_LARGE |
| INVALID_PASSWORD_OR_FILE | PASSWORD_INVALID_OR_FILE |
| TOO_MANY_ENTRIES | CERTIFICATE_NOT_USABLE |
| PRIVATE_KEY_MISSING | PRIVATE_KEY_MISSING |
| MULTIPLE_PRIVATE_KEYS | MULTIPLE_PRIVATE_KEYS |
| CERTIFICATE_NOT_X509 | CERTIFICATE_NOT_X509 |
| CHAIN_TOO_LONG | CERTIFICATE_NOT_USABLE |
| UNSUPPORTED_KEY_TYPE | UNSUPPORTED_KEY_TYPE |
| CERTIFICATE_EXPIRED | CERTIFICATE_EXPIRED |
| CERTIFICATE_NOT_YET_VALID | CERTIFICATE_NOT_YET_VALID |
| KEY_USAGE_NOT_PERMITTED | KEY_USAGE_NOT_PERMITTED |
| KEY_CERTIFICATE_MISMATCH | KEY_CERTIFICATE_MISMATCH |
| NO_CERTIFICATE_SELECTED | CERTIFICATE_NOT_SELECTED |
| DOCUMENT_UNAVAILABLE | DOCUMENT_UNAVAILABLE |
| REFERENCE_STORAGE_FAILURE | STORAGE_FAILURE |

## Executed focused checks

1. **RED:** the first opaque password/file failure test was written before production changes. Standalone compilation exited 1 specifically because `CertificateErrorPresentation` did not yet exist. This was a compile-time seam RED, not a claim that the old Android ViewModel was executed or had different behavior.
2. **First GREEN:** intact pure production sources plus that test compiled and JUnit passed 1 test.
3. **Exhaustive GREEN:** intact `CertificateModels.kt`, `CertificateErrorPresentation.kt`, and `CertificateErrorPresentationTest.kt` compiled with installed Kotlin 2.4.20 using `-Werror -jvm-target 17`; compiler output was empty. JUnit 4.13.2 passed **19 tests**: 15 parameterized typed-failure rows plus completeness/no duplicates/no extras and opaque presentation checks. No raw failure details were added to any production or test interface.
4. Generated mapper and both real enum class files have JVM class major **61** (Java 17). The host runtime was OpenJDK 21.0.12; this is JVM-17-targeted compilation, not a claim of execution under JDK 17.
5. Focused Python byte guards and existing detekt policy tests passed. The guards pin all ViewModel bytes outside the exact mapping method, all remaining UiState bytes, and the complete relocated public enum declaration. They also check the retained ViewModel baseline signatures and the actual XML evidence below. These guards supplement, not replace, executable Kotlin mapping tests.
6. Whitespace: `git diff --check` passed.

### Standalone JVM reproduction (cached tools only)

Run from the worktree. These commands do not invoke Gradle or download dependencies:

```bash
set -eu
KOTLIN_HOME="$PREFIX/opt/kotlin"
JUNIT=$(find "$HOME/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2" -name 'junit-4.13.2.jar' -print -quit)
HAMCREST=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3" -name 'hamcrest-core-1.3.jar' -print -quit)
test -f "$JUNIT" && test -f "$HAMCREST"
OUT=app/build/reports/certificate-error-display/reproduced-classes
java -Xmx384m -Dkotlin.home="$KOTLIN_HOME" -Dkotlin.colors.enabled=false \
  -cp "$KOTLIN_HOME/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Werror -jvm-target 17 -classpath "$JUNIT" \
  app/src/main/java/dev/junta/firmamobile/certificate/CertificateModels.kt \
  app/src/main/java/dev/junta/firmamobile/ui/CertificateErrorPresentation.kt \
  app/src/test/java/dev/junta/firmamobile/ui/CertificateErrorPresentationTest.kt \
  -d "$OUT"
java -cp "$OUT:$JUNIT:$HAMCREST:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  org.junit.runner.JUnitCore \
  dev.junta.firmamobile.ui.CertificateErrorPresentationTest \
  dev.junta.firmamobile.ui.CertificateErrorPresentationMatrixTest
python3 -m unittest tools.tests.test_certificate_error_display tools.tests.test_detekt_policy -v
git diff --check
```

## Actual cached detekt evidence

The already-cached standalone **detekt 1.23.8** JAR was SHA-256 checked before each invocation against:

`2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`

All invocations used `--build-upon-default-config --base-path "$PWD"`. Raw diagnostic reports used `--max-issues 1000` **without a baseline** so the complexity finding could not be hidden by its existing baseline entry. This diagnostic mode is not the acceptance gate.

- Before input: original, unedited `app/src/main/java/dev/junta/firmamobile/ui/CertificateViewModel.kt` at the exact base. It reported **5 findings**, including `toUiError` cyclomatic complexity **15**.
- After input: `CertificateViewModel.kt`, `CertificateErrorPresentation.kt`, and `CertificateUiState.kt`. It reported **4 findings**, exactly the pre-existing `TooManyFunctions` and three `ReturnCount` diagnostics with unchanged messages. The target complexity finding is absent; the new production mapping/enum file and remaining UiState source have **0 raw findings**. The diagnostic was not moved to the new file.
- After acceptance input: the same three files with `--baseline config/detekt/baseline.xml --max-issues 0`. It passed, producing **0 findings**.

Actual XML outputs are copied byte-for-byte, without editing, into the focused test fixtures:

| Artifact under `tools/tests/fixtures/certificate_error_display/` | SHA-256 |
| --- | --- |
| `detekt-before.xml` | `7a030296fcd879a00df43037f5515cd40583c415b13340696f9bdd25095a2577` |
| `detekt-after.xml` | `0f23d338d2a94b09143483c6d0e068a18ba419164699cd55006bad087cd85ea5` |
| `detekt-after-baseline.xml` | `f491e274f0d208f81c3edbc7c1097c30db590a3822fcf579468ac814d9ccc62b` |

The only baseline edit removes this now-absent exact ID:

```text
CyclomaticComplexMethod:CertificateViewModel.kt$CertificateViewModel$private fun CertificateErrorCode.toUiError()
```

Baseline count: **1845 -> 1844; additions 0; removals 1**. Every other baseline byte is preserved, including the already-reduced Legal entries and all `CertificateViewModel` MagicNumber `24`, three ReturnCount, and TooManyFunctions IDs. No suppression was added. Baseline generation was not invoked.

### Scoped acceptance reproduction

```bash
set -eu
JAR="$HOME/.cache/firmamobile/detekt/1.23.8/detekt-cli-1.23.8-all.jar"
printf '%s  %s\n' 2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1 "$JAR" |
  sha256sum --check --status
SCOPE=app/src/main/java/dev/junta/firmamobile/ui
java -Xmx512m -jar "$JAR" \
  --input "$SCOPE/CertificateViewModel.kt,$SCOPE/CertificateErrorPresentation.kt,$SCOPE/CertificateUiState.kt" \
  --build-upon-default-config --base-path "$PWD" \
  --baseline config/detekt/baseline.xml --max-issues 0 \
  --report xml:app/build/reports/certificate-error-display/reproduced-after-baseline.xml
```

## Byte identity pins

All pins are SHA-256:

| Boundary | SHA-256 |
| --- | --- |
| Original complete ViewModel at base | `9bed67283cd279fa60728e93734a31b44e22e1803268d6578350b88c38fb8c5d` |
| ViewModel outside the exact mapping seam, before = after | `b86795de0426f449e8697d819b4330f3545c3ebb77829b537e7496b0f6579555` |
| Complete relocated enum declaration, before = after | `dc98c3b9354b1f4e5a7fb2a3d4e21e96071cfc74e00de2a5a528e51500e13047` |
| Entire remaining UiState source, equal to original minus enum | `5c1b393a272485e6526339d67bf022caf8910c1fa67f33b2593895baa94d4f96` |
| Original baseline | `316178bd47e5a53fdbc7bc5589d230af1970984e6c0f9fc4c3154d853ad8fc1d` |
| Baseline after exact target removal | `47daa2186281cda81c3d0b4b007ce323f9e7b395abcc396f0d1253337fc5c570` |

## Handoff limitations

This is local implementation evidence only. No broad Android unit suite, lint, Gradle, bootstrap, Android build/APK, emulator/device/E2E, portal, real certificate, or permanent key operation was run. Android integration and canonical GitHub Actions acceptance remain for the parent on its eventual exact published candidate SHA. Existing Android/Robolectric tests were not weakened or edited.

No review/self-review/reviewer session, Codex CLI/runtime/Cloud, push, PR, GitHub mutation, refresh/fetch, rebase, or reset was performed. Parent owns publication/integration. No unrelated/frozen worktree was modified.
