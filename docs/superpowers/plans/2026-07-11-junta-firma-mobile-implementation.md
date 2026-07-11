# Junta Firma Mobile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, validate, install, and release a native Android 16 application that keeps the Junta portal in an origin-restricted WebView, unlocks a user-selected PKCS#12 identity only in memory, intercepts the portal's real MiniApplet/AutoFirma flow, obtains explicit confirmation, signs locally, and returns the result without launching Play Store or external AutoFirma.

**Architecture:** A single Kotlin Android app separates UI, WebView, AutoFirma compatibility, certificate, signing, network/session, and security boundaries behind small interfaces. Work proceeds as an evidence-driven vertical slice: deterministic components are developed with TDD, a debug-only WebView probe captures safe runtime protocol metadata, and the tri-phase wire codec is planned and implemented only from that observed contract. The full goal remains open until real portal E2E and release gates pass.

**Tech Stack:** Android API 26–36, Android Gradle Plugin 9.2.1, Gradle 9.4.1, JDK 21 (AGP minimum 17), AGP built-in Kotlin 2.3.10, Kotlin Compose compiler plugin 2.3.10, Compose BOM 2026.06.00, Activity Compose 1.13.0, AndroidX WebKit 1.16.0, Bouncy Castle 1.84, OkHttp/MockWebServer 5.3.0, JUnit 4.13.2, Robolectric 4.16.1, AndroidX Test 1.7/1.3, Espresso 3.7.

## Global Constraints

- Application name is `Junta Firma Mobile`; application ID and namespace are `dev.junta.firmamobile` and never `es.gob.afirma`.
- The UI always exposes `Cliente no oficial para uso personal`.
- One `app` module; Kotlin, AndroidX, Material 3; `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`.
- No required Google Play Services, external AutoFirma, Play Store fallback, localhost WSS, local CA, trust-all TLS, cleartext, or wildcard domains.
- No code, resources, DEX, APK, or decompiled implementation from official AutoFirma.
- No automatic signing: every request requires a native confirmation showing site, certificate, format, and algorithm.
- No password persistence or logging; passwords are `CharArray`, private keys remain `PrivateKey` objects in process memory, and user P12 bytes are not copied in the initial scope.
- No raw document, `dat`, P12, private key, full certificate, signature, cookies, token, or symmetric key in logs or diagnostics.
- Callback/server URLs require exact HTTPS allowlisting plus SSRF, DNS, redirect, userinfo, port, and private-address checks.
- `SHA1withRSA` is allowed only for the exact observed Junta login contract and with a visible compatibility warning; SHA-256 remains the normal supported algorithm.
- The project is not complete until unit, instrumentation, Android 16 device, real authorized certificate E2E, logcat audit, release signing, `apksigner`, `zipalign`, and APK SHA-256 evidence all pass.
- Every task ends green. On any failure, invoke `superpowers:systematic-debugging` before changing implementation.
- Before claiming any task or project complete, invoke `superpowers:verification-before-completion` and run fresh commands.

## Source-verified build baseline

- Android 16 is API 36 and official examples use `compileSdk = 36` / `targetSdk = 36`.
- AGP 9.2 supports through API 37, requires Gradle 9.4.1 and JDK 17+, and defaults to Build Tools 36.0.0.
- AGP 9 uses built-in Kotlin; do not apply `org.jetbrains.kotlin.android`.
- Compose uses `org.jetbrains.kotlin.plugin.compose` 2.3.10 and stable BOM 2026.06.00.
- AndroidX WebKit 1.16.0 is stable and supports minSdk 24, below this app's minSdk 26.
- AndroidX Activity Compose 1.13.0 is stable.
- Bouncy Castle README identifies 1.84 as current `bcprov-jdk18on`; include matching `bcpkix-jdk18on` and `bcutil-jdk18on`.
- OkHttp official docs identify 5.3.0 and Android API 21+, below this app's minSdk.

Primary references:

- `https://developer.android.com/build/releases/agp-9-2-0-release-notes`
- `https://developer.android.com/build/migrate-to-built-in-kotlin`
- `https://developer.android.com/develop/ui/compose/bom`
- `https://developer.android.com/jetpack/androidx/releases/webkit`
- `https://developer.android.com/jetpack/androidx/releases/activity`
- `https://github.com/bcgit/bc-java/blob/main/README.md`
- `https://square.github.io/okhttp/`

## Required execution order

Start with
`docs/superpowers/plans/2026-07-11-junta-firma-mobile-research-shell.md`.
That plan is the authoritative Phase 1 sequence and completes the build baseline,
sanitized logging, origin validation, minimal secure WebView, MiniApplet metadata
probe, Android 16 installation, and runtime observation before certificate or
signing implementation begins. After Phase 1 evidence is committed, resume this
master roadmap at Task 4 and execute Tasks 4–15, skipping only work already
proved complete by identical tests in the Phase 1 commit.

---

## File map

### Build and policy

```text
.gitignore
.gitattributes
build.gradle.kts
settings.gradle.kts
gradle.properties
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
keystore.properties.example
tools/bootstrap-termux-aapt2.sh
docs/building-on-termux.md
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/xml/network_security_config.xml
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
```

### Production sources

```text
app/src/main/java/dev/junta/firmamobile/MainActivity.kt
app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt
app/src/main/java/dev/junta/firmamobile/model/AppError.kt
app/src/main/java/dev/junta/firmamobile/model/AppState.kt
app/src/main/java/dev/junta/firmamobile/model/SigningState.kt
app/src/main/java/dev/junta/firmamobile/security/SensitiveData.kt
app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt
app/src/main/java/dev/junta/firmamobile/network/AllowedOrigins.kt
app/src/main/java/dev/junta/firmamobile/network/SafeUrlValidator.kt
app/src/main/java/dev/junta/firmamobile/network/JuntaCookieJar.kt
app/src/main/java/dev/junta/firmamobile/network/JuntaHttpClient.kt
app/src/main/java/dev/junta/firmamobile/browser/WebViewCookieBridge.kt
app/src/main/java/dev/junta/firmamobile/browser/WebViewStateHolder.kt
app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt
app/src/main/java/dev/junta/firmamobile/browser/JuntaWebChromeClient.kt
app/src/main/java/dev/junta/firmamobile/browser/TrustedJuntaWebView.kt
app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt
app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt
app/src/main/java/dev/junta/firmamobile/afirma/AfirmaOperation.kt
app/src/main/java/dev/junta/firmamobile/afirma/AfirmaRequest.kt
app/src/main/java/dev/junta/firmamobile/afirma/AfirmaUriParser.kt
app/src/main/java/dev/junta/firmamobile/afirma/PendingSignRequestStore.kt
app/src/main/java/dev/junta/firmamobile/afirma/AfirmaRequestRouter.kt
app/src/main/java/dev/junta/firmamobile/afirma/LegacyAfirmaPayloadCodec.kt
app/src/main/java/dev/junta/firmamobile/certificate/CertificateSummary.kt
app/src/main/java/dev/junta/firmamobile/certificate/UnlockedIdentity.kt
app/src/main/java/dev/junta/firmamobile/certificate/Pkcs12Loader.kt
app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt
app/src/main/java/dev/junta/firmamobile/certificate/CertificateSession.kt
app/src/main/java/dev/junta/firmamobile/signing/SignatureResult.kt
app/src/main/java/dev/junta/firmamobile/signing/LocalSignatureEngine.kt
app/src/main/java/dev/junta/firmamobile/signing/CadesSigner.kt
app/src/main/java/dev/junta/firmamobile/signing/TriPhaseClient.kt
app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt
app/src/main/java/dev/junta/firmamobile/ui/AppRoot.kt
app/src/main/java/dev/junta/firmamobile/ui/CertificateSetupScreen.kt
app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt
app/src/main/java/dev/junta/firmamobile/ui/SignatureConfirmationSheet.kt
app/src/main/java/dev/junta/firmamobile/ui/SigningProgressOverlay.kt
app/src/main/java/dev/junta/firmamobile/ui/DiagnosticScreen.kt
app/src/main/res/raw/afirma_shim.js
```

