# Browser Identity Button-Role Design

## Problem

`IndustrialBrowserTopBar` makes the service-identity/address block clickable when address editing is available by applying `Modifier.clickable(onClick = onIdentityClick)`. The resulting merged semantics node exposes an `OnClick` action but no explicit role.

The first focused RED also tested a suspected touch-target-size defect. That hypothesis was not reproduced: the tagged interactive semantics node measured 69 px high in the current Robolectric configuration and passed the 48 dp minimum assertion. The only observed failure was the missing `(Role = 'Button')` assertion. Therefore no layout change is justified.

## Constraints

- Preserve toolbar dimensions, typography, strings, colors, host/trust text, click behavior and address-editing flow.
- Do not add `heightIn`, padding, or any other layout change; the size hypothesis was not reproduced.
- Change only the non-null `onIdentityClick` branch; the passive identity remains non-clickable and role-free.
- Preserve all WebView/network/TLS/Client TLS/certificate/signing/profile/release behavior and dependencies.
- Automated evidence covers Compose semantics only. Physical TalkBack wording/interaction and visual rendering remain manual acceptance gates.

## Considered approaches

1. **Recommended: set the role on the existing clickable modifier.** Use `clickable(role = Role.Button, onClick = onIdentityClick)`. This matches the established custom-button pattern and changes only semantics metadata.
2. Add a separate `.semantics { role = Role.Button }` before `clickable`. Rejected as redundant because `clickable` already accepts the role and owns the click action.
3. Add a minimum height or alter the toolbar. Rejected because RED measured the interactive node at 69 px and did not reproduce a size defect.

## Accessibility contract

When `onIdentityClick != null`:

- the node tagged `BROWSER_ADDRESS_LABEL_TAG` exposes `SemanticsActions.OnClick`;
- the same node exposes `SemanticsProperties.Role == Role.Button`;
- existing profile name plus host/trust descendant text remain merged and available.

When `onIdentityClick == null`:

- no click action or button role is exposed.

## Exact implementation shape

- `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`: focused failing regression for `Role.Button` plus passive-control assertions.
- `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`: add `Role.Button` to the existing conditional `Modifier.clickable`; no layout change.
- Evidence only after GREEN/full gates: `docs/autonomous/2026-08-04-audit-ledger.md`, `docs/test-report.md`, `docs/security-roadmap.md`, and `docs/handoffs/NEXT_CHAT_HANDOFF.md`.

No threat-model change is expected because no asset, trust edge, navigation decision or sensitive-data path changes.

## Verification

The focused RED is valid only for the missing-role assertion; its measured 69 px bounds disprove the separate size hypothesis. Implement the single semantics fix, run focused Debug/QA GREEN, then full Debug/QA JVM suites, lint, Debug/QA/QA-AndroidTest assemblies, dependency/toolchain gates, Python unittest discovery, Go test/vet/build, Android artifact checks and release-signing fail-closed verification. Review the complete diff, run `git diff --check`, scan changed content, remove generated artifacts, then commit and push atomically with exact remote-SHA verification.
