# Browser notice live-region design

## Finding

`BrowserNoticeBanner` renders portal/network failures and recovery actions dynamically above the
WebView. Its current semantics expose the visible text and retry control only after accessibility
focus reaches them. The banner has no live-region property, so a screen reader is not instructed
to announce a newly appearing failure while the user's focus remains elsewhere in browser chrome
or portal content.

The pinned Compose UI API provides `SemanticsProperties.LiveRegion`, the `liveRegion` semantics
property and `LiveRegionMode.Assertive`.

## Scope

Behavior and test:

- `app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt`
- `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeComponentsTest.kt`

Evidence after full verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No string resource, visual layout, focus target, retry action, WebView lifecycle, network/TLS,
certificate, signing, portal profile, dependency, release or workflow behavior changes.

## Required behavior

1. A rendered `BrowserNoticeBanner` must expose `SemanticsProperties.LiveRegion` with
   `LiveRegionMode.Assertive`.
2. The banner's existing visible message, optional retry control, test tag and touch target remain
   unchanged.
3. The implementation must not request focus or move accessibility focus.
4. No other static or progress component becomes assertive in this milestone.
5. Automated evidence covers the semantics contract; physical TalkBack announcement timing remains
   a manual acceptance gate and is not claimed from Robolectric.

## Considered approaches

### Selected: assertive live region on the banner container

Add one semantics property to the existing `Surface`. This directly models a newly appearing
blocking error, preserves the merged descendant text/action tree and avoids focus manipulation.
It is the smallest reversible change and is supported by the current pinned Compose API.

### Rejected: polite live region

A polite announcement can wait behind other speech. These notices report failed or uncertain
portal/network state and may require immediate retry/recovery, so delayed announcement is a weaker
contract than the severity of the banner.

### Rejected: explicit focus request or manual accessibility event

Moving focus would disrupt portal navigation and create lifecycle ownership concerns. Emitting a
platform event manually duplicates Compose semantics plumbing and is harder to test deterministically.

## Verification strategy

- Add a Compose semantics regression that expects `LiveRegionMode.Assertive` on the node tagged
  `BROWSER_NOTICE_TAG` while production remains unchanged; require RED because the property is
  absent.
- Add only `.semantics { liveRegion = LiveRegionMode.Assertive }` to the existing banner modifier.
- Run exact GREEN and the complete browser-chrome test class in Debug and QA.
- Run full Android unit/build/lint, Python, Go, APK artifact and release fail-closed gates.
- Review exact scope, whitespace, sensitive content and absence of unsafe WebView/network/security
  changes before commit and push.
