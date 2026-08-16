package dev.junta.firmamobile.network

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BurgosBatchUrlPolicyTest {
    private val policy = BurgosBatchUrlPolicy()
    private val melilla = MelillaBatchUrlPolicy()

    @Test
    fun acceptsOnlyBurgosExactStaRuntimeBindings() {
        val operationId = "runtime-operation-1"
        val documentId = "runtime-document-1"
        val pre = "$ORIGIN/sta/AutofirmaLote/presign/$operationId"
        val post = "$ORIGIN/sta/AutofirmaLote/postsign/$operationId"
        val data = "$ORIGIN/sta/AutofirmaLote/getdata/$operationId/$documentId"

        assertNotNull(policy.validatePreSignerUrl(pre))
        assertNotNull(policy.validatePostSignerUrl(post))
        assertNotNull(policy.validateDataReference(data, operationId, documentId))

        listOf(
            pre.replace("https://", "http://"),
            pre.replace("registro.diputaciondeburgos.es", "evil.registro.diputaciondeburgos.es"),
            pre.replace("registro.diputaciondeburgos.es", "registro.diputaciondeburgos.es:444"),
            "$pre?unexpected=value",
            "$pre#fragment",
            pre.replace("/sta/AutofirmaLote/", "/sta/autofirmalote/"),
            "$ORIGIN/sta/AutofirmaLote?op=presign&operacionId=$operationId",
        ).forEach { candidate ->
            assertNull(candidate, policy.validatePreSignerUrl(candidate))
        }
        assertNull(melilla.validatePreSignerUrl(pre))
        assertNull(policy.validatePreSignerUrl("https://sede.melilla.es/sta/AutofirmaLote/presign/$operationId"))
    }

    private companion object {
        const val ORIGIN = "https://registro.diputaciondeburgos.es"
    }
}
