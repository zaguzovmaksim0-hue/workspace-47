package dev.junta.firmamobile.signing

import java.security.Security
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSignatureEngineTest {
    private val engine: LocalSignatureEngine = JcaLocalSignatureEngine()

    @Test
    fun rawRsaSignsSyntheticBytesAndNeverReadsPrivateKeyEncoding() {
        val fixture = nonExportableSyntheticIdentity()
        val content = "synthetic-triphase-pre".encodeToByteArray()

        val clearedCopies = mutableListOf<Boolean>()
        val observedEngine = JcaLocalSignatureEngine(
            signatureObserver = SensitiveSignatureCopyObserver(clearedCopies::add),
        )

        val result = observedEngine.sign(
            content,
            fixture.identity,
            SigningAlgorithm.SHA256_WITH_RSA,
        )

        val signature = (result as LocalSignatureResult.Success).signature
        signature.use { ownedSignature ->
            ownedSignature.withBytes { bytes ->
                assertTrue(verify("SHA256withRSA", content, bytes, fixture.identity))
            }
        }
        assertThrows(IllegalStateException::class.java) {
            signature.withBytes { error("closed signature remained accessible") }
        }
        assertEquals(0, fixture.encodedReads.get())
        assertEquals(listOf(true), clearedCopies)
    }

    @Test
    fun rawRsaSignatureFailsVerificationAfterOneByteMutation() {
        val identity = syntheticIdentity()
        val content = "challenge".encodeToByteArray()
        val result = engine.sign(content, identity, SigningAlgorithm.SHA256_WITH_RSA)
            as LocalSignatureResult.Success
        val changed = content.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        result.signature.use { signature ->
            signature.withBytes { bytes ->
                assertFalse(verify("SHA256withRSA", changed, bytes, identity))
            }
        }
        changed.fill(0)
    }

    @Test
    fun sha1IsUsedOnlyWhenCallerExplicitlySelectsTheLegacyAlgorithm() {
        val identity = syntheticIdentity()
        val content = "legacy-junta-pre".encodeToByteArray()
        val signMethods = LocalSignatureEngine::class.java.declaredMethods
            .filter { it.name == "sign" }
        assertEquals(1, signMethods.size)
        assertEquals(3, signMethods.single().parameterCount)

        val result = engine.sign(content, identity, SigningAlgorithm.SHA1_WITH_RSA)
            as LocalSignatureResult.Success

        result.signature.use { signature ->
            signature.withBytes { bytes ->
                assertTrue(verify("SHA1withRSA", content, bytes, identity))
            }
        }
    }

    @Test
    fun rawRsaInputAndOutputBoundsFailClosed() {
        val identity = syntheticIdentity()
        val oversized = ByteArray(JcaLocalSignatureEngine.MAX_INPUT_BYTES + 1)

        assertEquals(
            LocalSignatureError.INPUT_TOO_LARGE,
            (engine.sign(
                oversized,
                identity,
                SigningAlgorithm.SHA256_WITH_RSA,
            ) as LocalSignatureResult.Failure).error,
        )
        val tinyOutputEngine = JcaLocalSignatureEngine(maxOutputBytes = 1)
        assertEquals(
            LocalSignatureError.OUTPUT_TOO_LARGE,
            (tinyOutputEngine.sign(
                byteArrayOf(1),
                identity,
                SigningAlgorithm.SHA256_WITH_RSA,
            ) as LocalSignatureResult.Failure).error,
        )
        oversized.fill(0)
    }

    @Test
    fun rawSignerDoesNotInstallOrReorderGlobalSecurityProviders() {
        val before = Security.getProviders().map { it.name }
        val identity = syntheticIdentity()

        val result = engine.sign(
            "provider-order".encodeToByteArray(),
            identity,
            SigningAlgorithm.SHA256_WITH_RSA,
        ) as LocalSignatureResult.Success
        result.signature.close()

        assertEquals(before, Security.getProviders().map { it.name })
    }

    private fun verify(
        algorithm: String,
        content: ByteArray,
        signatureBytes: ByteArray,
        identity: dev.junta.firmamobile.certificate.UnlockedIdentity,
    ): Boolean = Signature.getInstance(algorithm).run {
        initVerify(identity.certificate.publicKey)
        update(content)
        verify(signatureBytes)
    }
}
