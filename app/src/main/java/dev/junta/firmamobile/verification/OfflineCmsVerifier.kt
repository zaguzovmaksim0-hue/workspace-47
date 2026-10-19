package dev.junta.firmamobile.verification

import dev.junta.firmamobile.signing.BundledBouncyCastle
import java.security.PublicKey
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder

internal enum class OfflineCmsStatus {
    VERIFIED,
    INVALID,
    UNSUPPORTED,
    LIMIT_EXCEEDED,
    DETACHED_CONTENT_REQUIRED,
}

internal enum class OfflineCmsScope {
    INTEGRITY_ONLY,
}

internal enum class OfflineCmsTrust {
    NOT_EVALUATED,
}

internal data class OfflineCmsResult(
    val status: OfflineCmsStatus,
    val verifiedSigners: Int = 0,
    val scope: OfflineCmsScope = OfflineCmsScope.INTEGRITY_ONLY,
    val trust: OfflineCmsTrust = OfflineCmsTrust.NOT_EVALUATED,
)

private const val OFFLINE_CMS_MAX_BYTES = 1024 * 1024
internal const val OFFLINE_CMS_MAX_CONTENT_BYTES = 512 * 1024

internal class OfflineCmsUnsupportedProfileException : Exception("Unsupported CMS profile")

internal class OfflineCmsLimitException : Exception("CMS limit exceeded")

private data class OfflineCmsSignerCandidate(
    val signer: SignerInformation,
    val publicKey: PublicKey,
)

internal object OfflineCmsVerifier {
    fun verify(
        cmsBytes: ByteArray,
        detachedContent: ByteArray? = null,
    ): OfflineCmsResult {
        if (cmsBytes.size > OFFLINE_CMS_MAX_BYTES ||
            detachedContent?.size?.let { it > OFFLINE_CMS_MAX_CONTENT_BYTES } == true
        ) {
            return OfflineCmsResult(OfflineCmsStatus.LIMIT_EXCEEDED)
        }

        val cmsSnapshot = cmsBytes.copyOf()
        var contentSnapshot: ByteArray? = null
        return try {
            contentSnapshot = detachedContent?.copyOf()
            OfflineCmsVerification.verifySnapshot(cmsSnapshot, contentSnapshot)
        } catch (_: OfflineCmsUnsupportedProfileException) {
            OfflineCmsResult(OfflineCmsStatus.UNSUPPORTED)
        } catch (_: OfflineCmsLimitException) {
            OfflineCmsResult(OfflineCmsStatus.LIMIT_EXCEEDED)
        } catch (expected: Exception) {
            OfflineCmsResult(OfflineCmsStatus.INVALID)
        } finally {
            cmsSnapshot.fill(0)
            contentSnapshot?.fill(0)
        }
    }
}

private object OfflineCmsVerification {
    fun verifySnapshot(
        cmsBytes: ByteArray,
        detachedContent: ByteArray?,
    ): OfflineCmsResult {
        val parsed = OfflineCmsParsing.parse(cmsBytes)
        try {
            if (parsed.attachedContent != null && detachedContent != null) {
                return OfflineCmsResult(OfflineCmsStatus.INVALID)
            }

            val content = parsed.attachedContent ?: detachedContent
            val cmsData = createCmsSignedData(cmsBytes, content, parsed.attachedContent != null)
            val signers = cmsData.getSignerInfos().getSigners().toList()
            require(signers.size == parsed.signerCount) {
                "CMS signer count mismatch"
            }

            val candidates = buildCandidates(signers, parsed.certificates)
            return if (content == null) {
                OfflineCmsResult(OfflineCmsStatus.DETACHED_CONTENT_REQUIRED)
            } else {
                verifyCandidates(candidates)
                OfflineCmsResult(
                    status = OfflineCmsStatus.VERIFIED,
                    verifiedSigners = candidates.size,
                )
            }
        } finally {
            parsed.attachedContent?.fill(0)
        }
    }

    private fun createCmsSignedData(
        cmsBytes: ByteArray,
        content: ByteArray?,
        hasAttachedContent: Boolean,
    ): CMSSignedData {
        return when {
            hasAttachedContent -> CMSSignedData(cmsBytes)
            content != null -> CMSSignedData(CMSProcessableByteArray(content), cmsBytes)
            else -> CMSSignedData(cmsBytes)
        }
    }

    private fun buildCandidates(
        signers: List<SignerInformation>,
        certificates: List<X509CertificateHolder>,
    ): List<OfflineCmsSignerCandidate> {
        val usedCertificates = mutableSetOf<Int>()
        val converter = JcaX509CertificateConverter().setProvider(BundledBouncyCastle.provider)
        val candidates = mutableListOf<OfflineCmsSignerCandidate>()

        for (signer in signers) {
            val matchingIndexes = certificates.indices.filter { index ->
                signer.getSID().match(certificates[index])
            }
            require(matchingIndexes.size == 1 && usedCertificates.add(matchingIndexes.single())) {
                "Invalid CMS signer certificate"
            }

            val digestOid = signer.getDigestAlgOID()
            val signatureOid = signer.getEncryptionAlgOID()
            OfflineCmsAlgorithmPolicy.validateSignerAlgorithms(digestOid, signatureOid)
            OfflineCmsAttributePolicy.validate(signer)

            val certificate = converter.getCertificate(certificates[matchingIndexes.single()])
            val publicKey = certificate.publicKey
            OfflineCmsAlgorithmPolicy.validatePublicKey(digestOid, signatureOid, publicKey)
            candidates.add(OfflineCmsSignerCandidate(signer, publicKey))
        }
        return candidates
    }

    private fun verifyCandidates(
        candidates: List<OfflineCmsSignerCandidate>,
    ) {
        val verifierBuilder = JcaSimpleSignerInfoVerifierBuilder()
            .setProvider(BundledBouncyCastle.provider)
        for (candidate in candidates) {
            val verifier = verifierBuilder.build(candidate.publicKey)
            require(candidate.signer.verify(verifier)) {
                "CMS signature verification failed"
            }
        }
    }
}
