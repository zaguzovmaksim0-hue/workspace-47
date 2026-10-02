# Genuine child browser windows

## Scope

Continue PR338 from installed `6f436bab9e1b63e4c28c1f9f9be0a74dd3fba68d`. The previous WebChromeClient rejected every `onCreateWindow`; this slice supports one user-gesture-created child window in the same Activity. It is a genuine Chromium window completed through `WebView.WebViewTransport`, not a guessed target URL opened with `loadUrl` or a replay of a submitted form.

The parent WebView remains allocated with its current DOM and form values. A visible child header returns to that parent, and a script's `window.close` closes only its own child. Standard browser rules determine `window.opener`, same-origin access, postMessage, cookies and any explicit `noopener` relationship. No injected privileged JavaScript bridge is inherited: the child uses the ordinary null-profile BrowserScreen and its independently confirmed native AutoFirma/TLS/HTTP authentication paths.

## Admission and ownership

A top-level browser can host at most one child; recursive children are rejected. A request needs a current, visible parent, actual user gesture, resumed lifecycle, a valid public HTTPS page and no outstanding signature/certificate operation. Script-created unsolicited windows remain disabled. No assumption is made that the requesting frame itself has the same origin as the visible parent; the child receives no parent profile authority.

Before attachment, the token expires after 15 monotonic seconds. Once attached, a child is not killed merely because that handoff period ends. Changing or releasing the parent/document invalidates the child. A stale close cannot close a later window. A rejected/expired pending native transport is completed at most once with a null child; transport delivery is never retried after an uncertain send.

The parent is retained but hidden while the child is foreground. Its application-level native consent paths cannot proceed in that state. The child has its own WebView and controller instances. Clearing a child's remembered certificate choice still performs the process-scoped platform clear; the parent is retained through that clear, rather than destroying the original form. Actual certificate-use admission remains gated by the existing clear coordinator.

Backgrounding without an explicitly retained external-return flow closes the child. External file/certificate selection uses the existing bounded return rules; no persistent permission or arbitrary callback replay is created. Process death remains outside in-memory form preservation guarantees.

## Code authorship

Two GPT-6.1 Sol Max outputs were accepted: BrowserPopupHeader with its two instrumentation tests, and PopupChromeCallbacksTest with three callback-delegation cases. The initial broader lease/transport writers did not finish within their bounded runs and are not counted as successful implementation. The integrator implemented the lease, transport, recursive child-host wiring and combined tests. The header's button width was constrained by a row weight to retain its title at larger font sizes; original and integrated hashes are recorded separately.

## Verification

Pure lease JUnit tests cover uniqueness, stale tokens, owner/navigation changes, monotonic expiry and attached lifetime. Android unit tests exercise actual Message/WebViewTransport single delivery and cancellation. Instrumentation creates a real Chromium popup from a trusted touch event over local HTTPS fixtures, checks opener/postMessage, preserves an edited parent input, closes through JavaScript, blocks unsolicited script windows and exercises independent child certificate consent. No user certificate, government account, real form submission or external page content is used.

Broad acceptance requires all six existing GitHub Actions checks on the exact PR head before an in-place, same-signer APK update. This specification is not evidence those checks have run; actual XML/log receipts are collected separately.

## Primary references

- Android WebChromeClient.onCreateWindow/onCloseWindow: https://developer.android.com/reference/android/webkit/WebChromeClient
- Chromium implementation details: https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/how-does-on-create-window-work.md

The references explain why a fresh child and the supplied native transport are necessary, and why only one pending native child can exist at a time. No upstream implementation is copied into this application.

## Remaining limitations

Only one additional window and user-gesture creation are supported in this slice. An async scripted window without a gesture is not silently authorized; nested popups are not supported. Browser-created content still has to comply with existing public HTTPS/navigation policies. This does not implement missing AutoFirma formats, WebAuthn, downloads, global intent registration, arbitrary third-party cookies or administrative submission/withdrawal. Tests do not establish acceptance by every government website.
