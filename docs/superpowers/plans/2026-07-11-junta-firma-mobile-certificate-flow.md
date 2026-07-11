# Junta Firma Mobile: Certificate Flow Implementation Plan

> **Status:** active Phase 2 plan. The authoritative build/runtime host is the
> POCO F6 Pro running Android 16 and native Termux/aarch64.

**Goal:** Let the user select a `.p12`/`.pfx` through SAF, unlock exactly one
usable RSA identity in memory, inspect safe X.509 metadata, and manually or
automatically lock it without persisting the password, PKCS#12 bytes, private
key, or unlocked session.

**Architecture:** A bounded pure-Java `Pkcs12Loader` is isolated from Android
I/O. `CertificateRepository` owns the narrow SAF/content boundary and persists
only a `content:` URI plus safe display metadata. `CertificateSession` owns the
memory-only unlocked identity. A lifecycle-aware ViewModel exposes a closed UI
state to Compose. The Activity only launches `OpenDocument`; parsing and secret
handling never occur in composables.

**Security invariants:** 10 MiB input limit; at most 32 aliases and 16 chain
certificates; exactly one `PrivateKeyEntry`; RSA + valid X.509 + permitted
`digitalSignature` key usage; private/public correspondence proof; internal
password/P12/challenge copies cleared; no raw exception messages or direct
logging; no `PrivateKey.encoded`; URI permission is read-only and released on
forget.

---

## Task 1: Bounded PKCS#12 loader and memory-only session

**Files:**

- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateModels.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/Pkcs12Loader.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateSession.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/certificate/TestCertificateFactory.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/certificate/Pkcs12LoaderTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateSessionTest.kt`

1. Pin Bouncy Castle 1.84 for synthetic test fixtures and coroutines-test
   1.10.2; do not register BC in production loader code.
2. Write RED tests for valid RSA, wrong password/corrupt file, declared/actual
   oversize, certificate-only, EC key, expired/not-yet-valid, missing signing
   key usage, multiple private entries, oversized alias/chain collections, and
   mismatched private/public key.
3. Implement the closed result/error model and bounded loader. Prove key/cert
   correspondence with `SHA256withRSA` over a random challenge without reading
   private-key encoded bytes.
4. Write/implement session tests for locked/unlocked, manual lock, ten-minute
   expiry, background/trim-memory lock, replacement, and process-death-by-design.

**Gate:**

```bash
./gradlew testDebugUnitTest --tests '*Pkcs12LoaderTest' --tests '*CertificateSessionTest'
rg -n 'PrivateKey.*encoded|privateKey\.encoded|\.getEncoded\(\)' app/src/main/java/dev/junta/firmamobile/certificate && exit 1 || true
```

---

## Task 2: SAF repository and URI-only persistence

**Files:**

- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateDocumentAccess.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateReferenceStore.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateRepositoryTest.kt`

1. RED-test only `content:` URIs, `.p12`/`.pfx`, accepted PKCS#12 or fallback
   octet-stream MIME, size bounds, persistable read permission, recreation,
   reopen, replacement permission release, forget, and generic errors.
2. Implement a `ContentResolver` adapter and Preferences DataStore containing
   only URI string, safe display name/MIME/size, and safe certificate summary.
3. Never copy the selected document into app storage and never store password,
   alias, private-key material, or certificate bytes.

**Gate:**

```bash
./gradlew testDebugUnitTest --tests '*CertificateRepositoryTest'
rg -n 'password.*(put|preference)|pkcs12.*(put|preference)' app/src/main/java/dev/junta/firmamobile/certificate && exit 1 || true
```

---

## Task 3: Compose certificate setup, lock controls, and device QA

**Files:**

- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/AppRoot.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/CertificateViewModel.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/CertificateUiState.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/ui/CertificateViewModelTest.kt`
- Create/modify: `app/src/androidTest/java/dev/junta/firmamobile/CertificateSetupFlowTest.kt`

1. RED-test UI states and events: no selection, selected/locked, password entry,
   loading, invalid password, unlocked summary, lock, forget, and retry.
2. Use `rememberLauncherForActivityResult(OpenDocument())` with PKCS#12 MIME
   candidates. The password field is masked, never saveable, cleared after each
   attempt, and not echoed in semantics after unlock/failure.
3. Display owner, issuer, validity interval/status and locked/unlocked state.
   Show no private key, serial dump, raw DN debugging, path, or password.
4. Build, sign, install/update through `/data/local/tmp`, then launch only for
   the required instrumentation/runtime flow. Use a clearly synthetic test
   PKCS#12, never a personal certificate, in automated QA.

**Gate:**

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Device acceptance: SAF picker opens; synthetic `.p12` is selected; wrong
password is safely rejected; correct password reveals owner/issuer/validity;
manual lock removes the unlocked identity; package remains crash-free. Do not
claim real-certificate or signing E2E from this phase.