### Debug-only research

```text
app/src/debug/AndroidManifest.xml
app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt
app/src/debug/java/dev/junta/firmamobile/browser/ProtocolObservationRecorder.kt
```

The probe Activity is exported only by the debug manifest. The release APK has no probe Activity, WebView debugging, or raw protocol export.

### Tests

```text
app/src/test/java/dev/junta/firmamobile/security/SanitizedLoggerTest.kt
app/src/test/java/dev/junta/firmamobile/network/OriginAllowlistTest.kt
app/src/test/java/dev/junta/firmamobile/network/SafeUrlValidatorTest.kt
app/src/test/java/dev/junta/firmamobile/afirma/AfirmaUriParserTest.kt
app/src/test/java/dev/junta/firmamobile/afirma/PendingSignRequestStoreTest.kt
app/src/test/java/dev/junta/firmamobile/afirma/AfirmaRequestRouterTest.kt
app/src/test/java/dev/junta/firmamobile/certificate/Pkcs12LoaderTest.kt
app/src/test/java/dev/junta/firmamobile/certificate/CertificateSessionTest.kt
app/src/test/java/dev/junta/firmamobile/certificate/TestCertificateFactory.kt
app/src/test/java/dev/junta/firmamobile/signing/LocalSignatureEngineTest.kt
app/src/test/java/dev/junta/firmamobile/signing/CadesSignerTest.kt
app/src/test/java/dev/junta/firmamobile/network/CookieBridgeTest.kt
app/src/test/java/dev/junta/firmamobile/network/JuntaHttpClientTest.kt
app/src/test/java/dev/junta/firmamobile/signing/TriPhaseClientContractTest.kt
app/src/test/java/dev/junta/firmamobile/browser/WebMessageProtocolTest.kt
app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt
app/src/androidTest/java/dev/junta/firmamobile/AppLaunchTest.kt
app/src/androidTest/java/dev/junta/firmamobile/CertificateSetupFlowTest.kt
app/src/androidTest/java/dev/junta/firmamobile/TrustedWebViewTest.kt
app/src/androidTest/java/dev/junta/firmamobile/AfirmaInterceptionTest.kt
app/src/androidTest/java/dev/junta/firmamobile/WebMessageOriginTest.kt
app/src/androidTest/java/dev/junta/firmamobile/ConfigurationStateTest.kt
```

---

### Task 1: Reproducible Android project and secure manifest baseline

**Files:**
- Create every file under **Build and policy**.
- Create: `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/AppRoot.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/AppLaunchTest.kt`

**Interfaces:**
- Produces launcher `dev.junta.firmamobile/.MainActivity` and `@Composable fun AppRoot()`.
- Establishes dependency coordinates and release/debug build policy for every later task.
- Establishes a verified project-local Termux/aarch64 AAPT2 bootstrap while
  leaving supported desktop hosts on AGP's standard Maven AAPT2.

- [ ] **Step 1: Generate the wrapper and verify the pinned distribution**

Run from the repository root:

```bash
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
rg 'gradle-9\.4\.1-bin\.zip' gradle/wrapper/gradle-wrapper.properties
./tools/bootstrap-termux-aapt2.sh bootstrap # native Termux/aarch64 only
```

Expected: one matching distribution URL; `./gradlew --version` reports Gradle
9.4.1. On native Termux/aarch64 it first verifies pinned Termux package and
native-binary hashes under ignored `.gradle/termux-aapt2/`, injects the verified
project-relative launcher path, and otherwise fails closed with the bootstrap
command. The launcher pins the runtime for every AAPT2 child process and the
bootstrap verifies a real resource compile without any global package install.

- [ ] **Step 2: Add the exact version catalog and build plugins**

Use these version values without ranges or dynamic selectors:

```toml
[versions]
agp = "9.2.1"
compose-compiler = "2.3.10"
compose-bom = "2026.06.00"
activity = "1.13.0"
core = "1.18.0"
lifecycle = "2.10.0"
webkit = "1.16.0"
datastore = "1.2.0"
coroutines = "1.10.2"
bouncycastle = "1.84"
okhttp = "5.3.0"
junit = "4.13.2"
robolectric = "4.16.1"
androidx-test-ext = "1.3.0"
androidx-test-runner = "1.7.0"
espresso = "3.7.0"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "compose-compiler" }
```

Root build script:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

Do not apply `org.jetbrains.kotlin.android`; AGP 9.2 supplies built-in Kotlin.
The build must also verify that both `androidx.core:core` and
`androidx.core:core-ktx` resolve to exactly 1.18.0 on
`debugRuntimeClasspath`; a catalog declaration alone is insufficient.

- [ ] **Step 3: Add app build configuration**

