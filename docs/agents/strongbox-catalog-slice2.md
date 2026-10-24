# ADR 0005 slice 2: local storage verification

Scope: disabled storage lifecycle, not runtime activation. ADR 0009 records the
schema and commit/recovery boundary. No TLS, signing configuration, dependencies,
locks, verification metadata, security policy, or detekt baseline changed.

## Changes

- `CertificateReferenceCatalog.kt`: schema 2 reads schema 1, optional file source,
  versioned key reference, bounded owned-alias tombstones and validation.
- `CertificateReferenceStore.kt`: atomic compare-and-replace/removal, durable
  tombstone acknowledgement; active-file compatibility and retiring replaced keys.
- `KeystoreSigningKeyCatalog.kt`: defaults disabled; explicitly loads the expected
  source file, requires backup acknowledgement and separate TEE consent, imports
  and verifies before publishing; optional source forgetting is a separate commit.
  Passwords are cleared on every exit. Orphan sweeping uses only the owned prefix.
  Authenticated unlock verifies fingerprint and placement before creating the
  identity in an ordinary expiring `CertificateSession`.
- `KeystoreSigningKeyImport.kt`: backend chain/enumeration seam and reusable
  verification; `Pkcs12Loader.kt` shares existing certificate-summary extraction.
- `KeystoreSigningKeyRecordTest.kt`, `CertificateReferenceCatalogTest.kt`,
  `KeystoreSigningKeyImporterTest.kt`, `FakeSigningKeyStoreBackend.kt`: lifecycle,
  storage compatibility and failure recovery using the existing opaque-key fake.
- `app/src/main/res/{values,values-en,values-ca,values-gl,values-eu}/keystore_key_strings.xml`:
  14 matching resources each, including irreversible-loss/backup warnings,
  acknowledgement, explicit TEE consent, optional forgetting and deletion.

## RED / GREEN

The initial lifecycle test failed to compile before the record/lifecycle API existed.
A later behavioral RED ran 22 tests with one assertion failure:
`unspecifiedHardwareCannotBePublishedAsTee` expected
`Failed(NOT_HARDWARE_BACKED)` but the operation threw instead. The publication
boundary now removes the alias and returns the typed failure without claiming TEE.
That test then passed with the other 21 tests.

Final focused standalone Kotlin/JUnit run: **39 tests passed** (13.788 s execution,
compilation separate). It covered the importer, the new lifecycle and the complete
existing catalogue test class, including real file-backed DataStore reopen,
legacy/schema-1 compatibility, failed commits, cancellation before publication,
ambiguous post-commit failure, optional-forget failure, both tombstone interruption
points, foreign/live alias preservation, stale entries, authentication/fingerprint
failure and both StrongBox/TEE session placements.

The local harness is outside the worktree:
`~/.cache/w47-strongbox-check/check.py`. It uses cached Kotlin/JUnit, BC 1.85,
coroutines 1.10.2, DataStore 1.1.3 and Okio 3.17.0, an isolated Java-URI-backed
`android.net.Uri` substitute, and the SDK 36 compile stub. Robolectric runner and
configuration annotations are removed only from temporary test copies. The
SigningAlgorithm enum and jcaName mapping are extracted verbatim from production.
No AndroidKeyStore methods are executed. Missing DataStore runtime jars caused
harness-only failures; after completing the cached classpath all 39 tests passed.
This is supporting JVM evidence, not an Android build or a Robolectric run.

Other checks:

- `python3 -m unittest tools.tests.test_locale_en_resources tools.tests.test_locale_ca_resources tools.tests.test_locale_gl_resources tools.tests.test_locale_eu_resources -q`: **50 passed**.
- Checksum-pinned detekt 1.23.8 on the five changed production Kotlin files,
  with the unchanged repository baseline and default configuration: **exit 0**.
- `git diff --check`: **passed**.
- Separate read-only standards and spec reviews: no blocking findings.

## Boundaries

No push, PR, workflow dispatch, broad Gradle gate, instrumentation, emulator,
device install or physical StrongBox/TEE test was performed. Runtime UI and
password-cache/grant/reminder integration remain disconnected, as recorded in
ADR 0009 and the debt registry. The new warning strings are assets for that UI,
not evidence that the feature is user-operable. Optional forgetting removes the
stored file reference, not the user's backup or its existing SAF grant. An
unreadable ambiguous commit preserves its alias until recovery can read a safe
catalogue snapshot. Broad exact-SHA CI and separately authorized physical
acceptance remain prerequisites for runtime activation.
