# Afirma Frame UI Isolation Design

## Finding

G19 correctly stopped modern subframe and deprecated String-callback Afirma navigation from
reaching `onAfirmaRequest`, so those paths cannot deliver native signing work. Its special
`NavigationDecision.HandleAfirma` rejection branch, however, still calls
`BrowserNavigationCallbacks.onNavigationBlocked(UNTRUSTED_AFIRMA_ORIGIN)` when
`isModernMainFrame=false`.

`BrowserScreen` maps that callback to `blockedReason`, which drives an assertive top-level browser
notice. A valid-looking `afirma:` or embedded-Afirma `intent:` in an iframe can therefore still
mutate application-level UI even though the native request itself is correctly denied.

G22 established the broader frame-ownership invariant for generic `UpgradeToHttps` and `Block`
branches, but explicitly excluded changing G19 Afirma delivery. The current special branch is thus
an uncovered follow-up, not a regression of G19's native-delivery protection and not a navigation
allowlist bypass.

## Constraints

- Preserve G19's main-frame-only native Afirma delivery exactly.
- Preserve fail-closed consumption (`true`) for subframe and legacy Afirma navigation.
- Preserve the existing sanitized `NAVIGATION_BLOCKED` diagnostic with
  `UNTRUSTED_AFIRMA_ORIGIN` and `main_frame=false`.
- Do not change `JuntaNavigationPolicy`, Afirma parsing/payload handling, signing, external-browser
  handoff, Client TLS, WebMessage, profiles/release policy or dependencies.
- Do not add a different UI signal for subframes; frame-ambiguous input has no ownership of
  top-level application UI.

## Chosen approach

Remove only `callbacks.onNavigationBlocked(UNTRUSTED_AFIRMA_ORIGIN)` from the
`!isModernMainFrame` `HandleAfirma` branch. The branch still logs and returns `true`. The modern
main-frame branch still records the Afirma request and calls `onAfirmaRequest` unchanged.

This is preferable to filtering `blockedReason` in `BrowserScreen`: frame ownership is known at the
WebView callback boundary and should be enforced there, consistent with G20/G22/G23/G24/G26.

## Test contract

Update the existing direct regressions so:

- modern Afirma subframe + embedded-Afirma subframe both return `true` and leave application
  callback events empty;
- their sanitized diagnostics still contain `reason=UNTRUSTED_AFIRMA_ORIGIN` and
  `main_frame=false`, without raw request payload material;
- deprecated String Afirma returns `true` and leaves application callbacks empty;
- existing modern main-frame Afirma positive tests remain unchanged and continue reaching only the
  native Afirma request callback.

RED on unchanged production must fail because current subframe and legacy paths populate
`RecordingBrowserCallbacks.events` with `blocked:UNTRUSTED_AFIRMA_ORIGIN`.

## Exact files

Production:
- `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`

Tests:
- `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`

Evidence after full verification:
- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `docs/threat-model.md`

## Claim boundary

This closes a top-level UI/frame-ownership residual. It does not claim that subframes could sign,
that Afirma parsing or origin policy was bypassed, or that a physical portal flow was validated.
