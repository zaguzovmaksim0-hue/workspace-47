package dev.junta.firmamobile.signing

import dev.junta.firmamobile.certificate.TestCertificateFactory
import java.security.Security
import java.time.Clock
import java.time.ZoneOffset
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CadesSignerTest {
    private val signer: CadesSigner = BouncyCastleCadesSigner(
        clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC),
    )

    @Test
    fun detachedCadesVerifiesAgainstSyntheticContentAndCertificate() {
        val fixture = nonExportableSyntheticIdentity()
        val content = "synthetic-cades-document".encodeToByteArray()

        val result = signer.signDetached(
            content,
            fixture.identity,
            SigningAlgorithm.SHA256_WITH_RSA,
        ) as LocalSignatureResult.Success

        result.signature.use { signature ->
            signature.withBytes { bytes ->
                assertTrue(verifies(content, bytes))
            }
        }
        assertEquals(0, fixture.encodedReads.get())
    }

    @Test
    fun detachedCadesHasMessageDigestAndSigningCertificateV2Attributes() {
        val content = "attributes".encodeToByteArray()
        val result = signer.signDetached(
            content,
            syntheticIdentity(),
            SigningAlgorithm.SHA256_WITH_RSA,
        ) as LocalSignatureResult.Success

        result.signature.use { signature ->
            signature.withBytes { bytes ->
                val signerInfo = signerInfo(content, bytes)
                assertNotNull(signerInfo.signedAttributes.get(CMSAttributes.messageDigest))
                assertNotNull(
                    signerInfo.signedAttributes.get(
                        PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                    ),
                )
            }
        }
    }

    @Test
    fun detachedCadesContainsCertificateButNotAttachedContent() {
        val content = "detached".encodeToByteArray()
        val result = signer.signDetached(
            content,
            syntheticIdentity(),
            SigningAlgorithm.SHA256_WITH_RSA,
        ) as LocalSignatureResult.Success

        result.signature.use { signature ->
            signature.withBytes { bytes ->
                val withoutExternalContent = CMSSignedData(bytes)
                assertNull(withoutExternalContent.signedContent)
                assertFalse(withoutExternalContent.certificates.getMatches(null).isEmpty())
            }
        }
    }

    @Test
    fun detachedCadesFailsAgainstMutatedDetachedContent() {
        val content = "original".encodeToByteArray()
        val changed = "changed!".encodeToByteArray()
        val result = signer.signDetached(
            content,
            syntheticIdentity(),
            SigningAlgorithm.SHA256_WITH_RSA,
        ) as LocalSignatureResult.Success

        result.signature.use { signature ->
            signature.withBytes { bytes ->
                assertFalse(verifies(changed, bytes))
            }
        }
        changed.fill(0)
    }

    @Test
    fun cadesInputAndOutputBoundsFailClosed() {
        val identity = syntheticIdentity()
        val oversized = ByteArray(BouncyCastleCadesSigner.MAX_INPUT_BYTES + 1)
        assertEquals(
            LocalSignatureError.INPUT_TOO_LARGE,
            (signer.signDetached(
                oversized,
                identity,
                SigningAlgorithm.SHA256_WITH_RSA,
            ) as LocalSignatureResult.Failure).error,
        )
        val tinyOutputSigner = BouncyCastleCadesSigner(maxOutputBytes = 1)
        assertEquals(
            LocalSignatureError.OUTPUT_TOO_LARGE,
            (tinyOutputSigner.signDetached(
                byteArrayOf(1),
                identity,
                SigningAlgorithm.SHA256_WITH_RSA,
            ) as LocalSignatureResult.Failure).error,
        )
        oversized.fill(0)
    }

    @Test
    fun cadesSignerDoesNotInstallOrReorderGlobalSecurityProviders() {
        val before = Security.getProviders().map { it.name }

        val result = signer.signDetached(
            "provider-order".encodeToByteArray(),
            syntheticIdentity(),
            SigningAlgorithm.SHA256_WITH_RSA,
        ) as LocalSignatureResult.Success
        result.signature.close()

        assertEquals(before, Security.getProviders().map { it.name })
    }

    private fun verifies(content: ByteArray, signatureBytes: ByteArray): Boolean = runCatching {
        val signedData = CMSSignedData(CMSProcessableByteArray(content), signatureBytes)
        val signerInfo = signedData.signerInfos.signers.single()
        val holder = signedData.certificates.getMatches(null)
            .single() as X509CertificateHolder
        signerInfo.verify(
            JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider())
                .build(holder),
        )
    }.getOrDefault(false)

    private fun signerInfo(content: ByteArray, signatureBytes: ByteArray): SignerInformation =
        CMSSignedData(CMSProcessableByteArray(content), signatureBytes)
            .signerInfos
            .signers
            .single()
}
