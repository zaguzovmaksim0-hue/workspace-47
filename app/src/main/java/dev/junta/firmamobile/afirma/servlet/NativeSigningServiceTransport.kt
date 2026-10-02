package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.network.PublicIpAddressPolicy
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/** A phase may have reached its service before a response is lost. Never
 * describe that as a safe retry, and never log request fields or response XML. */
internal class NativeServiceException(val requestStarted: Boolean, val status: Int? = null) : IOException("Signing service phase did not complete")
internal fun interface NativeSigningServiceTransport {
    suspend fun exchange(endpoint: URI, form: Map<String, String>): AfirmaRetrievedBytes
}

internal class NativeHttpsSigningService(
    private val dns: Dns = Dns { hostname ->
        val addresses = InetAddress.getAllByName(hostname).toList()
        if (addresses.isEmpty() || addresses.any { !PublicIpAddressPolicy.isPublicRoutable(it) }) throw UnknownHostException("Non-public signing service")
        addresses
    },
    private val clientBuilder: () -> OkHttpClient.Builder = { OkHttpClient.Builder() },
) : NativeSigningServiceTransport {
    override suspend fun exchange(endpoint: URI, form: Map<String, String>): AfirmaRetrievedBytes {
        require(AfirmaEndpointPolicy.accepts(endpoint) && form.size in 1..32)
        require(form.keys.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")) })
        require(form.values.sumOf { it.length.toLong() } <= 4_194_304L)
        val encoded = FormBody.Builder().apply { form.forEach { (key, value) -> add(key, value) } }.build()
        val sent = AtomicBoolean(false)
        val body = object : RequestBody() {
            override fun contentType() = encoded.contentType()
            override fun contentLength() = encoded.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                if (!sent.compareAndSet(false, true)) throw IOException("Signing phase cannot be replayed")
                encoded.writeTo(sink)
            }
        }
        val request = Request.Builder().url(endpoint.toASCIIString()).post(body).build()
        require(request.url.queryParameterNames.none { name -> form.keys.any { it.equals(name, true) } })
        val client = clientBuilder().proxy(Proxy.NO_PROXY).dns(dns).cookieJar(CookieJar.NO_COOKIES)
            .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS)).build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            fun releaseClient() { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    releaseClient()
                    if (continuation.isActive) continuation.resumeWith(Result.failure(NativeServiceException(sent.get())))
                }
                override fun onResponse(call: Call, response: Response) {
                    var result: AfirmaRetrievedBytes? = null
                    var failure: Exception? = null
                    try {
                        response.use {
                            if (it.code != 200) throw NativeServiceException(sent.get(), it.code)
                            val source = it.body ?: throw NativeServiceException(sent.get())
                            if (source.contentLength() > MAX_RESPONSE) throw NativeServiceException(sent.get())
                            val buffer = java.io.ByteArrayOutputStream()
                            source.byteStream().use { input ->
                                val chunk = ByteArray(8192)
                                try {
                                    while (true) {
                                        val n = input.read(chunk)
                                        if (n == -1) break
                                        if (buffer.size() + n > MAX_RESPONSE) throw NativeServiceException(sent.get())
                                        buffer.write(chunk, 0, n)
                                    }
                                } finally { chunk.fill(0) }
                            }
                            val bytes = buffer.toByteArray()
                            if (bytes.isEmpty() || source.contentLength() >= 0 && bytes.size.toLong() != source.contentLength()) {
                                bytes.fill(0); throw NativeServiceException(sent.get())
                            }
                            result = AfirmaRetrievedBytes(bytes)
                        }
                    } catch (e: Exception) { failure = e }
                    finally { releaseClient() }
                    val owned = result
                    if (owned != null) {
                        continuation.resume(owned, onCancellation = { _, value, _ -> value.close() })
                    } else if (continuation.isActive) continuation.resumeWith(Result.failure(failure ?: NativeServiceException(sent.get())))
                }
            })
        }
    }
    private companion object { const val MAX_RESPONSE = 2_097_152 }
}
