# CAdES Capture Buffer Zeroization Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans`,
> `superpowers:test-driven-development`, `superpowers:systematic-debugging` for
> unexpected failures, and `superpowers:verification-before-completion` before
> commit/push.

**Goal:** Clear the actual backing buffer used to capture CAdES pre-sign signed
attributes, rather than clearing an unrelated `toByteArray()` copy.

**Architecture:** Reuse the repository's established sensitive
`ByteArrayOutputStream` ownership pattern locally: a private clearing subclass owns
and directly zeros protected `buf` through an explicit `clear()` method. No public API or cryptographic contract
changes.

## Task 1: Reproduce and pin the invariant

**Files:**
- Modify: `tools/tests/test_ci_policy.py`
- Create: `docs/superpowers/specs/2026-08-04-cades-capture-buffer-zeroization-design.md`
- Create: `docs/superpowers/plans/2026-08-04-cades-capture-buffer-zeroization.md`

- [ ] Record the standalone JVM `ByteArrayOutputStream` reproduction showing that
  `toByteArray().fill(0); reset()` retains the original bytes in `buf`.
- [ ] Add one source-policy test for the exact `CapturingContentSigner` ownership
  and zeroization pattern.
- [ ] Run only that test and observe the expected RED against current production.

## Task 2: Apply the minimum production fix

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/LocalCadesDetachedAdapter.kt`

- [ ] Replace the capturer's ordinary `ByteArrayOutputStream` with a private
  `ClearingByteArrayOutputStream`.
- [ ] Its explicit `clear()` must directly `buf.fill(0)` and then `reset()`; do not
  override the inherited stream `close()` because BouncyCastle may close the supplied
  stream before `signedBytes()` is read.
- [ ] `CapturingContentSigner.close()` must clear the owned stream and clear its
  placeholder; do not change signing, validation, limits, providers, or ownership.
- [ ] Run the source-policy GREEN and CAdES focused tests in Debug and QA.

## Task 3: Full verification and evidence

**Files updated only after evidence changes:**
- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

- [ ] Run toolchain pin checks, complete Debug/QA JVM suites, lint, Debug/QA and
  QA-AndroidTest builds, Android artifact verification, release fail-closed,
  complete Python tests, Go test/vet/build, and remove generated Go binary.
- [ ] Inspect complete diff, run `git diff --check`, scan changed content for
  sensitive material, unsafe security patterns, unrelated changes, and generated
  artifacts.
- [ ] Record only fresh evidence and exact limitations; do not claim physical
  secure erasure or device/portal validation.
- [ ] Fetch/recheck divergence, create one atomic commit, push autonomous branch,
  fetch again, and require exact local/remote SHA equality with divergence `0/0`.
