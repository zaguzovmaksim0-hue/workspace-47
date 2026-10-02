# Bound batch outcomes and per-document result details

## Scope

Continue the installed general-signatures candidate `e3ebc17477285ed017051535fda0f302a057be83` in PR338. This slice improves the existing local/remote batch and tri-phase paths, without another site-profile integration, a new signing format or relaxed origin/consent rules.

The native batch screen previously exposed only aggregate counts. A collapsed “Ver resultados por documento” action now shows up to32 approved document identifiers in descriptor order, the exact protocol status plus readable meaning, and an available bounded explanation. Local results are distinguished from remote-service reports. Outcome status is not delivery acknowledgement or evidence of administrative acceptance. The finished view has no retry/sign action; closing it only dismisses the view.

## Reproduced correctness defect

A real NativeMultiPhaseOperation test constructed a two-document JSON batch whose PRE rejected one document with ERROR_PRE. The POST response then falsely changed that document to DONE_AND_SAVED. On the unchanged production baseline, the operation accepted and forwarded the contradictory result as ACKNOWLEDGED; the added regression failed expecting UNCERTAIN (180 tests, one failure). The source hashes and full red output are retained in the task's red-proof.json.

POST results are now checked against the immutable set of PRE failures. A failure cannot silently become success, another error category, or an unrelated document's result. Duplicate/missing/foreign identities remain rejected. If a completed POST returns a contradiction, the result is not forwarded to the browser-facing servlet; the state remains UNCERTAIN because the service may already have performed other batch side effects. The operation cannot run again automatically. This does not promise rollback or absence of remote side effects.

A matching PRE failure's explanation is retained for display when POST omits it. XML reason/description fields are also retained, subject to the existing bounds and unambiguous field names. Outgoing accepted wire bytes are unchanged; display normalization does not rewrite IDs, XML, signatures or signing inputs. Duplicate reason/description elements are rejected instead of silently selecting one.

## Display and lifecycle

NativeBatchReceipt owns an immutable snapshot. Its rows are bound to the whole approved descriptor, reordered to the user's original item order and cannot be changed through a retained mutable source list. It stores no signature payload, certificate, private key, endpoint secret or original document bytes. The snapshot is captured before operation cleanup, presented only in FINISHED state, and released with its containing request when dismissed. No persistent batch journal, filesystem export or automatic clipboard copy is added.

NativeBatchDisplayText is for presentation only: bounded Unicode code points, no split surrogate pair, collapsed whitespace/control characters and removal of formatting/bidi controls. It keeps plain text rather than executing markup. A long description is visibly ellipsized; raw wire data is not modified. Document identifiers or descriptions may themselves contain private information supplied by a site, so they are never inserted into toString/log output.

The source notice distinguishes a local operation from a remote-service batch, including client-generated skipped outcomes. A returned DONE_AND_SAVED/OK status does not mean the browser received the final result, and is not independently verified government acceptance. An UNCERTAIN delivery remains visibly uncertain alongside whatever validated per-document report was obtained. Malformed/contradictory responses do not obtain a misleading partial-success snapshot.

## Tests and authorship

Two GPT-6.1 Sol Max code-output tasks completed successfully: the Unicode display helper with six JUnit tests, and the expandable result component with three instrumentation tests. The integrator aligned resource names, added readable status meanings and adjusted the relevant UI expectation. Original and integrated hashes are stored separately; the agents did not execute the acceptance suite. The immutable model, PRE/POST binding fix, controller/native-operation wiring and coupled tests were written by the integrator.

Tests cover actual baseline failure reproduction; PRE-to-POST contradictions; immutable ordering and privacy-safe diagnostics; XML explanations; lost-delivery semantics; cleanup-before-render; no repeated execution; supplementary Unicode/limits; and the actual finished consent dialog with all32 rows, scrolling and unchanged close/cancel/sign controls. Existing native PDF/CAdES/XAdES, general batch/tri-phase and cancellation tests remain in the focused regression run. Broad Android/Gradle, native instrumented UI, Python/Go and security acceptance must pass on the exact candidate SHA before installation.

## Primary references and explicit limits

Canonical reference sources already pinned for the general-signatures implementation remain under its reference/ directory at AutoFirma commit `0d7f3cf01fb65d2be5b245622d2c8f490f36e718`, including JSONPreSignBatchParser, BatchDataResult and JSONBatchInfo. The review checked their treatment of earlier failed items as status-bearing rows, rather than new signable input. This change does not copy upstream implementation code.

- AutoFirma source: https://github.com/ctt-gob-es/clienteafirma/tree/0d7f3cf01fb65d2be5b245622d2c8f490f36e718
- Credential-provider browser approval remains an external condition, not a code flag: https://developer.android.com/identity/sign-in/privileged-apps

WebAuthn provider approval, real passkey login, advanced XAdES policies/TSA/LT/LTA, arbitrary proprietary batch statuses and real government workflows are not claimed by this slice. User keys, accounts, WARP, Codex settings and the original working directory are not changed. The six exact-head gates, same-signer in-place update and installation postcondition checks remain required. Main is not merged.
