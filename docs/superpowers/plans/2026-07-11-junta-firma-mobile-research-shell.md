# Junta Firma Mobile Research Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce and run a secure debug research shell on the Android 16 POCO that opens the real Junta login page, blocks external AutoFirma/Play fallbacks, intercepts the page's actual MiniApplet calls before they execute, and records only safe protocol metadata in `docs/protocol-observations.md`.

**Architecture:** The launcher remains the required certificate setup screen, while a debug-only exported `ProtocolProbeActivity` hosts the same production `TrustedJuntaWebView` that the final app will use. Exact origin validation, typed sanitized logging, document-start MiniApplet interception, and a WebMessage listener form the first vertical slice. This phase stops at a native observation screen and cannot sign, transmit a signature, open an external signing app, or persist request payloads.

**Tech Stack:** Android API 26–36, AGP 9.2.1, Gradle 9.4.1, JDK 21, AGP built-in Kotlin 2.3.10, Compose compiler 2.3.10, Compose BOM 2026.06.00, Activity Compose 1.13.0, AndroidX WebKit 1.16.0, JUnit 4.13.2, Robolectric 4.16.1, AndroidX Test and Compose UI test.

## Global Constraints

- Application ID/namespace: `dev.junta.firmamobile`; never `es.gob.afirma`.
- Required names: `Junta Firma Mobile` and `Cliente no oficial para uso personal`.
- One app module; `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`; no Google Play Services.
- Main launcher shows certificate setup first. The portal probe is a separate debug-only Activity absent from release.
- Trust only the six exact HTTPS Junta origins; no wildcard, cleartext, user CA, SSL bypass, localhost service, or arbitrary intent.
- Use WebKit document-start script and web messages; no `addJavascriptInterface`, hardcoded callback name, `eval`, or string-built result JavaScript.
- Probe output may contain event, host, operation, algorithm, format, parameter names, lengths, eight-hex SHA-256 prefixes, endpoint host/status/content type, and error code.
- Probe output never contains raw `dat`, key, P12, password, private key, full certificate, signature, cookie, token, URL query, or decrypted payload.
- Probe never invokes pre-sign/post-sign, returns success to the portal, or calls the original MiniApplet sign method.
- Any failure triggers `superpowers:systematic-debugging`; completion claims require fresh verification.

---

### Task 1: Reproducible secure Android project

