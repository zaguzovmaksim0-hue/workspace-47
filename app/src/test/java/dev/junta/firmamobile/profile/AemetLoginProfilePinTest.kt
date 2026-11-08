package dev.junta.firmamobile.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AemetLoginProfilePinTest {
    @Test
    fun aCorrectLastEntryCannotHideContradictoryEvidence() {
        val mutations = listOf(
            Triple("aemet-public-solicitud-navigation", "https://www.aemet.es/es/sede_electronica", "2026-08-23"),
            Triple("canarias-sede", "https://sede.gobiernodecanarias.org/sede/identificacion", "2026-11-08"),
        ).map { (id, url, date) ->
            TlsProfileMutation(
                "$id contradictory evidence", id,
                "(\\{\\s*\"url\"\\s*:\\s*\"${Regex.escape(url)}\",\\s*\"reviewedOn\"\\s*:\\s*\"$date\"\\s*})",
                "{\"url\":\"$url\",\"reviewedOn\":\"2026-08-16\"},\$1",
            )
        }
        assertEquals(emptyList<String>(), TlsProfileMutations.accepted(mutations))
    }

    @Test
    fun independentlyPinnedLoginContractRejectsCatalogExpansion() {
        val mutations = listOf(
            "version" to ("\"profileVersion\": 2" to "\"profileVersion\": 1"),
            "key algorithm" to ("\"RSA\"" to "\"EC\""),
            "algorithm" to ("\"SHA1_WITH_RSA\"" to "\"SHA256_WITH_RSA\""),
            "format" to ("\"CADES\"" to "\"PADES\""),
            "packaging" to ("\"DETACHED\"" to "\"ENVELOPED\""),
            "mode" to ("\"EXPLICIT\"" to "\"IMPLICIT\""),
            "callback" to ("\"miniapplet-sign-callback-v1\"" to "\"other-callback\""),
            "properties" to ("\"fixedExtraProperties\": {}" to "\"fixedExtraProperties\": {\"mode\":\"implicit\"}"),
            "extra keys" to ("\"allowedExtraProperties\": []" to "\"allowedExtraProperties\": [\"format\"]"),
        ).map { (name, edit) ->
            TlsProfileMutation(name, "aemet-public-solicitud-navigation", Regex.escape(edit.first), edit.second)
        }
        assertEquals(emptyList<String>(), TlsProfileMutations.accepted(mutations))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replace("sede.aemet.gob.es", "foreign.example"))
        }
    }
}
