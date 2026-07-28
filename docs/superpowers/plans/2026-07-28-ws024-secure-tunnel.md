# WS024 Secure Tunnel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a QA-only, direct-first, fixed-destination TLS tunnel fallback for the exact Junta `ws024` tri-phase endpoint without exposing signing contents to the relay or requiring any user-installed companion app.

**Architecture:** Android keeps the official `ws024` URI and uses HTTP/1.1 only. A direct request may fall back once to an outer-TLS CONNECT relay only when instrumentation proves that zero HTTP bytes were written. The relay opens TCP only to `ws024.juntadeandalucia.es:443` and copies opaque bytes while Android performs a second, inner TLS handshake that verifies the official `ws024` hostname.

**Tech Stack:** Kotlin/JVM 17, Android API 26–36, OkHttp/MockWebServer, JUnit/Robolectric, Go 1.24+, `crypto/tls`, `net/http`, `net/netip`.

## Global Constraints

- The official protocol endpoint remains exactly `https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService` for `junta-ofvirtual`.
- The first milestone enables tunnel eligibility only for `(profileId=junta-ofvirtual, endpoint=the exact MiniApplet 1.5 URI)`.
- `junta-andalucia` MiniApplet 1.4 remains direct-only until a separate E2E review.
- Tri-phase HTTP uses only HTTP/1.1; HTTP/2 is disabled for this transport.
- Automatic fallback is allowed only for `DNS_BEFORE_CONNECT`, `TCP_BEFORE_HTTP_BYTES`, or `TLS_BEFORE_HTTP_BYTES` with an observed write count of zero.
- Any unknown phase or any failure after the first HTTP byte maps to `NETWORK_RESULT_UNCERTAIN` and is never retried automatically.
- The inner TLS connection verifies `ws024.juntadeandalucia.es` with the platform trust manager and hostname verifier; no trust-all code, user CA override, or relay certificate is accepted as an upstream certificate.
- The relay accepts only `CONNECT ws024.juntadeandalucia.es:443 HTTP/1.1` and has no caller-supplied upstream argument.
- The relay never terminates, parses, logs, caches, or persists the inner TLS stream.
- QA credentials are injected outside Git and are unavailable to release builds.
- Release remains direct-only in this plan. Play Integrity, `jti+nonce` proof-of-possession issuance, and production deployment require a later, separately approved plan.
- No log may contain certificate data, signature data, request body, authorization token, full URL, session identifier, challenge, or a stable hash derived from tri-phase parameters.
- Existing private release-signing gates must remain intact; no debug-key fallback is permitted.
- This milestone adds no Storage/Retrieve, document signing, co-signing, counter-signing, or new portal capability.

---

## File Structure

### Android files to create

- `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpFailurePhase.kt` — wire-phase and route result types.
- `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpCallPhaseTracker.kt` — conservative OkHttp `EventListener` state machine that marks HTTP write-start before request headers are sent.
- `app/src/main/java/dev/junta/firmamobile/network/SecureTunnelPolicy.kt` — exact profile+endpoint eligibility.
- `app/src/main/java/dev/junta/firmamobile/network/SecureTunnelProtocol.kt` — CONNECT request/response codec and bounded headers.
- `app/src/main/java/dev/junta/firmamobile/network/SecureTunnelSocketFactory.kt` — outer TLS, fixed CONNECT, inner-stream socket.
- `app/src/main/java/dev/junta/firmamobile/network/DirectFirstProfileHttpTransport.kt` — direct-first orchestration and one safe fallback.
- `app/src/main/java/dev/junta/firmamobile/network/TunnelRouteEvent.kt` — request-correlated fixed route progress events.
- `app/src/main/java/dev/junta/firmamobile/network/SecureTunnelRuntime.kt` — QA-only configuration/credential seams and release fail-closed factory.
- `app/src/debug/java/dev/junta/firmamobile/network/QaOneShotTunnelCredentialProvider.kt` — debuggable-only private one-shot credential loader.
- `app/src/test/java/dev/junta/firmamobile/network/ProfileHttpCallPhaseTrackerTest.kt`
- `app/src/test/java/dev/junta/firmamobile/network/SecureTunnelPolicyTest.kt`
- `app/src/test/java/dev/junta/firmamobile/network/SecureTunnelProtocolTest.kt`
- `app/src/test/java/dev/junta/firmamobile/network/SecureTunnelSocketFactoryTest.kt`
- `app/src/test/java/dev/junta/firmamobile/network/DirectFirstProfileHttpTransportTest.kt`

### Android files to modify

- `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpTransport.kt` — phase-aware failure, HTTP/1.1-only executor, write instrumentation.
- `app/src/main/java/dev/junta/firmamobile/signing/TriPhaseExecutionAdapter.kt` — exact network error mapping.
- `app/src/main/java/dev/junta/firmamobile/signing/JuntaOfvirtualTriPhaseAdapter.kt` — injected direct-first transport.
- `app/src/main/java/dev/junta/firmamobile/signing/JuntaTriPhaseAdapter.kt` — explicit direct-only transport.
- `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt` — runtime transport factory ownership.
- `app/src/main/java/dev/junta/firmamobile/MainActivity.kt` — adapter construction from application factory.
- `app/src/main/java/dev/junta/firmamobile/signing/SigningModels.kt` — new closed error codes.
- `app/src/main/java/dev/junta/firmamobile/signing/SigningNetworkProgress.kt` — request-correlated coordinator transitions.
- `app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt` — tunnel-connecting UI state propagation.
- `app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt` — tunnel events without parameter hashes.
- `app/src/main/java/dev/junta/firmamobile/ui/SigningStatusDialog.kt`
- `app/src/main/res/values/strings.xml`
- `app/build.gradle.kts` — QA-only Gradle fields and test dependencies without secret values.
- Existing test files listed in each task.

### Relay files to create

- `ws024-relay/go.mod`
- `ws024-relay/cmd/ws024-relay/main.go`
- `ws024-relay/internal/relay/config.go`
- `ws024-relay/internal/relay/connect.go`
- `ws024-relay/internal/relay/upstream.go`
- `ws024-relay/internal/relay/credentials.go`
- `ws024-relay/internal/relay/admission.go`
- `ws024-relay/internal/relay/pump.go`
- `ws024-relay/internal/relay/server.go`
- `ws024-relay/internal/relay/audit.go`
- Corresponding `_test.go` files.

### Integration/documentation files to create or modify

