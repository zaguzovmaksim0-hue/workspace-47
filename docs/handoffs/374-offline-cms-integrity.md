# Issue #374: offline CMS integrity handoff

Base: `dfc4744599ab7fb39e2f9da385622480fe160acb`.

`OfflineCmsVerifier.verify(cmsBytes, detachedContent = null)` is an internal, offline integrity entrypoint.
`VERIFIED` requires every signer to verify; every result carries `INTEGRITY_ONLY` and `NOT_EVALUATED`.
No certificate identity, certificate bytes, content, digest, source, or exception text is returned.

Accepted input is canonical DER SignedData containing `data`, attached or detached.
Supplying detached bytes with attached content is invalid; null detached content differs from empty content.
The signed-attribute allowlist is content-type and message-digest; other signed attributes are unsupported.
RSA PKCS#1 v1.5 permits SHA-256/384/512; ECDSA permits SHA-256/P-256 or SHA-384/P-384 with exact domains.
Every SID must select exactly one embedded certificate. Duplicate signers and partial success are rejected.
Certificate expiry and self-signing do not confer or prevent integrity-only verification.

Limits are 1 MiB CMS, 512 KiB content, eight signers, 32 certificates, and DER nesting depth 32.
Caller arrays are copied; owned temporary byte arrays are cleared on exit.
BC/provider internals and JVM copies have no zeroization guarantee; these limits are not a hard CPU budget.

## Actual implementation checks

- Focused Kotlin/JVM17 `-Werror` harness with cached BC1.85/JUnit4.13.2: 21/21 tests passed, including after the detekt decomposition.
- Tests were applied before production and observed missing-API RED; production then GREEN.
- Checksum-pinned detekt1.23.8 on all six new production files: zero findings; baseline/configuration/suppressions unchanged.
- In a separate scratch copy, bypassing actual CMS cryptographic verification caused three expected failures (tampering, invalid signer in multi-signature set, incorrect signed digest). Unmodified source then passed21/21 again.
- No local Android Gradle, portal, device, real-certificate, APK, or instrumentation actions. Exact-head canonical CI and Security are required separately.

UI, SAF, callers, PDF/XML, certificate trust, revocation, TSA, and signing remain outside this slice.
This is a bounded integrity backend, not full signature validation or completion of issue374.
