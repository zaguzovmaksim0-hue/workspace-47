# Exact certificate selection in the common AutoFirma path

## Compatibility gap and reproduction

The official AutoScript client can pass `filters=encodedcert:<base64 certificate>` with `headless=true` when a workflow requests the same certificate again. EncodedCertificateFilter compares the whole encoded certificate, not merely its subject, key algorithm or public key. The current generic native parser rejected this supported selection form before consent with certificate_filter_not_implemented.

A regression uses the real parser and native operation with generated synthetic certificates. On unchanged source e35abcee50c5bee1dbcf50f6171fc3231e087b81, two bound-certificate cases failed before reaching selection/signing while an ordinary unfiltered control passed. The complete focused run was214 tests with two failures; the independently repeated three-case class reproduced both rejections in6.677seconds. Source hashes and red outputs are retained outside the repository. This is a compatibility failure, not evidence of a live government transaction.

## One shared request-local rule

The parser accepts one `filter` or `filters` property containing exactly one encodedcert value. It validates bounded base64, exactly one X.509 DER certificate, full input consumption and a canonical certificate encoding matching the received bytes. Concatenated/truncated/PEM-wrapped certificates, unknown selectors, multiple/numbered filters and conflicting aliases are not silently interpreted.

The invocation owns a private copy of the required public certificate, scoped to that request. The source parser releases its copy. Comparison uses the full encoded certificate, so another certificate with the same public key or similar subject is not an interchangeable identity. Closing the invocation clears its owned bytes. The certificate and its base64 are excluded from diagnostic toString output; no selector is saved to a global sticky cache, clipboard or persistent log.

Client-selection properties are handled separately from document-signature properties. Strict boolean headless is understood as client metadata, but it does not suppress the application's native review, unlock or final send confirmation. Unknown signature requirements remain unsupported; no parameter is discarded merely to force a signature to succeed.

The same certificateCompatible check is enforced by local CAdES/PAdES/XAdES and selectcert, as well as the remote/local batch and single triphase operation. The multi-phase path rechecks it before service exchanges and between phases. A mismatching current identity cannot send even its public certificate through this request. Client-only filter/headless fields are not forwarded as server signature parameters. Supported top-level batch certificate selection does not add arbitrary per-item filters.

## Consent and UI

The genuine consent details carry only a boolean indicating that the site requires one exact certificate. The dialog explains this and retains the distinction between unlocking a key and authorizing a signature or result transfer. A wrong unlocked identity remains INCOMPATIBLE; unlocking the matching one reaches REVIEW and does not itself execute the operation. Existing owner/epoch checks, token replacement, cancellation, single-use upload and uncertain-delivery rules remain unchanged.

This is not implementation of sticky PIN caching, arbitrary subject/issuer filters, provider selection, keystore hints or a claim that every caller's headless automation is performed. Applications using other selection modes continue to receive explicit unsupported behavior, rather than silently accepting another identity. Certificate trust, revocation and qualified-signature status are not inferred from the page's requested certificate.

## Verification boundaries

New native tests cover exact selection/CAdES result delivery, rejection of another identity before any upload, the unchanged unfiltered path, wrong-identity rejection before remote PRE, matching PRE/POST flow, all local format dispatch, a local batch, and real controller unlock/review behavior. Parser tests cover aliases, standard/URL-safe base64, malformed/concatenated/truncated inputs, strict boolean metadata, unknown requirements, retrieved parameters, independent copies, close and diagnostic privacy.

Android tests use generated certificates and the actual platform CertificateFactory/operation path. Two certificates share one public key but have different DER encodings; only the exact requested certificate is accepted. The real dialog shows the requested-certificate notice and a locked-key action without an automatic confirm or send. All fixtures remain synthetic: no user PKCS#12, KeyChain identity, passkey, authenticated portal or administrative document is used.

A GPT-6.1 Sol Max request for parser tests timed out after repeated workspace-routing discovery failures without emitting source. It is not counted as accepted code or review. Production changes and tests were written by the integrator. Earlier accepted project code is retained.

All six exact-head GitHub Actions gates are required. This task does not repeat or bypass the earlier blocked physical-installation preparation. Code acceptance is separate from deployment, and main remains unmerged.

## Primary references

Pinned upstream ctt-gob-es/clienteafirma commit0d7f3cf01fb65d2be5b245622d2c8f490f36e718:

- `afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js`, configureExtraParams/addSignatoryCertificateToExtraParams: https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js
- `afirma-keystores-filters/src/main/java/es/gob/afirma/keystores/filters/EncodedCertificateFilter.java`: https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-keystores-filters/src/main/java/es/gob/afirma/keystores/filters/EncodedCertificateFilter.java
- `CertFilterManager.java`, encodedcert/filter/headless interpretation: https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-keystores-filters/src/main/java/es/gob/afirma/keystores/filters/CertFilterManager.java

These sources establish protocol semantics, not permission to bypass consent or proof of acceptance by any government website. No upstream implementation is copied into this patch.

The first implementation test pass exposed a fixture error: repeated calls to the shared non-exportable identity helper deliberately return the same cached certificate. Negative identity cases now explicitly generate a distinct synthetic certificate and keep the original mismatch assertions. The actual Android test also checks two different certificate encodings over one identical public key. Neither production exact-match checking nor consent rules were relaxed to accommodate a fixture. The initial failing test output is retained separately.
