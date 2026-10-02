# General native batch, tri-phase, XAdES and WebAuthn

## Scope and provenance

Continue the user's four requested capabilities from installed `7f8b91062bddce2b18617b5583567d8f083c3e1a` in PR338. This is one integrated package, not a new site-profile matrix. The resumable implementation already existed locally; the acceptance pass preserved all 41 pending files before modifying them.

The general path remains separate from privileged profile adapters. It never synthesizes a trusted profile to obtain a certificate or JavaScript signing bridge. Native AutoFirma requests use the existing origin/owner/epoch, certificate selection, explicit confirmation, cancellation, one-shot delivery and uncertain-receipt controls.

Canonical upstream wire behavior was checked against AutoFirma commit `0d7f3cf01fb65d2be5b245622d2c8f490f36e718`. Reference Java sources and their provenance manifest are retained in the task's `reference/` directory; upstream implementation was not copied into production source.

## XAdES local signing

The generic local signer supports XAdES-BES detached, enveloping, enveloped and externally detached packaging. Local algorithms are the existing explicitly requested RSA SHA-1, SHA-256 and SHA-512; compatibility with SHA-1 is not a recommendation to choose it. Payloads are limited to512KiB and encoded output to2MiB. The source data, SignedProperties and KeyInfo references are signed and verified before upload. The certificate digest, issuer/serial, packaging and requested metadata are checked. A key's encoded private representation is not requested.

Enveloped XML uses a same-document empty URI reference. Its canonicalized input includes the entire document, not only its root element: processing instructions before and after the root are also covered. An independently validating JDK XMLDSig test exposed the original root-only digest defect; production canonicalization was corrected, not the independent assertion.

Externally detached payloads use a locally generated digest URN; the validator never fetches that URI from a network. This is not arbitrary remote-data or external-URI signing. XML parsing rejects DTD/entities, ambiguous IDs and oversized/deep structures. No stylesheet execution or external entity resolver is used. Existing signatures, arbitrary transforms, advanced policy/TSA/LT/LTA and local XAdES co-/counter-sign are not silently implemented or discarded.

## General tri-phase signing

A request with an accepted tri-phase format or explicitly supplied service endpoint uses the generic service transport. Supported protocol families are CAdES, PDF/PAdES and XAdES, with sign/cosign/countersign operation names forwarded to the service. The service must implement the requested operation and parameters; forwarding a request is not proof of arbitrary format support on that server.

Single-operation forms use `op=pre/post`, `cop`, `format`, `algo`, the certificate chain, document/reference and parameters. PRE XML is received, its structure and complete bounded signature list checked, and each local RSA PKCS#1 result is self-verified. PK1 is inserted, PRE retained only when requested, and the post phase receives the signed session. The service's returned NEWID payload is delivered through the original browser-facing result envelope. It may be a document or an opaque server result; the phone does not claim independent validation of its complete format merely because it verified PK1.

The service defines opaque PRE bytes. The consent screen explicitly says that document/reference and public certificate are sent to the listed service, and that the phone cannot independently prove an opaque PRE matches the user's intended document. Endpoint identity is bound to the original accepted request, not selected by a later PRE response. This is user-authorized delegated signing, not invisible release of signing authority.

The original single final-upload permission is not reused for each phase. A separate checkpoint revalidates the same owner, epoch, fingerprint and foreground state before/after PRE and local signing, before POST and before the final receipt. Final permission is consumed once before the irreversible post phase. Ambiguity after POST or failure to return its receipt remains UNCERTAIN, and the operation cannot automatically rerun.

## General batches

Remote XML and JSON batches share the bounded descriptor and phase engine. A descriptor has at most32 items and512KiB; each item retains its approved ID, format, operation, reference and options. Service-specific XML signsaver metadata is forwarded as data, never executed as a local class. No site profile is required.

XML uses the documented `xml`, `certs` and `tridata` forms; JSON uses `json`, `certs` and `tridata`. Certificate-chain delimiters differ from single tri-phase requests and are preserved. JSON PRE failures are bound to the exact requested IDs, removed from the signable input descriptor and retained as explicit outcomes. Missing, duplicate, foreign or overlapping result identities cannot become an all-success result. Malformed optional JSON properties now reject instead of being treated as absent; a test caught the earlier possibility of silently discarding a constraint.

The result list is checked against the whole approved batch. The UI reports successful and other outcomes separately. `stoponerror` prevents subsequent local work; the local JSON batch follows upstream statuses and removes prior in-memory signatures from a stop-on-error report. A batch result is first in its intermediate-server envelope, followed by the certificate only when requested; it is not accidentally sent using the single-signature certificate-first ordering.

Local JSON batches support mixed locally implemented CAdES, XAdES and PDF signatures, including the supported PDF sequential-signing operation. They do not contact a tri-phase service. Aggregate signature output is bounded. Unsupported per-item algorithms/formats/operations are explicit failures, never silently replaced with another format. A remote status such as DONE_AND_SAVED is the service's assertion and does not establish government acceptance or atomic rollback of server-side side effects.

## Transport and credentials

