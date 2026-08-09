package dev.junta.firmamobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MelillaBatchUrlPolicyTest {
    private val policy = MelillaBatchUrlPolicy()

    @Test
    fun acceptsOnlyTheThreeExactOperationQueryShapes() {
        val operationId = "runtime-operation-1"
        val documentId = "runtime-document-1"

        val pre = policy.validatePreSignerUrl(
            "https://sede.melilla.es/sta/AutofirmaLote?op=presign&operacionId=$operationId",
        )
        val post = policy.validatePostSignerUrl(
            "https://sede.melilla.es:443/sta/AutofirmaLote?operacionId=$operationId&op=postsign",
        )
        val data = policy.validateDataReference(
            "https://sede.melilla.es/sta/AutofirmaLote?op=getdata&operacionId=$operationId" +
                "&docId=$documentId",
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
        val validData = "https://sede.melilla.es/sta/AutofirmaLote?op=getdata" +
            "&operacionId=$operationId&docId=$documentId"

        listOf(
            validData.replace("https://sede.melilla.es", "http://sede.melilla.es"),
            validData.replace("sede.melilla.es", "evil.sede.melilla.es"),
            validData.replace("/sta/AutofirmaLote", "/sta/AutofirmaLote/"),
            "$validData&unexpected=value",
            validData.replace("docId=$documentId", "docId="),
            validData.replace("op=getdata", "op=GETDATA"),
            validData.replace("&docId=$documentId", "&docId=$documentId&docId=duplicate"),
            validData.replace("$operationId", "bad%ZZoperation"),
            validData.replace("$operationId", "bad%20operation"),
        ).forEach { candidate ->
            assertNull(candidate, policy.validateDataReference(candidate))
        }
    }

    @Test
    fun validatesPrePostAndDocumentIdsAsOneRuntimeBinding() {
        val operationId = "runtime-operation-1"
        val documentId = "runtime-document-1"
        val pre = policy.validatePreSignerUrl(
            "https://sede.melilla.es/sta/AutofirmaLote?op=presign&operacionId=$operationId",
        )
        val post = policy.validatePostSignerUrl(
            "https://sede.melilla.es/sta/AutofirmaLote?op=postsign&operacionId=$operationId",
        )
        val data = policy.validateDataReference(
            "https://sede.melilla.es/sta/AutofirmaLote?op=getdata&operacionId=$operationId" +
                "&docId=$documentId",
        )

        assertEquals(pre?.operacionId, post?.operacionId)
        assertEquals(pre?.operacionId, data?.operacionId)
        assertEquals(documentId, data?.docId)
        assertNull(
            policy.validateDataReference(
                "https://sede.melilla.es/sta/AutofirmaLote?op=getdata" +
                    "&operacionId=other-operation&docId=$documentId",
                expectedOperacionId = pre?.operacionId,
                expectedDocId = documentId,
            ),
        )
        assertNull(
            policy.validateDataReference(
                "https://sede.melilla.es/sta/AutofirmaLote?op=getdata" +
                    "&operacionId=$operationId&docId=other-document",
                expectedOperacionId = operationId,
                expectedDocId = documentId,
            ),
        )
    }
}
