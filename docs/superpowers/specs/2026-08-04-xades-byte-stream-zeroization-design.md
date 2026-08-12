# XAdES temporary byte-stream zeroization design

## Finding

`XadesDetachedCodec.serialize()` and `canonicalize()` both use ordinary
`ByteArrayOutputStream` instances and return `toByteArray()` copies. Closing an
ordinary `ByteArrayOutputStream` is a no-op for its protected backing `buf`, so a
second copy remains in managed heap until garbage collection.

A standalone JVM probe wrote an XML canary, called `toByteArray()` and `close()`,
and observed both the returned copy and the stream backing buffer still containing
the canary. The current XAdES source has the same ownership pattern at the two
stream sites.

This matters because `serialize()` can contain the complete unsigned/final XAdES
XML and `canonicalize()` can contain the signed document content, SignedInfo,
SignedProperties or KeyInfo. These are not private-key bytes, but they can contain
administrative document data, certificate material and the final signature.

## Scope

Production file:

- `app/src/main/java/dev/junta/firmamobile/signing/LocalXadesDetachedAdapter.kt`

Regression policy:

- `tools/tests/test_ci_policy.py`

Evidence updates after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

## Design

Introduce one private XAdES-local `ClearingByteArrayOutputStream` matching the
already-established project pattern: `clear()` fills the actual protected `buf`
with zeroes and then calls `reset()`. Do not override `close()`, because stream
lifecycle owned by XML libraries must retain ordinary `ByteArrayOutputStream`
close semantics.

For `serialize()` and `canonicalize()`:

1. allocate the clearing stream;
2. perform the existing transform/canonicalization unchanged;
3. obtain the intentional returned `toByteArray()` copy;
4. clear the backing buffer in `finally` before returning or propagating an error.

The returned copy remains owned by the existing caller and is cleared by the
existing XAdES/signing lifecycle where applicable. The change is best-effort
managed-heap hygiene only; it does not claim physical RAM secure erasure or erase
immutable XML/Base64 strings held by DOM/JCA implementations.

## Non-goals

- No XAdES algorithm, namespace, canonicalization algorithm, digest, signing time,
  certificate chain, callback, profile or portal compatibility change.
- No new dependency or toolchain change.
- No change to certificate/private-key storage or lifecycle.
- No attempt to zero JDK/XML-library internal copies that the application does not
  own.

## Verification strategy

TDD starts with a source-policy regression that fails while either XAdES byte
stream site still uses ordinary `ByteArrayOutputStream` ownership and requires a
private clearing stream plus explicit `finally` cleanup in both helpers. After RED,
apply the minimal production change and run focused Python policy plus XAdES JVM
functional tests in Debug and QA. Then run the full relevant Android/Python/Go,
artifact and release fail-closed gates, inspect the complete diff, run
`git diff --check`, and scan for sensitive or unsafe unrelated changes before an
atomic commit and push.
