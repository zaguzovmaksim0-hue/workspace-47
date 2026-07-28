package dev.junta.firmamobile.network

import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class SecureTunnelExternalHarnessTest {
    @Test
    fun environmentContractAcceptsAllAbsentAndRejectsPartialInput() {
        assertEquals(null, HarnessEnvironment.from(emptyMap()))
        for (name in ENVIRONMENT_NAMES) {
            val partial = mapOf(name to "synthetic")
            assertThrows(IllegalArgumentException::class.java) {
                HarnessEnvironment.from(partial)
            }
        }
    }

    @Test
    fun externalDoubleTlsScenario() {
        val environment = HarnessEnvironment.from(System.getenv()) ?: return
        val requestBytes = Files.readAllBytes(environment.requestFile)
        CredentialSource.from(environment.credentialFile).use { credentialSource ->
            val observations = mutableListOf<TunnelRouteEvent>()
            val requestId = UUID.randomUUID()
            val directFailure = when (environment.scenario) {
                HarnessScenario.AFTER_WRITE -> ProfileHttpFailureDetail(
                    code = ProfileHttpFailure.NETWORK_ERROR,
                    phase = ProfileHttpFailurePhase.HTTP_WRITE_STARTED,
                    httpWriteStarted = true,
                )
                HarnessScenario.SUCCESS,
                HarnessScenario.WRONG_INNER,
                -> ProfileHttpFailureDetail(
                    code = ProfileHttpFailure.NETWORK_ERROR,
                    phase = ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES,
                    httpWriteStarted = false,
                )
            }
            val direct = ProfileHttpTransport { _, _ -> ProfileHttpResult.Failure(directFailure) }
            val tunnel = realTunnelTransport(environment, credentialSource)
            val endpoint = java.net.URI(JuntaOfvirtualTriPhaseAdapter.ENDPOINT)
            val transport = DirectFirstProfileHttpTransport(
                profileId = ProfileId("junta-ofvirtual"),
                endpoint = endpoint,
                policy = SecureTunnelPolicy.QA,
                direct = direct,
                tunnel = tunnel,
                observer = TunnelRouteObserver { observedId, event ->
                    assertEquals(requestId, observedId)
                    observations += event
                },
                monotonicNanos = { 0L },
            )
            val validated = SafeNetworkUrlPolicy(setOf(endpoint)).validateRequest(endpoint)
                as NetworkUrlValidation.Allowed
            val result = withDeterministicRelayHostnameVerifier {
                ProfileHttpRequest(validated.url, requestBytes, requestId).use { request ->
                    transport.post(request, ProfileHttpCancellation())
                }
            }

            val scenarioResult = when (environment.scenario) {
                HarnessScenario.SUCCESS -> {
                    assertEquals(
                        listOf(
                            TunnelRouteStage.DIRECT_FAILED_PRE_HTTP,
                            TunnelRouteStage.TUNNEL_CONNECTING,
                            TunnelRouteStage.TUNNEL_ESTABLISHED,
                        ),
                        observations.map(TunnelRouteEvent::stage),
                    )
                    val response = result as ProfileHttpResult.Success
                    response.response.use { body ->
                        body.withBody { assertEquals(SYNTHETIC_RESPONSE, it.decodeToString()) }
                    }
                    HarnessResult(
                        direct = "TCP_BEFORE_HTTP_BYTES",
                        tunnel = "ESTABLISHED",
                        innerTls = "VERIFIED_WS024",
                        httpPosts = 1,
                    )
                }
                HarnessScenario.AFTER_WRITE -> {
                    assertTrue(observations.isEmpty())
                    assertEquals(
                        ProfileHttpFailure.NETWORK_RESULT_UNCERTAIN,
                        (result as ProfileHttpResult.Failure).code,
                    )
                    HarnessResult(
                        direct = "HTTP_WRITE_STARTED",
                        tunnel = "NOT_ATTEMPTED",
                        innerTls = "NOT_ATTEMPTED",
                        httpPosts = 0,
                    )
                }
                HarnessScenario.WRONG_INNER -> {
                    assertEquals(
                        listOf(
                            TunnelRouteStage.DIRECT_FAILED_PRE_HTTP,
                            TunnelRouteStage.TUNNEL_CONNECTING,
                            TunnelRouteStage.TUNNEL_FAILED,
                        ),
                        observations.map(TunnelRouteEvent::stage),
                    )
                    assertEquals(
                        ProfileHttpFailure.TUNNEL_CONNECT_UNAVAILABLE,
                        (result as ProfileHttpResult.Failure).code,
                    )
                    HarnessResult(
                        direct = "TCP_BEFORE_HTTP_BYTES",
                        tunnel = "FAILED",
                        innerTls = "REJECTED_WRONG_LEAF",
                        httpPosts = 0,
                    )
                }
            }
            writeResultAtomically(environment.resultFile, scenarioResult)
        }
        assertTrue(requestBytes.all { it == 0.toByte() })
    }

    private fun realTunnelTransport(
        environment: HarnessEnvironment,
        credentialSource: CredentialSource,
    ): ProfileHttpTransport {
        val outerCa = readSingleCertificate(environment.outerCaPem)
        val innerCa = readSingleCertificate(environment.innerCaPem)
        val outerTrust = HandshakeCertificates.Builder()
            .addTrustedCertificate(outerCa)
            .build()
        val innerTrust = HandshakeCertificates.Builder()
            .addTrustedCertificate(innerCa)
            .build()
        val logicalAddress = InetAddress.getByName(LOGICAL_APPROVED_ADDRESS)
        val outerFactory = object : SecureTunnelOuterSocketFactory {
            override fun createRawSocket(): Socket = object : Socket() {
                override fun connect(endpoint: SocketAddress?, timeout: Int) {
                    val expected = endpoint as? InetSocketAddress
                        ?: error("Relay endpoint is not an InetSocketAddress")
                    check(expected.hostString == RELAY_HOST && expected.port == environment.relayPort)
                    super.connect(
                        InetSocketAddress(InetAddress.getLoopbackAddress(), environment.relayPort),
                        timeout,
                    )
                }
            }

            override fun createTlsSocket(rawSocket: Socket, relay: SecureTunnelRelay): SSLSocket =
                outerTrust.sslSocketFactory()
                    .createSocket(rawSocket, relay.host, relay.port, true) as SSLSocket
        }
        val socketProvider = TunnelSocketFactoryProvider { host, approved, cancellation ->
            assertEquals(UPSTREAM_HOST, host)
            assertEquals(setOf(logicalAddress), approved)
            SecureTunnelSocketFactory(
                relay = SecureTunnelRelay(
                    host = RELAY_HOST,
                    port = environment.relayPort,
                    spkiPins = setOf(spkiPin(outerCa)),
                ),
                credentialProvider = TunnelCredentialProvider { credentialSource.acquire() },
                expectedUpstreamHost = host,
                approvedUpstreamAddresses = approved,
                cancellation = cancellation,
                outerSocketFactory = outerFactory,
            )
        }
        val executor = OkHttpProfileHttpExecutor(socketProvider) {
            OkHttpClient.Builder()
                .sslSocketFactory(innerTrust.sslSocketFactory(), innerTrust.trustManager)
        }
        val endpoint = java.net.URI(JuntaOfvirtualTriPhaseAdapter.ENDPOINT)
        return HttpsProfileHttpTransport(
            urlPolicy = SafeNetworkUrlPolicy(setOf(endpoint)),
            dnsResolver = DnsResolver { host ->
                check(host == UPSTREAM_HOST)
                listOf(logicalAddress)
            },
            executor = executor,
        )
    }

    private fun <T> withDeterministicRelayHostnameVerifier(block: () -> T): T {
        val previous = HttpsURLConnection.getDefaultHostnameVerifier()
        HttpsURLConnection.setDefaultHostnameVerifier { hostname, session ->
            hostname == RELAY_HOST && session.peerCertificates
                .filterIsInstance<X509Certificate>()
                .firstOrNull()
                ?.subjectAlternativeNames
                ?.any { entry ->
                    entry.size >= 2 && entry[0] == DNS_SUBJECT_ALT_NAME && entry[1] == RELAY_HOST
                } == true
        }
        return try {
            block()
        } finally {
            HttpsURLConnection.setDefaultHostnameVerifier(previous)
        }
    }

    private fun readSingleCertificate(path: Path): X509Certificate = Files.newInputStream(path).use { input ->
        val certificates = CertificateFactory.getInstance("X.509")
            .generateCertificates(input)
            .filterIsInstance<X509Certificate>()
        require(certificates.size == 1) { "Expected exactly one CA certificate" }
        certificates.single()
    }

    private fun spkiPin(certificate: X509Certificate): String = "sha256/" +
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded),
        )

    private fun writeResultAtomically(path: Path, result: HarnessResult) {
        val encoded = result.toJson().encodeToByteArray()
        val temporary = Files.createTempFile(path.parent, ".ws024-result-", ".tmp")
        try {
            FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { channel ->
                var buffer = ByteBuffer.wrap(encoded)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            encoded.fill(0)
            Files.deleteIfExists(temporary)
        }
    }

    private data class HarnessResult(
        val direct: String,
        val tunnel: String,
        val innerTls: String,
        val httpPosts: Int,
    ) {
        fun toJson(): String =
            "{\"direct\":\"$direct\",\"tunnel\":\"$tunnel\",\"innerTls\":\"$innerTls\"," +
                "\"httpPosts\":$httpPosts,\"relayPayloadVisible\":false}"
    }

    private enum class HarnessScenario(val filePrefix: String) {
        SUCCESS("success-"),
        AFTER_WRITE("after-write-"),
        WRONG_INNER("wrong-inner-");

        companion object {
            fun from(path: Path): HarnessScenario {
                val name = path.fileName.toString()
                return entries.singleOrNull { name.startsWith(it.filePrefix) }
                    ?: throw IllegalArgumentException("Unknown harness scenario")
            }
        }
    }

    private data class HarnessEnvironment(
        val relayPort: Int,
        val outerCaPem: Path,
        val innerCaPem: Path,
        val resultFile: Path,
        val requestFile: Path,
        val credentialFile: Path,
        val scenario: HarnessScenario,
    ) {
        companion object {
            fun from(values: Map<String, String>): HarnessEnvironment? {
                val supplied = ENVIRONMENT_NAMES.associateWith { values[it] }
                if (supplied.values.all { it == null }) return null
                require(supplied.values.all { !it.isNullOrEmpty() }) { "Partial harness environment" }
                val relayPort = requireNotNull(supplied[ENV_RELAY_PORT]).toIntOrNull()
                    ?.takeIf { it in 1..65535 }
                    ?: throw IllegalArgumentException("Invalid relay port")
                val outerCa = Path.of(requireNotNull(supplied[ENV_OUTER_CA])).toAbsolutePath().normalize()
                val innerCa = Path.of(requireNotNull(supplied[ENV_INNER_CA])).toAbsolutePath().normalize()
                val result = Path.of(requireNotNull(supplied[ENV_RESULT_FILE])).toAbsolutePath().normalize()
                require(Files.isRegularFile(outerCa) && Files.isRegularFile(innerCa))
                require(Files.isDirectory(result.parent) && !Files.isSymbolicLink(result.parent))
                val request = result.resolveSibling(result.fileName.toString() + REQUEST_SUFFIX)
                val credential = result.resolveSibling(result.fileName.toString() + CREDENTIAL_SUFFIX)
                require(Files.isRegularFile(request) && Files.isRegularFile(credential))
                return HarnessEnvironment(
                    relayPort = relayPort,
                    outerCaPem = outerCa,
                    innerCaPem = innerCa,
                    resultFile = result,
                    requestFile = request,
                    credentialFile = credential,
                    scenario = HarnessScenario.from(result),
                )
            }
        }
    }

    private class CredentialSource private constructor(
        private var owned: CharArray?,
    ) : AutoCloseable {
        private val acquired = AtomicBoolean(false)

        @Synchronized
        fun acquire(): QATunnelCredential? {
            if (!acquired.compareAndSet(false, true)) return null
            val value = owned ?: return null
            owned = null
            return QATunnelCredential(value)
        }

        @Synchronized
        override fun close() {
            owned?.fill('\u0000')
            owned = null
        }

        companion object {
            fun from(path: Path): CredentialSource {
                val raw = Files.readAllBytes(path)
                try {
                    require(raw.isNotEmpty() && raw.size <= 512)
                    val chars = CharArray(raw.size) { index ->
                        val value = raw[index].toInt() and 0xff
                        require(value in '!'.code..'~'.code)
                        value.toChar()
                    }
                    return CredentialSource(chars)
                } finally {
                    raw.fill(0)
                }
            }
        }
    }

    private companion object {
        const val ENV_RELAY_PORT = "JFM_TUNNEL_TEST_RELAY_PORT"
        const val ENV_OUTER_CA = "JFM_TUNNEL_TEST_OUTER_CA_PEM"
        const val ENV_INNER_CA = "JFM_TUNNEL_TEST_INNER_CA_PEM"
        const val ENV_RESULT_FILE = "JFM_TUNNEL_TEST_RESULT_FILE"
        val ENVIRONMENT_NAMES = listOf(ENV_RELAY_PORT, ENV_OUTER_CA, ENV_INNER_CA, ENV_RESULT_FILE)
        const val REQUEST_SUFFIX = ".request"
        const val CREDENTIAL_SUFFIX = ".credential"
        const val RELAY_HOST = "relay.test"
        const val UPSTREAM_HOST = "ws024.juntadeandalucia.es"
        const val LOGICAL_APPROVED_ADDRESS = "8.8.8.8"
        const val SYNTHETIC_RESPONSE = "synthetic-triphase-ok"
        const val DNS_SUBJECT_ALT_NAME = 2
    }
}
