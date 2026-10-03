# User-confirmed external application links

## Outcome

Continue PR338 from installed `cd6590f4a609dc776fac2606350c4430a2059355`. A public HTTPS page can request a supported custom application URI, including the ordinary Chrome `intent://` representation, without requiring a new per-site signing profile. The browser offers a separate confirmation, not an immediate Activity launch.

The generic route is deliberately separate from native AutoFirma and existing official AutoFirma handling. Public HTTPS pages still load internally, and system/file/content/script/market/phone/message URI schemes are not promoted into this route. The request must be the current visible WebView's main-frame GET with a user gesture, not an iframe, legacy callback, automatic redirect or background page.

## Minimal outgoing intent

`ExternalAppLink` parses immutable navigation data, not an executable serialized Intent. The eventual outgoing object is newly constructed with ACTION_VIEW, CATEGORY_BROWSABLE, the exact admitted custom URI and, if supplied, a syntactically valid requested package. No component, selector, flags, ClipData, arbitrary extras, cookies, certificate, HTTP credentials or parent document contents are copied. The app's own package and the official AutoFirma package cannot be selected through this generic path.

The accepted `intent:` fields are scheme, package, VIEW action, BROWSABLE category and percent-encoded HTTPS browser_fallback_url. Duplicate fields, unknown extras, explicit components, selectors and active launch flags are rejected rather than inferred or silently rewritten. Target path/query/fragment bytes remain navigation data and may contain request tokens; they are not printed in the UI or object description. The UI warns that the link itself is sent to another application. A requested package is page-supplied, not proof of that application's identity or trustworthiness.

An optional HTTPS fallback is a separate button opening an external browser. It is never copied into the app Intent or followed automatically after failure. The receiving application is chosen by Android's normal resolution; the code does not enumerate installed packages or request QUERY_ALL_PACKAGES. ActivityNotFoundException and SecurityException leave an unavailable message, not an automatic installation or a replay of the link.

## Consent, ownership and return

One two-minute consent ticket belongs to the exact WebView and navigation epoch. Replacement navigation, a different view, cancellation, backgrounding or expiry cancels the pending offer. Confirmation rechecks ownership and foreground state and consumes the ticket before attempting an external effect. A later callback cannot use an earlier confirmation to launch a different target.

The actual launch uses the existing bounded BrowserExternalReturnLease, retaining the parent or child browser for an ordinary return. App-switch return is not interpreted as authentication success, registration, signature completion or a government receipt. Process death and application-specific return protocols are not implemented by this change.

## Authorship and verification

The dialog and three UI callback tests were written by a successful GPT-6.1 Sol Max code-output task. A separate lease-authoring task timed out without accepted code; it is not counted as implementation. The integrator wrote the parser, lease, routing, MainActivity integration and coupled tests. Original agent output and integrated hashes are retained separately.

Pure lease tests verify one-shot ownership, expiry, foreground and reentrant invalidation. Android unit tests verify minimal intent reconstruction and reject injected extras/components, reserved schemes, malformed fallbacks, unsafe source contexts and non-user navigation. Android instrumentation traverses the actual MainActivity/catalog/browser route, invokes the real modern WebView callback, checks confirmation/cancellation and captures the platform startActivity call with an Instrumentation.ActivityMonitor. This prevents an actual external app or account from opening in tests. It is not proof that Cl@ve or any other third-party app completed a login.

All six repository GitHub Actions gates on the exact candidate SHA are required before the same-signer physical update. Existing profile signing/certificate paths, iframe restrictions and the application's external-return regression suites are retained.

## Primary sources consulted

- Chrome Android intent syntax and user-gesture boundaries: https://developer.chrome.com/docs/android/intents
- Android intent-redirection risk: https://developer.android.com/privacy-and-security/risks/intent-redirection
- Launching activities under package-visibility limits: https://developer.android.com/training/package-visibility/use-cases
- Instrumentation.ActivityMonitor (test-only interception): https://developer.android.com/reference/android/app/Instrumentation.ActivityMonitor

## WebAuthn finding and remaining limitations

Android WebKit has WebAuthn support levels, but browser-mode use for arbitrary relying parties requires approval by credential providers; Google Password Manager requires its own privileged-caller review. That approval is not fabricated, bypassed or requested by this change. This is why simply setting the browser-support flag would not establish usable third-party passkey login.

Primary references: https://developer.android.com/reference/androidx/webkit/WebSettingsCompat and https://developer.android.com/identity/sign-in/privileged-apps .

Custom URI navigation is not universal login support. Auto-created links without a gesture, uncommon intent extras, explicit component routing, callbacks into an exported FirmaMobile entry, arbitrary schemes/permissions and automatic web fallback are outside this slice. Missing AutoFirma formats, WebAuthn approval/integration, POST/blob download capture and process-death session recovery remain independent work. No user password, private certificate, real administrative document or live government account is used in validation.