Each phase is an explicit bounded HTTPS POST with public-address DNS validation, no browser cookies, Authorization, Referer, HTTP-password negotiation or client-certificate forwarding. There are no redirects, connection retries or automatic Retry-After replays. Request bodies are one-shot. Response sizes, timeouts, cancellation and resource cleanup are bounded. The existing final-result storage protocol remains separate.

The public certificate is intentionally supplied after user confirmation; the private key remains local. A service can already have saved a partial result when its response or final browser receipt is lost. Such uncertainty is not described as safe to retry.

## Native WebAuthn

Every TrustedJuntaWebView conditionally enables Android WebKit's native `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER` and reads back the effective level. Credential Manager and the Play Services provider adapter are declared with verified dependency hashes. Unsupported or failed configuration leaves ordinary navigation available. No navigator.credentials shim, fabricated origin, fake credential or bypassed relying-party authorization is introduced.

The browser exposes a capability/status action and the existing explicit external-browser fallback. The enabled state is deliberately `ENABLED_PROVIDER_AUTHORIZATION_UNKNOWN`: a credential provider must authorize a privileged browser caller for third-party relying parties. Google Password Manager approval has not been obtained or claimed. Enabling the native WebView API therefore does not prove that a real passkey login succeeds on this installation.

Android instrumentation checks actual native feature support, mode readback, a real secure HTTPS origin and native JavaScript capability types, plus honest status/fallback UI. It does not create/get a personal credential or enroll/authenticate a real account. WebAuthn is native integration with provider-dependent readiness, not a universal completed-login claim.

## Tests and authorship

Four completed GPT-6.1 Sol Max code outputs were accepted: native WebAuthn configuration/tests; XAdES option parsing/tests; tri-phase XML codec tests; and independent whole-document XAdES boundary tests. Larger XML/descriptor/batch author requests timed out without accepted code and are not counted. The integrator wrote the production engines/orchestration, remaining tests and the fixes discovered during acceptance. Original responses, timestamps and integrated hashes are retained. Subagents wrote code, not review reports, and did not run the complete acceptance suite.

The actual native focused suite includes legacy PDF/CAdES/cosign/cancellation/deferred-protocol regressions and new XAdES, tri-phase, remote/local batch and transport cases. Independent JDK XMLDSig validates all four packages and three algorithms with controlled local references; document-level processing-instruction tampering is covered. Synthetic services verify PRE/PK1/session/form/envelope semantics, partial results, stop-on-error, cancellation, uncertain POSTs and no automatic retry. Android tests exercise local canonicalization/key use and real unprofiled browser consent for XAdES, batch and tri-phase requests.

All existing exact-head GitHub Actions gates remain mandatory before installation. A focus-test pass, mode flag or generated APK alone is not acceptance. Actual final results and installation receipts are recorded separately; this specification does not claim unexecuted tests passed.

## Primary references

- AutoFirma: https://github.com/ctt-gob-es/clienteafirma/tree/0d7f3cf01fb65d2be5b245622d2c8f490f36e718
- XML Signature1.1 (same-document references and core validation): https://www.w3.org/TR/xmldsig-core/
- Native WebView authentication mode: https://developer.android.com/reference/androidx/webkit/WebSettingsCompat
- Credential-provider privileged caller approval: https://developer.android.com/identity/sign-in/privileged-apps

No personal signing key, real government document, private account or administrative action is used for automated validation. Main is not merged by this package. WARP, existing Codex/proxy runtime and unrelated working trees remain outside the change.

The unqualified local XAdES default is Enveloping, matching the pinned upstream XAdESSigner format default. The legacy mode value is syntactically checked but does not change packaging, as upstream Utils.checkIllegalParams explicitly ignores mode for XAdES. The integrator corrected the authored default and its expectations after checking those exact sources; original and integrated hashes remain separate. Old native-navigation tests that assumed every batch/countersign route was unsupported now retain malformed/unknown-operation checks and independently test the new data routes with unchanged main-frame/GET restrictions.

## Final Android integration corrections

The final acceptance pass found and retained three real failures. Credential Manager's compile graph selected Fragment1.2.5 while the runtime lock already selected1.5.7, invalidating ActivityResult usage. A compile/runtime constraint now selects the existing verified1.5.7; dependency locking and verification are not relaxed. A narrow strict dependencyInsight probe confirmed resolution before the full CI run.

A software RSA key that exposes its parameters but deliberately has no encoded format caused Android Conscrypt to recurse through private-key upcalls. The shared raw signer now chooses a per-instance, unregistered Bouncy Castle provider for RSAPrivateKey software keys; opaque AndroidKeyStore keys retain normal platform JCA dispatch. No encoded key accessor, global provider mutation or second signature attempt is added. A fifth successful GPT-6.1 Sol Max output supplied two Android tests: all three software RSA digests without private-key encoding, and an independently generated opaque AndroidKeyStore key. The test owns and deletes only its unique synthetic alias. The integrator fixed imports/constructor field names before applying it; no agent-run test execution is claimed.

Local PDF co-signing retains its precise existing consent text; the newly added generic delegated cosign label no longer hides that information. The two original PDF consent regressions stay unchanged. All failed XML/logs are preserved under the task's completion-resume directory, and a passing native JVM subset does not replace the full Android gate.
