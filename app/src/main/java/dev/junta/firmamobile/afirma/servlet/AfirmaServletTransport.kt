package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.network.PublicIpAddressPolicy
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okhttp3.Response

internal enum class AfirmaDeliveryResult {
    ACKNOWLEDGED,
    REJECTED,
    NOT_SENT,
    UNCERTAIN,
}

/** One explicitly approved upload; ACKNOWLEDGED means storage replied OK, not
 * that a government service accepted the signature or submitted a document. */
internal fun interface AfirmaResultTransport {
    suspend fun store(endpoint: URI, sessionId: String, result: String): AfirmaDeliveryResult
}

internal object AfirmaEndpointPolicy {
    private val DNS = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+", RegexOption.IGNORE_CASE)
    fun accepts(uri: URI): Boolean = runCatching {
        val text = uri.toASCIIString()
        val host = uri.host ?: return false
        val port = if (uri.port == -1) 443 else uri.port
        uri.scheme.equals("https", true) && !uri.isOpaque &&
            uri.rawUserInfo == null && uri.rawFragment == null &&
            text.length <= 8192 && text.none { it.isISOControl() || it == '\\' } &&
            host.length <= 253 && DNS.matches(host) &&
            host.any { !it.isDigit() && it != '.' } &&
            !host.endsWith(".localhost", true) && !host.endsWith(".local", true) && port in 1..65535
    }.getOrDefault(false)
}

/** Fresh client configuration, no site cookies, authorization or client key.
 * DNS results are validated before the connect; redirects and retries are off.
 * Existing HTTPS query routing is preserved, but the protocol's own form keys
 * must not also be supplied by the endpoint URL (ambiguous duplicate params).
 */
internal class AfirmaServletTransport(
    private val dns: Dns = publicDns(),
    private val clientBuilder: () -> OkHttpClient.Builder = { OkHttpClient.Builder() },
) : AfirmaResultTransport {
    override suspend fun store(endpoint: URI, sessionId: String, result: String): AfirmaDeliveryResult {
        if (!AfirmaEndpointPolicy.accepts(endpoint) || !SESSION_ID.matches(sessionId) ||
            result.isEmpty() || result.length > MAX_RESULT_CHARS || result.any { it.code !in 0x20..0x7e }
        ) return AfirmaDeliveryResult.NOT_SENT
        val encodedForm = FormBody.Builder()
            .add("op", "put").add("v", "1_0").add("id", sessionId).add("dat", result).build()
        val written = AtomicBoolean(false)
        val requestBody = object : RequestBody() {
            override fun contentType() = encodedForm.contentType()
            override fun contentLength() = encodedForm.contentLength()
            // retryOnConnectionFailure=false alone does not prevent 503 +
            // Retry-After:0 follow-ups. A signature upload is strictly one-shot.
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                if (!written.compareAndSet(false, true)) throw IOException("Result upload cannot be repeated")
                encodedForm.writeTo(sink)
            }
        }
        val request = runCatching { Request.Builder().url(endpoint.toASCIIString()).post(requestBody).build() }
            .getOrNull() ?: return AfirmaDeliveryResult.NOT_SENT
        if (request.url.queryParameterNames.any { it.lowercase(java.util.Locale.ROOT) in RESERVED_QUERY_KEYS }) {
            return AfirmaDeliveryResult.NOT_SENT
        }
        val bodyStarted = AtomicBoolean(false)
        val client = clientBuilder()
            .proxy(Proxy.NO_PROXY)
            .dns(dns)
            .cookieJar(CookieJar.NO_COOKIES)
            .authenticator(okhttp3.Authenticator.NONE)
            .proxyAuthenticator(okhttp3.Authenticator.NONE)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
            .eventListener(object : EventListener() {
                override fun requestBodyStart(call: Call) { bodyStarted.set(true) }
            }).build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdown()
                    if (continuation.isActive) continuation.resume(
                        if (bodyStarted.get()) AfirmaDeliveryResult.UNCERTAIN else AfirmaDeliveryResult.NOT_SENT,
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    val value = response.use {
                        try {
                            val body = it.body
                            if (it.code != 200 || body == null) return@use AfirmaDeliveryResult.REJECTED
                            val source = body.source()
                            if (source.request(MAX_ACK_BYTES + 1L)) return@use AfirmaDeliveryResult.REJECTED
                            val bytes = source.readByteArray()
                            try {
                                if (bytes.size <= MAX_ACK_BYTES && bytes.toString(Charsets.US_ASCII).trim() == "OK") {
                                    AfirmaDeliveryResult.ACKNOWLEDGED
                                } else AfirmaDeliveryResult.REJECTED
                            } finally { bytes.fill(0) }
                        } catch (_: IOException) {
                            AfirmaDeliveryResult.UNCERTAIN
                        }
                    }
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdown()
                    if (continuation.isActive) continuation.resume(value)
                }
            })
        }
    }

    private companion object {
        val SESSION_ID = Regex("[A-Za-z0-9_-]{1,128}")
        val RESERVED_QUERY_KEYS = setOf("op", "v", "id", "dat")
        const val MAX_RESULT_CHARS = 6 * 1024 * 1024
        const val MAX_ACK_BYTES = 256
        fun publicDns(): Dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !PublicIpAddressPolicy.isPublicRoutable(it) }) {
                    throw UnknownHostException("Non-public intermediate server")
                }
                return addresses
            }
        }
    }
}