The module must contain these invariant blocks:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.junta.firmamobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.junta.firmamobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug { isDebuggable = true }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
```

Dependencies must use the stable Compose BOM, Activity Compose, Material 3,
Lifecycle ViewModel/Runtime Compose, Core KTX, DataStore Preferences, WebKit,
coroutines, the three Bouncy Castle 1.84 artifacts, OkHttp 5.3.0, and the test
libraries listed in the catalog. No Hilt, Google Play Services, SpongyCastle,
Retrofit, or permissive network helper is added.

- [ ] **Step 4: Add manifest and network/backup policies before Activity code**

The main manifest must declare only `INTERNET` and `ACCESS_NETWORK_STATE`, set
`android:name=".JuntaFirmaApplication"`, `android:allowBackup="false"`,
`android:usesCleartextTraffic="false"`,
`android:networkSecurityConfig="@xml/network_security_config"`, and export only
`.MainActivity` with the launcher intent filter.

`network_security_config.xml` must be exactly system-CA/cleartext restrictive:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

Both backup XML files must exclude the root domain and define no inclusion.

- [ ] **Step 5: Write the launch instrumentation test first**

```kotlin
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun showsNameAndUnofficialDisclosure() {
        rule.onNodeWithText("Junta Firma Mobile").assertIsDisplayed()
        rule.onNodeWithText("Cliente no oficial para uso personal").assertIsDisplayed()
        rule.onNodeWithText("Seleccionar certificado").assertIsDisplayed()
    }
}
```

- [ ] **Step 6: Run the test compile to prove the UI does not exist yet**

Run: `./gradlew compileDebugAndroidTestKotlin`

Expected: FAIL because `MainActivity`/`AppRoot` or the semantics do not exist.

- [ ] **Step 7: Add the minimal Application, Activity, theme, and setup UI**

`JuntaFirmaApplication` contains no secret initialization. `MainActivity`
extends `ComponentActivity`, enables edge-to-edge, and calls `setContent` with
Material 3 theme and `AppRoot`. `AppRoot` displays the exact app name,
disclosure, `Certificado digital`, the privacy explanation, and
`Seleccionar certificado`; it performs no navigation or signing in this task.

- [ ] **Step 8: Validate foundation**

Run:

```bash
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration lintDebug testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
./gradlew :app:dependencyInsight --dependency androidx.core:core-ktx --configuration debugRuntimeClasspath
aapt dump badging app/build/outputs/apk/debug/app-debug.apk | rg "package: name='dev.junta.firmamobile'|sdkVersion:'26'|targetSdkVersion:'36'"
```

Expected: all Gradle tasks pass and all three manifest facts match.

- [ ] **Step 9: Commit**

```bash
git add .gitignore .gitattributes build.gradle.kts settings.gradle.kts gradle.properties gradle gradlew gradlew.bat keystore.properties.example tools docs/building-on-termux.md app
git commit -m "build: scaffold secure Android application"
```

---

### Task 2: Closed error model, sensitive helpers, and sanitized logger

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/model/AppError.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/security/SensitiveData.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/security/SanitizedLoggerTest.kt`

**Interfaces:**
- Produces `enum class AppErrorCode`, `data class AppError`,
  `fun CharArray.clear()`, `fun ByteArray.clear()`, `fun sha256Prefix`, and
  `SanitizedLogger.record(LogEvent)` / `snapshot()` / `clear()` / `exportText()`.
- Consumers never pass arbitrary maps or throwable messages to the logger.

- [ ] **Step 1: Write failing allowlist logger tests**

Test exact output from a fixed `Clock` and assert rejected fields:

```kotlin
@Test fun recordsOnlySafeTypedMetadata() {
    logger.record(LogEvent.ProtocolObserved(
        host = "www.juntadeandalucia.es",
        operation = "sign",
        algorithm = "SHA1withRSA",
        format = "CAdES",
        valueLength = 4824,
        sha256Prefix = "12ab34cd",
    ))
    val text = logger.exportText()
    assertThat(text).contains("valueLength=4824")
    assertThat(text).doesNotContain("dat=")
}

@Test fun hashesExposeExactlyEightLowerHexCharacters() {
    assertThat(sha256Prefix("secret".encodeToByteArray())).matches("[0-9a-f]{8}")
}
```

Also test bounded ring capacity, deterministic ordering, clear, newline/control
sanitization, and release/debug level filtering.

- [ ] **Step 2: Run red**

Run: `./gradlew testDebugUnitTest --tests '*SanitizedLoggerTest'`

Expected: FAIL because typed events/helpers are absent.

- [ ] **Step 3: Implement closed typed events and errors**

`AppErrorCode` contains every code from `docs/spec.md`; `AppError` contains only
`code`, a localized message resource key, recoverability, and a safe details
object. `LogEvent` is a sealed interface with typed constructors; it has no raw
`message: String`, cookie, URI query, certificate, or throwable field. Store at
most 500 lines in app-private storage and escape control characters.

- [ ] **Step 4: Run green and global secret-name guard**

```bash
./gradlew testDebugUnitTest --tests '*SanitizedLoggerTest'
rg -n 'Log\.(d|i|w|e)\(|println\(' app/src/main/java && exit 1 || true
```

Expected: tests pass; no direct production logging calls.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/model app/src/main/java/dev/junta/firmamobile/security app/src/test/java/dev/junta/firmamobile/security
git commit -m "feat: add sanitized diagnostics boundary"
```

---

### Task 3: Exact origins and SSRF-safe URL validation

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/network/AllowedOrigins.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/network/SafeUrlValidator.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/OriginAllowlistTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/SafeUrlValidatorTest.kt`

**Interfaces:**
- `AllowedOrigins.isAllowedOrigin(uri: Uri): Boolean`
- `AllowedOrigins.isAllowedHost(host: String): Boolean`
- `SafeUrlValidator.validate(raw: String, purpose: UrlPurpose): SafeUrlResult`
- `suspend fun SafeUrlValidator.resolveAndValidate(url: HttpUrl): SafeUrlResult`

- [ ] **Step 1: Write table-driven failing tests**

Include all six HTTPS origins plus HTTP, suffix phishing, unlisted subdomain,
Unicode/punycode, trailing dot, userinfo, port 444, localhost, IPv4/IPv6
loopback, RFC1918, link-local, documentation/reserved IP, `javascript:`, `file:`,
`content:`, `data:`, and redirect escape. Use a fake `DnsResolver` returning
controlled `InetAddress` values; no public DNS in unit tests.

```kotlin
@Test fun phishingSuffixIsRejected() {
    assertThat(origins.isAllowedOrigin(Uri.parse(
        "https://www.juntadeandalucia.es.evil.example/path"
    ))).isFalse()
}

@Test fun callbackResolvingPrivateAddressIsRejected() = runTest {
    val validator = validator(dns = fakeDns("ws024.juntadeandalucia.es", "10.0.0.4"))
    assertThat(validator.resolveAndValidate(validWs024Url).error?.code)
        .isEqualTo(AppErrorCode.CALLBACK_URL_REJECTED)
}
```

- [ ] **Step 2: Run red**

Run: `./gradlew testDebugUnitTest --tests '*OriginAllowlistTest' --tests '*SafeUrlValidatorTest'`

Expected: FAIL because validators are absent.

- [ ] **Step 3: Implement canonical exact matching**

Normalize scheme/host with `Uri`, `HttpUrl`, `IDN.toASCII`, lowercase
`Locale.ROOT`, reject trailing dot ambiguity and any userinfo. Callback/server
purposes require port 443. Browser external links may use HTTPS only and never
receive cookies. DNS results must all be globally routable and the HTTP layer
must invoke the validator again for each redirect.

- [ ] **Step 4: Run green**

