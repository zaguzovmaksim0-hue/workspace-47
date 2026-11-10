package dev.junta.firmamobile.browser

import dev.junta.firmamobile.afirma.servlet.AfirmaEndpointPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import java.net.URI

/** A different HTTPS port is a different page authority. The documented
 * public AutoFirma retrieval exception remains limited to its HTTPS default. */
internal object UniversalAfirmaRetrievalPolicy {
    private const val HTTPS_PORT = 443
    private const val OFFICIAL_HOST = "afirma.redsara.es"

    fun accepts(endpoint: URI, origin: TrustedOrigin): Boolean {
        if (!AfirmaEndpointPolicy.accepts(endpoint) || !origin.scheme.equals("https", true)) return false
        val port = effectivePort(endpoint.port)
        val sameOrigin = endpoint.host.equals(origin.host, true) && port == effectivePort(origin.port)
        val official = endpoint.host.equals(OFFICIAL_HOST, true) && port == HTTPS_PORT
        return sameOrigin || official
    }

    private fun effectivePort(port: Int): Int = if (port == -1) HTTPS_PORT else port
}
