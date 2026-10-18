# Software-only raw EC engine candidate

## Boundary

Base: `98e4f9d679f5352e8a1eadba54d7527ccbcd8526` (fresh `origin/main`).
Branch: `feat/software-ec-raw-engine-20261018`.

This is an **unwired internal primitive**, not global EC support and not completion of #374.
No shared `SigningAlgorithm` changes or existing RSA production-source edits are included.
The historical `2026-10-18-ec-engine-import-boundary.md` design is not a capability declaration;
this candidate does not activate its runtime/import/TLS work.

## API and policy

- `SoftwareEcSignatureAlgorithm` has only `P256_SHA256` (`secp256r1`, `SHA256withECDSA`)
  and `P384_SHA384` (`secp384r1`, `SHA384withECDSA`). Arbitrary curve/hash strings are not an input API.
- Request ownership is immutable.
- Inputs: an X.509 certificate, a software EC key and a selected algorithm.
  Original bytes are passed separately to `SoftwareEcSignatureEngine.sign`.
- Private and certificate public parameters must both match the selected complete mathematical
  named domain: prime field/curve coefficients, generator, order and cofactor. An `EC` label or
  equal field size is insufficient. Equivalent explicit parameter specs are accepted.
- The source private key must implement JCA `ECPrivateKey`, expose explicit matching parameters
  and a readable scalar strictly between zero and the group order. Opaque, implicit-parameter,
  RSA, cross-pair and other-curve sources fail closed before signature dispatch.
- Already-readable numerical material is snapshotted through `ECPrivateKeySpec` and
  `ECPublicKeySpec` into the isolated bundled BC key factory. No source-key encoding is requested.
  This is not a hardware/AndroidKeyStore/KeyChain export or fallback path.
- There is one signing attempt through the existing `BundledBouncyCastle.provider`; it is not
  installed, replaced or reordered in `Security`. Exceptions do not trigger another provider.
- Output is a canonical DER sequence of the two ECDSA integers, never IEEE P1363 or XML.
  A fresh BC JCA verifier checks the generated signature against the supplied certificate's
  captured public key and original bytes before success. Tests also verify with a separate
  default JCA provider. Internal `verify` exposes the same bounded DER-verification boundary.

## Bounds and ownership

- Input: zero through **524,288 bytes**, inclusive; empty messages are supported.
- DER output: at most **72 bytes** for P-256 or **104 bytes** for P-384, also capped by the
  engine output limit. Constructor overrides may only narrow the hard input/output bounds.
- Input is size-checked before copying. Verification also checks signature size before copying.
  Temporary byte copies and failed generated signatures are cleared; returned successful DER
  bytes belong to the caller. Source keys and caller-owned input/signature arrays are not cleared.
- Failures expose only the small typed error enum (or `false` for verification), not key material
  or provider exception details.

## Focused local evidence

Only existing cached tooling/dependencies were used: Kotlin JVM compiler targeting Java 17
with `-Werror`, BC 1.85, JUnit 4.13.2 and Hamcrest 1.3. These are supporting pure-JVM results,
not the canonical broad Android/Gradle/CI integration gate.

- 33 tests pass across `SoftwareEcSignatureEngineTest`, `SoftwareEcSignaturePolicyTest` and
  `SoftwareEcSignatureVerificationTest`; compiler diagnostic output is empty.
- Both allowed pairs, BC and standard JCA software-key sources, independently JCA-verified DER,
  every private/public domain component, other curves (including same-size domains), RSA/EC
  mismatches, opaque/missing/out-of-range scalars, implicit parameters and invalid public points.
- Wrong same-curve certificate, altered input/signature, malformed/non-canonical DER, P1363,
  cross-curve/hash requests, wrong hash verification, empty/max/oversized input and output caps.
- Zero source private-key encoding reads, one source-scalar snapshot, unchanged global provider
  object/order, owned original-byte snapshot, and a failing entropy source with no signing retry.
- Scoped cached detekt 1.23.8 passes on the three new production files without a baseline or
  suppression. The executable checksum is the repository pin:
  `2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`.

Three negative controls were executed in separate temporary source copies outside the worktree:

| Deliberate defect | Observed focused result |
| --- | --- |
| Replace exact domain matching with field-size-only matching | 33 tests run, 3 assertion failures |
| Skip generated-signature verification | 33 tests run, wrong-certificate assertion fails |
| Retry through the default provider after signer failure | 33 tests run, no-retry entropy assertion fails |

Each defective copy exited 1 with the intended JUnit assertions, not a compilation error.
None of these changes is in the candidate.

Local harness and detailed logs: `~/.cache/w47-ec-raw-engine-tests-20261018/`.
Only synthetic keys/certificates are generated in memory; no private credential fixture is saved.

## Remaining work, explicitly not activated

No runtime caller, CMS/CAdES, PDF/PAdES, XML/XAdES, parser, bridge, batch, portal/profile,
origin/catalog, certificate import/unlock policy or TLS/client-authentication path is enabled.
No certificate expiry, chain trust, import authorization or portal-acceptance claim follows from
this primitive; those remain separate caller policies and future evidence.

No Gradle/Android build, emulator/device, E2E, real certificate, authenticated portal, permanent
signing key or final APK action was performed. No dependencies, workflows or detekt baseline
were changed. Broad integration checks remain pending a separately authorized publication.
