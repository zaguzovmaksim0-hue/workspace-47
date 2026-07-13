package dev.junta.firmamobile.network

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

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

    data class Failure(val code: ProfileHttpFailure) : ProfileHttpResult
}

fun interface ProfileHttpTransport {
    fun post(
        request: ProfileHttpRequest,
        cancellation: ProfileHttpCancellation,
    ): ProfileHttpResult
}

class ProfileHttpCancellation internal constructor() {
    private val cancelled = AtomicBoolean(false)
    private var cancelAction: (() -> Unit)? = null

    fun register(action: () -> Unit): Closeable {
        val invokeNow = synchronized(this) {
            if (cancelled.get()) {
                true
            } else {
                check(cancelAction == null) { "A network cancellation action is already registered" }
                cancelAction = action
                false
            }
        }
        if (invokeNow) action()
        return Closeable {
            synchronized(this) {
                if (cancelAction === action) cancelAction = null
            }
        }
    }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        val action = synchronized(this) {
            cancelAction.also { cancelAction = null }
        }
        action?.invoke()
    }
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
        body: ByteArray,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        maxResponseBytes: Int,
        cancellation: ProfileHttpCancellation,
    ): RawProfileHttpResponse
}

internal class ProfileResponseTooLargeException : Exception()

class HttpsProfileHttpTransport internal constructor(
    private val urlPolicy: SafeNetworkUrlPolicy = SafeNetworkUrlPolicy(),
    private val dnsResolver: DnsResolver = DnsResolver { host ->
        InetAddress.getAllByName(host).toList()
    },
    private val executor: ProfileHttpExecutor = UrlConnectionProfileHttpExecutor(),
) : ProfileHttpTransport {
    override fun post(
        request: ProfileHttpRequest,
        cancellation: ProfileHttpCancellation,
    ): ProfileHttpResult {
        val validated = urlPolicy.validateRequest(request.url.uri)
        if (validated !is NetworkUrlValidation.Allowed) {
            return ProfileHttpResult.Failure(ProfileHttpFailure.INVALID_ENDPOINT)
        }
        val addresses = try {
            dnsResolver.resolve(request.url.uri.host)
        } catch (_: Exception) {
            return ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
        }
        if (addresses.isEmpty() || addresses.any { !it.isPublicAddress() }) {
            return ProfileHttpResult.Failure(ProfileHttpFailure.PRIVATE_ADDRESS)
        }

        val bodyCopy = try {
            request.withBody { it.copyOf() }
        } catch (_: Exception) {
            return ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
        }
        val raw = try {
                executor.post(
                    url = request.url.uri,
                    body = bodyCopy,
                    connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS,
                    readTimeoutMillis = READ_TIMEOUT_MILLIS,
                    maxResponseBytes = MAX_RESPONSE_BYTES,
                    cancellation = cancellation,
                )
        } catch (_: ProfileResponseTooLargeException) {
            return ProfileHttpResult.Failure(ProfileHttpFailure.RESPONSE_TOO_LARGE)
        } catch (_: Exception) {
            return ProfileHttpResult.Failure(ProfileHttpFailure.NETWORK_ERROR)
        } finally {
            bodyCopy.fill(0)
        }

        fun fail(code: ProfileHttpFailure): ProfileHttpResult.Failure {
            raw.body.fill(0)
            return ProfileHttpResult.Failure(code)
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
            val first = raw.first().toInt() and 0xff
            if (first == 0xfc || first == 0xfd) return false
            if (raw.size == 16 &&
                (0 until 10).all { raw[it] == 0.toByte() } &&
                raw[10] == 0xff.toByte() && raw[11] == 0xff.toByte()
            ) {
                return raw.copyOfRange(12, 16).isGlobalIpv4()
            }
            if (raw.size == 16 &&
                raw[0] == 0x20.toByte() && raw[1] == 0x01.toByte() &&
                raw[2] == 0x0d.toByte() && raw[3] == 0xb8.toByte()
            ) {
                return false
            }
        }
        return true
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
        private const val TEXT_HTML = "text/html"
        private const val TEXT_PLAIN = "text/plain"
    }
}

internal fun interface HttpsConnectionFactory {
    fun open(url: URI): HttpsURLConnection
}

internal class UrlConnectionProfileHttpExecutor(
    private val connectionFactory: HttpsConnectionFactory = HttpsConnectionFactory { url ->
        url.toURL().openConnection() as HttpsURLConnection
    },
) : ProfileHttpExecutor {
    override fun post(
        url: URI,
        body: ByteArray,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        maxResponseBytes: Int,
        cancellation: ProfileHttpCancellation,
    ): RawProfileHttpResponse {
        val connection = connectionFactory.open(url)
        val cancellationRegistration = cancellation.register(connection::disconnect)
        var responseBody: ByteArray? = null
        var bodyTransferred = false
        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.useCaches = false
            connection.defaultUseCaches = false
            connection.doInput = true
            connection.doOutput = true
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Accept", "text/plain")
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8",
            )
            connection.setRequestProperty("Origin", "https://${url.host}")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.outputStream.use { output -> output.write(body) }

            val status = connection.responseCode
            val contentType = connection.contentType
            val location = connection.getHeaderField("Location")
            responseBody = if (status == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { it.readBounded(maxResponseBytes) }
            } else {
                ByteArray(0)
            }
            val response = RawProfileHttpResponse(
                statusCode = status,
                contentType = contentType,
                location = location,
                body = checkNotNull(responseBody),
            )
            bodyTransferred = true
            response
        } finally {
            if (!bodyTransferred) responseBody?.fill(0)
            cancellationRegistration.close()
            connection.disconnect()
        }
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
}
