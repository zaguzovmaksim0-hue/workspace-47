package dev.junta.firmamobile.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.security.cert.Certificate
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileHttpTransportTest {
    private val policy = SafeNetworkUrlPolicy()
    private val requestUrl = (
        policy.validateRequest(java.net.URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT)) as NetworkUrlValidation.Allowed
    ).url

    @Test
    fun publicExactEndpointReturnsOneOwnedBoundedBody() {
        val body = "synthetic-response".encodeToByteArray()
        val executor = QueueExecutor(
            RawProfileHttpResponse(
                statusCode = 200,
                contentType = "text/plain;charset=UTF-8",
                location = null,
                body = body,
            ),
        )
        val transport = HttpsProfileHttpTransport(
            dnsResolver = DnsResolver { listOf(InetAddress.getByName("217.12.21.226")) },
            executor = executor,
        )

        val requestBody = "op=pre&cop=sign".encodeToByteArray()
        val request = ProfileHttpRequest(requestUrl, requestBody)
        val result = request.use {
            transport.post(it, ProfileHttpCancellation())
        } as ProfileHttpResult.Success

        result.response.use { response ->
            response.withBody { assertArrayEquals("synthetic-response".encodeToByteArray(), it) }
        }
        assertTrue(body.all { it == 0.toByte() })
        assertTrue(requestBody.all { it == 0.toByte() })
        assertEquals("op=pre&cop=sign", executor.bodies.single().decodeToString())
        assertEquals(1, executor.calls.get())
    }

    @Test
    fun redirectsHtmlAuthAndPrivateDnsFailClosedWithoutFollowingOrExposingBodies() {
        val redirectedBody = "must-clear".encodeToByteArray()
        val redirectExecutor = QueueExecutor(
            RawProfileHttpResponse(
                statusCode = 302,
                contentType = "text/plain",
                location = "https://evil.example/",
                body = redirectedBody,
            ),
        )
        val redirectTransport = HttpsProfileHttpTransport(
            dnsResolver = DnsResolver { listOf(InetAddress.getByName("217.12.21.226")) },
            executor = redirectExecutor,
        )

        assertEquals(
            ProfileHttpFailure.REDIRECT_BLOCKED,
            (post(redirectTransport) as ProfileHttpResult.Failure).code,
        )
        assertTrue(redirectedBody.all { it == 0.toByte() })
        assertEquals(1, redirectExecutor.calls.get())

        val htmlBody = "<html>login</html>".encodeToByteArray()
        val htmlTransport = HttpsProfileHttpTransport(
            dnsResolver = DnsResolver { listOf(InetAddress.getByName("217.12.21.226")) },
            executor = QueueExecutor(
                RawProfileHttpResponse(200, "text/html", null, htmlBody),
            ),
        )
        assertEquals(ProfileHttpFailure.SESSION_EXPIRED, (post(htmlTransport) as ProfileHttpResult.Failure).code)
        assertTrue(htmlBody.all { it == 0.toByte() })

        val privateExecutorCalled = AtomicBoolean(false)
        val privateTransport = HttpsProfileHttpTransport(
            dnsResolver = DnsResolver { listOf(InetAddress.getByName("127.0.0.1")) },
            executor = ProfileHttpExecutor { _, _, _, _, _, _ ->
                privateExecutorCalled.set(true)
                error("must not connect")
            },
        )
        assertEquals(ProfileHttpFailure.PRIVATE_ADDRESS, (post(privateTransport) as ProfileHttpResult.Failure).code)
        assertTrue(!privateExecutorCalled.get())
    }

    @Test
    fun authAndOversizedResponsesMapToClosedCodes() {
        val authTransport = HttpsProfileHttpTransport(
            dnsResolver = DnsResolver { listOf(InetAddress.getByName("217.12.21.226")) },
            executor = QueueExecutor(RawProfileHttpResponse(401, null, null, ByteArray(0))),
        )
        assertEquals(ProfileHttpFailure.SESSION_EXPIRED, (post(authTransport) as ProfileHttpResult.Failure).code)

        val oversizedTransport = HttpsProfileHttpTransport(
            dnsResolver = DnsResolver { listOf(InetAddress.getByName("217.12.21.226")) },
            executor = ProfileHttpExecutor { _, _, _, _, _, _ -> throw ProfileResponseTooLargeException() },
        )
        assertEquals(ProfileHttpFailure.RESPONSE_TOO_LARGE, (post(oversizedTransport) as ProfileHttpResult.Failure).code)

        listOf(null, "application/json", "application/octet-stream").forEach { contentType ->
            val wrongTypeBody = "synthetic".encodeToByteArray()
            val wrongTypeTransport = HttpsProfileHttpTransport(
                dnsResolver = DnsResolver { listOf(InetAddress.getByName("217.12.21.226")) },
                executor = QueueExecutor(RawProfileHttpResponse(200, contentType, null, wrongTypeBody)),
            )
            assertEquals(
                ProfileHttpFailure.CONTENT_TYPE_INVALID,
                (post(wrongTypeTransport) as ProfileHttpResult.Failure).code,
            )
            assertTrue(wrongTypeBody.all { it == 0.toByte() })
        }
    }

    @Test
    fun everyRepresentativeNonGlobalDnsRangeIsRejectedBeforeConnect() {
        val blockedAddresses = listOf(
            "0.1.2.3",
            "10.0.0.1",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.1.1",
            "172.16.0.1",
            "192.0.0.1",
            "192.0.2.1",
            "192.168.0.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "240.0.0.1",
            "2001:db8::1",
            "fc00::1",
        )
        blockedAddresses.forEach { address ->
            val executorCalled = AtomicBoolean(false)
            val transport = HttpsProfileHttpTransport(
                dnsResolver = DnsResolver { listOf(InetAddress.getByName(address)) },
                executor = ProfileHttpExecutor { _, _, _, _, _, _ ->
                    executorCalled.set(true)
                    error("must not connect")
                },
            )
            assertEquals(
                address,
                ProfileHttpFailure.PRIVATE_ADDRESS,
                (post(transport) as ProfileHttpResult.Failure).code,
            )
            assertTrue(!executorCalled.get())
        }
    }

    @Test
    fun productionExecutorUsesQueryFreeBoundedPostAndLeavesTlsAndCookiesAlone() {
        val endpoint = URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT)
        val fake = FakeHttpsConnection(
            endpoint.toURL(),
            responseBody = "synthetic-response".encodeToByteArray(),
        )
        val originalSocketFactory = fake.sslSocketFactory
        val originalHostnameVerifier = fake.hostnameVerifier
        var openedUri: URI? = null
        val executor = UrlConnectionProfileHttpExecutor(
            HttpsConnectionFactory { uri ->
                openedUri = uri
                fake
            },
        )
        val body = "op=pre&cop=sign".encodeToByteArray()

        val response = executor.post(
            endpoint,
            body,
            1_234,
            2_345,
            1_024,
            ProfileHttpCancellation(),
        )

        assertEquals(endpoint, openedUri)
        assertEquals(null, openedUri?.rawQuery)
        assertEquals("POST", fake.requestMethod)
        assertTrue(!fake.instanceFollowRedirects)
        assertTrue(fake.doOutput)
        assertEquals(1_234, fake.connectTimeout)
        assertEquals(2_345, fake.readTimeout)
        assertEquals(body.size, fake.fixedLength)
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", fake.getRequestProperty("Content-Type"))
        assertEquals("text/plain", fake.getRequestProperty("Accept"))
        assertEquals(null, fake.getRequestProperty("Cookie"))
        assertEquals(null, fake.getRequestProperty("Authorization"))
        assertTrue(fake.sslSocketFactory === originalSocketFactory)
        assertTrue(fake.hostnameVerifier === originalHostnameVerifier)
        assertArrayEquals(body, fake.posted.toByteArray())
        assertArrayEquals("synthetic-response".encodeToByteArray(), response.body)
        assertTrue(fake.disconnected)
        response.body.fill(0)

        val oversized = FakeHttpsConnection(
            endpoint.toURL(),
            responseBody = ByteArray(5) { 7 },
        )
        val boundedExecutor = UrlConnectionProfileHttpExecutor(HttpsConnectionFactory { oversized })
        assertThrows(ProfileResponseTooLargeException::class.java) {
            boundedExecutor.post(
                endpoint,
                "op=pre".encodeToByteArray(),
                100,
                100,
                4,
                ProfileHttpCancellation(),
            )
        }
        assertTrue(oversized.disconnected)
    }

    @Test
    fun cancellationDisconnectsConnectionsBlockedInWriteAndRead() {
        listOf(true, false).forEach { blockOnWrite ->
            val endpoint = URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT)
            val connection = BlockingHttpsConnection(endpoint.toURL(), blockOnWrite)
            val cancellation = ProfileHttpCancellation()
            val executor = UrlConnectionProfileHttpExecutor(HttpsConnectionFactory { connection })
            val worker = Thread {
                try {
                    executor.post(
                        endpoint,
                        "op=pre".encodeToByteArray(),
                        10_000,
                        10_000,
                        1_024,
                        cancellation,
                    )
                } catch (_: Exception) {
                    // Expected after disconnecting the blocked call.
                }
            }

            worker.start()
            assertTrue(connection.blockStarted.await(2, TimeUnit.SECONDS))
            cancellation.cancel()
            worker.join(2_000)

            assertTrue(connection.disconnected)
            assertTrue(!worker.isAlive)
        }
    }

    private fun post(transport: ProfileHttpTransport): ProfileHttpResult =
        ProfileHttpRequest(requestUrl, "op=pre".encodeToByteArray()).use {
            transport.post(it, ProfileHttpCancellation())
        }

    private class QueueExecutor(
        vararg responses: RawProfileHttpResponse,
    ) : ProfileHttpExecutor {
        private val responses = ArrayDeque(responses.toList())
        val calls = AtomicInteger()
        val bodies = mutableListOf<ByteArray>()

        override fun post(
            url: java.net.URI,
            body: ByteArray,
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int,
            maxResponseBytes: Int,
            cancellation: ProfileHttpCancellation,
        ): RawProfileHttpResponse {
            calls.incrementAndGet()
            bodies += body.copyOf()
            return responses.removeFirst()
        }
    }

    private class FakeHttpsConnection(
        url: URL,
        private val responseBody: ByteArray,
    ) : HttpsURLConnection(url) {
        val posted = ByteArrayOutputStream()
        var disconnected = false
        var fixedLength = -1

        override fun setFixedLengthStreamingMode(contentLength: Int) {
            fixedLength = contentLength
            super.setFixedLengthStreamingMode(contentLength)
        }

        override fun getOutputStream(): ByteArrayOutputStream = posted

        override fun getInputStream(): ByteArrayInputStream = ByteArrayInputStream(responseBody)

        override fun getResponseCode(): Int = 200

        override fun getContentType(): String = "text/plain;charset=UTF-8"

        override fun getHeaderField(name: String?): String? = when (name) {
            "Set-Cookie" -> "ignored=synthetic"
            else -> null
        }

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getCipherSuite(): String = "TLS_FAKE"

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    private class BlockingHttpsConnection(
        url: URL,
        private val blockOnWrite: Boolean,
    ) : HttpsURLConnection(url) {
        private val release = CountDownLatch(1)
        val blockStarted = CountDownLatch(1)
        @Volatile
        var disconnected = false

        override fun getOutputStream(): OutputStream = if (blockOnWrite) {
            object : OutputStream() {
                override fun write(value: Int) = blockUntilDisconnected()

                override fun write(bytes: ByteArray, offset: Int, length: Int) =
                    blockUntilDisconnected()
            }
        } else {
            ByteArrayOutputStream()
        }

        override fun getInputStream(): InputStream = if (!blockOnWrite) {
            object : InputStream() {
                override fun read(): Int {
                    blockUntilDisconnected()
                    return -1
                }
            }
        } else {
            ByteArrayInputStream(ByteArray(0))
        }

        private fun blockUntilDisconnected() {
            blockStarted.countDown()
            release.await()
            throw IOException("synthetic disconnect")
        }

        override fun getResponseCode(): Int = 200

        override fun getContentType(): String = "text/plain"

        override fun getHeaderField(name: String?): String? = null

        override fun disconnect() {
            disconnected = true
            release.countDown()
        }

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getCipherSuite(): String = "TLS_FAKE"

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
