# Certificate Error Live-Region Design

## Finding

`AppRoot` renders certificate-selection and certificate-unlock failures through
`CertificateError`, which is an ordinary Compose `Text`. These errors are state transitions
caused by user actions such as a failed document selection or wrong PKCS#12 password, but the
rendered error has no `SemanticsProperties.LiveRegion`. A screen-reader user whose focus remains
on the selection/unlock action therefore has no Compose semantic instruction to announce the
new blocking error.

This is separate from G10/G13 browser-notice semantics: the affected surface is the certificate
home panel and its `CertificateUiState` transitions, not a browser/network banner.

## Constraints

- Change accessibility semantics only; preserve certificate state, validation, persistence,
  password handling, signing, navigation and copy.
- Do not move/request accessibility focus or modify visual layout.
- Keep the error text and localization resources unchanged.
- Use `LiveRegionMode.Assertive` because the message represents failure of the immediately
  requested certificate action and blocks progression until the user corrects it.
- Automated evidence establishes only the Compose semantics tree. Actual TalkBack announcement
  timing/interruption and visual behavior remain physical/manual gates.

## Approaches considered

1. **Request focus on the error.** Rejected: it is more intrusive, changes navigation/focus order,
   and is unnecessary to express announcement semantics.
2. **Add an assertive live region directly to `CertificateError`.** Recommended: one local,
   reversible semantic property covers both selection and unlock errors without altering state or
   layout.
3. **Add a broad live region to the entire certificate panel.** Rejected: unrelated text/status
   changes could be re-announced and increase assistive-technology noise.

## Design

`CertificateError` keeps the same `Text`, message, color and typography and adds only:

```kotlin
modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
```

The focused regression renders `AppRoot` in a `CertificateUiState.Locked` state with
`PASSWORD_INVALID_OR_FILE`, finds the existing error text and requires
`SemanticsProperties.LiveRegion == LiveRegionMode.Assertive`. This proves the actual user-visible
error surface rather than a helper-only contract.

## Exact files

Production:
- `app/src/main/java/dev/junta/firmamobile/ui/AppRoot.kt`

Tests:
- `app/src/test/java/dev/junta/firmamobile/ui/AppRootTest.kt`

Evidence after GREEN/full gates:
- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`

Threat-model wording does not need to change because this milestone introduces no asset, trust
edge, credential flow or externally reachable behavior.
