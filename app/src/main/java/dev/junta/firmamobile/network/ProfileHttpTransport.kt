package dev.junta.firmamobile.network

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.util.IdentityHashMap
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

class ProfileHttpRequest internal constructor(
    val url: ValidatedNetworkUrl,
    body: ByteArray,
) : Closeable {
    private var ownedBody: ByteArray? = body

    init {
        require(body.isNotEmpty() && body.size <= MAX_REQUEST_BYTES)
    }

    @Synchronized
    internal fun <T> withBody(block: (ByteArray) -> T): T =
        block(checkNotNull(ownedBody) { "HTTP request body is closed" })

    @Synchronized
    override fun close() {
        ownedBody?.fill(0)
        ownedBody = null
    }

    internal companion object {
        const val MAX_REQUEST_BYTES = 4 * 1024 * 1024
    }
}

class ProfileHttpResponse internal constructor(
    body: ByteArray,
) : Closeable {
    private var ownedBody: ByteArray? = body

    @Synchronized
    internal fun <T> withBody(block: (ByteArray) -> T): T =
        block(checkNotNull(ownedBody) { "HTTP response body is closed" })

    @Synchronized
    override fun close() {
        ownedBody?.fill(0)
        ownedBody = null
    }
}

enum class ProfileHttpFailure {
    INVALID_ENDPOINT,
    PRIVATE_ADDRESS,
    REDIRECT_BLOCKED,
    SESSION_EXPIRED,
    CONTENT_TYPE_INVALID,
    RESPONSE_TOO_LARGE,
    HTTP_ERROR,
    NETWORK_ERROR,
}

sealed interface ProfileHttpResult {
    data class Success(val response: ProfileHttpResponse) : ProfileHttpResult

    @Suppress("EXPOSED_PARAMETER_TYPE", "EXPOSED_PROPERTY_TYPE")
    data class Failure(val detail: ProfileHttpFailureDetail) : ProfileHttpResult {
        val code: ProfileHttpFailure
            get() = detail.code

        constructor(code: ProfileHttpFailure) : this(
            ProfileHttpFailureDetail(
                code = code,
                phase = ProfileHttpFailurePhase.UNKNOWN,
                httpWriteStarted = true,
            ),
        )
    }
}

fun interface ProfileHttpTransport {
    fun post(
        request: ProfileHttpRequest,
        cancellation: ProfileHttpCancellation,
    ): ProfileHttpResult
}

class ProfileHttpCancellation internal constructor() {
    private val cancelled = AtomicBoolean(false)
    private val attempt = AtomicReference<ProfileHttpCallPhaseTracker?>(null)
    private val cancelActions = IdentityHashMap<() -> Unit, Unit>()

    fun register(action: () -> Unit): Closeable {
        val invokeNow = synchronized(this) {
            if (cancelled.get()) {
                true
            } else {
                cancelActions[action] = Unit
                false
            }
        }
        if (invokeNow) action()
        return Closeable {
            synchronized(this) {
                cancelActions.remove(action)
            }
        }
    }

    fun cancel() {
        val actions = synchronized(this) {
            if (!cancelled.compareAndSet(false, true)) {
                emptyList()
            } else {
                cancelActions.keys.toList().also { cancelActions.clear() }
            }
        }
        actions.forEach { action -> action() }
    }

    fun isCancelled(): Boolean = cancelled.get()

    internal fun beginAttempt(tracker: ProfileHttpCallPhaseTracker): Boolean = synchronized(this) {
        if (cancelled.get()) return@synchronized false
        attempt.set(tracker)
        tracker.beginDns()
        true
    }

    internal fun snapshotFailure(code: ProfileHttpFailure): ProfileHttpFailureDetail =
        attempt.get()?.failure(code) ?: ProfileHttpFailureDetail(
            code = code,
            phase = ProfileHttpFailurePhase.UNKNOWN,
            httpWriteStarted = true,
        )
}

