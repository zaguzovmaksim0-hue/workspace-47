# Native PDF signing without a per-site profile

## Scope and contract

Continue PR338 from installed `6e9e03b3efe6b314ad154b2e7f1916c0dc9a7e86`. The generic intermediate-server AutoFirma handler previously accepted only CAdES, while several exact profile adapters already supported PDF signing. This change adds a typed PDF branch to the existing native `sign` operation instead of copying a site's profile or treating PDF bytes as a detached CMS output.

Accepted format identifiers are PAdES, PAdES Detached and Adobe PDF (case insensitive, no implicit trimming). Inline data and the existing validated fileid/retrieval path share the same builder. The response stays certificate-first, result-second with the existing base64/DES/AES framing, but the result field now contains the complete signed PDF. No change to endpoint admission, request ownership, explicit certificate/signing consent, one-shot upload, cancellation or uncertain receipt semantics is made.

The supported first step is an invisible first signature of an unencrypted PDF up to512KiB; output is bounded to2MiB. Existing signature dictionaries or signature fields, document certification permissions and XFA are rejected rather than silently modified. The PDF parser limits stream buffering and rejects excessive page/object counts, but that is not a claim of a universal total-heap bound for every hostile PDF. No remote URL is fetched by the signing engine and no user key is exported.

## Properties and signature structure

The supported properties are mode (explicit/implicit), the PDF format aliases, signatureSubFilter (ETSI.CAdES.detached or adbe.pkcs7.detached), signReason, signatureProductionCity, signerContact, headless and signingCertificateV2=true. Metadata has a256-character bound and rejects control/bidi characters. Existing Java-properties-compatible parser normalization still applies before typed option checking. `headless` never suppresses the application's user consent. Other constraints, including visible appearance, selecting an existing signature field, a policy identifier, timestamp authority and permission to alter a certified PDF, are explicitly unsupported; they are not discarded.

The implementation uses the already-declared PDFBox Android2.0.27.0 and BouncyCastle1.85. It adds the signature as an incremental revision, retaining the original bytes exactly. A fresh invisible PDF signature dictionary carries Adobe.PPKLite, the requested subfilter and claimed time in `/M`. The detached CMS signs precisely the two covered byte ranges and binds the selected certificate via signingCertificateV2. RSA SHA-1/SHA-256/SHA-512 are the existing explicit requested algorithms; adding the PDF route is not a recommendation to choose legacy SHA-1.

For the PDF-specific CMS engine, signingTime is removed after BouncyCastle's default signed-attribute generation, because the default generator would otherwise reinsert it. The existing standalone CAdES behavior retains its signingTime attribute unchanged. No trusted timestamp, revocation evidence, qualified-signature status, trust-chain acceptance or complete independent PAdES conformance certification is claimed.

## Verification before upload

The result is reopened with PDFBox. It must contain exactly one expected signature, matching subfilter and metadata. Its ByteRange must cover the complete output except one even-length hexadecimal Contents string, without overflow or a gap hiding any original bytes. The original PDF is an exact prefix. The excluded bytes must be the same signature dictionary's Contents; only zero padding may follow the single CMS object. The CMS is independently verified against the original covered bytes, exact certificate, requested digest and detached mode before the result becomes eligible for the existing final authorization and one upload.

Cancellation before upload cannot send a generated PDF; an explicit unsigned cancellation still sends only the existing literal CANCEL without reading a personal key. Repeating an operation cannot send it twice. Cryptographic success is not acceptance by an intermediate server or government procedure.

## Testing and authorship

New tests execute the real PDFBox/CMS code with synthetic RSA identities whose private-key encoding accessor throws. They cover three algorithms and two subfilters, independent CMS verification, original-byte preservation, changed data/certificate/digest, appended bytes, malformed/encrypted/already-signed/certified/XFA documents, output limits, inline/retrieved aliases and the two-field result envelope. The old unsupported-format assertion now uses the still-unsupported XAdES rather than rejecting all PDF requests.

A first focused run exposed a test-fixture encoding mismatch: Java URLEncoder represented spaces as plus whereas the existing protocol parser deliberately preserves plus for base64 data. The test URI now percent-encodes spaces like encodeURIComponent; the production base64/parser behavior was not loosened. The expanded narrow run passes60 actual native tests, including unchanged CAdES and deferred-retrieval checks. The initial broad run still had one older retrieved-format assertion expecting every PAdES request to be unsupported; that assertion now uses XAdES, and an additional real encrypted-descriptor test verifies the new PDF request reaches the same typed operation after exactly one retrieval. It uses actual native code and PDFBox; only an Android logging stub and unused browser/network type placeholders support the plain-JVM environment. Broad Android acceptance remains the exact-head GitHub Actions gate.

Android instrumentation also signs a synthetic PDF with a non-exportable synthetic RSA wrapper and compares the original/signed page pixel-for-pixel using Android PdfRenderer. Separate checks verify the real unprofiled BrowserScreen displays a PDF consent request and retains the original page on local cancellation; no personal identity or external storage upload is used.

Two initial GPT-6.1 Sol Max implementation-output tasks timed out without code during CONNECT502 errors. Their production helpers are integrator-authored. A narrower model request emitted a complete NativePadesOptionsTest class containing eight tests in stdout, but its supervised CLI job timed out before producing the designated final-message file. That complete emitted source was inspected and applied unchanged by the integrator; its source hash and timed-out runner status are both retained, without claiming a successful runner exit or tests executed by the agent.

## Primary references checked on 2026-10-02

- AutoFirma1.9 integrator manual, PDF properties and subfilters (printed pages136 and138): https://desarrollo.juntadeandalucia.es/sites/default/files/2026-01/MCF-manual-integrador-ES.pdf
- ETSI EN319142-1 V1.2.1, Table1 (printed page19): claimed PDF `/M` time and absent CMS signingTime for baseline signatures: https://www.etsi.org/deliver/etsi_en/319100_319199/31914201/01.02.01_60/en_31914201v010201p.pdf
- PDFBox external incremental signing lifecycle: https://pdfbox.apache.org/docs/2.0.13/javadocs/org/apache/pdfbox/pdmodel/PDDocument.html

No upstream application implementation was copied. This documentation defines intended and tested scope; exact final CI/installation receipts are collected separately.

## Remaining work

This does not implement arbitrary existing signatures, co-/counter-signing, certified-document permission changes, visible stamps, policy/TSA/LT/LTA requirements, generic triphase/batch, XAdES or documents beyond current byte limits. It does not claim all portals accept every supported input or that a locally valid signature has legal/qualified status. Only synthetic test documents and identities are used during automated validation; main remains unmerged until separate acceptance.
