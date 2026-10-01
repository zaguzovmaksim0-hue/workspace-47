# AutoFirma indirect configuration retrieval

Bounded continuation of PR338 from a33ec3ff8edb5b1350bd832ea37e8625531df039.

## Delivered behavior and separation

An actual ordinary-WebView AutoFirma sign/selectcert request may carry `fileid` and `rtservlet` instead of inline data. The application performs one bounded foreground configuration read, decodes the selected carrier, parses operation XML, validates all inner parameters, then creates a new explicit signing/certificate-disclosure review using the existing native operation. Retrieval alone never reads the selected personal identity, signs a document, submits the original browser form or grants consent.

`fileid` selects the downloaded configuration; `rid` (when provided) binds the expected inner result `id`. Optional outer `stservlet` must agree with the normalized inner storage URL, including routing path/query. Missing inner required values are not invented from outer hints. Operation mismatch, recursive retrieval, ambiguous mixed inline/indirect data and unknown signing constraints cannot silently change the operation. The outer cipher decrypts configuration; the inner cipher independently encodes results.

After a consuming read, unsupported/invalid content is not reopened using the original fileid. The UI explains that the portal must create a new operation. There is no hidden network retry button. Existing untouched unsupported initial requests retain their prior official-app fallback.

## Transport and bounds

- One form POST `op=get`, `v=1_0`, `id=<fileid>`. The request body is explicitly one-shot; no retry or redirect is followed, including `503 Retry-After:0`.
- Same public HTTPS/DNS restrictions as native result upload; no browser cookies, HTTP credentials or client certificate. TLS trust verification remains enabled.
- Response cap 2 MiB applies to actual bytes read through the response source, including transparent decompression. XML cap 1 MiB, decoded signing-data cap 512 KiB, 64 parameter entries, two element levels and bounded names/values.
- One trailing LF or CRLF from the servlet is removed from an encrypted ASCII response. No arbitrary byte trimming, second Base64 decode after AES, or DES downgrade after a configured cipher error.
- HTTP error, empty response, `err-` body, malformed cipher/XML and unsupported semantics are distinct terminal preparation failures. Failure does not promise the server kept its file.

## XML and trust

Input is strict UTF-8, optionally with a UTF-8 BOM. A simple `<sign>`, legacy `<op>` or `<selectcert>` root contains `<e k="..." v="..."/>` entries. XML syntax is parsed by the platform SAX parser. DOCTYPE/entity declarations are forbidden before parsing; external entity lookup is independently rejected. Optional platform feature flags are additional defense, not the only guard. Processing instructions, unknown elements/attributes, namespace ambiguity, duplicates, excess nesting and non-whitespace text are rejected. Safe formatting whitespace/comments are tolerated.

Attribute v is UTF-8 application/x-www-form-urlencoded and decoded exactly once. Attribute k retains its case and is not form-decoded. Decoded values go directly to the shared native parser, not through a reconstructed URI. Thus `%2B`, `%252B` and `+` retain their respective meanings.

A no-cipher envelope explicitly accepts raw XML over HTTPS as a bounded compatibility extension. This is not claimed to reproduce all official versions: the pinned launcher helper leaves its result null when no cipher is configured. It does not accept guessed Base64 plaintext or silently abandon a configured cipher.

## Lifecycle and ownership

The loading controller owns one owner/epoch/deadline-bound request and consumes its attempt before yielding. The same file capability is not read again within the same document/controller after success, cancellation, error or dismissal; the bounded in-memory attempt ledger resets for a new document or controller. It is not a durable cross-process replay journal.

Cancellation before coroutine entry, during the HTTP callback, across the parsing dispatcher or during the consent handoff closes the still-owned material. Closeable transport results use cancellation-aware continuation delivery. Decoded/parsed values remain reachable by cleanup even if prompt cancellation discards a dispatcher result. The consent handoff matches the existing consuming `offer` contract, including rejection.

Loading participates in native/JavaScript/TLS busy checks, navigation invalidation, WebView release and screen disposal. Background cancels preparation even for an expected external return. Foreground never restarts the same read. Preparation has its own maximum five-minute ownership window; a successfully prepared request receives the existing separate five-minute explicit-consent window. A late preparation cannot refresh its own expired window.

## Evidence and review

Three parallel GPT-6.1 Sol Max static reviews were requested for protocol, lifecycle and transport. Their proposals were checked against the concrete implementation and primary sources; they did not run tests or review the final commit a second time.

References are pinned to ctt-gob-es/clienteafirma commit 0d7f3cf01fb65d2be5b245622d2c8f490f36e718:
- ProtocolInvocationLauncher and ProtocolInvocationLauncherUtil: full XML parameter replacement and cipher handling.
- ProtocolInvocationUriParserUtil.parseXml: operation root and one form decode of value attributes.
- IntermediateServerUtil.retrieveData: actual POST form operation.
- RetrieveService.retrieveSign: consumed file deletion after read.
- autoscript.js buildXML/buildUrlWithoutData/cipherAndSendData: XML, fileid versus rid, DES/AES input carriers.

Sources remain external review evidence, not vendored GPL/EUPL implementation. Kotlin code uses the project's existing dependencies and runtime abstractions.

Acceptance requires focused actual Kotlin XML/parser/resolver/controller/transport and pipeline tests, synthetic Android SAX/Compose tests, existing browser regression tests and all six canonical GitHub Actions gates on the exact candidate SHA before an in-place QA update. The update must retain package identity, signing key and user data. No real personal certificate or administrative operation is an automated fixture.

## Remaining limits

This resolves indirect configuration for existing supported native CAdES/selectcert operations only. It does not implement batch/triphase/co-sign/counter-sign, XAdES/PDF/PAdES, arbitrary remote `dat` URLs, large-document handling, global Android intent registration or every specialized portal login. Cancellation is local and does not yet upload a CANCEL record. An acknowledged storage write is not government acceptance. Legacy DES consumer differences documented in the prior inline slice remain separate and are not hidden by an unverified global padding workaround.
