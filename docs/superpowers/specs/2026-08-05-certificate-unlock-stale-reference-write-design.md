# Certificate unlock stale-reference write design

## Finding hypothesis

`CertificateRepository.unlock()` reads the current selected reference, performs blocking PKCS#12
I/O/parsing, and on success writes an updated copy containing the safe certificate summary. The
repository call runs inside `withContext(ioDispatcher)`, but the blocking loader is not itself a
cancellation point.

`CertificateViewModel` cancels an in-flight unlock before certificate replacement or forget, but
it does not join that job before launching the new repository operation. Therefore an old unlock
can remain inside blocking document/PKCS#12 work after cancellation while a newer operation
changes or clears the selected reference.

After the blocking loader returns, current repository code does not check cancellation before
calling `referenceStore.write(oldReference.copy(summary=...))`. A suspend store implementation
that performs work before its first suspension (or does not suspend) can therefore receive and
commit the stale old reference even though the unlock job has already been cancelled. The outer
`withContext` may still report cancellation to its caller only after this side effect.

## Scope

Production:
- Modify `app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt` only if
  deterministic RED confirms a stale post-cancellation summary write.

Tests:
- Modify `app/src/test/java/dev/junta/firmamobile/certificate/CertificateRepositoryTest.kt` with
  a blocking synthetic PKCS#12 input and a non-suspending reference store observation.

Evidence after full verification:
- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No certificate format/validation, password, unlock-cache, session, signing, WebView/network/TLS,
portal profile, release or dependency policy changes.

## Required behavior

1. Cancellation that occurs while blocking certificate loading is in progress must be observed
   before any successful-unlock reference-summary write is attempted.
2. The original `CancellationException` remains propagated; it is not converted to a product
   error.
3. A non-cancelled successful unlock retains the existing safe-summary persistence behavior.
4. Failed certificate loads retain current error mapping and do not write a summary.
5. Replacement/forget semantics are unchanged except that a cancelled old unlock cannot write
   its stale reference after blocking work returns.

## Selected approach

Add an explicit coroutine cancellation check immediately after the blocking loader call and
before the successful-result summary persistence branch. `currentCoroutineContext().ensureActive()`
is the minimum repository-level barrier: it does not depend on the reference-store implementation
being cancellable and prevents a cancelled old operation from initiating any later write.

## Verification strategy

- Use a valid synthetic P12 returned through a blocking input stream that signals when parsing
  has entered a read and waits for release.
- Run `repository.unlock()` on a real background dispatcher, wait until blocked, cancel the job,
  then release the stream. The fake reference store's `write()` is intentionally non-suspending
  and records every write.
- Require the caller to remain cancelled and the store to contain no post-cancellation write.
- Observe RED on unchanged production, then add only the explicit cancellation barrier.
- Run focused repository Debug/QA tests and full Android/Python/Go/artifact/release gates before
  evidence, staged review, atomic commit and push.
