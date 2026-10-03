# SHA-384 in the common AutoFirma engine / version 0.2.0

## Reproduced compatibility gap

Continue PR338 from source3795b6f52b19c38924c957d3af2466d0f7f21a7e. The user explicitly requested continued implementation and a final tested APK update. The existing shared engine accepted RSA SHA-1/SHA-256/SHA-512 but rejected an explicit SHA384withRSA request before consent. The actual baseline parser failed three of four new request tests: direct signing, batch descriptors and tri-phase signing. The incompatible-family negative control passed. No key, account or endpoint was used to reproduce those admission failures.

## Implementation

SHA384_WITH_RSA is appended to SigningAlgorithm, preserving the ordinal values of existing algorithms. The common request parser accepts the explicit JCA name; XML/JSON batch descriptor normalization also accepts its ordinary SHA384 abbreviation. No request is silently converted to SHA-256 or SHA-512.

CAdES, PDF/PAdES (including verification of a prior SHA-384 signature before another approval signature), all four supported XAdES packagings, raw local RSA and the generic PRE/PK1/POST path use the requested SHA-384 algorithm. Expected CMS digest OID is2.16.840.1.101.3.4.2.2; the combined RSA signature OID is1.2.840.113549.1.1.12. A generic rsaEncryption signature OID is accepted only with its separately checked digest. Cross-digest combinations remain rejected.

For XML, the digest URI is http://www.w3.org/2001/04/xmldsig-more#sha384, not an invented xmlenc#sha384 identifier. The signature URI is http://www.w3.org/2001/04/xmldsig-more#rsa-sha384. Existing certificate-reference digest choices are unchanged; they are distinct from the requested data/signature algorithm.

Legacy reviewed profile allowlists are not widened by adding the shared algorithm. Profile-bound SigningCoordinator rejects an unreviewed SHA-384 request explicitly; dedicated Junta and Sevilla contracts retain their existing algorithm restrictions. Exhaustive enum-to-JCA maps are updated so adding an enum value does not silently select another digest or cause a missing branch. Normalized native generic routes remain separate from profile authority.

VersionCode increases from1 to2 and versionName from0.1.0 to0.2.0 (QA suffix retained). This gives the updated package a visible version change rather than relying solely on an APK hash. Increasing the version does not prove that the APK has actually been installed.

## Verification plan and authorship

NativeSha384RequestTest checks direct CAdES/XAdES/PAdES, tri-phase formats and both batch descriptor formats, with PSS/ECDSA/ambiguous names still rejected. NativeSha384CryptoTest exercises real CMS, four XML packagings and mixed-digest sequential PDF signatures, altered-data rejection and no private-key serialization. Existing all-algorithm native tests automatically include SHA-384.

Two bounded GPT-6.1 Sol Max tasks produced accepted test source: NativeSha384OidTest and NativeSha384TriphaseTest. The OID output needed its SigningAlgorithm import added by the integrator; the tri-phase test calls were adapted to the actual suspend API using runBlocking after the compiler identified the missing coroutine context. Assertions and production behavior were not weakened. A third request for the crypto tests timed out without code; those tests were written by the integrator. Subagent source output is not a claim that the agents ran acceptance tests.

Android instrumentation extends the real software-key and AndroidKeyStore tests to SHA-384. The ephemeral test key permits both SHA-256 and SHA-384 and is deleted by the existing test cleanup. Personal keys or certificates are not imported. The full exact-commit GitHub Actions gates remain mandatory; local native tests are supporting evidence only.

The existing checks for certificate identity, user approval, page ownership, final send authorization, one-shot delivery, uncertain POST outcomes and verified PDF/XML/CMS output are retained. No TLS verification bypass, arbitrary signature policy, key export, provider-order mutation, new network endpoint or permission is introduced. RSA-PSS, EC, SHA-3 and other unsupported families remain outside this change.

## Primary references

- RFC5754, sections2.3 and3.2, SHA-384 digest and RSA signature identifiers: https://www.rfc-editor.org/rfc/rfc5754.html
- W3C XML Signature1.1, sections6.2.4 and6.4.2, SHA-384 and RSA-SHA384 algorithm URIs: https://www.w3.org/TR/xmldsig-core1/

The references define algorithm encoding; they do not establish certificate trust or acceptance by a government service. Synthetic tests and an HTTP response must not be represented as completed real administrative procedures.

## Deployment boundary

No application data may be cleared, no certificate identity changed and no unrelated Codex/WARP state modified. The final update is subject to the available tool's authorization and exact APK/source/signing verification. A rejected tool operation is not to be bypassed through another interface. Only a verified installed-package postcondition may be reported as installation; code/CI success alone is not deployment evidence.
