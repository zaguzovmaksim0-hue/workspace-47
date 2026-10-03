package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSha384TriphaseTest {
    private val algorithm = SigningAlgorithm.valueOf("SHA384_WITH_RSA")

    private fun parameters(payload: String, needPre: Boolean) = linkedMapOf(
        "PRE" to Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8)),
        "NEED_PRE" to needPre.toString(),
        "OPAQUE" to "opaque-metadata"
    )

    private fun session(firstNeedPre: Boolean = true) = NativeTriSession(
        "CAdES",
        listOf(
            NativeTriSign("id-1", "signature-1", parameters("opaque-payload-1", firstNeedPre)),
            NativeTriSign("id-2", null, parameters("opaque-payload-2", true))
        )
    )

    private fun snapshot(session: NativeTriSession) =
        session.signs.map { it.parameters.toMap() }

    @Test
    fun signsTwoPresWithSha384AndPreservesMetadataWithoutExportingKey() {
        val synthetic = nonExportableSyntheticIdentity()
        val input = session()
        val before = snapshot(input)

        val signed = runBlocking { NativeTriphaseCodec.sign(input, synthetic.identity, algorithm) {} }

        assertEquals(input.format, signed.format)
        assertEquals(
            input.signs.map { it.id to it.signatureId },
            signed.signs.map { it.id to it.signatureId }
        )
        signed.signs.forEachIndexed { index, sign ->
            val pre = Base64.getDecoder().decode(before[index].getValue("PRE"))
            val pk1 = Base64.getDecoder().decode(sign.parameters.getValue("PK1"))
            val publicKey = synthetic.identity.certificate.publicKey
            val sha384 = Signature.getInstance("SHA384withRSA")
            sha384.initVerify(publicKey)
            sha384.update(pre)
            assertTrue(sha384.verify(pk1))

            val sha256 = Signature.getInstance("SHA256withRSA")
            sha256.initVerify(publicKey)
            sha256.update(pre)
            assertTrue(!sha256.verify(pk1))
            assertEquals(before[index].getValue("PRE"), sign.parameters["PRE"])
        }
        assertEquals(before, snapshot(input))
        assertTrue(input.signs.all { "PK1" !in it.parameters })
        assertEquals(0, synthetic.encodedReads.get())
    }

    @Test
    fun checkpointFailurePropagatesWithoutMutatingInput() {
        val synthetic = nonExportableSyntheticIdentity()
        val input = session()
        val before = snapshot(input)
        val failure = IllegalStateException("opaque-checkpoint-failure")

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                NativeTriphaseCodec.sign(input, synthetic.identity, algorithm) { throw failure }
            }
        }

        assertTrue(thrown === failure)
        assertEquals(before, snapshot(input))
        assertTrue(input.signs.all { "PK1" !in it.parameters })
        assertEquals(0, synthetic.encodedReads.get())
    }

    @Test
    fun removesPreOnlyFromReturnedCopyWhenNeedPreIsFalse() {
        val synthetic = nonExportableSyntheticIdentity()
        val input = session(firstNeedPre = false)
        val before = snapshot(input)

        val signed = runBlocking { NativeTriphaseCodec.sign(input, synthetic.identity, algorithm) {} }

        assertEquals(before, snapshot(input))
        assertTrue(input.signs.all { "PRE" in it.parameters && "PK1" !in it.parameters })
        assertTrue("PRE" !in signed.signs[0].parameters)
        assertEquals(before[1].getValue("PRE"), signed.signs[1].parameters["PRE"])
        assertTrue(signed.signs.all { "PK1" in it.parameters })
        assertEquals(0, synthetic.encodedReads.get())
    }
}
