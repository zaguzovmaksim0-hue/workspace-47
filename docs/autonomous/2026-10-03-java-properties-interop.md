# Java Properties interoperability in the shared AutoFirma engine — 0.2.2

## Scope

Continue the installed0.2.1 candidate in PR338. The operator requested independent implementation, no subagents, no E2E, and a verified in-place APK update. The existing PR338 verification:no-e2e label remains; five automated groups must pass and Android instrumentation must be explicitly skipped, not described as passed. Android test sources still compile. No browser, emulator, personal certificate, private account or real administrative operation is used here.

## Reproduced incompatibility

AfirmaServletInvocationParser.properties previously rejected every backslash and used a line regex plus trim on keys/values. This rejected normal Java Properties escaping, Unicode escapes and logical-line continuations, lost significant value whitespace, and did not support the standard key-with-empty-value form.

The original code failed seven of ten new request-level tests. The three passing controls covered ordinary properties and continued rejection of malformed/unsupported input. Failures included a real Properties.store-produced PDF metadata block, an escaped encodedcert selection, continued mode text, a retrieved descriptor and remote string values. Original source hashes and the complete red output are retained outside the repository.

The official pinned AutoFirma AOUtil.base642Properties decodes Base64 and loads a Properties object through a character Reader; its serializer uses Properties.store. The implementation should follow that grammar rather than reject its ordinary output. This does not authorize unsupported signing policies or arbitrary certificate filters.

## Decoder

NativeJavaProperties delegates the actual line/escape grammar to java.util.Properties.load(Reader). It retains the existing strict UTF-8/Base64 input decoding and16KiB input limit. It adds bounded64-entry/16384-decoded-unit checks, rejects duplicate names after decoding, rejects malformed Unicode, unpaired surrogates, empty names and unsupported control characters, and returns a separate insertion-ordered map.

Literal and escaped keys resolve before existing parameter allow-lists and certificate constraints are applied. For example, filters and filt\u0065rs refer to the same actual name; duplicate differently encoded names are rejected rather than selecting the last value. Unknown signature policies are still unsupported. No URL/form decoding is added, and '\\u0041' is not decoded twice.

Java Properties intentionally drops a backslash before a non-special letter. The old blanket-rejection test for mode=imp\licit is therefore moved to the successful-grammar cases; its unsupported-policy and malformed-value assertions remain. Significant trailing/escaped leading spaces are preserved rather than silently trimmed. Typed consumers continue to validate exact semantic values.

## Safe re-encoding for the remote three-phase route

Accepting an escaped newline requires fixing the outgoing path too. Previously that path joined the decoded key/value map using raw '=' and LF characters. NativeJavaProperties.encode now creates a deterministic escaped ASCII properties block without timestamps. Newlines, carriage returns, backslashes, key separators, comment markers, leading spaces and non-ASCII characters are serialized so that a normal server Properties reader reconstructs exactly the original map.

An embedded string such as a description containing '\nserverUrl=...' remains one description. It cannot introduce another endpoint or headless flag. The agreed service endpoint and explicit consent remain unchanged. PRE and POST share the same serialized params value; no second key use, extra request or retry is added. Both XML/JSON batch descriptors retain their original wire bytes; local batch items reuse the shared decoder.

Encoding expansion is bounded by at most six ASCII characters per decoded UTF-16 unit plus one '=' and LF per pair (98432bytes). This is an encoding bound, not a larger document limit or a relaxation of the16KiB inbound parameter limit. Control characters NUL/ESC and malformed surrogate sequences remain rejected; supported whitespace values are escaped for transport and may still be rejected by a typed local format policy.

## Tests

Ten request-level tests cover official Java serialization, Spanish/Unicode PDF metadata, continuation lines, exact selected certificate binding, retrieved inputs, empty values, duplicate normalized names and retained unsupported/malformed rejection. Eight codec tests compare against the JDK reader, exercise serialization without property injection, control/Unicode and exact size/count boundaries, and196 deterministic character combinations. Two native integration tests cover actual in-memory tri-phase PRE/PK1/POST with parameter-map equality and a mixed local CAdES/PDF batch with real output verification and metadata retention.

These are unit/component/in-memory tests, not E2E. Generated synthetic keys are used only in the existing test fixtures; no personal identity is loaded. The same focused suite and full Debug/QA unit, lint, build, policy and security gates must pass on the exact candidate SHA. The input descriptor and final upload authorization remain unchanged.

## Release and primary references

VersionName0.2.2, versionCode4; QA suffix retained. Preserve the installed Android signer, UID, first-install date, user data and previous APK backup. Perform the normal update only after code acceptance and a non-foreground check; reconcile actual installed bytes before reporting success. Do not merge main.

- Java Properties grammar and escaping: https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html#load-java.io.Reader-
- Official AutoFirma helper, pinned commit0d7f3cf01fb65d2be5b245622d2c8f490f36e718: https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-core/src/main/java/es/gob/afirma/core/misc/AOUtil.java

No upstream implementation is copied. Native signing cryptography, signature algorithms, certificate matching, server trust warnings and WebAuthn provider-authorization status are not changed by this compatibility fix.
