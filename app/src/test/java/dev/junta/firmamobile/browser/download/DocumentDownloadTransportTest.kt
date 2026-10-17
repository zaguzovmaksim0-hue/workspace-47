package dev.junta.firmamobile.browser.download

import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.net.InetAddress
import java.time.Instant
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class DocumentDownloadTransportTest {
    @Test fun sameOriginSessionGetIsExactAndDoesNotCarryAuthorization() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").body("%PDF-synthetic").build())
        val plan = f.plan("/download?token=synthetic%2Fvalue", cookies = CookieLookup { "sid=synthetic" })
        val result = runBlocking { f.transport.fetch(plan, f.directory) }
        result.use {
            assertEquals("%PDF-synthetic", it.file.readText()); assertEquals(14L, it.bytes)
            assertEquals(64, it.sha256.length)
        }
        assertFalse(result.file.exists())
        val request = checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("GET", request.method); assertEquals("sid=synthetic", request.headers["Cookie"])
        assertNull(request.headers["Authorization"]); assertNull(request.headers["Origin"])
        assertEquals("https://download.example/", request.headers["Referer"])
        assertEquals("/download?token=synthetic%2Fvalue", request.target)
        assertEquals(1, f.server.requestCount)
    }
    @Test fun httpsRedirectIsFollowedWithFreshCookiesForEachHop() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/final.pdf")
            .addHeader("Set-Cookie", "hop=chain; Path=/").build())
        f.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").body("%PDF-final").build())
        val asked = mutableListOf<String>()
        val plan = f.plan(cookies = CookieLookup { url -> asked += url; "sid=synthetic" })
        runBlocking { f.transport.fetch(plan, f.directory) }.use { assertEquals("%PDF-final", it.file.readText()) }
        val first = checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
        val second = checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/download", first.target); assertEquals("/final.pdf", second.target)
        assertEquals("sid=synthetic; hop=chain", second.headers["Cookie"])
        assertEquals(2, asked.size); assertTrue(asked[1].endsWith("/final.pdf"))
    }
    @Test fun sameNamedBrowserCookiesForDifferentPathsAreAllSent() {
        val url = "https://download.example/a/b".toHttpUrl()
        assertEquals("sid=path; sid=root; x=1", DocumentDownloadTransport.cookieHeader("sid=path; sid=root; x=1", emptyList()))
        val fresh = checkNotNull(okhttp3.Cookie.parse(url, "sid=new"))
        assertEquals("x=1; sid=new", DocumentDownloadTransport.cookieHeader("sid=path; sid=root; x=1", listOf(fresh)))
    }
    @Test fun redirectToPlainHttpIsRefused() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://download.example/document").build())
        val error = assertThrows(DocumentDownloadException::class.java) { runBlocking { f.transport.fetch(f.plan(cookies = CookieLookup { "sid=synthetic" }), f.directory) } }
        assertEquals(DocumentDownloadProblem.REDIRECT, error.problem)
        assertEquals(1, f.server.requestCount); assertTrue(f.directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun endlessRedirectsStop() = fixture { f ->
        repeat(12) { f.server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/again$it").build()) }
        val error = assertThrows(DocumentDownloadException::class.java) { runBlocking { f.transport.fetch(f.plan(), f.directory) } }
        assertEquals(DocumentDownloadProblem.REDIRECT, error.problem)
        assertEquals(11, f.server.requestCount); assertTrue(f.directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun anotherSiteDoesNotReceiveThePageCookies() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").body("%PDF").build())
        val plan = f.plan(page = "https://portal.example/page", cookies = CookieLookup { "sid=synthetic" })
        runBlocking { f.transport.fetch(plan, f.directory) }.close()
        val request = checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
        assertNull(request.headers["Cookie"]); assertEquals("https://portal.example/", request.headers["Referer"])
    }
    @Test fun confirmedFormIsResubmittedOnceWithItsOriginAndBody() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(303).addHeader("Location", "/justificante.pdf").build())
        f.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").body("%PDF-form").build())
        val form = checkNotNull(FormSubmission.encode(null, "UTF-8", listOf(FormEntry.Text("javax.faces.ViewState", "1:2"), FormEntry.Text("nombre", "Peña López"))))
        val plan = f.plan(form = form, cookies = CookieLookup { "JSESSIONID=synthetic" })
        runBlocking { f.transport.fetch(plan, f.directory) }.use { assertEquals("%PDF-form", it.file.readText()) }
        val post = checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("POST", post.method); assertEquals("https://download.example", post.headers["Origin"])
        assertEquals("application/x-www-form-urlencoded", post.headers["Content-Type"])
        assertEquals("javax.faces.ViewState=1%3A2&nombre=Pe%C3%B1a+L%C3%B3pez", post.body?.utf8())
        assertEquals("JSESSIONID=synthetic", post.headers["Cookie"])
        val get = checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("GET", get.method); assertEquals("/justificante.pdf", get.target)
        assertEquals(2, f.server.requestCount)
    }
    @Test fun temporaryRedirectKeepsTheFormPost() = fixture { f ->
        f.server.enqueue(MockResponse.Builder().code(307).addHeader("Location", "/other").build())
        f.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").body("%PDF").build())
        val plan = f.plan(form = checkNotNull(FormSubmission.encode(null, null, listOf(FormEntry.Text("a", "b")))))
        runBlocking { f.transport.fetch(plan, f.directory) }.close()
        checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
        val second = checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("POST", second.method); assertEquals("a=b", second.body?.utf8())
    }
    @Test fun certificateSessionPresentsTheClientCertificateToItsHost() {
        val client = HeldCertificate.Builder().commonName("Synthetic Holder").build()
        mtlsFixture(client) { f ->
            f.server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").body("%PDF-mtls").build())
            val identity = ClientTlsIdentity("download.example") { identity(client) }
            runBlocking { f.transport.fetch(f.plan(identity = identity), f.directory) }.use { assertEquals("%PDF-mtls", it.file.readText()) }
            val request = checkNotNull(f.server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("CN=Synthetic Holder", (request.handshake!!.peerCertificates.first() as java.security.cert.X509Certificate).subjectX500Principal.name)
        }
    }
    @Test fun lockedCertificateSessionFailsInsteadOfSendingWithoutIt() {
        val client = HeldCertificate.Builder().commonName("Synthetic Holder").build()
        mtlsFixture(client) { f ->
            val e = assertThrows(DocumentDownloadException::class.java) {
                runBlocking { f.transport.fetch(f.plan(identity = ClientTlsIdentity("download.example") { null }), f.directory) }
            }
            assertEquals(DocumentDownloadProblem.AUTHENTICATION, e.problem); assertEquals(0, f.server.requestCount)
        }
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
        assertThrows(DocumentDownloadException::class.java) { runBlocking { transport.fetch(f.plan(cookies = CookieLookup { "sid=synthetic" }), f.directory) } }
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

    private class Fixture(val server: MockWebServer, val trusted: HandshakeCertificates) {
        val directory = Files.createTempDirectory("document-test-").toFile()
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                check(hostname == "download.example")
                return listOf(InetAddress.getByName("127.0.0.1"))
            }
        }
        val transport = DocumentDownloadTransport(dns = dns, clientBuilder = {
            OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
        }, trustManager = { trusted.trustManager },
            // The fixture server cannot listen on 443; only the port is relaxed.
            admit = { it.isHttps && it.host == "download.example" })
        fun plan(path: String = "/download", cookies: CookieLookup? = null, page: String = "https://download.example/",
            form: FormSubmission? = null, identity: ClientTlsIdentity? = null): DocumentDownloadPlan {
            // Public entry admission supports 443 only. The test changes only
            // the private transport fixture URL, not public caller policy.
            val result = checkNotNull(DocumentDownloadPlan.create(page, "https://download.example$path", "test.pdf", "application/pdf", -1, "Fixture", cookies, form, identity))
            val url = java.net.URI("https://download.example:${server.port}$path")
            val field = DocumentDownloadPlan::class.java.getDeclaredField("url"); field.isAccessible = true; field.set(result, url)
            return result
        }
    }
    private fun identity(held: HeldCertificate) = UnlockedIdentity(held.keyPair.private, held.certificate, listOf(held.certificate),
        CertificateSummary("Synthetic Holder", "Synthetic Holder", Instant.EPOCH, Instant.now().plusSeconds(3600)))

    private fun mtlsFixture(client: HeldCertificate, block: (Fixture) -> Unit) {
        val certificate = HeldCertificate.Builder().commonName("download.example").addSubjectAlternativeName("download.example").build()
        val served = HandshakeCertificates.Builder().heldCertificate(certificate).addTrustedCertificate(client.certificate).build()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer(); server.useHttps(served.sslSocketFactory()); server.requireClientAuth()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        val fixture = Fixture(server, trusted)
        try { block(fixture) } finally { server.close(); fixture.directory.deleteRecursively() }
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