- `tools/ws024_tunnel_harness.py` — starts synthetic upstream and relay, prints only safe result codes.
- `scripts/verify-ws024-tunnel.sh` — deterministic local gate.
- `docs/test-report.md`
- `docs/security-roadmap.md`
- `docs/compatibility/spanish-government-signing-matrix.md`

---

### Task 1: Introduce a conservative pre-HTTP/after-HTTP failure model

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpFailurePhase.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpCallPhaseTracker.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpTransport.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/ProfileHttpCallPhaseTrackerTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/ProfileHttpTransportTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  internal enum class ProfileHttpFailurePhase {
      DNS_BEFORE_CONNECT,
      TCP_BEFORE_HTTP_BYTES,
      TLS_BEFORE_HTTP_BYTES,
      HTTP_WRITE_STARTED,
      READ_AFTER_HTTP_WRITE,
      UNKNOWN,
  }

  internal data class ProfileHttpFailureDetail(
      val code: ProfileHttpFailure,
      val phase: ProfileHttpFailurePhase,
      val httpWriteStarted: Boolean,
  ) {
      val safeForRouteFallback: Boolean
          get() = !httpWriteStarted && phase in SAFE_PRE_WRITE_PHASES
  }

  internal class ProfileHttpCallPhaseTracker : okhttp3.EventListener() {
      override fun connectStart(call: Call, address: InetSocketAddress, proxy: Proxy)
      override fun secureConnectStart(call: Call)
      override fun requestHeadersStart(call: Call)
      override fun responseHeadersStart(call: Call)
      fun dnsFailure(code: ProfileHttpFailure): ProfileHttpFailureDetail
      fun failure(code: ProfileHttpFailure): ProfileHttpFailureDetail
  }

  class ProfileHttpCancellation internal constructor() {
      fun cancel()
      fun isCancelled(): Boolean
      internal fun beginAttempt(tracker: ProfileHttpCallPhaseTracker): Boolean
      internal fun snapshotFailure(code: ProfileHttpFailure): ProfileHttpFailureDetail
  }
  ```
- `ProfileHttpResult.Failure` becomes:
  ```kotlin
  data class Failure(val detail: ProfileHttpFailureDetail) : ProfileHttpResult
  ```

- [ ] **Step 1: Write RED tracker tests**

  ```kotlin
  @Test
  fun requestHeadersStartConservativelyClosesTheFallbackWindow() {
      val tracker = ProfileHttpCallPhaseTracker()
      tracker.connectStart(call, address, Proxy.NO_PROXY)
      tracker.secureConnectStart(call)
      assertTrue(tracker.failure(ProfileHttpFailure.NETWORK_ERROR).safeForRouteFallback)

      tracker.requestHeadersStart(call)

      val failure = tracker.failure(ProfileHttpFailure.NETWORK_ERROR)
      assertEquals(ProfileHttpFailurePhase.HTTP_WRITE_STARTED, failure.phase)
      assertTrue(failure.httpWriteStarted)
      assertFalse(failure.safeForRouteFallback)
  }

  @Test
  fun aRaceCanOnlyBecomeUnsafeNeverFallbackSafe() {
      val cancellation = ProfileHttpCancellation()
      val tracker = ProfileHttpCallPhaseTracker()
      assertTrue(cancellation.beginAttempt(tracker))
      tracker.requestHeadersStart(call)
      cancellation.cancel()
      assertFalse(cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR).safeForRouteFallback)
      assertFalse(cancellation.beginAttempt(ProfileHttpCallPhaseTracker()))
  }
  ```

- [ ] **Step 2: Run RED**

  ```bash
  ./gradlew testDebugUnitTest \
    --tests dev.junta.firmamobile.network.ProfileHttpCallPhaseTrackerTest \
    --no-daemon
  ```
  Expected: compilation failure because the new types do not exist.

- [ ] **Step 3: Implement the monotonic phase tracker**

  `requestHeadersStart()` must set `HTTP_WRITE_STARTED` before OkHttp starts transmitting headers. `responseHeadersStart()` sets `READ_AFTER_HTTP_WRITE`. State never moves backwards. `failure()` returns `UNKNOWN/httpWriteStarted=true` if events do not prove a safe pre-HTTP phase; exception class and elapsed time are never sufficient evidence.

- [ ] **Step 4: Bind each network attempt to cancellation**

  `ProfileHttpCancellation.beginAttempt()` atomically publishes the current tracker before DNS/TCP work. It rejects a new attempt after cancellation. `snapshotFailure()` reads the current monotonic tracker and returns unsafe `UNKNOWN` when none exists. Add tests for:

  - cancellation during DNS/TCP/TLS before headers preserves the exact phase;
  - cancellation or deadline after `requestHeadersStart` returns `HTTP_WRITE_STARTED` or stricter;
  - a concurrent timeout/request-header race can never return a safe phase;
  - a tunnel attempt cannot bind after cancellation.

- [ ] **Step 5: Integrate phase-aware failures into `HttpsProfileHttpTransport`**

  Create one tracker per OkHttp call through `EventListener.Factory`, bind it before resolution/network execution, and map every transport error to `ProfileHttpFailureDetail`. Existing HTTP/auth/content/size codes remain unchanged but carry a non-fallback-safe phase after headers.

- [ ] **Step 6: Force HTTP/1.1**

  In `OkHttpProfileHttpExecutor.buildClient()` add:

  ```kotlin
  .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
  ```

  Assert `client.protocols == listOf(Protocol.HTTP_1_1)` and retain disabled redirects, cookies, authenticators, proxy, retries and cache.

- [ ] **Step 7: Run focused regressions**

  ```bash
  ./gradlew testDebugUnitTest \
    --tests dev.junta.firmamobile.network.ProfileHttpCallPhaseTrackerTest \
    --tests dev.junta.firmamobile.network.ProfileHttpTransportTest \
    --tests dev.junta.firmamobile.signing.TriPhaseExecutionAdapterTest \
    --no-daemon
  ```
  Expected: PASS.

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/main/java/dev/junta/firmamobile/network \
          app/src/test/java/dev/junta/firmamobile/network
  git commit -m "feat: classify tri-phase network failures by wire phase"
  ```
---