Run: `./gradlew testDebugUnitTest --tests '*OriginAllowlistTest' --tests '*SafeUrlValidatorTest'`

Expected: all cases pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/network/AllowedOrigins.kt app/src/main/java/dev/junta/firmamobile/network/SafeUrlValidator.kt app/src/test/java/dev/junta/firmamobile/network
git commit -m "feat: enforce Junta origin and SSRF allowlists"
```

---

### Task 4: AutoFirma URI parser and one-shot request store

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/afirma/AfirmaOperation.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/afirma/AfirmaRequest.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/afirma/AfirmaUriParser.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/afirma/PendingSignRequestStore.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/afirma/AfirmaUriParserTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/afirma/PendingSignRequestStoreTest.kt`

**Interfaces:**

```kotlin
enum class AfirmaOperation { SIGN, SELECT_CERTIFICATE, WEBSOCKET }
data class AfirmaParameter(val name: String, val rawValue: String, val decodedValue: String)
data class AfirmaRequest(
    val rawUri: String,
    val operation: AfirmaOperation,
    val origin: Origin,
    val parameters: Map<String, AfirmaParameter>,
)
sealed interface AfirmaParseResult
fun AfirmaUriParser.parse(rawUri: String, sourceOrigin: Uri): AfirmaParseResult
```

`PendingSignRequestStore.create`, `getActive`, `consume`, `cancel`, and
`invalidateNavigation` operate on UUID+origin+navigationId and reject reuse.

- [ ] **Step 1: Write all required parser tests before production code**

Use literal fixtures for valid sign, percent encoding, base64url, duplicate
`dat`, missing algorithm, unknown operation, over-limit URI, external/localhost/
javascript callback, and double encoding. Add duplicate `algorithm`/`format`,
mixed-case critical names, empty values, invalid percent triplets, and origin
mismatch. Assert raw and decoded values separately.

- [ ] **Step 2: Run red**

Run: `./gradlew testDebugUnitTest --tests '*AfirmaUriParserTest' --tests '*PendingSignRequestStoreTest'`

Expected: test compile fails because parser/store types do not exist.

- [ ] **Step 3: Implement bounded single decoding with android.net.Uri**

Set the URI maximum to 131072 UTF-16 code units and reject before parsing.
Use `Uri.parse`, but split raw query components to preserve each raw value;
apply `Uri.decode` exactly once. Critical names are `dat`, `algorithm`,
`format`, `serverurl`, `rtservlet`, `stservlet`, `id`, `key`, `fileid`, `cop`,
`deskey`, and `cipherkey`; duplicates reject the request. Validate every URL
parameter with Task 3.

- [ ] **Step 4: Implement single-use pending state**

Use immutable entries, injected `Clock`, maximum age five minutes, constant-time
state transitions on the main dispatcher, and removal on all terminal paths.
Never serialize an entry to Bundle, disk, SavedStateHandle, or logs.

- [ ] **Step 5: Run green**

Run: `./gradlew testDebugUnitTest --tests '*AfirmaUriParserTest' --tests '*PendingSignRequestStoreTest'`

Expected: all parser and lifecycle cases pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/afirma app/src/test/java/dev/junta/firmamobile/afirma
git commit -m "feat: parse bounded AutoFirma requests"
```

---

### Task 5: SAF certificate repository and memory-only session

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateSummary.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/UnlockedIdentity.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/Pkcs12Loader.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/certificate/CertificateSession.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/certificate/TestCertificateFactory.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/certificate/Pkcs12LoaderTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateRepositoryTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/certificate/CertificateSessionTest.kt`

**Interfaces:**

```kotlin
data class CertificateSummary(val displayName: String, val issuerName: String, val validUntil: Instant)
class UnlockedIdentity internal constructor(
    internal val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val chain: List<X509Certificate>,
    val summary: CertificateSummary,
)
sealed interface CertificateLoadResult
suspend fun Pkcs12Loader.load(input: InputStream, size: Long?, password: CharArray): CertificateLoadResult
interface CertificateRepository {
    suspend fun select(uri: Uri): Result<StoredCertificateReference>
    suspend fun unlock(password: CharArray): CertificateLoadResult
    suspend fun forget()
}
```

- [ ] **Step 1: Build synthetic certificate/P12 fixtures in test code**

`TestCertificateFactory` uses Bouncy Castle only to create clearly synthetic RSA
and EC identities: valid RSA, wrong password, certificate-only, expired,
not-yet-valid, two-entry, no digitalSignature keyUsage, mismatched key, and
two-level chain. It never writes fixture bytes outside the test temp directory.

- [ ] **Step 2: Write failing loader/session tests**

Cover every case in `docs/test-plan.md`. `CertificateRepositoryTest` additionally
uses a fake content boundary to prove persistable read permission,
MIME/extension validation, URI-only persistence, reopening after repository
recreation, forget/release behavior, and absence of password/P12 bytes in stored
preferences. Pass a password copy, then assert the caller-owned array remains
under caller control while every internal copy is zeroed via an injected
`SensitiveCopyObserver` available only in tests. Assert `UnlockedIdentity` has
no public key-byte export method.

- [ ] **Step 3: Run red**

Run: `./gradlew testDebugUnitTest --tests '*Pkcs12LoaderTest' --tests '*CertificateRepositoryTest' --tests '*CertificateSessionTest'`

Expected: FAIL because certificate classes are absent.

- [ ] **Step 4: Implement bounded PKCS#12 validation**

Limit input to 10 MiB before `KeyStore.load`. Enumerate aliases with a maximum
of 32, require one selected `PrivateKeyEntry`, parse at most 16 X.509 chain
entries, call `checkValidity`, inspect keyUsage bit 0 if present, require RSA,
and prove private/public correspondence by signing 32 random bytes with
`SHA256withRSA` and verifying with the certificate. Do not call
`privateKey.encoded`.

Map exceptions to the certificate error codes without retaining exception
messages. Zero password copies and challenge bytes in `finally`.

- [ ] **Step 5: Implement SAF persistence without copying the P12**

Use `ACTION_OPEN_DOCUMENT`, `CATEGORY_OPENABLE`, MIME
`application/x-pkcs12` plus `application/octet-stream` fallback, verify the
display-name extension `.p12`/`.pfx` for fallback MIME, call
`takePersistableUriPermission`, and store only the URI string and safe summary
in DataStore. Never store password or alias private metadata.

`CertificateSession` supports locked/unlocked, ten-minute optional expiry,
background expiry, manual lock, `onTrimMemory`, and process-death-by-design.

- [ ] **Step 6: Run green and static secret checks**

```bash
./gradlew testDebugUnitTest --tests '*Pkcs12LoaderTest' --tests '*CertificateRepositoryTest' --tests '*CertificateSessionTest'
rg -n 'password.*(put|preference)|PrivateKey.*encoded|\.getEncoded\(\)' app/src/main/java/dev/junta/firmamobile/certificate && exit 1 || true
```

