package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.URI
import java.security.Signature
import java.util.Base64
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Test
import com.tom_roush.pdfbox.pdmodel.PDDocument

/** In-memory protocol/crypto tests; no browser, remote server or E2E. */
class NativeJavaPropertiesSigningTest {
    @Test fun triphaseReencodesValuesWithoutInventingServiceOrConsentParameters() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity()
        val expected = linkedMapOf("note" to "First line\nserverUrl=https://not-a-new-target.example\nheadless=true",
            "literal" to "\\u0041 + %2F", "description" to "  Renovación en Cádiz  ")
        val supplied = expected + mapOf("serverUrl" to SERVICE, "headless" to "true")
        val invocation = acceptedGeneral("sign", baseFields("XAdEStri", DATA) + ("properties" to stored(supplied)))
        assertEquals(expected, invocation.remoteOptions!!.properties)
        var phases = 0; var authorizations = 0; var deliveries = 0; var firstParams: String? = null
        val operation = NativeMultiPhaseOperation(invocation, NativeSigningServiceTransport { target, form ->
            assertEquals(URI(SERVICE), target)
            val encoded = form.getValue("params")
            val raw = Base64.getUrlDecoder().decode(encoded)
            val parsed = Properties().apply { StringReader(raw.toString(Charsets.UTF_8)).use { load(it) } }
            val actual = parsed.stringPropertyNames().associateWith(parsed::getProperty)
            assertEquals(expected, actual); assertFalse(parsed.containsKey("headless")); assertFalse(parsed.containsKey("serverUrl"))
            raw.fill(0)
            phases++
            if (phases == 1) {
                assertEquals("pre", form["op"]); assertEquals(0, authorizations); firstParams = encoded
                val pre = NativeTriSession("XAdES", listOf(NativeTriSign("one", null,
                    mapOf("PRE" to Base64.getEncoder().encodeToString(DATA), "NEED_PRE" to "false"))))
                AfirmaRetrievedBytes(b64(checkNotNull(NativeTriphaseXml.encode(pre))).toByteArray())
            } else {
                assertEquals(2, phases); assertEquals("post", form["op"]); assertEquals(1, authorizations)
                assertEquals(firstParams, encoded)
                val signed = checkNotNull(NativeTriphaseXml.parse(Base64.getUrlDecoder().decode(form.getValue("session"))))
                val contribution = signed.signs.single()
                assertTrue(Signature.getInstance("SHA256withRSA").run {
                    initVerify(key.identity.certificate.publicKey); update(DATA)
                    verify(Base64.getDecoder().decode(contribution.parameters.getValue("PK1")))
                })
                AfirmaRetrievedBytes(("OK NEWID=" + b64("synthetic-result".toByteArray())).toByteArray())
            }
        }, AfirmaResultTransport { _, _, _ ->
            assertEquals(1, authorizations); assertEquals(2, phases); deliveries++; AfirmaDeliveryResult.ACKNOWLEDGED
        }, generalClockFor(key.identity))
        try {
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { authorizations++ })
            assertEquals(1, deliveries); assertEquals(0, key.encodedReads.get())
            assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(key.identity) { error("No replay") } } }
        } finally { operation.close() }
    }

    @Test fun localMixedBatchPreservesEscapedPdfMetadataAndRequestedCadesPackaging() = runBlocking<Unit> {
        val key = nonExportableSyntheticIdentity(); val pdf = nativePadesFixture()
        val reason = "Renovación: copia #1"; val city = "Cádiz"
        val descriptor = NativeProtocolJson.encode(mapOf("algorithm" to "SHA256withRSA", "singlesigns" to listOf(
            mapOf("id" to "cms", "format" to "CAdES", "datareference" to b64(DATA), "extraparams" to b64("mode=im\\u0070licit".toByteArray())),
            mapOf("id" to "pdf", "format" to "PAdES", "datareference" to b64(pdf),
                "extraparams" to stored(mapOf("signReason" to reason, "signatureProductionCity" to city))),
        )))
        val request = acceptedGeneral("batch", mapOf("id" to "Properties123", "stservlet" to STORAGE,
            "dat" to b64(descriptor), "jsonbatch" to "true", "localBatchProcess" to "true"))
        var approvals = 0; var stores = 0
        val operation = NativeMultiPhaseOperation(request, NativeSigningServiceTransport { _, _ -> error("Local batch must not contact a service") },
            AfirmaResultTransport { _, _, wire ->
                assertEquals(1, approvals); stores++
                val result = AfirmaIntermediateCipher.decode(wire, null)
                try {
                    val rows = NativeProtocolJson.parse(result)["signs"] as List<*>
                    assertEquals(2, rows.size)
                    val entries = rows.map { NativeTriphaseCodec.objectValue(it) }
                    assertEquals(listOf("cms", "pdf"), entries.map { it["id"] })
                    assertTrue(entries.all { it["result"] == "DONE_AND_SAVED" })
                    val cms = CMSSignedData(Base64.getDecoder().decode(entries[0]["signature"] as String))
                    assertArrayEquals(DATA, cms.signedContent.content as ByteArray)
                    assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(key.identity.certificate)))
                    val signedPdf = Base64.getDecoder().decode(entries[1]["signature"] as String)
                    try {
                        PDDocument.load(signedPdf).use {
                            assertEquals(reason, it.signatureDictionaries.single().reason)
                            assertEquals(city, it.signatureDictionaries.single().location)
                        }
                    } finally { signedPdf.fill(0) }
                } finally { result.fill(0) }
                AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(key.identity))
        try {
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(key.identity) { approvals++ })
            assertEquals(1, approvals); assertEquals(1, stores); assertEquals(0, key.encodedReads.get())
        } finally { operation.close(); pdf.fill(0); descriptor.fill(0) }
    }

    private fun stored(values: Map<String, String>): String {
        val output = ByteArrayOutputStream()
        Properties().apply { values.forEach { (k, v) -> setProperty(k, v) } }.store(output, "Synthetic properties")
        return b64(output.toByteArray())
    }
    private fun baseFields(format: String, bytes: ByteArray) = mapOf("id" to "Properties123", "stservlet" to STORAGE,
        "format" to format, "algorithm" to "SHA256withRSA", "dat" to b64(bytes))
    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().encodeToString(bytes)
    companion object {
        private val DATA = "Synthetic properties signing payload".toByteArray()
        private const val SERVICE = "https://signer.synthetic.example/service"
        private const val STORAGE = "https://storage.synthetic.example/put"
    }
}
