# Tunnel route diagnostic ownership implementation plan

**Goal:** Prevent stale/foreign tri-phase route callbacks from repopulating application/QA
sanitized diagnostics after the owning signing operation is no longer active.

**Design:** `docs/superpowers/specs/2026-08-08-tunnel-route-diagnostic-ownership-design.md`

- [x] Add a source-level regression in `BrowserSecurityRegressionTest` requiring
  `MainActivity` to record `TUNNEL_ROUTE` only after coordinator ownership acceptance; run it and
  observe the expected RED against unchanged production source.
- [x] Extend `SigningCoordinatorTest` around the existing route-progress test so active matching
  events are accepted and wrong/pre-confirmation/post-completion/cancelled events are rejected.
- [x] Change `SigningCoordinator.onTunnelRouteEvent` to return `Boolean`, with `false` on every
  existing early-rejection path and `true` only after an active matching event is accepted.
- [x] Gate `SanitizedLogger.recordTunnelRouteEvent(event)` in `MainActivity` on that returned
  ownership result. Do not change event contents or network emission.
- [x] Run focused Debug/QA signing + security regressions, then adjacent route/network tests.
- [x] Run fresh runtime-lock/core/AAPT2, full Debug/QA JVM, lint, Debug/QA/QA-AndroidTest build,
  Python, Go test/vet/build, Android artifact checks and release-signing fail-closed gates.
- [x] Inspect full diff, `git diff --check`, sensitive/unsafe-pattern scans, generated relay and
  release APK absence.
- [x] Update authoritative evidence only with observed results and prepare the exact verified
  atomic G30 diff for the required commit/push protocol.
