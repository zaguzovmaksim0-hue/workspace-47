package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import dev.junta.firmamobile.signing.LocalSignatureResult
import java.net.URI
import java.net.URLEncoder
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NativeGeneralSigningTest {
    @Test fun eachTriphaseFormatBindsThreePhasesAndUploadsOnlyOneFinalEnvelope() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val clock = generalClockFor(key.identity)
        for ((label, wireFormat) in listOf("CAdEStri" to "CAdES", "PAdEStri" to "pades", "XAdEStri" to "XAdES")) {
            val invocation = acceptedGeneral("sign", generalFields("original".toByteArray(), label) + ("serverurl" to SERVICE))
            val phases = mutableListOf<String>(); var authorization = 0; var checks = 0; var stored: String? = null
            val service = NativeSigningServiceTransport { endpoint, fields ->
                assertEquals(URI(SERVICE), endpoint); assertEquals(wireFormat, fields["format"])
                assertEquals("sign", fields["cop"]); assertEquals("SHA256withRSA", fields["algo"])
                assertArrayEquals(key.identity.certificate.encoded, Base64.getUrlDecoder().decode(fields.getValue("cert")))
                assertEquals("original", Base64.getUrlDecoder().decode(fields.getValue("doc")).toString(Charsets.UTF_8))
                val phase = fields.getValue("op"); phases += phase
                if (phase == "pre") {
                    assertEquals(0, authorization)
                    AfirmaRetrievedBytes(url64(checkNotNull(NativeTriphaseXml.encode(session(wireFormat)))).toByteArray())
                } else {
                    assertEquals(1, authorization)
                    val signed = checkNotNull(NativeTriphaseXml.parse(Base64.getUrlDecoder().decode(fields.getValue("session"))))
                    verifyPk1(signed, key.identity)
                    AfirmaRetrievedBytes(("OK NEWID=" + url64("service-result".toByteArray())).toByteArray())
                }
            }
            val operation = NativeMultiPhaseOperation(invocation, service, AfirmaResultTransport { _, _, wire ->
                assertEquals(listOf("pre", "post"), phases); assertEquals(1, authorization); stored = wire; AfirmaDeliveryResult.ACKNOWLEDGED
            }, clock)
            assertTrue(operation.details.delegatedSigning); assertEquals(listOf(SERVICE), operation.details.serviceDestinations)
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.executeWithCheckpoints(key.identity, { checks++ }, { authorization++ }))
            assertEquals("service-result", AfirmaIntermediateCipher.decode(stored!!.substringAfter('|'), null).toString(Charsets.UTF_8))
            assertTrue(checks >= 5)
            assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) { error("No repeated authorization") } } }
            operation.close()
        }
        assertEquals(0, key.encodedReads.get())
    }

    @Test fun xmlBatchTransmitsNativeXmlAndResultComesBeforeOptionalCertificate() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); var authorized = false; var phase = 0; var wire = ""
        val descriptor = """<signbatch algorithm="SHA256withRSA" stoponerror="false"><singlesign id="doc"><datasource>YWJj</datasource><format>XAdES</format><suboperation>sign</suboperation></singlesign></signbatch>""".toByteArray()
        val invocation = acceptedGeneral("batch", batchFields(descriptor, false) + ("needcert" to "true"))
        val service = NativeSigningServiceTransport { endpoint, fields ->
            assertArrayEquals(descriptor, Base64.getUrlDecoder().decode(fields.getValue("xml")))
            assertNull(fields["json"]); phase++
            if (phase == 1) { assertEquals(URI(PRE), endpoint); AfirmaRetrievedBytes(checkNotNull(NativeTriphaseXml.encode(session("XAdES")))) }
            else {
                assertEquals(URI(POST), endpoint); assertTrue(authorized)
                verifyPk1(checkNotNull(NativeTriphaseXml.parse(Base64.getUrlDecoder().decode(fields.getValue("tridata")))), key.identity)
                AfirmaRetrievedBytes("<signs><sign id='doc'><result>OK</result></sign></signs>".toByteArray())
            }
        }
        val operation = NativeMultiPhaseOperation(invocation, service, AfirmaResultTransport { _, _, value -> wire = value; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.executeWithCheckpoints(key.identity, {}, { authorized = true }))
        val parts = wire.split('|'); assertEquals(2, parts.size)
        assertTrue(AfirmaIntermediateCipher.decode(parts[0], null).toString(Charsets.UTF_8).startsWith("<signs>"))
        assertArrayEquals(key.identity.certificate.encoded, AfirmaIntermediateCipher.decode(parts[1], null))
        assertTrue(operation.resultSummary!!.contains("positivo: 1")); operation.close()
    }

    @Test fun partialJsonPreFailureIsBoundToDescriptorAndNeverReportedAsAllSigned() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val bytes = jsonBatch(); var calls = 0; var wire = ""
        val pre = NativeProtocolJson.encode(mapOf("td" to NativeProtocolJson.parse(NativeTriphaseCodec.encodeJson(session("XAdES"))),
            "results" to listOf(mapOf("id" to "failed", "result" to "ERROR_PRE", "description" to "Synthetic failure"))))
        val operation = NativeMultiPhaseOperation(acceptedGeneral("batch", batchFields(bytes, true)), NativeSigningServiceTransport { _, fields ->
            calls++
            if (calls == 1) AfirmaRetrievedBytes(pre.copyOf()) else {
                val updated = NativeProtocolJson.parse(Base64.getUrlDecoder().decode(fields.getValue("json")))
                val rows = updated["singlesigns"] as List<*>
                val failed = NativeTriphaseCodec.objectValue(rows[1]); assertEquals("ERROR_PRE", failed["result"])
                assertFalse(failed.containsKey("datareference")); assertFalse(failed.containsKey("format"))
                val td = NativeTriphaseCodec.parseJson(NativeProtocolJson.parse(Base64.getUrlDecoder().decode(fields.getValue("tridata"))))
                assertEquals(listOf("doc"), td.signs.map { it.id }); verifyPk1(td, key.identity)
                AfirmaRetrievedBytes("""{"signs":[{"id":"doc","result":"DONE_AND_SAVED"},{"id":"failed","result":"ERROR_PRE"}]}""".toByteArray())
            }
        }, AfirmaResultTransport { _, _, value -> wire = value; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.executeWithCheckpoints(key.identity, {}, {}))
        assertEquals(2, calls); assertTrue(operation.resultSummary!!.contains("otros resultados: 1"))
        assertEquals(2, (NativeProtocolJson.parse(AfirmaIntermediateCipher.decode(wire, null))["signs"] as List<*>).size)
        operation.close()
    }

    @Test fun stopOnErrorNeverSignsOrPostsOtherJsonItems() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); var serviceCalls = 0; var authorizations = 0; var wire = ""
        val bytes = jsonBatch(stop = true)
        val operation = NativeMultiPhaseOperation(acceptedGeneral("batch", batchFields(bytes, true)), NativeSigningServiceTransport { _, _ ->
            serviceCalls++; check(serviceCalls == 1)
            AfirmaRetrievedBytes(NativeProtocolJson.encode(mapOf("td" to NativeProtocolJson.parse(NativeTriphaseCodec.encodeJson(session("XAdES"))),
                "results" to listOf(mapOf("id" to "failed", "result" to "ERROR_PRE")))))
        }, AfirmaResultTransport { _, _, result -> wire = result; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.executeWithCheckpoints(key.identity, {}, { authorizations++ }))
        val rows = NativeProtocolJson.parse(AfirmaIntermediateCipher.decode(wire, null))["signs"] as List<*>
        assertEquals(listOf("SKIPPED", "ERROR_PRE"), rows.map { NativeTriphaseCodec.objectValue(it)["result"] })
        assertEquals(1, serviceCalls); assertEquals(1, authorizations); assertEquals(0, key.encodedReads.get())
        operation.close()
    }

    @Test fun postOrReceiptFailureIsUncertainAndCannotBeAutomaticallyRepeated() = runBlocking<Unit> {
        val id = nonExportableSyntheticIdentity().identity
        for (failPost in listOf(true, false)) {
            var calls = 0; var stores = 0
            val operation = NativeMultiPhaseOperation(acceptedGeneral("sign", generalFields("data".toByteArray(), "XAdEStri") + ("serverurl" to SERVICE)), NativeSigningServiceTransport { _, form ->
                calls++
                if (form["op"] == "pre") AfirmaRetrievedBytes(url64(checkNotNull(NativeTriphaseXml.encode(session("XAdES")))).toByteArray())
                else if (failPost) throw NativeServiceException(true) else AfirmaRetrievedBytes("OK NEWID=YWJj".toByteArray())
            }, AfirmaResultTransport { _, _, _ -> stores++; AfirmaDeliveryResult.NOT_SENT }, generalClockFor(id))
            assertEquals(AfirmaDeliveryResult.UNCERTAIN, operation.executeWithCheckpoints(id, {}, {}))
            assertEquals(2, calls); assertEquals(if (failPost) 0 else 1, stores)
            assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(id) {} } }
            operation.close()
        }
    }

    @Test fun cancellationAfterPreAndInjectedForeignBatchIdsPreventPostAndStorage() = runBlocking<Unit> {
        val id = nonExportableSyntheticIdentity().identity; var calls = 0; var cancelled = false
        val operation = NativeMultiPhaseOperation(acceptedGeneral("sign", generalFields("data".toByteArray(), "CAdEStri") + ("serverurl" to SERVICE)), NativeSigningServiceTransport { _, _ ->
            calls++; cancelled = true
            AfirmaRetrievedBytes(url64(checkNotNull(NativeTriphaseXml.encode(session("CAdES")))).toByteArray())
        }, AfirmaResultTransport { _, _, _ -> error("No storage after cancellation") }, generalClockFor(id))
        try { operation.executeWithCheckpoints(id, { if (cancelled) throw CancellationException("Synthetic navigation") }, { error("No post permission") }); fail("Expected cancellation") }
        catch (_: CancellationException) { assertEquals(1, calls) }
        operation.close()
        val batch = checkNotNull(NativeBatchDescriptor.parse(jsonBatch(), true))
        val foreign = session("XAdES").copy(signs = session("XAdES").signs.map { it.copy(id = "unapproved") })
        assertThrows(IllegalArgumentException::class.java) {
            NativeBatchProtocol.pre(NativeProtocolJson.encode(mapOf("td" to NativeProtocolJson.parse(NativeTriphaseCodec.encodeJson(foreign)), "results" to listOf(mapOf("id" to "failed", "result" to "ERROR_PRE")))), batch)
        }
    }

    @Test fun localMixedBatchSignsCadesXadesAndPdfWithoutContactingAService() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val payloads = listOf("one".toByteArray(), "two".toByteArray(), nativePadesFixture())
        val formats = listOf("CAdES", "XAdES", "PAdES")
        val bytes = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "format" to "CAdES", "singlesigns" to formats.mapIndexed { index, format ->
            mapOf("id" to "item$index", "format" to format, "datareference" to Base64.getEncoder().encodeToString(payloads[index]))
        }))
        var wire = ""; var authorized = 0
        val fields = mapOf("id" to "Request123", "stservlet" to STORAGE, "dat" to url64(bytes), "jsonbatch" to "true", "localBatchProcess" to "true", "needcert" to "true")
        val operation = NativeMultiPhaseOperation(acceptedGeneral("batch", fields), NativeSigningServiceTransport { _, _ -> error("No remote service in local batch") },
            AfirmaResultTransport { _, _, value -> assertEquals(1, authorized); wire = value; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(key.identity))
        assertFalse(operation.details.delegatedSigning); assertEquals(3, operation.details.batchItems)
        assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.executeWithCheckpoints(key.identity, {}, { authorized++ }))
        val rows = NativeProtocolJson.parse(AfirmaIntermediateCipher.decode(wire.substringBefore('|'), null))["signs"] as List<*>
        for (index in rows.indices) {
            val row = NativeTriphaseCodec.objectValue(rows[index]); assertEquals("DONE_AND_SAVED", row["result"])
            val signed = Base64.getDecoder().decode(row["signature"] as String)
            val good = when (index) {
                0 -> NativeCadesEngine(clock = generalClockFor(key.identity)).verify(signed, payloads[index], key.identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, true)
                1 -> NativeXadesEngine(generalClockFor(key.identity)).verify(signed, payloads[index], key.identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, NativeXadesOptions.parse("XAdES", emptyMap())!!)
                else -> NativePadesEngine(generalClockFor(key.identity)).verify(signed, payloads[index], key.identity.certificate, SigningAlgorithm.SHA256_WITH_RSA, NativePadesOptions.parse(emptyMap())!!)
            }
            assertTrue("Locally reverify $index", good)
        }
        assertEquals(0, key.encodedReads.get()); operation.close()
    }

    @Test fun localBatchFailureProducesExplicitRollbackAndSkippedStatuses() = runBlocking<Unit> {
        val id = nonExportableSyntheticIdentity().identity; var wire = ""
        val bytes = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "format" to "CAdES", "stoponerror" to true,
            "singlesigns" to listOf(mapOf("id" to "a", "datareference" to "YWJj"), mapOf("id" to "b", "format" to "unsupported", "datareference" to "YWJj"), mapOf("id" to "c", "datareference" to "YWJj"))))
        val operation = NativeMultiPhaseOperation(acceptedGeneral("batch", mapOf("id" to "Batch123", "stservlet" to STORAGE, "dat" to url64(bytes), "jsonbatch" to "true", "localBatchProcess" to "true")),
            NativeSigningServiceTransport { _, _ -> error("No service") }, AfirmaResultTransport { _, _, v -> wire = v; AfirmaDeliveryResult.ACKNOWLEDGED }, generalClockFor(id))
        operation.executeWithCheckpoints(id, {}, {})
        val rows = (NativeProtocolJson.parse(AfirmaIntermediateCipher.decode(wire, null))["signs"] as List<*>).map(NativeTriphaseCodec::objectValue)
        assertEquals(listOf("SKIPPED", "ERROR_PRE", "SKIPPED"), rows.map { it["result"] }); assertTrue(rows.none { "signature" in it })
        operation.close()
    }

    private fun session(format: String) = NativeTriSession(format, listOf(NativeTriSign("doc", null, linkedMapOf("PRE" to "c2lnbiB0aGVzZSBieXRlcw==", "NEED_PRE" to "false", "OPAQUE" to "value<&>"))))
    private fun verifyPk1(session: NativeTriSession, key: UnlockedIdentity) {
        for (sign in session.signs) {
            assertFalse(sign.parameters.containsKey("PRE")); assertEquals("value<&>", sign.parameters["OPAQUE"])
            assertTrue(Signature.getInstance("SHA256withRSA").run { initVerify(key.certificate.publicKey); update("sign these bytes".toByteArray()); verify(Base64.getDecoder().decode(sign.parameters.getValue("PK1"))) })
        }
    }
    private fun jsonBatch(stop: Boolean = false) = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "format" to "XAdES", "stoponerror" to stop,
        "singlesigns" to listOf(mapOf("id" to "doc", "datareference" to "YWJj"), mapOf("id" to "failed", "datareference" to "ZGVm"))))
    private fun batchFields(bytes: ByteArray, json: Boolean) = mapOf("id" to "Batch123", "stservlet" to STORAGE, "dat" to url64(bytes), "jsonbatch" to json.toString(), "batchpresignerurl" to PRE, "batchpostsignerurl" to POST)
    companion object { private const val SERVICE = "https://service.example/triphase"; private const val PRE = "https://service.example/pre"; private const val POST = "https://service.example/post"; private const val STORAGE = "https://storage.example/put" }
}

internal fun url64(bytes: ByteArray): String = Base64.getUrlEncoder().encodeToString(bytes)
internal fun generalFields(payload: ByteArray, format: String): Map<String, String> = mapOf("id" to "Request123", "stservlet" to "https://storage.example/put", "dat" to url64(payload), "format" to format, "algorithm" to "SHA256withRSA")
internal fun acceptedGeneral(operation: String, fields: Map<String, String>): AfirmaServletInvocation {
    val query = fields.entries.joinToString("&") { (name, value) -> name + "=" + URLEncoder.encode(value, "UTF-8").replace("+", "%20") }
    val result = AfirmaServletInvocationParser.parse("afirma://$operation?$query", "https://source.example/page")
    assertTrue(result.toString(), result is AfirmaServletParseResult.Accepted)
    return (result as AfirmaServletParseResult.Accepted).invocation
}

internal fun generalClockFor(identity: UnlockedIdentity): java.time.Clock = java.time.Clock.fixed(identity.certificate.notBefore.toInstant().plusSeconds(60), java.time.ZoneOffset.UTC)
