# Batch outcomes bound to actual local signing contributions

## Reproduced gap

Continue the verified PR338 source `87da08c16e5f02dab835975042466b8c7f85efc0` without installing it or merging main. The existing JSON PRE-error binding catches a changed explicit error, but the XML PRE envelope can contain only the documents for which the server supplied pre-signatures. An omitted item has no explicit PRE error to bind.

A new test executes the real NativeMultiPhaseOperation with a two-document XML descriptor, one actual PRE/PK1 contribution and a POST claiming both documents are OK. On the unchanged baseline the operation returned ACKNOWLEDGED instead of UNCERTAIN. This was reproduced both in the native suite (206 tests, one failure) and the narrow four-test class (one failure, three positive controls). The baseline sources, compiled-artifact hash and both red outputs are retained outside the repository.

The POST may already have had effects. The correction must not claim nothing happened, retry it, alter a server response to manufacture a negative result or promise rollback.

## Evidence passed between phases

After NativeTriphaseCodec.sign has successfully created and self-verified all approved local PK1 contributions, the batch operation captures the set of document IDs represented in that signed session. It does so before POST and passes that set to final result validation for both XML and JSON remote batches.

A result claiming that a signature was created requires the ID to be in that set: OK, DONE_AND_SAVED, DONE_BUT_NOT_SAVED_YET, DONE_BUT_SAVED_SKIPPED, DONE_BUT_ERROR_SAVING and SAVE_ROLLBACKED. Negative/incomplete statuses that do not assert a generated signature can remain for an omitted XML PRE item; its actual server code/reason is retained rather than inventing one.

The ID evidence must be a subset of the approved descriptor and must not overlap explicit PRE-error IDs. Existing exact PRE-status preservation, duplicate/missing/foreign ID checks and raw wire-byte preservation remain intact. Multiple legitimate counter-signature targets for one document retain their separate signid values while contributing one document ID to the evidence set.

The low-level result parser can still be used without evidence for purely syntactic or local-result parsing. The real remote XML/JSON operation must always pass the freshly completed local contribution set. The final validation does not trust a signed-ID list returned by the server.

## Limits of this check

A locally produced PK1 is not proof that opaque PRE bytes represent the document the service claims, that the service saved the final signature, or that a public authority accepted a procedure. The existing delegated-service warning and consent still apply. This check only rejects a claim inconsistent with what this client actually signed. Service-side pre-existing or foreign-key signatures are not silently accepted as completion of this client's new batch operation.

Contradictions after POST return UNCERTAIN and do not forward an acknowledged browser-facing result. Honest partial XML failures still complete normally. Legitimately signed documents may report saving failure, rollback or other supported outcomes without being falsely labelled complete or automatically retried.

No signing primitive, private-key export policy, service endpoint, TLS setting, request body, credential, UI confirmation, runtime configuration or permission is changed. Original multi-document ordering, optional certificate envelope and explicit consent checkpoints are retained. No real account, certificate or administrative document is used in tests.

## Primary-source basis

The upstream reference is pinned to ctt-gob-es/clienteafirma commit `0d7f3cf01fb65d2be5b245622d2c8f490f36e718`. The XML BatchSigner parses the service PRE, produces local PKCS#1 contributions, then posts that native tri-data with the original descriptor. BatchDataResult enumerates saved, generated-but-not-saved, errors, skips and rollback as distinct states. The consistency guard is this application's additional validation; it is not presented as an upstream cryptographic attestation.

- https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-crypto-batch-client/src/main/java/es/gob/afirma/signers/batch/client/BatchSigner.java
- https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-crypto-batch-client/src/main/java/es/gob/afirma/signers/batch/client/BatchDataResult.java
- https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-core/src/main/java/es/gob/afirma/core/signers/TriphaseDataSigner.java

## Acceptance and deployment boundary

The deterministic real-operation negative test must pass after the fix, together with positive partial-failure, repeated signid and save-failure controls. XML/JSON status matrices and full exact-head GitHub Actions remain required. A test-only in-memory service does not establish acceptance by any live government server.

Earlier installation preparation was blocked by the tool. This continuation does not retry that action, create replacement deployment tooling or use another route around it. Source/CI acceptance and a report are separate from updating the installed e3ebc174 APK. Main stays unmerged.

## Test authorship

A GPT-6.1 Sol Max request was made for a status-coverage test class. Its connection repeatedly reported workspace-routing discovery timeouts and the bounded runner ended without any agent-message source or final output. It is not counted as accepted code or successful review. The integrator authored both production changes, the four real-operation regression scenarios and five XML/JSON status/evidence matrix tests. Earlier accepted project subagent files remain unchanged.

The matrix checks all12 final statuses both when a document has a completed local PK1 and when it has none, in XML and JSON. The24+24 combinations are test data, not48 separate JUnit methods. Other tests cover exact ID ordering, foreign/contradictory evidence and absent versus explicitly empty parsing context.