Expected: tests pass and forbidden patterns are absent.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/certificate app/src/test/java/dev/junta/firmamobile/certificate
git commit -m "feat: validate PKCS12 identities in memory"
```

---

### Task 6: Local RSA and CAdES signing engines

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/signing/SignatureResult.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/LocalSignatureEngine.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/CadesSigner.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/signing/LocalSignatureEngineTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/signing/CadesSignerTest.kt`

**Interfaces:**

```kotlin
enum class SigningAlgorithm(val jcaName: String) { SHA1_RSA("SHA1withRSA"), SHA256_RSA("SHA256withRSA") }
data class SigningPolicy(val allowLegacySha1ForObservedLogin: Boolean)
sealed interface SignatureResult
fun LocalSignatureEngine.sign(data: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm, policy: SigningPolicy): SignatureResult
fun CadesSigner.signDetached(data: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm, policy: SigningPolicy): SignatureResult
```

- [ ] **Step 1: Write failing known-answer and mutation tests**

Generate a fixed-seed synthetic test identity and verify SHA256withRSA with the
public key; assert one-byte mutation fails, unsupported/EC keys reject, SHA-1
rejects under default policy and passes only under the explicit observed-login
policy. For CAdES, parse `CMSSignedData`, verify signer info, messageDigest,
certificate inclusion, detached content semantics, and mutation failure.

- [ ] **Step 2: Run red**

Run: `./gradlew testDebugUnitTest --tests '*LocalSignatureEngineTest' --tests '*CadesSignerTest'`

Expected: FAIL because signing classes are absent.

- [ ] **Step 3: Implement with JCA and explicit BC builders**

Use `Signature.getInstance` for raw RSA and Bouncy Castle CMS/JCA builders for
CAdES. Pass a provider instance to builders only when required; do not call
`Security.addProvider`, `insertProviderAt`, or modify global provider order.
Return owned result bytes and clear temporary digest/content copies.

- [ ] **Step 4: Run green and provider guard**

```bash
./gradlew testDebugUnitTest --tests '*LocalSignatureEngineTest' --tests '*CadesSignerTest'
rg -n 'Security\.(addProvider|insertProviderAt)' app/src/main && exit 1 || true
```

Expected: tests pass and no global provider registration exists.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/signing app/src/test/java/dev/junta/firmamobile/signing
git commit -m "feat: add bounded RSA and CAdES signing"
```

---

### Task 7: Cookie isolation, HTTPS client, and hardened protocol parsing shell

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/browser/WebViewCookieBridge.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/network/JuntaCookieJar.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/network/JuntaHttpClient.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/TriPhaseClient.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/CookieBridgeTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/JuntaHttpClientTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/signing/TriPhaseClientContractTest.kt`

**Interfaces:**

```kotlin
interface CookieStoreAdapter { fun get(url: String): String?; fun set(url: String, value: String); fun flush() }
interface JuntaHttpClient { suspend fun execute(request: SafeHttpRequest): SafeHttpResponse }
sealed interface TriPhaseResult
interface TriPhaseClient { suspend fun sign(request: ObservedTriPhaseRequest, signer: LocalPreSigner): TriPhaseResult }
```

- [ ] **Step 1: Write failing cookie/session/response-bound tests**

Test same-host cookie transfer, cross-host denial, Set-Cookie synchronization,
per-hop redirect validation, 401/403/login redirect, HTML login with status 200,
wrong content type, more than five redirects, connect/read/write/call timeout
configuration, and bodies above 2 MiB. Assert captured test logs contain no
cookie value.

- [ ] **Step 2: Write hardened XML parser tests**

The contract test must reject DOCTYPE, external entity, external DTD,
billion-laughs structure, malformed XML, unexpected root, and body over limit.
At this stage a syntactically safe but unobserved tri-phase payload returns
`AFIRMA_OPERATION_UNSUPPORTED`; it must not trigger network signing.

- [ ] **Step 3: Run red**

Run: `./gradlew testDebugUnitTest --tests '*CookieBridgeTest' --tests '*JuntaHttpClientTest' --tests '*TriPhaseClientContractTest'`

Expected: FAIL because boundaries are absent.

- [ ] **Step 4: Implement restricted HTTP and XML shell**

Configure OkHttp with 10 s connect, 20 s read, 20 s write, 45 s call timeout,
`followRedirects(false)`, `followSslRedirects(false)`, default TLS/hostname
verification, no cache, and a network interceptor that enforces response size.
Drive redirects manually through Task 3 validation. Cookie adapters operate on
the exact URL and never share a cookie across hosts.

Create an XML factory with secure processing, DOCTYPE disabled, external
general/parameter entities false, external DTD disabled, and an empty external
entity resolver. The `TriPhaseClient` public interface exists, but its network
implementation refuses unobserved wire data by design.

- [ ] **Step 5: Run green and TLS anti-pattern guard**

```bash
./gradlew testDebugUnitTest --tests '*CookieBridgeTest' --tests '*JuntaHttpClientTest' --tests '*TriPhaseClientContractTest'
rg -n 'TrustAll|X509TrustManager|hostnameVerifier|sslSocketFactory|handler\.proceed\(' app/src/main && exit 1 || true
```

Expected: tests pass and no bypass pattern exists.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/browser/WebViewCookieBridge.kt app/src/main/java/dev/junta/firmamobile/network app/src/main/java/dev/junta/firmamobile/signing/TriPhaseClient.kt app/src/test/java/dev/junta/firmamobile/network app/src/test/java/dev/junta/firmamobile/signing/TriPhaseClientContractTest.kt
git commit -m "feat: isolate cookies and harden protocol HTTP"
```

---

### Task 8: Trusted WebView navigation and external-intent policy

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/browser/WebViewStateHolder.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebChromeClient.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/TrustedJuntaWebView.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/afirma/AfirmaRequestRouter.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/afirma/LegacyAfirmaPayloadCodec.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/afirma/AfirmaRequestRouterTest.kt`

**Interfaces:**
- `TrustedJuntaWebView.configure(debug: Boolean)` applies all settings once.
- `JuntaWebViewClient` delegates AutoFirma requests to `AfirmaRequestRouter`.
- `ExternalNavigator.openHttps(uri)` is the only route to system browser.
- `LegacyAfirmaPayloadCodec` is a rejecting implementation until DES is observed.

- [ ] **Step 1: Write failing router/navigation tests**

Test allowed HTTPS returns `false` without calling `loadUrl`; external HTTPS is
validated and dispatched; HTTP is rejected; `afirma://sign` routes internally;
intent fallback containing afirma routes internally; market/Play AutoFirma is
blocked with `PLAY_STORE_FALLBACK_INTERCEPTED`; arbitrary package/component,
javascript/data/file/content and malformed intent are rejected.

