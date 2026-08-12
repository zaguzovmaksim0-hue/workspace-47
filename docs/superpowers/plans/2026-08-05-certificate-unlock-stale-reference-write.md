# Certificate Unlock Stale Reference Write Implementation Plan

> **For agentic workers:** use `superpowers:executing-plans`,
> `superpowers:test-driven-development`, `superpowers:systematic-debugging`, and
> `superpowers:verification-before-completion`.

**Goal:** Prevent a cancelled unlock that outlives blocking PKCS#12 work from initiating a stale
selected-reference summary write.

## Task 1: Reproduce stale post-cancellation write

**Files:**
- Modify `app/src/test/java/dev/junta/firmamobile/certificate/CertificateRepositoryTest.kt`.

- [ ] Add a blocking `InputStream`/document-access path around a valid synthetic P12 and signal
      that blocking certificate loading has started.
- [ ] Use a reference store whose `write()` records immediately without suspension.
- [ ] Start unlock on a background dispatcher, cancel while loader is blocked, release loader,
      and require cancellation plus zero summary writes after cancellation.
- [ ] Run exact Debug RED and accept only the expected stale-write assertion failure.

## Task 2: Add minimum cancellation barrier

**Files:**
- Modify `app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt`.

- [ ] Call `currentCoroutineContext().ensureActive()` after blocking load returns and before the
      successful-result reference write.
- [ ] Preserve all success/failure mapping and summary contents.
- [ ] Run exact GREEN and complete `CertificateRepositoryTest` in Debug+QA.

## Task 3: Full verification and remote completion

- [ ] Run full pin/unit/assembly and forced lint gates.
- [ ] Run Python, Android artifact, release fail-closed and Go test/vet/build gates; remove relay
      binary and require no release APK.
- [ ] Review exact diff/scope, whitespace, sensitive/personal data and unsafe security additions.
- [ ] Update audit ledger, roadmap, test report and durable handoff only with observed evidence.
- [ ] Fetch, verify remote state, stage exact surface, run cached checks, commit atomically, push,
      fetch again and require exact remote SHA with divergence `0/0`.
