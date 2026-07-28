package dev.junta.firmamobile.network

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import javax.net.ssl.SSLServerSocket
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
            outerClientFactory = { _, _ ->
                outerTlsOpened = true
                error("must not open outer TLS") as SSLSocket
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
        val logicalAddress = InetAddress.getByName("203.0.113.40")
        val cancellation = ProfileHttpCancellation()
        var opened = false
        val preCancelled = SecureTunnelSocketFactory(
            relay = relay("relay.example", setOf(unrelatedPin())),
            credentialProvider = TunnelCredentialProvider { QATunnelCredential("synthetic-token".toCharArray()) },
            expectedUpstreamHost = "ws024.juntadeandalucia.es",
            approvedUpstreamAddresses = setOf(logicalAddress),
            cancellation = cancellation,
            outerClientFactory = { _, _ ->
                opened = true
                error("cancelled sockets must not open") as SSLSocket
            },
        ).createSocket()
        cancellation.cancel()
        assertThrows(java.io.IOException::class.java) {
            preCancelled.connect(InetSocketAddress(logicalAddress, 443), 1_000)
        }
        assertFalse(opened)

        val relayCertificate = heldCertificate("relay.example")
        RelayServer(serverCertificates(relayCertificate)).use { relayServer ->
            val registered = CountDownLatch(1)
            val releaseFactory = CountDownLatch(1)
            val outer = AtomicReference<SSLSocket>()
            val racingCancellation = ProfileHttpCancellation()
            val socket = SecureTunnelSocketFactory(
                relay = relay("relay.example", setOf(spkiPin(relayCertificate))),
                credentialProvider = TunnelCredentialProvider { QATunnelCredential("synthetic-token".toCharArray()) },
                expectedUpstreamHost = "ws024.juntadeandalucia.es",
                approvedUpstreamAddresses = setOf(logicalAddress),
                cancellation = racingCancellation,
                outerClientFactory = { relay, timeout ->
                    val value = clientSocket(serverCertificates(relayCertificate), relay.host, relayServer.port, timeout)
                    outer.set(value)
                    registered.countDown()
                    assertTrue(releaseFactory.await(2, TimeUnit.SECONDS))
                    value
                },
            ).createSocket()
            val completed = AtomicBoolean(false)
            val connecting = Thread {
                try {
                    socket.connect(InetSocketAddress(logicalAddress, 443), 1_000)
                } catch (_: java.io.IOException) {
                    completed.set(true)
                }
            }
            connecting.start()
            assertTrue(registered.await(2, TimeUnit.SECONDS))
            racingCancellation.cancel()
            releaseFactory.countDown()
            connecting.join(2_000)
            assertTrue(completed.get())
            assertTrue(checkNotNull(outer.get()).isClosed)
        }

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
        outerClientFactory = { relay, timeout ->
            clientSocket(clientCertificates(relayCertificate), relay.host, relayServer.port, timeout)
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

    private fun clientSocket(
        certificates: HandshakeCertificates,
        hostname: String,
        port: Int,
        timeout: Int,
    ): SSLSocket {
        val tcp = Socket()
        tcp.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), timeout)
        return certificates.sslSocketFactory().createSocket(tcp, hostname, port, true) as SSLSocket
    }

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
        val EXPECTED_CONNECT = (
            "CONNECT ws024.juntadeandalucia.es:443 HTTP/1.1\r\n" +
                "Host: ws024.juntadeandalucia.es:443\r\n" +
                "Authorization: Bearer synthetic-token\r\n" +
                "X-WS024-Tunnel-Version: 1\r\n\r\n"
            ).encodeToByteArray()
    }
}