- [ ] **Step 2: Run red**

Run: `./gradlew testDebugUnitTest --tests '*AfirmaRequestRouterTest'`

Expected: FAIL because router/WebView policy is absent.

- [ ] **Step 3: Implement secure WebView settings**

Enable JavaScript, DOM storage, cookies, database storage only as required by
WebView; disable mixed content, file access, content access unless the SAF
chooser needs it outside WebView, file-URL network access, universal file access,
geolocation, multiple windows, and media autoplay. Enable Safe Browsing via
feature check. Call `WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)`
once from Application and never from release-specific code.

Override `onReceivedSslError` with unconditional `handler.cancel()`, handle
`onRenderProcessGone`, main-frame network errors, downloads, file chooser via
SAF, and reject `onCreateWindow`.

- [ ] **Step 4: Implement intent parsing conservatively**

Use `Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)`, inspect `data`,
`browserFallbackUrl`, package and component. Never start an intent carrying
AutoFirma data. Only a validated HTTPS URL can leave via `ExternalNavigator`.
The legacy codec returns unsupported and logs only the operation/error code.

- [ ] **Step 5: Run green plus source guards**

```bash
./gradlew testDebugUnitTest --tests '*AfirmaRequestRouterTest'
rg -n 'shouldOverrideUrlLoading[\s\S]{0,500}loadUrl' app/src/main/java/dev/junta/firmamobile/browser && exit 1 || true
rg -n '63117|63118|63119|17629|localhost|127\.0\.0\.1' app/src/main && exit 1 || true
```

Expected: tests pass; no normal-navigation `loadUrl` or localhost service code.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/browser app/src/main/java/dev/junta/firmamobile/afirma app/src/test/java/dev/junta/firmamobile/afirma
git commit -m "feat: add trusted Junta WebView policy"
```

---

### Task 9: Origin-bound MiniApplet shim and WebMessage protocol

**Files:**
- Create: `app/src/main/res/raw/afirma_shim.js`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/browser/WebMessageProtocolTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt`

**Interfaces:**

```kotlin
sealed interface BridgeMessage
data class SignBridgeRequest(val requestId: UUID, val origin: Origin, val data: String, val algorithm: String, val format: String, val properties: String?) : BridgeMessage
data class BridgeResult(val requestId: UUID, val status: BridgeStatus, val result: String?, val errorCode: AppErrorCode?)
fun WebMessageBridge.install(webView: WebView): Closeable
fun WebMessageBridge.deliver(result: BridgeResult)
```

- [ ] **Step 1: Write failing JSON and lifecycle tests**

Test schema version 1, known message types only, 131072-character maximum,
required UUID, exact source origin, matching current main-frame origin,
navigation id, unknown/duplicate fields where security-critical, one-shot result,
origin change, cancellation, and JS-injection strings. Assert parse failures do
not echo payload values.

- [ ] **Step 2: Write shim source invariant tests**

Load the raw resource text and assert it contains no `eval`, `Function(`,
`innerHTML`, filesystem/API execution method, hardcoded callback name
`saveSignatureAuthCallback`, localhost port, or automatic call to original
`sign`. Assert it wraps `cargarMiniApplet`, `sign`, and `selectCertificate`,
stores callbacks in a closure Map keyed by UUID, and deletes them on all terminal
results.

- [ ] **Step 3: Run red**

Run: `./gradlew testDebugUnitTest --tests '*WebMessageProtocolTest' --tests '*AfirmaJavascriptShimTest'`

Expected: FAIL because bridge/shim are absent.

- [ ] **Step 4: Implement document-start interception**

At document start, define a property interceptor for global `MiniApplet`; when
the public script assigns its object, wrap only the observed methods. Preserve
the page-provided success/error function references in a private closure. A
`sign` call creates a random UUID using `crypto.randomUUID`, sends typed JSON via
the injected WebMessage object, and does not call the original method. Result
messages use `WebViewCompat.postWebMessage`/reply proxy with JSON and invoke the
stored function reference—never string-built JavaScript.

Install both document-start script and message listener only when
`DOCUMENT_START_SCRIPT` and `WEB_MESSAGE_LISTENER` features are supported and
only for the six exact HTTPS origins. If unavailable, show a compatibility error
and keep signing disabled rather than fall back to `addJavascriptInterface`.

- [ ] **Step 5: Run green and bridge guards**

```bash
./gradlew testDebugUnitTest --tests '*WebMessageProtocolTest' --tests '*AfirmaJavascriptShimTest'
rg -n 'addJavascriptInterface|evaluateJavascript\(' app/src/main && exit 1 || true
```

Expected: tests pass; neither unsafe bridge nor concatenated JS delivery exists.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/raw/afirma_shim.js app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt app/src/test/java/dev/junta/firmamobile/browser
git commit -m "feat: intercept MiniApplet with origin-bound messages"
```

---

### Task 10: Complete Material 3 UI and signing coordinator state machine

**Files:**
- Create/modify all files under `app/src/main/java/dev/junta/firmamobile/ui/`.
- Create: `app/src/main/java/dev/junta/firmamobile/model/AppState.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/model/SigningState.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Test: `app/src/androidTest/java/dev/junta/firmamobile/CertificateSetupFlowTest.kt`

**Interfaces:**
- `AppState` owns certificate reference/lock state, browser state, diagnostics,
  and `SigningState`; it never contains password or private key.
- `SigningCoordinator.prepare`, `confirm`, `cancel`, and `retry` enforce the
  one-shot state machine and call Task 7 tri-phase only when an observed codec is active.

- [ ] **Step 1: Write UI/state tests first**

Verify first screen Spanish copy, SAF launcher, password field secure flag,
summary fields, continue/choose-other, top bar back/refresh/menu, all menu items,
certificate status, signature confirmation fields/buttons, SHA-1 warning,
progress copy, success, error code, retry/diagnostic, and no automatic call to
signer before `Firmar` click.

- [ ] **Step 2: Run red compile/test**

Run: `./gradlew compileDebugAndroidTestKotlin`

Expected: FAIL because full UI/state APIs are absent.

- [ ] **Step 3: Implement immutable state machine**

States are `Idle`, `AwaitingConfirmation`, `Signing`, `Completed`, `Failed`, and
`Cancelled`. Only `AwaitingConfirmation -> Signing` on explicit user action is
legal. Background/navigation/certificate lock cancels pending work. Retry creates
a new requestId only after the prior request is terminal and delivery is known
not to have occurred.

- [ ] **Step 4: Implement UI and lifecycle security**

Use `FLAG_SECURE` while password UI is visible and clear it after dismissal.
Use `rememberLauncherForActivityResult(OpenDocument)` with persistable flags.
Use AndroidView for the single WebView instance, save/restore history through
`WebViewStateHolder`, route system Back through `canGoBack`, and destroy WebView
on final Activity destruction. Implement menu actions exactly as specified;
session clearing clears CookieManager, WebStorage, cache, history, and reloads
the initial URL after confirmation.

