# Legal dialog decomposition (#374)

Base: `04d67ba69cf8f722be151a2716542f26124ebf72`.
Branch: `refactor/legal-dialog-decomposition-20261019`.

## Implementation

- Keep `internal fun LegalInformationDialog(onDismiss: () -> Unit)` unchanged.
- Extract private lowerCamel helpers for title, header, document rendering, license rendering, menu buttons and synchronous asset reads.
- Keep navigation state, both `produceState` calls and their keys, document chunks, feedback snapshot and menu scroll state in their original dialog branches. Helpers own no remembered state.
- Preserve resource IDs, content order, modifiers, semantics, asset validation/size/error boundaries, back/close callbacks and the existing feedback pane/confirmation flow. Asset access stays inside `runCatching`.
- Change no resources, locales, feedback implementation, dependencies or detekt configuration.

## Focused local evidence — 2026-10-19

Cached detekt **1.23.8** JAR SHA-256 verified before execution:
`2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`.

Actual standalone CLI scope: only `app/src/main/java/dev/junta/firmamobile/ui/LegalInformationDialog.kt`, using `--build-upon-default-config --base-path "$PWD" --max-issues 0`.

| Finding on LegalInformationDialog | Before, no baseline | After, no baseline |
| --- | --- | --- |
| CyclomaticComplexMethod | complexity 19 (threshold 15) | absent |
| LongMethod | length 114 (threshold 60) | absent |
| FunctionNaming | existing finding | unchanged |

Raw runs exit 2 because other existing findings remain. The same scoped run with the reduced `config/detekt/baseline.xml` exits **0**, with zero reported findings. Delete only the two confirmed-obsolete method entries. All other baseline lines remain untouched, including FunctionNaming and the three MaxLineLength entries whose raw findings disappear through extraction/unindentation.

Standalone `kotlinc` + cached JUnit 4.13.2: **6 tests passed** — four new `LegalInformationDialogStructureTest` checks and two existing `LegalInformationTest` checks compiled against the pure declarations copied directly from the current source. The structural tests also pass from the `app/` working directory. These checks do not compile or execute Android/Compose UI.

Local logs/XML are retained outside the worktree in `~/.cache/w47-legal-dialog-decomposition-20261019-evidence/`.

## Integration boundary

Canonical CI remains the parent integration's responsibility. Android/Compose compilation and the existing `CompatibilityFeedbackPaneTest` regressions (preview privacy, explicit copy, separate confirmation, cancellation, report reset and menu/back navigation) were **not run locally**. No additional Compose test is needed for the stateless extraction; existing coverage remains unchanged.

No local Android Gradle/bootstrap/downloads, device/E2E, portal, certificate/key/APK operations, Codex, review/audit, push, PR or GitHub mutation were performed.
