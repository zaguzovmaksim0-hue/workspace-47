package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMultiPhasePresentationTest {
    @Test fun singleRemoteFormatsRetainTheServiceFormatAndDestinations() {
        for (format in listOf("CAdEStri", "XAdEStri", "PAdEStri")) {
            val fields = generalFields("doc".toByteArray(), format) + ("serverurl" to "https://service.example/tri")
            acceptedGeneral("sign", fields).use { invocation ->
                nativeOperation(invocation).use { operation ->
                    assertEquals(NativeMultiPhaseFormat.TRIPHASE, operation.details.multiPhaseFormat)
                    assertEquals(checkNotNull(invocation.remoteOptions).format, operation.details.format)
                    assertEquals(listOf("https://service.example/tri"), operation.details.serviceDestinations)
                    assertTrue(operation.details.delegatedSigning)
                }
            }
        }
    }
    @Test fun localBatchMetadataDistinguishesCosigningWithoutExecuting() {
        for (cosign in listOf(false, true)) {
            val item = mapOf("id" to "a", "datareference" to "YWJj",
                "suboperation" to if (cosign) "cosign" else "sign")
            val bytes = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA",
                "format" to "CAdES", "singlesigns" to listOf(item)))
            try {
                val fields = mapOf("id" to "I18nBatch123", "stservlet" to "https://store.example/put",
                    "jsonbatch" to "true", "localBatchProcess" to "true", "dat" to url64(bytes))
                acceptedGeneral("batch", fields).use { invocation ->
                    nativeOperation(invocation).use { operation ->
                        val expected = if (cosign) NativeMultiPhaseFormat.LOCAL_CADES_COSIGN_BATCH
                            else NativeMultiPhaseFormat.LOCAL_BATCH
                        assertEquals(expected, operation.details.multiPhaseFormat)
                        assertEquals("Batch", operation.details.format)
                        assertFalse(operation.details.delegatedSigning)
                        assertTrue(operation.details.serviceDestinations.isEmpty())
                    }
                }
            } finally { bytes.fill(0) }
        }
    }
    @Test fun remoteBatchMetadataRetainsDelegationAndApprovedServices() {
        val item = mapOf("id" to "a", "datareference" to "YWJj")
        val bytes = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA",
            "format" to "CAdES", "singlesigns" to listOf(item)))
        try {
            val fields = mapOf("id" to "I18nRemote123", "stservlet" to "https://store.example/put",
                "jsonbatch" to "true", "dat" to url64(bytes),
                "batchpresignerurl" to "https://service.example/pre",
                "batchpostsignerurl" to "https://service.example/post")
            acceptedGeneral("batch", fields).use { invocation ->
                nativeOperation(invocation).use { operation ->
                    assertEquals(NativeMultiPhaseFormat.REMOTE_BATCH, operation.details.multiPhaseFormat)
                    assertEquals("Batch", operation.details.format)
                    assertTrue(operation.details.delegatedSigning)
                    assertEquals(listOf("https://service.example/pre", "https://service.example/post"),
                        operation.details.serviceDestinations)
                }
            }
        } finally { bytes.fill(0) }
    }
}
