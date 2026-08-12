# Browser Identity Button-Role Design

## Problem

`IndustrialBrowserTopBar` contains an internal optional `onIdentityClick` branch that
wraps the service-identity block in `Modifier.clickable`. The branch exposed an
`OnClick` action but no explicit role.

The focused RED also tested a suspected touch-target-size defect. That hypothesis was
not reproduced: the tagged interactive semantics node measured 69 px high in the
current Robolectric configuration and reached the role assertion. The only observed
failure was the missing `(Role = 'Button')` assertion, so no layout change was justified.

A post-commit call-site audit established the exact runtime scope: production
`BrowserLayout` does not pass `onIdentityClick` and supplies `editingContent = null`;
`BrowserScreenTest.toolbarIdentityCannotOpenManualUrlEditor` pins the read-only toolbar
contract. Therefore this milestone hardens a dormant internal optional branch and does
not change the current production user path.

## Constraints

- Preserve the production read-only toolbar identity and manual-URL-editor prohibition.
- Preserve toolbar dimensions, typography, strings, colors, host/trust text and click
  behavior of any future explicitly wired internal consumer.
- Do not add `heightIn`, padding or another layout change; the size hypothesis was not
  reproduced.
- Change only the non-null `onIdentityClick` branch; the passive identity remains
  non-clickable and role-free.
- Preserve all WebView/network/TLS/Client TLS/certificate/signing/profile/release
  behavior and dependencies.
- Automated evidence covers Compose semantics only. Physical TalkBack/visual behavior
  remains a manual gate if the optional branch is ever enabled by a reviewed change.

## Considered approaches

1. **Set the role on the existing optional clickable.** Use
   `clickable(role = Role.Button, onClick = onIdentityClick)`. This is the minimum
   semantics hardening and matches the established custom-button pattern.
2. Add separate `.semantics { role = Role.Button }`. Rejected as redundant because
   `clickable` already accepts the role and owns the click action.
3. Add a minimum height or alter the toolbar. Rejected because RED measured the node at
   69 px and did not reproduce a size defect.
4. Remove the dormant hook in this milestone. Deferred to a separate audit decision:
   removing an internal API is broader than the reproduced missing-role defect and the
   current production call is already fail-closed/read-only.

## Accessibility contract

For a deliberately wired `onIdentityClick != null` internal consumer:

- the tagged identity node exposes `SemanticsActions.OnClick`;
- the same node exposes `SemanticsProperties.Role == Role.Button`;
- existing profile-name and host/trust descendant text remain merged.

For the current production/default `onIdentityClick == null` path:

- no click action or button role is exposed;
- no manual URL editor becomes reachable.

## Exact implementation shape

- `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`: focused
  failing regression for `Role.Button` plus passive-control assertions.
- `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`: add
  `Role.Button` to the existing conditional `Modifier.clickable`; no layout change.
- Evidence documents must state that the branch is dormant in current production.

No threat-model change is required because no runtime trust edge, asset, navigation
policy or sensitive-data path changed.

## Verification

RED is valid only for the missing-role assertion; its measured 69 px bounds reject the
separate size hypothesis. Focused Debug/QA, full Debug/QA JVM, lint, assemblies,
dependency/toolchain, Python, Go, APK-artifact and release-fail-closed gates verify the
change. A separate call-site audit must keep the runtime scope explicit: production
`BrowserLayout` passes no `onIdentityClick`, so this is latent API hardening rather than
a physical/current-user accessibility claim.
