package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.net.URI
import java.net.URLEncoder
import java.time.Clock
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic complete get-config -> decrypt -> XML -> CAdES -> cipher -> put.
 * Only explicit execute simulates accepted consent; no real service is called. */
class AfirmaIndirectPipelineTest {
    @Test fun actualCadesPipelineUsesDownloadedDataAndItsOwnResultCipher() = runBlocking {
        val identity = nonExportableSyntheticIdentity()
        val clock = Clock.fixed(identity.identity.summary.validFrom.plusSeconds(60), ZoneOffset.UTC)
        val payload = "binary payload + original\u0000 bytes".toByteArray()
        for (aes in listOf(false, true)) {
            val key = ByteArray(32) { it.toByte() }; val iv = ByteArray(16) { (it + 9).toByte() }
            val cipher = AfirmaAesParameters(key, iv)
            val config = b64("{\"algo\":\"AES\",\"key\":\"${b64(key)}\",\"iv\":\"${b64(iv)}\"}".toByteArray())
            val outer = "afirma://sign?fileid=Config-123&rid=Result-456&key=12345678&rtservlet=${form(GET)}&stservlet=${form(PUT)}" +
                if (aes) "&cipher=${form(config)}" else ""
            val request = (AfirmaServletInvocationParser.parse(outer, "https://original-page.example/work?secret=hidden") as AfirmaServletParseResult.Deferred).invocation
            val xml = "<sign>" + mapOf("id" to "Result-456", "stservlet" to PUT, "key" to "87654321",
                "format" to "CAdES", "algorithm" to "SHA512withRSA", "dat" to b64(payload)).entries.joinToString("") {
                    "<e k='${it.key}' v='${form(it.value)}'/>"
                } + "</sign>"
            val wire = ((if (aes) cipher.encode(xml.toByteArray()) else AfirmaIntermediateCipher.encode(xml.toByteArray(), "12345678")) + "\n").toByteArray()
            var downloads = 0; var uploads = 0; var authorized = false; var result: String? = null
            val resolver = AfirmaDeferredResolver(AfirmaRequestTransport { url, id ->
                downloads++; assertEquals(URI(GET), url); assertEquals("Config-123", id); AfirmaRetrievedBytes(wire)
            }, operationFactory = { invocation ->
                NativeAfirmaOperation(invocation, AfirmaResultTransport { url, id, value ->
                    assertTrue(authorized); uploads++; assertEquals(URI(PUT), url); assertEquals("Result-456", id)
                    result = value; AfirmaDeliveryResult.ACKNOWLEDGED
                }, clock = clock)
            })
            val operation = resolver.resolve(request)
            assertEquals(1, downloads); assertEquals(0, uploads)
            assertEquals("https://original-page.example", operation.details.sourceOrigin)
            assertEquals(payload.size, operation.details.payloadBytes)
            assertTrue(operation.certificateCompatible(identity.identity))
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(identity.identity) { authorized = true })
            assertEquals(1, uploads)
            val fields = checkNotNull(result).split('|'); assertEquals(2, fields.size)
            val cert = AfirmaIntermediateCipher.decode(fields[0], "87654321")
            val signature = AfirmaIntermediateCipher.decode(fields[1], "87654321")
            assertArrayEquals(identity.identity.certificate.encoded, cert)
            assertTrue(NativeCadesEngine(clock = clock).verify(signature, payload, identity.identity.certificate, SigningAlgorithm.SHA512_WITH_RSA, true))
            assertTrue(wire.all { it == 0.toByte() })
            cert.fill(0); signature.fill(0); operation.close(); cipher.close(); key.fill(0); iv.fill(0)
        }
        assertEquals(0, identity.encodedReads.get()); payload.fill(0)
    }

    @Test fun downloadedSelectCertificateNeverUsesTheSignOperation() = runBlocking {
        val identity = nonExportableSyntheticIdentity()
        val request = (AfirmaServletInvocationParser.parse(
            "afirma://selectcert?fileid=Config-1&rtservlet=${form(GET)}&key=12345678", "https://page.example",
        ) as AfirmaServletParseResult.Deferred).invocation
        val xml = "<selectcert><e k='id' v='Response-1'/><e k='stservlet' v='${form(PUT)}'/><e k='key' v='87654321'/></selectcert>"
        var result: String? = null
        val resolver = AfirmaDeferredResolver(AfirmaRequestTransport { _, _ -> AfirmaRetrievedBytes(AfirmaIntermediateCipher.encode(xml.toByteArray(), "12345678").toByteArray()) },
            operationFactory = { NativeAfirmaOperation(it, AfirmaResultTransport { _, _, value -> result = value; AfirmaDeliveryResult.ACKNOWLEDGED },
                clock = Clock.fixed(identity.identity.summary.validFrom.plusSeconds(60), ZoneOffset.UTC)) })
        val operation = resolver.resolve(request)
        assertEquals("selectcert", operation.details.operation); assertNull(operation.details.payloadSha256)
        operation.execute(identity.identity) {}
        assertFalse(checkNotNull(result).contains('|'))
        val certificate = AfirmaIntermediateCipher.decode(checkNotNull(result), "87654321")
        assertArrayEquals(identity.identity.certificate.encoded, certificate)
        assertEquals(0, identity.encodedReads.get()); certificate.fill(0); operation.close()
    }

    private fun form(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private companion object { const val GET = "https://retriever.example/get?route=A"; const val PUT = "https://storage.example/put?route=B" }
}
