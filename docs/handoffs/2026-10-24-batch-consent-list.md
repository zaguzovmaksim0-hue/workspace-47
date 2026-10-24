# Batch-document consent audit (task 11)

## Scope and evidence

Audited `origin/main` at `07b8232d`, against project-plan sections 3 and 6.
This change owns consent-dialog tests and documentation only. It does not change
production, `BrowserScreen.kt` (task 7), adapters, protocols, dependencies,
verification metadata, locks or security policy.

| Consent path | Count before signing | List before signing | Name / size / SHA-256 per document |
| --- | --- | --- | --- |
| `SigningConfirmationDialog` / `BatchSigningCoordinator` | Yes, pluralized | Expandable, sanitized IDs in request order | No; IDs are explicitly site-provided identifiers, not verified filenames or a preview |
| `NativeAfirmaConsentDialog`, local batch | Yes, from `batchItems` | No | No |
| `NativeAfirmaConsentDialog`, delegated/triphase batch | Yes, from `batchItems` | No | No |

Source trace (all under `app/src/main/java/dev/junta/firmamobile/`):

- `signing/BatchSigningCoordinator.kt` constructs `BatchSigningDocumentReview(request.documents)`
  for `SigningUiState.AwaitingConfirmation`.
- `signing/BatchSigningModels.kt`: review retains only bounded display IDs;
  normalized documents contain ID, data reference, format and suboperation, not
  verified document name/size/hash. The UI intentionally does not expose data references.
- `ui/SigningConfirmationDialog.kt`: `BatchSigningDocuments` shows count, a
  disclosure toggle, the site-identifier explanation and numbered IDs; it replaces
  single-document evidence, rather than mislabeling a batch as one document.
- `afirma/servlet/NativeBatchDescriptor.kt`: item IDs and data references are
  available internally, but are not per-document evidence in the consent model.
- `afirma/servlet/NativeMultiPhaseOperation.kt`: `details` forwards only item count;
  `payloadBytes` and `payloadSha256` describe the invocation payload/descriptor,
  **not each document**. They must not be presented as per-document evidence.
- `afirma/servlet/AfirmaConsentController.kt`: `AfirmaConsentDetails` has no item
  review list. `ui/NativeAfirmaConsentDialog.kt` renders count before the details
  toggle; its technical-details size/hash is payload-level. Finished batch receipts
  are after-the-fact results, not pre-sign document review.

## Behavioral coverage

Existing `SigningDocumentConsentTest.batchReviewRemainsSeparateAndNeverShowsASingleDocumentHash`
checks one sanitized ID, singular count and non-disclosure of the private reference.
The added multi-document Compose/Robolectric test verifies plural count, exact
numbered request order (not alphabetic sorting), visible explanatory text,
collapse/expand without implicit consent, no single-document bytes/hash, no raw
private reference and a working cancellation callback.

Added native Compose/Robolectric coverage verifies local and delegated batch
counts, count remaining visible when technical details are opened, replacement
of stale count/details on a new token, and no consent/unlock/cancel/dismiss
callback from reading details. These tests render composables and inspect their
semantics; they do not read Kotlin files as test data. They cover rendering of
prepared state, not the end-to-end preparation/controller/adapter pipeline.

## Gap and ownership stop

Task 11 is **not full implementation** of per-document review. Native consent
still lacks a pre-sign list even though parsed item IDs exist internally. Both
paths lack per-document name/size/hash evidence in the UI model. Closing this
requires production changes outside this task's tests/docs ownership; therefore
no production implementation is attempted. No expectation is weakened to call
the gap complete, and no test mandates preserving the missing native list.

## Follow-up PR stages (owner approval required)

1. **Native identifier list, no wire-format changes.** Extend
   `AfirmaConsentDetails` in `AfirmaConsentController.kt` with an immutable bounded
   display-only list populated by `NativeMultiPhaseOperation.details` from the
   parsed descriptor. Render count and a numbered expandable list in
   `NativeAfirmaConsentDialog.kt`. Reuse the existing `NativeBatchDisplayText`
   sanitization rules. Add strings in `values`, `values-en`, `values-ca`,
   `values-gl`, `values-eu`; never expose data references, URL queries or content.
   Keep receipts distinct from review; leave `BrowserScreen.kt` unchanged unless
   a separately owned integration change is actually needed.
2. **Evidence provenance design before implementation.** Inspect local-batch
   payload preparation, `BatchSigningCoordinator` and `BatchSigningModels` to
   establish where exact document bytes are available *before* consent. Define
   explicit unavailable/provided-digest/service-delegated states. Never hash a
   reference/descriptor/PRE digest and label it the document hash, never fetch
   remote references merely for display, never trust an ID as a filename.
   Implement only bounded immutable evidence bound to the same approved request;
   split protocol/security changes into separately reviewed work if required.
3. **Behavioral acceptance in CI.** Test local and delegated preparation through
   actual state producers as well as rendering: one/many/max-count batches,
   sanitized/blank/truncated IDs, stable order, unavailable evidence, real-byte
   size/hash where available, new-request reset, arming, lock/unlock != consent,
   cancellation and no leakage. Expand/collapse must never authorize signing.

## Validation and limitations

- Local: `git diff --check` only; manual source/diff review.
- No local build, compilation, Gradle, emulator, instrumentation or E2E executed.
- New Compose/Robolectric tests await automatic PR CI on the exact pushed SHA;
  no RED/GREEN runtime claim is made before CI. Existing tests were not run locally.
- No physical-device, real certificate, portal or administrative-flow acceptance.
- Repository Matt workflow documentation was read; `prepare_task` / the named
  Matt TDD skill were not exposed by the Chipula tool discovery. No substitute
  subagent/runtime bridge was launched.
- Remaining risk: identifier-only review is not document-content verification;
  descriptor-level native fingerprints may still be misunderstood. This audit
  records that gap rather than changing production outside ownership.
