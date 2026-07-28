package dev.junta.firmamobile.network

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.SocketFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal data class SecureTunnelRelay(
    val host: String,
    val port: Int,
    val spkiPins: Set<String>,
) {
    init {
        require(host.isNotBlank()) { "Relay host is required" }
        require(port in 1..65535) { "Relay port is invalid" }
        require(spkiPins.isNotEmpty()) { "At least one relay pin is required" }
        spkiPins.forEach(::validatePin)
    }

    private companion object {
        fun validatePin(pin: String) {
            require(pin.startsWith(SPKI_PREFIX)) { "Relay pin is not a SHA-256 SPKI pin" }
            val encoded = pin.removePrefix(SPKI_PREFIX)
            val decoded = try {
                Base64.getDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Relay pin is not base64")
            }
            require(decoded.size == SHA_256_SIZE && Base64.getEncoder().encodeToString(decoded) == encoded) {
                "Relay pin is not an exact SHA-256 value"
            }
        }

        const val SPKI_PREFIX = "sha256/"
        const val SHA_256_SIZE = 32
    }
}

internal fun interface TunnelCredentialProvider {
    fun acquire(): QATunnelCredential?
}

internal fun interface TunnelSocketFactoryProvider {
    fun create(
        expectedUpstreamHost: String,
        approvedUpstreamAddresses: Set<InetAddress>,
        cancellation: ProfileHttpCancellation,
    ): SocketFactory
}

internal interface SecureTunnelOuterSocketFactory {
    fun createRawSocket(): Socket

    fun createTlsSocket(rawSocket: Socket, relay: SecureTunnelRelay): SSLSocket
}