### Task 2: Define exact tunnel eligibility and route contracts

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/network/SecureTunnelPolicy.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/SecureTunnelPolicyTest.kt`

**Interfaces:**
- Consumes: `ProfileId`, exact `URI`.
- Produces:
  ```kotlin
  internal data class SecureTunnelBinding(
      val profileId: ProfileId,
      val endpoint: URI,
  )

  internal class SecureTunnelPolicy private constructor(
      private val bindings: Set<SecureTunnelBinding>,
  ) {
      fun allows(profileId: ProfileId, endpoint: URI): Boolean

      companion object {
          val QA: SecureTunnelPolicy
          val RELEASE: SecureTunnelPolicy
      }
  }
  ```

- [ ] **Step 1: Write RED tests for exact binding**

  ```kotlin
  @Test
  fun qaAllowsOnlyOfvirtualMiniApplet15ExactTuple() {
      assertTrue(SecureTunnelPolicy.QA.allows(ProfileId("junta-ofvirtual"), OFVIRTUAL_15))
      assertFalse(SecureTunnelPolicy.QA.allows(ProfileId("junta-andalucia"), JUNTA_14))
      assertFalse(SecureTunnelPolicy.QA.allows(ProfileId("junta-ofvirtual"), URI("https://ws024.juntadeandalucia.es/")))
      assertFalse(SecureTunnelPolicy.QA.allows(ProfileId("unizar-tramitador"), OFVIRTUAL_15))
  }

  @Test
  fun releaseAllowsNoTunnelBindings() {
      assertFalse(SecureTunnelPolicy.RELEASE.allows(ProfileId("junta-ofvirtual"), OFVIRTUAL_15))
  }
  ```

- [ ] **Step 2: Run RED**

  ```bash
  ./gradlew testDebugUnitTest --tests dev.junta.firmamobile.network.SecureTunnelPolicyTest --no-daemon
  ```

- [ ] **Step 3: Implement an immutable exact set**

  Use `URI.equals`, not host-only matching, prefix matching, normalization after construction, or profile branding.

- [ ] **Step 4: Run GREEN and full profile tests**

  ```bash
  ./gradlew testDebugUnitTest \
    --tests dev.junta.firmamobile.network.SecureTunnelPolicyTest \
    --tests dev.junta.firmamobile.profile.SiteProfileCatalogParserTest \
    --tests dev.junta.firmamobile.profile.RuntimeProfilePolicyTest \
    --no-daemon
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/dev/junta/firmamobile/network/SecureTunnelPolicy.kt \
          app/src/test/java/dev/junta/firmamobile/network/SecureTunnelPolicyTest.kt
  git commit -m "feat: restrict secure tunnel to exact ws024 contract"
  ```

---

### Task 3: Implement the bounded CONNECT protocol codec

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/network/SecureTunnelProtocol.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/SecureTunnelProtocolTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  internal class QATunnelCredential internal constructor(private val opaqueValue: CharArray) : AutoCloseable

  internal data class SecureTunnelConnectRequest(
      val authority: String = SecureTunnelProtocol.FIXED_AUTHORITY,
      val protocolVersion: String = SecureTunnelProtocol.VERSION,
      val authorization: CharArray,
  )

  internal sealed interface SecureTunnelConnectResult {
      data object Established : SecureTunnelConnectResult
      data class Rejected(val code: SecureTunnelRejectCode) : SecureTunnelConnectResult
  }

  internal object SecureTunnelProtocol {
      const val FIXED_AUTHORITY = "ws024.juntadeandalucia.es:443"
      const val VERSION = "1"
      const val MAX_RESPONSE_HEADER_BYTES = 8192
      fun encodeConnect(request: SecureTunnelConnectRequest): ByteArray
      fun readResponse(input: InputStream): SecureTunnelConnectResult
  }
  ```

- [ ] **Step 1: Write RED codec tests**

  Test exact bytes for:

  ```text
  CONNECT ws024.juntadeandalucia.es:443 HTTP/1.1\r\n
  Host: ws024.juntadeandalucia.es:443\r\n
  Authorization: Bearer synthetic-qa-token\r\n
  X-WS024-Tunnel-Version: 1\r\n
  \r\n
  ```

  Add rejection tests for LF-only, headers larger than 8192 bytes, duplicate status lines, non-200 status, response body before tunnel establishment, and values containing CR/LF.

- [ ] **Step 2: Run RED**

  ```bash
  ./gradlew testDebugUnitTest --tests dev.junta.firmamobile.network.SecureTunnelProtocolTest --no-daemon
  ```

- [ ] **Step 3: Implement strict encoding and bounded response parsing**

  Copy credential chars to a short-lived UTF-8 byte array, reject control characters, and zero temporary arrays in `finally`. Do not log or retain the authorization value.

- [ ] **Step 4: Run GREEN**

  ```bash
  ./gradlew testDebugUnitTest --tests dev.junta.firmamobile.network.SecureTunnelProtocolTest --no-daemon
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/dev/junta/firmamobile/network/SecureTunnelProtocol.kt \
          app/src/test/java/dev/junta/firmamobile/network/SecureTunnelProtocolTest.kt
  git commit -m "feat: add fixed ws024 CONNECT protocol"
  ```

---

### Task 4: Build outer TLS and preserve inner TLS verification

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/network/SecureTunnelSocketFactory.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/network/SecureTunnelSocketFactoryTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpTransport.kt`

**Interfaces:**
- Produces:
  ```kotlin
  internal data class SecureTunnelRelay(
      val host: String,
      val port: Int,
      val spkiPins: Set<String>,
  )

  internal fun interface TunnelCredentialProvider {
      fun acquire(): QATunnelCredential?
  }

  internal fun interface TunnelSocketFactoryProvider {
      fun create(
          expectedUpstreamHost: String,
          approvedUpstreamAddresses: Set<InetAddress>,
          cancellation: ProfileHttpCancellation,
      ): SocketFactory
  }

  internal class SecureTunnelSocketFactory(
      private val relay: SecureTunnelRelay,
      private val credentialProvider: TunnelCredentialProvider,
      private val expectedUpstreamHost: String,
      private val approvedUpstreamAddresses: Set<InetAddress>,
      private val cancellation: ProfileHttpCancellation,
      private val outerClientFactory: (SecureTunnelRelay, Int) -> SSLSocket,
  ) : SocketFactory()

  internal class TunnelBackedSocket(...) : Socket() {
      override fun connect(endpoint: SocketAddress, timeout: Int)
      override fun getInputStream(): InputStream
      override fun getOutputStream(): OutputStream
      override fun setSoTimeout(timeout: Int)
      override fun getSoTimeout(): Int
      override fun shutdownInput()
      override fun shutdownOutput()
      override fun isConnected(): Boolean
      override fun isClosed(): Boolean
      override fun isInputShutdown(): Boolean
      override fun isOutputShutdown(): Boolean
      override fun getInetAddress(): InetAddress?
      override fun getRemoteSocketAddress(): SocketAddress?
      override fun getPort(): Int
      override fun getLocalAddress(): InetAddress
      override fun getLocalSocketAddress(): SocketAddress?
      override fun getLocalPort(): Int
      override fun setTcpNoDelay(on: Boolean)
      override fun getTcpNoDelay(): Boolean
      override fun setKeepAlive(on: Boolean)
      override fun getKeepAlive(): Boolean
      override fun close()
  }
  ```

- [ ] **Step 1: Write a synthetic double-TLS RED test**

  Use separate `HeldCertificate` chains:

  - outer relay SAN `relay.example`;
  - inner upstream SAN `ws024.juntadeandalucia.es`.

  Establish outer TLS, observe the exact CONNECT, return `200`, then let OkHttp create the inner TLS layer for the original `https://ws024...` URL. Assert the relay sees only opaque TLS records after CONNECT.

