package dev.junta.firmamobile.afirma.servlet

import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class AfirmaServletTransportTest {
    @Test fun realTlsPostPreservesRoutingAndEscapesEachFormFieldOnce() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(200).body("OK\n").build())
        val result = runBlocking { f.transport.store(f.endpoint("?route=one"), "Session-123", "a+b/c==|0.test-_") }
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, result)
        val request = checkNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("route=one", request.url.encodedQuery)
        assertEquals("store.example", request.url.host)
        assertTrue("store.example" in request.handshakeServerNames)
        assertNull(request.headers["Cookie"])
        assertNull(request.headers["Authorization"])
        val body = checkNotNull(request.body).utf8()
        val fields = body.split('&').associate {
            val pair = it.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
        assertEquals(mapOf("op" to "put", "v" to "1_0", "id" to "Session-123", "dat" to "a+b/c==|0.test-_"), fields)
        assertNull(f.server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test fun anHtmlLoginPageWithStatus200IsNotAnAcknowledgement() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(200).body("<html>Sign in</html>").build())
        assertEquals(AfirmaDeliveryResult.REJECTED, runBlocking { f.transport.store(f.endpoint(), "Session123", "test") })
        assertEquals(1, f.server.requestCount)
    }

    @Test fun redirectsAreNotFollowedAndTheUploadIsNeverRetried() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(302).addHeader("Location", f.endpoint("?new=1")).body("redirect").build())
        f.server.enqueue(MockResponse.Builder().code(200).body("OK").build())
        assertEquals(AfirmaDeliveryResult.REJECTED, runBlocking { f.transport.store(f.endpoint(), "Session123", "test") })
        assertNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
        assertNull(f.server.takeRequest(300, TimeUnit.MILLISECONDS))
    }

    @Test fun retryAfter503DoesNotRepeatANonIdempotentUpload() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").body("retry").build())
        f.server.enqueue(MockResponse.Builder().code(200).body("OK").build())
        assertEquals(AfirmaDeliveryResult.REJECTED, runBlocking { f.transport.store(f.endpoint(), "Session123", "test") })
        assertNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
        assertNull(f.server.takeRequest(300, TimeUnit.MILLISECONDS))
    }

    @Test fun invalidSessionBodyOrAmbiguousEndpointCannotContactTheServer() = withServer { f ->
        val requests = listOf(
            Triple(f.endpoint(), "../escape", "test"),
            Triple(f.endpoint(), "Session123", "line\nsecond"),
            Triple(f.endpoint(), "Session123", ""),
            Triple(f.endpoint("?op=get"), "Session123", "test"),
            Triple(f.endpoint("?%69d=collision"), "Session123", "test"),
            Triple(URI("http://store.example:${f.server.port}/storage"), "Session123", "test"),
            Triple(URI("https://user:password@store.example:${f.server.port}/storage"), "Session123", "test"),
        )
        for ((url, id, body) in requests) {
            assertEquals(AfirmaDeliveryResult.NOT_SENT, runBlocking { f.transport.store(url, id, body) })
        }
        assertEquals(0, f.server.requestCount)
    }

    @Test fun invalidHttpsCertificateNeverReceivesTheResult() = withServer { f ->
        val untrusted = AfirmaServletTransport(dns = f.dns)
        assertEquals(AfirmaDeliveryResult.NOT_SENT, runBlocking { untrusted.store(f.endpoint(), "Session123", "test") })
        assertNull(f.server.takeRequest(300, TimeUnit.MILLISECONDS))
    }

    @Test fun oversizedAcknowledgementsAreBoundedAndNotAccepted() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(200).body("OK" + " ".repeat(300)).build())
        assertEquals(AfirmaDeliveryResult.REJECTED, runBlocking { f.transport.store(f.endpoint(), "Session123", "test") })
    }

    @Test fun publicEndpointPolicyRejectsLocalTargetsAndMetadataButAllowsDnsRouting() {
        assertTrue(AfirmaEndpointPolicy.accepts(URI("https://store.example:8443/StorageService?route=alpha")))
        for (url in listOf("http://store.example/", "https://127.0.0.1/", "https://[::1]/", "https://localhost/",
            "https://box.local/", "https://box.localhost/", "https://user@store.example/", "https://store.example/#fragment")) {
            assertFalse(url, AfirmaEndpointPolicy.accepts(URI(url)))
        }
    }

    @Test fun retrievalUsesTheFileIdInExactlyOnePostWithoutBrowserCookies() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(200).body("0.cipher_fixture\n").build())
        val owned = runBlocking { f.transport.retrieve(f.endpoint("?route=one"), "File-123") }
        val bytes = owned.take(); assertEquals("0.cipher_fixture\n", bytes.toString(Charsets.UTF_8)); bytes.fill(0); owned.close()
        val request = checkNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method); assertEquals("route=one", request.url.encodedQuery)
        assertNull(request.headers["Cookie"]); assertNull(request.headers["Authorization"])
        assertEquals("op=get&v=1_0&id=File-123", checkNotNull(request.body).utf8())
        assertNull(f.server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test fun configurationRedirectsAreNotFollowedAfterAConsumingRead() {
        for (code in listOf(302, 307, 308)) withServer { f ->
            f.server.enqueue(MockResponse.Builder().code(code).addHeader("Location", f.endpoint("?other=1")).body("redirect").build())
            f.server.enqueue(MockResponse.Builder().code(200).body("must not be read").build())
            val error = assertThrows(AfirmaRetrievalException::class.java) { runBlocking { f.transport.retrieve(f.endpoint(), "File-123") } }
            assertEquals(AfirmaRetrievalProblem.UNAVAILABLE, error.problem)
            assertNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
            assertNull(f.server.takeRequest(200, TimeUnit.MILLISECONDS))
        }
    }

    @Test fun retryAfterZeroNeverRepeatsConfigurationPost() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").body("unavailable").build())
        f.server.enqueue(MockResponse.Builder().code(200).body("would consume another copy").build())
        assertThrows(AfirmaRetrievalException::class.java) { runBlocking { f.transport.retrieve(f.endpoint(), "File-123") } }
        assertNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
        assertNull(f.server.takeRequest(300, TimeUnit.MILLISECONDS))
    }

    @Test fun invalidIdsAndDuplicateQueryFieldsFailWithoutContactingRetriever() = withServer { f ->
        for ((url, id) in listOf(f.endpoint() to "../escape", f.endpoint("?id=Another") to "File-123",
            f.endpoint("?%6fp=get") to "File-123", f.endpoint("?v=1_0") to "File-123",
            URI("http://store.example:${f.server.port}/get") to "File-123")) {
            val error = assertThrows(AfirmaRetrievalException::class.java) { runBlocking { f.transport.retrieve(url, id) } }
            assertEquals(AfirmaRetrievalProblem.NOT_SENT, error.problem)
        }
        assertEquals(0, f.server.requestCount)
    }

    @Test fun untrustedRetrieverCannotReceiveFileId() = withServer { f ->
        val untrusted = AfirmaServletTransport(dns = f.dns)
        val error = assertThrows(AfirmaRetrievalException::class.java) { runBlocking { untrusted.retrieve(f.endpoint(), "File-123") } }
        assertEquals(AfirmaRetrievalProblem.NOT_SENT, error.problem)
        assertNull(f.server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test fun knownLengthAndChunkedConfigurationResponsesHaveTheSameHardLimit() {
        for (chunked in listOf(false, true)) withServer { f ->
            val value = "x".repeat(AfirmaDeferredInvocation.MAX_WIRE_BYTES + 1)
            val response = MockResponse.Builder().code(200)
            if (chunked) response.chunkedBody(value, 8192) else response.body(value)
            f.server.enqueue(response.build())
            val error = assertThrows(AfirmaRetrievalException::class.java) { runBlocking { f.transport.retrieve(f.endpoint(), "File-123") } }
            assertEquals(AfirmaRetrievalProblem.TOO_LARGE, error.problem)
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test fun emptyOrExplicitServerErrorIsNotTreatedAsConfiguration() {
        for (body in listOf("", "err-06: fixture expired\n")) withServer { f ->
            f.server.enqueue(MockResponse.Builder().code(200).body(body).build())
            assertThrows(AfirmaRetrievalException::class.java) { runBlocking { f.transport.retrieve(f.endpoint(), "File-123") } }
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test fun cancelWhileRetrieverDelaysBodyDoesNotRetryOrDeliverAResponse() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(200).body("0.delayed").bodyDelay(700, TimeUnit.MILLISECONDS).build())
        runBlocking {
            val task = async(kotlinx.coroutines.Dispatchers.Default) { f.transport.retrieve(f.endpoint(), "File-123") }
            assertNotNull(f.server.takeRequest(3, TimeUnit.SECONDS))
            task.cancel(); task.join(); assertTrue(task.isCancelled)
        }
        assertNull(f.server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    private class Fixture(val server: MockWebServer, certificates: HandshakeCertificates) {
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                check(hostname == "store.example")
                return listOf(InetAddress.getByName("127.0.0.1"))
            }
        }
        val transport = AfirmaServletTransport(dns = dns, clientBuilder = {
            OkHttpClient.Builder().sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
        })
        fun endpoint(query: String = "") = URI("https://store.example:${server.port}/StorageService$query")
    }
    private fun withServer(block: (Fixture) -> Unit) {
        val held = HeldCertificate.Builder().commonName("store.example").addSubjectAlternativeName("store.example").build()
        val serverCert = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientCert = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        val server = MockWebServer(); server.useHttps(serverCert.sslSocketFactory())
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try { block(Fixture(server, clientCert)) } finally { server.close() }
    }
}
