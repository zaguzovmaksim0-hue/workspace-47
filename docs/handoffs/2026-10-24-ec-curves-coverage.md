# EC P-256/P-384 signing coverage audit

Date: 2026-10-24. Task 10 only. Audited base: `07b8232d` (fresh `origin/main`).
Scope: signing JVM tests and this document; no production, dependency, importer, provider-registration, profile or security-policy changes.

## Conclusion and capability matrix

Internal software EC primitives exist, but they are **not runtime EC support**. The shared `SigningAlgorithm` API still exposes only RSA. Direct engine tests deliberately construct synthetic EC identities without going through PKCS#12 import. They must not be read as approval to import EC, enable TLS identities, widen profile algorithms, or claim acceptance by a portal.

“RSA” below means the existing supported contract, not arbitrary RSA parameters or every portal. “Internal” means offline software-key API only. The table describes source behavior; execution evidence for this candidate belongs to its exact-head PR CI, not to historical handoffs.

| Engine / surface | RSA | P-256 | P-384 | Signature representation / evidence |
| --- | --- | --- | --- | --- |
| `signing/JcaLocalSignatureEngine` | Yes | Rejected | Rejected | Raw RSA PKCS#1 v1.5 modulus-sized octets; `RuntimeSigningKeyCoverageTest` verifies SHA-256/384 RSA controls and EC rejection. |
| `signing/BouncyCastleCadesSigner` | Yes | Rejected | Rejected | CMS; `signature` OCTET STRING contains RSA octets, not DER `(r,s)`; new runtime matrix checks digest/signature OIDs and independent CMS verification. |
| `afirma/servlet/NativeCadesEngine` | Yes | Rejected | Rejected | Separate actual native CMS implementation; same RSA encoding. New matrix exercises both detached and attached packaging. |
| `afirma/servlet/NativeCadesCoSignEngine` | Yes, existing contract | Not enabled | Not enabled | Source-traced delegation to RSA-only `NativeCadesEngine` after `NativeCadesHistory` validation; no new co-sign coverage claimed. |
| `afirma/servlet/NativeXadesEngine` | Yes | Rejected | Rejected | All four packaging modes delegate signing to `JcaLocalSignatureEngine`; Base64 of raw RSA bytes in `SignatureValue`. New matrix exercises all four with valid XML, checks RSA signature length and independently verifies canonical SignedInfo bytes. |
| `afirma/servlet/NativePadesEngine` | Yes | Rejected | Rejected | Explicit RSA certificate gate, PDF ByteRange CMS via `NativeCadesEngine`; new matrix uses a valid synthetic PDF and RSA positive controls, not malformed-input rejection. |
| Portal CAdES codecs (`LocalCadesDetachedAdapter` / `CadesDetachedCodec`) | Profile-specific RSA | Not enabled | Not enabled | Source-audited RSA modulus-sized placeholder and RSA algorithm/OID mappings, not the software EC CMS candidate. Existing per-adapter tests remain authoritative. |
| Portal XAdES codecs (`LocalXadesDetachedAdapter`, Policia, Sevilla/ATSE, Airef/CDTI, Transportes) | Profile-specific RSA | Not enabled | Not enabled | Existing RSA requests, XML RSA algorithm URIs and raw RSA bytes; no ECDSA XML encoding path is introduced. Source audit only in this task; not a claim that every adapter was newly executed. |
| Portal PAdES / tri-phase / batch adapters | Profile-specific RSA | Not enabled | Not enabled | Server pre/post-sign protocol wraps the RSA raw engine; it is not a local EC PDF engine. Their exact origin/format/algorithm contracts are unchanged. Source audit only. |
| `signing/SoftwareEcSignatureEngine` | Rejected by EC policy | Internal: SHA-256 | Internal: SHA-384 | Canonical DER SEQUENCE of two INTEGERs. Existing curve-policy/negative tests plus new `EcSignatureEncodingCoverageTest`. No XML/P1363 API. |
| `signing/SoftwareEcCadesEngine` | Rejected by EC policy | Internal: SHA-256 | Internal: SHA-384 | Detached DER CMS, one signer/certificate, DER ECDSA inside signature OCTET STRING; algorithm parameters absent. New encoding tests inspect OIDs, independently verify CMS, and reject replacement by fixed-width XML bytes. |