- [ ] **Step 2: Write resolved-IP socket contract tests**

  OkHttp's `Dns` gives `InetAddress` objects to the route selector, so `Socket.connect()` receives a resolved `InetSocketAddress`. Test that the socket:

  - accepts only port `443` and an address in the exact `approvedUpstreamAddresses` set produced by the validated lookup for `expectedUpstreamHost`;
  - rejects unresolved, null, unapproved and differently ported endpoints before outer TLS;
  - ignores the accepted IP only for physical routing and sends the fixed CONNECT authority;
  - reports the accepted logical address/port through socket getters.

- [ ] **Step 3: Write trust-separation RED tests**

  - Wrong relay hostname or unpinned relay SPKI: no CONNECT.
  - Correct relay but inner leaf SAN `evil.example`: outer CONNECT succeeds, inner TLS fails and no HTTP POST reaches upstream.
  - Relay certificate can never satisfy inner trust.
  - Primary and backup SPKI pins are independently accepted; an unrelated pin is rejected.

- [ ] **Step 4: Run RED**

  ```bash
  ./gradlew testDebugUnitTest \
    --tests dev.junta.firmamobile.network.SecureTunnelSocketFactoryTest \
    --no-daemon
  ```

- [ ] **Step 5: Implement `TunnelBackedSocket` completely**

  `createSocket()` returns an unconnected wrapper. On accepted `connect()` it opens outer TLS with TLS 1.2 minimum, TLS 1.3 preferred, no 0-RTT path, relay hostname verification and SPKI pinning; sends one CONNECT; accepts only exact `200`; then delegates every listed stream/state/timeout/TCP/local/remote/half-close method to the established outer `SSLSocket`. Cancellation closes the outer socket. Credential and CONNECT buffers are zeroed immediately after the response.

- [ ] **Step 6: Adapt the OkHttp executor per call**

  Add `tunnelSocketFactoryProvider: TunnelSocketFactoryProvider?`. After existing DNS/public-address validation:

  ```kotlin
  val routeSocketFactory = tunnelSocketFactoryProvider?.create(
      expectedHost,
      approvedAddresses.toSet(),
      cancellation,
  )
  routeSocketFactory?.let(builder::socketFactory)
  ```

  Keep the platform/system `SSLSocketFactory`, trust manager and hostname verifier for the inner connection. Keep `.protocols(listOf(HTTP_1_1))`.

- [ ] **Step 7: Run GREEN and regression**

  ```bash
  ./gradlew testDebugUnitTest \
    --tests dev.junta.firmamobile.network.SecureTunnelSocketFactoryTest \
    --tests dev.junta.firmamobile.network.ProfileHttpTransportTest \
    --no-daemon
  ```

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/main/java/dev/junta/firmamobile/network \
          app/src/test/java/dev/junta/firmamobile/network
  git commit -m "feat: add double-tls ws024 tunnel socket"
  ```
---

### Task 5: Add direct-first, one-shot safe fallback

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/network/DirectFirstProfileHttpTransport.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/network/TunnelRouteEvent.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/DirectFirstProfileHttpTransportTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  internal enum class ProfileHttpRoute { DIRECT, SECURE_TUNNEL }

  internal enum class TunnelRouteStage {
      DIRECT_FAILED_PRE_HTTP,
      TUNNEL_CONNECTING,
      TUNNEL_ESTABLISHED,
      TUNNEL_FAILED,
  }

  internal data class TunnelRouteEvent(
      val requestId: UUID,
      val route: ProfileHttpRoute,
      val stage: TunnelRouteStage,
      val phase: ProfileHttpFailurePhase? = null,
      val resultCode: ProfileHttpFailure? = null,
  )

  internal fun interface TunnelRouteObserver {
      fun onEvent(event: TunnelRouteEvent)
  }

  internal class DirectFirstProfileHttpTransport(
      private val profileId: ProfileId,
      private val endpoint: URI,
      private val policy: SecureTunnelPolicy,
      private val direct: ProfileHttpTransport,
      private val tunnel: ProfileHttpTransport?,
      private val observer: TunnelRouteObserver,
  ) : ProfileHttpTransport
  ```

- [ ] **Step 1: Write RED decision-table tests**

  Parameterize the three safe phases and assert direct then one tunnel call. Parameterize every unsafe/unknown phase and assert one direct call and zero tunnel calls.

  ```kotlin
  @Test
  fun aFailureAfterRequestHeadersStartIsNeverRetried() {
      val direct = QueueTransport(
          failure(ProfileHttpFailurePhase.HTTP_WRITE_STARTED, httpWriteStarted = true),
      )
      val tunnel = QueueTransport(success("must-not-run"))
      val result = transport(direct, tunnel).post(request(), cancellation())
      assertEquals(1, direct.calls)
      assertEquals(0, tunnel.calls)
      assertEquals(ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN, result.failureCode())
  }
  ```

- [ ] **Step 2: Add cancellation/deadline RED tests**

  Assert:

  - cancellation between safe direct failure and tunnel binding atomically blocks tunnel;
  - caller cancellation after `requestHeadersStart` propagates and makes zero tunnel calls;
  - an internal deadline after `requestHeadersStart` returns `NETWORK_RESULT_UNCERTAIN` and makes zero tunnel calls;
  - an unknown tracker snapshot is never retried;
  - HTTP response errors, HTML, wrong content type, oversized response and parse errors never trigger fallback.

- [ ] **Step 3: Add correlation/event RED tests**

  Add immutable `requestId: UUID` to `ProfileHttpRequest`. Every emitted event must carry that UUID, but observer/logger tests must prove it is never rendered or serialized. Expected sequence on successful fallback:

  ```text
  DIRECT_FAILED_PRE_HTTP
  TUNNEL_CONNECTING
  TUNNEL_ESTABLISHED
  ```

  A failed tunnel emits `TUNNEL_FAILED` instead of `TUNNEL_ESTABLISHED`; no duplicate terminal event is allowed.

