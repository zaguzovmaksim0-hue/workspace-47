package dev.junta.firmamobile.afirma.servlet

import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
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
