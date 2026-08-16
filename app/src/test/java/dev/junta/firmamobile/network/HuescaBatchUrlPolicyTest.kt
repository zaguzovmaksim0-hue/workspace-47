package dev.junta.firmamobile.network

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HuescaBatchUrlPolicyTest {
    private val policy = HuescaBatchUrlPolicy()
    private val melilla = MelillaBatchUrlPolicy()

    @Test
    fun acceptsOnlyHuescaExactStaRuntimeBindings() {
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
            pre.replace("ovc24.dphuesca.es", "evil.ovc24.dphuesca.es"),
            pre.replace("ovc24.dphuesca.es", "ovc24.dphuesca.es:444"),
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
        const val ORIGIN = "https://ovc24.dphuesca.es"
    }
}
