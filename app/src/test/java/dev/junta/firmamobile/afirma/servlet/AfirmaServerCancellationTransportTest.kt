package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
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

/** Real HTTP/TLS framing, but only a loopback synthetic server and no personal key. */
class AfirmaServerCancellationTransportTest {
    @Test fun actualCancellationPostContainsOnlyTheProtocolFieldsNotSigningMaterial() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(200).body("OK\n").build())
        val invocation = invocation(f.endpoint)
        val operation = NativeAfirmaOperation(invocation, f.transport)
        var authorization = 0
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, runBlocking { operation.notifyCancellation { authorization++ } })
        assertEquals(1, authorization)
        val request = checkNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        val fields = checkNotNull(request.body).utf8().split('&').associate { entry ->
            val pair = entry.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
        assertEquals(mapOf("op" to "put", "v" to "1_0", "id" to "Cancellation123", "dat" to "CANCEL"), fields)
        assertNull(request.headers["Authorization"])
        assertNull(request.headers["Cookie"])
        assertTrue("store.example" in request.handshakeServerNames)
        assertNull(f.server.takeRequest(200, TimeUnit.MILLISECONDS))
        assertThrows(IllegalStateException::class.java) { invocation.payloadCopy() }
        operation.close()
    }

    @Test fun retryAfterZeroCannotCauseASecondCancellationPost() = withServer { f ->
        f.server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").body("retry").build())
        f.server.enqueue(MockResponse.Builder().code(200).body("OK").build())
        val operation = NativeAfirmaOperation(invocation(f.endpoint), f.transport)
        assertEquals(AfirmaDeliveryResult.REJECTED, runBlocking { operation.notifyCancellation {} })
        assertNotNull(f.server.takeRequest(2, TimeUnit.SECONDS))
        assertNull(f.server.takeRequest(300, TimeUnit.MILLISECONDS))
        assertThrows(IllegalStateException::class.java) { runBlocking { operation.notifyCancellation {} } }
        operation.close()
    }

    @Test fun untrustedHttpsCannotReceiveEvenTheCancellationSessionId() = withServer { f ->
        val operation = NativeAfirmaOperation(invocation(f.endpoint), AfirmaServletTransport(dns = f.dns))
        assertEquals(AfirmaDeliveryResult.NOT_SENT, runBlocking { operation.notifyCancellation {} })
        assertNull(f.server.takeRequest(200, TimeUnit.MILLISECONDS)); operation.close()
    }

    private fun invocation(endpoint: URI) = AfirmaServletInvocation(
        AfirmaServletOperation.SIGN, "https://page.example", endpoint, "Cancellation123", "12345678",
        SigningAlgorithm.SHA256_WITH_RSA, true, "DO_NOT_SEND_THE_DOCUMENT".toByteArray(),
    )
    private class Fixture(val server: MockWebServer, trusted: HandshakeCertificates) {
        val endpoint = URI("https://store.example:${server.port}/StorageService")
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                check(hostname == "store.example")
                return listOf(InetAddress.getByName("127.0.0.1"))
            }
        }
        val transport = AfirmaServletTransport(dns = dns, clientBuilder = {
            OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
        })
    }
    private fun withServer(block: (Fixture) -> Unit) {
        val certificate = HeldCertificate.Builder().commonName("store.example").addSubjectAlternativeName("store.example").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer(); server.useHttps(serverCertificates.sslSocketFactory())
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try { block(Fixture(server, trust)) } finally { server.close() }
    }
}
