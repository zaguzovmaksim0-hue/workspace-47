package dev.junta.firmamobile.browser

import dev.junta.firmamobile.network.TrustedOrigin
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class UniversalAfirmaRetrievalPolicyTest {
    @Test fun acceptsTheExactPagePortAndRejectsOtherPortsOfTheSameHost() {
        for (port in listOf(443, 8443, 4443)) {
            val origin = TrustedOrigin("https", "portal.example", port)
            assertTrue(UniversalAfirmaRetrievalPolicy.accepts(URI("https://portal.example:$port/get"), origin))
            for (other in listOf(443, 8443, 4443).filter { it != port }) {
                assertFalse(UniversalAfirmaRetrievalPolicy.accepts(URI("https://portal.example:$other/get"), origin))
            }
        }
    }

    @Test fun officialExceptionIsStillLimitedToItsDefaultHttpsOrigin() {
        val origin = TrustedOrigin("https", "portal.example", 8443)
        assertTrue(UniversalAfirmaRetrievalPolicy.accepts(URI("https://afirma.redsara.es/get"), origin))
        for (target in listOf("https://afirma.redsara.es:4443/get", "https://afirma.redsara.es:8443/get",
                "https://other.example:8443/get", "http://portal.example:8443/get")) {
            assertFalse(target, UniversalAfirmaRetrievalPolicy.accepts(URI(target), origin))
        }
    }

    @Test fun implicitAndExplicitHttpsDefaultsAreTheSameOrigin() {
        assertTrue(UniversalAfirmaRetrievalPolicy.accepts(URI("https://PORTAL.example/get"),
            TrustedOrigin("https", "portal.example", 443)))
        assertTrue(UniversalAfirmaRetrievalPolicy.accepts(URI("https://portal.example:443/get"),
            TrustedOrigin("https", "portal.example", -1)))
    }
}