- [ ] **Step 4: Run RED**

  ```bash
  ./gradlew testDebugUnitTest \
    --tests dev.junta.firmamobile.network.DirectFirstProfileHttpTransportTest \
    --no-daemon
  ```

- [ ] **Step 5: Implement one-shot orchestration**

  `ProfileHttpRequest.duplicateForRetry()` preserves the request UUID and creates an independent owned body copy before direct execution. After a safe direct failure, check `cancellation.isCancelled()` and atomically bind the tunnel attempt; either failure blocks tunnel. Close and zero the unused retry copy in every path.

  Do not implement a persistent network cache in this task.

- [ ] **Step 6: Add closed route errors**

  Add:

  ```kotlin
  DIRECT_CONNECT_UNAVAILABLE,
  TUNNEL_AUTH_UNAVAILABLE,
  TUNNEL_CONNECT_UNAVAILABLE,
  UPSTREAM_CONNECT_UNAVAILABLE,
  NETWORK_RESULT_UNCERTAIN,
  ```

  Any possible HTTP write maps to `NETWORK_RESULT_UNCERTAIN`.

- [ ] **Step 7: Run GREEN**

  ```bash
  ./gradlew testDebugUnitTest \
    --tests dev.junta.firmamobile.network.DirectFirstProfileHttpTransportTest \
    --tests dev.junta.firmamobile.network.ProfileHttpTransportTest \
    --no-daemon
  ```

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/main/java/dev/junta/firmamobile/network \
          app/src/test/java/dev/junta/firmamobile/network
  git commit -m "feat: add safe direct-first ws024 fallback"
  ```
---

### Task 6: Wire QA-only runtime configuration without embedding a secret

**Files:**
- Create: `app/src/main/java/dev/junta/firmamobile/network/SecureTunnelRuntime.kt`
- Create: `app/src/debug/java/dev/junta/firmamobile/network/QaOneShotTunnelCredentialProvider.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/network/QaOneShotTunnelCredentialProviderTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/dev/junta/firmamobile/JuntaFirmaApplication.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/JuntaOfvirtualTriPhaseAdapter.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/JuntaTriPhaseAdapter.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/signing/JuntaOfvirtualTriPhaseAdapterTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/signing/JuntaTriPhaseAdapterTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  internal interface SecureTunnelRuntime {
      fun transportFor(
          profileId: ProfileId,
          endpoint: URI,
          observer: TunnelRouteObserver,
      ): ProfileHttpTransport
  }

  internal class DirectOnlyTunnelRuntime(...) : SecureTunnelRuntime
  internal class QaSecureTunnelRuntime(...) : SecureTunnelRuntime
  ```

- [ ] **Step 1: Write RED runtime tests**

  Assert:

  - QA exact 1.5 tuple with valid host, port, two pins and credential provider returns direct-first transport;
  - absent or partial configuration, invalid host/port, control characters or fewer than two pins fail closed;
  - debug, release, MiniApplet 1.4 and Unizar remain direct-only;
  - no credential appears in BuildConfig/resources/generated APK fixtures.

- [ ] **Step 2: Define exact variant-specific public configuration**

  Before `android {}`:

  ```kotlin
  val qaRelayHost = providers.secretValue("JFM_WS024_QA_RELAY_HOST") ?: ""
  val qaRelayPort = (providers.secretValue("JFM_WS024_QA_RELAY_PORT") ?: "443").toInt()
  val qaRelayPins = providers.secretValue("JFM_WS024_QA_RELAY_SPKI_PINS") ?: ""
  val qaTunnelConfigured = qaRelayHost.isNotBlank() &&
      qaRelayPort in 1..65535 &&
      qaRelayPins.split(',').map(String::trim).filter(String::isNotEmpty).size >= 2
  ```

  If any one of host/port/pins is explicitly supplied while the complete tuple is invalid, throw `GradleException`. Add all four fields to each variant:

  ```kotlin
  debug/release: ENABLE=false, HOST="", PORT=443, PINS=""
  qa: ENABLE=qaTunnelConfigured, HOST=quoted(qaRelayHost),
      PORT=qaRelayPort, PINS=quoted(qaRelayPins)
  ```

  Exact field names:

  ```text
  ENABLE_WS024_QA_TUNNEL
  WS024_QA_RELAY_HOST
  WS024_QA_RELAY_PORT
  WS024_QA_RELAY_SPKI_PINS
  ```

  `quoted()` escapes Java literals and rejects control characters. Host/port/pins are public. There is no credential field.

- [ ] **Step 3: Add a debuggable-only one-shot credential loader**

  `QaOneShotTunnelCredentialProvider` reads at most 512 bytes from:

  ```text
  no_backup/ws024-qa-credential.once
  ```

  It rejects symlinks/non-regular files, deletes the file before returning, stores only a closeable `CharArray`, zeroes temporary bytes, and returns `null` for missing/invalid files. Tests cover deletion-on-read, oversize, second-read-null and zeroing. Release must not contain this class/path.

  Developer-only injection, with shell tracing disabled:

  ```bash
  printf '%s' "$JFM_WS024_QA_CREDENTIAL" | \
    rish -c 'run-as dev.junta.firmamobile sh -c "umask 077; cat > no_backup/ws024-qa-credential.once"'
  ```

- [ ] **Step 4: Wire runtime and adapters**

  `JuntaFirmaApplication` owns the runtime. `MainActivity` creates a lazy observer callback and obtains:

  ```kotlin
  val routeObserver = TunnelRouteObserver(::onTunnelRouteEvent)
  val ofvirtualTransport = app.secureTunnelRuntime.transportFor(
      ProfileId("junta-ofvirtual"),
      URI(JuntaOfvirtualTriPhaseAdapter.ENDPOINT),
      routeObserver,
  )
  val juntaAdapter = JuntaTriPhaseAdapter(transport = directJuntaTransport)
  val ofvirtualAdapter = JuntaOfvirtualTriPhaseAdapter(transport = ofvirtualTransport)
  ```

  Task 7 implements `onTunnelRouteEvent`; until then use a no-op callback covered by compilation tests.

- [ ] **Step 5: Run variant and adapter tests**

  ```bash
  ./gradlew testDebugUnitTest testQaUnitTest \
    --tests dev.junta.firmamobile.network.QaOneShotTunnelCredentialProviderTest \
    --tests dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapterTest \
    --tests dev.junta.firmamobile.signing.JuntaTriPhaseAdapterTest \
    --no-daemon
  ```

- [ ] **Step 6: Assert release fail-closed**

  Extend runtime policy tests to inspect release configuration and assert empty policy/host/pins. Do not bypass `verifyReleaseSigning`.

- [ ] **Step 7: Commit**

  ```bash
  git add app/build.gradle.kts app/src/main/java app/src/debug/java \
          app/src/test/java/dev/junta/firmamobile
  git commit -m "feat: wire qa-only ws024 tunnel runtime"
  ```
---

### Task 7: Add closed user-visible errors and request-correlated progress

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/SigningModels.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/signing/SigningNetworkProgress.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/TriPhaseExecutionAdapter.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/SigningStatusDialog.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/dev/junta/firmamobile/security/SanitizedLoggerTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/ui/SigningStatusDialogTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/signing/SigningCoordinatorTest.kt`
- Test: `app/src/test/java/dev/junta/firmamobile/signing/TriPhaseExecutionAdapterTest.kt`

