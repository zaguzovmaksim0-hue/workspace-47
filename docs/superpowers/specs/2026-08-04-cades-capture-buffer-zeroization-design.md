# CAdES pre-sign capture buffer zeroization design

## Context

`CadesDetachedCodec.CapturingContentSigner` captures the DER-encoded signed
attributes that are later signed by the local private key. Its `close()` currently
calls `output.toByteArray().fill(0)` and then `output.reset()`.

`ByteArrayOutputStream.toByteArray()` returns a copy. Clearing that returned array
does not clear the stream-owned protected `buf`; `reset()` only resets the logical
length. A standalone JVM reproduction using a subclass that exposes `buf` retained
the exact canary after this sequence (`retained=true`). Therefore the current close
path leaves a temporary copy of the pre-sign input in heap memory until the stream
buffer is reclaimed or overwritten.

The existing CAdES functional tests all pass because this is a memory-hygiene
invariant, not a signature-correctness defect.

## Scope

Make one narrow behavior change: when the CAdES capturing signer is closed, clear
the actual `ByteArrayOutputStream` backing buffer before resetting it.

Do not change:

- CAdES structure, algorithms, signed attributes, or certificate contents;
- signature/certificate ownership exposed through `LocalSignature`;
- portal profiles, network/TLS/WebView behavior, callback encoding, or release policy;
- dependency or provider versions.

This is best-effort managed-heap zeroization. It is not a claim of physical RAM,
GC-copy, JVM-internal, or flash secure erasure.

## Root cause and working pattern

The defective pattern is:

```kotlin
output.toByteArray().fill(0)
output.reset()
```

The repository already uses the correct pattern in other sensitive stream owners
(`Pkcs12Loader`, `JuntaTriPhaseCodec`, and `ProfileHttpTransport`): subclass
`ByteArrayOutputStream`, clear the protected `buf` through an explicit `clear()` method, then
`reset()`. This preserves the inherited no-op `ByteArrayOutputStream.close()` behavior
for libraries that close a supplied output stream during generation.

Use that established pattern locally in `LocalCadesDetachedAdapter.kt`. The
capturer still returns an intentional `toByteArray()` copy from `signedBytes()`;
that copy is transferred into `PreSignResult`, which already owns and clears the
pre-sign bytes on consume/close.

## Test strategy

The private stream buffer is intentionally not part of a production API. A
repository source-policy regression in `tools/tests/test_ci_policy.py` therefore
pins the hygiene implementation without widening visibility only for tests. It
must:

1. reject `output.toByteArray().fill(0)` inside the CAdES source;
2. require `CapturingContentSigner` to own a `ClearingByteArrayOutputStream`;
3. require that clearing stream to call `buf.fill(0)` and `reset()`;
4. require `CapturingContentSigner.close()` to call the owned stream's explicit `clear()`.

On the current source this test must fail, establishing RED. After the minimal
production edit it must pass. Existing CAdES adapter and signer tests then confirm
that output compatibility is unchanged.

## Exact files

Test-first / policy:

- `tools/tests/test_ci_policy.py`

Production after RED only:

- `app/src/main/java/dev/junta/firmamobile/signing/LocalCadesDetachedAdapter.kt`

Evidence after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

## Acceptance criteria

- Generic JVM reproduction documents that the old pattern retains its canary.
- New source-policy regression fails on the unmodified production source for the
  exact old clearing pattern.
- Final source clears the owned stream `buf` before reset and no longer clears only
  a `toByteArray()` copy.
- Existing CAdES generation/validation tests remain green in Debug and QA.
- Full required Android, Python, Go, artifact, release-fail-closed, diff, and
  sensitive-content gates pass before commit/push.
