package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.TrustMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserUrlPolicyTest {
    private val registry = BuiltInSiteProfiles.qaRegistry
    private val known = ProfileId("junta-andalucia")

    @Test
    fun publicPortsLoadWithoutBorrowingTheHostsSigningProfile() {
        for (selected in listOf(null, known)) {
            val policy = BrowserUrlPolicy(registry, selected)
            for (active in listOf(null, known)) {
                for (port in listOf(1, 80, 444, 8443, 4443, 65535)) {
                    val url = "https://www.juntadeandalucia.es:$port/form?step=one#section"
                    val resolution = policy.resolve(url, active)
                    assertEquals(url, resolution.uri?.toString())
                    assertNull(resolution.site)
                    assertEquals(TrustMode.BROWSE_ONLY, resolution.trustMode)
                }
            }
        }
        val start = registry.profile(known)!!.startUrl.toASCIIString()
        assertEquals(TrustMode.TRUSTED_SIGNING, BrowserUrlPolicy(registry, known).resolve(start).trustMode)
    }

    @Test
    fun externalOnlyOriginsDoNotSilentlyMatchAnotherPort() {
        val policy = BrowserUrlPolicy(registry, null, setOf(ExactOrigin.parse("https://public.example")))
        assertEquals(TrustMode.EXTERNAL_ONLY, policy.resolve("https://public.example:443/").trustMode)
        for (port in listOf(8443, 4443)) {
            assertEquals(TrustMode.BROWSE_ONLY, policy.resolve("https://public.example:$port/").trustMode)
        }
    }

    @Test
    fun profileSelectionDoesNotBypassPublicEndpointValidation() {
        val invalid = listOf(
            "http://public.example:8443/",
            "https://localhost:8443/",
            "https://service.local:4443/",
            "https://127.0.0.1:8443/",
            "https://192.0.2.1:4443/",
            "https://[::1]:8443/",
            "https://user@public.example:4443/",
            "https://public.example:/",
            "https://public.example:0443/",
            "https://public.example:0/",
            "https://public.example:65536/",
            "https://%70ublic.example:8443/",
            "https://public.example:8443/%GG",
            "https://public.example:4443/a\\b",
            "https://public.example:8443/" + "a".repeat(8192),
        )
        for (selected in listOf(null, known)) {
            val policy = BrowserUrlPolicy(registry, selected)
            invalid.forEach { raw ->
                val resolution = policy.resolve(raw, selected)
                assertNull(raw, resolution.uri)
                assertNull(raw, resolution.site)
                assertEquals(raw, TrustMode.BLOCKED, resolution.trustMode)
            }
        }
    }
}