Relevant production files are under `app/src/main/java/dev/junta/firmamobile/` with the paths above. Portal family registration is in `signing/RuntimeSigningAdapters.kt`. The PDF-history OID boundary is in `afirma/servlet/NativePdfSignerAlgorithms.kt`. Earlier scope decisions and broader negative-test inventory: [EC import boundary](2026-10-18-ec-engine-import-boundary.md), [raw candidate](2026-10-18-software-ec-raw-engine.md), [CMS candidate](2026-10-18-software-ec-detached-cades-engine.md). Their historical pass claims are not passes for this candidate.

## Algorithms, identifiers and encodings

| Context | SHA-256 | SHA-384 |
| --- | --- | --- |
| Digest OID | `2.16.840.1.101.3.4.2.1` | `2.16.840.1.101.3.4.2.2` |
| RSA JCA name | `SHA256withRSA` | `SHA384withRSA` |
| Combined RSA signature OID | `1.2.840.113549.1.1.11` | `1.2.840.113549.1.1.12` |
| EC JCA name | `SHA256withECDSA` | `SHA384withECDSA` |
| Combined ECDSA signature OID | `1.2.840.10045.4.3.2` | `1.2.840.10045.4.3.3` |
| Allowed internal curve | P-256 / `secp256r1`, OID `1.2.840.10045.3.1.7` | P-384 / `secp384r1`, OID `1.3.132.0.34` |
| Future XMLDSig ECDSA method, **not implemented** | `http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256` | `http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384` |
| XMLDSig ECDSA value, **not supported by runtime** | Fixed 32-byte r + 32-byte s = 64 bytes, Base64 encoded | Fixed 48-byte r + 48-byte s = 96 bytes, Base64 encoded |

A curve OID, public-key OID and signature OID are different things. EC SubjectPublicKeyInfo uses `id-ecPublicKey` (`1.2.840.10045.2.1`) with named-curve parameters; ECDSA CMS signature AlgorithmIdentifier parameters are absent. `SHA256withECDSA` / `SHA384withECDSA` name signatures, not curves.

The raw EC engine emits DER, with variable-length positive INTEGERs and possible leading sign-padding bytes (maximum 72/104 bytes). The CMS candidate preserves that DER inside its signature OCTET STRING. Fixed-width unsigned `r || s` belongs to XMLDSig and is **not interchangeable** with DER. The new test-only conversion pads each scalar separately to 32/48 bytes and deliberately substitutes those bytes at DER-only boundaries: both raw verification and CMS verification must reject them. There is no production conversion or new XML ECDSA support in this change.

Current CMS RSA generation with bundled BC 1.85 emits the digest-specific `sha256WithRSAEncryption` (`1.2.840.113549.1.1.11`) / `sha384WithRSAEncryption` (`1.2.840.113549.1.1.12`) signature identifier plus a separate digest AlgorithmIdentifier; the new RSA controls assert the exact matching combined OID. Native verification also accepts generic `rsaEncryption` (`1.2.840.113549.1.1.1`) with the separate digest, but rejects mismatched combined digest/signature identifiers. `NativePdfSignerAlgorithms` does not map ECDSA OIDs. Raw RSA engines have no ASN.1 AlgorithmIdentifier in their return bytes. XML uses RSA method URIs (`...#rsa-sha256` / `...#rsa-sha384`) rather than CMS OIDs. PDF embeds CMS, not XML raw `r || s`.

## Tests added and preserved