**Interfaces:**
- Add:
  ```kotlin
  SigningErrorCode.SIGNING_SERVICE_UNAVAILABLE
  SigningErrorCode.NETWORK_RESULT_UNCERTAIN
  SigningUiState.ConnectingSecurely(requestId: UUID)

  internal fun SigningCoordinator.onTunnelRouteEvent(event: TunnelRouteEvent)
  ```

- [ ] **Step 1: Write RED UI/progress tests**

  Matching active request ID transitions:

  ```text
  Signing → TUNNEL_CONNECTING → ConnectingSecurely
  ConnectingSecurely → TUNNEL_ESTABLISHED → Signing
  ```

  Stale/wrong request IDs are ignored. Exact Spanish copy:

  ```text
  Conectando de forma segura con el servicio de firma…
  No se pudo conectar con el servicio de firma de la Junta. Inténtalo de nuevo más tarde.
  El resultado de red no es seguro. Vuelve al portal e inicia la operación de nuevo.
  ```

  Assert UI contains no proxy/CONNECT/Tor terminology, relay host, path, token, certificate owner or UUID.

- [ ] **Step 2: Write RED timeout/cancellation tests**

  In `TriPhaseExecutionAdapterTest` assert:

  - internal deadline calls `cancellation.cancel()` and maps `cancellation.snapshotFailure()`;
  - deadline after `requestHeadersStart` becomes `NETWORK_RESULT_UNCERTAIN` and never starts tunnel;
  - caller coroutine cancellation remains propagated `CancellationException`, cancels the bound call and prevents a subsequent attempt;
  - adapter never fabricates a fresh fallback-safe `NETWORK_ERROR` after timeout.

- [ ] **Step 3: Write RED sanitized logging tests**

  Tunnel events serialize only fixed route/stage/phase/result tokens and coarse duration buckets. UUID, parameter hashes (`sha256_8`), exact sizes, values, credentials and raw exception messages are absent.

- [ ] **Step 4: Implement progress ownership**

  `MainActivity.onTunnelRouteEvent()` posts to `Dispatchers.Main.immediate`, passes the event to `SigningCoordinator`, and records a separately sanitized event. The UUID exists only for in-memory matching and is never logged. The coordinator updates UI only when the event UUID equals the active operation.

- [ ] **Step 5: Implement network error mapping**

  - both routes unavailable before HTTP → `SIGNING_SERVICE_UNAVAILABLE`;
  - after-write or unknown result → `NETWORK_RESULT_UNCERTAIN`;
  - session/HTTP/content/parse mappings stay unchanged.

