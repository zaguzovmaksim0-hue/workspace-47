package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.BouncyCastleCadesSigner
import dev.junta.firmamobile.signing.JcaLocalSignatureEngine
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.OpaqueRsaFixture
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.jcaName
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real CMS/PDF engines with a synthetic JCA provider holding an opaque key.
 * No Activity, emulator, device key, personal identity or E2E navigation. */
class NativeOpaqueRsaKeyTest {
    @Test fun opaqueProviderCanAlreadySignThroughTheRawEnginePositiveControl() = OpaqueRsaFixture.use { f ->
        val result = JcaLocalSignatureEngine().sign(DATA, f.identity, SigningAlgorithm.SHA384_WITH_RSA)
        assertTrue(result.toString(), result is LocalSignatureResult.Success)
        (result as LocalSignatureResult.Success).signature.close()
        assertEquals(1, f.signatures.get()); assertEquals(0, f.encodingReads.get())
    }

    @Test fun commonCadesUsesTheOpaqueKeysProviderForEveryDigestAndPackaging() = OpaqueRsaFixture.use { f ->
        val engine = NativeCadesEngine(clock = generalClockFor(f.identity))
        for (algorithm in SigningAlgorithm.entries) for (detached in listOf(false, true)) {
            val result = engine.sign(DATA, f.identity, algorithm, detached)
            assertTrue("${algorithm.name}/detached=$detached: $result", result is LocalSignatureResult.Success)
            (result as LocalSignatureResult.Success).signature.use { signature ->
                signature.withBytes { bytes ->
                    assertTrue(engine.verify(bytes, DATA, f.identity.certificate, algorithm, detached))
                    assertFalse(engine.verify(bytes, "changed".toByteArray(), f.identity.certificate, algorithm, detached))
                }
            }
        }
        assertEquals(8, f.signatures.get()); assertEquals(8, f.initializations.get())
        assertEquals(0, f.encodingReads.get()); assertEquals(0, f.formatReads.get())
    }

    @Test fun reviewedCadesSignerAlsoKeepsTheOpaquePrivateKeyInsideItsProvider() = OpaqueRsaFixture.use { f ->
        val signer = BouncyCastleCadesSigner(clock = generalClockFor(f.identity))
        val verifier = NativeCadesEngine(clock = generalClockFor(f.identity))
        for (algorithm in SigningAlgorithm.entries) {
            val result = signer.signDetached(DATA, f.identity, algorithm)
            assertTrue(result.toString(), result is LocalSignatureResult.Success)
            (result as LocalSignatureResult.Success).signature.use { signed ->
                signed.withBytes { assertTrue(verifier.verify(it, DATA, f.identity.certificate, algorithm, true)) }
            }
        }
        assertEquals(4, f.signatures.get()); assertEquals(0, f.encodingReads.get())
    }

    @Test fun padesAndItsIncrementalSignatureCanUseTheSameOpaqueKey() = OpaqueRsaFixture.use { f ->
        val engine = NativePadesEngine(clock = generalClockFor(f.identity)); val first = nativePadesFixture()
        val result = engine.sign(first, f.identity, SigningAlgorithm.SHA256_WITH_RSA, NativePadesOptions())
        assertTrue(result.toString(), result is LocalSignatureResult.Success)
        val signedPdf = (result as LocalSignatureResult.Success).signature.use { it.withBytes { data -> data.copyOf() } }
        try {
            val again = engine.sign(signedPdf, f.identity, SigningAlgorithm.SHA384_WITH_RSA, NativePadesOptions(), true)
            assertTrue(again.toString(), again is LocalSignatureResult.Success)
            (again as LocalSignatureResult.Success).signature.close()
            assertEquals(2, f.signatures.get()); assertEquals(0, f.encodingReads.get())
        } finally { first.fill(0); signedPdf.fill(0) }
    }

    @Test fun providerRefusalDoesNotTriggerAnotherSignatureAttemptOrExport() = OpaqueRsaFixture.use { f ->
        f.failAtSign = true
        val result = NativeCadesEngine(clock = generalClockFor(f.identity)).sign(DATA, f.identity, SigningAlgorithm.SHA256_WITH_RSA, true)
        assertTrue(result is LocalSignatureResult.Failure)
        assertEquals("Exactly one provider signing call, no retry", 1, f.signatures.get())
        assertEquals(1, f.initializations.get()); assertEquals(0, f.encodingReads.get())
    }

    @Test fun corruptProviderOutputIsNotReturnedAsASuccessfulCms() = OpaqueRsaFixture.use { f ->
        f.corruptSignature = true
        val result = NativeCadesEngine(clock = generalClockFor(f.identity)).sign(DATA, f.identity, SigningAlgorithm.SHA512_WITH_RSA, true)
        assertTrue(result is LocalSignatureResult.Failure)
        assertEquals(1, f.signatures.get()); assertEquals(0, f.encodingReads.get())
    }

    @Test fun theRealNativeOperationStillRequiresExplicitFinalUploadAuthorization() = OpaqueRsaFixture.use { f ->
        runBlocking {
            var authorizations = 0; var stores = 0
            val invocation = acceptedGeneral("sign", mapOf("id" to "Opaque123", "stservlet" to "https://storage.synthetic.example/put",
                "format" to "CAdES", "algorithm" to "SHA384withRSA", "dat" to Base64.getUrlEncoder().encodeToString(DATA)))
            val operation = NativeAfirmaOperation(invocation, AfirmaResultTransport { _, _, wire ->
                assertEquals(1, authorizations); assertEquals(1, f.signatures.get()); stores++
                assertEquals(2, wire.split('|').size)
                AfirmaDeliveryResult.ACKNOWLEDGED
            }, generalClockFor(f.identity))
            try {
                assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(f.identity) { authorizations++ })
                assertEquals(1, authorizations); assertEquals(1, stores)
                assertThrows(IllegalStateException::class.java) { runBlocking { operation.execute(f.identity) { error("No replay") } } }
                assertEquals(1, f.signatures.get()); assertEquals(0, f.encodingReads.get())
            } finally { operation.close() }
        }
    }

    @Test fun sizeRejectionDoesNotInitializeOrInvokeTheOpaqueProvider() = OpaqueRsaFixture.use { f ->
        val result = NativeCadesEngine(maxInputBytes = 3, clock = generalClockFor(f.identity)).sign(DATA, f.identity, SigningAlgorithm.SHA256_WITH_RSA, true)
        assertTrue(result is LocalSignatureResult.Failure)
        assertEquals(0, f.initializations.get()); assertEquals(0, f.signatures.get()); assertEquals(0, f.encodingReads.get())
    }
    companion object { private val DATA = "Synthetic opaque-key test document".toByteArray() }
}