**Files:**
- Create: `.gitignore`, `.gitattributes`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- Create: `gradle/libs.versions.toml` and Gradle Wrapper pinned to 9.4.1
- Create: `tools/bootstrap-termux-aapt2.sh`, `docs/building-on-termux.md`
- Create: `keystore.properties.example`, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/res/xml/backup_rules.xml`
- Create: `app/src/main/res/xml/data_extraction_rules.xml`
- Create: `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/AppRoot.kt`
- Create: Material 3 theme and string resources
- Test: `app/src/androidTest/java/dev/junta/firmamobile/AppLaunchTest.kt`

**Interfaces:**
- Produces launcher `.MainActivity` and `@Composable fun AppRoot()`.
- Produces `debug` and non-debuggable `release` APK variants.
- Produces a fail-closed, project-local Termux/aarch64 AAPT2 bootstrap and
  integrity-verification contract; supported desktop hosts keep AGP's Maven AAPT2.

- [ ] **Step 1: Generate wrapper and pin the build matrix**

```bash
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
./tools/bootstrap-termux-aapt2.sh bootstrap # native Termux/aarch64 only
./gradlew --version
```

Expected: Gradle 9.4.1 starts on JDK 21. On native Termux/aarch64 the tracked
bootstrap downloads exact packages through configured Termux apt metadata,
verifies pinned archive/native hashes, and extracts only into ignored
`.gradle/termux-aapt2/`. Literal `./gradlew` verifies this installation before
injecting a project-local launcher as the experimental AAPT2 override. The
launcher pins the runtime even for existing Gradle daemons, and verification
includes a real resource compile. Missing or corrupt state fails with the
bootstrap command. No package is installed globally.

Use exact versions in `libs.versions.toml`:

```toml
[versions]
agp = "9.2.1"
compose-compiler = "2.3.10"
compose-bom = "2026.06.00"
activity = "1.13.0"
core = "1.18.0"
webkit = "1.16.0"
junit = "4.13.2"
robolectric = "4.16.1"
androidx-test-ext = "1.3.0"
androidx-test-runner = "1.7.0"
espresso = "3.7.0"
```

Root plugins are `com.android.application` 9.2.1 and
`org.jetbrains.kotlin.plugin.compose` 2.3.10. Do not apply
`org.jetbrains.kotlin.android`; AGP 9.2 supplies built-in Kotlin.
`verifyResolvedCoreVersion` must prove that both `androidx.core:core` and
`androidx.core:core-ktx` resolve to exactly 1.18.0 on `debugRuntimeClasspath`.

- [ ] **Step 2: Add manifest/network/backup policy before behavior**

The manifest has only `INTERNET` and `ACCESS_NETWORK_STATE`, application class,
`allowBackup=false`, `usesCleartextTraffic=false`, network config, and launcher
Activity. Network config trusts only `system` certificates and sets cleartext
false. Backup/data extraction rules exclude root data from cloud backup and
device transfer.

- [ ] **Step 3: Write the launch test first**

```kotlin
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun showsRequiredFirstRunCopy() {
        rule.onNodeWithText("Junta Firma Mobile").assertIsDisplayed()
        rule.onNodeWithText("Cliente no oficial para uso personal").assertIsDisplayed()
        rule.onNodeWithText("Certificado digital").assertIsDisplayed()
        rule.onNodeWithText("Seleccionar certificado").assertIsDisplayed()
    }
}
```

- [ ] **Step 4: Run red, then implement the exact first-run shell**

Run `./gradlew compileDebugAndroidTestKotlin`; expect failure because Activity/UI
types do not exist. Implement `ComponentActivity` + Material 3 `setContent`.
`AppRoot` renders the exact title, disclosure, certificate copy, privacy copy,
and selection button. This phase does not open the portal from the launcher.

- [ ] **Step 5: Validate the build contract**

```bash
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration lintDebug testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
./gradlew :app:dependencyInsight --dependency androidx.core:core-ktx --configuration debugRuntimeClasspath
aapt dump badging app/build/outputs/apk/debug/app-debug.apk | rg "package: name='dev.junta.firmamobile'|sdkVersion:'26'|targetSdkVersion:'36'"
```

Expected: tasks pass, both APKs exist, package/min/target match.

- [ ] **Step 6: Commit**

```bash
git add .gitignore .gitattributes settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat keystore.properties.example tools docs/building-on-termux.md app
git commit -m "build: scaffold secure Android research shell"
```

---

### Task 2: Typed sanitized observations

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/model/AppError.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/security/SensitiveData.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/security/SanitizedLoggerTest.kt`

**Interfaces:**

```kotlin
data class ProtocolFieldMetadata(val name: String, val length: Int, val sha256Prefix: String)
sealed interface SafeLogEvent {
    data class MiniAppletCall(
        val originHost: String,
        val operation: String,
        val algorithm: String?,
        val format: String?,
        val fields: List<ProtocolFieldMetadata>,
        val endpointHost: String?,
    ) : SafeLogEvent
    data class NavigationBlocked(
        val originHost: String?,
        val scheme: String,
        val errorCode: AppErrorCode,
    ) : SafeLogEvent
}
interface SanitizedLogger {
    fun record(event: SafeLogEvent)
    fun snapshot(): List<SafeLogEvent>
    fun clear()
    fun exportText(): String
}
```

- [ ] **Step 1: Write failing security tests**

Use canaries `RAW_DAT_CANARY`, `COOKIE_CANARY`, `PASSWORD_CANARY`, and
`SIGNATURE_CANARY`. Assert output contains operation/length/eight-hex hash but
none of the canaries, URL query delimiters, control characters, or full hashes.
Assert capacity 500, oldest eviction, deterministic clock order, clear, and
private-file export.

- [ ] **Step 2: Run red, implement a closed schema, and run green**

Run `./gradlew testDebugUnitTest --tests '*SanitizedLoggerTest'`; expect missing
types. Implement sealed events only—no arbitrary message/map/Throwable fields.
Normalize printable metadata with strict maximum lengths. Hash prefixes are
exactly eight lowercase hex. Persist only sanitized lines app-private.

```bash
./gradlew testDebugUnitTest --tests '*SanitizedLoggerTest'
rg -n 'Log\.(v|d|i|w|e)\(|println\(' app/src/main/java && exit 1 || true
```

Expected: tests pass and no direct production logging exists.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/model app/src/main/java/dev/junta/firmamobile/security app/src/test/java/dev/junta/firmamobile/security
git commit -m "feat: add sanitized protocol observations"
```

---

### Task 3: Exact Junta origins and safe navigation classification

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/network/AllowedOrigins.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/network/SafeUrlValidator.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/OriginAllowlistTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/SafeUrlValidatorTest.kt`

**Interfaces:**

