# Native AutoFirma inline protocol slice

## Scope and implementation

Continue PR338 from verified commit ef1f00f32d420f9c866f6f5d562fb757eee80c04. In the ordinary WebView, a live top-level GET navigation to `afirma://sign` or `afirma://selectcert` (including the precise Android `intent` envelope) is parsed as data. It does not execute page-provided intent extras or grant a profile/JavaScript bridge new trust.

This slice supports direct inline CAdES attached/detached with RSA SHA-1/256/512, selectcert, explicit storage URL/session ID, plain Base64 or the supplied legacy DES/AES carrier. Sign operations self-verify original content, digest, certificate and CMS attributes before delivery. A single approved operation performs at most one storage POST. Storage `OK` is an acknowledgement of transfer, not proof that a government service accepted a document.

Unimplemented indirect/fileid retrieval, batch, co-sign/counter-sign, tri-phase, PDF/XAdES and unsupported semantic constraints produce a bounded unsupported dialog with an explicitly chosen official-app fallback. No downgrade from an unrecognized advanced cipher to DES is permitted. Ordinary viewing and TLS login remain separate operations.

The consent controller owns an immutable description and a closeable payload. Owner, navigation epoch, monotonic lifetime, foreground state and displayed certificate fingerprint are checked before execution and again at the upload boundary. LAZY start, terminal-state idempotency and pre-body cancellation cleanup prevent reentrant/canceled work from reappearing. A canceled in-flight upload remains UNCERTAIN until explicit dismissal; it is never automatically retried. The unsupported dialog has its own owner/epoch/token/deadline, not a loose remembered raw URI.

## Source provenance

Project-native Kotlin is implemented with existing project abstractions and declared JCA/Bouncy Castle/OkHttp dependencies; no upstream Java/JavaScript implementation is vendored in the application. Official sources were inspected as interoperability references, pinned to `ctt-gob-es/clienteafirma` commit `0d7f3cf01fb65d2be5b245622d2c8f490f36e718` (2026-08-22). Their GPL/EUPL notices and rights are not reclassified as this project's license.

Relevant reference paths include:
- `afirma-core/.../protocol/UrlParameters.java`, `UrlParametersToSign.java`, `UrlParametersToSelectCert.java`;
- `afirma-simple/.../protocol/ProtocolInvocationLauncherSign.java`, `ProtocolInvocationLauncherSelectCert.java`, `IntermediateServerUtil.java`;
- `afirma-signature-storage/.../StorageService.java`;
- `afirma-crypto-cipher2/.../ciphers/DesServerCipher.java`, `AesServerCipher.java`;
- `afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js`;
- `afirma-crypto-cades/.../CAdESParameters.java` and `afirma-core/.../AOSignConstants.java` for the explicit/detached default.

Cipher numeric golden vectors were generated from the pinned public reference and independently from WebCrypto, not from the tested Kotlin encoder. Synthetic keys and documents are intentionally non-operational.

A compatibility caveat was reproduced in the pinned legacy JavaScript consumer: `decipherDES` removes an additional eight bytes from the last response field, whereas `DesServerCipher.cipherData` and the raw JS DES primitive use only declared block-alignment padding. This slice does not silently add a global extra block to the wire format or claim to fix every site's private library consumer. AES and the supported Java framing are tested; actual affected-site acceptance remains unverified.

## Delegated review and verified corrections

Three concurrent CLI reviews were explicitly configured as `gpt-6.1-sol` with `model_reasoning_effort="max"`: protocol, execution and browser integration. All returned results. They are static analyses, not executed tests. Findings were checked against actual sources and turned into controller/parser/fallback regressions; unsupported assertions were not treated as executed evidence.

Corrections include: pre-body scope cancellation cleanup; no abandoned WORKING state after reentrant owner changes; retained terminal receipts; owner-bound official fallback; controller-matched disposal; case-sensitive semantic parameter names; valid JSON Unicode escapes; digest-bound BC 1.85 RSA signature OIDs; and a one-shot OkHttp body to prevent even a `503 Retry-After:0` follow-up from repeating an upload.

## Acceptance and limits

Required: the actual protocol/cipher/controller/transport/pipeline JVM tests; Bouncy Castle content/certificate/digest verification; local trusted TLS fixture tests with one POST and no implicit retries; negative ownership and cancellation cases; real Android dialog tests; existing JS interoperability tests; and all canonical GitHub Actions gates on the exact candidate SHA before an in-place QA replacement.

Real personal certificates, government accounts and administrative submissions are not test fixtures. A successful synthetic test or storage acknowledgement is not evidence of universal portal compatibility. Pending results, parameters, signing bytes, URI keys and sessions are not written to diagnostic logs. Existing unrelated worktrees, user data, Codex, CONNECT proxy, VPN and time-zone settings remain outside this change set.
