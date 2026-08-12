package dev.junta.firmamobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MelillaBatchUrlPolicyTest {
    private val policy = MelillaBatchUrlPolicy()

    @Test
    fun acceptsOnlyTheThreeExactLiveServletPathShapes() {
        val operationId = "runtime-operation-1"
        val documentId = "runtime-document-1"

        val pre = policy.validatePreSignerUrl(
            "https://sede.melilla.es/sta/AutofirmaLote/presign/$operationId",
        )
        val post = policy.validatePostSignerUrl(
            "https://sede.melilla.es:443/sta/AutofirmaLote/postsign/$operationId",
        )
        val data = policy.validateDataReference(
            "https://sede.melilla.es/sta/AutofirmaLote/getdata/$operationId/$documentId",
        )

        assertNotNull(pre)
        assertNotNull(post)
        assertNotNull(data)
        assertEquals(operationId, pre?.operacionId)
        assertEquals(operationId, post?.operacionId)
        assertEquals(operationId, data?.operacionId)
        assertEquals(documentId, data?.docId)
    }

    @Test
    fun rejectsOriginPathQueryAndOpaqueBindingViolations() {
        val operationId = "runtime-operation-1"
        val documentId = "runtime-document-1"
        val validData = "https://sede.melilla.es/sta/AutofirmaLote/getdata/$operationId/$documentId"

        listOf(
            validData.replace("https://sede.melilla.es", "http://sede.melilla.es"),
            validData.replace("sede.melilla.es", "evil.sede.melilla.es"),
            validData.replace("/sta/AutofirmaLote", "/sta/AutofirmaLote/"),
            "$validData?unexpected=value",
            validData.replace("/$documentId", "/"),
            validData.replace("/getdata/", "/GETDATA/"),
            "$validData/extra",
            validData.replace("$operationId", "bad%2Foperation"),
            validData.replace("$operationId", "bad%20operation"),
            "https://sede.melilla.es/sta/AutofirmaLote?op=getdata" +
                "&operacionId=$operationId&docId=$documentId",
        ).forEach { candidate ->
            assertNull(candidate, policy.validateDataReference(candidate))
        }
    }

    @Test
    fun validatesPrePostAndDocumentIdsAsOneRuntimeBinding() {
        val operationId = "runtime-operation-1"
        val documentId = "runtime-document-1"
        val pre = policy.validatePreSignerUrl(
            "https://sede.melilla.es/sta/AutofirmaLote/presign/$operationId",
        )
        val post = policy.validatePostSignerUrl(
            "https://sede.melilla.es/sta/AutofirmaLote/postsign/$operationId",
        )
        val data = policy.validateDataReference(
            "https://sede.melilla.es/sta/AutofirmaLote/getdata/$operationId/$documentId",
        )

        assertEquals(pre?.operacionId, post?.operacionId)
        assertEquals(pre?.operacionId, data?.operacionId)
        assertEquals(documentId, data?.docId)
        assertNull(
            policy.validateDataReference(
                "https://sede.melilla.es/sta/AutofirmaLote/getdata/other-operation/$documentId",
                expectedOperacionId = pre?.operacionId,
                expectedDocId = documentId,
            ),
        )
        assertNull(
            policy.validateDataReference(
                "https://sede.melilla.es/sta/AutofirmaLote/getdata/$operationId/other-document",
                expectedOperacionId = operationId,
                expectedDocId = documentId,
            ),
        )
    }
}
