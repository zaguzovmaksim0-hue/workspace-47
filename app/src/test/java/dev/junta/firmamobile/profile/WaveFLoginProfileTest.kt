package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveFLoginProfileTest {
    private val starts = mapOf(
        "boe-sede-public-home" to "https://extranet.boe.es/anuncios/",
        "cmt-public-navigation" to "https://sede.cmt.gob.es/",
        "fondos-europeos-sede-public-home" to
            "https://webpub2.igae.hacienda.gob.es/accesoremoto/frmExecAPP.aspx?code=SIRIOWS&ta=ANO",
        "imserso-sede-public-navigation" to
            "https://sede.imserso.gob.es/sedecdi?locale=es&action=consulta&representante=no&aut=si",
        "ine-sede-public-navigation" to "https://sede.ine.gob.es/es/index.htm",
        "sepes-transportes-public-complaints" to "https://sede.transportes.gob.es/MFOM.Sisgest/autenticacion.aspx",
    )

    @Test
    fun reviewedLoginsGrantOnlyQaClientTlsWithExactStarts() {
        starts.forEach { (id, start) ->
            val profile = BuiltInSiteProfiles.qaRegistry.profile(ProfileId(id))!!
            assertEquals(2, profile.profileVersion)
            assertEquals(URI(start), profile.startUrl)
            assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
            assertEquals(ProfileActivation.QA_ONLY, profile.activation)
            assertNull(BuiltInSiteProfiles.releaseRegistry.profile(ProfileId(id)))
            assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
            assertTrue(profile.endpoints.isEmpty() && profile.operationPolicies.isEmpty())
            assertTrue(profile.trustedBrowseOrigins.isEmpty())
            assertEquals(CertificateFilterRules(setOf("RSA", "EC"), true), profile.certificateRules)
            val policy = profile.clientAuthPolicy!!
            assertEquals(443, policy.requestPort)
            assertEquals(15, policy.grantTtlSeconds)
            assertTrue(policy.allowEmptyIssuerList)
            assertTrue(policy.requireOfferedKeyTypeMatch && policy.requireTlsClientAuthExtendedKeyUsage)
        }
    }

    @Test
    fun boeAndCmtRequireTheirExactSourceAndTargetTuples() {
        val boe = BuiltInSiteProfiles.qaRegistry.profile(ProfileId("boe-sede-public-home"))!!.clientAuthPolicy!!
        val boeSource = "https://extranet.boe.es/id/check-cert.php?referer=https://extranet.boe.es/anuncios/"
        assertTrue(boe.matchesSourceUrl(URI(boeSource)))
        assertFalse(boe.matchesSourceUrl(URI("https://extranet.boe.es/anuncios/")))
        assertTrue(boe.matchesRequestUrl(URI("https://check-cert.extranet.boe.es/")))
        assertFalse(boe.matchesRequestUrl(URI("https://check-cert.extranet.boe.es/?extra=1")))
        val cmt = BuiltInSiteProfiles.qaRegistry.profile(ProfileId("cmt-public-navigation"))!!.clientAuthPolicy!!
        val source = "https://sede.cmt.gob.es/carpetaciudadana/login.aspx?returnUrl=" +
            "https%3a%2f%2fsede.cmt.gob.es%2fcarpetaciudadana%2fcarpeta_ciudadana.aspx"
        assertTrue(cmt.matchesSourceUrl(URI(source)))
        assertFalse(cmt.matchesSourceUrl(URI("https://sede.cmt.gob.es/segex/identificacion_opciones.aspx")))
        val target = "https://identificacionssl.sedipualba.es/"
        val token = "20261108-AbCdEf0123456789_fixtureToken"
        assertTrue(cmt.matchesRequestUrl(URI("$target?idtoken=$token&idioma=es&entidad=99006")))
        listOf(
            "?idtoken=&idioma=es&entidad=99006", "?idtoken=invalid&idioma=es&entidad=99006",
            "?idtoken=$token&idtoken=$token&idioma=es&entidad=99006",
            "?idtoken=$token&idioma=es&entidad=02003", "?idtoken=$token&idioma=es",
            "?idtoken=$token&idioma=es&entidad=99006&extra=1",
        ).forEach { assertFalse(it, cmt.matchesRequestUrl(URI(target + it))) }
    }

    @Test
    fun independentPinsRejectContractWidening() {
        starts.forEach { (id, _) ->
            val profile = BuiltInSiteProfiles.qaRegistry.profile(ProfileId(id))!!
            val policy = profile.clientAuthPolicy!!
            val json = BuiltInSiteProfiles.JSON
            val start = json.indexOf("\"profileId\": \"$id\"")
            val end = json.indexOf("\"profileId\":", start + 1).let { if (it < 0) json.length else it }
            val block = json.substring(start, end)
            val mutations = listOf(
                "\"profileVersion\": 2" to "\"profileVersion\": 1",
                "\"startUrl\": \"${profile.startUrl}\"" to "\"startUrl\": \"https://unreviewed.example/\"",
                "\"CLIENT_TLS_AUTH\"" to "\"SIGN\"",
                "\"CLIENT_TLS_AUTH\"" to "\"AFIRMA_URI\"",
                "\"requireDigitalSignatureKeyUsage\": true" to "\"requireDigitalSignatureKeyUsage\": false",
                "\"requestPath\": \"${policy.requestPath}\"" to "\"requestPath\": \"/unreviewed\"",
                "\"requestPort\": 443" to "\"requestPort\": 8443",
                "\"grantTtlSeconds\": 15" to "\"grantTtlSeconds\": 60",
                "\"allowEmptyIssuerList\": true" to "\"allowEmptyIssuerList\": false",
                "\"requireOfferedKeyTypeMatch\": true" to "\"requireOfferedKeyTypeMatch\": false",
                "\"requireTlsClientAuthExtendedKeyUsage\": true" to
                    "\"requireTlsClientAuthExtendedKeyUsage\": false",
                "\"${policy.sourceUrls.single()}\"" to "\"https://unreviewed.example/\"",
                "\"${policy.requestOrigins.single().serialized}\"" to "\"https://unreviewed.example\"",
                "\"${policy.sourceUrls.single()}\"" to "\"${policy.sourceUrls.single()}&extra=1\"",
            )
            val queryMutation = if (id == "cmt-public-navigation") {
                "\"entidad\": \"99006\"" to "\"entidad\": \"02000\""
            } else {
                "\"fixedQueryParameters\": {}" to "\"fixedQueryParameters\": {\"extra\":\"1\"}"
            }
            (mutations + queryMutation).forEach { (old, new) ->
                assertTrue("$id: $old", old in block)
                assertThrows(id, IllegalArgumentException::class.java) {
                    SiteProfileCatalogParser.parse(
                        json.substring(0, start) + block.replace(old, new) + json.substring(end),
                    )
                }
            }
        }
    }
}
