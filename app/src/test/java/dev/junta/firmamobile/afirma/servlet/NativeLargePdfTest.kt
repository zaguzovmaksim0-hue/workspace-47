package dev.junta.firmamobile.afirma.servlet

import com.tom_roush.pdfbox.pdmodel.PDDocument
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.freshSyntheticIdentity
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.ZoneOffset
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** Local synthetic PDF and software test identity only. No endpoint is contacted. */
class NativeLargePdfTest {
    @Test fun eightMegabyteMultipagePdfParsesPreviewsSignsAndVerifies() {
        val identity = freshSyntheticIdentity()
        val clock = Clock.fixed(identity.certificate.notBefore.toInstant().plusSeconds(60), ZoneOffset.UTC)
        val original = multipagePdf()
        assertTrue(original.size > 7 * MIB)
        assertTrue(original.size <= 8 * MIB)
        val parsed = AfirmaServletInvocationParser.parse(request(original), "https://portal.example/form")
        assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
        (parsed as AfirmaServletParseResult.Accepted).invocation.use { invocation ->
            val operation = NativeAfirmaOperation(invocation, clock = clock)
            try {
                assertEquals(original.size, operation.details.payloadBytes)
                assertEquals(original.size, checkNotNull(operation.pdfPreviews[0]).withBytes { it.size })
            } finally { operation.close() }
        }
        val engine = NativePadesEngine(clock = clock)
        val result = engine.sign(original, identity, SigningAlgorithm.SHA256_WITH_RSA)
        assertTrue(result.toString(), result is LocalSignatureResult.Success)
        (result as LocalSignatureResult.Success).signature.use { signature ->
            signature.withBytes { signed ->
                assertTrue(signed.size > original.size)
                assertTrue(engine.verify(signed, original, identity.certificate, SigningAlgorithm.SHA256_WITH_RSA))
                PDDocument.load(signed).use { assertEquals(8, it.numberOfPages) }
                val encoded = AfirmaIntermediateCipher.encode(signed, null)
                assertTrue(encoded.length < AFIRMA_MAX_RESULT_CHARS)
                val decoded = AfirmaIntermediateCipher.decode(encoded, null)
                try { assertArrayEquals(signed, decoded) } finally { decoded.fill(0) }
            }
        }
        original.fill(0)
    }

    @Test fun largePdfResultFitsLegacyAndAesCarriers() {
        val bytes = ByteArray(8 * MIB + 65_536) { (it % 251).toByte() }
        try {
            val legacy = AfirmaIntermediateCipher.encode(bytes, "12345678")
            assertTrue(legacy.length < AFIRMA_MAX_RESULT_CHARS)
            val legacyDecoded = AfirmaIntermediateCipher.decode(legacy, "12345678")
            try { assertArrayEquals(bytes, legacyDecoded) } finally { legacyDecoded.fill(0) }
            AfirmaAesParameters(ByteArray(32) { 1 }, ByteArray(16) { 2 }).use { cipher ->
                val encoded = cipher.encode(bytes)
                assertTrue(encoded.length < AFIRMA_MAX_RESULT_CHARS)
                val decoded = cipher.decode(encoded)
                try { assertArrayEquals(bytes, decoded) } finally { decoded.fill(0) }
            }
        } finally { bytes.fill(0) }
    }

    @Test fun oversizedPdfIsRejectedAndOtherFormatsRetainTheirExistingBudget() {
        for ((format, size) in listOf("PAdES" to 8 * MIB + 1, "CAdES" to 524_289)) {
            val bytes = ByteArray(size)
            "%PDF-1.4\n".toByteArray().copyInto(bytes)
            try {
                assertTrue(AfirmaServletInvocationParser.parse(request(bytes, format),
                    "https://portal.example/") is AfirmaServletParseResult.Invalid)
            } finally { bytes.fill(0) }
        }
    }

    @Test fun retrievedConfigurationCarriesLargePdfAndStillRejectsDeclarations() {
        val bytes = ByteArray(8 * MIB) { 65 }
        "%PDF-1.4\n".toByteArray().copyInto(bytes)
        val encoded = Base64.getUrlEncoder().encodeToString(bytes)
        val xml = ("<sign><e k=\"id\" v=\"largePdf\"/><e k=\"format\" v=\"PAdES\"/>" +
            "<e k=\"algorithm\" v=\"SHA256withRSA\"/>" +
            "<e k=\"stservlet\" v=\"https%3A%2F%2Fstorage.example%2Fstore\"/>" +
            "<e k=\"dat\" v=\"$encoded\"/></sign>").toByteArray()
        try {
            val parameters = AfirmaConfigurationXml.parse(xml)
            val parsed = AfirmaServletInvocationParser.parseRetrieved(
                parameters.operation, "https://portal.example", parameters.values)
            assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
            (parsed as AfirmaServletParseResult.Accepted).invocation.use { assertEquals(bytes.size, it.dataSize) }
            assertThrows(IllegalArgumentException::class.java) {
                AfirmaConfigurationXml.parse("<!DOCTYPE sign><sign/>".toByteArray())
            }
        } finally { bytes.fill(0); xml.fill(0) }
    }

    private fun request(pdf: ByteArray, format: String = "PAdES"): String =
        "afirma://sign?id=largePdf&format=$format&algorithm=SHA256withRSA" +
            "&stservlet=https%3A%2F%2Fstorage.example%2Fstore&dat=" +
            Base64.getUrlEncoder().encodeToString(pdf)

    private fun multipagePdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun put(value: String) { out.write(value.toByteArray(Charsets.US_ASCII)) }
        fun objectStart(number: Int) { offsets += out.size(); put("$number 0 obj\n") }
        put("%PDF-1.4\n")
        objectStart(1); put("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        objectStart(2)
        put("<< /Type /Pages /Kids [${(0..7).joinToString(" ") { "${3 + it * 2} 0 R" }}] /Count 8 >>\nendobj\n")
        val stream = ByteArray(MIB - 2048) { 32 }.also { it[0] = '%'.code.toByte(); it[it.lastIndex] = 10 }
        for (page in 0..7) {
            val pageId = 3 + page * 2
            objectStart(pageId)
            put("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents ${pageId + 1} 0 R >>\nendobj\n")
            objectStart(pageId + 1); put("<< /Length ${stream.size} >>\nstream\n")
            out.write(stream); put("endstream\nendobj\n")
        }
        val xref = out.size()
        put("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { put("%010d 00000 n \n".format(java.util.Locale.ROOT, it)) }
        put("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        stream.fill(0)
        return out.toByteArray()
    }

    private companion object { const val MIB = 1024 * 1024 }
}
