package dev.junta.firmamobile.browser.download

import dev.junta.firmamobile.browser.PublicBrowserAddress
import dev.junta.firmamobile.network.PublicIpAddressPolicy
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.UnknownHostException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal enum class DocumentDownloadProblem { NETWORK, SERVER, REDIRECT, AUTHENTICATION, TOO_LARGE, NOT_DOCUMENT, CANCELLED, STORAGE }
internal class DocumentDownloadException(val problem: DocumentDownloadProblem) : IOException("Document transfer: $problem")
internal class StagedDocument(val file: File, val bytes: Long, val sha256: String) : AutoCloseable {
    init { synchronized(active) { active.add(file.absolutePath) } }
    override fun close() { abandon(file) }
    companion object {
        private val active = mutableSetOf<String>()
        fun createFile(directory: File): File = synchronized(active) {
            File.createTempFile("document-", ".part", directory).also { active.add(it.absolutePath) }
        }
        fun abandon(file: File) = synchronized(active) {
            file.delete(); active.remove(file.absolutePath)
            Unit
        }
        fun pruneStale(directory: File, nowMillis: Long) = synchronized(active) {
            directory.listFiles()?.filter {
                it.isFile && it.name.startsWith("document-") && it.name.endsWith(".part") &&
                    it.lastModified() < nowMillis - 3_600_000L && it.absolutePath !in active
            }?.forEach { it.delete() }
            Unit
        }
    }
}

/** One newly and explicitly approved request, not the already-consumed WebView
 * response: a GET, or the resubmission of the form the user confirmed. HTTPS
 * redirects are followed hop by hop, each checked like the first address. */
