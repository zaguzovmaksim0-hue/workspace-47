# Browser and Probe UX Correction Design

**Date:** 2026-07-12
**Scope:** Junta browser/probe UI only
**Status:** Approved through the user's standing instruction to select the recommended option

> **Superseded privacy policy (2026-07-30, F-05):** the password-only
> `FLAG_SECURE` scope below is historical. Current `MainActivity` keeps the flag
> for Locked, Unlocking, Unlocked and all non-idle signing states, including the
> native catalog and portal WebView. The first-run no-certificate UI and isolated
> debug probe remain capturable.

## Goal

Fix three device-confirmed UX defects on the POCO F6 Pro before continuing the
Junta protocol probe:

1. keep WebView content above system navigation and the IME;
2. keep the browser address presentation to one stable-height line;
3. allow screenshots outside genuinely sensitive certificate/password UI.

This is not a browser redesign. It does not change the protocol observer,
certificate loading, signing, trusted origins, WebView hardening, cookies, or
external-intent policy.

## Chosen approach

Use one explicit inset policy with Compose and Android View adapters. Browser
chrome owns system insets exactly once, and WebView content consumes the
resulting chrome padding. Use a small address presentation component in the
existing toolbar. Replace Activity-lifetime secure-window mutation with a
scoped sensitive-content effect.

Rejected alternatives:

- Globally restoring `decorFitsSystemWindows=true` would conflict with the
  existing edge-to-edge Compose setup and make IME behavior dependent on the
  Activity currently shown.
- Fixed dp padding or DOM/CSS injection would not adapt to three-button,
  gestural, cutout, or IME insets and would modify a third-party page.

## Inset ownership

`BrowserWindowInsetsPolicy` is the shared boundary.

### Compose browser

- The Activity remains edge-to-edge.
- `TopAppBar` is the sole owner of safe top and horizontal insets through its
  `windowInsets` parameter; no outer top padding is added.
- The toolbar content height is 64 dp. The measured top slot is therefore
  `safeTop + 64 dp`, and edit mode cannot change either component.
- The bottom certificate chrome consumes safe bottom/horizontal and IME
  insets. The union uses the maximum bottom inset; navigation and IME values
  are never added together.
- `Scaffold` content padding includes the measured top and bottom chrome.
- Browser content applies and consumes that padding once, then applies only
  remaining horizontal safe-drawing insets.
- The `AndroidView` hosting `TrustedJuntaWebView` does not add its own system
  padding.

This makes the WebView viewport end above the navigation bar and above an open
keyboard, while avoiding duplicate top/bottom padding.

### Debug probe

- `ProtocolProbeActivity` explicitly uses edge-to-edge layout.
- The Activity enables edge-to-edge before `setContentView`; the root View
  receives one idempotent `WindowInsetsCompat` listener supplied by the same
  policy after attachment.
- The listener combines system bars, display cutout, and IME with a per-side
  maximum and assigns padding relative to the root's original padding.
- Re-dispatching insets replaces the previous inset contribution instead of
  accumulating it. Reinstalling the policy on the same View preserves the
  original baseline padding.

No WebView page JavaScript or DOM is modified for inset handling.

## Address presentation

The new address-bar state tracks the latest top-level URL in non-saveable
Compose memory and does not add writes to DataStore, diagnostics, or logs.
Existing WebView history restoration remains unchanged and can contain the
browser's own full URLs; sanitizing that history is a later security task and
is outside this UI-only correction.

After a successful history restore, `WebViewStateHolder` publishes the
restored current item's URL to the same non-saveable UI state so the toolbar
shows the actual restored host. The saved Bundle key and history contents are
not changed.

Normal mode:

- parse the current HTTPS URL and show the ASCII host only;
- one line, no soft wrapping, ellipsis on overflow;
- use a fixed 64 dp toolbar content height;
- expose a textual accessibility label.

Edit mode:

- starts only after the user presses the host label;
- shows the full current URL in a single-line editable field;
- supports normal Android selection and copy behavior;
- scrolls horizontally rather than wrapping;
- uses the existing `JuntaNavigationPolicy` before any submitted address is
  loaded or sent externally;
- exits back to host-only display on submit, cancel, or focus exit;
- never logs the entered URL.

`JuntaWebViewClient` gains a UI-only top-level URL callback from page lifecycle
events. Existing navigation decisions, SSL behavior, and bridge behavior are
unchanged.

The debug probe shows only the host in its collapsed header. A user-controlled
`Detalles` section may expose the current full URL in a selectable,
single-line, horizontally scrolling TextView. The full text is assigned only
while expanded, cleared and set to `GONE` on collapse, and never appended to
`SanitizedLogger` or the protocol report. QA captures details collapsed and
deletes or scans its temporary UI-tree artifact.

