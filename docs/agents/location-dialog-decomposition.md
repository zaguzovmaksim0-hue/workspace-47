# Location result dialog decomposition (#374)

Base: `8827cbd7f236cc89714165b97c8d6e8f457afe12` (the shared PR396 draft candidate, including the earlier legal-dialog extraction).
Branch: `refactor/location-dialog-decomposition-20261019`.
Worktree: `~/.cache/w47-location-dialog-decomposition-20261019`.

## Implementation boundary

The tracked file is `app/src/main/java/dev/junta/firmamobile/catalog/PortalCatalogScreen.kt`, not `ui/PortalCatalogScreen.kt`.

- Keep the private `@Composable LocationResultDialog` signature and the IDLE/LOADING visibility gate.
- Extract four adjacent private lowerCamel helpers: `locationDialogTitle`, `locationDialogCopy`, `locationDialogConfirmButton`, and `locationDialogDismissButton`.
- The two exhaustive resource mappings return null for IDLE/LOADING. Every error keeps its original title and copy resource IDs. The dialog retains its existing early returns and AlertDialog configuration.
- Keep the original Button/OutlinedButton branches, callback references, labels, shapes, default enabled state, slot order, colors and implicit Material accessibility semantics. No new modifiers or semantics are introduced.
- Helpers own no remembered state and launch no effects. The caller still owns dismissal, the region-picker flag, and the dismiss-before-settings/manual callback sequence.
- An automated byte comparison verifies that the source before the leaf and after the new helpers is unchanged. No permissions/settings launching, location detection, queries, network, origins, security boundaries, other screen functions, resources, locales, manifest, dependencies or workflows are changed.

## Focused local evidence — 2026-10-19

Only cached standalone tooling was used through Chipula MCP. The detekt **1.23.8** JAR was verified against SHA-256:
`2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`.

Input scope for all detekt runs: only `app/src/main/java/dev/junta/firmamobile/catalog/PortalCatalogScreen.kt`.
Arguments: `--build-upon-default-config --base-path "$PWD" --max-issues 0 --report xml:<report-path>`; after-runs used `java -Xmx512m`. The final scoped run additionally used `--baseline config/detekt/baseline.xml`. No baseline generation was performed.

| Finding on LocationResultDialog | Before, no baseline | After, no baseline |
| --- | --- | --- |
| CyclomaticComplexMethod | complexity 22; threshold 15 | absent |
| LongMethod | length 63; threshold 60 | absent |
| FunctionNaming | existing finding | unchanged |
| ReturnCount | existing 3-return finding | unchanged |

Raw XML findings across the scoped file: **22 -> 20**. Both raw commands exit **2** because unrelated baseline debt remains. The existing file-level TooManyFunctions diagnostic changes its count from **11 to 15** due to the four extracted helpers; its baseline ID is untouched. An automated comparison explicitly accounts for that metric change and confirms no additional raw findings, no other message changes, and no complexity/length findings on the new helpers.

Only the two confirmed-obsolete CyclomaticComplexMethod/LongMethod IDs for `LocationResultDialog` were deleted, as literal lines. Baseline ID count: **1845 -> 1843**; additions: **0**. All other suppression lines are byte-for-byte unchanged. In particular, the earlier two LegalInformationDialog method removals remain absent. The reduced-baseline scoped run exits **0**, with **zero XML findings**.

### RED/GREEN tests

`LocationResultDialogStructureTest` was added before production changes, compiled using standalone cached `kotlinc` and JUnit **4.13.2**, then executed against the untouched base:

- RED: **4 tests, 4 expected failures** (missing extraction helpers / original monolithic body), exit **1**.
- GREEN after extraction: **4 tests passed**, exit **0**, with the test source unchanged since RED.
- The same compiled tests also pass from the `app/` working directory.
- `git diff --check` passes.

These are **source-only extraction guards**, including exact resource mapping, callback wiring, statelessness, visibility gate and presentation structure. They do not compile or execute production Android/Compose code and are not UI behavior acceptance.

### Retained machine evidence

Local artifacts are outside the worktree at `~/.cache/w47-location-dialog-decomposition-20261019-evidence/`:

- `detekt-before.xml`, `detekt-after.xml`, `detekt-after-baseline.xml`, their logs and exit-code files;
- checksum logs, the exact base source/baseline snapshots, and the unchanged RED test snapshot;
- `source-tests-red.log`, `source-tests-green.log`, `source-tests-green-app-cwd.log`;
- `verify_xml_and_prune.py`, `xml-proof.log`, and `verification-summary.json` (automated XML, byte-scope and exact baseline-delta assertions).

The pruning script was run against the original baseline snapshot before deleting the two lines; it is intentionally not an idempotent command to rerun against the already-reduced baseline.

## Integration handoff / not executed

Five new `LocationResultDialogTest` Compose/Robolectric tests are prepared for CI through the existing PortalCatalogScreen entry point: hidden states; all six error titles/copies and enabled button roles; dismiss-before-settings callbacks; manual selection from either slot and the region picker; and close-only dismissal for ordinary failures. They use synthetic empty catalog state and callback counters, not portals or location services.

These Compose/Robolectric tests were **not compiled or run locally**. Android/Compose compilation, runtime semantics, system back/outside dismissal, and broad integration acceptance remain unverified here. The exact PR-head GitHub Actions checks belong to the parent integrator; no CI pass is claimed by this handoff.

No Codex execution, subagents, review/audit/self-review stages, local Gradle/Android/bootstrap/downloads, devices, emulators, E2E, real portals, certificates/keys/APKs, push, PR, merge or CI mutation was performed. Other worktrees and the parallel AppRoot offline-verification / Application/MainActivity AppContainer areas were not modified. This implements only this leaf cleanup and does not close #374.
