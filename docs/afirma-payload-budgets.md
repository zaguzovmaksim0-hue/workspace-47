# Native AutoFirma document budgets

The local PAdES path accepts PDF input up to **8 MiB** and produces a signed PDF up to **10 MiB**. These are admission limits, not a promise that every PDF structure is supported. Existing restrictions on encrypted, certified, locked, malformed or unsupported signed PDFs continue to apply.

## One budget through the request and result paths

`NativeAfirmaLimits` defines the shared bounds:

- Local PAdES input and consent preview: 8 MiB.
- Signed PDF output, byte-range validation and prior-signature inspection: 10 MiB.
- Direct in-browser `afirma:` URI: 16 MiB of characters, including the base64 document and parameters.
- Retrieved XML configuration and decrypted carrier plaintext: 12 MiB.
- Retrieved encoded response: 17 MiB; WebMessage adds a separate 64 KiB envelope allowance.
- Final result string: 16 MiB, including base64 and protocol separators.

CAdES, XAdES, batch and remote/triphase input retain their existing smaller limits.

Browser `intent:` wrappers retain the legacy **1 MiB** character limit, checked before `Intent.parseUri` allocates Android Intent metadata. The 16 MiB allowance applies only to direct `afirma:` requests. Large inline documents therefore use direct `afirma:` or the existing explicitly authorized indirect configuration path; an inline `intent:` wrapper above 1 MiB is refused. This preserves the earlier wrapper admission boundary while keeping the verified large-document route available.

Incoming Android external intents and outgoing handoff retain their existing 1 MiB admission limits. Binder transactions are not enlarged. Indirect retrieval does not grant signing consent. External `fileid` requests remain refused until their source can be authorized.

## Memory behavior

Base64 decoding allocates the exact bounded decoded byte array and checks padding and alphabet without a second normalized or re-encoded document. Percent-free ASCII query components avoid a redundant whole-value UTF-8 copy. Plain retrieved XML is size-checked before making an owned copy.

PDF signing still owns byte arrays for input, preview and output; it is not a fully streaming PDF implementation. The CMS byte-range digest is computed incrementally with a 16 KiB buffer. Verification hashes its two signed spans directly. The output buffer cannot grow beyond its 10 MiB cap, and replaced buffers are erased.

PDFBox uses a 16 MiB scratch limit. Its byte-array loading overload reuses the already-owned input as the random-access source. Loading the same 8 MiB document through the InputStream overload consumed scratch both for the source and for parsed streams, and failed when the signed result crossed that budget; the byte-array path removes that duplicate scratch copy without raising the cap.

These bounds limit individual allocations and protocol buffers. Android renderer, ART, PDF structure and concurrent UI allocations still require device validation before a release claim.

## Local verification, 10 November 2026

A standalone Kotlin overlay compiled the changed production classes with Kotlin module name `app`, the existing QA runtime classpath and friend paths to the already compiled QA classes. JUnit ran under `-Xmx256m` with no live endpoint and no personal certificate.

- The original compiled admission path rejected an 8 MiB request with `invalid_uri_size_or_control` (job `job_20261110_105309_822578db`).
- The final focused suite passed **124/124** tests (job `job_20261110_111902_007f18b7`). It includes a synthetic eight-page PDF above 7 MiB: request parsing, consent preview, signing with a newly generated software test identity, independent signature verification, reopening all eight pages and result-wire roundtrip. Large legacy DES and AES carriers, oversized admission, configuration parsing, cancellation/one-shot ownership and existing PAdES/co-sign behavior are covered.
- After extracting the validation and bounded-read helpers, the expanded focused suite passed **174/174** under the same 256 MiB heap (job `job_20261110_114601_63b83c60`), including TLS-chain completion, exact HTTPS-port scopes and catalog URL admission.
- The JavaScript relay unit suite passed **21/21** tests (job `job_20261110_110118_d0fc4ab3`), including a result above the former relay cap and rejection above the new cap.
- A separate focused browser navigation suite passed **12/12** Robolectric tests under `-Xmx768m` (job `job_20261110_131800_fdacdeac`). Benign boundary inputs confirm that a 1 MiB `intent:` is recognized, one additional character is refused before Android Intent parsing, an equally sized direct `afirma:` remains recognized, and the refused wrapper cannot reach another browser handler. The two new regressions failed against the earlier compiled implementation (job `job_20261110_131256_2d018659`).

This is local unit and protocol verification, not REAL E2E, Android memory profiling, portal login, portal acceptance of a signature, or an installed APK update. Those checks were deliberately not run in this work.
