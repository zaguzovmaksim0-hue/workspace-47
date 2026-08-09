package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DgtVerificationProfileTest {
    @Test
    fun parsesTheExactDgtQaOnlyContractAndExcludesItFromRelease() {
        val id = ProfileId("dgt-verificacion-equipo")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == id }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/verificacion-de-mi-equipo/"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.dgt.gob.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), operation.capabilities)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertNull(operation.endpointId)
        assertEquals(mapOf("filter" to "nonexpired:"), operation.fixedExtraProperties)
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(id))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(id))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
    }

    @Test
    fun endpointlessMiniAppletCadesAcceptsOnlyTheTwoKnownFixedPropertyShapes() {
        val exact = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("dgt-verificacion-equipo")
        }
        assertEquals(mapOf("filter" to "nonexpired:"), exact.operationPolicies.getValue(ProtocolOperation.SIGN).fixedExtraProperties)

        val wrongValue = BuiltInSiteProfiles.JSON.replace(
            "\"filter\": \"nonexpired:\"",
            "\"filter\": \"nonexpired\"",
        )
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(wrongValue)
        }

        val extraKey = BuiltInSiteProfiles.JSON.replace(
            "\"filter\": \"nonexpired:\"",
            "\"filter\": \"nonexpired:\", \"unexpected\": \"value\"",
        )
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(extraKey)
        }

        val impostorProfileId = BuiltInSiteProfiles.JSON.replace(
            "\"profileId\": \"dgt-verificacion-equipo\"",
            "\"profileId\": \"dgt-verificacion-impostor\"",
        )
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(impostorProfileId)
        }

        val aragon = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("aragon-siraw")
        }
        assertEquals(
            mapOf("mode" to "explicit", "filter" to "nonexpired"),
            aragon.operationPolicies.getValue(ProtocolOperation.SIGN).fixedExtraProperties,
        )
        assertFalse(
            aragon.operationPolicies.getValue(ProtocolOperation.SIGN).fixedExtraProperties ==
                exact.operationPolicies.getValue(ProtocolOperation.SIGN).fixedExtraProperties,
        )
    }
}