- [ ] **Step 6: Run focused tests**

  ```bash
  ./gradlew testDebugUnitTest \
    --tests dev.junta.firmamobile.security.SanitizedLoggerTest \
    --tests dev.junta.firmamobile.ui.SigningStatusDialogTest \
    --tests dev.junta.firmamobile.signing.SigningCoordinatorTest \
    --tests dev.junta.firmamobile.signing.TriPhaseExecutionAdapterTest \
    --no-daemon
  ```

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/main/java/dev/junta/firmamobile \
          app/src/main/res/values/strings.xml \
          app/src/test/java/dev/junta/firmamobile
  git commit -m "feat: report secure tunnel progress without sensitive data"
  ```
---

### Task 8: Build the fixed-destination Go relay parser and dialer

**Files:**
- Create: `ws024-relay/go.mod`
- Create: `ws024-relay/internal/relay/config.go`
- Create: `ws024-relay/internal/relay/connect.go`
- Create: `ws024-relay/internal/relay/upstream.go`
- Create tests: `connect_test.go`, `upstream_test.go`

**Interfaces:**
- Produces:
  ```go
  const FixedAuthority = "ws024.juntadeandalucia.es:443"
  const TunnelProtocolVersion = "1"

  type ConnectRequest struct {
      Credential []byte
  }

  func ParseFixedCONNECT(r *bufio.Reader, maxHeaderBytes int64) (ConnectRequest, error)

  type Resolver interface {
      LookupNetIP(ctx context.Context, network, host string) ([]netip.Addr, error)
  }

  type TCPDialer interface {
      DialContext(ctx context.Context, network, address string) (net.Conn, error)
  }

  type FixedUpstreamDialer interface {
      DialContext(ctx context.Context) (net.Conn, netip.Addr, error)
  }
  ```

- [ ] **Step 1: Initialize the Go module**

  `go.mod`:

  ```go
  module github.com/zaguzovmaksim0-hue/workspace-47/ws024-relay

  go 1.24
  ```

- [ ] **Step 2: Write RED CONNECT parser tests**

  Cover exact success plus method, target, Host, version, duplicate headers, missing authorization, content length, transfer encoding, obs-fold, LF-only, userinfo, trailing dot, IP literal, path/query, extra bytes and oversized headers.

- [ ] **Step 3: Run RED**

  ```bash
  cd ws024-relay
  go test ./internal/relay -run 'TestParseFixedCONNECT' -count=1
  ```

- [ ] **Step 4: Implement the strict parser**

  Accept only the exact request described in Task 3. Return generic typed errors that do not echo input.

- [ ] **Step 5: Write RED public-IP and literal-dial tests**

  Test private, loopback, link-local, CGNAT, documentation, benchmark, multicast, reserved, ULA and non-global IPv6. Assert the dialer receives an approved IP literal plus `:443`, never the hostname.

- [ ] **Step 6: Implement `IsPublicRoutable` and fixed dialer**

  Resolve once, validate each address, dial the chosen literal, and verify `RemoteAddr()` equals the selected IP. There is no host/port parameter on the public interface.

- [ ] **Step 7: Run tests and vet**

  ```bash
  go test ./... -count=1
  go vet ./...
  ```

- [ ] **Step 8: Commit**

  ```bash
  git add ws024-relay
  git commit -m "feat: add fixed ws024 relay admission path"
  ```

---

### Task 9: Add QA authentication, admission, bounded pump and safe audit

**Files:**
- Create: `ws024-relay/internal/relay/credentials.go`
- Create: `ws024-relay/internal/relay/admission.go`
- Create: `ws024-relay/internal/relay/pump.go`
- Create: `ws024-relay/internal/relay/audit.go`
- Create: `ws024-relay/internal/relay/server.go`
- Create: `ws024-relay/cmd/ws024-relay/main.go`
- Create corresponding tests.

**Interfaces:**
- Produces:
  ```go
  type CredentialVerifier interface {
      Verify(ctx context.Context, raw []byte, peer netip.Addr) (CredentialGrant, error)
  }

  type CredentialGrant struct {
      ID        string
      ExpiresAt time.Time
  }

  type AdmissionController interface {
      Admit(ctx context.Context, credentialID string, peer netip.Addr) (release func(), err error)
  }

  type PumpLimits struct {
      IdleTimeout          time.Duration
      MaxSessionDuration   time.Duration
      MaxBytesPerDirection int64
  }

  func Pump(ctx context.Context, downstream net.Conn, downstreamReader io.Reader,
      upstream net.Conn, limits PumpLimits) (PumpResult, error)
  ```

- [ ] **Step 1: Write RED QA credential tests**

  Verify missing, malformed, wrong, expired and revoked credentials fail generically. Compare secret hashes in constant time and never return/log raw tokens.

- [ ] **Step 2: Write RED admission tests**

  Assert maximum two concurrent sessions per credential plus conservative per-IP/global limits. Release functions decrement exactly once.

- [ ] **Step 3: Write RED bounded-pump tests**

  Cover buffered bytes after CONNECT, both directions, half-close, 30-second idle, 90-second total duration, 4 MiB each direction, cancellation, joined goroutines and cleared non-pooled buffers.

- [ ] **Step 4: Write RED audit tests**

  Audit contains only protocol version, fixed result codes and coarse duration/byte buckets. It excludes token, IP, authority input, payload, certificates and exact counts.

- [ ] **Step 5: Write RED outer-TLS policy tests**

  Add:

  ```go
  func OuterTLSConfig(cert tls.Certificate) *tls.Config {
      return &tls.Config{
          Certificates: []tls.Certificate{cert},
          MinVersion:   tls.VersionTLS12,
          NextProtos:   []string{"http/1.1"},
      }
  }
  ```

  Test TLS 1.1 rejection and that missing/wrong ALPN never reaches CONNECT parsing. The service is TCP `tls.Server`, not QUIC, and exposes no early-data/0-RTT path.

- [ ] **Step 6: Implement server order**

  ```text
  outer TLS handshake
  → bounded CONNECT parse
  → exact authority/version
  → QA credential verify
  → admission
  → fixed upstream dial
  → 200 Connection Established
  → opaque bounded pump
  → safe audit
  ```

  Errors have no body and reflect no input.

- [ ] **Step 7: Add executable configuration validation**

  `main.go` reads listen address, TLS cert/key paths, QA key file and bounded limit overrides. Missing TLS/credentials abort startup. No default credential and no arbitrary upstream option.

- [ ] **Step 8: Run relay gate**

  ```bash
  cd ws024-relay
  gofmt -w .
  go test ./... -count=1
  go test ./... -race -count=1
  go vet ./...
  go build ./cmd/ws024-relay
  ```

- [ ] **Step 9: Commit**

  ```bash
  git add ws024-relay
  git commit -m "feat: add bounded authenticated ws024 relay"
  ```
---

### Task 10: Add deterministic synthetic double-TLS integration

**Files:**
- Create: `tools/ws024_tunnel_harness.py`
- Create: `scripts/verify-ws024-tunnel.sh`
- Create: `ws024-relay/cmd/ws024-relay-integration/main.go`
- Create: `app/src/test/java/dev/junta/firmamobile/network/SecureTunnelExternalHarnessTest.kt`
- Modify: `ws024-relay/internal/relay/server_test.go`

**Interfaces:**
- `ws024-relay-integration` has `//go:build integration`, injects a loopback fixed dialer and is excluded from the production build.
- JVM test reads exactly:
  ```text
  JFM_TUNNEL_TEST_RELAY_PORT
  JFM_TUNNEL_TEST_OUTER_CA_PEM
  JFM_TUNNEL_TEST_INNER_CA_PEM
  JFM_TUNNEL_TEST_RESULT_FILE
  ```
- Final sanitized JSON:
  ```json
  {"direct":"TCP_BEFORE_HTTP_BYTES","tunnel":"ESTABLISHED",
   "innerTls":"VERIFIED_WS024","httpPosts":1,"relayPayloadVisible":false}
  ```

- [ ] **Step 1: Write a failing shell gate**

  ```bash
  #!/data/data/com.termux/files/usr/bin/bash
  set -euo pipefail
  exec python "$PWD/tools/ws024_tunnel_harness.py"
  ```

- [ ] **Step 2: Generate two independent temporary PKIs**

  With `openssl`, create outer CA/relay SAN `relay.test`, inner CA/upstream SAN `ws024.juntadeandalucia.es`, and wrong inner SAN `evil.example`. Private files are `0600`, never printed and deleted in `finally`.

- [ ] **Step 3: Start synthetic inner TLS upstream**

  Python binds `127.0.0.1:0`, loads only inner cert/key, accepts HTTP/1.1 POST, records method/content type/count only, compares an in-memory random canary and returns synthetic `text/plain` bytes.

- [ ] **Step 4: Start build-tagged Go integration relay**

  ```bash
  (cd ws024-relay && \
    go build -tags=integration -o "$TMP/ws024-relay-integration" \
      ./cmd/ws024-relay-integration)
  ```

  Only this build-tagged command accepts loopback upstream address. It prints one bounded `READY <port>` record, never credential/payload. Production `cmd/ws024-relay` has no upstream override.

- [ ] **Step 5: Implement external JVM runner**

  `SecureTunnelExternalHarnessTest`:

  - skips only if all four variables are absent; partial input fails;
  - trusts outer CA only in relay connector and inner CA only in OkHttp inner TLS;
  - maps `relay.test` to loopback while verifying hostname/SPKI;
  - maps logical `ws024` to the approved synthetic address set used by `TunnelBackedSocket`;
  - injects direct pre-HTTP failure then executes the real tunnel/OkHttp POST;
  - atomically writes sanitized JSON to the result file.