- `RuntimeSigningKeyCoverageTest`: 4 parameter rows × 5 methods = 20 JVM cases. Runtime-generated RSA-2048 controls with SHA-256 and SHA-384; named P-256/P-384 synthetic identities presented to unchanged RSA requests. Native CMS covers both content packagings and native XML covers all four. A failure must not return a signature; error taxonomy is asserted (`UNSUPPORTED_KEY`, except PDF's current `SIGNATURE_FAILED`). This tests the existing rejection boundary, not a newly representable runtime ECDSA request.
- `EcSignatureEncodingCoverageTest`: 2 curve/digest rows × 3 methods = 6 JVM cases. Canonical two-INTEGER DER, independent JCA/CMS verification, digest/ECDSA OIDs and absent parameters, wrong-content rejection, and rejection of correctly sized 64/96-byte raw scalar concatenations at the raw and CMS APIs. PDF-history OID mapping rejects both ECDSA identifiers, with matching generic/combined RSA OIDs as positive controls.
- Existing `SoftwareEcSignatureEngineTest`, curve-policy, EC CMS/boundary/verification tests, `LocalSignatureEngineTest`, `CadesSignerTest`, native engine and per-adapter tests remain unchanged. Existing positive EC CMS tests already assert digest and signature OIDs; this task adds the cross-engine/runtime boundary and cross-representation regression seam rather than replacing those oracles.
- All EC keys and certificates are generated in memory by `SoftwareEcTestFixtures`; RSA controls are generated in memory too. No private keys, keystores, PEM/DER fixture files, passwords, real documents or identities are saved or committed. These are software scalar tests, not opaque AndroidKeyStore/KeyChain/StrongBox evidence.

## Production gap and stop boundary

No confirmed production defect is fixed here. The unsupported runtime EC capability is an existing intentional boundary, not evidence that the internal primitives fail. Enabling EC would leave this task's tests/docs ownership; do not change the shared enum, importer, adapters or verification to make the matrix green.

A separately authorized follow-up should:

1. Choose one explicit local detached CAdES runtime capability and its key/curve/digest contract. Review callers in `afirma/servlet/NativeAfirmaOperation.kt`, `NativeCadesEngine.kt` and `signing/SigningModule.kt`; do not wire the software candidate into every format implicitly.
2. Add compatibility preflight before `SigningCoordinator` / `BatchSigningCoordinator` adapter preparation and network side effects. Keep profile, consent and origin contracts exact. Import and shared client-TLS implications require their own reviewed scope (`certificate/Pkcs12Loader.kt` and browser client-identity consumers).
3. Treat native CAdES/co-sign/history/PDF OID gates separately. Preserve all RSA acceptance/rejection and strict EC parameters; accepting an ECDSA OID alone is insufficient.
4. Design XML curve-aware DER-to-fixed-width conversion and its strict inverse in a separate PR, with independent XMLDSig validation, leading-zero/sign-padding, wrong widths, scalar ranges, changed SignedInfo and curve/hash mismatch tests. Never insert JCA DER directly into an XML ECDSA SignatureValue.
5. Evaluate EC PDF CMS and signed-history validation only after an explicitly approved PDF scope. Keep unknown signatures and policy combinations fail-closed.

Each stage requires behavioral negative tests, exact-head automatic CI, and separate operator-authorized device/portal acceptance where applicable. No compatibility upgrade or closure of the full roadmap is justified by these unit tests.

## Verification boundary for this change

Local: source/API review, ownership/status checks and `git diff --check` only. No local compilation, Gradle, assembly, instrumentation, E2E, emulator, real certificate or portal operation. JVM execution and broad integration are delegated to the existing automatic PR CI/security workflows on the final head. Pending checks must be handed to the coordinator; pending/skipped is not a pass. The draft PR records live run links and any failure disposition.

### Initial CI failure and test-oracle correction

[Automatic CI run 40085947244](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/40085947244) at initial head `b80a3eebd04ac2f5065b5e72d1d98d11ff9325e8` compiled the tests and reported 2,488 release unit cases, four failures. All four were new RSA standalone/native CMS controls: their assertion incorrectly expected generic `rsaEncryption`, while the artifact XML records the correct combined SHA-256/SHA-384 identifiers `.11` / `.12`. The existing production verifier explicitly documents BC 1.85's digest-specific encoding. All six new EC encoding/OID cases passed on that old head; that is not a final-head pass.

The correction retains strict equality to the appropriate digest-specific OID, digest checks and independent cryptographic verification. It does not remove an assertion, accept arbitrary RSA OIDs, alter production or widen verification. No unrelated/base test failure was reported in that release result. The amended candidate still needs its own automatic CI; no local JVM or compiler run was used to repair it.