```kotlin
data class WebOrigin(val scheme: String, val host: String, val port: Int)
enum class NavigationKind {
    INTERNAL_HTTPS, EXTERNAL_HTTPS, AFIRMA, INTENT, PLAY_FALLBACK, REJECTED
}
fun AllowedOrigins.parseAllowedOrigin(uri: Uri): WebOrigin?
fun SafeUrlValidator.classifyNavigation(rawUrl: String): NavigationKind
```

- [ ] **Step 1: Write failing allowlist/classification tests**

Table-drive all six HTTPS origins. Reject HTTP, wildcard/unlisted subdomain,
suffix phishing, Unicode confusable/punycode, trailing dot, userinfo, non-443
port, localhost/private IP, and file/content/javascript/data. Classify afirma,
intent, market and Play AutoFirma separately without opening them.

- [ ] **Step 2: Run red, implement canonical matching, and run green**

Run the two test classes and expect missing types. Implement with
`android.net.Uri`, lowercase `Locale.ROOT`, `IDN.toASCII`, explicit default port
443, no userinfo, and exactly six hosts. Classification performs no intent launch
or network request.

```bash
./gradlew testDebugUnitTest --tests '*OriginAllowlistTest' --tests '*SafeUrlValidatorTest'
```

Expected: every positive/negative case passes.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/network app/src/test/java/dev/junta/firmamobile/network
git commit -m "feat: restrict research shell to Junta origins"
```

---

### Task 4: Minimal secure TrustedJuntaWebView and debug probe Activity

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/browser/TrustedJuntaWebView.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebChromeClient.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/WebViewStateHolder.kt`
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewPolicyTest.kt`
- Test: `app/src/androidTest/java/dev/junta/firmamobile/TrustedWebViewTest.kt`

**Interfaces:**

```kotlin
interface ProbeNavigationSink {
    fun onAfirmaLikeNavigation(rawUrl: String, sourceOrigin: WebOrigin?)
}
interface ExternalNavigator { fun openValidatedHttps(uri: Uri) }
class TrustedJuntaWebView(context: Context, attrs: AttributeSet? = null) : WebView(context, attrs)
```

- [ ] **Step 1: Write policy tests first**

Assert allowed HTTPS returns false without `loadUrl`; external HTTPS delegates
only its validated URI; afirma/intent/market/Play go only to the probe sink;
malformed/unsafe URLs are blocked. Assert SSL cancels, new windows reject, file
chooser uses SAF, and renderer death becomes a recoverable state.

- [ ] **Step 2: Run red and implement settings/navigation**

Enable JavaScript, DOM storage, cookies, and Safe Browsing via feature check.
Disable mixed content, file/content access, file URL network/universal access,
multiple windows and geolocation. Enable WebView debugging only with
`BuildConfig.DEBUG`. Allowed pages return false. External HTTPS uses
`ExternalNavigator`; no other intent starts. SSL errors always cancel.

The debug Activity has back/refresh/title/disclosure, loads the exact start URL
automatically, restores WebView history, and displays observation status.

- [ ] **Step 3: Run green and source guards**

```bash
./gradlew testDebugUnitTest --tests '*JuntaWebViewPolicyTest'
./gradlew assembleDebug compileDebugAndroidTestKotlin
rg -n 'handler\.proceed\(|addJavascriptInterface|63117|63118|63119|17629' app/src && exit 1 || true
```

Expected: tests/build pass and forbidden patterns are absent.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/browser app/src/debug app/src/test/java/dev/junta/firmamobile/browser app/src/androidTest/java/dev/junta/firmamobile/TrustedWebViewTest.kt
git commit -m "feat: add secure Junta WebView probe"
```

---

### Task 5: Document-start MiniApplet metadata bridge

