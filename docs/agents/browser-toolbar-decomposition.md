# Browser toolbar decomposition

## Boundary and status

This is a narrow static-cleanup implementation, not a review, audit, or self-review.
The accepted base is `54bf22e51225622ba4b9252cd781f8168958b8b3` (#396 merged).
The isolated branch is `refactor/browser-toolbar-decomposition-20261019`, in
`~/.cache/w47-browser-toolbar-decomposition-20261019`.

Tracker context supplied by the operator: #374 has historically been CLOSED since
`2026-10-18T19:04:11Z`. This task does not change its state and does not establish
whole-plan completion. The static-cleanup roadmap remains **incomplete**.
Publication and integration belong to the parent after exact-head canonical gates.
Other branches' certificate-helper and Location baseline removals are not part of
this candidate.

## Presentation-only change

`IndustrialBrowserTopBar` keeps its exact API, parameter order, defaults, `remember`,
`menuExpanded`, overflow anchor, and all overflow callback wrappers. Each menu
callback still closes the menu before invoking the action. Nullable optional
callbacks still use nullable `let` wrappers, so absent actions remain absent menu
items rather than enabled no-ops. Navigation callbacks are forwarded unchanged;
there is no new action model and no action is performed during composition.

Two adjacent private, lowerCamel, stateless composables own presentation:

- `browserToolbarSurface`: the existing Column modifier chain, Surface, content
  slot, then the 2 dp teal and 1 dp translucent-ink separators in their original
  order. Caller modifier and window insets still apply to the same Column.
- `browserToolbarControls`: the existing Row, back button, weighted host Box/Text,
  home button, reload button, and overflow slot. `weight(1f)` is still created in
  the actual Row scope and applied to the host Box before its horizontal padding.
  Neither content slot introduces a layout node.

Surface height remains `BrowserIndustrialToolbarHeight - 3.dp`; the two separators
retain the original total toolbar height. Colors, padding, typography, ellipsis,
accessibility descriptions, icon sizes, resources, and test tags are unchanged.
The helpers have no state, effects, parsing, logging, or browser operations.

`BrowserServiceIdentity`, `BrowserOverflowMenu`, `BrowserMenuItem`,
`BrowserChromeIconButton`, all other functions, imports, and constants in the file
remain byte-identical to the accepted base. No caller, resource, locale,
navigation, URL parsing, browser-security, certificate, signing, cache, clearing,
container, activity, application, workflow, or dependency change is included.

The only baseline edit removes the existing toolbar `LongMethod` ID after an
unbaselined exact-file run proves that the diagnostic is gone. No baseline was
regenerated, no finding was added, and no suppression or threshold was changed.

## Dedicated tests

`tools/tests/test_browser_toolbar_decomposition.py` contains 11 source contracts:

- Balanced exact-function boundaries, including nested callbacks, comments,
  literals, and neighboring functions; not a whole-file substring search.
- Exact declaration/API/defaults/parameter order.
- Owner-only menu state, open/dismiss behavior, close-before-action order,
  exactly-once callback wiring, and nullable optional wrappers.
- Original layout/modifier order, Row/Box weight semantics, separators, visual
  constants, string resources, accessibility, and tags.
- Private/stateless/bounded helpers, plus byte-pinned prefix and suffix outside
  the authorized seam.
- Only the toolbar LongMethod baseline ID is removed; the other IDs for this
  Kotlin file remain unchanged. This persistent test does not freeze unrelated
  files' baseline IDs, allowing the parent's independent baseline integration.

`IndustrialBrowserTopBarDecompositionTest.kt` contains 11 dedicated synthetic
Compose/Robolectric tests for host/accessibility/navigation, height/insets, long
host geometry, menu order, all required and optional callbacks, null combinations,
dismiss/reopen, and remembered state during host recomposition. A throwing
synthetic callback test invokes the public semantics action and checks that the
menu has already been closed; closing after the callback would never run when it
throws. No real browser, certificate, clearing, or external action is performed.

These Android tests are **authored only: not compiled or executed locally**. No
Android green result is claimed. They require the parent's canonical exact-head
Android CI, including the existing chrome tests and applicable variant gates.

## Observed focused local verification

- The initial owner-boundary test was observed RED on the accepted source
  (`97 > 60` conservative nonblank physical body lines), then GREEN after
  decomposition. The owner now has 46 such lines; the two helpers have 28 and 37.
  These are source-test counts, not detekt's SLOC metric.
- An initial three-helper shape removed LongMethod but introduced
  `TooManyFunctions` at 11 functions. It was rejected, not baselined. The final
  two-helper shape has ten functions in this file and no new finding. The
  corresponding two-helper source tests were observed RED then GREEN.
- The baseline-removal contract was observed RED before the deletion, then all
  11 source contracts were GREEN. Fifteen in-memory mutation probes were detected
  for API/defaults/order, callback order/duplication, null handling, state,
  presentation, and outside-scope bytes. The probes did not modify production
  files on disk.
- `python3 -m unittest tools.tests.test_browser_toolbar_decomposition tools.tests.test_detekt_policy -v`:
  **16 tests passed** (11 dedicated + 5 existing policy tests), with bytecode writes
  disabled.
- Cached standalone detekt **1.23.8**, checksum checked before each run:
  `2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`.
  Both raw runs used exactly
  `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`, the
  default configuration, `--build-upon-default-config`, `--max-issues 0`, and XML.
  Before: exit 2, twelve findings, including toolbar `LongMethod (99 > 60)`.
  After: exit 2, eleven unchanged legacy findings; only that LongMethod is gone.
  The remaining findings are two LongParameterList, eight FunctionNaming, and one
  MaxLineLength; they are not represented as newly clean code.
- The reduced-baseline exact-file run exited **0**, with **zero reported findings**.
  The baseline changed from **1845 to 1844 IDs**. A separate full-byte comparison
  against the accepted baseline verifies that removing exactly the one original
  ID line reproduces the new XML; all other IDs and remaining bytes are identical.
- `git diff --check` passed during the local loop. The final commit gate repeats
  this check, verifies the exact intended path set, and scans a read-only snapshot
  of just those paths with cached Gitleaks 8.30.1 plus a synthetic detector canary.
  Its result and input hashes are recorded separately, not substituted for the
  canonical Git-history secret scan.

The reduced baseline SHA-256 before independent parent integration is
`cba56809bef492f082fff8aba1a1218f1eb603c5d8f48028b8cbec763f4f91ae`.
Raw XML, exit files, input checksums, RED/GREEN logs, mutation script/log, and the
full baseline-delta assertion are retained locally under
`~/.cache/w47-browser-toolbar-evidence-20261019/` (`before.xml`, `after.xml`,
`scoped.xml`, `source-contract.*`, `local-focused-tests.*`,
`baseline-delta.verified`). The rejected intermediate diagnostic is retained as
`attempt1-after.xml`, not presented as final green evidence.

## Final local commit scope and limits

Exactly these five paths belong to this candidate:

1. `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`
2. `config/detekt/baseline.xml`
3. `tools/tests/test_browser_toolbar_decomposition.py`
4. `app/src/test/java/dev/junta/firmamobile/ui/IndustrialBrowserTopBarDecompositionTest.kt`
5. `docs/agents/browser-toolbar-decomposition.md`

Only cached tools/dependencies and direct Chipula filesystem/Termux execution are
used. No Android Gradle/bootstrap, emulator/device/E2E, portal, real certificate,
permanent key, APK, signing, installation, Codex runtime/CLI, or subagent is run.
No review step, push, PR, GitHub mutation, refresh, rebase, or reset is performed;
existing worktrees are not modified.

Source contracts and scoped detekt are supporting implementation evidence, not
Kotlin/Compose compilation, Android execution, broad security acceptance, or
whole-roadmap completion. The parent must obtain applicable canonical results on
the exact integrated head before publication/integration. The roadmap remains
**incomplete**, regardless of the historical closed tracker state.
