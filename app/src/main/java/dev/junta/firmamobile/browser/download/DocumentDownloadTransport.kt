package dev.junta.firmamobile.browser.download

import dev.junta.firmamobile.network.PublicIpAddressPolicy
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal enum class DocumentDownloadProblem { NETWORK, SERVER, REDIRECT, AUTHENTICATION, TOO_LARGE, NOT_DOCUMENT, CANCELLED, STORAGE }
internal class DocumentDownloadException(val problem: DocumentDownloadProblem) : IOException("Document transfer: $problem")
internal class StagedDocument(val file: File, val bytes: Long, val sha256: String) : AutoCloseable {
    override fun close() { file.delete() }
}

/** One newly and explicitly approved GET, not the already-consumed WebView
 * response. No auth negotiation, redirects, retries or private client key. */
internal class DocumentDownloadTransport(
    private val dns: Dns = publicDns(),
    private val clientBuilder: () -> OkHttpClient.Builder = { OkHttpClient.Builder() },
) {
    suspend fun fetch(plan: DocumentDownloadPlan, directory: File, progress: (Long) -> Unit = {}): StagedDocument =
        suspendCancellableCoroutine { continuation ->
            val file = try {
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Staging unavailable")
                File.createTempFile("document-", ".part", directory)
            } catch (_: Exception) {
                continuation.resumeWith(Result.failure(DocumentDownloadException(DocumentDownloadProblem.STORAGE)))
                return@suspendCancellableCoroutine
            }
            val client = try { clientBuilder().proxy(Proxy.NO_PROXY).dns(dns)
                .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
                .addNetworkInterceptor(object : okhttp3.Interceptor {
                    private val sent = AtomicBoolean(false)
                    override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
                        // OkHttp can follow 503 Retry-After:0 even with ordinary
                        // connection retries disabled. Never make that second GET.
                        if (!sent.compareAndSet(false, true)) throw IOException("Document request already sent")
                        return chain.proceed(chain.request())
                    }
                }).build() } catch (_: Exception) {
                    file.delete()
                    continuation.resumeWith(Result.failure(DocumentDownloadException(DocumentDownloadProblem.NETWORK)))
                    return@suspendCancellableCoroutine
                }
            val request = Request.Builder().url(plan.url.toASCIIString()).get()
                .header("User-Agent", plan.userAgent.ifEmpty { "FirmaMobile" })
                .apply { plan.cookie?.let { header("Cookie", it) } }.build()
            val call = client.newCall(request)
            fun cleanup() { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    file.delete(); cleanup()
                    if (continuation.isActive) continuation.resumeWith(Result.failure(DocumentDownloadException(DocumentDownloadProblem.NETWORK)))
                }
                override fun onResponse(call: Call, response: Response) {
                    var staged: StagedDocument? = null
                    var problem = DocumentDownloadProblem.NETWORK
                    try {
                        response.use {
                            when {
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
                            val receipt = body.byteStream().use { input -> file.outputStream().use { output ->
                                BoundedDocumentCopy.copy(input, output, DocumentDownloadPlan.MAX_BYTES, length,
                                    active = { continuation.isActive && !call.isCanceled() }, progress = progress)
                            } }
                            if (!continuation.isActive) throw DocumentDownloadException(DocumentDownloadProblem.CANCELLED)
                            staged = StagedDocument(file, receipt.bytes, receipt.sha256)
                        }
                    } catch (failure: DocumentDownloadException) { problem = failure.problem }
                    catch (_: Exception) { problem = if (file.length() >= DocumentDownloadPlan.MAX_BYTES) DocumentDownloadProblem.TOO_LARGE else DocumentDownloadProblem.NETWORK }
                    finally { cleanup() }
                    val result = staged
                    if (result != null) {
                        if (continuation.isActive) continuation.resume(result, onCancellation = { _, value, _ -> value.close() })
                        else result.close()
                    } else {
                        file.delete()
                        if (continuation.isActive) continuation.resumeWith(Result.failure(DocumentDownloadException(problem)))
                    }
                }
            })
        }

    companion object {
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
