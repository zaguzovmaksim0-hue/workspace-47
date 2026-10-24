package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.LocalSignatureError
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.freshSyntheticIdentity
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class NativeXadesEngineRegressionTest {
    @Test
    fun emptyChainFallsBackToLeafAndEightCertificatesRemainAccepted() {
        val identity = freshSyntheticIdentity()
        val engine = NativeXadesEngine(generalClockFor(identity))
        for (size in listOf(0, 1, 8)) {
            val candidate = identity.withPrivateKey {
                UnlockedIdentity(it, identity.certificate, List(size) { identity.certificate }, identity.summary)
            }
            val result = engine.sign(PAYLOAD, candidate, ALGORITHM, OPTIONS)
            assertTrue("Chain size $size", result is LocalSignatureResult.Success)
            (result as LocalSignatureResult.Success).signature.use { signature ->
                signature.withBytes { xml ->
                    val document = NativeSigningXml.parse(xml)
                    assertEquals(maxOf(1, size), document.getElementsByTagNameNS(DS, "X509Certificate").length)
                    assertTrue(engine.verify(xml, PAYLOAD, identity.certificate, ALGORITHM, OPTIONS))
                }
            }
        }
    }

    @Test
    fun ninthCertificateAndWrongFirstCertificateRemainRejected() {
        val identity = freshSyntheticIdentity()
        val other = freshSyntheticIdentity()
        val engine = NativeXadesEngine(generalClockFor(identity))
        for (chain in listOf(List(9) { identity.certificate }, listOf(other.certificate, identity.certificate))) {
            val candidate = identity.withPrivateKey {
                UnlockedIdentity(it, identity.certificate, chain, identity.summary)
            }
            val result = engine.sign(PAYLOAD, candidate, ALGORITHM, OPTIONS)
            assertTrue(result is LocalSignatureResult.Failure)
            assertEquals(LocalSignatureError.SIGNATURE_FAILED, (result as LocalSignatureResult.Failure).error)
        }
    }

    @Test
    fun algorithmUrisReferenceOrderAndDigestWidthsRemainUnchanged() {
        val identity = freshSyntheticIdentity()
        val engine = NativeXadesEngine(generalClockFor(identity))
        val algorithms = listOf(
            Triple(SigningAlgorithm.SHA1_WITH_RSA, DS + "sha1", DS + "rsa-sha1"),
            Triple(SigningAlgorithm.SHA256_WITH_RSA, SHA256_URI, MORE + "rsa-sha256"),
            Triple(SigningAlgorithm.SHA384_WITH_RSA, MORE + "sha384", MORE + "rsa-sha384"),
            Triple(SigningAlgorithm.SHA512_WITH_RSA, ENC + "sha512", MORE + "rsa-sha512")
        )
        for ((algorithm, digestUri, signatureUri) in algorithms) {
            val result = engine.sign(PAYLOAD, identity, algorithm, OPTIONS)
            assertTrue(result is LocalSignatureResult.Success)
            (result as LocalSignatureResult.Success).signature.use { signature ->
                signature.withBytes { xml ->
                    val document = NativeSigningXml.parse(xml)
                    val info = first(document, DS, "SignedInfo")
                    assertEquals(
                        listOf("CanonicalizationMethod", "SignatureMethod", "Reference", "Reference", "Reference"),
                        NativeSigningXml.children(info).map { it.localName }
                    )
                    assertEquals(NativeSigningXml.C14N, first(document, DS, "CanonicalizationMethod").getAttribute("Algorithm"))
                    assertEquals(signatureUri, first(document, DS, "SignatureMethod").getAttribute("Algorithm"))
                    val references = NativeSigningXml.children(info).drop(2)
                    val content = document.getElementsByTagName("CONTENT").item(0) as Element
                    assertEquals("#" + content.getAttribute("Id"), references[0].getAttribute("URI"))
                    assertEquals("#" + first(document, XA, "SignedProperties").getAttribute("Id"), references[1].getAttribute("URI"))
                    assertEquals("http://uri.etsi.org/01903#SignedProperties", references[1].getAttribute("Type"))
                    assertEquals("#" + first(document, DS, "KeyInfo").getAttribute("Id"), references[2].getAttribute("URI"))
                    for (reference in references) {
                        assertEquals(digestUri, NativeSigningXml.one(reference, DS, "DigestMethod").getAttribute("Algorithm"))
                    }
                    val digestName = when (algorithm) {
                        SigningAlgorithm.SHA1_WITH_RSA -> "SHA-1"
                        SigningAlgorithm.SHA256_WITH_RSA -> "SHA-256"
                        SigningAlgorithm.SHA384_WITH_RSA -> "SHA-384"
                        SigningAlgorithm.SHA512_WITH_RSA -> "SHA-512"
                    }
                    assertTrue(MessageDigest.isEqual(
                        MessageDigest.getInstance(digestName).digest(PAYLOAD),
                        Base64.getDecoder().decode(NativeSigningXml.one(references[0], DS, "DigestValue").textContent)
                    ))
                    val certificateDigest = first(document, XA, "CertDigest")
                    assertEquals(SHA256_URI, NativeSigningXml.one(certificateDigest, DS, "DigestMethod").getAttribute("Algorithm"))
                    assertEquals(32, Base64.getDecoder().decode(NativeSigningXml.one(certificateDigest, DS, "DigestValue").textContent).size)
                    assertTrue(engine.verify(xml, PAYLOAD, identity.certificate, algorithm, OPTIONS))
                }
            }
        }
    }

    @Test
    fun verificationStillFailsClosedForStructuralAndTransformChanges() {
        val identity = freshSyntheticIdentity()
        val engine = NativeXadesEngine(generalClockFor(identity))
        val result = engine.sign(PAYLOAD, identity, ALGORITHM, OPTIONS) as LocalSignatureResult.Success
        val mutations: List<(Document) -> Unit> = listOf(
            { first(it, DS, "CanonicalizationMethod").setAttribute("Algorithm", "urn:unsupported") },
            { first(it, DS, "Transform").setAttribute("Algorithm", "urn:unsupported") },
            { doc ->
                val info = first(doc, DS, "SignedInfo")
                info.appendChild(first(doc, DS, "Reference").cloneNode(true))
            },
            { first(it, DS, "Reference").setAttribute("URI", "https://invalid.example/not-dereferenced") },
            { first(it, DS, "SignatureValue").textContent = Base64.getEncoder().encodeToString(ByteArray(16_385)) },
            { first(it, DS, "X509Certificate").textContent = Base64.getEncoder().encodeToString(ByteArray(65_537)) },
            { first(it, DS, "DigestValue").textContent = Base64.getEncoder().encodeToString(ByteArray(65)) },
            { doc ->
                NativeSigningXml.one(first(doc, XA, "CertDigest"), DS, "DigestValue").textContent =
                    Base64.getEncoder().encodeToString(ByteArray(33))
            },
            { first(it, XA, "Description").textContent = "changed" }
        )
        result.signature.use { signature ->
            signature.withBytes { xml ->
                assertTrue(engine.verify(xml, PAYLOAD, identity.certificate, ALGORITHM, OPTIONS))
                for ((index, mutate) in mutations.withIndex()) {
                    val document = NativeSigningXml.parse(xml)
                    mutate(document)
                    assertFalse("Mutation $index", engine.verify(
                        NativeSigningXml.serialize(document), PAYLOAD, identity.certificate, ALGORITHM, OPTIONS
                    ))
                }
            }
        }
    }

    @Test
    fun payloadLimitRemainsInclusiveAndOversizedInputsKeepTheirError() {
        val identity = freshSyntheticIdentity()
        val engine = NativeXadesEngine(generalClockFor(identity))
        val payload = ByteArray(524_288) { 42 }
        val result = engine.sign(payload, identity, ALGORITHM, OPTIONS)
        assertTrue(result is LocalSignatureResult.Success)
        (result as LocalSignatureResult.Success).signature.use { signature ->
            signature.withBytes { xml ->
                assertTrue(engine.verify(xml, payload, identity.certificate, ALGORITHM, OPTIONS))
                assertFalse(engine.verify(xml, ByteArray(524_289), identity.certificate, ALGORITHM, OPTIONS))
            }
        }
        for (invalid in listOf(byteArrayOf(), ByteArray(524_289))) {
            val failure = engine.sign(invalid, identity, ALGORITHM, OPTIONS)
            assertTrue(failure is LocalSignatureResult.Failure)
            assertEquals(LocalSignatureError.INPUT_TOO_LARGE, (failure as LocalSignatureResult.Failure).error)
        }
        assertFalse(engine.verify(ByteArray(2_097_153), PAYLOAD, identity.certificate, ALGORITHM, OPTIONS))
    }

    private fun first(document: Document, namespace: String, name: String) =
        document.getElementsByTagNameNS(namespace, name).item(0) as Element

    private companion object {
        const val DS = "http://www.w3.org/2000/09/xmldsig#"
        const val XA = "http://uri.etsi.org/01903/v1.3.2#"
        const val ENC = "http://www.w3.org/2001/04/xmlenc#"
        const val MORE = "http://www.w3.org/2001/04/xmldsig-more#"
        const val SHA256_URI = ENC + "sha256"
        val PAYLOAD = "Synthetic regression document".toByteArray()
        val ALGORITHM = SigningAlgorithm.SHA256_WITH_RSA
        val OPTIONS = NativeXadesOptions(NativeXadesPackaging.DETACHED, "text/plain", "Synthetic document")
    }
}
