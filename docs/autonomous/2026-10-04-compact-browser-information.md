# Compact browser information

The operator requested moving explanatory content above the website into the
site-information menu. This is a presentation-only update layered on the
pending performance PR; it does not change WebView navigation or certificates.

- The toolbar is 56 dp with a one-line current host and existing navigation buttons.
- Overflow now has `Información del sitio` as its first item.
- That scrollable dialog retains the selected profile, navigation/trust label,
  public-browser explanation and existing Passkeys/WebAuthn status entry.
- The information dialog is read-only; close/reopen does not navigate. A new URL
  dismisses it so stale site details cannot remain visible.
- Errors, signing and certificate confirmations remain in their original flows.
- The certificate strip and inset behavior are retained. Icon touch targets remain 48 dp.
- Version 0.2.10 / code 12 supports an in-place update with the existing signer.

Verification: new Compose unit coverage exercises initial absence, overflow
access, repeated opening/closing, unchanged navigation callbacks, and URL change.
Existing toolbar/inset expectations and public-browser instrumentation fixtures
are updated for the new information-menu entry. Full checks run on the exact
PR commit in GitHub Actions. Physical UI acceptance follows the verified build.

The earlier site-loading report was subsequently attributed by the operator to
a different VPN, explicitly not WARP. R8 remains disabled in this daily build;
this UI task does not change network settings or reintroduce shrinking.
