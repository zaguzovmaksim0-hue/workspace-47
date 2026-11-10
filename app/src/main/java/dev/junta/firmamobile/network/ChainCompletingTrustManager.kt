package dev.junta.firmamobile.network

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Clock
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLContext
import java.net.Socket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/** Completes a server chain with narrowly matched bundled intermediates.
 * Trust is still established exclusively by [delegate]; hostname checking
 * remains with OkHttp.
 */
internal class ChainCompletingTrustManager(
    private val delegate: X509ExtendedTrustManager = defaultTrustManager(),
    bundledIntermediates: List<X509Certificate> = BundledIntermediates.certificates,
    clock: Clock = Clock.systemUTC(),
) : X509ExtendedTrustManager() {
    private val completion = CertificateChainCompletion(bundledIntermediates, clock)

    fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, host: String): List<X509Certificate> {
        // OkHttp owns hostname verification; this only rejects an absent caller-supplied host.
        require(host.isNotBlank())
        return checkedServerChain(chain, authType, null, null).toList()
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        checkedServerChain(chain, authType, null, null)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        checkedServerChain(chain, authType, socket, null)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        checkedServerChain(chain, authType, null, engine)
    }

    private fun checkedServerChain(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
        engine: SSLEngine?,
    ): Array<X509Certificate> {
        try {
            delegateServerCheck(chain, authType, socket, engine)
            return chain
        } catch (original: CertificateException) {
            return completion.find(chain) { candidate ->
                delegateServerCheck(candidate, authType, socket, engine)
            } ?: throw original
        }
    }

    private fun delegateServerCheck(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
        engine: SSLEngine?,
    ) {
        when {
            socket != null -> delegate.checkServerTrusted(chain, authType, socket)
            engine != null -> delegate.checkServerTrusted(chain, authType, engine)
            else -> delegate.checkServerTrusted(chain, authType)
        }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        delegate.checkClientTrusted(chain, authType, socket)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        delegate.checkClientTrusted(chain, authType, engine)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    private companion object {
        fun defaultTrustManager(): X509ExtendedTrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers
                .filterIsInstance<X509ExtendedTrustManager>()
                .firstOrNull()
                ?: error("No X509ExtendedTrustManager is available")
        }
    }
}

/** Bounded path search only; the validation callback must still establish platform trust. */
private class CertificateChainCompletion(
    private val intermediates: List<X509Certificate>,
    private val clock: Clock,
) {
    fun find(
        chain: Array<X509Certificate>,
        validate: (Array<X509Certificate>) -> Unit,
    ): Array<X509Certificate>? {
        if (!canComplete(chain)) return null
        val candidates = (chain.drop(1) + intermediates).distinct()
        val pending = ArrayDeque<List<X509Certificate>>()
        pending.add(listOf(chain.first()))
        var attempts = 0
        var accepted: Array<X509Certificate>? = null
        while (accepted == null && attempts < MAX_ATTEMPTS && pending.isNotEmpty()) {
            val path = pending.removeFirst()
            if (path.size >= MAX_CHAIN_LENGTH) continue
            val extensions = extend(path, candidates)
            while (accepted == null && attempts < MAX_ATTEMPTS && extensions.hasNext()) {
                val completed = extensions.next()
                attempts++
                if (isTrusted(completed, validate)) accepted = completed else pending.add(completed.toList())
            }
        }
        return accepted
    }

    private fun canComplete(chain: Array<X509Certificate>): Boolean =
        chain.isNotEmpty() && chain.size <= MAX_CHAIN_LENGTH && intermediates.size <= MAX_CANDIDATES

    private fun extend(
        path: List<X509Certificate>,
        candidates: List<X509Certificate>,
    ): Iterator<Array<X509Certificate>> =
        candidates.asSequence()
            .filter { it !in path && isIssuer(path.last(), it) }
            .map { (path + it).toTypedArray() }
            .iterator()

    private fun isTrusted(chain: Array<X509Certificate>, validate: (Array<X509Certificate>) -> Unit): Boolean =
        try {
            // A bundled certificate supplies a missing link only. The
            // platform still decides trust, validity and constraints.
            validate(chain)
            true
        } catch (_: CertificateException) {
            false
        }

    private fun isIssuer(child: X509Certificate, issuer: X509Certificate): Boolean {
        val now = clock.instant()
        return issuer.subjectX500Principal == child.issuerX500Principal &&
            issuer.basicConstraints >= 0 &&
            (issuer.keyUsage?.getOrElse(KEY_CERT_SIGN_BIT) { false } != false) &&
            issuer.notBefore.toInstant() <= now && now <= issuer.notAfter.toInstant() &&
            runCatching { child.verify(issuer.publicKey) }.isSuccess
    }

    private companion object {
        const val MAX_CHAIN_LENGTH = 10
        const val MAX_CANDIDATES = 32
        const val MAX_ATTEMPTS = 64
        const val KEY_CERT_SIGN_BIT = 5
    }
}

internal val chainCompletingTrustManager: ChainCompletingTrustManager by lazy {
    ChainCompletingTrustManager()
}

internal val chainCompletingSslContext: SSLContext by lazy {
    SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(chainCompletingTrustManager), null)
    }
}
