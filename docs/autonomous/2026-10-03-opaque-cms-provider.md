# CMS/PDF signatures with provider-owned RSA keys — 0.2.1

## Operator scope

Continue the installed 0.2.0 candidate on the current PR338; do not merge main. The operator explicitly requested no subagents and no E2E testing, and a verified in-place APK update at the end. No model workers, device UI, emulator, real KeyChain identity, private portal or administrative action is used in this slice.

PR338 now carries the explicit `verification:no-e2e` label. Only the instrumentation job of that numbered pull request is conditionally skipped while that label remains. Other PRs, main pushes and manual CI retain the default instrumentation job. All unit, lint, dependency/secret, Go, artifact and release-signing gates remain. Android test sources are still compiled, not executed. The skipped job must be reported as skipped, never as passed; release evidence records five successful groups and the explicit instrumentation exception. This is not authority to merge without any separately required release acceptance.

## Reproduction

The common NativeCadesEngine and shared BouncyCastleCadesSigner unconditionally pinned JcaContentSignerBuilder to the bundled Bouncy Castle provider. That works with software RSA keys whose numerical parameters are available, but a provider-owned private-key handle is not interchangeable with those parameters. The existing raw RSA/XAdES/tri-phase path already selected the system JCA path for an opaque key.

A JVM-only synthetic provider exposes a PrivateKey handle without RSAPrivateKey parameters. Its encoded accessor throws, it authorizes only that exact handle, and it performs real RSA internally with a generated test key. The unchanged raw signing engine succeeds with this handle, proving the fixture can sign. On the unmodified 0.2.0 CMS code six of eight new tests fail: common CAdES, profile-shared CAdES, PAdES, the native operation and provider invocation controls. The size-rejection and raw-engine positive controls pass. Logs, source hashes and the baseline compiled JAR are retained outside the repository.

This demonstrates a real provider-selection incompatibility at the actual CMS/PDF seam. It is not a claim that a personal AndroidKeyStore certificate was exercised or that every hardware implementation has been verified.

## Fix

For software RSAPrivateKey, keep the existing isolated bundled provider. For other RSA PrivateKey handles, let the standard JCA signature path select the provider supporting that key. Do not inspect key encoding, copy key material, install a global provider, retry a failed signing operation through a second provider or change the requested digest.

Apply the same narrow choice in NativeCadesEngine and the shared BouncyCastleCadesSigner. Common PDF signing uses the former and reviewed CAdES adapters use the latter; no profile allow-list or new signature format is introduced. The already-correct raw RSA selector, public-certificate/digest generation, CMS signed attributes, validation, input limits, consent and transport behavior are unchanged.

## Verification without E2E

The eight new unit/integration tests use real CMS/PDF engines with the isolated test provider. They cover all four existing RSA digests, attached/detached CMS, shared-profile CMS, PDF plus an incremental approval signature, rejected provider output, no second signature attempt after provider refusal, no provider call for oversized input, and the actual native operation's explicit final upload authorization. A minimal generated-key raw signature is the positive control. Provider registration exists only in the test fixture, is restricted to its handle and is removed in finally with the original provider list checked.

The same targeted tests must go green after the fix, together with the pre-existing native suite, complete Debug/QA unit tests, lint, builds and security checks on the exact candidate commit. Additional local cryptographic verification may use synthetic CMS/PDF files, never personal documents. An emulated provider is not an Android hardware acceptance test. No E2E, application window, emulator session or real government site is launched.

## Deployment

VersionName becomes0.2.1 (QA suffix retained), versionCode3. Verify the exact CI artifact SOURCE_COMMIT and checksum, retain the previous installed APK, preserve its Android signer and application payload, and perform only a normal non-foreground package-manager update without uninstall, clear, downgrade or permission grant. Reconcile actual installed bytes and metadata before claiming success or repeating a command after a lost response.

## Primary references

- Android Keystore operation and non-exportable key model: https://developer.android.com/privacy-and-security/keystore
- Android cryptography and JCA signature examples: https://developer.android.com/privacy-and-security/cryptography
- JCA Signature provider selection and initSign contract: https://docs.oracle.com/javase/8/docs/api/java/security/Signature.html

These references explain the key/provider interface, not certification of this application or of an administrative workflow. Existing WebAuthn provider approval, unsupported document policies and real-portal acceptance limits remain.
