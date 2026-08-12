# Tunnel route diagnostic ownership design

## Finding

`MainActivity.onTunnelRouteEvent(requestId, event)` currently gives the request ID to
`SigningCoordinator.onTunnelRouteEvent`, which ignores events that do not belong to the
currently active signing operation, but then records every event in the application
`SanitizedLogger` unconditionally.

A tri-phase HTTP call runs on an executor. Coroutine cancellation/timeout marks
`ProfileHttpCancellation` and can terminate the signing operation before the executor-side
transport returns. `DirectFirstProfileHttpTransport` may still emit its final closed route event
when that transport call returns. Navigation/background cancellation has the same ownership
split: the coordinator is no longer active, while the application process and QA diagnostic
mirror can still accept the late event.

The event contains only closed enums/coarse duration and no request ID or URL, so this is not a
raw-secret disclosure. It is nevertheless stale diagnostic provenance: a route observation from
an abandoned signing request can appear as if it belongs to the current browser/application
state. The existing stale-WebView diagnostic policy already treats lifecycle ownership as part
of the logging boundary.

## Selected behavior

Use `SigningCoordinator` as the single request-ownership authority for route observations.
`onTunnelRouteEvent(requestId, event)` returns `true` only when the event belongs to the current,
non-cancelled active operation. `MainActivity` records the sanitized route diagnostic only when
that call returns `true`.

The same accepted event continues to update `ConnectingSecurely`/`Signing` UI state exactly as
before. Wrong-request, pre-confirmation, post-completion and cancelled events return `false` and
are UI/diagnostic silent at the activity boundary.

## Non-goals

- Do not change `TunnelRouteEvent` fields or copy request IDs into diagnostics.
- Do not change direct/tunnel fallback, retry, timeout or cancellation semantics.
- Do not add raw URL, host, exception, credential or payload data to logs.
- Do not change QA/release activation, file-sink allowlists, signing algorithms, portal profiles,
  TLS or certificate policy.

## Exact files

Production:
- `app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt`
- `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`

Tests:
- `app/src/test/java/dev/junta/firmamobile/signing/SigningCoordinatorTest.kt`
- `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`

Evidence after GREEN/full verification:
- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `docs/threat-model.md`
