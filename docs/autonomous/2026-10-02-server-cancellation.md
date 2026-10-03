# Explicit AutoFirma cancellation notification

## Outcome and boundary

Continuation of the verified fileid candidate `567ef2d84a1f0b01ede0cae93b451e7ef1eb8f2c` in PR338. At unsigned REVIEW, the user's explicit cancellation can publish the protocol sentinel `CANCEL` to the invocation's validated storage endpoint/session. It sends neither a signature, document nor certificate. It works with a locked or incompatible personal certificate, without unlocking it.

The caller does not issue remote cancellation automatically on timeout, stale ownership, backgrounding, disposal or signing failure. Those remain local actions. Once signature execution has started, the user can stop local work, but this slice will not publish CANCEL over a potentially delivered signature. Cancellation does not withdraw or undo a document already accepted by a public service.

The authoritative browser consumer was inspected at `ctt-gob-es/clienteafirma` commit `0d7f3cf01fb65d2be5b245622d2c8f490f36e718`, `afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js`, lines 5134–5141: literal `CANCEL` (also followed by LF/CRLF) is handled before result decryption. This notification is plaintext protocol data, not encrypted or Base64-wrapped; the HTTP connection still uses normal verified TLS. Existing storage implementation supplies `op=put`, `v=1_0`, `id`, `dat` and forbids retries/redirects. No new trust bypass or permission is required.

## Parallel implementation ownership

Three GPT-6.1 Sol Max workers are assigned actual code/test authoring, not reviews, in isolated Git worktrees from a shared interface commit:

- operation: NativeAfirmaOperation and a new operation/transport regression test;
- controller: AfirmaCancellationOutcome and its unit tests; after the larger controller task timed out, the orchestrator integrated this authored component into AfirmaConsentController and added coroutine/ownership tests;
- UI: NativeAfirmaConsentDialog, Spanish resources and a new instrumentation test.

The shared API is `PreparedAfirmaOperation.notifyCancellation(authorizeUpload)`, the phase `CANCELLING`, and explicit terminal results `CANCEL_ACKNOWLEDGED`, `CANCEL_NOT_SENT`, `CANCEL_REJECTED`, `CANCEL_UNCERTAIN`. The orchestrator owns BrowserScreen integration, regression validation, publication and device installation. Worker file hashes and tool activity are recorded separately from integration edits. Worker completion is not itself acceptance evidence.

## Required invariants

1. One shared terminal claim per native operation: a signature result and cancellation cannot both be uploaded, including races and uncertain network results.
2. Request cancellation is accepted only at REVIEW for the same live owner/epoch/token, while foreground and within its existing deadline. No certificate identity lookup is required for this action.
3. The final upload boundary rechecks ownership and cancellation state. Reentrant callbacks, parent cancellation before coroutine body, timeout and disposal release data exactly once and never initiate an implicit second request.
4. The notification has a bounded execution time and no retry. A response loss after authorization is UNCERTAIN, not a successful cancellation. A storage acknowledgement is not evidence of administrative withdrawal.
5. Existing local `cancel`, `dismiss`, `invalidate`, `expire`, `onBackground` and `close` must not silently acquire remote side effects. The browser explicitly routes a REVIEW user cancellation into the new path; all later phases retain local stopping.
6. Result receipts remain until explicit dismissal. The UI explains notification in REVIEW, displays progress while notifying, and only offers a local stop during CANCELLING. It does not expose repeated signing or notification actions.

## Verification

Use actual Kotlin controller and operation tests, a fake/isolated TLS storage server, UI-only synthetic prompts and existing Android integration tests. The full browser lifecycle test remains network-free and now explicitly uses background cancellation: the user-facing REVIEW cancel action has intentionally become network-capable. Broad acceptance runs on the exact integrated GitHub Actions SHA (unit/lint/APK, emulator, Python, Go, history secrets, OSV). A working UI and storage ACK do not prove every government portal handles CANCEL correctly.

Real personal certificates, private accounts, administrative requests, device VPN and unrelated Codex/proxy settings are outside automated test scope. The existing application is replaced only after verification, with the same Android signer, unchanged payload after signing, preserved package UID/first-install date and no uninstall/clear.
