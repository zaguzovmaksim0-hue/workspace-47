# Sequential PDF approval signatures

## Outcome and protocol

Continue PR338 from installed `5616613bc21e31add9e0597b0fe9a4dd4580cddb`. A supported PDF with existing approval signatures can receive a new invisible signature without replacing its bytes or earlier signatures. The ordinary PDF sign route permits zero or more existing signatures; the explicit `afirma://cosign` route requires at least one. This is not a countersignature of another signer's CMS value.

The pinned official AutoFirma `autoscript.js` at commit `0d7f3cf01fb65d2be5b245622d2c8f490f36e718` implements coSign as `signOperation("cosign", ...)` and builds the encrypted configuration with the corresponding `<cosign>` root. Both the inline and retrieved paths preserve that typed operation and the existing endpoint/session/cipher binding. `cop` belongs to sign-and-save and is not invented as an alternative generic cosign route. CAdES/XAdES cosign, countersign and sign-and-save remain unsupported by this slice.

NativeAfirmaNavigation accepts this operation as protocol data, never as an executable Intent. Existing modern-main-frame GET, active view, consent, owner/epoch and final upload authorization apply. The UI labels explicit PDF cosign and distinguishes cryptographic integrity of earlier signatures from certificate trust or identity verification. Unlocking does not sign, headless does not hide confirmation and CANCEL remains a separate identity-free one-shot action.

## Preservation and validation

Before new private-key use, each existing signature must have a supported detached CMS, a single RSA signer and one matching embedded certificate. Supported OIDs are SHA-1/SHA-256/SHA-512 with their matching RSA signature identifier or rsaEncryption. Legacy SHA-1 verification is not a recommendation for new signatures. RSA-PSS, EC and other digests are not silently reinterpreted.

Existing ByteRange values must have exactly two ranges covering their complete revision outside one nonempty hexadecimal Contents gap. The revision ends at an actual EOF marker with a bounded whitespace suffix. Each signature is verified over that revision's bytes, not over the last file revision. The newest signature must cover the complete current input; unsigned trailing edits or a later unsigned incremental revision are rejected. Gaps and signature revision endpoints must be strictly ordered.

The newest document's signature fields are rebound to their own signed revisions: each historical prefix is reopened, its field name, range, exact CMS/certificate fingerprints, filter and signature metadata must match the corresponding entries seen from the final revision. A newest xref cannot silently rename or substitute an earlier signer/dictionary. Orphan signature dictionaries, duplicate fields, empty signature fields, DocMDP/FieldMDP/reference constraints, document permissions, encryption and XFA are not supported.

The new signature is added by an incremental external PDFBox save. The output must start with the entire unchanged input. Every prior verification record and signature must still be present and valid, with exactly one new signature. The new signature additionally uses the existing content/certificate/algorithm/options-bound native PAdES verifier. Existing endpoint confirmation and one-shot upload occur only after verification succeeds. Failure never falls back to uploading an unverified PDF or a new standalone CAdES value.

The implementation does not establish a trust anchor, current certificate status, revocation, trusted timestamp or legal effect for old signatures. A previous signature is a cryptographic statement about its covered revision, not proof every signer approved later revisions. Rendering tests are limited synthetic fixtures; preserving bytes is not a guarantee that arbitrary PDF readers display all unusual documents identically.

## Limits

Input remains at most512KiB and output2MiB. At most8 prior/new signature entries may be inspected in the final document; the input-size limit may be reached earlier because each native signature reserves space. A new operation must not exceed that total. Page/object/buffer bounds remain, without claiming a universal total-heap limit for hostile PDF parsers.

Existing blank-field selection, visible stamps, certified/locked/encrypted/XFA documents, document timestamps, DSS-only revisions, policies, TSA/LT/LTA and unsupported prior signature formats remain rejected. No general batch/triphase/XAdES or larger-file support is added here.

## Authorship and evidence

Two GPT-6.1 Sol Max code-output tasks completed successfully: NativePdfRevisionRange with eight pure tests and NativePdfSignerAlgorithms with four tests. Their exact returned source was reviewed and applied unchanged by the integrator. They did not run the acceptance suite. The history verifier, canonical protocol and browser/UI integration, native engine changes and combined tests were written by the integrator.

The initial local run found an actual ownership defect in the new history code: PDFBox exposes backing Contents bytes, and erasing that array during verification also erased the loaded dictionary. The verifier now copies that data before cleanup; a regression performs two inspections and verifies the original dictionary remains unchanged. The failed output is retained. An early test compilation lacked two synthetic-helper imports; that was corrected without changing production policy. Existing blanket rejection expectations for any signed PDF and the cosign XML root were replaced with damaged-signature/countersign rejection, while permission, XML entity, namespace, payload, endpoint and consent defenses remain tested.

Focused tests exercise real PDFBoxAndroid2.0.27.0/Bouncy Castle1.85 code with synthetic identities, including two different signers, three sequential approvals, mixed supported filters/digests, an independently constructed compatible CMS, tampering, unsigned append, field/document locks, limits and actual encrypted cosign descriptor retrieval. Android tests use generated non-exportable-wrapper keys and PdfRenderer, plus the actual unprofiled request-to-consent browser path. No private certificate, account, user document or live government submission is used.

All six canonical GitHub Actions gates must pass for the exact final SHA before deployment. Local tests and this specification are not claims that those broad checks or installation have occurred. Independently produced PDF fixtures can be checked by OpenSSL and Poppler; the final receipt records only actual performed checks.

## Primary sources

- Official AutoFirma coSign dispatch and XML builder: https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js
- PDFBox2.0.27 incremental external signing and one signature per loaded document: https://github.com/apache/pdfbox/blob/2.0.27/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java
- Bouncy Castle SignerInformation verification: https://downloads.bouncycastle.org/java/docs/bcpkix-jdk14-javadoc/org/bouncycastle/cms/SignerInformation.html

These references support the protocol/API choices, not certification of this implementation or government acceptance.