**Files:**
- Create: `app/src/main/res/raw/afirma_shim.js`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt`
- Create: `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolObservationRecorder.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/browser/WebMessageProtocolTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/browser/ProtocolObservationRecorderTest.kt`

**Interfaces:**

```kotlin
data class ProbeBridgeRequest(
    val version: Int,
    val requestId: UUID,
    val navigationId: UUID,
    val operation: String,
    val data: String?,
    val algorithm: String?,
    val format: String?,
    val properties: String?,
)
fun WebMessageBridge.install(
    webView: WebView,
    recorder: ProtocolObservationRecorder,
): Closeable
```

- [ ] **Step 1: Write failing schema, redaction, and shim tests**

Test version 1, UUIDs, 131072-character limit, known operations, exact source
origin, matching main-frame origin/navigation ID, malformed JSON, replay and
navigation change. Feed raw canaries and assert recorder retains only
name/length/hash prefix. Assert shim wraps `cargarMiniApplet`, `sign`, and
`selectCertificate`, uses `crypto.randomUUID` and private callback/request Map,
and contains no `eval`, `Function(`, hardcoded callback, localhost port,
original-sign invocation, or automatic success callback.

- [ ] **Step 2: Run red and implement origin-bound interception**

Run the three test classes and expect missing types. Use WebKit feature checks
and register script/listener only for six exact origins. At document start,
intercept assignment to global `MiniApplet` and wrap only observed methods.
`cargarMiniApplet` reports readiness metadata without external/WebSocket launch.
`sign`/`selectCertificate` create UUID, keep callbacks only in JS closure, post
typed JSON, and stop. The probe shows `Solicitud observada` and only `Cancelar`.

- [ ] **Step 3: Run green and bridge guards**

```bash
./gradlew testDebugUnitTest --tests '*AfirmaJavascriptShimTest' --tests '*WebMessageProtocolTest' --tests '*ProtocolObservationRecorderTest'
rg -n 'addJavascriptInterface|evaluateJavascript\(|saveSignatureAuthCallback|original.*sign' app/src/main app/src/debug && exit 1 || true
```

Expected: tests pass and unsafe delivery/callback hardcoding is absent.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/raw/afirma_shim.js app/src/main/java/dev/junta/firmamobile/browser app/src/debug/java/dev/junta/firmamobile/browser app/src/test/java/dev/junta/firmamobile/browser
git commit -m "feat: observe MiniApplet calls at document start"
```

---

### Task 6: Install, inspect, and document the real Android 16 flow

**Files:**
- Modify: `docs/protocol-observations.md`
- Create: `app/src/test/resources/protocol/public-login-observation.json` with synthetic replacement values only
- Create transient QA artifacts under `/storage/emulated/0/Codex/Work/junta-firma-mobile/`; do not commit personal/session data

**Interfaces:**
- Produces authoritative runtime evidence required before parser, certificate, and signing work continues.

- [ ] **Step 1: Run the full Phase 1 suite freshly**

```bash
./gradlew clean lintDebug testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

Expected: every task passes; debug/release APKs exist.

- [ ] **Step 2: Install debug APK through the live device path**

Check `adb devices`; if empty, verify `rish -c id`, copy APK to the Codex work
directory, then:

```bash
rish -c "pm install -r /sdcard/Codex/Work/junta-firma-mobile/app-debug.apk"
rish -c "am start -W -n dev.junta.firmamobile/.browser.ProtocolProbeActivity"
```

Expected: install success and Activity launch without ClassNotFoundException.

- [ ] **Step 3: Inspect UI/WebView and trigger the request safely**

Use UIAutomator for non-secret navigation, dump UI tree, and take a screenshot
only before personal/session data appears. Confirm page load, Back/refresh,
external-link policy and no Play/AutoFirma launch. Invoke the certificate-login
button, stop at `Solicitud observada`, and cancel. Do not enter/store a P12
password and do not run the page's original MiniApplet method.

- [ ] **Step 4: Record the exact safe contract**

Update observations with operation sequence, field names, lengths/hash prefixes,
algorithm, format, property keys, server/callback hosts and paths, attempted
scheme/transport, callback arity and delivery shape. Record whether the static
SHA1/CAdES/EXPLICIT/TriPhaseSignatureService inference is confirmed or
contradicted.

- [ ] **Step 5: Audit evidence, retest, and commit**

Search observation export, filtered logcat, docs and UI artifacts for canaries
and prohibited values. If found, delete the transient artifact, add a regression
test, fix under systematic debugging and repeat. Then:

```bash
git diff --check
./gradlew testDebugUnitTest
git add docs/protocol-observations.md app/src/test/resources/protocol
git commit -m "docs: capture Junta MiniApplet runtime contract"
```

Expected: commit has no APK, screenshot, UI XML, logcat, cookie, raw query, full
hash/value, certificate, password, or signature.

---

## Phase 1 completion gate

Phase 1 is complete only when all Tasks 1–5 tests/builds pass, debug and release
research-shell APKs build, debug installs/launches on API 36, the real page
renders under secure policy, no Play/AutoFirma opens, an actual MiniApplet call
is intercepted before original execution, safe runtime metadata is committed,
and UI tree/screenshot/logcat are inspected without leakage.

Then resume the master plan at its parser/request task and use the runtime
fixture instead of guessed fields. Phase 1 success is not application completion
and must never be reported as a working electronic signature flow.