- [ ] **Step 5: Validate compile and unit suite**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug`

Expected: all unit tests and both APK/test compilation pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/model app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt app/src/main/java/dev/junta/firmamobile/ui app/src/main/java/dev/junta/firmamobile/MainActivity.kt app/src/androidTest/java/dev/junta/firmamobile/CertificateSetupFlowTest.kt
git commit -m "feat: add confirmed signing user flow"
```

---

### Task 11: Android instrumentation coverage for WebView boundaries

**Files:**
- Create: `app/src/androidTest/java/dev/junta/firmamobile/TrustedWebViewTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/AfirmaInterceptionTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/WebMessageOriginTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/ConfigurationStateTest.kt`
- Modify production code only through dependency injection seams exposed by Tasks 8–10.

**Interfaces:**
- Tests inject `ExternalNavigator`, router, clock, certificate stream provider,
  controlled WebView content, and cookie adapter; release code paths remain unchanged.

- [ ] **Step 1: Write the mandatory instrumentation cases**

Use `WebViewAssetLoader` or a controlled HTTPS test interceptor to serve pages
under test-only origins without adding cleartext exceptions. Cover start URL,
history Back, afirma sign, Play fallback, external browser intent, SSL cancel,
bridge origin/subframe denial, new-window denial, state recreation, lock after
background, and user cancellation.

- [ ] **Step 2: Compile tests red before adding missing seams**

Run: `./gradlew compileDebugAndroidTestKotlin`

Expected: FAIL only for the explicit injection seams named by the tests.

- [ ] **Step 3: Add the smallest test seams**

Use internal constructors/interfaces and debug dependency providers. Do not add
release bypass flags, broad origins, cleartext config, SSL test exceptions, or
`BuildConfig` conditionals inside validators.

- [ ] **Step 4: Run instrumentation on Android 16**

Prefer connected ADB. If absent, copy APKs to shared storage and use rish/pm
with instrumentation shell commands. Exact commands:

```bash
./gradlew installDebug installDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

If `adb devices` is empty, derive package paths and run:

```bash
rish -c "pm install -r /sdcard/Codex/Work/junta-firma-mobile/app-debug.apk"
rish -c "pm install -r /sdcard/Codex/Work/junta-firma-mobile/app-debug-androidTest.apk"
rish -c "am instrument -w dev.junta.firmamobile.test/androidx.test.runner.AndroidJUnitRunner"
```

Expected: zero failures; XML result files identify API 36 device.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest app/src/main
git commit -m "test: cover Android WebView security boundaries"
```

---

### Task 12: Debug-only real portal protocol observation

**Files:**
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt`
- Create: `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolObservationRecorder.kt`
- Modify: `docs/protocol-observations.md`
- Create/update sanitized regression fixtures under `app/src/test/resources/protocol/` containing names, structure, lengths, and synthetic replacement values only.

**Interfaces:**
- Debug Activity uses production `TrustedJuntaWebView` and bridge.
- `ProtocolObservationRecorder` accepts typed metadata, not arbitrary strings,
  and emits an export with parameter names, lengths, SHA-256 prefixes, algorithms,
  formats, callback hosts/status/content type, and operation sequence.

- [ ] **Step 1: Write recorder tests before debug implementation**

Assert an observed direct `MiniApplet.sign` record includes origin, operation,
algorithm, format, each parameter length/hash prefix, and server host but not
synthetic raw values. Feed canary secrets and assert none occur in export.

- [ ] **Step 2: Run red**

Run: `./gradlew testDebugUnitTest --tests '*ProtocolObservationRecorderTest'`

Expected: FAIL because debug recorder is absent.

- [ ] **Step 3: Implement the debug probe without a signing bypass**

The debug manifest exports `.browser.ProtocolProbeActivity` solely in debug.
The Activity opens the real start URL, installs the same secure bridge, and
stops at native confirmation. It records metadata in app-private storage and
offers sanitized export. It never records bodies, callbacks values, cookies,
full URLs with query, certificate, signature, or passwords.

- [ ] **Step 4: Install and drive the probe on the API 36 POCO**

```bash
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/Codex/Work/junta-firma-mobile/app-debug.apk
rish -c "pm install -r /sdcard/Codex/Work/junta-firma-mobile/app-debug.apk"
rish -c "am start -n dev.junta.firmamobile/.browser.ProtocolProbeActivity"
rish -c "uiautomator dump /sdcard/Codex/Work/junta-firma-mobile/probe-ui.xml"
rish -c "screencap -p /sdcard/Codex/Work/junta-firma-mobile/probe.png"
rish -c "logcat -d -v threadtime dev.junta.firmamobile:V '*:S'"
```

Use UIAutomator/input only for non-secret navigation. Do not automate a password
or legal consent. Trigger the login-sign action far enough to observe the bridge
request, then cancel at the native confirmation.

- [ ] **Step 5: Derive evidence and update protocol documentation**

Record exact operation sequence, parameter names, lengths/hash prefixes,
algorithm/format, endpoint hosts/paths, HTTP methods/status/content types,
redirects and delivery mechanism. Confirm or revise the current static inference
about SHA-1, direct callback, and tri-phase. Verify official ownership before any
new allowlisted host and add its validator test first.

- [ ] **Step 6: Audit artifacts for secrets**

Run canary/field searches against sanitized export, logcat, UI XML, and docs.
Delete any capture that contains a raw sensitive value; fix the recorder under
systematic debugging, add a regression test, and repeat the observation.

- [ ] **Step 7: Commit evidence only**

```bash
git add app/src/debug app/src/test/resources/protocol docs/protocol-observations.md
git commit -m "docs: record sanitized Junta signing protocol"
```

Do not commit screenshots/UI XML/logcat if they contain user/session data.

---

### Task 13: Evidence-derived tri-phase codec and result delivery plan gate

**Files:**
- Create: `docs/superpowers/specs/YYYY-MM-DD-junta-triphase-contract-design.md`
- Create: `docs/superpowers/plans/YYYY-MM-DD-junta-triphase-contract-implementation.md`
- Modify later only after those documents pass review:
  `TriPhaseClient.kt`, `SigningCoordinator.kt`, protocol fixtures/tests, bridge result mapping, and `protocol-observations.md`.

**Interfaces:**
- The follow-up design must define exact request/response types, encoding,
  cookie hosts, redirects, XML/JSON grammar, local bytes-to-sign, post-sign body,
  final result and JS callback contract from Task 12 evidence.

- [ ] **Step 1: Convert runtime evidence into a closed protocol schema**

List every observed field with type, optionality, maximum length, decoding count,
sensitivity, transport location and validation. Replace sensitive fixture values
with deterministic synthetic bytes of identical structural shape. Include HTTP
method, content type, response root/fields, redirect rules and failure mapping.

- [ ] **Step 2: Re-run brainstorming on the wire contract without asking the user**

Compare direct tri-phase, Storage/Retrieve, form POST and hybrid delivery against
the evidence. Select the narrowest matching approach under the user's standing
recommended-option approval. Preserve all threat-model constraints.

- [ ] **Step 3: Write a TDD plan with exact fixture-derived code**

The follow-up implementation plan must contain failing tests for pre-sign parse,
local bytes-to-sign, post-sign serialization, final result parse, cookie/session,
redirect/login, wrong content type, XXE/oversize, cancellation and callback
delivery. It must not contain unknown field names or guessed callback functions.

- [ ] **Step 4: Implement that plan before continuing**

Use subagent-driven development with per-task spec and code review, or inline
execution if multi-agent capacity is unavailable. Run all prior tests after each
wire-codec task. Keep `LegacyAfirmaPayloadCodec` rejecting unless DES is directly
observed; if observed, add its own design, vectors and threat update first.

- [ ] **Step 5: Commit the implemented observed contract**

Expected commit subject: `feat: complete observed Junta tri-phase signing`.

This gate is intentionally evidence-dependent; skipping it would violate the
product requirement not to guess AutoFirma's callback or legacy protocol.

---

### Task 14: Full Android 16 E2E with authorized certificate

**Files:**
- Create: `docs/test-report.md`
- Modify: `docs/protocol-observations.md` only for safe new evidence.
- Modify tests/code only after a reproduced failure, one hypothesis, and a regression test.

**Interfaces:**
- Produces authoritative acceptance evidence for the entire user flow.

- [ ] **Step 1: Build a release candidate without minification**

Run:

```bash
./gradlew clean testDebugUnitTest connectedDebugAndroidTest assembleDebug assembleRelease
```

Expected: every test task passes and both APKs exist.

- [ ] **Step 2: Install and launch on API 36**

Use ADB if connected; otherwise copy the APK to the scoped Codex work directory
and install with rish. Confirm package/version/activity with `dumpsys package`
and `am start -W`.

- [ ] **Step 3: Complete the exact 14-step E2E checklist**

Follow section 5 of `docs/test-plan.md`. The user enters the real P12 password
directly in the app; no automation, shell command, screenshot, log or report may
contain it. Confirm SSO, no Play/AutoFirma escape, native confirmation, completed
signature, portal acceptance/continuation, re-lock, and password requirement.

- [ ] **Step 4: Collect safe evidence**

Capture UI tree/screenshot only on screens without personal data; capture
filtered logcat and audit it for forbidden field names/canaries. Record commands,
exit results, device API/model, portal outcome, and every skipped/failed case in
`docs/test-report.md`.

- [ ] **Step 5: Debug any failure scientifically**

For a failure: reproduce, capture the exact safe stack/network metadata, state
one hypothesis, add one failing regression test, make the smallest fix, rerun
the failed path and entire relevant suite. After three failed hypotheses, stop
patching and revisit the architecture before a fourth change.

- [ ] **Step 6: Commit the passing E2E report**

```bash
git add docs/test-report.md docs/protocol-observations.md app
git commit -m "test: verify Junta signing on Android 16"
```

Do not use this commit subject or mark the task complete if portal acceptance
was not directly observed.

---

### Task 15: Release key, hardened release APK, and final verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/proguard-rules.pro`
- Create: `docs/release-checklist.md`
- Create: `docs/release-report.md`
- Output: `/storage/emulated/0/Codex/Outputs/JuntaFirmaMobile/app-release.apk`
- Private external files: `$HOME/.config/junta-firma-mobile/release-keystore.jks` and `$HOME/.config/junta-firma-mobile/keystore.properties` with mode 600; never commit or print contents.

