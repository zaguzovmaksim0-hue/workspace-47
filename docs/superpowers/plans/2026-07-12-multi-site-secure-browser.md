# Secure Multi-Site Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the real Junta signing contour first, then evolve the app into a safe HTTPS mini-browser in which only explicit, versioned Site Profiles can reach certificate, cookies, protocol adapters, or native signing.

**Architecture:** Preserve `JuntaOriginPolicy` and the current certificate boundary as compatibility facades while adding pure URL/profile/trust components alongside them. A top-level trust state and navigation generation gate every bridge, cookie, and signing action; unknown HTTPS remains browse-only. Junta is the only signing profile in this plan and can become `FULLY_VERIFIED` only after the real portal accepts an end-to-end result.

**Tech Stack:** Kotlin/JVM 17, Android 16/API 36, AndroidX WebKit 1.16.0, Compose Material 3, DataStore Preferences, coroutines, JCA, Bouncy Castle 1.84 CMS APIs, JUnit 4, Robolectric 4.16.1, AndroidX instrumentation, Gradle Wrapper in native Termux/aarch64.

## Global Constraints

- The authoritative build/runtime target is Termux on the POCO F6 Pro; Windows, desktop Linux, cross-host portability, and autonomous portable AAPT2 are non-blocking.
- Package/namespace remains `dev.junta.firmamobile`; `certificate_reference` DataStore name and existing keys never change.
- Existing Junta start URL, six exact HTTPS origins, certificate/SAF tests, hardening, and release non-debuggable behavior are preserved.
- Unknown HTTPS may browse but never receives signing bridge, certificate access, native cookie export, or an external intent fallback.
- `http`, `file`, `content`, top-level `data`, `javascript`, top-level `blob`, custom schemes, userinfo, malformed and oversized URLs fail closed.
- Never use `addJavascriptInterface`, SSL `proceed`, trust-all TLS, permissive hostname verification, mixed content, universal file access, localhost signing servers, or automatic Google Play/AutoFirma launch.
- Every sign request requires a native `Firmar` action and a second validation of origin, profile/version, navigation ID, request expiry/fingerprint, and unlocked identity.
- Password, real PKCS#12, PrivateKey, `PrivateKey.encoded`, cookies, raw `afirma:` URI, payload, signature, callback token, and full certificate never enter Git, logs, saved state, DataStore, reports, or shell commands.
- Automated tests use only synthetic PKCS#12 fixtures. The user-provided real certificate is touched only during Task 6 final local E2E, selected via SAF, with password entered manually in the secure UI.
- Do not launch MainActivity after routine builds. Install/update without launch; launch only for a named UI/instrumentation/runtime/E2E gate.
- Junta remains `EXPERIMENTAL` until Task 6 passes every real E2E criterion; unit/instrumentation success alone cannot promote it.
- Each task follows RED → observed expected failure → minimal GREEN → full unit suite → lint → static security scan → diff review → one local commit. No push is authorized.

### Required close sequence for every task

Run the task's focused command first, then all of:

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintDebug --console=plain
rg -n 'addJavascriptInterface|handler\.proceed\(|HostnameVerifier\s*\{|X509TrustManager|allowUniversalAccessFromFileURLs\s*=\s*true|mixedContentMode\s*=\s*WebSettings\.MIXED_CONTENT_ALWAYS_ALLOW' app/src/main && exit 1 || true
rg -n 'PrivateKey\.encoded|privateKey\.encoded|evaluateJavascript\(' app/src/main app/src/test && exit 1 || true
git diff --check
git status --short
```

Expected: all Gradle tasks succeed; prohibited-pattern searches print nothing; diff check succeeds; status contains only the current task. Inspect `git diff --stat` and the complete task diff before staging. After staging exact paths, run `git diff --cached --check` and commit with the task's specified subject.

---

### Task 1: Observe the real Junta MiniApplet branch without signing

> **Required UX prerequisite:** Before the next long-running Task 1 runtime
> gate, execute
> `docs/superpowers/plans/2026-07-12-browser-probe-ux-correction.md` completely.
> This prerequisite changes browser/probe layout only and must not be used to
> infer a signing transport branch.

**Files:**

- Modify: `app/src/main/res/raw/afirma_shim.js`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt`
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt`
- Create: `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolObservationRecorder.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/browser/ProtocolObservationRecorderTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/browser/ProtocolProbeInstrumentedTest.kt`
- Modify after runtime evidence: `docs/protocol-observations.md`

**Interfaces:**

- Consumes: current `TrustedJuntaWebView`, `WebMessageBridge`, `AfirmaUriParser`, and `SanitizedLogger` from commit `95e068b`.
- Produces:

```kotlin
enum class ObservedMiniAppletCall { LOAD, SIGN }
enum class ObservedRuntimeBranch { AFIRMA, INTENT, WEBSOCKET, DIRECT_NETWORK, NONE }
enum class ObservationCorrelation { REQUEST_ID, ACTIVE_CALL_WINDOW, NONE }

data class SafeProtocolObservation(
    val call: ObservedMiniAppletCall,
    val originHost: String,
    val algorithm: String?,
    val format: String?,
    val argumentLengths: List<Int>,
    val branch: ObservedRuntimeBranch,
    val correlation: ObservationCorrelation,
)

interface ProtocolObservationSink {
    fun record(observation: SafeProtocolObservation)
}
```

- `ProtocolProbeActivity` exists only in the debug manifest, has no signing callback, never reads a certificate, and stops at the observation boundary.

- [ ] **Step 1: Write failing shim and recorder tests**

Add assertions that the raw script wraps assignment of `window.MiniApplet`, observes `cargarMiniApplet` and `sign`, preserves function references, emits only typed metadata, and never embeds callback names or sends raw `dat` as a diagnostic field:

```kotlin
@Test
fun shimObservesMiniAppletCallsWithoutCallingAResultCallback() {
    val script = AfirmaJavascriptShim.load(context)
    assertTrue(script.contains("MiniApplet"))
    assertTrue(script.contains("cargarMiniApplet"))
    assertTrue(script.contains("sign"))
    assertTrue(script.contains("MINIAPPLET_OBSERVATION"))
    assertFalse(script.contains("saveSignatureAuthCallback"))
    assertFalse(script.contains("showLogCallback"))
}

