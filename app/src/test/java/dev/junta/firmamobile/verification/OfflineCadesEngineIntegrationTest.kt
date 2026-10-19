package dev.junta.firmamobile.verification

import dev.junta.firmamobile.signing.SoftwareEcCadesEngine
import dev.junta.firmamobile.signing.SoftwareEcCadesResult
import dev.junta.firmamobile.signing.SoftwareEcSignatureAlgorithm
import dev.junta.firmamobile.signing.SoftwareEcSignatureRequest
import java.security.Security
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineCadesEngineIntegrationTest {
    @Test
    fun verifiesRealDetachedSoftwareOutputOnBothCurves() {
        val providersBefore = Security.getProviders().map { it.name }
        for (algorithm in SoftwareEcSignatureAlgorithm.entries) {
            val material = OfflineCmsFixtures.signer(
                signatureAlgorithm = algorithm.jcaName,
                keyAlgorithm = "EC",
                curve = algorithm.namedCurve,
            )
            val input = CONTENT.copyOf()
            val result = SoftwareEcCadesEngine().signDetached(
                input,
                SoftwareEcSignatureRequest(material.keyPair.private, material.certificate, algorithm),
            )
            check(result is SoftwareEcCadesResult.Success) { "Software CAdES fixture failed: $result" }
            val cms = result.cmsDer
            val originalCms = cms.copyOf()
            val changed = input.copyOf()
            changed[0] = (changed[0].toInt() xor 1).toByte()

            OfflineCmsAssertions.assertRejected(OfflineCmsStatus.DETACHED_CONTENT_REQUIRED, cms)
            OfflineCmsAssertions.assertVerified(cms, input)
            OfflineCmsAssertions.assertRejected(OfflineCmsStatus.INVALID, cms, changed)
            OfflineCmsAssertions.assertRejected(
                OfflineCmsStatus.INVALID,
                OfflineCmsSignerInfoFixtures.tamperSignature(cms),
                input,
            )
            assertArrayEquals(CONTENT, input)
            assertArrayEquals(originalCms, cms)
        }
        assertEquals(providersBefore, Security.getProviders().map { it.name })
    }

    private companion object {
        val CONTENT = "real detached software EC payload".toByteArray(Charsets.UTF_8)
    }
}
