package dev.junta.firmamobile.afirma.servlet

import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class NativeSigningServiceTransportTest {
    @Test fun eachPhaseIsAnExplicitPostWithoutBrowserCredentials() = runBlocking<Unit> {
        fixture { server, service, endpoint ->
            server.enqueue(MockResponse.Builder().body("<xml>synthetic</xml>").build())
            val form = linkedMapOf("op" to "pre", "doc" to "one+two/three=", "cert" to "public-certificate")
            service.exchange(endpoint, form).use { assertEquals("<xml>synthetic</xml>", it.take().toString(Charsets.UTF_8)) }
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            val actual = checkNotNull(request.body).utf8().split('&').associate { field ->
                URLDecoder.decode(field.substringBefore('='), "UTF-8") to URLDecoder.decode(field.substringAfter('='), "UTF-8")
            }
            assertEquals(form, actual)
            for (header in listOf("Cookie", "Authorization", "Proxy-Authorization", "Referer")) assertNull(request.headers[header])
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun redirectsAndRetryAfterResponsesNeverReplayAPhase() = runBlocking<Unit> {
        for (code in listOf(302, 401, 503)) fixture { server, service, endpoint ->
            server.enqueue(MockResponse.Builder().code(code).setHeader("Location", endpoint.toASCIIString()).setHeader("Retry-After", "0").body("not-success").build())
            server.enqueue(MockResponse.Builder().body("should-not-be-reached").build())
            try { service.exchange(endpoint, mapOf("op" to "post", "session" to "signed-state")); fail("Expected non-success") }
            catch (error: NativeServiceException) { assertTrue(error.requestStarted); assertEquals(code, error.status) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun ambiguousQueryAndUnsafeEndpointsAreRejectedBeforeNetwork() = runBlocking<Unit> {
        fixture { server, service, endpoint ->
            for (url in listOf(URI(endpoint.toString()+"?op=other"), URI("http://service.example/pre"), URI("https://127.0.0.1/pre"))) {
                try { service.exchange(url, mapOf("op" to "pre")); fail("Unsafe endpoint accepted") }
                catch (_: IllegalArgumentException) { }
            }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun largeEmptyAndTruncatedPhaseResultsAreNotAccepted() = runBlocking<Unit> {
        for (body in listOf("", "x".repeat(2_097_153))) fixture { server, service, endpoint ->
            server.enqueue(MockResponse.Builder().body(body).build())
            try { service.exchange(endpoint, mapOf("op" to "pre")); fail("Invalid response accepted") }
            catch (_: NativeServiceException) { }
            assertEquals(1, server.requestCount)
        }
    }
    private suspend fun fixture(test: suspend (MockWebServer, NativeHttpsSigningService, URI) -> Unit) {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("service.example").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory()); server.start()
            val service = NativeHttpsSigningService(Dns { listOf(InetAddress.getLoopbackAddress()) }) {
                OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            }
            test(server, service, URI("https://service.example:${server.port}/phase"))
        }
    }
}
