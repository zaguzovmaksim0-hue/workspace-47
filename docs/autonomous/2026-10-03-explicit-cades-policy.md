# Explicit CAdES signature-policy references — 0.2.7

## Outcome and operator scope

The operator requested continued core AutoFirma interoperability, not more catalog controls. Add standard fully supplied signature-policy metadata to local CAdES SIGN, COSIGN and the existing explicitly precomputed document-hash path, including local JSON batches. Preserve ordinary signing and remote service delegation. No subagents or E2E; use native/JVM component tests and the existing five exact-head CI groups plus the explicit PR338 instrumentation skip. Finish with a verified in-place APK update; do not merge main.

## Reproduction and protocol reference

The unchanged0.2.6 common parser rejects valid policy properties before consent. Three positive methods in an eight-method request suite reproduce signature_property_not_implemented; the five ordinary/malformed/unsupported controls pass. The source hashes and negative output are retained.

The official AutoFirma AdESPolicy contract accepts policyIdentifier, policyIdentifierHash, policyIdentifierHashAlgorithm and optional policyQualifier. CAdES encodes an explicit signature-policy identifier as a signed attribute (id-aa-ets-sigPolicyId,1.2.840.113549.1.9.16.2.15) containing the policy OID, policy-document hash algorithm/value and optional SPuri qualifier (1.2.840.113549.1.9.16.5.1). References are pinned/read, not copied as implementation.

https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-core/src/main/java/es/gob/afirma/core/signers/AdESPolicy.java
https://www.rfc-editor.org/rfc/rfc5126.html#section-5.8.1

## Bounded request contract

NativeCadesPolicy accepts a canonical numeric OID or urn:oid form, a supplied Base64 policy hash, a supported hash algorithm and an optional ASCII HTTP(S) qualifier without credentials. The OID is limited to256characters/32arcs/64digits per arc, the qualifier to2048characters; hash bytes must match SHA-1/256/384/512 length. Compact/hyphenated names and their OIDs map to the same algorithm. Policy hashing is independent of document-signature hashing: a SHA-512 policy hash may accompany a SHA-256 RSA signature.

No policy document is fetched. Missing hashes, the automatic-download sentinel0, URL policy identifiers that cannot be represented as an OID, malformed or partial metadata, unsupported hash algorithms and arbitrary additional signing properties are not silently dropped or downgraded to a signature without a policy. Empty optional qualifier is equivalent to omission, as in the official contract; an actual qualifier remains signed metadata only and is never visited.

The accepted object owns a private copy of the policy hash, produces independent encodings and has no generated toString exposing the reference/qualifier. It is immutable request metadata, not a system-wide policy, trust override, pin or external configuration source. Local PAdES/XAdES policy support is not introduced by this package; those typed consumers retain their restrictions. Remote service paths still forward their already accepted parameters under their existing service-trust disclosure.

## Generation and exact verification

The policy is inserted into signed CMS attributes before the RSA operation. Output acceptance requires exactly one signed policy attribute with the exact requested canonical DER contents. Identifier, hash algorithm, hash bytes and qualifier are compared together; a merely present attribute or a matching OID alone is insufficient. Ordinary no-policy generation retains an absent-policy expectation.

Existing CAdES sign/signDigest entry points remain for callers without a policy. New explicit methods share the same implementation with a supplied immutable policy. They preserve the software-versus-opaque-key provider behavior, certificate reference, content/digest validation, size limits and cleanup. No second key attempt, private-key export, algorithm substitution or endpoint change is added.

Co-signing preserves prior canonical signer records and their existing policies; only the new signer gets the newly requested policy. Policies are not automatically inherited or replaced. Different independently signed policy references can remain attached to their respective signatures. The current verified history/packaging/certificate-union and maximum-signer rules remain. Precomputed document hashes and policy-document hashes are distinct signed values.

Local batches use the same model for supported CAdES sign/cosign/prehash items. Malformed policies produce per-item errors before key use, with existing partial-result and stop-on-error behavior. The policy-request count shown before execution is not a promise that those items will succeed.

## Consent and limits of the claim

The existing consent dialog shows the requested policy OID, digest algorithm and supplied policy hash. It explicitly says the policy document was not downloaded or validated, and inclusion of a reference is not proof that every rule in the policy is satisfied. Batch requests have a corresponding notice. No new catalog button is added; headless metadata, unlocking a key and presenting a policy do not authorize signing or final upload.

This implements the explicit policy-reference signed attribute, commonly used by CAdES-EPES. It does not certify qualified-signature status, policy compliance, trust-chain/revocation status, legal effectiveness or government acceptance. It does not implement TSA/LT/LTA or automatically retrieve policies. A site requiring other unsupported attributes or formats can still fail with the existing explicit error.

## Validation

Eight request tests cover valid complete policies, OID/URN/qualifier, retrieved+precomputed requests and retained unsupported controls. Seven metadata tests independently decode the ASN.1 policy, exercise aliases, copy ownership, exact-reference matching, duplicate/missing attributes and bounds. Seven cryptographic tests cover all four RSA signature digests × four policy hash families × two content packaging modes, wrong/tampered policy references, old/new co-sign preservation, precomputed hashes, opaque keys and ordinary signatures. Six actual-operation tests cover final upload permission, cancellation/close, exact selected certificates, mixed policy batches and headless/unlock without consent. Three isolated JVM Compose tests cover policy disclosure and the unchanged explicit confirmation.

The existing full native suite is recompiled where defaulted request/consent model fields changed. Broad Debug/QA unit, lint, APK, Python, Go and security checks must refer to the exact final commit. Initial ASN.1/Kotlin collection and test-accessor compilation errors are retained as development evidence, not described as passing runs. No old negative test is removed to conceal a failure.

Independent fixture checks may verify the CMS with OpenSSL and inspect its signed policy fields; those establish syntax, cryptographic binding and preservation, not substantive policy compliance. Only generated test identities and synthetic documents/policies are used, with no real government calls.

## Deployment

Version0.2.7-qa/code9, preserving the previous Android signer, UID, first-install timestamp and user data. Verify exact CI artifact SOURCE_COMMIT/checksum/payload and non-foreground state before the normal package-manager update. Keep a backup, never clear or downgrade, and reconcile actual installed bytes before retrying after a lost response. Keep WARP, Codex and unrelated worktrees unchanged.
