package dev.junta.firmamobile.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.HttpsURLConnection
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureTunnelSocketFactoryTest {
    @Test
    fun resolvedUpstreamContractRejectsUnapprovedAddressBeforeOuterTls() {
        val approved = InetAddress.getByName("203.0.113.40")
        var outerTlsOpened = false
        val factory = SecureTunnelSocketFactory(
            relay = SecureTunnelRelay(
                host = "relay.example",
                port = 443,
                spkiPins = setOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
            ),
            credentialProvider = TunnelCredentialProvider { QATunnelCredential("synthetic-token".toCharArray()) },
            expectedUpstreamHost = "ws024.juntadeandalucia.es",
            approvedUpstreamAddresses = setOf(approved),
            cancellation = ProfileHttpCancellation(),
            outerSocketFactory = object : SecureTunnelOuterSocketFactory {
                override fun createRawSocket(): Socket = error("must not open a raw socket")

                override fun createTlsSocket(rawSocket: Socket, relay: SecureTunnelRelay): SSLSocket {
                    outerTlsOpened = true
                    error("must not open outer TLS")
                }
            },
        )

        val socket = factory.createSocket()

        assertThrows(java.io.IOException::class.java) {
            socket.connect(InetSocketAddress(InetAddress.getByName("203.0.113.41"), 443))
        }
        assertThrows(java.io.IOException::class.java) {
            socket.connect(InetSocketAddress.createUnresolved("ws024.juntadeandalucia.es", 443))
        }
        assertThrows(java.io.IOException::class.java) {
            socket.connect(InetSocketAddress(approved, 444))
        }
        assertThrows(java.io.IOException::class.java) {
            socket.connect(null)
        }
        assert(!outerTlsOpened)
    }

    @Test
    fun outerTlsSendsExactConnectThenOnlyCarriesOpaqueInnerTlsBytes() {
        withRelayHostnameVerifier {
            val relayCertificate = heldCertificate("relay.example")
            val relayServer = RelayServer(serverCertificates(relayCertificate))
            val credentialChars = "synthetic-token".toCharArray()
            val logicalAddress = InetAddress.getByName("203.0.113.40")
            val socket = factory(
                relayHost = "relay.example",
                relayServer = relayServer,
                relayCertificate = relayCertificate,
                credentialProvider = TunnelCredentialProvider { QATunnelCredential(credentialChars) },
                approvedAddress = logicalAddress,
            ).createSocket()

            try {
                socket.setSoTimeout(1_000)
                socket.setTcpNoDelay(true)
                socket.setKeepAlive(true)
                socket.connect(InetSocketAddress(logicalAddress, 443), 1_000)
                socket.outputStream.write(byteArrayOf(0x16, 0x03, 0x03))
                socket.outputStream.flush()

                assertTrue(relayServer.await())
                assertArrayEquals(EXPECTED_CONNECT, relayServer.connectRequest.get())
                assertEquals(0x16, relayServer.firstTunnelByte.get())
                assertArrayEquals(CharArray(credentialChars.size), credentialChars)
                assertEquals(logicalAddress, socket.inetAddress)
                assertEquals(443, socket.port)
                assertEquals(InetSocketAddress(logicalAddress, 443), socket.remoteSocketAddress)
                assertTrue(socket.tcpNoDelay)
                assertTrue(socket.keepAlive)
                assertEquals(1_000, socket.soTimeout)
            } finally {
                socket.close()
                relayServer.close()
            }
        }
    }

    @Test
    fun relayHostnameAndPinFailuresSendNoConnectAndBackupPinIsAccepted() {
        withRelayHostnameVerifier {
            val relayCertificate = heldCertificate("relay.example")
            val logicalAddress = InetAddress.getByName("203.0.113.40")

            RelayServer(serverCertificates(relayCertificate)).use { wrongHostServer ->
            val socket = factory(
                relayHost = "wrong.example",
                relayServer = wrongHostServer,
                relayCertificate = relayCertificate,
                approvedAddress = logicalAddress,
            ).createSocket()
            assertThrows(Exception::class.java) {
                socket.connect(InetSocketAddress(logicalAddress, 443), 1_000)
            }
            assertTrue(wrongHostServer.await())
            assertEquals(null, wrongHostServer.connectRequest.get())
            }

            RelayServer(serverCertificates(relayCertificate)).use { wrongPinServer ->
            val socket = factory(
                relayHost = "relay.example",
                relayServer = wrongPinServer,
                relayCertificate = relayCertificate,
                pins = setOf(unrelatedPin()),
                approvedAddress = logicalAddress,
            ).createSocket()
            assertThrows(Exception::class.java) {
                socket.connect(InetSocketAddress(logicalAddress, 443), 1_000)
            }
            assertTrue(wrongPinServer.await())
            assertEquals(null, wrongPinServer.connectRequest.get())
            }

            RelayServer(serverCertificates(relayCertificate)).use { backupPinServer ->
            val socket = factory(
                relayHost = "relay.example",
                relayServer = backupPinServer,
                relayCertificate = relayCertificate,
                pins = setOf(unrelatedPin(), spkiPin(relayCertificate)),
                approvedAddress = logicalAddress,
            ).createSocket()
            socket.connect(InetSocketAddress(logicalAddress, 443), 1_000)
            socket.close()
            assertTrue(backupPinServer.await())
            assertArrayEquals(EXPECTED_CONNECT, backupPinServer.connectRequest.get())
            }
        }
    }

    @Test
    fun cancellationIsLinearizableBeforeAndDuringOuterSocketRegistration() {
        var invoked = false
        val unregisteredCancellation = ProfileHttpCancellation()
        val handle = unregisteredCancellation.register { invoked = true }
        handle.close()
        handle.close()
        unregisteredCancellation.cancel()
        assertFalse(invoked)

        val multiHookCancellation = ProfileHttpCancellation()
        var firstHookInvoked = false
        var secondHookInvoked = false
        multiHookCancellation.register { firstHookInvoked = true }
        multiHookCancellation.register { secondHookInvoked = true }
        multiHookCancellation.cancel()
        assertTrue(firstHookInvoked)
        assertTrue(secondHookInvoked)
    }

    @Test
    fun cancellationBeforeRawConnectClosesTheCreatedSocketWithoutConnecting() {
        val cancellation = ProfileHttpCancellation()
        val raw = FakeRawSocket()
        val tls = FakeTlsSocket()
        val factory = FakeOuterSocketFactory(raw, tls)
        cancellation.cancel()

        val socket = tunnelSocket(cancellation, factory)

        assertThrows(java.io.IOException::class.java) {
            socket.connect(logicalEndpoint(), 1_000)
        }
        assertEquals(1, factory.rawSocketCreations.get())
        assertEquals(0, raw.connectCalls.get())
        assertTrue(raw.closed.get())
        assertEquals(0, factory.tlsSocketCreations.get())
    }

    @Test
    fun cancellationClosesTheExactRawSocketAndInterruptsBlockedConnect() {
        val cancellation = ProfileHttpCancellation()
        val raw = BlockingRawSocket()
        val factory = FakeOuterSocketFactory(raw, FakeTlsSocket())
        val socket = tunnelSocket(cancellation, factory)
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                socket.connect(logicalEndpoint(), 30_000)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }

        worker.start()
        assertTrue(raw.connectEntered.await(1, TimeUnit.SECONDS))
        cancellation.cancel()
        worker.join(1_000)

        assertFalse(worker.isAlive)
        assertTrue(raw.closed.get())
        assertTrue(failure.get() is SocketException)
        assertEquals(0, factory.tlsSocketCreations.get())
    }

    @Test
    fun cancellationClosesTheExactTlsSocketAndInterruptsBlockedHandshake() {
        val cancellation = ProfileHttpCancellation()
        val raw = FakeRawSocket()
        val tls = BlockingHandshakeTlsSocket()
        val socket = tunnelSocket(cancellation, FakeOuterSocketFactory(raw, tls))
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                socket.connect(logicalEndpoint(), 30_000)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }

        worker.start()
        assertTrue(tls.handshakeEntered.await(1, TimeUnit.SECONDS))
        cancellation.cancel()
        worker.join(1_000)

        assertFalse(worker.isAlive)
        assertTrue(tls.closed.get())
        assertTrue(failure.get() is SocketException)
    }

    @Test
    fun cancellationDuringRawToTlsHandoffClosesBothSocketLayers() {
        val cancellation = ProfileHttpCancellation()
        val raw = FakeRawSocket()
        val tls = FakeTlsSocket()
        val tlsCreated = CountDownLatch(1)
        val releaseTlsFactory = CountDownLatch(1)
        val factory = FakeOuterSocketFactory(raw, tls) {
            tlsCreated.countDown()
            assertTrue(releaseTlsFactory.await(1, TimeUnit.SECONDS))
        }
        val socket = tunnelSocket(cancellation, factory)
        val worker = Thread {
            try {
                socket.connect(logicalEndpoint(), 30_000)
            } catch (_: Throwable) {
                // Cancellation is the expected terminal path.
            }
        }

        worker.start()
        assertTrue(tlsCreated.await(1, TimeUnit.SECONDS))
        cancellation.cancel()
        releaseTlsFactory.countDown()
        worker.join(1_000)

        assertFalse(worker.isAlive)
        assertTrue(raw.closed.get())
        assertTrue(tls.closed.get())
        assertEquals(0, tls.handshakeCalls.get())
    }

    @Test
    fun splitOuterFactoryStillConnectsHandshakesVerifiesAndSendsConnectOnce() {
        val relayCertificate = heldCertificate("relay.example")
        val session = fakeSession(relayCertificate.certificate)
        val raw = FakeRawSocket()
        val tls = FakeTlsSocket(
            input = ByteArrayInputStream("HTTP/1.1 200 Connection Established\r\n\r\n".encodeToByteArray()),
            session = session,
        )
        val cancellation = ProfileHttpCancellation()
        val hostnameVerifications = AtomicInteger()
        val previousVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        HttpsURLConnection.setDefaultHostnameVerifier { hostname, observedSession ->
            hostnameVerifications.incrementAndGet()
            hostname == "relay.example" && observedSession === session
        }
        try {
            val socket = tunnelSocket(
                cancellation = cancellation,
                outerSocketFactory = FakeOuterSocketFactory(raw, tls),
                pins = setOf(spkiPin(relayCertificate)),
            )

            socket.connect(logicalEndpoint(), 1_234)

            assertEquals(1, raw.connectCalls.get())
            assertEquals(InetSocketAddress("relay.example", 443), raw.lastEndpoint.get())
            assertEquals(1_234, raw.lastTimeout.get())
            assertEquals(1, tls.handshakeCalls.get())
            assertEquals(1, hostnameVerifications.get())
            assertArrayEquals(EXPECTED_CONNECT, tls.written.toByteArray())
            socket.close()
        } finally {
            HttpsURLConnection.setDefaultHostnameVerifier(previousVerifier)
        }
    }

    @Test
    fun relayPinsAreStrictSha256SpkiValues() {
        assertThrows(IllegalArgumentException::class.java) {
            relay("relay.example", setOf("sha256/not-base64"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            relay("relay.example", setOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="))
        }
    }

    @Test
    fun tunnelSocketFactoryDoesNotReplaceTheSeparateInnerTlsConfiguration() {
        val relayCertificate = heldCertificate("relay.example")
        val upstreamCertificate = heldCertificate("ws024.juntadeandalucia.es")
        val innerCertificates = clientCertificates(upstreamCertificate)
        val innerFactory = innerCertificates.sslSocketFactory()
        val innerVerifier = javax.net.ssl.HostnameVerifier { hostname, _ ->
            hostname == "ws024.juntadeandalucia.es"
        }
        val approved = setOf(InetAddress.getByName("203.0.113.40"))
        var providerCalls = 0
        val executor = OkHttpProfileHttpExecutor(
            TunnelSocketFactoryProvider { expectedHost, addresses, _ ->
                providerCalls++
                assertEquals("ws024.juntadeandalucia.es", expectedHost)
                assertEquals(approved, addresses)
                SocketFactory.getDefault()
            },
        ) {
            OkHttpClient.Builder()
                .sslSocketFactory(innerFactory, innerCertificates.trustManager)
                .hostnameVerifier(innerVerifier)
        }

        val client = executor.buildClient(
            expectedHost = "ws024.juntadeandalucia.es",
            approvedAddresses = approved.toList(),
            connectTimeoutMillis = 1_000,
            readTimeoutMillis = 1_000,
            tracker = ProfileHttpCallPhaseTracker(),
            cancellation = ProfileHttpCancellation(),
        )

        assertEquals(1, providerCalls)
        assertSame(innerFactory, client.sslSocketFactory)
        assertSame(innerVerifier, client.hostnameVerifier)
        // The distinct relay certificate is intentionally never added to the inner trust manager.
        assertFalse(relayCertificate.certificate == upstreamCertificate.certificate)
    }

    private fun tunnelSocket(
        cancellation: ProfileHttpCancellation,
        outerSocketFactory: SecureTunnelOuterSocketFactory,
        pins: Set<String> = setOf(unrelatedPin()),
    ): Socket = SecureTunnelSocketFactory(
        relay = relay("relay.example", pins),
        credentialProvider = TunnelCredentialProvider { QATunnelCredential("synthetic-token".toCharArray()) },
        expectedUpstreamHost = "ws024.juntadeandalucia.es",
        approvedUpstreamAddresses = setOf(logicalAddress()),
        cancellation = cancellation,
        outerSocketFactory = outerSocketFactory,
    ).createSocket()

    private fun logicalAddress(): InetAddress = InetAddress.getByName("203.0.113.40")

    private fun logicalEndpoint(): InetSocketAddress = InetSocketAddress(logicalAddress(), 443)

    private fun factory(
        relayHost: String,
        relayServer: RelayServer,
        relayCertificate: HeldCertificate,
        credentialProvider: TunnelCredentialProvider = TunnelCredentialProvider {
            QATunnelCredential("synthetic-token".toCharArray())
        },
        pins: Set<String> = setOf(spkiPin(relayCertificate)),
        approvedAddress: InetAddress,
    ): SecureTunnelSocketFactory = SecureTunnelSocketFactory(
        relay = relay(relayHost, pins),
        credentialProvider = credentialProvider,
        expectedUpstreamHost = "ws024.juntadeandalucia.es",
        approvedUpstreamAddresses = setOf(approvedAddress),
        cancellation = ProfileHttpCancellation(),
        outerSocketFactory = object : SecureTunnelOuterSocketFactory {
            override fun createRawSocket(): Socket = object : Socket() {
                override fun connect(endpoint: SocketAddress?, timeout: Int) {
                    assertEquals(InetSocketAddress(relayHost, 443), endpoint)
                    super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), relayServer.port), timeout)
                }
            }

            override fun createTlsSocket(rawSocket: Socket, relay: SecureTunnelRelay): SSLSocket =
                clientCertificates(relayCertificate).sslSocketFactory()
                    .createSocket(rawSocket, relay.host, relay.port, true) as SSLSocket
        },
    )

    private fun relay(host: String, pins: Set<String>) = SecureTunnelRelay(host, 443, pins)

    private fun heldCertificate(host: String): HeldCertificate = HeldCertificate.Builder()
        .commonName(host)
        .addSubjectAlternativeName(host)
        .build()

    private fun serverCertificates(certificate: HeldCertificate): HandshakeCertificates = HandshakeCertificates.Builder()
        .heldCertificate(certificate)
        .build()

    private fun clientCertificates(certificate: HeldCertificate): HandshakeCertificates = HandshakeCertificates.Builder()
        .addTrustedCertificate(certificate.certificate)
        .build()

    private fun spkiPin(certificate: HeldCertificate): String = "sha256/" + Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(certificate.certificate.publicKey.encoded),
    )

    private fun unrelatedPin(): String = "sha256/" + Base64.getEncoder().encodeToString(ByteArray(32) { 7 })

    private fun withRelayHostnameVerifier(block: () -> Unit) {
        val previous = HttpsURLConnection.getDefaultHostnameVerifier()
        HttpsURLConnection.setDefaultHostnameVerifier { hostname, _ -> hostname == "relay.example" }
        try {
            block()
        } finally {
            HttpsURLConnection.setDefaultHostnameVerifier(previous)
        }
    }

    private class FakeOuterSocketFactory(
        private val raw: Socket,
        private val tls: SSLSocket,
        private val beforeTlsReturn: () -> Unit = {},
    ) : SecureTunnelOuterSocketFactory {
        val rawSocketCreations = AtomicInteger()
        val tlsSocketCreations = AtomicInteger()

        override fun createRawSocket(): Socket {
            rawSocketCreations.incrementAndGet()
            return raw
        }

        override fun createTlsSocket(rawSocket: Socket, relay: SecureTunnelRelay): SSLSocket {
            assertSame(raw, rawSocket)
            assertEquals("relay.example", relay.host)
            tlsSocketCreations.incrementAndGet()
            beforeTlsReturn()
            return tls
        }
    }

    private open class FakeRawSocket : Socket() {
        val connectCalls = AtomicInteger()
        val lastEndpoint = AtomicReference<SocketAddress?>()
        val lastTimeout = AtomicInteger(-1)
        val closed = AtomicBoolean(false)

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            connectCalls.incrementAndGet()
            lastEndpoint.set(endpoint)
            lastTimeout.set(timeout)
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class BlockingRawSocket : FakeRawSocket() {
        val connectEntered = CountDownLatch(1)
        private val closedWhileConnecting = CountDownLatch(1)

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            super.connect(endpoint, timeout)
            connectEntered.countDown()
            assertTrue(closedWhileConnecting.await(1, TimeUnit.SECONDS))
            throw SocketException("raw socket was closed during connect")
        }

        override fun close() {
            super.close()
            closedWhileConnecting.countDown()
        }
    }

    private open class FakeTlsSocket(
        private val input: InputStream = ByteArrayInputStream(ByteArray(0)),
        private val session: SSLSession = fakeSession(null),
    ) : SSLSocket() {
        val written = ByteArrayOutputStream()
        val handshakeCalls = AtomicInteger()
        val closed = AtomicBoolean(false)
        private var enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")

        override fun getInputStream(): InputStream = input

        override fun getOutputStream(): ByteArrayOutputStream = written

        override fun getSupportedCipherSuites(): Array<String> = emptyArray()

        override fun getEnabledCipherSuites(): Array<String> = emptyArray()

        override fun setEnabledCipherSuites(suites: Array<String>) = Unit

        override fun getSupportedProtocols(): Array<String> = arrayOf("TLSv1.3", "TLSv1.2")

        override fun getEnabledProtocols(): Array<String> = enabledProtocols

        override fun setEnabledProtocols(protocols: Array<String>) {
            enabledProtocols = protocols
        }

        override fun getSession(): SSLSession = session

        override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener) = Unit

        override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener) = Unit

        override fun startHandshake() {
            handshakeCalls.incrementAndGet()
        }

        override fun setUseClientMode(mode: Boolean) = Unit

        override fun getUseClientMode(): Boolean = true

        override fun setNeedClientAuth(need: Boolean) = Unit

        override fun getNeedClientAuth(): Boolean = false

        override fun setWantClientAuth(want: Boolean) = Unit

        override fun getWantClientAuth(): Boolean = false

        override fun setEnableSessionCreation(flag: Boolean) = Unit

        override fun getEnableSessionCreation(): Boolean = true

        override fun close() {
            closed.set(true)
        }
    }

    private class BlockingHandshakeTlsSocket : FakeTlsSocket() {
        val handshakeEntered = CountDownLatch(1)
        private val closedWhileHandshaking = CountDownLatch(1)

        override fun startHandshake() {
            super.startHandshake()
            handshakeEntered.countDown()
            assertTrue(closedWhileHandshaking.await(1, TimeUnit.SECONDS))
            throw SocketException("TLS socket was closed during handshake")
        }

        override fun close() {
            super.close()
            closedWhileHandshaking.countDown()
        }
    }

    private class RelayServer(
        private val certificates: HandshakeCertificates,
    ) : AutoCloseable {
        private val server = certificates.sslContext().serverSocketFactory.createServerSocket(
            0,
            50,
            InetAddress.getLoopbackAddress(),
        ) as SSLServerSocket
        val port: Int
            get() = server.localPort
        val connectRequest = AtomicReference<ByteArray?>()
        val firstTunnelByte = AtomicInteger(-1)
        private val finished = CountDownLatch(1)
        private val worker = Thread {
            try {
                (server.accept() as SSLSocket).use { outer ->
                    outer.soTimeout = 2_000
                    outer.startHandshake()
                    connectRequest.set(readHeader(outer.inputStream))
                    outer.outputStream.write("HTTP/1.1 200 Connection Established\r\n\r\n".encodeToByteArray())
                    outer.outputStream.flush()
                    firstTunnelByte.set(outer.inputStream.read())
                }
            } catch (_: Exception) {
                // Pin and hostname tests intentionally close before CONNECT.
            } finally {
                finished.countDown()
            }
        }.apply { isDaemon = true }

        init {
            worker.start()
        }

        fun await(): Boolean = finished.await(3, TimeUnit.SECONDS)

        override fun close() {
            server.close()
            worker.join(3_000)
        }

        private fun readHeader(input: java.io.InputStream): ByteArray {
            val bytes = ArrayList<Byte>()
            var state = 0
            while (state < 4) {
                val next = input.read()
                if (next < 0) throw java.io.EOFException()
                bytes += next.toByte()
                state = when {
                    state == 0 && next == '\r'.code -> 1
                    state == 1 && next == '\n'.code -> 2
                    state == 2 && next == '\r'.code -> 3
                    state == 3 && next == '\n'.code -> 4
                    next == '\r'.code -> 1
                    else -> 0
                }
            }
            return bytes.toByteArray()
        }
    }

    private companion object {
        fun fakeSession(certificate: X509Certificate?): SSLSession = java.lang.reflect.Proxy.newProxyInstance(
            SSLSession::class.java.classLoader,
            arrayOf(SSLSession::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getPeerCertificates" -> certificate?.let { arrayOf(it) } ?: emptyArray<java.security.cert.Certificate>()
                "toString" -> "FakeSslSession"
                "hashCode" -> System.identityHashCode(method)
                "equals" -> false
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
        } as SSLSession

        val EXPECTED_CONNECT = (
            "CONNECT ws024.juntadeandalucia.es:443 HTTP/1.1\r\n" +
                "Host: ws024.juntadeandalucia.es:443\r\n" +
                "Authorization: Bearer synthetic-token\r\n" +
                "X-WS024-Tunnel-Version: 1\r\n\r\n"
            ).encodeToByteArray()
    }
}
