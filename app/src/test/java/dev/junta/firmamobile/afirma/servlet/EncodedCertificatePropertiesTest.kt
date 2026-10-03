package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class EncodedCertificatePropertiesTest {
    @Test fun aliasesAndStandardOrUrlSafeEncodingKeepTheExactRequestedCertificate() {
        val identity = nonExportableSyntheticIdentity().identity
        for (name in listOf("filter", "filters")) for (encode in listOf(Base64.getEncoder(), Base64.getUrlEncoder())) {
            val property = "$name=encodedcert:${encode.encodeToString(identity.certificate.encoded)}\nheadless=TrUe\nmode=explicit"
            val value = accepted(parse(property))
            value.use { assertTrue(it.requiresExactCertificate); assertTrue(it.matchesCertificate(identity.certificate)) }
        }
    }

    @Test fun malformedTruncatedConcatenatedOrPemCertificatesDoNotBecomeAConstraint() {
        val bytes = nonExportableSyntheticIdentity().identity.certificate.encoded
        val invalid = listOf(bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(0), bytes + bytes,
            "not a certificate".toByteArray(), ("-----BEGIN CERTIFICATE-----\n" + Base64.getEncoder().encodeToString(bytes) + "\n-----END CERTIFICATE-----").toByteArray())
        for (value in invalid) rejected(parse("filters=encodedcert:${Base64.getEncoder().encodeToString(value)}"))
        rejected(parse("filters=encodedcert:%%%")); rejected(parse("filters=encodedcert:"))
    }

    @Test fun ambiguousAndUnimplementedFilterRequirementsAreNotSilentlyDiscarded() {
        val cert = Base64.getEncoder().encodeToString(nonExportableSyntheticIdentity().identity.certificate.encoded)
        for (property in listOf("filters=subject.contains:Somebody", "filters=", "filter=encodedcert:$cert\nfilters=encodedcert:$cert",
            "filters.1=encodedcert:$cert", "filters=encodedcert:$cert\nmandatoryCertSelection=false")) rejected(parse(property))
        // A known client constraint does not excuse an unknown signing property.
        rejected(parse("filters=encodedcert:$cert\nunknownSigningPolicy=ignored"))
    }

    @Test fun headlessIsStrictMetadataAndDoesNotCreateOrImplyACertificateBinding() {
        for (value in listOf("true", "false", "TRUE", "False")) accepted(parse("headless=$value\nmode=explicit")).use {
            assertFalse(it.requiresExactCertificate)
        }
        for (value in listOf("", "yes", "1", "false true")) rejected(parse("headless=$value"))
    }

    @Test fun retrievedPropertiesAndRequestLifetimePreserveBindingWithoutLeakingIt() {
        val key = nonExportableSyntheticIdentity(); val another = freshConstraintIdentity()
        val encoded = Base64.getEncoder().encodeToString(key.identity.certificate.encoded)
        val values = generalFields("abc".toByteArray(), "CAdES") + ("properties" to url64("filters=encodedcert:$encoded\nheadless=true".toByteArray()))
        val before = values.toMap()
        val invocation = accepted(AfirmaServletInvocationParser.parseRetrieved(AfirmaServletOperation.SIGN, "https://page.example", values))
        assertEquals(before, values); assertTrue(invocation.matchesCertificate(key.identity.certificate))
        assertFalse(invocation.matchesCertificate(another.identity.certificate)); assertFalse(invocation.toString().contains(encoded))
        val original = checkNotNull(NativeCertificateConstraint.parse("encodedcert:$encoded")); val copy = original.copy()
        original.close(); assertFalse(original.matches(key.identity.certificate)); assertTrue(copy.matches(key.identity.certificate))
        assertFalse(copy.toString().contains(encoded)); copy.close(); invocation.close()
        assertFalse(invocation.matchesCertificate(key.identity.certificate))
        accepted(parse("mode=explicit")).use { assertTrue(it.matchesCertificate(another.identity.certificate)) }
        assertEquals(0, key.encodedReads.get()); assertEquals(0, another.encodedReads.get())
    }

    private fun parse(properties: String): AfirmaServletParseResult = AfirmaServletInvocationParser.parse(
        "afirma://sign?id=Filter123&stservlet=https%3A%2F%2Fstorage.example%2Fput&format=CAdES&algorithm=SHA256withRSA&dat=YWJj&properties=" +
            URLEncoder.encode(url64(properties.toByteArray()), "UTF-8"), "https://page.example/")
    private fun accepted(result: AfirmaServletParseResult): AfirmaServletInvocation {
        assertTrue(result.toString(), result is AfirmaServletParseResult.Accepted)
        return (result as AfirmaServletParseResult.Accepted).invocation
    }
    private fun rejected(result: AfirmaServletParseResult) {
        if (result is AfirmaServletParseResult.Accepted) { result.invocation.close(); fail("Unexpected certificate constraint acceptance") }
        assertTrue(result is AfirmaServletParseResult.Invalid || result is AfirmaServletParseResult.Unsupported)
    }
}
