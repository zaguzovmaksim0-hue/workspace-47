# Browser Manual-URL Surface Removal Design

## Problem

The current production browser is intentionally closed: `BrowserLayout` renders a
read-only profile/host identity, never wires `onIdentityClick`, sets
`editingContent = null`, and `toolbarIdentityCannotOpenManualUrlEditor` pins that user
contract. However, main-source still contains a complete unused `BrowserAddressBar`
manual URL editor (`BasicTextField`, arbitrary `onSubmit(String)`) plus dormant
`IndustrialBrowserTopBar.onIdentityClick` and `editingContent` hooks.

Exact symbol audit `job_20260806_214058_9ef9999b` found no production consumer of
`BrowserAddressBar`, `onIdentityClick`, or non-null `editingContent`. The editor-only
strings are referenced only by that dead composable. Keeping this code does not create
a current exploit path, but it leaves a dormant navigation/input capability whose
safety depends on future call sites continuing not to wire it. This is unnecessary
attack surface for a profile-bound browser whose navigation policy is deliberately not
a general address bar.

## Constraints

- Preserve current runtime behavior: the toolbar remains read-only and shows the
  selected profile plus sanitized HTTPS host/trust status.
- Preserve `BrowserAddressPresentation.hostOf`, browser toolbar/bottom/content tags and
  `BROWSER_ADDRESS_LABEL_TAG` used by current UI/tests.
- Do not broaden any URL/origin/profile allowlist or change WebView navigation,
  redirects, TLS, Client TLS, signing, certificate, cookie or release behavior.
- Remove only dead/manual-editor code and its unused resource strings/hooks.
- Do not introduce a replacement text field, arbitrary URL callback, external intent,
  or generic navigation entry point.
- G17-01 remains valid historical evidence for the optional hook while it existed; G18
  may deliberately remove that now-proven-dormant hook.

## Considered approaches

1. **Recommended: remove the dead editor and dormant extension hooks.** Delete the
   `BrowserAddressBar` composable, editor-only imports/state/tag/string resources,
   `onIdentityClick`, and `editingContent`; render `BrowserServiceIdentity` directly.
   This structurally enforces the already-observed production contract.
2. Leave dead code but add comments saying not to wire it. Rejected because comments do
   not enforce a trust-boundary invariant and preserve unnecessary input capability.
3. Wire the editor through the existing navigation validator. Rejected because it
   changes product behavior and broadens user-driven navigation beyond the approved
   profile-bound design.

## Structural contract

After G18-01:

- main browser UI source contains no `BasicTextField` manual address editor;
- `IndustrialBrowserTopBar` has no `onIdentityClick` or `editingContent` extension hook;
- production `BrowserLayout` has no editor argument to configure;
- editor-only strings `browser_address_current_description` and
  `browser_address_edit_description` are absent;
- `BrowserAddressPresentation.hostOf` remains the sole address-display helper and
  current browser host display remains unchanged;
- existing test `toolbarIdentityCannotOpenManualUrlEditor` remains green.

## Exact files

- Add RED/policy coverage: `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`.
- Remove dormant editor while preserving presentation/constants:
  `app/src/main/java/dev/junta/firmamobile/ui/BrowserAddressBar.kt`.
- Remove dormant hooks: `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`.
- Remove obsolete `editingContent = null`: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`.
- Reconcile component tests: `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`.
- Keep negative no-editor UI assertions test-local after removing the production editor tag:
  `app/src/test/java/dev/junta/firmamobile/ui/BrowserScreenTest.kt`.
- Remove editor-only resources: `app/src/main/res/values/strings.xml`.
- Evidence only after verification: audit ledger, test report, security roadmap, durable handoff.

## Verification

Observe a source-policy RED against unchanged production sources, then remove the
minimum dead surface and run focused browser/security Debug+QA tests. Run fresh full
Debug/QA JVM, lint, Debug/QA/QA-AndroidTest assemblies, dependency/toolchain gates,
Python, Go, APK artifact verification and release-signing fail-closed. Inspect exact
diff and sensitive/unsafe patterns. Physical visual/TalkBack behavior is unchanged by
contract and is not claimed as newly validated.
