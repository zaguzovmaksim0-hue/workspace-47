# XAdES temporary byte-stream zeroization implementation plan

**Goal:** Remove unnecessary retained backing-buffer copies created by XAdES
serialization and canonicalization without changing XAdES output or protocol
behavior.

**Files:**

- Modify: `tools/tests/test_ci_policy.py`
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/LocalXadesDetachedAdapter.kt`
- Update evidence after GREEN/full verification:
  `docs/autonomous/2026-08-04-audit-ledger.md`, `docs/security-roadmap.md`,
  `docs/test-report.md`, `docs/handoffs/NEXT_CHAT_HANDOFF.md`

## TDD sequence

1. Add one narrow source-policy test requiring the two XAdES byte-stream helpers to
   use `ClearingByteArrayOutputStream`, clear its actual backing `buf`, and execute
   explicit cleanup in `finally`.
2. Run only that policy test and observe RED on the current ordinary
   `ByteArrayOutputStream` implementation.
3. Add the minimal private clearing stream and convert only `serialize()` and
   `canonicalize()` to clear their backing buffers in `finally` after obtaining the
   intentional returned copy.
4. Re-run the focused policy test and the existing
   `LocalXadesDetachedAdapterTest` in Debug and QA; do not alter the tests merely to
   accept changed XML bytes.
5. Run the current complete relevant gates: toolchain pin checks, Debug and QA JVM
   tests, lint, Debug/QA/QA-AndroidTest assemble, Android artifact verification,
   release-without-private-signing fail-closed, Python tests, Go test/vet/build.
6. Remove any generated relay binary, inspect all changed files, run
   `git diff --check`, and scan the diff for credentials/personal data, TLS/WebView
   weakening and unrelated changes.
7. Update evidence documents with exact RED/GREEN/full-gate results.
8. Commit atomically, push `agent/workspace-47-autonomous-20260803`, fetch/verify
   the exact remote SHA, and record it in the durable handoff.
