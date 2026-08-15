package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.VeaMultiModeBridgeRequest
import dev.junta.firmamobile.browser.VeaMultiModeReplyChannel
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class VeaMultiModeSigningAdapterTest {
    private val identity = syntheticIdentity()
    private val prehashedEngine = JcaPrehashedRsaSignatureEngine()
    private val adapter = VeaMultiModeSigningAdapter(prehashedEngine)

    @Test
    fun signsSingleDocumentAndDeliversBase64SignatureAndCert() {
        val message = "VEA Report Justificante Content".encodeToByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(message)
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        val reqId = UUID.randomUUID()
        val docId = UUID.randomUUID()

        val request = VeaMultiModeBridgeRequest(
            requestId = reqId,
            documentId = docId,
            operationArray = listOf("sign"),
            dataArray = listOf(hashHex),
            originalDataArray = null,
            arrayLength = 1,
            algorithm = "SHA256withRSA",
            format = "CADES",
            extraProperties = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
            hashAlgorithm = PrecalculatedHashAlgorithm.SHA256,
            hashes = listOf(hash),
            profileId = ProfileId("junta-andalucia-sede"),
            sourceOrigin = TrustedOrigin("https", "veaja.cloud.juntadeandalucia.es", 443),
            navigationEpoch = 100L,
        )

        var deliveredSignature: String? = null
        var deliveredCert: String? = null
        val sink = object : VeaMultiModeReplySink {
            override val requestId: UUID = reqId
            override fun success(signaturesB64: String, certificateB64: String): Boolean {
                deliveredSignature = signaturesB64
                deliveredCert = certificateB64
                return true
            }
            override fun failure(code: SigningErrorCode): Boolean = false
            override fun abandon(): Boolean = false
        }

        val result = adapter.execute(request, identity, sink)
        assertTrue(result)
        assertTrue(deliveredSignature != null)
        assertTrue(deliveredCert != null)

        val sigBytes = Base64.getDecoder().decode(deliveredSignature)
        val certBytes = Base64.getDecoder().decode(deliveredCert)

        assertEquals(Base64.getEncoder().encodeToString(identity.certificate.encoded), deliveredCert)

        // Verify signature with standard SHA256withRSA
        val verified = Signature.getInstance("SHA256withRSA").run {
            initVerify(identity.certificate.publicKey)
            update(message)
            verify(sigBytes)
        }
        assertTrue("Delivered signature must verify against message", verified)
    }

    @Test
    fun signsMultipleDocumentsAndSeparatesWithColon() {
        val msg1 = "Document One".encodeToByteArray()
        val msg2 = "Document Two".encodeToByteArray()
        val hash1 = MessageDigest.getInstance("SHA-256").digest(msg1)
        val hash2 = MessageDigest.getInstance("SHA-256").digest(msg2)
        val reqId = UUID.randomUUID()
        val docId = UUID.randomUUID()

        val request = VeaMultiModeBridgeRequest(
            requestId = reqId,
            documentId = docId,
            operationArray = listOf("sign", "sign"),
            dataArray = listOf(hash1.joinToString("") { "%02x".format(it) }, hash2.joinToString("") { "%02x".format(it) }),
            originalDataArray = null,
            arrayLength = 2,
            algorithm = "SHA256withRSA",
            format = "CADES",
            extraProperties = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
            hashAlgorithm = PrecalculatedHashAlgorithm.SHA256,
            hashes = listOf(hash1, hash2),
            profileId = ProfileId("junta-andalucia-sede"),
            sourceOrigin = TrustedOrigin("https", "veaja.cloud.juntadeandalucia.es", 443),
            navigationEpoch = 100L,
        )

        var deliveredSignature: String? = null
        val sink = object : VeaMultiModeReplySink {
            override val requestId: UUID = reqId
            override fun success(signaturesB64: String, certificateB64: String): Boolean {
                deliveredSignature = signaturesB64
                return true
            }
            override fun failure(code: SigningErrorCode): Boolean = false
            override fun abandon(): Boolean = false
        }

        val result = adapter.execute(request, identity, sink)
        assertTrue(result)
        assertTrue(deliveredSignature != null)

        val split = deliveredSignature!!.split(":")
        assertEquals(2, split.size)

        val sig1Bytes = Base64.getDecoder().decode(split[0])
        val sig2Bytes = Base64.getDecoder().decode(split[1])

        val verified1 = Signature.getInstance("SHA256withRSA").run {
            initVerify(identity.certificate.publicKey)
            update(msg1)
            verify(sig1Bytes)
        }
        val verified2 = Signature.getInstance("SHA256withRSA").run {
            initVerify(identity.certificate.publicKey)
            update(msg2)
            verify(sig2Bytes)
        }
        assertTrue("Signature 1 must verify", verified1)
        assertTrue("Signature 2 must verify", verified2)
    }
}
