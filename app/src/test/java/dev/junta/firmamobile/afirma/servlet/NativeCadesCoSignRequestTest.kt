package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** Real protocol admission with generated CMS, no browser or live service. */
class NativeCadesCoSignRequestTest {
    @Test fun nativeCosignAcceptsAnAttachedCadesWithoutAConfiguredServer() {
        val signature = NativeCadesCoSignFixtures.signed(false)
        try {
            val parsed = AfirmaServletInvocationParser.parse(request(signature), SOURCE)
            assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
            (parsed as AfirmaServletParseResult.Accepted).invocation.use {
                assertEquals(AfirmaServletOperation.COSIGN, it.operation)
                assertNull(it.remoteOptions); assertNull(it.padesOptions)
                assertFalse(it.detached)
                assertArrayEquals(signature, it.payloadCopy())
            }
        } finally { signature.fill(0) }
    }

    @Test fun nativeCosignAcceptsAnExternalContentSignatureWithoutPretendingToHaveTheOriginalFile() {
        val signature = NativeCadesCoSignFixtures.signed(true)
        try {
            val parsed = AfirmaServletInvocationParser.parse(request(signature), SOURCE)
            assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
            (parsed as AfirmaServletParseResult.Accepted).invocation.use {
                assertTrue(it.detached)
                assertNull(it.remoteOptions)
                assertArrayEquals(signature, it.payloadCopy())
            }
        } finally { signature.fill(0) }
    }

    @Test fun missingSignatureAndUnsupportedLocalCounterSignatureDoNotBecomeOrdinarySignRequests() {
        assertFalse(AfirmaServletInvocationParser.parse(request("not CMS".toByteArray()), SOURCE) is AfirmaServletParseResult.Accepted)
        val signature = NativeCadesCoSignFixtures.signed(false)
        try {
            assertTrue(AfirmaServletInvocationParser.parse(request(signature).replace("afirma://cosign", "afirma://countersign"), SOURCE) is AfirmaServletParseResult.Unsupported)
        } finally { signature.fill(0) }
    }
    companion object {
        const val SOURCE = "https://portal.synthetic.example"
        fun request(signature: ByteArray, properties: String? = null): String {
            val fields = linkedMapOf("id" to "CoSign123", "stservlet" to "https://storage.synthetic.example/put",
                "format" to "CAdES", "algorithm" to "SHA256withRSA", "dat" to Base64.getUrlEncoder().encodeToString(signature))
            properties?.let { fields["properties"] = Base64.getUrlEncoder().encodeToString(it.toByteArray()) }
            return "afirma://cosign?" + fields.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
        }
    }
}

internal object NativeCadesCoSignFixtures {
    val first by lazy { freshConstraintIdentity() }
    val second by lazy { freshConstraintIdentity() }
    val data get() = "Synthetic jointly signed document; no administrative submission.".toByteArray()
    val clock get() = generalClockFor(first.identity)
    fun signed(detached: Boolean, algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA): ByteArray {
        val input = data
        try {
            val result = NativeCadesEngine(clock = clock).sign(input, first.identity, algorithm, detached)
            check(result is LocalSignatureResult.Success) { result.toString() }
            return result.signature.use { it.withBytes(ByteArray::copyOf) }
        } finally { input.fill(0) }
    }
}
