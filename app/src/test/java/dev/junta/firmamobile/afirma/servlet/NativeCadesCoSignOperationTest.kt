package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.OpaqueRsaFixture
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Test

/** Real native operations and in-memory storage only; no live portal/E2E. */
class NativeCadesCoSignOperationTest {
    private val f get() = NativeCadesCoSignFixtures
    @Test fun nativeDirectCosignUsesOneApprovalAndPreservesCertificateThenSignatureWireOrder() = runBlocking<Unit> {
        for (detached in listOf(false,true)) {
            val previous=f.signed(detached)
            val request=NativeCadesCoSignRequestTest.request(previous)+"&key=12345678"
            val invocation=accepted(request)
            var authorizations=0;var stores=0
            val operation=NativeAfirmaOperation(invocation,AfirmaResultTransport { endpoint,id,wire ->
                assertEquals("https://storage.synthetic.example/put",endpoint.toString());assertEquals("CoSign123",id)
                assertEquals(1,authorizations);stores++
                val parts=wire.split('|');assertEquals(2,parts.size)
                val cert=AfirmaIntermediateCipher.decode(parts[0],"12345678")
                val result=AfirmaIntermediateCipher.decode(parts[1],"12345678")
                try {
                    assertArrayEquals(f.second.identity.certificate.encoded,cert)
                    checkCms(result,2,detached)
                } finally { cert.fill(0);result.fill(0) }
                AfirmaDeliveryResult.ACKNOWLEDGED
            },f.clock)
            try {
                assertEquals("cosign",operation.details.operation)
                assertTrue(operation.details.format!!.startsWith("CAdES · co-sign"))
                assertEquals(previous.size,operation.details.payloadBytes)
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED,operation.execute(f.second.identity){authorizations++})
                assertEquals(1,stores);assertEquals(1,authorizations)
                assertThrows(IllegalStateException::class.java){runBlocking{operation.execute(f.second.identity){error("No replay")}}}
            } finally { operation.close();previous.fill(0) }
        }
    }

    @Test fun finalUploadRefusalDoesNotSendTheComputedSignature() = runBlocking<Unit> {
        val previous=f.signed(false);var stores=0
        val operation=NativeAfirmaOperation(accepted(NativeCadesCoSignRequestTest.request(previous)),
            AfirmaResultTransport { _,_,_->stores++;AfirmaDeliveryResult.ACKNOWLEDGED },f.clock)
        try {
            try { operation.execute(f.second.identity){throw CancellationException("Synthetic cancellation")};fail("Must not upload") }
            catch (_:CancellationException) { }
            assertEquals(0,stores)
        } finally { operation.close();previous.fill(0) }
    }

    @Test fun explicitCancellationDoesNotUseAPrivateKeyAndCannotBeReplayed() = OpaqueRsaFixture.use { key ->
        runBlocking<Unit> {
            val previous=f.signed(false);var permissions=0;var messages=0
            val operation=NativeAfirmaOperation(accepted(NativeCadesCoSignRequestTest.request(previous)),AfirmaResultTransport { _,_,wire ->
                assertEquals(1,permissions);assertEquals("CANCEL",wire);messages++;AfirmaDeliveryResult.ACKNOWLEDGED
            },f.clock)
            try {
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED,operation.notifyCancellation{permissions++})
                assertEquals(1,messages);assertEquals(0,key.signatures.get())
                assertThrows(IllegalStateException::class.java){runBlocking{operation.execute(key.identity){error("No replay")}}}
            } finally { operation.close();previous.fill(0) }
        }
    }

    @Test fun localMixedBatchCanCosignAttachedAndDetachedCadesAlongsideANewSignature() = runBlocking<Unit> {
        val attached=f.signed(false);val detached=f.signed(true)
        val descriptor=NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA","format" to "CAdES","singlesigns" to listOf(
            mapOf("id" to "attached","suboperation" to "cosign","datareference" to b64(attached)),
            mapOf("id" to "detached","suboperation" to "cosign","datareference" to b64(detached)),
            mapOf("id" to "ordinary","suboperation" to "sign","datareference" to b64(f.data)),
        )))
        val invocation=acceptedGeneral("batch",mapOf("id" to "BatchCosign123","stservlet" to "https://storage.synthetic.example/put",
            "dat" to b64(descriptor),"jsonbatch" to "true","localBatchProcess" to "true"))
        var permissions=0;var stores=0
        val operation=NativeMultiPhaseOperation(invocation,NativeSigningServiceTransport{_,_->error("Local batch has no server signing phase")},AfirmaResultTransport{_,_,wire->
            assertEquals(1,permissions);stores++
            val body=AfirmaIntermediateCipher.decode(wire,null)
            try {
                val rows=(NativeProtocolJson.parse(body)["signs"] as List<*>).map(NativeTriphaseCodec::objectValue)
                assertEquals(listOf("attached","detached","ordinary"),rows.map{it["id"]})
                assertTrue(rows.all{it["result"]=="DONE_AND_SAVED"})
                for((index,row) in rows.withIndex()) {
                    val result=Base64.getDecoder().decode(row["signature"] as String)
                    try{checkCms(result,if(index<2)2 else 1,index!=0)}finally{result.fill(0)}
                }
            } finally { body.fill(0) }
            AfirmaDeliveryResult.ACKNOWLEDGED
        },f.clock)
        try {
            assertTrue(operation.details.format!!.contains("CAdES co-sign"))
            assertFalse(operation.details.delegatedSigning)
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED,operation.execute(f.second.identity){permissions++})
            assertEquals(1,stores);assertEquals(3,operation.batchReceipt!!.entries.size)
        } finally { operation.close();attached.fill(0);detached.fill(0);descriptor.fill(0) }
    }

    @Test fun invalidCosignItemDoesNotTurnAValidPartialBatchIntoAFakeAllSuccess() = runBlocking<Unit> {
        val descriptor=NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA","format" to "CAdES","singlesigns" to listOf(
            mapOf("id" to "bad","suboperation" to "cosign","datareference" to b64("invalid CMS".toByteArray())),
            mapOf("id" to "new","suboperation" to "sign","datareference" to b64(f.data)),
        )))
        val invocation=acceptedGeneral("batch",mapOf("id" to "BatchCosign123","stservlet" to "https://storage.synthetic.example/put","dat" to b64(descriptor),"jsonbatch" to "true","localBatchProcess" to "true"))
        var permissions=0
        val operation=NativeMultiPhaseOperation(invocation,NativeSigningServiceTransport{_,_->error("No remote service")},AfirmaResultTransport{_,_,wire->
            assertEquals(1,permissions)
            val body=AfirmaIntermediateCipher.decode(wire,null)
            try {
                val rows=(NativeProtocolJson.parse(body)["signs"] as List<*>).map(NativeTriphaseCodec::objectValue)
                assertEquals(listOf("ERROR_PRE","DONE_AND_SAVED"),rows.map{it["result"]})
                assertNull(rows[0]["signature"]);assertNotNull(rows[1]["signature"])
            }finally{body.fill(0)}
            AfirmaDeliveryResult.ACKNOWLEDGED
        },f.clock)
        try{assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED,operation.execute(f.second.identity){permissions++})}
        finally{operation.close();descriptor.fill(0)}
    }

    @Test fun deferredDescriptorRetainsCosignInsteadOfChangingItToOrdinarySign() {
        val previous=f.signed(true)
        val values=mapOf("id" to "CoSign123","stservlet" to "https://storage.synthetic.example/put","format" to "CAdES","algorithm" to "SHA256withRSA","dat" to b64(previous))
        try {
            val result=AfirmaServletInvocationParser.parseRetrieved(AfirmaServletOperation.COSIGN,NativeCadesCoSignRequestTest.SOURCE,values)
            assertTrue(result.toString(),result is AfirmaServletParseResult.Accepted)
            (result as AfirmaServletParseResult.Accepted).invocation.use {
                assertEquals(AfirmaServletOperation.COSIGN,it.operation);assertTrue(it.cadesCoSign);assertFalse(it.pdfCoSign)
            }
        } finally { previous.fill(0) }
    }

    @Test fun changedPackagingOrUnknownPolicyIsNotSilentlyAppliedToExistingSignatures() {
        val previous=f.signed(false)
        try {
            for(properties in listOf("mode=explicit","policyIdentifier=1.2.3","precalculatedHashAlgorithm=SHA256")) {
                assertTrue(AfirmaServletInvocationParser.parse(NativeCadesCoSignRequestTest.request(previous,properties),NativeCadesCoSignRequestTest.SOURCE) is AfirmaServletParseResult.Unsupported)
            }
            accepted(NativeCadesCoSignRequestTest.request(previous,"mode=implicit")).close()
        } finally { previous.fill(0) }
    }

    @Test fun ordinarySignOfACmsFileRemainsOrdinarySignRatherThanImplicitCosign() = runBlocking<Unit> {
        val previous=f.signed(false)
        val parsed=accepted(NativeCadesCoSignRequestTest.request(previous).replace("afirma://cosign","afirma://sign"))
        assertFalse(parsed.cadesCoSign)
        val operation=NativeAfirmaOperation(parsed,AfirmaResultTransport{_,_,wire->
            val output=AfirmaIntermediateCipher.decode(wire.substringAfter('|'),null)
            try {
                val cms=CMSSignedData(CMSProcessableByteArray(previous),output)
                assertEquals(1,cms.signerInfos.size())
                assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(f.second.identity.certificate)))
            }finally{output.fill(0)}
            AfirmaDeliveryResult.ACKNOWLEDGED
        },f.clock)
        try { assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED,operation.execute(f.second.identity){}) }
        finally { operation.close();previous.fill(0) }
    }

    private fun accepted(uri:String):AfirmaServletInvocation {
        val result=AfirmaServletInvocationParser.parse(uri,NativeCadesCoSignRequestTest.SOURCE)
        assertTrue(result.toString(),result is AfirmaServletParseResult.Accepted)
        return (result as AfirmaServletParseResult.Accepted).invocation
    }
    private fun b64(bytes:ByteArray)=Base64.getUrlEncoder().encodeToString(bytes)
    private fun checkCms(encoded:ByteArray,count:Int,detached:Boolean) {
        val data=f.data
        try {
            val bare=CMSSignedData(encoded);assertEquals(detached,bare.signedContent==null)
            val cms=if(detached)CMSSignedData(CMSProcessableByteArray(data),encoded)else bare
            assertEquals(count,cms.signerInfos.size())
            for(signer in cms.signerInfos.signers) {
                val certificate=cms.certificates.getMatches(null).filter(signer.sid::match).single()
                assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(certificate)))
            }
        }finally{data.fill(0)}
    }
}
