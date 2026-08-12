# Certificate selection permission cancellation design

## Finding hypothesis

`CertificateRepository.select()` intentionally takes persistable read permission before
writing the selected certificate reference so persisted state never points at a URI the app
cannot reopen. The write is a suspending `CertificateReferenceStore.write()` operation.

If the selection coroutine is cancelled after `takePersistableReadPermission(uri)` succeeds
but before the reference write commits, the current `CancellationException` branch rethrows
without releasing the newly acquired permission. The selection is not committed, but the app
can retain provider-granted access to the cancelled PKCS#12/PFX document until app data is
cleared, uninstall occurs, or another explicit release happens.

This violates least-privilege ownership for a user-selected certificate document: an
uncommitted/cancelled new selection must not extend the app's durable URI access.

## Scope

Production:

- Modify `app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt`
  only if RED confirms the permission leak.

Tests:

- Modify `app/src/test/java/dev/junta/firmamobile/certificate/CertificateRepositoryTest.kt`
  with a deterministic store-suspension cancellation regression.

Evidence after full verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/threat-model.md` only if the established certificate-document permission boundary
  materially changes; otherwise preserve it.

No PKCS#12 parsing, password handling, unlock cache, certificate session, signing, WebView,
network/TLS, portal profile, release or dependency behavior changes.

## Required behavior

1. A new persistable URI permission acquired by a selection must be released if the reference
   write is cancelled before that new reference is committed.
2. A pre-existing permission/reference for the same URI must not be released merely because a
   metadata rewrite is cancelled.
3. Ordinary write failure retains the existing rollback/release behavior.
4. Successful replacement still stores the new reference before releasing the previous URI.
5. Cancellation remains propagated to the caller; it is not converted into a product error.
6. Cleanup is best-effort because a provider may reject permission release; no exception from
   cleanup may replace coroutine cancellation.

## Selected approach

Use the existing rollback boundary already applied to ordinary write failures: when
`referenceStore.write(reference)` throws `CancellationException`, release the newly acquired
URI permission only when it differs from the previously persisted URI, then rethrow the same
cancellation. This is the minimum change for the reproducible pre-commit cancellation case and
does not alter successful selection ordering.

This milestone does not claim to solve arbitrary hostile `CertificateReferenceStore`
implementations that commit and then throw cancellation. The production DataStore is treated
as the store transaction boundary; the regression specifically pins cancellation while the
write is suspended before commit.

## Verification strategy

- Add a test store whose `write()` signals entry and suspends before mutating its reference.
- Start `repository.select()` asynchronously, wait until the write is suspended, cancel it,
  and require: cancellation propagates, the new permission is released, and no reference is
  committed.
- Observe RED on unchanged production.
- Apply only the cancellation cleanup in `CertificateRepository.selectOnIo()`.
- Run focused repository tests in Debug and QA, then the full Android/Python/Go/artifact/
  release-fail-closed gates.
- Review complete/staged diffs and security/privacy patterns before atomic commit/push.
