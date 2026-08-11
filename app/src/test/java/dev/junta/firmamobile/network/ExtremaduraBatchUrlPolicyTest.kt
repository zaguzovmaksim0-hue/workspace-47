package dev.junta.firmamobile.network

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExtremaduraBatchUrlPolicyTest {
    private val melilla = MelillaBatchUrlPolicy()
    private val extremadura = ExtremaduraBatchUrlPolicy()

    @Test
    fun rejectsExtremaduraOriginAndUrlWidening() {
        val valid =
            "https://tramites.juntaex.es/sta/AutofirmaLote/presign/runtime-operation-1"

        listOf(
            valid.replace("https://", "http://"),
            valid.replace("tramites.juntaex.es", "evil.tramites.juntaex.es"),
            valid.replace("https://", "https://user@"),
            valid.replace("tramites.juntaex.es", "tramites.juntaex.es:444"),
            "$valid?unexpected=value",
            "$valid#fragment",
            valid.replace("/sta/AutofirmaLote/", "/sta/autofirmalote/"),
            valid.replace("/presign/", "/presign//"),
        ).forEach { candidate ->
            assertNull(candidate, extremadura.validatePreSignerUrl(candidate))
        }
    }

    @Test
    fun fixedProfilesOwnOnlyTheirExactStaRuntimeBindings() {
        val operationId = "runtime-operation-1"
        val documentId = "runtime-document-1"

        val melillaPre =
            "https://sede.melilla.es/sta/AutofirmaLote/presign/$operationId"
        val melillaPost =
            "https://sede.melilla.es/sta/AutofirmaLote/postsign/$operationId"
        val melillaData =
            "https://sede.melilla.es/sta/AutofirmaLote/getdata/$operationId/$documentId"
        val extremaduraPre =
            "https://tramites.juntaex.es/sta/AutofirmaLote/presign/$operationId"
        val extremaduraPost =
            "https://tramites.juntaex.es:443/sta/AutofirmaLote/postsign/$operationId"
        val extremaduraData =
            "https://tramites.juntaex.es/sta/AutofirmaLote/getdata/$operationId/$documentId"

        assertNotNull(melilla.validatePreSignerUrl(melillaPre))
        assertNotNull(melilla.validatePostSignerUrl(melillaPost))
        assertNotNull(melilla.validateDataReference(melillaData, operationId, documentId))
        assertNotNull(extremadura.validatePreSignerUrl(extremaduraPre))
        assertNotNull(extremadura.validatePostSignerUrl(extremaduraPost))
        assertNotNull(extremadura.validateDataReference(extremaduraData, operationId, documentId))

        assertNull(melilla.validatePreSignerUrl(extremaduraPre))
        assertNull(extremadura.validatePreSignerUrl(melillaPre))
        assertNull(extremadura.validatePreSignerUrl(extremaduraPost))
        assertNull(extremadura.validatePostSignerUrl(extremaduraPre))
        assertNull(extremadura.validateDataReference(extremaduraPre, operationId, documentId))
        assertNull(
            extremadura.validateDataReference(
                extremaduraData,
                expectedOperacionId = "other-operation",
                expectedDocId = documentId,
            ),
        )
        assertNull(
            extremadura.validateDataReference(
                extremaduraData,
                expectedOperacionId = operationId,
                expectedDocId = "other-document",
            ),
        )
    }
}
