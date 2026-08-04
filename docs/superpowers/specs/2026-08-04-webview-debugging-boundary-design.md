# WebView Debugging Boundary Design

## Finding

`TrustedJuntaWebView` currently calls
`setWebContentsDebuggingEnabled(BuildConfig.DEBUG)`. The `qa` build type inherits
from `debug`, is explicitly debuggable, and therefore compiles with
`BuildConfig.DEBUG=true`. QA is also the controlled acceptance build used for
real portal sessions and certificate-backed checks.

Android documents `WebView.setWebContentsDebuggingEnabled(true)` as an
application-wide capability that permits WebView state to be inspected and
modified through ADB/DevTools, and identifies enabling it outside an explicitly
intended development use as a security liability:
https://developer.android.com/reference/android/webkit/WebView.html#setWebContentsDebuggingEnabled(boolean)

## Security objective

Keep remote WebView debugging available for the ordinary developer `debug`
variant while disabling it for `qa` and `release`, even though QA remains
`android:debuggable=true` for its existing controlled diagnostics and test
workflow.

## Chosen approach

Add an explicit boolean BuildConfig field named
`ENABLE_WEBVIEW_CONTENTS_DEBUGGING`:

- `debug`: `true`;
- `qa`: `false`;
- `release`: `false`;
- default configuration: fail-safe `false`.

`TrustedJuntaWebView` will pass only that explicit field to
`WebView.setWebContentsDebuggingEnabled` and will no longer use
`BuildConfig.DEBUG` for this security boundary.

This is preferred over making QA non-debuggable because QA currently relies on
other controlled diagnostics and test infrastructure. It is also preferred over
a runtime manifest-flag check because QA intentionally has the debuggable flag,
which is broader than the WebView DevTools policy we need to enforce.

## Files

- Modify `app/build.gradle.kts` to define the explicit per-build field.
- Modify `app/src/main/java/dev/junta/firmamobile/browser/TrustedJuntaWebView.kt`
  to consume the field.
- Modify `tools/tests/test_ci_policy.py` with a fail-closed source/config contract
  test that runs before and after production mutation.
- Update `docs/autonomous/2026-08-04-audit-ledger.md`, `docs/security-roadmap.md`,
  and `docs/test-report.md` only after verified evidence exists.

## Verification

1. RED: Python policy test fails because the field and explicit WebView policy do
   not yet exist.
2. GREEN: the focused Python policy test passes.
3. Run Debug and QA JVM suites so both generated BuildConfig variants compile.
4. Run lint/build/APK artifact/release-fail-closed, Python, and Go gates.
5. Inspect complete diff, `git diff --check`, and sensitive/unsafe-pattern scans.

No APK installation, app launch, device control, portal interaction, certificate
operation, or E2E claim is part of this milestone.
