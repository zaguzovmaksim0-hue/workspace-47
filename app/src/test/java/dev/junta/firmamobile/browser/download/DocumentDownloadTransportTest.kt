package dev.junta.firmamobile.browser.download

import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class DocumentDownloadTransportTest {
    @Test fun sameOriginSessionGetIsExactAndDoesNotCarryAuthorizationOrReferer() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").body("%PDF-synthetic").build())
        val plan = f.plan("/download?token=synthetic%2Fvalue", cookie = "sid=synthetic")
        val result = runBlocking { f.transport.fetch(plan, f.directory) }
        result.use {
            assertEquals("%PDF-synthetic", it.file.readText()); assertEquals(14L, it.bytes)
            assertEquals(64, it.sha256.length)
        }
        assertFalse(result.file.exists())
        val request = checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("GET", request.method); assertEquals("sid=synthetic", request.headers["Cookie"])
        assertNull(request.headers["Authorization"]); assertNull(request.headers["Referer"])
        assertEquals("/download?token=synthetic%2Fvalue", request.target)
        assertEquals(1, f.server.requestCount)
    }
    @Test fun redirectCannotTransferSessionToAnotherRequest() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "https://elsewhere.example/document").build())
        val error = assertThrows(DocumentDownloadException::class.java) { runBlocking { f.transport.fetch(f.plan(cookie = "sid=synthetic"), f.directory) } }
        assertEquals(DocumentDownloadProblem.REDIRECT, error.problem)
        assertEquals(1, f.server.requestCount); assertTrue(f.directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun retryAfterZeroCannotRepeatAOneUseGet() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").body("retry").build())
        f.server.enqueue(MockResponse.Builder().code(200).body("should never be requested").build())
        assertThrows(DocumentDownloadException::class.java) { runBlocking { f.transport.fetch(f.plan(), f.directory) } }
        assertEquals(1, f.server.requestCount)
        assertNotNull(f.server.takeRequest(1, TimeUnit.SECONDS)); assertNull(f.server.takeRequest(200, TimeUnit.MILLISECONDS))
        assertTrue(f.directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun aLoginHtmlPageDoesNotBecomeAFakePdf() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<html>Login</html>").build())
        val e = assertThrows(DocumentDownloadException::class.java) { runBlocking { f.transport.fetch(f.plan(), f.directory) } }
        assertEquals(DocumentDownloadProblem.NOT_DOCUMENT, e.problem)
        assertTrue(f.directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun authenticationFailureDoesNotNegotiateOrSaveTheResponse() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(401).addHeader("WWW-Authenticate", "Basic realm=synthetic").body("login").build())
        val e = assertThrows(DocumentDownloadException::class.java) { runBlocking { f.transport.fetch(f.plan(), f.directory) } }
        assertEquals(DocumentDownloadProblem.AUTHENTICATION, e.problem)
        assertEquals(1, f.server.requestCount); assertTrue(f.directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun untrustedTlsCannotReceiveTheSessionCookie() = fixture { f ->
        val transport = DocumentDownloadTransport(dns = f.dns)
        assertThrows(DocumentDownloadException::class.java) { runBlocking { transport.fetch(f.plan(cookie = "sid=synthetic"), f.directory) } }
        assertEquals(0, f.server.requestCount); assertTrue(f.directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun cancellationDeletesPartialStagingWithoutASecondRequest() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf")
            .body("%PDF-" + "x".repeat(8192)).throttleBody(1024, 1, TimeUnit.SECONDS).build())
        runBlocking {
            val task = async { f.transport.fetch(f.plan(), f.directory) }
            delay(150); task.cancelAndJoin()
        }
        // OkHttp's response callback releases its own IO resources after cancel.
        repeat(40) { if (f.directory.listFiles().orEmpty().isNotEmpty()) Thread.sleep(25) }
        assertTrue(f.directory.listFiles().orEmpty().isEmpty())
        assertTrue(f.server.requestCount <= 1)
    }
    @Test fun staleCleanupNeverDeletesAnActivePreparedFileOrAnUnrelatedFile() {
        val dir = Files.createTempDirectory("stage-retention-").toFile()
        val now = System.currentTimeMillis()
        val live = StagedDocument.createFile(dir).apply { writeText("held"); setLastModified(now - 7_200_000L) }
        val orphan = java.io.File(dir, "document-orphan.part").apply { writeText("old"); setLastModified(now - 7_200_000L) }
        val unrelated = java.io.File(dir, "report.pdf").apply { writeText("keep"); setLastModified(now - 7_200_000L) }
        try {
            StagedDocument.pruneStale(dir, now)
            assertTrue(live.exists()); assertTrue(unrelated.exists()); assertFalse(orphan.exists())
            StagedDocument.abandon(live); assertFalse(live.exists())
        } finally { StagedDocument.abandon(live); dir.deleteRecursively() }
    }

    private class Fixture(val server: MockWebServer, trusted: HandshakeCertificates) {
        val directory = Files.createTempDirectory("document-test-").toFile()
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                check(hostname == "download.example")
                return listOf(InetAddress.getByName("127.0.0.1"))
            }
        }
        val transport = DocumentDownloadTransport(dns = dns, clientBuilder = {
            OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
        })
        fun plan(path: String = "/download", cookie: String? = null): DocumentDownloadPlan {
            // Public entry admission supports 443 only. The test changes only
            // the private transport fixture URL, not public caller policy.
            val result = checkNotNull(DocumentDownloadPlan.create("https://download.example/", "https://download.example$path", "test.pdf", "application/pdf", -1, "Fixture", cookie))
            val url = java.net.URI("https://download.example:${server.port}$path")
            val field = DocumentDownloadPlan::class.java.getDeclaredField("url"); field.isAccessible = true; field.set(result, url)
            return result
        }
    }
    private fun fixture(block: (Fixture) -> Unit) {
        val certificate = HeldCertificate.Builder().commonName("download.example").addSubjectAlternativeName("download.example").build()
        val served = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer(); server.useHttps(served.sslSocketFactory()); server.start(InetAddress.getByName("127.0.0.1"), 0)
        val fixture = Fixture(server, trusted)
        try { block(fixture) } finally { server.close(); fixture.directory.deleteRecursively() }
    }
}