**Interfaces:**
- Produces stable v2/v3-signed, aligned, non-debuggable release APK and fingerprint/SHA-256 report.

- [ ] **Step 1: Create private signing material if absent**

Generate a 4096-bit RSA signing key with long random store/key passwords, save
both only in Termux app-private config with directory mode 700/files 600, and
configure Gradle to load that external properties path or environment variables.
Never print passwords. If a keystore already exists, verify fingerprint and reuse
it; never overwrite it.

- [ ] **Step 2: Build and test release without shrinking once more**

Run unit/instrumentation/E2E smoke plus `assembleRelease`. Inspect merged manifest
for `debuggable=false`, `testOnly=false`, allowBackup/cleartext false, package ID,
and absence of debug Activity.

- [ ] **Step 3: Enable minify and resource shrinking only after Step 2 passes**

Set both release flags true, add the smallest keep rules required for BC/OkHttp/
WebKit reflected APIs based on actual R8 warnings, then rerun unit tests,
instrumentation and the real sign flow. A build-only pass is insufficient.

- [ ] **Step 4: Verify APK cryptographically and structurally**

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release.apk
apkanalyzer manifest application-id app/build/outputs/apk/release/app-release.apk
apkanalyzer manifest debuggable app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

Expected: v2 and v3 verified, alignment successful, application ID exact,
debuggable false, and one SHA-256 value recorded.

- [ ] **Step 5: Static release audit**

Search decompiled resource/string listings and production source for debug URL,
probe Activity, `es.gob.afirma`, localhost ports, trust bypass, SSL proceed,
`addJavascriptInterface`, secret logs and committed keystore/password files.
Review dependency licenses and final Git diff.

- [ ] **Step 6: Export without exposing signing material**

Copy only the verified APK to the output path, write fingerprint, APK SHA-256,
commands and results to `docs/release-report.md`, and explain that future updates
require the same private keystore. Do not copy properties/passwords to shared
storage.

- [ ] **Step 7: Final completion audit**

Invoke `superpowers:verification-before-completion`. Map every checkbox in the
original acceptance criteria and every case in `docs/test-plan.md` to direct
evidence. Any missing/indirect item remains incomplete; continue work rather
than claiming success.

- [ ] **Step 8: Commit release metadata and report**

```bash
git add app/build.gradle.kts app/proguard-rules.pro docs/release-checklist.md docs/release-report.md
git commit -m "release: verify Junta Firma Mobile APK"
```

Do not commit the APK, keystore, properties, passwords, screenshots with personal
data, raw logs, or protocol bodies.

---

## Stage gates and progress reporting

After each task:

1. show changed files with `git status --short` and `git diff --stat`;
2. show exact commands and fresh results;
3. state known limitations and whether they are change-caused, external, or not
   yet in scope for that stage;
4. do not start the next task while a relevant test fails;
5. commit only the reviewed task scope.

The plan deliberately includes an evidence-derived sub-plan at Task 13. The
master objective is unchanged: Tasks 1–15, including real portal acceptance and
release verification, must all be complete before the goal can be marked done.
