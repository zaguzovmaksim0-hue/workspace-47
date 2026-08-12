# Certificate Selection Permission Cancellation Implementation Plan

> **For agentic workers:** use `superpowers:executing-plans`,
> `superpowers:test-driven-development`, `superpowers:systematic-debugging`, and
> `superpowers:verification-before-completion` for this milestone.

**Goal:** Ensure a cancelled, uncommitted certificate selection does not retain a newly
acquired persistable URI read permission.

**Architecture:** Keep the existing permission-before-reference ordering, reproduce
cancellation while the reference write is suspended before commit, and extend the existing
write-failure rollback to cancellation without swallowing `CancellationException`.

## Task 1: Reproduce permission retention on cancelled pre-commit write

**Files:**
- Modify `app/src/test/java/dev/junta/firmamobile/certificate/CertificateRepositoryTest.kt`.

- [x] Add a `CertificateReferenceStore` test double whose `write()` signals entry and suspends
      before changing its stored reference.
- [x] Start `CertificateRepository.select()` in a child coroutine, wait for write entry,
      cancel/join it, and assert the new URI appears exactly once in both acquired and released
      permission lists while the stored reference remains null.
- [x] Run the exact Debug test and observe RED on the release assertion.

## Task 2: Implement minimum cancellation rollback

**Files:**
- Modify `app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt`.

- [x] In the `referenceStore.write(reference)` `CancellationException` branch, call the
      existing best-effort `releaseQuietly(uri)` only when `previous?.uri != uri`, then rethrow
      the original cancellation.
- [x] Do not alter ordinary failure, same-URI, successful replacement or permission ordering.
- [x] Run the exact regression GREEN, then complete `CertificateRepositoryTest` Debug+QA.

## Task 3: Full verification and remote completion

**Evidence files after verification:**
- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

- [x] Run full pin/unit/assembly and forced lint gates.
- [x] Run complete Python, Android artifact, release fail-closed and Go test/vet/build gates;
      remove generated relay binary and require no release APK.
- [x] Review exact diff/scope, `git diff --check`, sensitive-data, personal-data and unsafe
      WebView/TLS/backup additions.
- [x] Update evidence only with observed results; preserve threat-model wording unless needed.
- [ ] Fetch, verify branch/upstream/canonical/divergence, stage exact milestone files, run
      cached checks/scans, commit atomically, push, fetch again and require exact remote SHA
      with divergence `0/0`.