internal class SecureTunnelSocketFactory(
    relay: SecureTunnelRelay,
    private val credentialProvider: TunnelCredentialProvider,
    expectedUpstreamHost: String,
    approvedUpstreamAddresses: Set<InetAddress>,
    private val cancellation: ProfileHttpCancellation,
    private val outerSocketFactory: SecureTunnelOuterSocketFactory = PlatformOuterSocketFactory,
) : SocketFactory() {
    private val relay = relay.copy(spkiPins = relay.spkiPins.toSet())
    private val expectedUpstreamHost = expectedUpstreamHost.also {
        require(it == SecureTunnelProtocol.FIXED_AUTHORITY.substringBefore(':')) {
            "Only the fixed WS024 upstream host is supported"
        }
    }
    private val approvedUpstreamAddresses = approvedUpstreamAddresses.toSet().also {
        require(it.isNotEmpty()) { "An approved WS024 address is required" }
    }

    override fun createSocket(): Socket = TunnelBackedSocket(
        relay = relay,
        credentialProvider = credentialProvider,
        expectedUpstreamHost = expectedUpstreamHost,
        approvedUpstreamAddresses = approvedUpstreamAddresses,
        cancellation = cancellation,
        outerSocketFactory = outerSocketFactory,
    )

    override fun createSocket(host: String?, port: Int): Socket = unsupported()

    override fun createSocket(host: InetAddress?, port: Int): Socket = unsupported()

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = unsupported()

    override fun createSocket(host: InetAddress?, port: Int, localHost: InetAddress?, localPort: Int): Socket = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException(
        "WS024 tunnel sockets must be connected by OkHttp using a validated InetSocketAddress",
    )

    private object PlatformOuterSocketFactory : SecureTunnelOuterSocketFactory {
        override fun createRawSocket(): Socket = Socket()

        override fun createTlsSocket(rawSocket: Socket, relay: SecureTunnelRelay): SSLSocket =
            (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(rawSocket, relay.host, relay.port, true) as SSLSocket
    }
}

internal class TunnelBackedSocket(
    private val relay: SecureTunnelRelay,
    private val credentialProvider: TunnelCredentialProvider,
    private val expectedUpstreamHost: String,
    approvedUpstreamAddresses: Set<InetAddress>,
    private val cancellation: ProfileHttpCancellation,
    private val outerSocketFactory: SecureTunnelOuterSocketFactory,
) : Socket() {
    private val approvedUpstreamAddresses = approvedUpstreamAddresses.toSet()
    private val lock = Any()

    init {
        require(expectedUpstreamHost == SecureTunnelProtocol.FIXED_AUTHORITY.substringBefore(':')) {
            "Only the fixed WS024 upstream host is supported"
        }
        require(approvedUpstreamAddresses.isNotEmpty()) { "An approved WS024 address is required" }
    }

    @Volatile
    private var outerSocket: SSLSocket? = null
    @Volatile
    private var rawSocket: Socket? = null
    private var cancellationRegistration: Closeable? = null
    private var closed = false
    private var connecting = false
    private var connected = false
    private var inputShutdown = false
    private var outputShutdown = false
    private var logicalAddress: InetAddress? = null
    private var logicalPort = 0
    private var pendingSoTimeout = 0
    private var pendingTcpNoDelay = false
    private var pendingKeepAlive = false

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        if (timeout < 0) throw IllegalArgumentException("timeout < 0")
        val accepted = validateEndpoint(endpoint)
        synchronized(lock) {
            if (closed) throw SocketException("Socket is closed")
            if (connected || connecting || outerSocket != null || rawSocket != null) {
                throw SocketException("Socket is already connected")
            }
            connecting = true
        }
        try {
            val raw = outerSocketFactory.createRawSocket()
            synchronized(lock) {
                if (closed) {
                    closeQuietly(raw)
                    throw SocketException("Socket is closed")
                }
                rawSocket = raw
                val registration = cancellation.register(::close)
                if (closed) {
                    registration.close()
                    throw SocketException("Socket is closed")
                }
                cancellationRegistration = registration
            }

            raw.connect(InetSocketAddress(relay.host, relay.port), timeout)
            val outer = outerSocketFactory.createTlsSocket(raw, relay)
            synchronized(lock) {
                if (closed || cancellation.isCancelled()) {
                    closeQuietly(outer)
                    throw SocketException("Socket is closed")
                }
                outerSocket = outer
                rawSocket = null
                outer.soTimeout = pendingSoTimeout
                outer.tcpNoDelay = pendingTcpNoDelay
                outer.keepAlive = pendingKeepAlive
            }

            configureAndVerifyOuterTls(outer)
            establishConnect(outer)
            synchronized(lock) {
                if (closed || cancellation.isCancelled()) throw SocketException("Socket is closed")
                logicalAddress = accepted.address
                logicalPort = accepted.port
                connected = true
                connecting = false
            }
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    private fun validateEndpoint(endpoint: SocketAddress?): InetSocketAddress {
        val inet = endpoint as? InetSocketAddress ?: throw SocketException("A resolved InetSocketAddress is required")
        if (inet.isUnresolved) throw SocketException("An unresolved upstream address is not allowed")
        if (inet.port != HTTPS_PORT || inet.address !in approvedUpstreamAddresses) {
            throw SocketException("Upstream address is outside the validated WS024 route")
        }
        return inet
    }

    private fun configureAndVerifyOuterTls(outer: SSLSocket) {
        val supported = outer.supportedProtocols.toSet()
        if (TLS_1_2 !in supported) throw SSLPeerUnverifiedException("Relay TLS 1.2 is unavailable")
        outer.enabledProtocols = buildList {
            if (TLS_1_3 in supported) add(TLS_1_3)
            add(TLS_1_2)
        }.toTypedArray()
        outer.startHandshake()
        val session = outer.session
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(relay.host, session)) {
            throw SSLPeerUnverifiedException("Relay hostname verification failed")
        }
        val matchesPinnedSpki = try {
            session.peerCertificates
                .filterIsInstance<X509Certificate>()
                .any { certificate -> pinFor(certificate) in relay.spkiPins }
        } catch (_: Exception) {
            false
        }
        if (!matchesPinnedSpki) throw SSLPeerUnverifiedException("Relay pin verification failed")
    }

    private fun establishConnect(outer: SSLSocket) {
        val credential = credentialProvider.acquire() ?: throw IOException("Tunnel credential is unavailable")
        var requestBytes: ByteArray? = null
        try {
            requestBytes = credential.withValue { authorization ->
                SecureTunnelProtocol.encodeConnect(SecureTunnelConnectRequest(authorization = authorization))
            }
            outer.outputStream.write(requestBytes)
            outer.outputStream.flush()
            if (SecureTunnelProtocol.readResponse(outer.inputStream) !is SecureTunnelConnectResult.Established) {
                throw IOException("Tunnel CONNECT was rejected")
            }
        } finally {
            requestBytes?.fill(0)
            credential.close()
        }
    }

    override fun getInputStream(): InputStream = establishedSocket().inputStream

    override fun getOutputStream(): OutputStream = establishedSocket().outputStream

    override fun setSoTimeout(timeout: Int) {
        if (timeout < 0) throw IllegalArgumentException("timeout < 0")
        synchronized(lock) {
            pendingSoTimeout = timeout
            outerSocket?.soTimeout = timeout
        }
    }

    override fun getSoTimeout(): Int = synchronized(lock) { outerSocket?.soTimeout ?: pendingSoTimeout }

    override fun shutdownInput() {
        synchronized(lock) {
            establishedSocketLocked().shutdownInput()
            inputShutdown = true
        }
    }

    override fun shutdownOutput() {
        synchronized(lock) {
            establishedSocketLocked().shutdownOutput()
            outputShutdown = true
        }
    }

    override fun isConnected(): Boolean = synchronized(lock) { connected }

    override fun isClosed(): Boolean = synchronized(lock) { closed }

    override fun isInputShutdown(): Boolean = synchronized(lock) { inputShutdown }

    override fun isOutputShutdown(): Boolean = synchronized(lock) { outputShutdown }

    override fun getInetAddress(): InetAddress? = synchronized(lock) { logicalAddress }

    override fun getRemoteSocketAddress(): SocketAddress? = synchronized(lock) {
        logicalAddress?.let { InetSocketAddress(it, logicalPort) }
    }

    override fun getPort(): Int = synchronized(lock) { logicalPort }

    override fun getLocalAddress(): InetAddress = synchronized(lock) {
        outerSocket?.localAddress ?: super.getLocalAddress()
    }

    override fun getLocalSocketAddress(): SocketAddress? = synchronized(lock) { outerSocket?.localSocketAddress }

    override fun getLocalPort(): Int = synchronized(lock) { outerSocket?.localPort ?: 0 }

    override fun setTcpNoDelay(on: Boolean) {
        synchronized(lock) {
            pendingTcpNoDelay = on
            outerSocket?.tcpNoDelay = on
        }
    }

    override fun getTcpNoDelay(): Boolean = synchronized(lock) { outerSocket?.tcpNoDelay ?: pendingTcpNoDelay }

    override fun setKeepAlive(on: Boolean) {
        synchronized(lock) {
            pendingKeepAlive = on
            outerSocket?.keepAlive = on
        }
    }

    override fun getKeepAlive(): Boolean = synchronized(lock) { outerSocket?.keepAlive ?: pendingKeepAlive }

    override fun close() {
        val outerToClose: SSLSocket?
        val rawToClose: Socket?
        val registration: Closeable?
        synchronized(lock) {
            if (closed) return
            closed = true
            connecting = false
            outerToClose = outerSocket
            outerSocket = null
            rawToClose = rawSocket
            rawSocket = null
            registration = cancellationRegistration
            cancellationRegistration = null
        }
        try {
            closeQuietly(outerToClose)
        } finally {
            try {
                closeQuietly(rawToClose)
            } finally {
                registration?.close()
            }
        }
    }

    private fun establishedSocket(): SSLSocket = synchronized(lock) { establishedSocketLocked() }

    private fun establishedSocketLocked(): SSLSocket {
        if (!connected || closed) throw SocketException("Socket is not connected")
        return outerSocket ?: throw SocketException("Socket is closed")
    }

    private fun pinFor(certificate: X509Certificate): String = SPKI_PREFIX + Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded),
    )

    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: IOException) {
            // A rejected route never needs a second error channel.
        }
    }

    private companion object {
        const val HTTPS_PORT = 443
        const val TLS_1_2 = "TLSv1.2"
        const val TLS_1_3 = "TLSv1.3"
        const val SPKI_PREFIX = "sha256/"
    }
}
