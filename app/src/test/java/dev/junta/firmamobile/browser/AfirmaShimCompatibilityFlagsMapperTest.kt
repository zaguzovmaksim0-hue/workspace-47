package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.ShimFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AfirmaShimCompatibilityFlagsMapperTest {
    @Test
    fun emptyFeatureSetProducesEveryLegacyFlagFalse() {
        val flags = AfirmaShimCompatibilityFlags.fromFeatures(emptySet())

        assertFalse(flags.ugr)
        assertFalse(flags.cantabria)
        assertFalse(flags.jccm)
        assertFalse(flags.jccmRegistro)
        assertFalse(flags.sevillaAtse)
        assertFalse(flags.airef)
        assertFalse(flags.cdti)
        assertFalse(flags.policia)
        assertFalse(flags.granCanaria)
        assertFalse(flags.fuerteventura)
        assertFalse(flags.canarias)
        assertFalse(flags.mineco)
        assertFalse(flags.melillaBatch)
        assertFalse(flags.lugoBatch)
        assertFalse(flags.caibBatch)
        assertFalse(flags.isciiiCertificateSelection)
        assertFalse(flags.valenciaCertificateSelection)
        assertFalse(flags.xuntaGalicia)
        assertFalse(flags.euskadiClientAuthPost)
        assertFalse(flags.acceda)
        assertFalse(flags.badajoz)
    }

    @Test
    fun everyFeatureEnablesExactlyItsOwnLegacyFlag() {
        val expected = mapOf(
            ShimFeature.UGR_LOGIN to AfirmaShimCompatibilityFlags(ugr = true),
            ShimFeature.CANTABRIA_LOGIN to AfirmaShimCompatibilityFlags(cantabria = true),
            ShimFeature.JCCM_PROBE to AfirmaShimCompatibilityFlags(jccm = true),
            ShimFeature.JCCM_REGISTRO to AfirmaShimCompatibilityFlags(jccmRegistro = true),
            ShimFeature.SEVILLA_ATSE to AfirmaShimCompatibilityFlags(sevillaAtse = true),
            ShimFeature.AIREF_SIGN to AfirmaShimCompatibilityFlags(airef = true),
            ShimFeature.CDTI_SIGN to AfirmaShimCompatibilityFlags(cdti = true),
            ShimFeature.POLICIA_SIGN to AfirmaShimCompatibilityFlags(policia = true),
            ShimFeature.GRAN_CANARIA_SIGN to AfirmaShimCompatibilityFlags(granCanaria = true),
            ShimFeature.FUERTEVENTURA_SIGN to AfirmaShimCompatibilityFlags(fuerteventura = true),
            ShimFeature.CANARIAS_LOGIN to AfirmaShimCompatibilityFlags(canarias = true),
            ShimFeature.MINECO_SIGN to AfirmaShimCompatibilityFlags(mineco = true),
            ShimFeature.ACCEDA_SIGN to AfirmaShimCompatibilityFlags(acceda = true),
            ShimFeature.BADAJOZ_SIGN to AfirmaShimCompatibilityFlags(badajoz = true),
            ShimFeature.ISCIII_SELECTION to
                AfirmaShimCompatibilityFlags(isciiiCertificateSelection = true),
            ShimFeature.VALENCIA_SELECTION to
                AfirmaShimCompatibilityFlags(valenciaCertificateSelection = true),
            ShimFeature.XUNTA_SIGN_SELECTION to AfirmaShimCompatibilityFlags(xuntaGalicia = true),
            ShimFeature.EUSKADI_CLIENT_AUTH_POST to
                AfirmaShimCompatibilityFlags(euskadiClientAuthPost = true),
            ShimFeature.STA_JSON_BATCH to AfirmaShimCompatibilityFlags(melillaBatch = true),
            ShimFeature.LUGO_XML_BATCH to AfirmaShimCompatibilityFlags(lugoBatch = true),
            ShimFeature.CAIB_PORTAFIB_BATCH to AfirmaShimCompatibilityFlags(caibBatch = true),
        )

        assertEquals(21, expected.size)
        assertEquals(ShimFeature.entries.toSet(), expected.keys)
        expected.forEach { (feature, expectedFlags) ->
            assertEquals(feature.name, expectedFlags, AfirmaShimCompatibilityFlags.fromFeatures(setOf(feature)))
        }
    }
}