- [ ] **Step 6: Run three isolated scenarios**

  Python invokes exactly:

  ```bash
  ./gradlew testDebugUnitTest \
    --tests dev.junta.firmamobile.network.SecureTunnelExternalHarnessTest \
    --no-daemon --console=plain
  ```

  A. Safe direct failure → tunnel success → exactly one POST.  
  B. Failure after `requestHeadersStart` → zero relay connections and no retry.  
  C. Wrong inner leaf → outer CONNECT occurs, inner TLS rejects, zero HTTP POSTs.

- [ ] **Step 7: Prove relay opacity**

  Scan relay stdout/stderr and audit serialization; random canary, token, exact body length and certificate bytes must be absent. Only then set `relayPayloadVisible=false`.

- [ ] **Step 8: Validate cleanup and JSON**

  Validate exact keys/types/counts, terminate children, confirm no process remains, delete PKI, and print one compact JSON object. Any mismatch fails.

- [ ] **Step 9: Run deterministic gate**

  ```bash
  scripts/verify-ws024-tunnel.sh
  ```
  Expected: exit 0 and exact sanitized JSON.

- [ ] **Step 10: Commit**

  ```bash
  git add tools/ws024_tunnel_harness.py scripts/verify-ws024-tunnel.sh \
          app/src/test/java/dev/junta/firmamobile/network/SecureTunnelExternalHarnessTest.kt \
          ws024-relay/cmd/ws024-relay-integration \
          ws024-relay/internal/relay/server_test.go
  git commit -m "test: verify ws024 tunnel with synthetic double tls"
  ```
---

### Task 11: Run full Android/relay gates and document QA limitations

**Files:**
- Modify: `docs/test-report.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/compatibility/spanish-government-signing-matrix.md`

- [ ] **Step 1: Run complete JVM suites**

  ```bash
  ./gradlew testDebugUnitTest testQaUnitTest --no-daemon
  ```
  Expected: zero failures/errors/skips unless an existing test intentionally skips with documented reason.

- [ ] **Step 2: Run lint and APK builds**

  ```bash
  ./gradlew lintDebug lintQa assembleDebug assembleQa --no-daemon
  ```

- [ ] **Step 3: Verify APK integrity**

  ```bash
  zipalign -c -p -v 4 app/build/outputs/apk/debug/app-debug.apk
  zipalign -c -p -v 4 app/build/outputs/apk/qa/app-qa.apk
  apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
  apksigner verify --verbose --print-certs app/build/outputs/apk/qa/app-qa.apk
  sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/qa/app-qa.apk
  ```

- [ ] **Step 4: Run relay and synthetic gates**

  ```bash
  (cd ws024-relay && go test ./... -count=1 && go test ./... -race -count=1 && go vet ./... && go build ./cmd/ws024-relay)
  scripts/verify-ws024-tunnel.sh
  ```

- [ ] **Step 5: Verify release remains fail-closed**

  Run release policy unit tests. Then invoke `assembleRelease` without secrets only to confirm it stops at `verifyReleaseSigning`; do not bypass or replace the gate.

- [ ] **Step 6: Scan artifacts for forbidden values**

  Search source, generated resources and unpacked QA APK for:

  - QA credential values;
  - `Authorization: Bearer` followed by a real token;
  - private keys;
  - full tri-phase payloads;
  - production tunnel enabled flag.

  Use a known synthetic canary in tests and assert it is absent from APK and logs.

- [ ] **Step 7: Update documentation with exact evidence**

  Record:

  - unit/lint/build counts;
  - relay gate results;
  - APK hashes/signature schemes;
  - that only synthetic E2E is verified;
  - that `junta-ofvirtual` remains `VERIFIED_CONTRACT`/E2E pending;
  - that production credentials and external relay deployment are not implemented;
  - that release is direct-only.

- [ ] **Step 8: Commit**

  ```bash
  git add docs/test-report.md docs/security-roadmap.md \
          docs/compatibility/spanish-government-signing-matrix.md
  git commit -m "docs: record ws024 tunnel qa verification"
  ```

---

### Task 12: Device QA with an external relay and final branch review

**Files:**
- Modify only after evidence: `docs/test-report.md`
- No release status/profile promotion in this task.

**Precondition:** A project-controlled QA relay is deployed outside the blocked network with valid outer TLS, configured SPKI pins and a revocable QA credential injected only into the QA runtime.

- [ ] **Step 1: Install the verified QA APK with data preserved**

  Stage through `/data/local/tmp`, compare SHA-256 before and after staging, run `pm install -r`, and verify installed `base.apk` hash.

- [ ] **Step 2: Run the real Oficina Virtual flow once**

  User manually unlocks the certificate and approves one login signature. Never request or read the certificate password through chat/terminal.

- [ ] **Step 3: Capture only sanitized route evidence**

  Expected sequence:

  ```text
  DIRECT / TCP_BEFORE_HTTP_BYTES / FAILED
  SECURE_TUNNEL / ESTABLISHED
  PRE / SUCCESS
  LOCAL_SIGNATURE / SUCCESS
  POST / SUCCESS
  CALLBACK / SUCCESS
  PORTAL_LOGIN / ACCEPTED
  ```

  No payload, certificate, signature, cookie, token or full URL may be captured.

- [ ] **Step 4: Verify relay privacy**

  Confirm relay logs contain only coarse event fields and cannot decrypt the inner TLS stream. Confirm hosting access logs do not record authorization headers.

- [ ] **Step 5: Record evidence without screenshots containing identity data**

  Store a textual, sanitized report. Do not commit screenshots showing certificate owner or official identifiers.

- [ ] **Step 6: Dispatch final whole-branch review on `gpt-5.6-terra` high**

  Reviewer scope:

  - spec compliance;
  - duplicate POST safety;
  - inner/outer TLS trust boundaries;
  - open-proxy/SSRF resistance;
  - QA/release separation;
  - credential/log leakage;
  - complete Android and Go tests.

- [ ] **Step 7: Resolve final findings and run one fresh full gate**

  Use one fix dispatch, one scoped re-review, then rerun all commands from Task 11.

- [ ] **Step 8: Commit device evidence**

  ```bash
  git add docs/test-report.md
  git commit -m "docs: record ws024 tunnel device qa"
  ```

- [ ] **Step 9: Finish the development branch**

  Invoke `superpowers:finishing-a-development-branch`. Do not push or merge without explicit user authorization.
