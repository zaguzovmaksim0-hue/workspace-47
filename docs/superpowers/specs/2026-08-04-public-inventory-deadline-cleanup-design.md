# Public inventory deadline cleanup design

## Finding

`tools.public_portal_inventory._run_with_deadline()` starts a daemon worker and
then computes the remaining deadline before joining it. If the deadline expires
between worker start and that computation, `_remaining_seconds()` raises before
the existing `on_timeout` callback is reached. A blocking socket/body operation
can therefore remain alive until its own fallback timeout even though the caller
has already returned a closed deadline error.

The full Python gate exposed the race in
`DeadlineTest.test_one_blocking_read_is_cancelled_at_the_wall_clock_deadline`.
Five immediate focused reruns passed, confirming timing sensitivity. A
deterministic expired-deadline probe then returned
`HTTPS request deadline exceeded` with `on_timeout_called=False`, proving the
cleanup gap independently of scheduler timing.

## Scope

Production:

- Modify `tools/public_portal_inventory.py` only in the generic deadline helper.

Tests:

- Modify `tools/tests/test_public_portal_inventory.py` with a deterministic test
  for expiry after worker start but before join-time calculation.

Evidence after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No Android, WebView, TLS policy, DNS classification, portal catalog, profile,
signing, certificate, dependency, release, or network allowlist behavior changes.

## Required behavior

1. Once a deadline-owned worker has started, every timeout path invokes the
   supplied cleanup callback best-effort before returning an `InventoryError`.
2. Cleanup exceptions remain suppressed so timeout classification is stable.
3. A completed operation retains its existing result/exception behavior.
4. The deadline is not extended, retried, or weakened.
5. The helper continues to use a daemon worker and does not wait indefinitely for
   hostile/blocking I/O after cleanup is requested.

## Implementation shape

Introduce one private best-effort cleanup helper. In `_run_with_deadline()`,
compute the join timeout inside a `try` after worker start; if deadline
calculation raises, invoke cleanup and re-raise. Reuse the same helper for the
existing `worker.is_alive()` timeout path. No polling, retry, grace-period
extension, or thread-join weakening is added.