internal fun interface DnsResolver {
    fun resolve(host: String): List<InetAddress>
}

internal data class RawProfileHttpResponse(
    val statusCode: Int,
    val contentType: String?,
    val location: String?,
    val body: ByteArray,
)

internal fun interface ProfileHttpExecutor {
    fun post(
        url: URI,
        resolvedAddresses: List<InetAddress>,
        body: ByteArray,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        maxResponseBytes: Int,
        cancellation: ProfileHttpCancellation,
        tracker: ProfileHttpCallPhaseTracker,
    ): RawProfileHttpResponse
}

internal class ProfileResponseTooLargeException : Exception()

class HttpsProfileHttpTransport internal constructor(
    private val urlPolicy: SafeNetworkUrlPolicy = SafeNetworkUrlPolicy(),
    private val dnsResolver: DnsResolver = DnsResolver { host ->
        InetAddress.getAllByName(host).toList()
    },
    private val tunnelSocketFactoryProvider: TunnelSocketFactoryProvider? = null,
    private val executor: ProfileHttpExecutor = OkHttpProfileHttpExecutor(tunnelSocketFactoryProvider),
    private val dnsTimeoutMillis: Long = DNS_TIMEOUT_MILLIS,
) : ProfileHttpTransport {
    init {
        require(dnsTimeoutMillis > 0)
    }

    override fun post(
        request: ProfileHttpRequest,
        cancellation: ProfileHttpCancellation,
    ): ProfileHttpResult {
        val validated = urlPolicy.validateRequest(request.url.uri)
        if (validated !is NetworkUrlValidation.Allowed) {
            return ProfileHttpResult.Failure(cancellation.snapshotFailure(ProfileHttpFailure.INVALID_ENDPOINT))
        }
        val tracker = ProfileHttpCallPhaseTracker()
        if (!cancellation.beginAttempt(tracker)) {
            return ProfileHttpResult.Failure(cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR))
        }
        val addresses = resolveWithDeadline(request.url.uri.host, cancellation)
        if (addresses == null) {
            return ProfileHttpResult.Failure(tracker.dnsFailure(ProfileHttpFailure.NETWORK_ERROR))
        }
        val approvedAddresses = addresses.filter { it.isPublicAddress() }
        if (approvedAddresses.isEmpty()) {
            return ProfileHttpResult.Failure(tracker.dnsFailure(ProfileHttpFailure.PRIVATE_ADDRESS))
        }

        val bodyCopy = try {
            request.withBody { it.copyOf() }
        } catch (_: Exception) {
            return ProfileHttpResult.Failure(tracker.failure(ProfileHttpFailure.NETWORK_ERROR))
        }
        val raw = try {
            executor.post(
                url = request.url.uri,
                resolvedAddresses = approvedAddresses,
                body = bodyCopy,
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS,
                readTimeoutMillis = READ_TIMEOUT_MILLIS,
                maxResponseBytes = MAX_RESPONSE_BYTES,
                cancellation = cancellation,
                tracker = tracker,
            )
        } catch (_: ProfileResponseTooLargeException) {
            tracker.responseHeadersObserved()
            return ProfileHttpResult.Failure(tracker.failure(ProfileHttpFailure.RESPONSE_TOO_LARGE))
        } catch (_: Exception) {
            return ProfileHttpResult.Failure(tracker.failure(ProfileHttpFailure.NETWORK_ERROR))
        } finally {
            bodyCopy.fill(0)
        }

        fun fail(code: ProfileHttpFailure): ProfileHttpResult.Failure {
            raw.body.fill(0)
            tracker.responseHeadersObserved()
            return ProfileHttpResult.Failure(tracker.failure(code))
        }

        return when {
            raw.statusCode in 300..399 || raw.location != null ->
                fail(ProfileHttpFailure.REDIRECT_BLOCKED)
            raw.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                raw.statusCode == HttpURLConnection.HTTP_FORBIDDEN ->
                fail(ProfileHttpFailure.SESSION_EXPIRED)
            raw.statusCode != HttpURLConnection.HTTP_OK ->
                fail(ProfileHttpFailure.HTTP_ERROR)
            raw.body.size > MAX_RESPONSE_BYTES ->
                fail(ProfileHttpFailure.RESPONSE_TOO_LARGE)
            raw.contentType?.substringBefore(';')?.trim()?.lowercase() == TEXT_HTML ||
                raw.body.looksLikeHtml() ->
                fail(ProfileHttpFailure.SESSION_EXPIRED)
            !raw.contentType.isAcceptedProtocolType() ->
                fail(ProfileHttpFailure.CONTENT_TYPE_INVALID)
            else -> ProfileHttpResult.Success(ProfileHttpResponse(raw.body))
        }
    }

    private fun InetAddress.isPublicAddress(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress ||
            isSiteLocalAddress || isMulticastAddress
        ) {
            return false
        }
        val raw = address
        if (raw.size == 4 && !raw.isGlobalIpv4()) return false
        if (this is Inet6Address) {
            // Fail closed until the full IANA IPv6 special-purpose registry is modeled.
            return false
        }
        return true
    }

    private fun resolveWithDeadline(
        host: String,
        cancellation: ProfileHttpCancellation,
    ): List<InetAddress>? {
        val future = try {
            DNS_EXECUTOR.submit(Callable { dnsResolver.resolve(host) })
        } catch (_: RejectedExecutionException) {
            return null
        }
        return cancellation.register { future.cancel(true) }.use {
            try {
                future.get(dnsTimeoutMillis, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                future.cancel(true)
                null
            } catch (_: CancellationException) {
                null
            } catch (_: ExecutionException) {
                null
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                future.cancel(true)
                null
            }
        }
    }

    private fun ByteArray.isGlobalIpv4(): Boolean {
        val first = this[0].toInt() and 0xff
        val second = this[1].toInt() and 0xff
        val third = this[2].toInt() and 0xff
        return when {
            first == 0 || first == 10 || first == 127 || first >= 224 -> false
            first == 100 && second in 64..127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 168 -> false
            first == 192 && second == 0 && third == 0 -> false
            first == 192 && second == 0 && third == 2 -> false
            first == 192 && second == 88 && third == 99 -> false
            first == 198 && second in 18..19 -> false
            first == 198 && second == 51 && third == 100 -> false
            first == 203 && second == 0 && third == 113 -> false
            else -> true
        }
    }

    private fun ByteArray.looksLikeHtml(): Boolean {
        var start = 0
        while (start < size && this[start].toInt().toChar().isWhitespace()) start++
        return matchesAsciiIgnoreCase(start, "<html") ||
            matchesAsciiIgnoreCase(start, "<!doctype html")
    }

    private fun ByteArray.matchesAsciiIgnoreCase(start: Int, expected: String): Boolean {
        if (start + expected.length > size) return false
        return expected.indices.all { index ->
            this[start + index].toInt().toChar().lowercaseChar() == expected[index].lowercaseChar()
        }
    }

    private fun String?.isAcceptedProtocolType(): Boolean {
        if (this == null) return false
        return substringBefore(';').trim().equals(TEXT_PLAIN, ignoreCase = true)
    }

    internal companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val DNS_TIMEOUT_MILLIS = 5_000L
        private const val TEXT_HTML = "text/html"
        private const val TEXT_PLAIN = "text/plain"
        private val DNS_EXECUTOR = ThreadPoolExecutor(
            0,
            2,
            30,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            { task -> Thread(task, "profile-dns").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        ).apply { allowCoreThreadTimeOut(true) }
    }
}

