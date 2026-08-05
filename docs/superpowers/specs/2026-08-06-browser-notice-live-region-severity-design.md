# Browser Notice Live-Region Severity Design

## Problem

`BrowserNoticeBanner` currently applies `LiveRegionMode.Assertive` to every browser
notice. `BrowserScreen` routes both urgent failures and non-error status messages
through that component. In particular, `ClientCertPreferenceBarrierState.CLEARING`
renders `Preparando una sesión nueva de certificado…`, while successful exact site
or global browser-data deletion renders a success message. Those ordinary progress
and success updates therefore inherit assertive announcement semantics and may
interrupt assistive-technology speech even though no immediate user intervention is
required.

The existing G10 remediation is still correct for actual browser errors: load,
compatibility, navigation, Client TLS preference, and clear failures must remain
assertive.

## Constraints

- Preserve the current Material 3 visual treatment and all browser strings.
- Do not change navigation, WebView, TLS, Client TLS, certificate, signing, data
  deletion, profile/catalog, release, or dependency behavior.
- Do not infer urgency from the presence of a Retry callback: compatibility or
  blocked-navigation alerts may be urgent without Retry, while progress/success
  messages are non-urgent for accessibility purposes.
- Preserve `Assertive` as the default component behavior so existing error call sites
  cannot silently become less urgent.
- Automated tests may prove Compose semantics only. Physical TalkBack timing and
  interruption behavior remain a manual acceptance gate.

## Considered approaches

1. **Recommended: explicit live-region mode plus a pure notice policy.**
   `BrowserNoticeBanner` accepts an explicit `LiveRegionMode`, defaulting to
   `Assertive`. `BrowserScreen` computes the mode from the same state precedence used
   to choose the displayed notice. This is small, testable, and does not couple
   accessibility severity to UI affordances.
2. Infer `Polite` whenever `onRetry == null`. Rejected because blocked-navigation and
   compatibility failures can be urgent without a Retry action.
3. Split the banner into separate error/status components. Rejected as unnecessary
   UI restructuring for a semantics-only defect.

## Notice severity contract

The live-region policy follows the displayed-notice precedence:

- Client certificate preference `CLEARING`: `Polite`.
- Client certificate preference `FAILED`: `Assertive`.
- Compatibility error: `Assertive`.
- Any `BrowserErrorCode`: `Assertive`.
- Any `NavigationBlockReason`: `Assertive`.
- Global browser-data clear success: `Polite`.
- Global browser-data clear failure: `Assertive`.
- Exact current-site clear success: `Polite`.
- Limited current-site clear or failure: `Assertive`.
- No notice: fallback `Assertive`; no banner is rendered in this state.

If a progress/success result coexists with a higher-priority error, the error wins and
remains assertive.

## Implementation shape

- `BrowserChromeComponents.kt`: add `liveRegionMode: LiveRegionMode =
  LiveRegionMode.Assertive` to `BrowserNoticeBanner` and use it in semantics.
- `BrowserScreen.kt`: add an internal pure `browserNoticeLiveRegionMode(...)` helper
  over the existing notice-source states and pass its result to
  `BrowserNoticeBanner`.
- `BrowserChromeComponentsTest.kt`: retain the existing assertive error regression and
  add a polite component regression.
- `BrowserScreenTest.kt`: table-like assertions pin progress/success as polite and
  failures/higher-priority errors as assertive.

## Verification

TDD must observe RED before production mutation. Focused Debug and QA tests cover the
component and policy. Then run the full relevant Android unit/lint/build gates,
Python and Go gates, artifact checks, release fail-closed verification, diff/secret/
unsafe-pattern scans, and remove generated relay/release artifacts. Update the audit
ledger, test report, security roadmap, and durable handoff only after evidence
changes. No threat-model wording change is expected because this introduces no new
asset or trust edge.