internal class DocumentDownloadTransport(
    private val dns: Dns = publicDns(),
    private val clientBuilder: () -> OkHttpClient.Builder = { OkHttpClient.Builder() },
    private val trustManager: () -> X509TrustManager = ::systemTrustManager,
    /** Every hop must be an address the browser could have opened: public HTTPS on 443. */
    private val admit: (HttpUrl) -> Boolean = { PublicBrowserAddress.parse(it.toString()) != null },
) {
    private class HopTag { val sent = AtomicBoolean(false) }
    private sealed interface Outcome {
        class Done(val staged: StagedDocument) : Outcome
        class Redirect(val location: HttpUrl, val code: Int) : Outcome
    }

    suspend fun fetch(plan: DocumentDownloadPlan, directory: File, progress: (Long) -> Unit = {}): StagedDocument {
        val start = plan.url?.toASCIIString()?.toHttpUrlOrNull() ?: throw DocumentDownloadException(DocumentDownloadProblem.NETWORK)
        val file = try {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Staging unavailable")
            StagedDocument.createFile(directory)
        } catch (_: Exception) { throw DocumentDownloadException(DocumentDownloadProblem.STORAGE) }
        var base: OkHttpClient? = null
        try {
            val client = try { baseClient() } catch (_: Exception) { throw DocumentDownloadException(DocumentDownloadProblem.NETWORK) }
            base = client
            val received = ResponseCookies()
            var url = start
            var post = plan.form != null
            repeat(MAX_REDIRECTS + 1) {
                if (!admit(url)) throw DocumentDownloadException(DocumentDownloadProblem.REDIRECT)
                val request = request(plan, url, post, received)
                when (val outcome = exchange(clientFor(client, plan, url.host), request, plan, file, received, progress)) {
                    is Outcome.Done -> return outcome.staged
                    is Outcome.Redirect -> {
                        // Browsers turn a POST into a GET after 301/302/303; 307/308 keep it.
                        if (outcome.code in 301..303) post = false
                        url = outcome.location
                    }
                }
            }
            throw DocumentDownloadException(DocumentDownloadProblem.REDIRECT)
        } catch (failure: Throwable) {
            StagedDocument.abandon(file)
            if (failure is CancellationException || failure is DocumentDownloadException) throw failure
            throw DocumentDownloadException(DocumentDownloadProblem.NETWORK)
        } finally {
            base?.let { it.connectionPool.evictAll(); it.dispatcher.executorService.shutdown() }
        }
    }

    private fun baseClient(): OkHttpClient = clientBuilder().proxy(Proxy.NO_PROXY).dns(dns)
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
        .addNetworkInterceptor { chain ->
            // OkHttp can follow 503 Retry-After:0 even with ordinary connection
            // retries disabled. Never send any hop of the chain twice.
            val tag = chain.request().tag(HopTag::class.java) ?: throw IOException("Untracked document request")
            if (!tag.sent.compareAndSet(false, true)) throw IOException("Document request already sent")
            chain.proceed(chain.request())
        }.build()

    /** The certificate is offered only to the host of the certificate session. */
    private fun clientFor(base: OkHttpClient, plan: DocumentDownloadPlan, host: String): OkHttpClient {
        val identity = plan.clientIdentity?.takeIf { it.appliesTo(host) } ?: return base
        val keys = try { identity.keyManager() } catch (_: Exception) { null }
            ?: throw DocumentDownloadException(DocumentDownloadProblem.AUTHENTICATION)
        val trust = trustManager()
        val context = SSLContext.getInstance("TLS").apply { init(arrayOf(keys), arrayOf(trust), null) }
        return base.newBuilder().sslSocketFactory(context.socketFactory, trust).build()
    }

    private fun request(plan: DocumentDownloadPlan, url: HttpUrl, post: Boolean, received: ResponseCookies): Request {
        val page = plan.referrer
        val sameOrigin = page.host.equals(url.host, ignoreCase = true)
        val builder = Request.Builder().url(url).tag(HopTag::class.java, HopTag())
            .header("User-Agent", plan.userAgent.ifEmpty { "FirmaMobile" })
            // strict-origin-when-cross-origin, the browser default.
            .header("Referer", if (sameOrigin) page.toASCIIString() else "https://${page.host}/")
        val browser = plan.cookies?.takeIf {
            DocumentDownloadPlan.site(page.host) == DocumentDownloadPlan.site(url.host)
        }?.let { runCatching { it.cookieFor(url.toString()) }.getOrNull() }
        cookieHeader(browser, received.forUrl(url))?.let { builder.header("Cookie", it) }
        val form = plan.form
        if (post && form != null) {
            builder.header("Origin", "https://${page.host}")
            builder.post(form.body().toRequestBody(form.contentType.toMediaType()))
        } else builder.get()
        return builder.build()
    }

    private suspend fun exchange(client: OkHttpClient, request: Request, plan: DocumentDownloadPlan, file: File,
        received: ResponseCookies, progress: (Long) -> Unit): Outcome =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(DocumentDownloadException(DocumentDownloadProblem.NETWORK)))
                }
                override fun onResponse(call: Call, response: Response) {
                    var outcome: Outcome? = null
                    var problem = DocumentDownloadProblem.NETWORK
                    try {
                        response.use {
                            received.save(request.url, it.headers("Set-Cookie"))
                            when {
                                it.code in REDIRECT_CODES -> {
                                    val next = it.header("Location")?.let(request.url::resolve)
                                        ?.takeIf { location -> location.isHttps }
                                        ?: throw DocumentDownloadException(DocumentDownloadProblem.REDIRECT)
                                    outcome = Outcome.Redirect(next.newBuilder().fragment(null).build(), it.code)
                                    return@use
                                }
                                it.code in 300..399 -> throw DocumentDownloadException(DocumentDownloadProblem.REDIRECT)
                                it.code == 401 || it.code == 403 -> throw DocumentDownloadException(DocumentDownloadProblem.AUTHENTICATION)
                                it.code != 200 -> throw DocumentDownloadException(DocumentDownloadProblem.SERVER)
                            }
                            val body = it.body
                            val length = body.contentLength()
                            if (length > DocumentDownloadPlan.MAX_BYTES) throw DocumentDownloadException(DocumentDownloadProblem.TOO_LARGE)
                            // A login/error HTML page is not silently saved as the PDF
                            // that the browser said was being downloaded.
                            val type = body.contentType()?.let { t -> t.type + "/" + t.subtype }
                            if (type == "text/html" && plan.mimeType != "text/html") {
                                throw DocumentDownloadException(DocumentDownloadProblem.NOT_DOCUMENT)
                            }
                            if (!continuation.isActive) throw DocumentDownloadException(DocumentDownloadProblem.CANCELLED)
                            val receipt = body.byteStream().use { input -> file.outputStream().use { output ->
                                BoundedDocumentCopy.copy(input, output, DocumentDownloadPlan.MAX_BYTES, length,
                                    active = { continuation.isActive && !call.isCanceled() }, progress = progress)
                            } }
                            if (!continuation.isActive) throw DocumentDownloadException(DocumentDownloadProblem.CANCELLED)
                            outcome = Outcome.Done(StagedDocument(file, receipt.bytes, receipt.sha256))
                        }
                    } catch (failure: DocumentDownloadException) { problem = failure.problem }
                    catch (_: Exception) { problem = if (file.length() >= DocumentDownloadPlan.MAX_BYTES) DocumentDownloadProblem.TOO_LARGE else DocumentDownloadProblem.NETWORK }
                    val result = outcome
                    when {
                        result == null -> {
                            // The caller may already have abandoned the file on
                            // cancellation; remove anything this callback wrote since.
                            StagedDocument.abandon(file)
                            if (continuation.isActive) continuation.resumeWith(Result.failure(DocumentDownloadException(problem)))
                        }
                        continuation.isActive -> continuation.resume(result, onCancellation = { _, value, _ ->
                            (value as? Outcome.Done)?.staged?.close()
                        })
                        else -> (result as? Outcome.Done)?.staged?.close()
                    }
                }
            })
        }

    /** Cookies set by servers within this one download chain; never written back to the browser. */
    private class ResponseCookies {
        private val cookies = mutableListOf<Cookie>()
        @Synchronized fun save(url: HttpUrl, headers: List<String>) {
            for (cookie in headers.take(64).mapNotNull { Cookie.parse(url, it) }) {
                cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis() && cookies.size < 64) cookies.add(cookie)
            }
        }
        @Synchronized fun forUrl(url: HttpUrl): List<Cookie> =
            cookies.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
    }

    companion object {
        private const val MAX_REDIRECTS = 10
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        /** Browser cookies in their order, including same-named cookies for different paths;
         * a cookie the chain's server just set replaces the browser's cookies of that name. */
        internal fun cookieHeader(browser: String?, received: List<Cookie>): String? {
            val replaced = received.map { it.name }.toSet()
            val pairs = browser?.split(';')?.map { it.trim() }
                ?.filter { it.isNotEmpty() && it.substringBefore('=') !in replaced }.orEmpty() +
                received.map { it.name + "=" + it.value }
            val header = pairs.joinToString("; ")
            return header.takeIf { it.isNotEmpty() && it.length <= 32768 && it.all { c -> c.code in 0x20..0x7e } }
        }

        private fun systemTrustManager(): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
                .trustManagers.filterIsInstance<X509TrustManager>().first()

        private fun publicDns(): Dns = object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !PublicIpAddressPolicy.isPublicRoutable(it) }) {
                    throw UnknownHostException("Public document server required")
                }
                return addresses
            }
        }
    }
}
