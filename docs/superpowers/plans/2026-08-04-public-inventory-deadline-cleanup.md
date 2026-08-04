# Public inventory deadline cleanup implementation plan

**Goal:** Ensure a started deadline-owned I/O worker receives its fail-closed
cleanup callback even when the deadline expires before the join timeout can be
calculated.

**Exact files:**

- Modify `tools/tests/test_public_portal_inventory.py`
- Modify `tools/public_portal_inventory.py`
- Update evidence documents only after fresh verification.

## TDD sequence

1. Add a deterministic test that starts a blocking operation, supplies an already
   expired monotonic deadline at the post-start calculation point, and requires
   the cleanup callback to release the operation before `InventoryError` returns.
2. Run only that test and observe RED because current control flow raises before
   invoking `on_timeout`.
3. Add the minimum private best-effort cleanup helper and invoke it both when
   join-time calculation raises and when the joined worker remains alive.
4. Run the deterministic test, the complete `DeadlineTest`, repeated focused
   deadline tests, and full Python discovery.
5. Inspect the exact diff, run `git diff --check`, scan added content for secrets,
   personal data and unsafe network-policy changes, and update evidence docs with
   exact results.
6. Stage only the G6-01 surface, run staged checks, commit atomically, push, fetch,
   and verify the exact remote SHA and divergence 0/0.
