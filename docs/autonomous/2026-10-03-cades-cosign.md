# Local CAdES co-signing in the shared AutoFirma engine — 0.2.5

## Outcome and verification scope

The operator requested improvements to core public-site signature capabilities, not more catalog controls. This slice adds local CAdES co-signature support to the common `afirma://cosign` route, retrieved descriptors and local JSON batches. Existing PDF co-signing and remote triphase operations remain. Work is performed by the primary executor without subagents or E2E; the normal five CI groups must pass on the exact head while the explicitly excluded PR338 instrumentation job remains SKIPPED with zero steps. Do not merge main.

The baseline generic parser rejected valid attached and detached CAdES input with `local_cosign_only_pdf`. Both positive request cases failed on the unmodified installed0.2.4 source; a malformed/unsupported negative control passed. The failure output and baseline source hashes are retained outside the repository. This is a missing operation, not a problem in a particular site's profile.

## Co-signature semantics

A new independent signer is added to the original signed-data envelope, rather than signing the existing signature file as an ordinary new document. The previous canonical SignerInfo structures, certificate set and encapsulated content are retained. The output must contain exactly the earlier signer records plus one new verified record; certificates are deduplicated by encoding, digest identifiers are maintained, and every resulting signer is checked again.

The original attached/detached packaging is preserved by default. An explicitly incompatible requested mode is rejected, not silently rewritten. Ordinary `sign` of a CMS file continues to sign those file bytes and never silently becomes `cosign`. Local countersign and XAdES co-sign are not introduced by this slice; remote service-based equivalents retain their existing behavior.

Attached CAdES contains the document: prior signatures and message digests are checked against those bytes, and the new signer may use any of the four existing RSA digest algorithms.

Detached CAdES omits the original: the client verifies all existing signatures over their signed attributes and their agreed messageDigest, then creates the new signature over that same verified digest. All previous detached signers must agree on one supported digest family and value, and the requested new algorithm must match it. Mixed-digest detached histories cannot be linked to the same absent document here and are rejected. No assertion is made that the original document was read or its bytes validated. The native consent paragraph explicitly explains that difference.

## Supported history and limits

- Ordinary signed-data content type `data`, with1–8 signer records and at most32 unambiguous X.509 certificates. Adding a ninth signer is rejected.
- Existing RSA SHA-1/SHA-256/SHA-384/SHA-512 identifiers and compatible RSA algorithm parameters only; no new algorithms or profile permissions.
- Each old signer must have unique signed-attribute names, a correctly sized messageDigest, correct content type and a matching SigningCertificate or SigningCertificateV2 reference. Optional issuer/serial references must match the embedded signing certificate. Duplicate signer records and conflicting signer certificates are rejected.
- Unsupported unsigned attributes, timestamp/archive/counter-signatures, revocation lists, ambiguous or foreign certificate choices are not silently stripped. Their processing remains outside this local path.
- Input512KiB, output2MiB, bounded iterative BER/DER pre-scan with32 container depth and12000 nodes before BC object construction. This is not an unrestricted hostile-input memory guarantee.

History validation occurs before the new private key is called. Software and opaque-key provider behavior from0.2.1 is reused. No private-key encoding, global provider registration, second signing attempt or external validation request is added. Verification establishes cryptographic consistency, not certificate trust, revocation, a trusted signing time, legal status or government acceptance. SHA-1 is retained solely as existing compatibility, not a recommendation.

## Native protocol and batch behavior

Direct and retrieved requests retain their operation identity, selected-certificate constraint, optional legacy/AES wire handling and final independent upload authorization. The result remains `certificate|signature` under the existing encoding/cipher. Cancellation uses the existing one-shot CANCEL path, never a signing key. No automatic request replay is added.

Local JSON batch items can co-sign attached/detached CAdES alongside ordinary CAdES/PDF/XAdES items. The existing per-item status and stop-on-error behavior remains: a failed co-sign item does not become a successful row or invent a saved signature. The batch review indicates CAdES co-signing and displays the same absent-original/integrity notice. It does not attribute local history verification to a delegated triphase service.

No extra user buttons or catalog/profile choice is required: the site requests the operation and the existing consent screen handles it. Unsupported new policy parameters are not ignored just to create a successful-looking response.

## Tests and evidence

New request tests reproduce admission of attached/detached CAdES; crypto/history tests verify all four supported digest families, multiple successive signers, preservation of old records/content/certs, tamper rejection, detached digest restrictions, ordinary CAdES certificate-reference variants, limits and opaque key behavior. Operation tests exercise the real native handler, final upload refusal, one-shot cancellation, retrieved operation identity and mixed local batch results with in-memory transports only. Iterative ASN.1 budget tests cover definite/indefinite containers, truncation, depth, nodes and trailing data.

Three isolated JVM Compose tests verify the native co-sign disclosure and existing unlock/confirmation behavior with recording callbacks. They do not launch MainActivity, a WebView, an emulator or a government website. The old PDF-specific rejection test now rejects PDF bytes mislabeled as CAdES instead of declaring all CAdES co-sign requests unsupported; its PDF, unknown-format and cancellation assertions remain.

Initial development compile/test harness failures are retained: a non-inline signature callback required explicit error propagation rather than a nonlocal return, and one JUnit method needed an explicit Unit return. These are not successful test runs. All final tests and GitHub Actions must be bound to the exact candidate.

## Primary references

CMS parallel signer/content structure: https://www.rfc-editor.org/rfc/rfc5652.html
BC1.85 detached hash-map verification API: https://downloads.bouncycastle.org/java/docs/bcpkix-jdk18on-javadoc/org/bouncycastle/cms/CMSSignedData.html

Official AutoFirma protocol/operation reference pinned at ctt-gob-es/clienteafirma commit0d7f3cf01fb65d2be5b245622d2c8f490f36e718:
- afirma-crypto-cades-multi/src/main/java/es/gob/afirma/signers/multi/cades/AOCAdESCoSigner.java
- afirma-crypto-cades-multi/src/main/java/es/gob/afirma/signers/multi/cades/CAdESCoSigner.java

Those sources describe using embedded content or a compatible existing digest and retaining old signer information. No upstream implementation is copied. Our prior-signature validation and bounded supported-history checks are additional application decisions, not evidence of official certification.

## Deployment

Version0.2.5, versionCode7, QA suffix retained. Require exact CI artifact/source checksum, existing Android signer, unchanged application payload after signing, a retained prior APK, non-foreground state and UID/first-install preservation. Install in place without clearing data or granting permissions. Do not open the app for E2E; rehash installed bytes and rerun focused native tests. WARP, Codex runtime, personal documents/keys and unrelated worktrees remain untouched.
