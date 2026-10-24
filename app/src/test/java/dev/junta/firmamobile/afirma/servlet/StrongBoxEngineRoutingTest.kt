package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.KeystorePlacement
import dev.junta.firmamobile.signing.BouncyCastleCadesSigner
import dev.junta.firmamobile.signing.JcaLocalSignatureEngine
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.OpaqueRsaFixture
import dev.junta.firmamobile.signing.SigningAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A StrongBox-style handle accepts only SHA256withRSA and NONEwithRSA
 * (ADR 0005). Engines must still produce standard SHAxwithRSA signatures. */
class StrongBoxEngineRoutingTest {
    @Test fun rawEngineSignsEveryDigestWithAStrongBoxKey() = strongBox { f ->
        for (algorithm in SigningAlgorithm.entries) {
            val strongBox = JcaLocalSignatureEngine().sign(DATA, f.identity, algorithm).bytes()
            val software = JcaLocalSignatureEngine().sign(DATA, f.softwareIdentity, algorithm).bytes()
            assertArrayEquals(algorithm.name, software, strongBox)
        }
        assertEquals(setOf("SHA256withRSA", "NONEwithRSA"), f.algorithms.toSet())
        assertEquals(0, f.encodingReads.get())
    }

    @Test fun nativeCadesMatchesTheSoftwareKeyByteForByte() = strongBox { f ->
        val engine = NativeCadesEngine(clock = generalClockFor(f.identity))
        for (algorithm in SigningAlgorithm.entries) for (detached in listOf(false, true)) {
            val strongBox = engine.sign(DATA, f.identity, algorithm, detached).bytes()
            assertTrue(engine.verify(strongBox, DATA, f.identity.certificate, algorithm, detached))
            val software = engine.sign(DATA, f.softwareIdentity, algorithm, detached).bytes()
            assertArrayEquals("${algorithm.name}/detached=$detached", software, strongBox)
        }
        assertEquals(8, f.signatures.get())
    }

    @Test fun reviewedCadesSignerMatchesTheSoftwareKeyByteForByte() = strongBox { f ->
        val signer = BouncyCastleCadesSigner(clock = generalClockFor(f.identity))
        for (algorithm in SigningAlgorithm.entries) {
            val strongBox = signer.signDetached(DATA, f.identity, algorithm).bytes()
            val software = signer.signDetached(DATA, f.softwareIdentity, algorithm).bytes()
            assertArrayEquals(algorithm.name, software, strongBox)
        }
    }

    @Test fun padesSignsSha512WithAStrongBoxKey() = strongBox { f ->
        val pdf = nativePadesFixture()
        try {
            val result = NativePadesEngine(clock = generalClockFor(f.identity))
                .sign(pdf, f.identity, SigningAlgorithm.SHA512_WITH_RSA, NativePadesOptions())
            assertTrue(result.toString(), result is LocalSignatureResult.Success)
            (result as LocalSignatureResult.Success).signature.close()
            assertEquals(listOf("NONEwithRSA"), f.algorithms)
        } finally {
            pdf.fill(0)
        }
    }

    @Test fun withoutThePlacementTheSameHandleRefusesSha512NegativeControl() =
        OpaqueRsaFixture.use(OpaqueRsaFixture.STRONGBOX_SIGNATURES, null) { f ->
            val algorithm = SigningAlgorithm.SHA512_WITH_RSA
            assertTrue(JcaLocalSignatureEngine().sign(DATA, f.identity, algorithm) is LocalSignatureResult.Failure)
            assertTrue(
                NativeCadesEngine(clock = generalClockFor(f.identity))
                    .sign(DATA, f.identity, algorithm, true) is LocalSignatureResult.Failure,
            )
            assertTrue(
                BouncyCastleCadesSigner(clock = generalClockFor(f.identity))
                    .signDetached(DATA, f.identity, algorithm) is LocalSignatureResult.Failure,
            )
            assertEquals(0, f.signatures.get())
        }

    @Test fun trustedEnvironmentKeysSignNatively() =
        OpaqueRsaFixture.use(null, KeystorePlacement.DEFAULT) { f ->
            for (algorithm in SigningAlgorithm.entries) {
                JcaLocalSignatureEngine().sign(DATA, f.identity, algorithm).bytes()
            }
            assertTrue("NONEwithRSA" !in f.algorithms)
        }

    private fun <T> strongBox(block: (OpaqueRsaFixture) -> T): T =
        OpaqueRsaFixture.use(OpaqueRsaFixture.STRONGBOX_SIGNATURES, KeystorePlacement.STRONGBOX, block)

    private fun LocalSignatureResult.bytes(): ByteArray {
        assertTrue(toString(), this is LocalSignatureResult.Success)
        return (this as LocalSignatureResult.Success).signature.use { it.withBytes { bytes -> bytes.copyOf() } }
    }

    companion object { private val DATA = "Synthetic StrongBox routing document".toByteArray() }
}
