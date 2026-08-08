# JavaScript Dialog Privacy Boundary Design

## Milestone

G25-01 — suppress insecure default WebView JavaScript dialogs.

## Evidence and problem

`BrowserScreen` is entered with an unlocked certificate and the existing sensitive-window
policy protects that browser surface with `WindowManager.LayoutParams.FLAG_SECURE`.
`TrustedJuntaWebView` installs `JuntaWebChromeClient`, but that client currently does not
override JavaScript dialog callbacks.

Android's current `WebChromeClient` API documentation states that the default dialogs for
`onJsAlert`, `onJsConfirm`, `onJsPrompt`, and `onJsBeforeUnload` do not inherit the parent
window's secure-display flag. The same documentation defines the no-dialog fallback
semantics when a `WebChromeClient` is absent:

- alert: suppress UI and continue JavaScript;
- confirm: suppress UI and return `false`;
- prompt: suppress UI and return `null`;
- before-unload: suppress UI and resume the pending navigation.

Official references, retrieved 2026-08-08:

- https://developer.android.com/reference/android/webkit/WebChromeClient#onJsAlert(android.webkit.WebView,%20java.lang.String,%20java.lang.String,%20android.webkit.JsResult)
- https://developer.android.com/reference/android/webkit/WebChromeClient#onJsConfirm(android.webkit.WebView,%20java.lang.String,%20java.lang.String,%20android.webkit.JsResult)
- https://developer.android.com/reference/android/webkit/WebChromeClient#onJsPrompt(android.webkit.WebView,%20java.lang.String,%20java.lang.String,%20java.lang.String,%20android.webkit.JsPromptResult)
- https://developer.android.com/reference/android/webkit/WebChromeClient#onJsBeforeUnload(android.webkit.WebView,%20java.lang.String,%20java.lang.String,%20android.webkit.JsResult)
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE

Therefore the current client can create an independent modal window that is outside the
browser's screenshot/screen-share protection. Remote JavaScript controls the message
content, so this is a privacy/UI trust-boundary defect even though it does not bypass
navigation, TLS, Client TLS, certificate validation, or signing policy.

## Approaches considered

### A. Suppress all four JavaScript dialogs explicitly — selected

Handle each callback in `JuntaWebChromeClient`, return `true`, and immediately resolve the
`JsResult` using Android's documented no-`WebChromeClient` semantics:

- `onJsAlert` -> `result.confirm()`;
- `onJsConfirm` -> `result.cancel()`;
- `onJsPrompt` -> `result.cancel()`;
- `onJsBeforeUnload` -> `result.confirm()`.

This creates no extra window, cannot leak the JavaScript-controlled message through the
default dialog, keeps execution/navigation from remaining suspended, and introduces no
new lifecycle owner. It also fails closed for JavaScript confirmation/prompt requests.

Trade-off: a portal that requires modal JavaScript dialogs can no longer use them inside
this privileged WebView. No existing profile contract promises JavaScript modal-dialog
support; physical compatibility remains an external acceptance gate.

### B. Reimplement secure custom dialogs — rejected for this milestone

A custom Activity/Compose dialog could set a secure flag explicitly and preserve modal
interaction. It would add lifecycle ownership, stale-WebView handling, focus/accessibility,
string localization, prompt input, result-once semantics, and sensitive-content retention.
That is a materially larger attack and reliability surface than the requirement justifies.

### C. Remove `WebChromeClient` — rejected

This would suppress dialogs automatically, but would also remove explicit permission and
geolocation denial plus bounded page-progress handling. Those existing hardening controls
must remain.

## Exact production contract

Only `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebChromeClient.kt` changes.
The client must:

1. continue progress clamping unchanged;
2. continue rejecting popup windows, generic permission requests, and geolocation;
3. explicitly handle all four JavaScript modal callbacks and never delegate them to the
   platform default dialog;
4. resolve each callback immediately with the mapping above;
5. never display, log, persist, or forward `url`, `message`, or prompt `defaultValue`;
6. add no Activity, Dialog, Compose state, profile exception, allowlist, dependency, or
   release behavior.

## Test contract

Add focused JVM/Robolectric coverage in
`app/src/test/java/dev/junta/firmamobile/browser/JuntaWebChromeClientTest.kt` or the
smallest equivalent browser-security regression. RED must demonstrate that unchanged
production returns the platform-default path for at least one JavaScript dialog callback.
GREEN must prove all four callbacks are handled and that confirm/prompt are denied while
alert/before-unload are resolved without leaving JavaScript/navigation suspended.

A source-level regression may supplement runtime assertions to guarantee that all four
callbacks remain explicit and no platform-default delegation is reintroduced.

## Verification and scope

After focused Debug/QA GREEN, run the existing dependency/toolchain/full JVM gate,
Debug/QA lint and non-release assemblies, Python tests, Go test/vet/build, Android APK
artifact checks, release-signing fail-closed gate, `git diff --check`, exact-scope review,
and sensitive/unsafe WebView/TLS pattern scans. Remove generated relay/release artifacts.

No APK installation or launch, device automation, authenticated portal use, credentials,
private certificate material, real signing, upload, payment, or submission is permitted.
The automated claim is limited to eliminating Android's insecure default JavaScript dialog
window from this privileged WebView; portal compatibility remains not physically E2E
validated.