internal class OkHttpProfileHttpExecutor(
    private val tunnelSocketFactoryProvider: TunnelSocketFactoryProvider? = null,
    private val clientBuilderFactory: () -> OkHttpClient.Builder = { OkHttpClient.Builder() },
) : ProfileHttpExecutor {
    override fun post(
        url: URI,
        resolvedAddresses: List<InetAddress>,
        body: ByteArray,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        maxResponseBytes: Int,
        cancellation: ProfileHttpCancellation,
        tracker: ProfileHttpCallPhaseTracker,
    ): RawProfileHttpResponse {
        val approvedAddresses = resolvedAddresses.distinct()
        if (approvedAddresses.isEmpty()) throw UnknownHostException("No approved address")
        val client = buildClient(
            expectedHost = url.host,
            approvedAddresses = approvedAddresses,
            connectTimeoutMillis = connectTimeoutMillis,
            readTimeoutMillis = readTimeoutMillis,
            tracker = tracker,
            cancellation = cancellation,
        )
        val request = buildRequest(url, body)
        val call = client.newCall(request)
        return cancellation.register(call::cancel).use {
            call.execute().use { response ->
                RawProfileHttpResponse(
                    statusCode = response.code,
                    contentType = response.header("Content-Type"),
                    location = response.header("Location"),
                    body = if (response.code == HttpURLConnection.HTTP_OK) {
                        response.body.byteStream().use { it.readBounded(maxResponseBytes) }
                    } else {
                        ByteArray(0)
                    },
                )
            }
        }
    }

    internal fun buildRequest(url: URI, body: ByteArray): Request = Request.Builder()
        .url(url.toASCIIString())
        .header("Accept", "text/plain")
        .header("Origin", "https://${url.host}")
        .header("Cache-Control", "no-store")
        .post(OneShotFormRequestBody(body))
        .build()

    internal fun buildClient(
        expectedHost: String,
        approvedAddresses: List<InetAddress>,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        tracker: ProfileHttpCallPhaseTracker,
        cancellation: ProfileHttpCancellation? = null,
    ): OkHttpClient {
        val approved = approvedAddresses.distinct()
        require(expectedHost.isNotBlank() && approved.isNotEmpty())
        val builder = clientBuilderFactory()
            .dns(
                Dns { hostname ->
                    if (hostname != expectedHost) {
                        throw UnknownHostException("Hostname is outside the endpoint contract")
                    }
                    approved
                },
            )
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE)
            .cache(null)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
            .protocols(listOf(Protocol.HTTP_1_1))
            .eventListenerFactory(EventListener.Factory { tracker })
            .connectTimeout(connectTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout((connectTimeoutMillis + readTimeoutMillis).toLong(), TimeUnit.MILLISECONDS)
            .addNetworkInterceptor { chain ->
                val connectedAddress = chain.connection()?.route()?.socketAddress?.address
                if (connectedAddress !in approved) {
                    throw IOException("Connected address is outside the approved DNS set")
                }
                chain.proceed(chain.request())
            }
        tunnelSocketFactoryProvider?.let { provider ->
            builder.socketFactory(
                provider.create(
                    expectedUpstreamHost = expectedHost,
                    approvedUpstreamAddresses = approved.toSet(),
                    cancellation = requireNotNull(cancellation),
                ),
            )
        }
        return builder.build()
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ClearingByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        return try {
            var total = 0
            while (true) {
                val read = read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) throw ProfileResponseTooLargeException()
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } finally {
            buffer.fill(0)
            output.clear()
        }
    }

    private class ClearingByteArrayOutputStream : ByteArrayOutputStream() {
        fun clear() {
            buf.fill(0)
            reset()
        }
    }

    private class OneShotFormRequestBody(
        private val bytes: ByteArray,
    ) : RequestBody() {
        override fun contentType() = FORM_CONTENT_TYPE

        override fun contentLength(): Long = bytes.size.toLong()

        override fun isOneShot(): Boolean = true

        override fun writeTo(sink: BufferedSink) {
            sink.write(bytes)
        }
    }

    private companion object {
        val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
    }
}
