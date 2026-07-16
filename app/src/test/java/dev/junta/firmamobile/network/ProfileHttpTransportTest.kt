package dev.junta.firmamobile.network

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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
        assertEquals(listOf(InetAddress.getByName("217.12.21.226")), executor.resolvedAddresses.single())
        assertEquals(1, executor.calls.get())
    }

    @Test
    fun transportUsesTheExactEndpointSetInjectedByTheActiveProfile() {
        val secondEndpoint = URI(
            "https://tramita.unizar.es/afirma-server-triphase-signer-2.7.3/SignatureService",
        )
        val secondPolicy = SafeNetworkUrlPolicy(setOf(secondEndpoint))
        val secondUrl = (secondPolicy.validateRequest(secondEndpoint) as NetworkUrlValidation.Allowed).url
        val executor = QueueExecutor(RawProfileHttpResponse(200, "text/plain", null, "ok".encodeToByteArray()))
        val transport = HttpsProfileHttpTransport(
            urlPolicy = secondPolicy,
            dnsResolver = DnsResolver { listOf(InetAddress.getByName("93.184.216.34")) },
            executor = executor,
        )

        val allowed = ProfileHttpRequest(secondUrl, "op=pre".encodeToByteArray()).use {
            transport.post(it, ProfileHttpCancellation())
        }

        (allowed as ProfileHttpResult.Success).response.close()
        assertEquals(1, executor.calls.get())

        var dnsCalled = false
        val defaultTransport = HttpsProfileHttpTransport(
            dnsResolver = DnsResolver {
                dnsCalled = true
                emptyList()
            },
            executor = executor,
        )
        val blocked = ProfileHttpRequest(secondUrl, "op=pre".encodeToByteArray()).use {
            defaultTransport.post(it, ProfileHttpCancellation())
        }
        assertEquals(ProfileHttpFailure.INVALID_ENDPOINT, (blocked as ProfileHttpResult.Failure).code)
        assertTrue(!dnsCalled)
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
            executor = ProfileHttpExecutor { _, _, _, _, _, _, _ ->
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
            executor = ProfileHttpExecutor { _, _, _, _, _, _, _ -> throw ProfileResponseTooLargeException() },
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
            "192.88.99.2",
            "192.168.0.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "240.0.0.1",
            "2001:db8::1",
            "fc00::1",
            "64:ff9b:1::7f00:1",
            "100::1",
            "2001:2::1",
            "2001:10::1",
            "2606:4700:4700::1111",
        )
        blockedAddresses.forEach { address ->
            val executorCalled = AtomicBoolean(false)
            val transport = HttpsProfileHttpTransport(
                dnsResolver = DnsResolver { listOf(InetAddress.getByName(address)) },
                executor = ProfileHttpExecutor { _, _, _, _, _, _, _ ->
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
    fun dnsResolutionIsBoundedAndCancellationReturnsWithoutCallingHttp() {
        val timeoutStarted = CountDownLatch(1)
        val timeoutInterrupted = CountDownLatch(1)
        val executorCalled = AtomicBoolean(false)
        val timeoutTransport = HttpsProfileHttpTransport(
            dnsResolver = DnsResolver {
                timeoutStarted.countDown()
                try {
                    Thread.sleep(10_000)
                } catch (_: InterruptedException) {
                    timeoutInterrupted.countDown()
                }
                emptyList()
            },
            executor = ProfileHttpExecutor { _, _, _, _, _, _, _ ->
                executorCalled.set(true)
                error("HTTP must not start")
            },
            dnsTimeoutMillis = 100,
        )

        val timedOut = post(timeoutTransport)

        assertTrue(timeoutStarted.await(1, TimeUnit.SECONDS))
        assertEquals(ProfileHttpFailure.NETWORK_ERROR, (timedOut as ProfileHttpResult.Failure).code)
        assertTrue(timeoutInterrupted.await(1, TimeUnit.SECONDS))
        assertTrue(!executorCalled.get())

        val cancelStarted = CountDownLatch(1)
        val cancelInterrupted = CountDownLatch(1)
        val cancellation = ProfileHttpCancellation()
        val cancelledResult = AtomicReference<ProfileHttpResult>()
        val cancelTransport = HttpsProfileHttpTransport(
            dnsResolver = DnsResolver {
                cancelStarted.countDown()
                try {
                    Thread.sleep(10_000)
                } catch (_: InterruptedException) {
                    cancelInterrupted.countDown()
                }
                emptyList()
            },
            executor = ProfileHttpExecutor { _, _, _, _, _, _, _ -> error("HTTP must not start") },
            dnsTimeoutMillis = 10_000,
        )
        val worker = Thread {
            cancelledResult.set(
                ProfileHttpRequest(requestUrl, "op=pre".encodeToByteArray()).use {
                    cancelTransport.post(it, cancellation)
                },
            )
        }
        worker.start()
        assertTrue(cancelStarted.await(1, TimeUnit.SECONDS))
        cancellation.cancel()
        worker.join(1_000)

        assertTrue(!worker.isAlive)
        assertTrue(cancelInterrupted.await(1, TimeUnit.SECONDS))
        assertEquals(
            ProfileHttpFailure.NETWORK_ERROR,
            (cancelledResult.get() as ProfileHttpResult.Failure).code,
        )
    }

    @Test
    fun dnsResolverSaturationFailsClosedWithoutGrowingTheWorkerPool() {
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val cancellations = List(2) { ProfileHttpCancellation() }
        val workers = cancellations.map { cancellation ->
            Thread {
                val transport = HttpsProfileHttpTransport(
                    dnsResolver = DnsResolver {
                        started.countDown()
                        while (release.count > 0) {
                            try {
                                release.await()
                            } catch (_: InterruptedException) {
                                // Simulate a platform resolver that ignores interruption.
                            }
                        }
                        emptyList()
                    },
                    executor = ProfileHttpExecutor { _, _, _, _, _, _, _ ->
                        error("HTTP must not start")
                    },
                    dnsTimeoutMillis = 10_000,
                )
                ProfileHttpRequest(requestUrl, "op=pre".encodeToByteArray()).use {
                    transport.post(it, cancellation)
                }
            }.apply { start() }
        }

        try {
            assertTrue(started.await(1, TimeUnit.SECONDS))
            val saturated = HttpsProfileHttpTransport(
                dnsResolver = DnsResolver { error("A third resolver task must be rejected") },
                executor = ProfileHttpExecutor { _, _, _, _, _, _, _ -> error("HTTP must not start") },
                dnsTimeoutMillis = 10_000,
            )

            assertEquals(
                ProfileHttpFailure.NETWORK_ERROR,
                (post(saturated) as ProfileHttpResult.Failure).code,
            )
        } finally {
            cancellations.forEach(ProfileHttpCancellation::cancel)
            release.countDown()
            workers.forEach { it.join(1_000) }
        }
        assertTrue(workers.none(Thread::isAlive))
    }

    @Test
    fun productionClientUsesOnlyApprovedDnsWithoutRedirectsCookiesAuthRetriesOrProxy() {
        val approved = listOf(
            InetAddress.getByName("217.12.21.226"),
            InetAddress.getByName("93.184.216.34"),
        )
        val client = OkHttpProfileHttpExecutor().buildClient(
            expectedHost = "ws024.juntadeandalucia.es",
            approvedAddresses = approved,
            connectTimeoutMillis = 1_234,
            readTimeoutMillis = 2_345,
        )

        assertEquals(approved, client.dns.lookup("ws024.juntadeandalucia.es"))
        assertThrows(IOException::class.java) { client.dns.lookup("evil.example") }
        assertTrue(!client.followRedirects)
        assertTrue(!client.followSslRedirects)
        assertTrue(!client.retryOnConnectionFailure)
        assertTrue(client.cookieJar === CookieJar.NO_COOKIES)
        assertTrue(client.authenticator === Authenticator.NONE)
        assertTrue(client.proxyAuthenticator === Authenticator.NONE)
        assertTrue(client.proxy === Proxy.NO_PROXY)
        assertEquals(null, client.cache)
        assertEquals(1, client.networkInterceptors.size)
        assertEquals(1_234, client.connectTimeoutMillis)
        assertEquals(2_345, client.readTimeoutMillis)
        assertEquals(2_345, client.writeTimeoutMillis)

        val body = "op=pre&cop=sign".encodeToByteArray()
        val request = OkHttpProfileHttpExecutor().buildRequest(
            URI(SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT),
            body,
        )
        val wireBody = Buffer()
        val requestBody = checkNotNull(request.body)
        requestBody.writeTo(wireBody)
        assertTrue(requestBody.isOneShot())
        assertEquals("POST", request.method)
        assertEquals("text/plain", request.header("Accept"))
        assertEquals("https://ws024.juntadeandalucia.es", request.header("Origin"))
        assertEquals("no-store", request.header("Cache-Control"))
        assertEquals(null, request.header("Cookie"))
        assertEquals(null, request.header("Authorization"))
        assertEquals(
            "application/x-www-form-urlencoded; charset=UTF-8",
            requestBody.contentType().toString(),
        )
        assertArrayEquals(body, wireBody.readByteArray())
    }

    @Test
    fun realTlsExchangeUsesApprovedRouteSniAndNeverReplaysOneShotPostOn503() {
        val certificate = HeldCertificate.Builder()
            .commonName("portal.example")
            .addSubjectAlternativeName("portal.example")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        val server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory())
        val loopback = InetAddress.getByName("127.0.0.1")
        server.start(loopback, 0)
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(503)
                    .addHeader("Content-Type", "text/plain")
                    .addHeader("Retry-After", "0")
                    .body("do-not-retry")
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/plain")
                    .body("unexpected-retry")
                    .build(),
            )
            val executor = OkHttpProfileHttpExecutor {
                okhttp3.OkHttpClient.Builder().sslSocketFactory(
                    clientCertificates.sslSocketFactory(),
                    clientCertificates.trustManager,
                )
            }
            val body = "op=pre&cop=sign".encodeToByteArray()
            val response = executor.post(
                url = URI("https://portal.example:${server.port}/SignatureService"),
                resolvedAddresses = listOf(loopback),
                body = body,
                connectTimeoutMillis = 2_000,
                readTimeoutMillis = 2_000,
                maxResponseBytes = 1_024,
                cancellation = ProfileHttpCancellation(),
            )

            assertEquals(503, response.statusCode)
            val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("POST", request.method)
            // HTTP/2 carries the authority as :authority instead of a Host header.
            assertEquals("portal.example", request.url.host)
            assertTrue("portal.example" in request.handshakeServerNames)
            assertArrayEquals(body, checkNotNull(request.body).toByteArray())
            assertEquals(null, server.takeRequest(250, TimeUnit.MILLISECONDS))
            response.body.fill(0)
        } finally {
            server.close()
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
        val resolvedAddresses = mutableListOf<List<InetAddress>>()

        override fun post(
            url: java.net.URI,
            resolvedAddresses: List<InetAddress>,
            body: ByteArray,
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int,
            maxResponseBytes: Int,
            cancellation: ProfileHttpCancellation,
        ): RawProfileHttpResponse {
            calls.incrementAndGet()
            bodies += body.copyOf()
            this.resolvedAddresses += resolvedAddresses.toList()
            return responses.removeFirst()
        }
    }
}
