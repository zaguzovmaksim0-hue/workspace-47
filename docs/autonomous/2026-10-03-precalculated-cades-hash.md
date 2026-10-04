# Explicit site-provided CAdES document hashes — 0.2.6

## User outcome and scope

Extend core AutoFirma interoperability, not the catalog design. The existing generic sign route should understand the standard `precalculatedHashAlgorithm` input used when a site sends a previously calculated document hash instead of the whole document. Direct `afirma://sign` requests, retrieved configurations and local JSON batch items use the same typed path. No new site profile, catalog action or hidden authorization is added.

The operator requested no subagents and no E2E. Use local native/component tests, exact-head GitHub Actions unit/lint/build/security gates and the existing explicitly skipped instrumentation job for PR338. Finish with a normal verified in-place APK update. Do not merge main or alter WARP, Codex, personal credentials or unrelated worktrees.

## Baseline and primary contract

The installed0.2.5 parser rejects the property with signature_property_not_implemented. Three positive methods in an eight-method request suite reproduce the failure; the remaining ordinary-content and malformed/unsupported controls continue to pass.

Official AutoFirma CAdESParameters reads PRECALCULATED_HASH_ALGORITHM, treats supplied data as the document digest and omits encapsulated content even when mode=implicit is supplied. The relevant source is pinned to ctt-gob-es/clienteafirma commit0d7f3cf01fb65d2be5b245622d2c8f490f36e718. CMS messageDigest semantics are specified by RFC5652. No upstream implementation is copied.

https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-crypto-cades/src/main/java/es/gob/afirma/signers/cades/CAdESParameters.java
https://www.rfc-editor.org/rfc/rfc5652.html#section-5.4

## Typed admission and bounds

NativePrecalculatedHash recognizes the four already-supported digest families SHA-1/256/384/512, with their hyphenated and compact names ignoring case. Whitespace, unknown algorithms, RSA-PSS/EC/SHA3/MD5 and inferred hash modes are not accepted. The requested RSA signature digest must match the stated document digest; mixed hash/signature combinations are not silently rewritten.

Decoded input must be exactly20/32/48/64 bytes for its explicit family. Hex text or an additional Base64 layer is not guessed or decoded again. A normal document of those lengths remains an ordinary document when the property is absent. Existing UTF-8, Base64, URI, properties and endpoint policies remain in effect.

This feature is local CAdES SIGN only. Existing cosign and countersign operations are not reinterpreted as hash-sign operations, and PAdES/XAdES requests do not treat hash bytes as documents. Their unsupported parameter behavior remains. Existing service-delegated requests still forward their own parameters under their original trust warning; they are not claimed locally verified by this feature.

Both recognized explicit and implicit mode values result in detached CAdES under the official precomputed-hash contract. This is not a fabricated embedded document: the original is absent. The invocation constructor enforces local-sign/format/algorithm/size invariants and owns its existing copied payload buffer; close clears it.

## Signing and verification

The existing digest-calculator-based CAdES engine is reused. It places the explicitly provided digest directly in the CMS messageDigest attribute; it does not hash the digest as if it were the original document. The content stream must remain empty, the CMS is detached, and its certificate, algorithm, signed attributes and cryptographic signature are verified against the accepted digest before upload.

No raw signature oracle, private-key export, global provider mutation, algorithm fallback or new network call is added. The software/opaque provider selection, exact selected-certificate constraint, cancellation behavior, one-shot action and final upload authorization remain unchanged. A received hash is a site assertion, not proof of the absent original document's content or identity.

Local mixed batches can combine ordinary documents, supported PDFs and explicitly supplied CAdES hashes. Invalid lengths, unsupported operations and mismatched algorithms fail their own item before key use. Existing partial-result/stop-on-error/uncertain-delivery behavior remains. A batch warning counts requested hash items before work; it is not a promise that every item will succeed.

## Consent and privacy

The existing consent dialog explicitly distinguishes a direct received hash from a document: the digest family, byte count and absence of the original are disclosed before confirmation. The existing SHA-256 payload fingerprint is labelled as a fingerprint of received bytes, not misrepresented as the original document hash. Mixed local batches show a separate supplied-hash item warning; ordinary signing and delegated flows retain their existing notices.

No new primary buttons or catalog clutter are introduced. `headless=true`, unlocking a matching identity and receiving a digest do not count as consent. A denied send, closed request or attempted replay still prevents transmission. Metadata retains only the digest-family label/count; the existing owned payload stores the actual digest and clears on close. No query, hash, private key or document is added to diagnostic strings.

## Verification plan

Eight request tests cover modes/name variants/retrieval/lengths/unsupported operations/ordinary-data and delegated controls. Nine actual native operation tests cover all four digest families, exact messageDigest (not double hashing), independent original-content verification, opaque-provider signing, wrong-certificate binding, denied/closed authorization, request ownership, headless/unlock and key-free cancellation. Four local batch tests cover mixed formats, partial failures, stop-on-error and unsupported operations before key use. Three isolated JVM Compose tests cover the absent-document warning, correct size/fingerprint labels and unchanged explicit confirmation.

A known synthetic original can be retained only by an independent checker to verify the resulting detached signature, while the native request receives only its digest. Successful checking with that known fixture does not prove the original of an arbitrary future site-provided hash. No real portal/account, personal certificate or administrative document is used. The existing co-sign and ordinary-content suites must continue to pass.

## Release

VersionName0.2.6 (QA suffix retained), versionCode8. Preserve the Android application signer, installed UID/first-install timestamp, user data and a backup of0.2.5. Require five exact-head passing groups plus the explicit skipped-E2E receipt, verify SOURCE_COMMIT/checksum/payload identity, install only when the target is not foreground, then reconcile actual installed bytes. A lost command response must not trigger a duplicate installation. WebAuthn provider approval and real-government acceptance remain outside this tested scope.

The first full native pass found one historical assertion still expecting every precomputed-hash request to be Unsupported. Its malformed short-payload case is retained and now expects the precise invalid-length rejection; its unsupported policy, sticky and algorithm assertions are unchanged. The original failed output is archived. An initial new-test helper visibility compile error was corrected in test code only.