The sanitized observation report keeps its complete text but is capped to a
144 dp vertically scrollable debug region. This prevents a long observation
from displacing the WebView and is presentation-only: recorder content,
parsing, and branch detection are unchanged.

## Screenshot and secure-window policy

Browser, home, probe, browse-only, and sanitized diagnostic UI do not set
`FLAG_SECURE`.

`SensitiveWindowProtection` sets the flag only while the PKCS#12 password
input state is composed. Its disposal path always clears the flag. Moving to
unlocking, unlocked browser, another screen, or Activity teardown therefore
removes protection. The debug probe never enables the flag.

The policy remains available for a future confirmation screen if that screen
actually displays sensitive payload. Merely having an unlocked certificate in
memory does not make the whole Activity secure-screen-only.

## Files and boundaries

Expected production changes:

- create `app/src/main/java/dev/junta/firmamobile/ui/BrowserWindowInsets.kt`;
- create `app/src/main/java/dev/junta/firmamobile/ui/BrowserAddressBar.kt`;
- create `app/src/main/java/dev/junta/firmamobile/ui/SensitiveWindowProtection.kt`;
- modify `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`;
- modify `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`;
- modify `app/src/main/java/dev/junta/firmamobile/browser/WebViewStateHolder.kt`
  only to publish the current restored URL;
- modify `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`;
- modify `app/src/main/AndroidManifest.xml` for explicit `adjustResize`;
- modify `app/src/main/res/values/strings.xml`;
- modify debug-only
  `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt`
  and `app/src/debug/AndroidManifest.xml`;
- create `app/src/debug/res/values/ids.xml` for the probe test seam.
- create `app/src/debug/res/values/strings.xml` for probe-only labels so no
  probe resource is packaged in release.

Expected test changes:

- extend `app/src/test/java/dev/junta/firmamobile/ui/BrowserScreenTest.kt`;
- add `app/src/test/java/dev/junta/firmamobile/ui/BrowserWindowInsetsTest.kt`;
- add
  `app/src/test/java/dev/junta/firmamobile/ui/SensitiveWindowProtectionTest.kt`;
- extend
  `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`;
- extend
  `app/src/test/java/dev/junta/firmamobile/browser/WebViewStateHolderTest.kt`;
- extend `app/src/androidTest/java/dev/junta/firmamobile/AppLaunchTest.kt`;
- extend
  `app/src/androidTest/java/dev/junta/firmamobile/CertificateSetupFlowTest.kt`;
- extend debug probe instrumentation coverage without changing observer
  assertions, including a bounds regression for long sanitized observations.

Exact paths may be reduced if one focused file cleanly owns two of the small UI
helpers. No new runtime dependency is required.

## TDD and validation

RED tests prove:

- a long URL displays only a one-line host in normal mode;
- edit mode exposes the full URL without changing toolbar height;
- injected three-button, gestural, and IME inset transitions reserve the
  expected maximum bottom space;
- repeated native inset dispatch and repeated adapter installation do not
  accumulate padding;
- browser and probe have no Activity-lifetime `FLAG_SECURE`;
- password UI can set the flag and disposal/state transition clears it;
- top-level URL updates reach browser UI without changing navigation policy.

GREEN and close gates are the focused tests, full `testDebugUnitTest`,
`compileDebugAndroidTestKotlin`, `lintDebug`, debug/release assembly, APK
signature/alignment verification, release non-debuggable verification, and
release probe-absence verification.

Device QA installs the debug APK through `/data/local/tmp`, launches only the
minimum browser/probe scenario, captures one real screenshot, checks cookie
banner clearance and address height, tests scrolling/tapping, records the
current navigation mode, and force-stops the app afterward.

## Security invariants

- TLS, Safe Browsing, origin rules, bridge restrictions, and WebView settings
  are unchanged.
- No `addJavascriptInterface`, mixed content, universal file access, external
  intent expansion, or release WebView debugging is introduced.
- No cookie, URL query/fragment, PKCS#12 data, password, key, or signing payload
  is logged or additionally persisted by the new UI.
- The probe Activity, listener, recorder, and debug-only classes remain absent
  from release. Passive compatibility-shim checks and the intentional
  loopback WebSocket blocker remain common code until a separate hardening
  task splits the diagnostic overlay.
- WebView state/history capture remains unchanged.

## Completion boundary

This correction is complete only after its dedicated Git commit and one real
POCO F6 Pro screenshot demonstrate that the lower page controls sit above the
system navigation bar and the address chrome remains one line. Capturing a
screenshot does not complete the wider Junta signing goal; runtime protocol
observation resumes afterward.
