# Certificate unlock threat-model reconciliation implementation plan

**Goal:** Make the authoritative threat model match the already-implemented 24-hour
encrypted certificate-unlock recovery boundary and prevent the stale boundary from
returning.

**Files:**

- Modify: `tools/tests/test_ci_policy.py`
- Modify: `docs/threat-model.md`
- Update: `docs/autonomous/2026-08-04-audit-ledger.md`
- Update: `docs/security-roadmap.md`
- Update: `docs/test-report.md`
- Update: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

## Evidence sequence

1. Add a focused documentation-policy test requiring the current encrypted-cache,
   expiry, recovery and clearing boundary and rejecting the obsolete process-death
   lock wording.
2. Run only that test and observe RED against the current stale threat model.
3. Update the asset/trust-boundary/T5 text only; do not change runtime behavior.
4. Re-run the focused policy test GREEN.
5. Run the existing focused certificate lifecycle tests for memory-pressure and
   process-recreation semantics, then run the complete Python policy suite.
6. Inspect the complete diff, run `git diff --check`, and scan added content for
   secrets, personal data, unsafe security claims and unrelated changes.
7. Update evidence documents with the exact documentation mismatch and verification.
8. Stage only the exact milestone files, run staged diff check, commit atomically,
   push the autonomous branch and verify exact remote SHA and divergence 0/0.