@Test
fun recorderExportsOnlyClosedSafeFields() {
    val recorder = ProtocolObservationRecorder(SanitizedLogger(fixedClock))
    recorder.record(safeObservation)
    val output = recorder.exportText()
    assertTrue(output.contains("branch=AFIRMA"))
    assertFalse(output.contains(RAW_DAT_CANARY))
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*AfirmaJavascriptShimTest' --tests '*ProtocolObservationRecorderTest' --console=plain
```

Expected: FAIL because the current shim only handles `window.open` and `ProtocolObservationRecorder` does not exist.

- [ ] **Step 3: Implement the bounded observation shim and debug probe**

At document start, install a configurable property wrapper around `MiniApplet`. Wrap only callable `cargarMiniApplet` and `sign`; preserve `this`, arguments, return value, and callback references. The observation message contains `type`, UUID, operation, non-secret algorithm/format, and argument lengths. Let the current WebView client consume any resulting `afirma:`/`intent:` navigation; never invoke Package Manager, pre-sign, success callback, or error callback from the probe.

Implement recorder parsing with a closed enum, maximum 32 arguments, maximum integer length 1,048,576, host sanitization, and no generic map/string payload API.

Never attach a native branch to merely the latest or next call. A JavaScript
branch must reuse the exact request UUID. Native navigation may use only an
explicitly labelled `ACTIVE_CALL_WINDOW` when there is one already-open
top-level SIGN, one bound document UUID/generation and trusted origin,
algorithm and format are present, and elapsed time is at most 250 ms. If
Android WebView delivers navigation before WebMessage, record a safe false
negative and poison native correlation for that document. Overflow, expiry,
replay, unknown transitions and duplicate critical JSON keys fail closed and
never evict state to continue. Hold a candidate branch until its matching
`MINIAPPLET_CALL_END`; emit one closed `PROTOCOL_CORRELATION_REJECTED` marker
if any later transition invalidates that document.

- [ ] **Step 4: Run focused GREEN and Android-test compilation**

```bash
./gradlew testDebugUnitTest --tests '*AfirmaJavascriptShimTest' --tests '*ProtocolObservationRecorderTest' compileDebugAndroidTestKotlin --console=plain
```

Expected: all focused tests pass; the debug-only probe compiles; release sources do not contain `ProtocolProbeActivity`.

- [ ] **Step 5: Run the required close sequence**

Run the global close sequence. Expected: 91 existing tests plus new tests pass, lint succeeds, security searches are empty.

- [ ] **Step 6: Build, install, and execute the one allowed runtime gate**

```bash
./gradlew assembleDebug assembleDebugAndroidTest --console=plain
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -p 4 app/build/outputs/apk/debug/app-debug.apk
```

Stage through `/storage/emulated/0/Codex/Work/junta-firma-mobile/`, install with `pm install -r -t`, and verify `pm path`/`dumpsys package`. Launch only `.browser.ProtocolProbeActivity`, observe the public portal branch, and export only `SafeProtocolObservation`. Expected: no Play/AutoFirma activity opens, no certificate is read, and one closed `ObservedRuntimeBranch` is recorded. If legal/login interaction is required before `sign`, record `NONE` and keep this task open rather than inferring a branch.

- [ ] **Step 7: Update evidence and commit**

Record only branch, method/content-type names, hosts, lengths and short hashes in `docs/protocol-observations.md`. Run:

```bash
git add app/src/main/res/raw/afirma_shim.js app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt app/src/debug app/src/test/java/dev/junta/firmamobile/browser app/src/androidTest/java/dev/junta/firmamobile/browser/ProtocolProbeInstrumentedTest.kt docs/protocol-observations.md
git diff --cached --check
git commit -m "feat: observe Junta MiniApplet runtime safely"
```

Review gate: release APK has no probe Activity; observation contains no raw URL/query, `dat`, cookie, certificate, callback token or secret.

---

### Task 2: Add single-use signing request and protocol adapter boundaries

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/signing/SigningModels.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/SigningProtocolAdapter.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/PendingSignRequestStore.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/NavigationId.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/signing/PendingSignRequestStoreTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/signing/SigningProtocolAdapterContractTest.kt`

**Interfaces:**

- Consumes: `AfirmaRequest`, `TrustedOrigin`, `CertificateSummary`; no WebView or `PrivateKey` type.
- Produces:

```kotlin
@JvmInline value class SigningProtocolId(val value: String)

enum class SigningAlgorithm { SHA1_WITH_RSA, SHA256_WITH_RSA }
enum class SigningFormat { CADES }
enum class SigningErrorCode {
    INVALID_REQUEST,
    REQUEST_TOO_LARGE,
    REQUEST_EXPIRED,
    PROFILE_NOT_ACTIVE,
    ORIGIN_NOT_ALLOWED,
    NAVIGATION_CHANGED,
    PAYLOAD_CHANGED,
    CERTIFICATE_LOCKED,
    UNSUPPORTED_PROTOCOL,
    UNOBSERVED_CONTRACT,
    SESSION_EXPIRED,
    LOCAL_SIGNATURE_FAILED,
    PROTOCOL_FAILED,
    USER_CANCELLED,
}
data class SigningContext(
    val profileId: String,
    val profileVersion: Int,
    val origin: TrustedOrigin,
    val navigationId: NavigationId,
    val observedAt: Instant,
)

data class NormalizedSignRequest(
    val requestId: UUID,
    val protocolId: SigningProtocolId,
    val context: SigningContext,
    val algorithm: SigningAlgorithm,
    val format: SigningFormat,
    val safeDescription: String,
    internal val payload: ByteArray,
)

interface SigningProtocolAdapter {
    val id: SigningProtocolId
    fun recognize(input: InterceptedSigningInput, profileId: String): Boolean
    fun normalize(input: InterceptedSigningInput, context: SigningContext): AdapterParseResult
    suspend fun prepare(request: NormalizedSignRequest): PreSignResult
    suspend fun complete(request: NormalizedSignRequest, localSignature: LocalSignature): ProtocolCompletionResult
}

enum class ConsumeError {
    NOT_FOUND,
    REQUEST_EXPIRED,
    PROFILE_CHANGED,
    ORIGIN_CHANGED,
    NAVIGATION_CHANGED,
    PAYLOAD_CHANGED,
    ALREADY_CONSUMED,
}

data class PendingValidationContext(
    val requestId: UUID,
    val profileId: String,
    val profileVersion: Int,
    val origin: TrustedOrigin,
    val navigationId: NavigationId,
    internal val payload: ByteArray,
)

sealed interface PendingConsumeResult {
    data class Accepted(val request: NormalizedSignRequest) : PendingConsumeResult
    data class Rejected(val error: ConsumeError) : PendingConsumeResult
}

data class PendingSignSummary(
    val requestId: UUID,
    val context: SigningContext,
    val algorithm: SigningAlgorithm,
    val format: SigningFormat,
    val safeDescription: String,
    val expiresAt: Instant,
)

internal fun interface SensitiveSigningCopyObserver {
    fun onCleared(allZero: Boolean)
}

class PendingSignRequestStore(
    private val clock: Clock = Clock.systemUTC(),
    private val lifetime: Duration = Duration.ofMinutes(2),
    private val observer: SensitiveSigningCopyObserver = SensitiveSigningCopyObserver {},
) {
    fun put(request: NormalizedSignRequest): PendingSignSummary
    fun peek(): PendingSignSummary?
    fun consume(expected: PendingValidationContext): PendingConsumeResult
    fun clear(reason: ConsumeError)
}
```

`NavigationId` is the sole inline type in `browser/NavigationId.kt`:

```kotlin
@JvmInline value class NavigationId(val value: String)
```

`SigningModels.kt` also defines the closed types used above:

```kotlin
sealed interface InterceptedSigningInput {
    data class AfirmaUri internal constructor(internal val rawUri: String) : InterceptedSigningInput
    data class WebMessage internal constructor(internal val message: WebBridgeMessage) : InterceptedSigningInput
}

sealed interface AdapterParseResult {
    data class Accepted(val request: NormalizedSignRequest) : AdapterParseResult
    data class Rejected(val code: SigningErrorCode) : AdapterParseResult
}

data class PreSignResult internal constructor(internal val bytesToSign: ByteArray)
data class LocalSignature internal constructor(internal val bytes: ByteArray)
sealed interface SignDelivery {
    data class BridgeResult internal constructor(
        val requestId: UUID,
        internal val resultJson: String,
    ) : SignDelivery
}
sealed interface ProtocolCompletionResult {
    data class Success(val delivery: SignDelivery) : ProtocolCompletionResult
    data class Failure(val code: SigningErrorCode) : ProtocolCompletionResult
}
```

- `PendingSignRequestStore` stores at most one request, owns a SHA-256 fingerprint and expiry, binds it to context, and zeroes payload/fingerprint on every terminal transition.

- [ ] **Step 1: Write RED state-machine tests**

```kotlin
@Test
fun requestIsSingleUseAndBoundToOriginProfileNavigationAndPayload() {
    val store = PendingSignRequestStore(fixedClock, Duration.ofMinutes(2), observer)
    val pending = store.put(request)
    assertEquals(pending, store.peek())
    val result = store.consume(changedNavigationContext)
    assertEquals(ConsumeError.NAVIGATION_CHANGED, (result as PendingConsumeResult.Rejected).error)
    assertTrue(observer.allSensitiveCopiesCleared)
}

@Test
fun adapterContractContainsNoCertificateSessionOrPrivateKeySurface() {
    val methods = SigningProtocolAdapter::class.java.methods.map { it.toGenericString() }
    assertTrue(methods.none { "PrivateKey" in it || "CertificateSession" in it })
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*PendingSignRequestStoreTest' --tests '*SigningProtocolAdapterContractTest' --console=plain
```

Expected: FAIL with unresolved signing types.

- [ ] **Step 3: Implement minimal closed models and store**

Use enum algorithms `SHA1_WITH_RSA`, `SHA256_WITH_RSA` and format `CADES`. Do not implement adapters yet. Copy payload once, hash with SHA-256, compare using `MessageDigest.isEqual`, cap payload at 524,288 bytes, expire after two minutes, and call a test-only internal clearing observer after zeroing arrays.

- [ ] **Step 4: Run focused GREEN**

```bash
./gradlew testDebugUnitTest --tests '*PendingSignRequestStoreTest' --tests '*SigningProtocolAdapterContractTest' --console=plain
```

Expected: all tests pass, including duplicate request ID, expiry, cancel, profile/origin/navigation mismatch, changed payload, and zeroization cases.

- [ ] **Step 5: Run the required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/signing app/src/main/java/dev/junta/firmamobile/browser/NavigationId.kt app/src/test/java/dev/junta/firmamobile/signing
git diff --cached --check
git commit -m "feat: add single-use signing request boundary"
```

Review gate: no signing interface exposes `PrivateKey`, password, P12, raw callback or cookies; all mutable payload copies have an owner and terminal cleanup.

---

### Task 3: Implement bounded RSA and CAdES cryptography

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/LocalSignatureEngine.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/CadesSigner.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/signing/LocalSignatureEngineTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/signing/CadesSignerTest.kt`
- Reuse: `app/src/test/java/dev/junta/firmamobile/certificate/TestCertificateFactory.kt`

**Interfaces:**

- Consumes: `UnlockedIdentity.withPrivateKey`, `SigningAlgorithm`, `SigningFormat`; synthetic test identity only.
- Produces:

```kotlin
sealed interface LocalSignatureResult {
    data class Success(val signature: LocalSignature) : LocalSignatureResult
    data class Failure(val code: LocalSignatureError) : LocalSignatureResult
}

interface LocalSignatureEngine {
    fun sign(input: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm): LocalSignatureResult
}

interface CadesSigner {
    fun signDetached(
        content: ByteArray,
        identity: UnlockedIdentity,
        algorithm: SigningAlgorithm,
    ): LocalSignatureResult
}
```

- [ ] **Step 1: Add RED crypto tests**

Test SHA-256 verification, explicit SHA-1 policy, changed-data failure, unsupported algorithm, input bounds, CAdES detached parsing/signer verification/messageDigest attribute, and a key wrapper whose `getEncoded()` throws:

```kotlin
@Test
fun engineSignsWithoutReadingPrivateKeyEncoding() {
    val identity = syntheticIdentity(privateKey = NonExportablePrivateKey(delegateKey))
    val result = engine.sign("challenge".encodeToByteArray(), identity, SHA256_WITH_RSA)
    assertTrue(verify(result.signature.bytes, identity.certificate.publicKey))
    assertEquals(0, identity.privateKeyEncodedReads)
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*LocalSignatureEngineTest' --tests '*CadesSignerTest' --console=plain
```

Expected: FAIL because engines do not exist and Bouncy Castle is test-only.

- [ ] **Step 3: Add the demonstrated runtime dependency and implementation**

Promote existing pinned `bcprov-jdk18on:1.84` and `bcpkix-jdk18on:1.84` aliases to `implementation` only because CMS/CAdES generation requires them. Do not register a global provider. Use JCA `Signature` for raw RSA and Bouncy Castle CMS builders directly for detached CAdES. Cap local input at 524,288 bytes and output at 2,097,152 bytes. Copy/zero temporary byte arrays in `finally`; never call key `encoded`.

- [ ] **Step 4: Run focused GREEN and dependency verification**

```bash
./gradlew testDebugUnitTest --tests '*LocalSignatureEngineTest' --tests '*CadesSignerTest' verifyResolvedCoreVersion --console=plain
```

Expected: crypto tests pass; CMS verifies using the synthetic public certificate; resolved versions remain pinned.

- [ ] **Step 5: Run required close sequence, APK size check, and commit**

```bash
./gradlew assembleDebug assembleRelease --console=plain
du -h app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release.apk
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/dev/junta/firmamobile/signing app/src/test/java/dev/junta/firmamobile/signing
git diff --cached --check
git commit -m "feat: add bounded RSA and CAdES signing"
```

Review gate: dependency addition is limited to the already pinned CMS libraries; release does not expose debug/provider configuration; no key encoding or sensitive logging.

---

### Task 4: Implement only the observed Junta tri-phase contract

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/network/SafeNetworkUrlPolicy.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpTransport.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/JuntaTriPhaseCodec.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/JuntaTriPhaseAdapter.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/WebViewCookieBridge.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/network/SafeNetworkUrlPolicyTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/network/ProfileHttpTransportTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/signing/JuntaTriPhaseCodecTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/signing/JuntaTriPhaseAdapterTest.kt`
- Create from Task 1 evidence: `app/src/test/resources/junta-triphase/synthetic-contract.properties`

**Interfaces:**

- Consumes: the closed branch/method/content-type/field-name evidence committed by Task 1, current six exact Junta origins, `SigningProtocolAdapter`, `WebViewCookieBridge`, and `LocalSignatureEngine`.
- Produces `JuntaTriPhaseAdapter` with ID `junta-afirma-triphase-cades-v1`. Its codec accepts only the exact field names, encodings, status/content types and redirect behavior recorded in Task 1. The fixture is synthetic and contains no real `dat`, token, cookie, certificate or signature.

- [ ] **Step 1: Write RED URL, transport, codec, and adapter tests**

```kotlin
@Test
fun callbackAndEveryRedirectMustRemainAnExactJuntaNetworkOrigin() {
    assertEquals(Allowed(ws024Url), policy.validate(ws024Url))
    assertEquals(Blocked(IP_LITERAL), policy.validate("https://127.0.0.1/sign"))
    assertEquals(Blocked(ORIGIN_NOT_ALLOWED), policy.validate("https://evil.example/sign"))
}

@Test
fun htmlLoginAndOversizedOrDoctypeResponsesNeverBecomePreSign() {
    assertEquals(SESSION_EXPIRED, codec.parsePreSign(html200).error)
    assertEquals(RESPONSE_TOO_LARGE, codec.parsePreSign(oversized).error)
    assertEquals(RESPONSE_FORMAT_INVALID, codec.parsePreSign(doctype).error)
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*SafeNetworkUrlPolicyTest' --tests '*ProfileHttpTransportTest' --tests '*JuntaTriPhaseCodecTest' --tests '*JuntaTriPhaseAdapterTest' --console=plain
```

Expected: FAIL with missing policy/transport/codec/adapter.

- [ ] **Step 3: Implement exact contract, secure parsing, and cookie scope**

Use `HttpsURLConnection` behind `ProfileHttpTransport`; disable automatic redirects and revalidate every `Location`. Allow HTTPS, exact profile hosts, documented port 443, no userinfo/IP/private DNS result, maximum 5 redirects, 15-second connect/read timeouts, 2 MiB response, and expected content type. Resolve DNS behind an injected `DnsResolver`; reject any non-public address before connecting and after redirects.

Configure XML parsers with secure processing, DOCTYPE/DTD/external entities disabled. Treat 401/403, login redirect, and HTML where protocol data is expected as `SESSION_EXPIRED`. Read/apply cookies only for the exact request URL through `WebViewCookieBridge`; transport errors expose codes, never bodies/headers.

If Task 1 recorded no executable tri-phase contract, implement `JuntaTriPhaseAdapter.normalize` to return `UNOBSERVED_CONTRACT` and do not add network serialization. The task remains GREEN and safely closed, but Task 6 cannot start until a later Task 1 evidence commit adds the exact synthetic fixture and enables the codec; no guessed wire request is permitted.

- [ ] **Step 4: Run focused GREEN**

```bash
./gradlew testDebugUnitTest --tests '*SafeNetworkUrlPolicyTest' --tests '*ProfileHttpTransportTest' --tests '*JuntaTriPhaseCodecTest' --tests '*JuntaTriPhaseAdapterTest' --console=plain
```

Expected: exact observed fixture round-trips; malformed/XXE/HTML/redirect/cookie/DNS/timeout cases fail closed. If contract is unobserved, only the explicit refusal path passes and no transport call occurs.

- [ ] **Step 5: Run the required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/network app/src/main/java/dev/junta/firmamobile/signing app/src/main/java/dev/junta/firmamobile/browser/WebViewCookieBridge.kt app/src/test/java/dev/junta/firmamobile/network app/src/test/java/dev/junta/firmamobile/signing app/src/test/resources/junta-triphase
git diff --cached --check
git commit -m "feat: implement observed Junta tri-phase contract"
```

Review gate: all allowed wire fields exist in Task 1 evidence; no redirect/cookie/body bypass; `UNOBSERVED_CONTRACT` is the only behavior when evidence is incomplete.

---

### Task 5: Wire explicit Junta confirmation and signing coordinator

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/SigningUiState.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/SigningConfirmationDialog.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/dev/junta/firmamobile/signing/SigningCoordinatorTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/ui/SigningConfirmationDialogTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/SigningConfirmationInstrumentedTest.kt`

**Interfaces:**

- Consumes: `PendingSignRequestStore`, `JuntaTriPhaseAdapter`, `LocalSignatureEngine`, `CertificateSession`, current top-level `TrustedOrigin` provider.
- Produces:

```kotlin
sealed interface SigningUiState {
    data object Idle : SigningUiState
    data class AwaitingConfirmation(val summary: SignConfirmationSummary) : SigningUiState
    data class Signing(val requestId: UUID) : SigningUiState
    data class Completed(val requestId: UUID) : SigningUiState
    data class Failed(val requestId: UUID, val code: SigningErrorCode, val retryable: Boolean) : SigningUiState
}

interface SigningCoordinator {
    fun prepare(request: NormalizedSignRequest): PrepareResult
    suspend fun confirm(expected: ConfirmationSnapshot): SigningResult
    fun cancel(reason: CancelReason)
}

data class ConfirmationSnapshot(
    val requestId: UUID,
    val profileId: String,
    val profileVersion: Int,
    val origin: TrustedOrigin,
    val navigationId: NavigationId,
    val expiresAt: Instant,
    internal val payloadFingerprint: ByteArray,
    val certificateSummary: CertificateSummary,
)

enum class CancelReason { USER, NAVIGATION_CHANGED, RELOAD, CERTIFICATE_LOCKED, BACKGROUND }
sealed interface PrepareResult {
    data class Ready(val snapshot: ConfirmationSnapshot) : PrepareResult
    data class Rejected(val code: SigningErrorCode) : PrepareResult
}
sealed interface SigningResult {
    data class Success(val delivery: SignDelivery) : SigningResult
    data class Failure(val code: SigningErrorCode) : SigningResult
}
```

- [ ] **Step 1: Write RED coordinator and UI tests**

```kotlin
@Test
fun signerIsNeverCalledBeforeFirmarAndOriginChangeCancels() = runTest {
    coordinator.prepare(request)
    assertEquals(0, fakeEngine.calls)
    currentOrigin.value = evilOrigin
    assertEquals(NAVIGATION_CHANGED, coordinator.confirm(snapshot).error)
    assertEquals(0, fakeEngine.calls)
}

@Test
fun dialogShowsExactSafeFieldsAndLegacyWarning() {
    rule.setContent { SigningConfirmationDialog(sha1Summary, onConfirm, onCancel) }
    rule.onNodeWithText("www.juntadeandalucia.es", substring = true).assertIsDisplayed()
    rule.onNodeWithText("SHA-1", substring = true).assertIsDisplayed()
    rule.onNodeWithText("Firmar").performClick()
    rule.runOnIdle { check(confirmCalls == 1) }
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*SigningCoordinatorTest' --tests '*SigningConfirmationDialogTest' --console=plain
```

Expected: FAIL because coordinator/state/dialog do not exist.

- [ ] **Step 3: Implement single-use coordinator and replace observation-only dialog**

Show exact domain, `Junta de Andalucía`, `EXPERIMENTAL`, operation, CAdES, algorithm, owner and safe description. SHA-1 gets a separate legacy warning. `Firmar` is the only confirmation transition. Before accessing identity, compare full snapshot and current origin/navigation, profile version, expiry and payload fingerprint. Cancel on Back/Reload/navigation/lock/background. Return results to bridge by request ID as typed JSON; do not build JavaScript strings.

- [ ] **Step 4: Run focused GREEN and instrumentation compilation**

```bash
./gradlew testDebugUnitTest --tests '*SigningCoordinatorTest' --tests '*SigningConfirmationDialogTest' compileDebugAndroidTestKotlin --console=plain
```

Expected: confirmation, cancel, expiry, origin/profile/navigation/payload/certificate changes, duplicate confirm and no-auto-sign tests pass.

- [ ] **Step 5: Run the required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt app/src/main/java/dev/junta/firmamobile/ui app/src/main/java/dev/junta/firmamobile/MainActivity.kt app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt app/src/main/res/values/strings.xml app/src/test/java/dev/junta/firmamobile/signing/SigningCoordinatorTest.kt app/src/test/java/dev/junta/firmamobile/ui/SigningConfirmationDialogTest.kt app/src/androidTest/java/dev/junta/firmamobile/SigningConfirmationInstrumentedTest.kt
git diff --cached --check
git commit -m "feat: require confirmation for Junta signing"
```

Review gate: no code path signs from bridge callback; no raw payload appears in Compose semantics/saved state; cancellation clears all sensitive request state.

---
### Task 6: Pass and record the real Junta end-to-end gate

**Files:**

- Modify with sanitized evidence only: `docs/protocol-observations.md`
- Create: `docs/test-report.md`
- Modify: `docs/test-plan.md`
- No production source is changed unless a reproducible E2E defect is first covered in the responsible Task 1–5 test file.

**Interfaces:**

- Consumes: completed Tasks 1–5, POCO F6 Pro, legitimate portal access, and the external real PKCS#12 selected manually through SAF.
- Produces: an evidence record containing build hash, app version, profile/contract version, timestamps, closed event/error codes, and pass/fail for each E2E step. It contains no personal name, certificate subject, file path, password, payload, callback token, cookies, signature, or full URL query.

- [ ] **Step 1: Establish the failing acceptance gate**

Add a dated run section to `docs/test-report.md` with every item initially `Not run`:

```text
JUNTA_E2E_OPEN=Not run
JUNTA_E2E_CERTIFICATE_UNLOCK=Not run
JUNTA_E2E_REQUEST_INTERCEPT=Not run
JUNTA_E2E_CONFIRMATION=Not run
JUNTA_E2E_LOCAL_SIGNATURE=Not run
JUNTA_E2E_POST_SIGN=Not run
JUNTA_E2E_CALLBACK_ACCEPTED=Not run
JUNTA_E2E_PORTAL_CONTINUED=Not run
JUNTA_E2E_RELOCK=Not run
JUNTA_E2E_SECRET_AUDIT=Not run
```

Expected RED: the profile cannot be called `FULLY_VERIFIED` while any value is not `Passed`.

- [ ] **Step 2: Run the complete pre-E2E build/security gate**

```bash
./gradlew clean testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug assembleRelease --console=plain
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -p 4 app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -p 4 app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release.apk
```

Expected: clean Wrapper build succeeds in Termux; both APKs verify and align; release manifest has no `debuggable=true`/`testOnly=true`.

- [ ] **Step 3: Install without auto-launch and verify package manager**

Copy only the generated APK to `/storage/emulated/0/Codex/Work/junta-firma-mobile/`, then use local Android shell to copy it to `/data/local/tmp` and run `pm install -r -t`. Verify `pm path dev.junta.firmamobile` and `dumpsys package` for version/min/target/ABI. Expected: install `Success`; MainActivity has not been started.

- [ ] **Step 4: Execute the one real manual E2E**

Launch MainActivity because this is the explicit final signing gate. In the Android UI, manually select the external real certificate through SAF and manually enter its password; do not use clipboard, `adb input`, a test fixture, environment variable, file, DataStore, or shell argument for the credential.

Complete exactly:

1. validate safe owner/issuer/validity summary;
2. open Junta start URL and complete legitimate login interaction;
3. trigger signing and verify no AutoFirma/Google Play activity opens;
4. verify confirmation shows exact Junta host, experimental support, owner, CAdES and requested algorithm;
5. press `Firmar` once;
6. observe pre-sign/local-sign/post-sign typed states;
7. verify callback returns to the same active navigation/profile;
8. verify the portal accepts and continues;
9. lock certificate and verify a second sign requires password;
10. inspect sanitized app diagnostics and logcat for forbidden canaries without printing raw logs containing personal UI text.

Expected GREEN: every listed `JUNTA_E2E_*` field becomes `Passed`. A failure stays `Failed` with a closed code; it is reproduced in a synthetic test and fixed in Task 1–5 before retry. HTTP 200 alone is not acceptance.

- [ ] **Step 5: Clear sensitive runtime state**

Use the app's lock and confirmed session-clear actions, close/force-stop the app, clear its in-memory diagnostic journal, and delete only staged APK/test copies from `/data/local/tmp` and `/storage/emulated/0/Codex/Work/junta-firma-mobile/`. Do not delete or move the external certificate. Verify the session is locked on next launch. Private-key object references are dropped; all owned mutable password/payload buffers have zeroization assertions from automated tests.

- [ ] **Step 6: Run required close sequence and review evidence**

Run the global close sequence. Additionally:

```bash
rg -n 'BEGIN (PRIVATE KEY|CERTIFICATE)|Cookie:|Set-Cookie:|afirma://|password=' docs app/src && exit 1 || true
git diff --check
```

Expected: no forbidden value appears; evidence records facts and limitations only.

- [ ] **Step 7: Commit evidence**

```bash
git add docs/protocol-observations.md docs/test-report.md docs/test-plan.md
git diff --cached --check
git commit -m "test: verify Junta signing end to end"
```

Review gate: all ten fields are directly observed `Passed`; otherwise do not commit a success claim and do not begin Task 7.

---

### Task 7: Add browser HTTPS URL normalization and blocking policy

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/browser/BrowserUrlPolicy.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/network/TrustedOrigin.kt`
- Modify without API change: `app/src/main/java/dev/junta/firmamobile/network/JuntaOriginPolicy.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/browser/BrowserUrlPolicyTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/network/JuntaOriginPolicyTest.kt`

**Interfaces:**

- Consumes: existing `TrustedOrigin` API and exact Junta tests.
- Produces the following types. Moving `TrustedOrigin` to its own file keeps package/name/constructor unchanged.

```kotlin
data class NormalizedHttpsUrl(
    val serialized: String,
    val origin: TrustedOrigin,
    val path: String,
    val query: String?,
    val fragment: String?,
)

enum class BrowserUrlError {
    EMPTY, TOO_LONG, CONTROL_CHARACTER, UNSUPPORTED_SCHEME,
    USERINFO_NOT_ALLOWED, HOST_MISSING, HOST_NOT_CANONICAL,
    IP_LITERAL_NOT_ALLOWED, MALFORMED,
}

sealed interface BrowserUrlResult {
    data class Valid(val url: NormalizedHttpsUrl) : BrowserUrlResult
    data class Blocked(val error: BrowserUrlError) : BrowserUrlResult
}

class BrowserUrlPolicy {
    fun normalize(input: String): BrowserUrlResult
}
```

- [ ] **Step 1: Write comprehensive RED URL tests**

```kotlin
@Test
fun domainOnlyGetsHttpsAndNoSearchFallback() {
    val result = policy.normalize("sede.dgt.gob.es/path") as BrowserUrlResult.Valid
    assertEquals("https://sede.dgt.gob.es/path", result.url.serialized)
}

@Test
fun blocksUnsafeOrAmbiguousInputsBeforeNetwork() {
    val blocked = listOf(
        "http://example.es", "file:///etc/passwd", "content://provider/id",
        "data:text/html,x", "javascript:alert(1)", "blob:https://example.es/id",
        "https://user:pass@example.es", "https://example.es.\u0020",
        "https://127.0.0.1", "https://[::1]", "https://exa\u0000mple.es",
        "palabras para buscar", "https://administración.example",
    )
    blocked.forEach { assertTrue(policy.normalize(it) is BrowserUrlResult.Blocked) }
}
```

Cover empty, 8,193 chars, controls, only U+0020 trim, default/explicit port, uppercase, trailing dot, malformed percent, ASCII A-label browse-only, query/fragment transient fields, and no Google/search URL generation.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*BrowserUrlPolicyTest' --tests '*JuntaOriginPolicyTest' --console=plain
```

Expected: FAIL because generic URL policy/types do not exist.

- [ ] **Step 3: Implement pure deterministic normalization**

Reject controls before trimming. Add `https://` only when the input contains a valid ASCII hostname. Parse once, reject userinfo/IP/localhost/non-ASCII raw host, canonicalize using `IDN.toASCII(..., USE_STD3_ASCII_RULES)`, lowercase with `Locale.ROOT`, and rebuild a single serialized HTTPS URL. Never perform DNS/network/search in this class.

- [ ] **Step 4: Run focused GREEN and existing Junta regression**

```bash
./gradlew testDebugUnitTest --tests '*BrowserUrlPolicyTest' --tests '*JuntaOriginPolicyTest' --tests '*JuntaNavigationPolicyTest' --console=plain
```

Expected: all new cases and all existing Junta exact-origin/navigation cases pass unchanged.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/browser/BrowserUrlPolicy.kt app/src/main/java/dev/junta/firmamobile/network/TrustedOrigin.kt app/src/main/java/dev/junta/firmamobile/network/JuntaOriginPolicy.kt app/src/test/java/dev/junta/firmamobile/browser/BrowserUrlPolicyTest.kt app/src/test/java/dev/junta/firmamobile/network/JuntaOriginPolicyTest.kt
git diff --cached --check
git commit -m "feat: validate safe HTTPS browser addresses"
```

Review gate: parser has no side effects, no automatic search and no trusted-domain inference.

---

### Task 8: Add generic Site Profile model, registry, and Junta compatibility facade

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/site/SiteProfileModels.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/site/OriginPolicy.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/site/SiteProfileRegistry.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/site/JuntaSiteProfile.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/network/JuntaOriginPolicy.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/site/SiteProfileRegistryTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/site/JuntaSiteProfileTest.kt`
- Keep green: `app/src/test/java/dev/junta/firmamobile/network/JuntaOriginPolicyTest.kt`

**Interfaces:**

- Produces the following types; imports the sole `SigningProtocolId` from `signing/SigningModels.kt`.

```kotlin
@JvmInline value class SiteProfileId(val value: String)
enum class SiteSupportLevel { FULLY_VERIFIED, EXPERIMENTAL, BROWSE_ONLY }
enum class SigningRequestScheme { AFIRMA, INTENT_AFIRMA, WEB_MESSAGE }
enum class OriginPurpose { TOP_LEVEL, LOGIN, BRIDGE, CALLBACK, NATIVE_NETWORK }

data class ProfileCookiePolicy(
    val nativeReadOrigins: Set<TrustedOrigin>,
    val nativeWriteOrigins: Set<TrustedOrigin>,
)

data class ConfirmationPolicy(
    val requireExplicitAction: Boolean,
    val showExperimentalWarning: Boolean,
)

data class SiteProfile(
    val id: SiteProfileId,
    val displayName: String,
    val version: Int,
    val homeUrl: NormalizedHttpsUrl,
    val topLevelOrigins: Set<TrustedOrigin>,
    val loginOrigins: Set<TrustedOrigin>,
    val bridgeOrigins: Set<TrustedOrigin>,
    val callbackOrigins: Set<TrustedOrigin>,
    val networkOrigins: Set<TrustedOrigin>,
    val requestSchemes: Set<SigningRequestScheme>,
    val signingProtocols: Set<SigningProtocolId>,
    val cookiePolicy: ProfileCookiePolicy,
    val confirmationPolicy: ConfirmationPolicy,
    val supportLevel: SiteSupportLevel,
)

interface OriginPolicy {
    fun allows(profile: SiteProfile, origin: TrustedOrigin, purpose: OriginPurpose): Boolean
}

interface SiteProfileRegistry {
    val profiles: List<SiteProfile>
    fun profileForTopLevel(origin: TrustedOrigin): SiteProfile?
    fun profile(id: SiteProfileId): SiteProfile?
    fun bridgeOriginRules(): Set<String>
}
```
- `DefaultSiteProfileRegistry` contains only `JuntaSiteProfile` as a signing profile. Official home shortcuts are not automatically profiles.
- `JuntaOriginPolicy` delegates while retaining public constants/properties/functions.

- [ ] **Step 1: Write RED registry invariants and compatibility tests**

```kotlin
@Test
fun registryRejectsWildcardsDuplicateOriginsAndInvalidBrowseOnlyCapabilities() {
    assertFailsWith<IllegalArgumentException> { registry(profile(host = "*.gob.es")) }
    assertFailsWith<IllegalArgumentException> { registry(profileA, profileWithSameTopOrigin) }
    assertFailsWith<IllegalArgumentException> {
        registry(browseOnly.copy(signingProtocols = setOf(protocolId)))
    }
}

@Test
fun JuntaFacadeStillExposesTheExactSixOriginsAndStartUrl() {
    assertEquals(EXISTING_START_URL, JuntaOriginPolicy.START_URL)
    assertEquals(EXISTING_SIX_HOSTS, JuntaOriginPolicy.allowedHosts)
    assertEquals(EXISTING_SIX_RULES, JuntaOriginPolicy.webMessageOriginRules)
}
```

Also test stable ASCII ID, version >= 1, home containment, cookie subsets, explicit confirmation, experimental warning, exact purpose matching and no suffix/punycode lookalike.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*SiteProfileRegistryTest' --tests '*JuntaSiteProfileTest' --tests '*JuntaOriginPolicyTest' --console=plain
```

Expected: FAIL with missing `site` package.

- [ ] **Step 3: Implement immutable registry and Junta profile**

Use an unmodifiable list/map built at application startup. Initial Junta profile version is `1`, ID `junta-andalucia-empleo-autcert`, support `FULLY_VERIFIED` only because Task 6 is a prerequisite and its evidence commit is present; otherwise construction/test expects `EXPERIMENTAL`. Keep all six existing origins in each compatibility purpose set for version 1. No runtime/user mutation API exists.

- [ ] **Step 4: Run focused GREEN and facade regression**

```bash
./gradlew testDebugUnitTest --tests '*SiteProfileRegistryTest' --tests '*JuntaSiteProfileTest' --tests '*JuntaOriginPolicyTest' --tests '*JuntaNavigationPolicyTest' --tests '*AfirmaUriParserTest' --console=plain
```

Expected: registry tests pass and every old Junta assertion remains identical.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/site app/src/main/java/dev/junta/firmamobile/network/JuntaOriginPolicy.kt app/src/test/java/dev/junta/firmamobile/site app/src/test/java/dev/junta/firmamobile/network/JuntaOriginPolicyTest.kt
git diff --cached --check
git commit -m "feat: register Junta as the first site profile"
```

Review gate: no wildcard/dynamic trust; `FULLY_VERIFIED` is tied to committed Task 6 evidence; no other government shortcut receives signing capabilities.

---

### Task 9: Add trusted/browse-only state machine and generic navigation policy

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/browser/BrowserTrustState.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/BrowserTrustController.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/BrowserNavigationPolicy.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaNavigationPolicy.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/browser/BrowserTrustControllerTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/browser/BrowserNavigationPolicyTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`

**Interfaces:**

- Consumes: `BrowserUrlPolicy`, `SiteProfileRegistry`, pending-request cancellation callback.
- Produces:

```kotlin
sealed interface BrowserTrustState {
    data object Home : BrowserTrustState
    data class BrowseOnly(val url: NormalizedHttpsUrl) : BrowserTrustState
    data class Trusted(
        val profile: SiteProfile,
        val origin: TrustedOrigin,
        val navigationId: NavigationId,
    ) : BrowserTrustState
    data class Blocked(val reason: BrowserUrlError) : BrowserTrustState
}

data class TopLevelNavigation(
    val url: NormalizedHttpsUrl,
    val navigationId: NavigationId,
    val transition: TrustTransition,
)

enum class TrustTransition { STAYING_TRUSTED, ENTERING_TRUSTED, LEAVING_TRUSTED, BROWSE_ONLY }
enum class TrustRevalidation { UNCHANGED, UPDATED, CANCELLED_PENDING, BLOCKED }

enum class BrowserNavigationBlockReason {
    INVALID_URL,
    PROFILE_NOT_ACTIVE,
    INVALID_AFIRMA_URI,
    UNSUPPORTED_INTENT,
    PLAY_STORE_FALLBACK,
    UNSUPPORTED_SCHEME,
}

sealed interface BrowserNavigationDecision {
    data class AllowTrusted(val url: NormalizedHttpsUrl, val profile: SiteProfile) : BrowserNavigationDecision
    data class AllowBrowseOnly(val url: NormalizedHttpsUrl) : BrowserNavigationDecision
    data class HandleSigning(val input: InterceptedSigningInput) : BrowserNavigationDecision
    data class Block(val reason: BrowserNavigationBlockReason) : BrowserNavigationDecision
}

interface BrowserTrustController {
    val state: StateFlow<BrowserTrustState>
    fun propose(url: NormalizedHttpsUrl): TopLevelNavigation
    fun observeCommitted(url: NormalizedHttpsUrl): TrustRevalidation
    fun onHistoryUrl(url: String): TrustRevalidation
    fun resetHome()
}
```

- [ ] **Step 1: Write RED state and navigation security tests**

```kotlin
@Test
fun trustedToUnknownCancelsPendingBeforeBrowseNavigation() {
    controller.observeCommitted(juntaUrl)
    val result = controller.propose(unknownHttps)
    assertEquals(TrustTransition.LEAVING_TRUSTED, result.transition)
    assertEquals(listOf(CancelReason.NAVIGATION_CHANGED), cancellations)
    assertTrue(controller.state.value is BrowserTrustState.BrowseOnly)
}

@Test
fun unknownHttpsStaysInWebViewButCannotRouteAfirmaOrIntent() {
    assertEquals(BrowserNavigationDecision.AllowBrowseOnly(unknownHttps), policy.decide(unknownHttps.serialized, unknownPage))
    assertEquals(BrowserNavigationDecision.Block(BrowserNavigationBlockReason.PROFILE_NOT_ACTIVE), policy.decide(validAfirma, unknownPage))
    assertEquals(BrowserNavigationDecision.Block(BrowserNavigationBlockReason.UNSUPPORTED_INTENT), policy.decide(arbitraryIntent, unknownPage))
}
```

Cover initial load, redirect, client redirect/page commit, history, restore, trusted iframe ignored, lookalike host, HTTP/custom schemes, Play fallback, and new navigation ID on every top-level transition.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*BrowserTrustControllerTest' --tests '*BrowserNavigationPolicyTest' --tests '*JuntaWebViewClientTest' --console=plain
```

Expected: FAIL because generic trust/navigation do not exist.

- [ ] **Step 3: Implement controller and migrate client through an adapter seam**

Generate random UUID navigation IDs. Cancel pending work synchronously before emitting leaving-trusted state. Revalidate in proposed and committed/history callbacks. `JuntaWebViewClient` accepts the generic policy/controller while retaining a constructor/default compatible with existing tests. Allowed unknown HTTPS returns `false` to stay in WebView; no call to `loadUrl` from `shouldOverrideUrlLoading`.

- [ ] **Step 4: Run focused GREEN and old Junta tests**

```bash
./gradlew testDebugUnitTest --tests '*BrowserTrustControllerTest' --tests '*BrowserNavigationPolicyTest' --tests '*JuntaWebViewClientTest' --tests '*JuntaNavigationPolicyTest' --console=plain
```

Expected: all transition/security cases pass; old policy still routes external URLs as its compatibility contract while the generic UI policy allows safe HTTPS browse-only.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/browser app/src/test/java/dev/junta/firmamobile/browser
git diff --cached --check
git commit -m "feat: enforce browser trust transitions"
```

Review gate: top-level origin is the sole trust driver; iframe/source origin cannot elevate; leaving trust cancels before load; custom intent never reaches Android.

---

### Task 10: Add address bar, navigation controls, progress, and trust status

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/ui/BrowserUiState.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/BrowserViewModel.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/BrowserChrome.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/BrowserDownloadPolicy.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebChromeClient.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/dev/junta/firmamobile/ui/BrowserViewModelTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/ui/BrowserChromeTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/browser/BrowserDownloadPolicyTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/BrowserAddressBarInstrumentedTest.kt`

**Interfaces:**

- Consumes: URL policy, trust controller, WebView callbacks.
- Produces:

```kotlin
enum class BrowserTrustLabel { TRUSTED, NORMAL, BLOCKED }
enum class CertificateAccessLabel { SIGNING_AVAILABLE, SIGNING_EXPERIMENTAL, DISABLED, LOCKED }

data class BrowserUiState(
    val addressText: String,
    val displayedHost: String?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val loading: Boolean,
    val progress: Int,
    val trustLabel: BrowserTrustLabel,
    val certificateLabel: CertificateAccessLabel,
    val inputError: BrowserUrlError?,
)

sealed interface BrowserCommand {
    data class Navigate(val url: NormalizedHttpsUrl) : BrowserCommand
    data object Back : BrowserCommand
    data object Forward : BrowserCommand
    data object Reload : BrowserCommand
    data object Stop : BrowserCommand
    data object Home : BrowserCommand
    data object OpenExternal : BrowserCommand
    data object ToggleFavorite : BrowserCommand
    data object ShowSiteInfo : BrowserCommand
    data object ClearCurrentSite : BrowserCommand
    data object ChangeCertificate : BrowserCommand
    data object LockCertificate : BrowserCommand
    data object CloseSession : BrowserCommand
    data object DeleteAllBrowserData : BrowserCommand
}

sealed interface BrowserDownloadDecision {
    data class AskToOpenExternal(val url: NormalizedHttpsUrl, val safeFileName: String?) : BrowserDownloadDecision
    data class Block(val error: BrowserUrlError) : BrowserDownloadDecision
}
```

- [ ] **Step 1: Write RED ViewModel/Compose tests**

```kotlin
@Test
fun invalidAddressShowsClosedErrorAndEmitsNoNavigation() {
    viewModel.submitAddress("palabras para buscar")
    assertEquals(BrowserTrustLabel.BLOCKED, viewModel.state.value.trustLabel)
    assertEquals("Introduce una dirección web completa y segura.", viewModel.errorText())
    assertTrue(commands.isEmpty())
}

@Test
fun chromeExposesTextualTrustAndAllControlsToAccessibility() {
    rule.setContent { BrowserChrome(trustedState, callbacks) }
    rule.onNodeWithContentDescription("Atrás").assertExists()
    rule.onNodeWithContentDescription("Adelante").assertExists()
    rule.onNodeWithContentDescription("Detener").assertExists()
    rule.onNodeWithText("Sitio de confianza").assertIsDisplayed()
    rule.onNodeWithText("Firma disponible").assertIsDisplayed()
    rule.onNodeWithContentDescription("Más opciones").performClick()
    listOf(
        "Abrir en navegador externo", "Añadir a favoritos", "Información del sitio",
        "Nivel de soporte de firma", "Limpiar datos de este sitio",
        "Cambiar certificado", "Bloquear certificado", "Cerrar sesión",
        "Eliminar todos los datos locales",
    ).forEach { rule.onNodeWithText(it).assertIsDisplayed() }
}
```

Cover domain paste, keyboard Go, full suspicious host visible, no search, loading indicator 0–100, reload↔stop, home, Back/Forward enablement, unknown labels, blocked label, experimental warning text, and query not persisted outside transient state. `BrowserDownloadPolicyTest` accepts only normalized HTTPS after explicit user confirmation, strips unsafe filename controls, passes no Cookie header, and blocks blob/data/content/file/custom download URLs. Web file upload chooser remains disabled with a clear message so an unknown page cannot request the certificate file.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*BrowserViewModelTest' --tests '*BrowserChromeTest' --tests '*BrowserDownloadPolicyTest' --console=plain
```

Expected: FAIL with missing UI state/ViewModel/chrome.

- [ ] **Step 3: Implement split browser chrome and bind one WebView**

Move toolbar code out of the 379-line `BrowserScreen`. Use a single-line editable field, IME Go, paste through `submitAddress`, textual semantics for trust/certificate, Back/Forward, Reload/Stop, Home and progress. Add all nine specified menu actions as typed commands; global delete has a confirmation distinct from close-session confirmation. `OpenExternal` accepts only the current normalized HTTPS URL and first cancels pending signing. Later Tasks 12 and 16 bind favorite/site-clear storage behavior; before then those commands return a closed unavailable state rather than a fake success. Bind WebView downloads to `BrowserDownloadPolicy`: show filename/host confirmation, then open the HTTPS URL externally without cookies or signing state; never auto-download. Override file chooser to return a controlled unsupported result in this release. Update state from `onPageStarted`, `onPageCommitVisible`, `onPageFinished`, `doUpdateVisitedHistory` and Chrome progress callbacks. Never persist `addressText`; ViewModel `SavedStateHandle` is not used for raw URLs.

- [ ] **Step 4: Run focused GREEN and Android-test compilation**

```bash
./gradlew testDebugUnitTest --tests '*BrowserViewModelTest' --tests '*BrowserChromeTest' --tests '*BrowserDownloadPolicyTest' --tests '*BrowserScreenTest' compileDebugAndroidTestKotlin --console=plain
```

Expected: all browser chrome tests pass; existing certificate UI tests stay green.

- [ ] **Step 5: Run required close sequence, build/install without launch, and commit**

```bash
./gradlew assembleDebug assembleRelease --console=plain
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -p 4 app/build/outputs/apk/debug/app-debug.apk
git add app/src/main/java/dev/junta/firmamobile/ui app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt app/src/main/java/dev/junta/firmamobile/browser/JuntaWebChromeClient.kt app/src/main/java/dev/junta/firmamobile/browser/BrowserDownloadPolicy.kt app/src/main/res/values/strings.xml app/src/test/java/dev/junta/firmamobile/ui app/src/test/java/dev/junta/firmamobile/browser/BrowserDownloadPolicyTest.kt app/src/androidTest/java/dev/junta/firmamobile/BrowserAddressBarInstrumentedTest.kt
git diff --cached --check
git commit -m "feat: add secure browser address controls"
```

Install/update and verify package manager without launching. Review gate: status is text-accessible, unknown site says certificate disabled, and no invalid input causes network.

---

### Task 11: Add native home, verified official shortcuts, and browse-without-certificate entry

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/browserdata/OfficialShortcut.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browserdata/OfficialShortcuts.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/BrowserHome.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/AppRoot.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/dev/junta/firmamobile/browserdata/OfficialShortcutsTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/ui/BrowserHomeTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/AppRootTest.kt`
- Create: `docs/official-site-sources.md`

**Interfaces:**

- Consumes: exact official URLs/source links verified in the design, `BrowserUrlPolicy`, `BrowserCommand.Navigate`, `DefaultSiteProfileRegistry`.
- Produces:

```kotlin
enum class OfficialCategory {
    JUNTA,
    GENERAL_STATE,
    SOCIAL_SECURITY,
    EMPLOYMENT,
    TAX,
    TRAFFIC,
    MUNICIPAL,
}

data class OfficialShortcut(
    val id: String,
    val label: String,
    val category: OfficialCategory,
    val url: NormalizedHttpsUrl,
    val support: SiteSupportLevel,
    val sourceUrl: NormalizedHttpsUrl,
    val verifiedOn: LocalDate,
)
```

- Only the current Junta signing start URL resolves to a signing profile. Junta general, AGE, Seguridad Social, SEPE, AEAT, DGT and municipal directory shortcuts are `BROWSE_ONLY`.

- [ ] **Step 1: Write RED home/source tests**

```kotlin
@Test
fun everyShortcutIsHttpsSourceBackedAndBrowseOnlyUnlessRegistryMatches() {
    OfficialShortcuts.all.forEach { shortcut ->
        assertEquals("https", shortcut.url.origin.scheme)
        assertEquals(LocalDate.parse("2026-07-12"), shortcut.verifiedOn)
        if (registry.profileForTopLevel(shortcut.url.origin) == null) {
            assertEquals(SiteSupportLevel.BROWSE_ONLY, shortcut.support)
        }
    }
}

@Test
fun homeOffersJuntaOtherSiteFavoritesRecentAndNoCertificateBrowsing() {
    rule.setContent { BrowserHome(state, callbacks) }
    rule.onNodeWithText("Junta de Andalucía").assertIsDisplayed()
    rule.onNodeWithText("Abrir otro sitio").assertIsDisplayed()
    rule.onNodeWithText("Favoritos").assertIsDisplayed()
    rule.onNodeWithText("Recientes").assertIsDisplayed()
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*OfficialShortcutsTest' --tests '*BrowserHomeTest' --tests '*AppRootTest' --console=plain
```

Expected: FAIL because home/shortcut types do not exist.

- [ ] **Step 3: Implement Compose-native home and optional browsing entry**

Hardcode only the seven source-verified HTTPS roots from the design with source URL/date metadata. Render them as browse-only cards; do not add profile origins. Add `Navegar sin certificado` to the certificate entry screen; it opens Home with `CertificateAccessLabel.DISABLED`. A Junta sign request while locked routes to certificate setup, never auto-unlocks.

- [ ] **Step 4: Run focused GREEN**

```bash
./gradlew testDebugUnitTest --tests '*OfficialShortcutsTest' --tests '*BrowserHomeTest' --tests '*AppRootTest' --tests '*FirstRunCopyTest' --console=plain
```

Expected: source and UI tests pass; all existing first-run disclosure/certificate actions remain visible.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/browserdata app/src/main/java/dev/junta/firmamobile/ui/BrowserHome.kt app/src/main/java/dev/junta/firmamobile/ui/AppRoot.kt app/src/main/java/dev/junta/firmamobile/MainActivity.kt app/src/main/res/values/strings.xml app/src/test/java/dev/junta/firmamobile/browserdata app/src/test/java/dev/junta/firmamobile/ui docs/official-site-sources.md
git diff --cached --check
git commit -m "feat: add verified browser home shortcuts"
```

Review gate: source documentation links only official pages; shortcuts never confer trust; browsing without a certificate cannot access `CertificateSession` through bridge.

---

### Task 12: Add safe favorites, recent history, and redacted WebView saved state

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/browserdata/BrowserDataModels.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browserdata/BrowserDataRepository.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browserdata/PreferencesBrowserDataStore.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/WebViewStateHolder.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserViewModel.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserHome.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/browserdata/BrowserDataRepositoryTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/browserdata/PreferencesBrowserDataStoreTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/WebViewStateHolderTest.kt`

**Interfaces:**

- Produces `Context.browserDataStore` with DataStore name `browser_data`; never touches `certificate_reference`.
- Produces:

```kotlin
data class SafeBrowserRecord(
    val urlWithoutQueryOrFragment: String,
    val host: String,
    val safeTitle: String,
    val lastVisitedAt: Instant,
)

data class BrowserData(
    val recent: List<SafeBrowserRecord>,
    val favorites: List<SafeBrowserRecord>,
)

interface BrowserDataRepository {
    val data: Flow<BrowserData>
    suspend fun recordVisit(url: NormalizedHttpsUrl, title: String?)
    suspend fun toggleFavorite(url: NormalizedHttpsUrl, title: String?)
    suspend fun clearOrigin(origin: TrustedOrigin)
    suspend fun clearAll()
}
```

- [ ] **Step 1: Write RED persistence/redaction tests**

```kotlin
@Test
fun recordsStripQueryFragmentControlsAndSensitiveKeys() = runTest {
    repository.recordVisit(policy.valid("https://example.es/path?token=secret#frag"), "Title\u0000")
    val record = repository.data.first().recent.single()
    assertEquals("https://example.es/path", record.urlWithoutQueryOrFragment)
    assertFalse(record.safeTitle.contains('\u0000'))
    assertFalse(serializedPreferences().contains("secret"))
}

@Test
fun WebViewBundleIsNotSavedWhenHistoryContainsQueryOrSigningScheme() {
    target.history = listOf("https://safe.es/", "https://safe.es/callback?token=x", "afirma://sign?dat=x")
    holder.save(target, outState, pendingSign = false)
    assertNull(outState.getBundle(WebViewStateHolder.STATE_KEY))
    assertEquals("https://safe.es/callback", outState.getString(WebViewStateHolder.FALLBACK_KEY))
}
```

Cover 50 recent/100 favorites caps, deterministic newest ordering, duplicate merge, title 120-char/control sanitization, query names such as token/code/state/signature/dat, no raw callback, pending sign disables Bundle, safe history allows Bundle, and corrupted DataStore entries fail closed without touching certificate store.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*BrowserDataRepositoryTest' --tests '*PreferencesBrowserDataStoreTest' --tests '*WebViewStateHolderTest' --console=plain
```

Expected: FAIL because browser data store and safe history API do not exist.

- [ ] **Step 3: Implement bounded app-private records and history inspection**

Store records as closed JSON strings in Preferences string sets with schema version `1`; parse only expected fields, cap record/string sizes and discard malformed entries. Strip all query/fragment unconditionally, not by a partial denylist. Bind `BrowserCommand.ToggleFavorite` and the Home favorites/recent lists to this repository. Bind confirmed `DeleteAllBrowserData` to repository clear plus the separately confirmed WebView global-data clear; never invoke it from `ClearCurrentSite`. `WebViewStateTarget` exposes copied history URLs; call `saveState` only when every entry has no query/fragment and is safe HTTPS and no pending request exists. Otherwise save one sanitized fallback URL.

- [ ] **Step 4: Run focused GREEN and DataStore regression**

```bash
./gradlew testDebugUnitTest --tests '*BrowserDataRepositoryTest' --tests '*PreferencesBrowserDataStoreTest' --tests '*WebViewStateHolderTest' --tests '*CertificateReferenceStoreTest' --console=plain
```

Expected: all redaction/cap/corruption cases pass; certificate reference data remains byte-for-byte behavior compatible.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/browserdata app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt app/src/main/java/dev/junta/firmamobile/browser/WebViewStateHolder.kt app/src/main/java/dev/junta/firmamobile/ui/BrowserViewModel.kt app/src/main/java/dev/junta/firmamobile/ui/BrowserHome.kt app/src/test/java/dev/junta/firmamobile/browserdata app/src/test/java/dev/junta/firmamobile/browser/WebViewStateHolderTest.kt
git diff --cached --check
git commit -m "feat: persist redacted browser data"
```

Review gate: inspect app-private serialized test data and Activity Bundle fixture for zero query/fragment/sign payload; backup remains disabled.

---

### Task 13: Bind WebMessage bridge lifecycle to active profile and navigation

**Files:**

- Modify: `app/src/main/java/dev/junta/firmamobile/browser/WebMessageProtocol.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/WebMessageRouter.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt`
- Modify: `app/src/main/res/raw/afirma_shim.js`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/WebMessageProtocolTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/WebMessageRouterTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/browser/WebMessageBridgeLifecycleTest.kt`
- Modify: `app/src/androidTest/java/dev/junta/firmamobile/browser/WebMessageBridgeInstrumentedTest.kt`

**Interfaces:**

- Consumes: `SiteProfileRegistry.bridgeOriginRules()`, `BrowserTrustController`, active navigation ID, `SigningProtocolAdapter` registry.
- Produces versioned bridge message types `CAPABILITIES_QUERY`, `SIGN_REQUEST`, `SIGN_CANCEL`, `SIGN_RESULT`, `SIGN_ERROR`; retains legacy `AFIRMA_URI` only for the Junta shim adapter.

```kotlin
interface ActiveSigningContextProvider {
    fun snapshot(): ActiveSigningContext?
}

data class ActiveSigningContext(
    val profileId: SiteProfileId,
    val profileVersion: Int,
    val origin: TrustedOrigin,
    val navigationId: NavigationId,
)
```

- [ ] **Step 1: Write RED lifecycle/origin tests**

```kotlin
@Test
fun unknownTopLevelCannotUseTrustedIframeBridge() {
    contextProvider.current = null
    val result = router.route(validJuntaMessage, juntaSourceOrigin, isMainFrame = false)
    assertEquals(PROFILE_NOT_ACTIVE, (result as Rejected).errorCode)
    assertEquals(0, adapter.calls)
}

@Test
fun staleNavigationAndTrustedToUnknownRejectWithoutCertificateAccess() {
    contextProvider.current = juntaContext(newNavigationId)
    val result = router.route(message(oldNavigationId), juntaSourceOrigin, true)
    assertEquals(NAVIGATION_CHANGED, (result as Rejected).errorCode)
    assertEquals(0, certificateSessionAccesses)
}
```

Cover unknown origin object absent, exact registry rules, source origin mismatch, unknown command, duplicate critical JSON keys, oversized message, iframe, profile version mismatch, leave/return, close idempotency, capabilities without certificate detail, and no unrestricted fallback when WebKit feature missing.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*WebMessageProtocolTest' --tests '*WebMessageRouterTest' --tests '*WebMessageBridgeLifecycleTest' --console=plain
```

Expected: FAIL because router does not consume active profile/navigation context and protocol has no versioned command set.

- [ ] **Step 3: Implement defense-in-depth bridge routing**

Register document-start/listener rules for the exact union of compiled `bridgeOrigins`; initially the same six Junta rules. Unknown origins receive no named object. Before adapter lookup, require active context, exact source/top-level origin, main frame, profile version, navigation ID, message version/type/UUID/size. On leaving trust, cancel pending; keep router refusal authoritative even if a page retained a JS reference. Never use `evaluateJavascript` to concatenate result data; use reply proxy JSON.

- [ ] **Step 4: Run focused GREEN and instrumentation compilation**

```bash
./gradlew testDebugUnitTest --tests '*WebMessageProtocolTest' --tests '*WebMessageRouterTest' --tests '*WebMessageBridgeLifecycleTest' --tests '*AfirmaJavascriptShimTest' compileDebugAndroidTestKotlin --console=plain
```

Expected: all origin/navigation/command cases pass and AndroidX listener/document script compile.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/browser app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt app/src/main/res/raw/afirma_shim.js app/src/test/java/dev/junta/firmamobile/browser app/src/androidTest/java/dev/junta/firmamobile/browser/WebMessageBridgeInstrumentedTest.kt
git diff --cached --check
git commit -m "feat: bind signing bridge to active profile"
```

Review gate: unknown top-level and trusted iframe both have zero adapter/session accesses; no dynamic origin rule or global JS interface exists.

---

### Task 14: Enforce strict bounded `afirma:` parsing for profile adapters

**Files:**

- Modify: `app/src/main/java/dev/junta/firmamobile/afirma/AfirmaModels.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/afirma/AfirmaUriParser.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/afirma/AfirmaUriParserTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/JuntaTriPhaseAdapter.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/signing/JuntaTriPhaseAdapterTest.kt`

**Interfaces:**

- Consumes: current parser raw+decoded parameter model and profile origin policy.
- Produces constants `MAX_URI_CHARS=1_048_576`, `MAX_PARAMETER_COUNT=64`, `MAX_PARAMETER_NAME_CHARS=64`, `MAX_ENCODED_VALUE_CHARS=786_432`, `MAX_DECODED_DAT_BYTES=524_288`, `MAX_PROTOCOL_URLS=3` and closed errors for each bound.

- [ ] **Step 1: Extend RED parser tests**

```kotlin
@Test
fun rejectsParameterFloodOversizedValueAndDecodedDat() {
    assertFailure(parser.parse(uriWithParameters(65), origin), TOO_MANY_PARAMETERS)
    assertFailure(parser.parse(uriWithValue(786_433), origin), PARAMETER_TOO_LARGE)
    assertFailure(parser.parse(uriWithDecodedDat(524_289), origin), DAT_TOO_LARGE)
}

@Test
fun rejectsDuplicateCriticalFieldsAndMoreThanOneProtocolUrlEach() {
    assertFailure(parser.parse("afirma://sign?algorithm=A&Algorithm=B&format=CAdES", origin), DUPLICATE_CRITICAL_PARAMETER)
    assertFailure(parser.parse(uriWithTwoServerUrls, origin), DUPLICATE_CRITICAL_PARAMETER)
}
```

Cover percent decode once, malformed encoding, base64/base64url bounds before allocation, case-insensitive critical duplicates, algorithm exact allowlist, CAdES exact format, max three named callback/server fields, unknown operation, unknown parameters preserved only as bounded typed values, and raw URI never included in errors/toString/logger.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*AfirmaUriParserTest' --tests '*JuntaTriPhaseAdapterTest' --console=plain
```

Expected: at least flood/value/decoded-size tests fail against the current parser.

- [ ] **Step 3: Implement pre-allocation bounds and profile algorithm/format policy**

Scan query segments once to enforce count/name/encoded size before decoding. Decode each value exactly once. Decode `dat` with a size-predicting bound and zero temporary bytes on failure/after transfer. Keep full raw URI private and override any diagnostic representation with operation/origin/length metadata only. Adapter accepts only algorithms/formats declared by Junta profile.

- [ ] **Step 4: Run focused GREEN and fuzz-like table cases**

```bash
./gradlew testDebugUnitTest --tests '*AfirmaUriParserTest' --tests '*JuntaTriPhaseAdapterTest' --console=plain
```

Expected: all old parser tests plus new limits pass without OOM or raw URI output.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/afirma app/src/main/java/dev/junta/firmamobile/signing/JuntaTriPhaseAdapter.kt app/src/test/java/dev/junta/firmamobile/afirma app/src/test/java/dev/junta/firmamobile/signing/JuntaTriPhaseAdapterTest.kt
git diff --cached --check
git commit -m "fix: bound AutoFirma request parsing"
```

Review gate: bounds execute before large allocation; parser never accepts origin/profile by suffix or TLS alone; error surfaces contain no payload.

---

### Task 15: Replace permissive `intent:` handling with strict internal AutoFirma conversion

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/afirma/AfirmaIntentParser.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/afirma/AfirmaIntentParserTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/BrowserNavigationPolicy.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaNavigationPolicy.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/BrowserNavigationPolicyTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaNavigationPolicyTest.kt`

**Interfaces:**

- Produces:

```kotlin
enum class AfirmaIntentError {
    TOO_LONG,
    MALFORMED,
    COMPONENT_NOT_ALLOWED,
    SELECTOR_NOT_ALLOWED,
    FALLBACK_NOT_ALLOWED,
    PLAY_STORE_FALLBACK,
    UNSUPPORTED_SCHEME,
    AFIRMA_PAYLOAD_MISSING,
}

sealed interface AfirmaIntentResult {
    data class InternalAfirma(val rawAfirmaUri: String) : AfirmaIntentResult
    data class Blocked(val reason: AfirmaIntentError) : AfirmaIntentResult
}

class AfirmaIntentParser {
    fun parse(rawIntentUri: String): AfirmaIntentResult
}
```

- It never calls `startActivity`, `resolveActivity`, PackageManager, or a browser fallback.

- [ ] **Step 1: Write RED strict-intent tests**

```kotlin
@Test
fun convertsOnlyAnEmbeddedAfirmaSchemeAndNeverExecutesPackage() {
    val result = parser.parse("intent://sign?...#Intent;scheme=afirma;package=es.gob.afirma;end")
    assertTrue(result is InternalAfirma)
    assertTrue((result as InternalAfirma).rawAfirmaUri.startsWith("afirma://sign"))
}

@Test
fun blocksFallbackComponentSelectorAndArbitrarySchemes() {
    listOf(intentWithBrowserFallback, intentWithComponent, intentWithSelector, marketIntent, customPayIntent)
        .forEach { assertTrue(parser.parse(it) is Blocked) }
}
```

Also test 1 MiB bound, malformed `#Intent`, duplicate scheme/package fields, explicit package without embedded afirma data, Play URL, HTTP fallback and percent-encoded nested intent.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*AfirmaIntentParserTest' --tests '*BrowserNavigationPolicyTest' --tests '*JuntaNavigationPolicyTest' --console=plain
```

Expected: FAIL because the dedicated parser does not exist and current policy can open a safe HTTP(S) fallback.

- [ ] **Step 3: Implement parse-only internal conversion**

Use `Intent.parseUri(..., URI_INTENT_SCHEME)` only as a parser. Reject component, selector, clip data, categories outside BROWSABLE, browser fallback, non-AutoFirma package without embedded afirma, and every non-afirma data scheme. A package marker is ignored as an execution target only when an embedded normalized `afirma:` URI exists; return that URI to `AfirmaUriParser` and never retain the `Intent` object.

- [ ] **Step 4: Run focused GREEN and PackageManager absence scan**

```bash
./gradlew testDebugUnitTest --tests '*AfirmaIntentParserTest' --tests '*BrowserNavigationPolicyTest' --tests '*JuntaNavigationPolicyTest' --console=plain
rg -n 'startActivity|resolveActivity|queryIntentActivities' app/src/main/java/dev/junta/firmamobile/afirma app/src/main/java/dev/junta/firmamobile/browser
```

Expected: tests pass; scan finds only the explicitly reviewed external-browser menu path outside intent/afirma routing, never these parser/policy files.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/afirma/AfirmaIntentParser.kt app/src/main/java/dev/junta/firmamobile/browser/BrowserNavigationPolicy.kt app/src/main/java/dev/junta/firmamobile/browser/JuntaNavigationPolicy.kt app/src/test/java/dev/junta/firmamobile/afirma/AfirmaIntentParserTest.kt app/src/test/java/dev/junta/firmamobile/browser/BrowserNavigationPolicyTest.kt app/src/test/java/dev/junta/firmamobile/browser/JuntaNavigationPolicyTest.kt
git diff --cached --check
git commit -m "fix: contain AutoFirma intents inside the app"
```

Review gate: no fallback URL, package, component or Play target can cause an external action; existing internal Junta afirma-intent case remains green.

---

### Task 16: Enforce per-profile cookie/network policy and measure WebView capabilities

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/browser/WebViewProfileCapabilities.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/browser/SiteDataCleaner.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/network/ProfileCookieBridge.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/TrustedJuntaWebView.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/dev/junta/firmamobile/browser/WebViewProfileCapabilitiesTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/browser/SiteDataCleanerTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/network/ProfileCookieBridgeTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/browser/WebViewCapabilitiesInstrumentedTest.kt`
- Create: `docs/device-qa.md`

**Interfaces:**

- Consumes: active `SiteProfile`, its `ProfileCookiePolicy`/`networkOrigins`, `AndroidWebCookieStore`, AndroidX WebKit feature checks.
- Produces:

```kotlin
data class WebViewProfileCapabilities(
    val providerPackage: String,
    val providerVersion: String,
    val multiProfile: Boolean,
    val getCookieInfo: Boolean,
    val webMessageListener: Boolean,
    val documentStartScript: Boolean,
)

enum class SiteClearResult {
    CLEARED_EXACTLY,
    WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE,
    FAILED,
}

class ProfileCookieBridge(
    private val profile: SiteProfile,
    private val cookieStore: WebCookieStore,
) {
    fun cookieHeaderFor(url: NormalizedHttpsUrl): String?
    fun applySetCookie(url: NormalizedHttpsUrl, value: String): Boolean
}
```

- [ ] **Step 1: Write RED logical-isolation and cleaner tests**

```kotlin
@Test
fun nativeCookiesNeverCrossProfileOrReachBrowseOnly() {
    val juntaBridge = ProfileCookieBridge(juntaProfile, store)
    assertEquals("SESSION=opaque", juntaBridge.cookieHeaderFor(juntaNetworkUrl))
    assertNull(juntaBridge.cookieHeaderFor(aeatUrl))
    assertFailsWith<IllegalArgumentException> { ProfileCookieBridge(browseOnlyProfile, store) }
}

@Test
fun clearCurrentSiteNeverFallsBackToGlobalCookieDeletion() {
    val result = cleaner.clearOrigin(exampleOrigin, capabilities(getCookieInfo = false))
    assertEquals(SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE, result)
    assertEquals(0, store.removeAllCalls)
}
```

Cover read/write purpose mismatch, redirects, CRLF/size, exact host/port, cookies absent, no log values, WebStorage origin deletion, exact cookie expiry when info supported, and global delete only from a separately confirmed method.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*WebViewProfileCapabilitiesTest' --tests '*SiteDataCleanerTest' --tests '*ProfileCookieBridgeTest' --console=plain
```

Expected: FAIL because capability/cleaner/profile bridge types do not exist.

- [ ] **Step 3: Implement conservative capabilities and logical isolation**

Query feature booleans without assuming provider version. `ProfileCookieBridge` rejects a profile/origin not explicitly allowed before calling CookieManager. Bind `BrowserCommand.ClearCurrentSite` to `SiteDataCleaner.clearOrigin` for the current normalized origin only and surface the exact/limited result in site info. `SiteDataCleaner.clearOrigin` calls `WebStorage.deleteOrigin`; if `GET_COOKIE_INFO` is true, parse only cookie name/domain/path attributes needed to set an expired same-origin cookie, never log/return values. If unavailable, return the Spanish limitation and leave cookies intact until user confirms global clear. Keep one default WebView physical profile in this plan even when `MULTI_PROFILE=true`; record capability but do not call `setProfile`.

- [ ] **Step 4: Run focused GREEN and Android-test compilation**

```bash
./gradlew testDebugUnitTest --tests '*WebViewProfileCapabilitiesTest' --tests '*SiteDataCleanerTest' --tests '*ProfileCookieBridgeTest' --tests '*WebViewCookieBridgeTest' compileDebugAndroidTestKotlin --console=plain
```

Expected: exact logical isolation and cleanup tests pass; existing Junta cookie tests stay green.

- [ ] **Step 5: Run required close sequence**

Run the global close sequence. Expected: no cookie strings in test output/reports and no global clear from site-specific action.

- [ ] **Step 6: Run the device capability instrumentation gate**

Build/install debug and androidTest APKs without starting MainActivity. Run only:

```bash
adb shell am instrument -w -e class dev.junta.firmamobile.browser.WebViewCapabilitiesInstrumentedTest dev.junta.firmamobile.test/androidx.test.runner.AndroidJUnitRunner
```

Use the local Android-shell equivalent if adb transport is unavailable. Expected: provider package/version and four booleans are recorded in instrumentation output with no URLs/cookies. Write the observed values to `docs/device-qa.md`. This is allowed runtime instrumentation and does not open the app UI.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/browser/WebViewProfileCapabilities.kt app/src/main/java/dev/junta/firmamobile/browser/SiteDataCleaner.kt app/src/main/java/dev/junta/firmamobile/network/ProfileCookieBridge.kt app/src/main/java/dev/junta/firmamobile/browser/TrustedJuntaWebView.kt app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt app/src/main/res/values/strings.xml app/src/test/java/dev/junta/firmamobile/browser app/src/test/java/dev/junta/firmamobile/network/ProfileCookieBridgeTest.kt app/src/androidTest/java/dev/junta/firmamobile/browser/WebViewCapabilitiesInstrumentedTest.kt docs/device-qa.md
git diff --cached --check
git commit -m "feat: isolate native cookies by site profile"
```

Review gate: documentation distinguishes API availability from adopted physical isolation; no feature result changes trust automatically.

---

### Task 17: Make protocol registry and confirmation fully profile-aware

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/signing/SigningProtocolRegistry.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/SigningModels.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/SigningUiState.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/SigningConfirmationDialog.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/WebMessageRouter.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/dev/junta/firmamobile/signing/SigningProtocolRegistryTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/signing/SigningCoordinatorTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/ui/SigningConfirmationDialogTest.kt`

**Interfaces:**

- `SigningProtocolRegistry` is immutable and contains only `JuntaTriPhaseAdapter` under `junta-afirma-triphase-cades-v1`.

```kotlin
interface SigningProtocolRegistry {
    fun adapterFor(profile: SiteProfile, input: InterceptedSigningInput): SigningProtocolAdapter?
}

data class ConfirmationSnapshot(
    val requestId: UUID,
    val profileId: SiteProfileId,
    val profileVersion: Int,
    val supportLevel: SiteSupportLevel,
    val origin: TrustedOrigin,
    val navigationId: NavigationId,
    val expiresAt: Instant,
    val payloadFingerprint: ByteArray,
    val certificateSummary: CertificateSummary,
)
```

- [ ] **Step 1: Write RED profile/adapter/confirmation tests**

```kotlin
@Test
fun unknownAndBrowseOnlyProfilesHaveNoAdapterOrConfirmation() {
    assertNull(registry.adapterFor(browseOnlyProfile, validAfirmaInput))
    assertEquals(PROFILE_NOT_ACTIVE, coordinator.prepare(input, browseOnlyContext).error)
    assertEquals(0, certificateSessionAccesses)
}

@Test
fun experimentalAndVerifiedLabelsAreHonestAndEveryModeRequiresFirmar() {
    assertTrue(dialogText(experimentalSnapshot).contains("Firma experimental"))
    assertTrue(dialogText(verifiedSnapshot).contains("Firma disponible"))
    assertEquals(0, engine.callsBeforeFirmar)
}
```

Cover missing adapter, protocol not declared by profile, profile/version changed, support downgrade, origin/navigation/payload/certificate changed, expired request, experimental warning, verified text, cancel, one-shot and no empty Fire/MiniApplet adapter.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*SigningProtocolRegistryTest' --tests '*SigningCoordinatorTest' --tests '*SigningConfirmationDialogTest' --console=plain
```

Expected: FAIL because current coordinator/dialog are Junta-specific and no registry exists.

- [ ] **Step 3: Implement exact adapter selection and profile snapshot revalidation**

Registry first verifies profile support is not BROWSE_ONLY, request scheme is declared, adapter ID is in profile protocols, adapter recognizes input, and active top-level context matches. Build the full snapshot; show profile display name/support. On `Firmar`, compare every field and fingerprint in constant time before reading identity. Do not add placeholder adapters.

- [ ] **Step 4: Run focused GREEN**

```bash
./gradlew testDebugUnitTest --tests '*SigningProtocolRegistryTest' --tests '*SigningCoordinatorTest' --tests '*SigningConfirmationDialogTest' --tests '*WebMessageRouterTest' --console=plain
```

Expected: all selection/revalidation/UI cases pass; unknown/browse-only session access remains zero.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/signing app/src/main/java/dev/junta/firmamobile/ui/SigningUiState.kt app/src/main/java/dev/junta/firmamobile/ui/SigningConfirmationDialog.kt app/src/main/java/dev/junta/firmamobile/browser/WebMessageRouter.kt app/src/main/res/values/strings.xml app/src/test/java/dev/junta/firmamobile/signing app/src/test/java/dev/junta/firmamobile/ui/SigningConfirmationDialogTest.kt
git diff --cached --check
git commit -m "refactor: select signing by active site profile"
```

Review gate: only one concrete adapter exists; no generic arbitrary-data signing API; profile changes cannot reuse a pending request.

---

### Task 18: Add full Junta profile regression coverage

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/AppDependencies.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt`
- Modify: `app/src/androidTest/java/dev/junta/firmamobile/TestCertificateDependencies.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/site/JuntaProfileRegressionTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/signing/JuntaSigningRegressionTest.kt`
- Modify: `app/src/androidTest/java/dev/junta/firmamobile/CertificateSetupFlowTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/JuntaProfileFlowInstrumentedTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/browser/TrustedToBrowseOnlyInstrumentedTest.kt`
- Modify: `docs/test-plan.md`

**Interfaces:**

- Consumes all production boundaries through dependency-injection seams and synthetic PKCS#12/controlled WebView content.
- Produces a deterministic regression matrix proving current Junta behavior survived generalization; it does not contact the real portal or use the real certificate.

- [ ] **Step 1: Write RED integrated regression tests**

```kotlin
@Test
fun JuntaProfileMatchesEveryLegacyOriginPolicyDecision() {
    LEGACY_CASES.forEach { case ->
        assertEquals(case.expected, genericPolicy.decisionFor(case.url, case.currentUrl))
        assertEquals(case.legacySecurityOutcome, juntaFacade.decisionFor(case.url, case.currentUrl))
    }
}

@Test
fun syntheticJuntaFlowRequiresUnlockConfirmAndReturnsTypedResult() = runTest {
    harness.unlockSyntheticIdentity()
    harness.interceptSyntheticJuntaRequest()
    assertEquals(AwaitingConfirmation::class, harness.state::class)
    harness.confirm()
    assertEquals(Completed::class, harness.state::class)
    assertEquals(1, harness.callbackDeliveries)
}
```

Instrumentation covers first-run/SAF, locked/unlocked, Continue default Junta, exact trust labels, bridge request, Play blocked, confirmation cancel/confirm using fake adapter, navigation cancel, restore redaction, session clear and lock.

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests '*JuntaProfileRegressionTest' --tests '*JuntaSigningRegressionTest' compileDebugAndroidTestKotlin --console=plain
```

Expected: at least missing integrated harness/instrumentation seams fail compilation or assertions.

- [ ] **Step 3: Add test-only seams, never production bypasses**

Add an internal `AppDependencies` container whose production factory constructs the real registry, adapter, browser command sink, cookie store and clock. `JuntaFirmaApplication` owns that container; `TestCertificateDependencies` swaps it transactionally and restores it in `close()`. Release defaults always use production implementations. No debug Activity or fake signing service is merged into release.

- [ ] **Step 4: Run focused GREEN and full instrumentation compilation**

```bash
./gradlew testDebugUnitTest --tests '*JuntaProfileRegressionTest' --tests '*JuntaSigningRegressionTest' --tests '*JuntaOriginPolicyTest' --tests '*JuntaNavigationPolicyTest' --tests '*AfirmaUriParserTest' compileDebugAndroidTestKotlin --console=plain
```

Expected: all legacy and generic matrices pass; instrumentation compiles with synthetic fixtures only.

- [ ] **Step 5: Run required close sequence and commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/AppDependencies.kt app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt app/src/androidTest/java/dev/junta/firmamobile/TestCertificateDependencies.kt app/src/test/java/dev/junta/firmamobile/site/JuntaProfileRegressionTest.kt app/src/test/java/dev/junta/firmamobile/signing/JuntaSigningRegressionTest.kt app/src/androidTest/java/dev/junta/firmamobile/CertificateSetupFlowTest.kt app/src/androidTest/java/dev/junta/firmamobile/JuntaProfileFlowInstrumentedTest.kt app/src/androidTest/java/dev/junta/firmamobile/browser/TrustedToBrowseOnlyInstrumentedTest.kt docs/test-plan.md
git diff --cached --check
git commit -m "test: preserve Junta flow across site profiles"
```

Review gate: release manifest/source set contains no fake gateway/adapter; no existing certificate/navigation test was weakened or removed.

---

### Task 19: Run multi-site instrumentation and POCO runtime QA

**Files:**

- Create: `app/src/androidTest/java/dev/junta/firmamobile/BrowserBrowseOnlyInstrumentedTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/BrowserDangerousSchemeInstrumentedTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/browser/ControlledHttpsContent.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/browser/BridgeOriginIsolationInstrumentedTest.kt`
- Create: `app/src/androidTest/java/dev/junta/firmamobile/browser/BrowserHistoryInstrumentedTest.kt`
- Modify: `docs/device-qa.md`
- Modify: `docs/test-report.md`

**Interfaces:**

- Consumes production APK/test APK, controlled HTTPS WebView content where possible, and POCO F6 Pro runtime.
- Produces direct device evidence for unknown browse-only, trusted Junta, transition cancellation, controls/history, dangerous schemes, Play suppression, bridge origin/subframe and release settings. It uses no real certificate; Task 6 already owns that credential gate.

- [ ] **Step 1: Write RED instrumentation cases**

Cases must assert:

```text
unknown HTTPS loads + Sitio normal + Certificado desactivado
unknown top-level cannot post bridge message
trusted Junta shows Sitio de confianza + Firma disponible
trusted→unknown shows leaving warning and cancels pending
unknown iframe/trusted iframe cannot elevate top-level
Back/Forward/Home/Reload/Stop work
safe history restores; query/signing payload does not restore
HTTP/javascript/file/content/data/blob/custom blocked
arbitrary intent and Play fallback do not launch any activity
release WebView debugging false and app non-debuggable
```

- [ ] **Step 2: Run expected pre-device RED**

```bash
./gradlew compileDebugAndroidTestKotlin --console=plain
```

Expected: FAIL until all four instrumentation classes and required test seams exist; after compilation, device gate remains `Not run` and therefore RED.

- [ ] **Step 3: Complete deterministic instrumentation implementations**

Use `ControlledHttpsContent` with AndroidX `WebViewAssetLoader`/test-only request interception under reserved test hosts, plus Espresso intents; do not add production cleartext exceptions. Do not depend on external portal for deterministic assertions. Test bridge messages with exact origins and `isMainFrame`; verify no resolver intent for afirma/intent/market.

- [ ] **Step 4: Run full local pre-device gate**

```bash
./gradlew clean testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --console=plain
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -p 4 app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -p 4 app/build/outputs/apk/release/app-release.apk
```

Expected: clean build and APK structural gates pass.

- [ ] **Step 5: Install without launch, then run the device GREEN instrumentation gate**

Install/update target and test APKs through `/data/local/tmp`, verify package manager, then run the four named classes plus existing certificate/WebView classes with `am instrument -w -e class ...`. Expected: all tests `OK`; no app UI is opened outside instrumentation.

- [ ] **Step 6: Execute one manual browser UI gate**

Launch MainActivity because address bar/trust transition is new UI. Without selecting a real certificate: enter a benign external HTTPS official shortcut, verify browse-only labels; navigate to Junta, verify trusted label; navigate out and verify warning; exercise Back/Forward/Home/Reload; enter dangerous schemes and verify blocked copy; trigger controlled Play fallback and verify no Play activity. Capture only a non-personal screenshot/UI tree and sanitized logcat event codes.

- [ ] **Step 7: Update evidence, run security audit, and commit**

```bash
rg -n 'Cookie:|Set-Cookie:|afirma://|BEGIN PRIVATE KEY|password=' docs/device-qa.md docs/test-report.md && exit 1 || true
git add app/src/androidTest/java/dev/junta/firmamobile docs/device-qa.md docs/test-report.md
git diff --cached --check
git commit -m "test: verify secure multi-site browsing on Android 16"
```

Review gate: every device claim maps to executed instrumentation/manual evidence; screenshots contain no identity, form data, token or certificate details.

---

### Task 20: Finalize documentation, support status, release signing, and artifacts

**Files:**

- Modify: `docs/spec.md`
- Modify: `docs/threat-model.md`
- Modify: `docs/test-plan.md`
- Modify: `docs/test-report.md`
- Modify: `docs/protocol-observations.md`
- Modify: `docs/building-on-termux.md`
- Create: `docs/site-profile-support.md`
- Create: `README.md`
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore`
- Create outside repository: `/data/data/com.termux/files/home/.local/share/junta-firma-mobile/release-signing.jks`
- Create outside repository: `/data/data/com.termux/files/home/.config/junta-firma-mobile/release-signing.env` with mode `0600`
- Export after verification: `/storage/emulated/0/Codex/Outputs/junta-firma-mobile/`

**Interfaces:**

- Consumes every prior commit and direct Task 6/16/19 evidence.
- Produces final debug APK, v2/v3-signed non-debuggable release APK, SHA-256/fingerprint report, source commit ID, install instructions and honest support matrix. Junta is `FULLY_VERIFIED` only if Task 6 passed; all other shortcuts are `BROWSE_ONLY`.

- [ ] **Step 1: Write the failing final checklist**

Add checkboxes to `docs/test-report.md` for clean build, all unit/instrumentation, POCO browser QA, Junta E2E, secret audit, release non-debuggable, WebView debugging off, v2/v3, alignment, hashes, source commit and exported paths. Expected RED: unchecked until direct verification.

- [ ] **Step 2: Update normative docs and support matrix**

Document two modes, URL rules, exact profiles, support evidence, physical-cookie limitation/capability result, bridge lifecycle, data retention/clear semantics, real E2E result and unsupported protocols. `site-profile-support.md` lists profile ID/version, exact origins by purpose, protocol, level, last E2E date/device/app version and evidence link; it never lists personal certificate data.

- [ ] **Step 3: Configure external release signing without repository secrets**

Create a 4096-bit RSA APK-signing key at `/data/data/com.termux/files/home/.local/share/junta-firma-mobile/release-signing.jks` only if it does not already exist. Generate random store/key passwords without printing them, save them only in `/data/data/com.termux/files/home/.config/junta-firma-mobile/release-signing.env` with mode `0600`, and reference them through environment variables in Gradle. `.gitignore` rejects `*.jks`, `*.keystore`, signing properties and secret env files. Set release `isDebuggable=false`, `enableV1Signing=false`, `enableV2Signing=true`, `enableV3Signing=true`; never reuse the user's PKCS#12 as an APK key.

- [ ] **Step 4: Run clean final verification-before-completion gate**

```bash
./gradlew clean testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug assembleRelease --console=plain
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -p 4 app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -p 4 app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump xmltree app/build/outputs/apk/release/app-release.apk AndroidManifest.xml
```

Expected: all tests/lint/builds pass; release reports v2 and v3 true, alignment valid, package/version/min26/target36 correct, no debuggable/testOnly.

- [ ] **Step 5: Run final static and archive secret audit**

```bash
rg -n 'addJavascriptInterface|handler\.proceed\(|PrivateKey\.encoded|privateKey\.encoded|allowUniversalAccessFromFileURLs\s*=\s*true|MIXED_CONTENT_ALWAYS_ALLOW|trustAll|ALLOW_ALL_HOSTNAME_VERIFIER' app/src/main && exit 1 || true
git ls-files | rg '\.(p12|pfx|jks|keystore|env)$' && exit 1 || true
unzip -l app/build/outputs/apk/release/app-release.apk | rg 'androidTest|ProtocolProbe|synthetic-identity|\.p12|\.pfx' && exit 1 || true
git diff --check
```

Expected: no prohibited result; release contains no test/probe/certificate artifacts.

- [ ] **Step 6: Install release without auto-launch and verify package manager**

Stage the release APK, `pm install -r`, verify `pm path`/`dumpsys package`, signing certificate digest, non-debuggable flag and version. Do not reopen UI; Task 6 and Task 19 already supplied required runtime evidence for the exact source state. If final signing/config changed executable behavior, rerun the relevant runtime gate before acceptance.

- [ ] **Step 7: Export artifacts and commit source/docs**

Export only APKs, `junta-firma-mobile-source-<commit>.tar.gz` without Git ignored secrets/build caches, and `validation-report.txt` containing hashes/public signing certificate fingerprint/commit/validation summary under `/storage/emulated/0/Codex/Outputs/junta-firma-mobile/`. Then:

```bash
git add README.md .gitignore app/build.gradle.kts docs
git diff --cached --check
git commit -m "release: finalize secure multi-site browser"
git status --short --branch
```

Review gate: worktree clean, no push, artifacts open/verify, no real certificate/credential, every readiness claim backed by evidence. Release remains not ready if Task 6 portal acceptance is absent.

---

## Requirement coverage matrix

| Requirement area | Implemented/verified by |
|---|---|
| Preserve current Junta, finish its signing contour and real E2E first | Tasks 1–6, 18, 20 |
| Safe HTTPS normalization; no search; dangerous schemes/credentials/IDN blocked | Tasks 7, 9, 10, 19 |
| Generic SiteProfile/OriginPolicy/registry and Junta compatibility facade | Task 8 |
| FULLY_VERIFIED/EXPERIMENTAL/BROWSE_ONLY truthfulness | Tasks 6, 8, 10, 17, 20 |
| Trusted/browse-only transitions, redirects, history and iframe rules | Tasks 9, 13, 18, 19 |
| Back/Forward/Reload-Stop/Home/address/status/progress/accessibility | Tasks 10, 19 |
| Native home, official shortcuts, favorites and recent history | Tasks 11, 12 |
| No certificate requirement for ordinary browsing | Task 11 |
| Origin-restricted versioned bridge and message classes | Task 13 |
| Strict bounded `afirma:` and internal-only `intent:` | Tasks 14, 15 |
| SigningProtocolAdapter boundary with no placeholder adapters | Tasks 2, 4, 17 |
| Explicit confirmation and post-confirmation revalidation | Tasks 2, 5, 17, 18 |
| Profile cookie/network policy, exact site cleanup and physical-capability honesty | Task 16 |
| No instant user-created trust and honest site info/menu | Tasks 8, 10, 11, 16, 17 |
| Saved-state/history signing-payload redaction | Tasks 2, 12, 13, 19 |
| Existing certificate/SAF/WebView/Junta regression suite | Tasks 7–18, especially 18 |
| Mandatory security cases and release hardening | Tasks 7–20 |
| POCO instrumentation/runtime QA without repetitive launches | Tasks 1, 6, 16, 19, 20 |
| Documentation, support reporting, APK/signature/hash deliverables | Task 20 |

No additional signing site profile is created by this plan. A future profile
starts a separate evidence/design/plan/E2E cycle and cannot reuse Junta's
`FULLY_VERIFIED` result.

## Plan self-review checklist

- [x] All 23 design/requirement sections map to at least one Task 1–20.
- [x] Tasks 1–6 complete current Junta observation/signing/E2E before multi-site production activation.
- [x] Tasks 7–17 cover URL policy, SiteProfile registry/facade, trust state, address UI, home, safe data, bridge lifecycle, strict afirma/intent, cookie isolation and profile-aware confirmation.
- [x] Tasks 18–20 cover Junta regressions, instrumentation/POCO QA, documentation/support status and release artifacts.
- [x] Every task has RED, expected failure, minimal implementation, focused GREEN, full suite/lint/security, exact commit and review gate.
- [x] No task uses a real certificate automatically or puts its path/password in code, tests, docs, logs or commands.
- [x] No placeholder adapter/profile or universal AutoFirma compatibility is planned.
- [x] Physical WebView profile isolation is measured but not claimed/adopted without a separate regression-proven decision.
- [x] Junta remains the only signing profile and is promoted only by real E2E.
