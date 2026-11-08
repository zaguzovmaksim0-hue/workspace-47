package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnTlsLoginProfileTest {
    private val addedIds = listOf(
        "diputacion-huelva-sede-public", "comunidad-madrid-cuenta-digital-53f1",
    )

    @Test
    fun newCertificateLoginProfilesGrantNoSigningOrReleaseAccess() {
        addedIds.forEach { id ->
            val profile = BuiltInSiteProfiles.qaRegistry.profile(ProfileId(id))!!
            assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
            assertTrue(profile.endpoints.isEmpty() && profile.operationPolicies.isEmpty())
            assertEquals(ProfileActivation.QA_ONLY, profile.activation)
            assertNull(BuiltInSiteProfiles.releaseRegistry.profile(ProfileId(id)))
            val policy = profile.clientAuthPolicy!!
            assertEquals(443, policy.requestPort)
            assertEquals(15, policy.grantTtlSeconds)
            assertFalse(policy.allowEmptyIssuerList)
            assertTrue(policy.requireOfferedKeyTypeMatch && policy.requireTlsClientAuthExtendedKeyUsage)
        }
    }

    @Test
    fun catalogCannotWidenReviewedOwnTlsTargetsOrCertificateFilters() {
        addedIds.forEach { id ->
            val json = BuiltInSiteProfiles.JSON
            val start = json.indexOf("\"profileId\": \"$id\"").also { check(it >= 0) }
            val end = json.indexOf("\"profileId\":", start + 1).let { if (it < 0) json.length else it }
            val block = json.substring(start, end)
            val policyStart = block.indexOf("\"clientAuthPolicy\":")
            val policyEnd = block.indexOf("\"certificateRules\":", policyStart)
            val bridgeEscalation = block.replaceRange(policyStart, policyEnd, "\"clientAuthPolicy\":null,\n")
                .replace(Regex("\"capabilities\"\\s*:\\s*\\[[^]]*]"), "\"capabilities\":[\"AFIRMA_URI\"]")
            assertThrows("$id cannot exchange TLS for a native bridge", IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(json.substring(0, start) + bridgeEscalation + json.substring(end))
            }
            val mutations = listOf(
                "\"requestPath\"\\s*:\\s*\"[^\"]*\"" to "\"requestPath\":\"/other\"",
                "\"requestOrigins\"\\s*:\\s*\\[[^]]*]" to
                    "\"requestOrigins\":[\"https://unreviewed.example\"]",
                "\"grantTtlSeconds\"\\s*:\\s*15" to "\"grantTtlSeconds\":60",
                "\"allowEmptyIssuerList\"\\s*:\\s*false" to "\"allowEmptyIssuerList\":true",
                "\"requireDigitalSignatureKeyUsage\"\\s*:\\s*true" to
                    "\"requireDigitalSignatureKeyUsage\":false",
            )
            mutations.forEach { (pattern, replacement) ->
                val regex = Regex(pattern)
                assertEquals("$id: $pattern", 1, regex.findAll(block).count())
                val changed = json.substring(0, start) + regex.replace(block, replacement) + json.substring(end)
                assertThrows("$id: $pattern", IllegalArgumentException::class.java) {
                    SiteProfileCatalogParser.parse(changed)
                }
            }
        }
    }

    @Test
    fun madridBindsOnlyItsFreshIdAndExactPostTarget() {
        val profile = BuiltInSiteProfiles.qaRegistry.profile(ProfileId("comunidad-madrid-cuenta-digital-53f1"))!!
        val policy = profile.clientAuthPolicy!!
        assertEquals(HttpMethod.POST, policy.requestMethod)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        val source = "https://gestiona.comunidad.madrid/auto_login/seleccion-idp.jsf"
        assertTrue(policy.matchesSourceUrl(URI("$source?id=fresh-session")))
        listOf(source, "$source?id=", "$source?id=a&id=b", "$source?id=a&extra=1").forEach {
            assertFalse(it, policy.matchesSourceUrl(URI(it)))
        }
        val target = "https://gestiona2.comunidad.madrid/auto_certificado/SelCertificado"
        assertTrue(policy.matchesRequestUrl(URI(target)))
        assertFalse(policy.matchesRequestUrl(URI("$target?id=fresh-session")))
        assertFalse(policy.matchesRequestUrl(URI(target.replace("gestiona2.", "gestiona."))))
    }

    @Test
    fun socialSecuritySedeAndImportassBindTheSamePostSourceChoice() {
        val profiles = listOf("seguridad-social-sede-autofirma", "tgss-importass-client-auth").map {
            BuiltInSiteProfiles.qaRegistry.profile(ProfileId(it))!!
        }
        assertEquals(profiles[0].clientAuthPolicy, profiles[1].clientAuthPolicy)
        val policy = profiles[0].clientAuthPolicy!!
        assertEquals(HttpMethod.POST, policy.requestMethod)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        val source = "https://idp.seg-social.es/PGIS/Login"
        assertTrue(policy.matchesSourceUrl(URI("$source?seleccion=IPCE")))
        listOf(source, "$source?seleccion=OTHER", "$source?seleccion=IPCE&extra=1").forEach {
            assertFalse(it, policy.matchesSourceUrl(URI(it)))
        }
        assertTrue(policy.matchesRequestUrl(URI("https://ipce.seg-social.es/IPCE/Login")))
        assertFalse(policy.matchesRequestUrl(URI("https://ipce.seg-social.es/IPCE/Login?SAMLRequest=fixture")))
    }

    @Test
    fun portalsRequiringANewSecurityModelKeepTheirOriginalBoundary() {
        listOf(
            "cnmc-remision-solicitudes-public",
            "diputacion-alava-registro-comun", "diputacion-bizkaia-instancia-generica",
            "diputacion-gipuzkoa-registro-public", "formentera-sede-electronica",
            "xunta-galicia-solicitude-xenerica",
        ).forEach { id ->
            val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == ProfileId(id) }
            assertNull(id, profile.clientAuthPolicy)
            assertFalse(id, Capability.CLIENT_TLS_AUTH in profile.capabilities)
        }
    }
}
